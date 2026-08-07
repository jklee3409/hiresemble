<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import {
  JOB_EDITABLE_CONFLICT_FIELDS,
  isJobVersionConflict,
  reapplyJobDraft,
  type JobConflictDraft,
  type JobVersionConflict,
} from '@/features/jobs/conflict'
import JobRunMonitor from '@/features/jobs/JobRunMonitor.vue'
import JobDescriptionDocument from '@/features/jobs/JobDescriptionDocument.vue'
import JobPreparationJourney from '@/features/jobs/JobPreparationJourney.vue'
import JobVersionConflictPanel from '@/features/jobs/JobVersionConflictPanel.vue'
import {
  CLOSED_REASON_LABELS,
  DEADLINE_SOURCE_LABELS,
  DESCRIPTION_SOURCE_LABELS,
  JOB_EXTRACTION_STATUS_LABELS,
  JOB_STATUS_LABELS,
  formatJobInstant,
  jobExtractionGuidance,
} from '@/features/jobs/presentation'
import {
  finalizeJobDeletion,
  jobQueryKeys,
  useDeleteJobMutation,
  useJobDetailQuery,
  useLatestJobExtractionRunQuery,
  useRetryJobExtractionMutation,
  useUpdateJobMutation,
  useUpdateJobStatusMutation,
} from '@/features/jobs/queries'
import {
  instantToLocalDateTime,
  type JobUpdateForm,
  validateJobUpdateForm,
} from '@/features/jobs/validation'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import { getJob } from '@/shared/api/jobApi'
import {
  JOB_STATUSES,
  type JobDetailDto,
  type JobExtractionStatus,
  type JobStatus,
  type UpdateJobRequest,
} from '@/shared/api/jobContracts'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const latestRun = useLatestJobExtractionRunQuery(userId, jobId)
const updateMutation = useUpdateJobMutation(userId)
const statusMutation = useUpdateJobStatusMutation(userId)
const retryMutation = useRetryJobExtractionMutation(userId)
const deleteMutation = useDeleteJobMutation(userId)

const form = reactive<JobUpdateForm>(emptyUpdateForm())
const editing = ref(false)
const selectedStatus = ref<JobStatus>('IN_PROGRESS')
const fieldErrors = ref<Record<string, string>>({})
const actionError = ref('')
const message = ref('')
const conflict = ref<(JobVersionConflict & { operation: 'edit' | 'status' }) | null>(null)
const descriptionInput = ref<HTMLTextAreaElement | null>(null)

const requestedRunId = computed(() => {
  const value = typeof route.query.run === 'string' ? route.query.run : ''
  return UUID_PATTERN.test(value) ? value : ''
})
const monitoredRunId = computed(
  () => requestedRunId.value || latestRun.data.value?.items[0]?.id || '',
)
const extractionRunActive = computed(() =>
  ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(latestRun.data.value?.items[0]?.status ?? ''),
)
const extractionCommandUnavailable = computed(
  () =>
    retryMutation.isPending.value ||
    extractionRunActive.value ||
    latestRun.isLoading.value ||
    latestRun.isError.value,
)
const loadError = computed(() => {
  if (job.error.value === null) return ''
  const error = normalizeApiError(job.error.value)
  return error.status === 404 ? '공고를 찾을 수 없어요.' : error.message
})
const createdMessage = computed(() => {
  if (route.query.created === 'manual') {
    return '공고를 등록했어요. 입력한 내용을 바탕으로 분석도 자동으로 시작했어요.'
  }
  if (route.query.created === 'async') {
    return '공고를 등록했어요. 내용을 읽은 뒤 분석까지 이어서 준비할게요.'
  }
  return ''
})

watch(
  () => job.data.value,
  (value) => {
    if (value === undefined || editing.value || conflict.value !== null) return
    loadForm(value)
    selectedStatus.value = value.status
  },
  { immediate: true },
)

function beginEdit(focusDescription = false): void {
  if (job.data.value === undefined) return
  loadForm(job.data.value)
  editing.value = true
  fieldErrors.value = {}
  actionError.value = ''
  if (focusDescription) {
    void nextTick(() => descriptionInput.value?.focus())
  }
}

function cancelEdit(): void {
  editing.value = false
  fieldErrors.value = {}
  if (job.data.value !== undefined) loadForm(job.data.value)
}

async function saveEdit(): Promise<void> {
  const validation = validateJobUpdateForm(form)
  fieldErrors.value = validation.fieldErrors
  message.value = ''
  actionError.value = ''
  if (validation.data === null) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }
  try {
    const saved = await updateMutation.mutateAsync({
      jobId: jobId.value,
      request: validation.data,
    })
    loadForm(saved)
    selectedStatus.value = saved.status
    editing.value = false
    message.value =
      saved.descriptionSource === 'USER_ENTERED'
        ? '공고 정보를 저장하고 직접 입력한 내용을 적용했어요.'
        : '공고 정보를 저장했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (isJobVersionConflict(apiError)) {
      await establishConflict(validation.data, 'edit', [...JOB_EDITABLE_CONFLICT_FIELDS])
      return
    }
    actionError.value = apiError.message
  }
}

async function changeStatus(): Promise<void> {
  const current = job.data.value
  if (current === undefined || selectedStatus.value === current.status) return
  message.value = ''
  actionError.value = ''
  try {
    const saved = await statusMutation.mutateAsync({
      jobId: jobId.value,
      status: selectedStatus.value,
      version: current.version,
    })
    selectedStatus.value = saved.status
    message.value = `지원 상태를 ${JOB_STATUS_LABELS[saved.status]}(으)로 변경했어요.`
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (isJobVersionConflict(apiError)) {
      const draft = editableRequest(current)
      draft.status = selectedStatus.value
      await establishConflict(draft, 'status', [{ key: 'status', label: '지원 상태' }])
      return
    }
    selectedStatus.value = current.status
    actionError.value = apiError.message
  }
}

async function retryExtraction(): Promise<void> {
  const current = job.data.value
  if (current === undefined || extractionCommandUnavailable.value) return
  message.value = ''
  actionError.value = ''
  try {
    const accepted = await retryMutation.mutateAsync({
      jobId: current.id,
      version: current.version,
    })
    await router.replace({
      query: { ...route.query, run: accepted.agentRunId, created: undefined },
    })
    message.value =
      accepted.status === 'WAITING_USER'
        ? '진행 중인 공고 불러오기를 다시 확인했어요.'
        : '공고를 다시 불러오기 시작했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (isJobVersionConflict(apiError)) {
      await refreshLatest()
      actionError.value = '공고가 다른 곳에서 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해 주세요.'
      return
    }
    actionError.value = apiError.message
  }
}

async function remove(): Promise<void> {
  const current = job.data.value
  if (current === undefined) return
  const confirmed = await notifications.confirm({
    title: '관심 공고를 삭제할까요?',
    message:
      '공고와 연결된 분석·자기소개서 흐름에 영향을 줄 수 있어요. 삭제 후에는 목록에서 복구할 수 없습니다.',
    confirmLabel: '공고 삭제',
  })
  if (!confirmed) return
  message.value = ''
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync({ jobId: current.id, version: current.version })
    await completeDeletion()
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (apiError.status === 404) {
      await finalizeJobDeletion(cache, userId.value, current.id)
      await completeDeletion()
      return
    }
    if (isJobVersionConflict(apiError)) {
      await refreshLatest()
      actionError.value =
        '공고가 다른 곳에서 변경됐어요. 최신 내용을 확인한 뒤 삭제를 다시 선택해 주세요.'
      return
    }
    actionError.value = apiError.message
  }
}

async function establishConflict(
  draft: JobConflictDraft,
  operation: 'edit' | 'status',
  fields: ReadonlyArray<{ key: keyof JobConflictDraft; label: string }>,
): Promise<void> {
  try {
    const latest = await refreshLatest()
    conflict.value = { latest, draft, fields, operation }
    actionError.value = '최신 공고와 내 입력을 비교해 다시 적용해 주세요.'
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

function reapplyConflict(selectedFields: string[]): void {
  const currentConflict = conflict.value
  if (currentConflict === null) return
  const allowed = new Set(currentConflict.fields.map((field) => field.key))
  const selected = selectedFields.filter((field): field is keyof JobConflictDraft =>
    allowed.has(field as keyof JobConflictDraft),
  )
  const reapplied = reapplyJobDraft(currentConflict.latest, currentConflict.draft, selected)
  if (currentConflict.operation === 'edit') {
    loadFormFromRequest(reapplied)
    editing.value = true
  } else {
    selectedStatus.value = reapplied.status ?? currentConflict.latest.status
  }
  conflict.value = null
  actionError.value = ''
  message.value = '선택한 내 값을 최근 저장된 내용에 다시 적용했어요. 확인한 뒤 저장해 주세요.'
}

function cancelConflict(): void {
  const latest = conflict.value?.latest
  conflict.value = null
  actionError.value = ''
  if (latest !== undefined) {
    loadForm(latest)
    selectedStatus.value = latest.status
  }
}

async function refreshLatest(): Promise<JobDetailDto> {
  const latest = await getJob(jobId.value)
  cache.setQueryData(jobQueryKeys.detail(userId.value, jobId.value), latest)
  return latest
}

async function completeDeletion(): Promise<void> {
  closeAgentRunStreamsForResource(userId.value, 'JOB', jobId.value)
  await router.replace({ name: 'jobs', query: { deleted: 'true' } })
}

function editableRequest(value: JobDetailDto): JobConflictDraft {
  return {
    companyName: value.companyName,
    title: value.title,
    positionName: value.positionName,
    descriptionText: value.descriptionText,
    deadlineAt: value.deadlineAt,
    version: value.version,
  }
}

function loadForm(value: JobDetailDto): void {
  loadFormFromRequest(editableRequest(value))
}

function loadFormFromRequest(value: UpdateJobRequest): void {
  Object.assign(form, {
    companyName: value.companyName ?? '',
    title: value.title ?? '',
    positionName: value.positionName ?? '',
    descriptionText: value.descriptionText ?? '',
    deadlineAt: instantToLocalDateTime(value.deadlineAt ?? null),
    version: value.version,
  })
}

function emptyUpdateForm(): JobUpdateForm {
  return {
    companyName: '',
    title: '',
    positionName: '',
    descriptionText: '',
    deadlineAt: '',
    version: 0,
  }
}

function extractionTone(
  value: JobExtractionStatus,
): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return (
    {
      QUEUED: 'neutral',
      EXTRACTING: 'info',
      EXTRACTED: 'success',
      MANUAL_INPUT_PROVIDED: 'success',
      NEEDS_MANUAL_INPUT: 'warning',
      FAILED: 'danger',
    } as const
  )[value]
}
</script>

<template>
  <div class="job-overview app-page">
    <StatePanel
      v-if="job.isPending.value"
      kind="loading"
      title="공고 정보를 불러오는 중…"
      description="저장한 공고와 불러오기 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="job.isError.value"
      kind="error"
      title="공고를 불러오지 못했어요."
      :description="loadError"
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="job.refetch()">
          다시 시도
        </button>
        <RouterLink class="button button--ghost" :to="{ name: 'jobs' }">
          공고 목록으로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>

    <template v-else-if="job.data.value">
      <p v-if="createdMessage" class="alert alert--success job-overview__created" role="status">
        {{ createdMessage }}
      </p>
      <JobPreparationJourney :job="job.data.value" />

      <!-- 공고 이름 옆 "원본 공고 보기" 아래로 보내 별도 제목 줄을 없앤다. -->
      <Teleport to="#job-detail-actions">
        <div class="job-overview__actions">
          <!-- 지원 상태는 별도 form 없이 고르는 즉시 저장한다. -->
          <label class="job-status-picker">
            <span class="sr-only">지원 상태</span>
            <select
              id="job-status-select"
              v-model="selectedStatus"
              class="control control--compact"
              :disabled="statusMutation.isPending.value"
              @change="changeStatus"
            >
              <option v-for="status in JOB_STATUSES" :key="status" :value="status">
                {{ JOB_STATUS_LABELS[status] }}
              </option>
            </select>
          </label>
          <button
            type="button"
            class="button button--secondary button--compact"
            @click="beginEdit(false)"
          >
            편집
          </button>
          <button
            v-if="['NEEDS_MANUAL_INPUT', 'FAILED'].includes(job.data.value.extractionStatus)"
            id="job-manual-input"
            type="button"
            class="button button--warning button--compact"
            @click="beginEdit(true)"
          >
            본문 직접 입력
          </button>
          <button
            v-if="job.data.value.extractionStatus === 'FAILED'"
            id="job-retry-extraction"
            type="button"
            class="button button--secondary button--compact"
            :disabled="extractionCommandUnavailable"
            @click="retryExtraction"
          >
            {{ extractionRunActive ? '불러오는 중…' : '공고 다시 불러오기' }}
          </button>
          <button
            id="job-delete"
            type="button"
            class="button button--danger button--compact"
            :disabled="deleteMutation.isPending.value"
            @click="remove"
          >
            {{ deleteMutation.isPending.value ? '삭제 중…' : '삭제' }}
          </button>
        </div>
      </Teleport>

      <!-- 정상 상태의 안내는 본문 카드 안의 배지로 충분하다. 조치가 필요할 때만 알림을 띄운다. -->
      <p
        v-if="['NEEDS_MANUAL_INPUT', 'FAILED'].includes(job.data.value.extractionStatus)"
        class="alert alert--info job-overview__notice"
      >
        {{ jobExtractionGuidance(job.data.value.extractionStatus) }}
      </p>
      <p
        v-if="
          job.data.value.extractionStatus === 'FAILED' && job.data.value.extractionError !== null
        "
        class="alert alert--warning job-overview__notice"
        role="alert"
      >
        {{ job.data.value.extractionError.message }}
      </p>
      <p v-if="message" class="alert alert--success job-overview__notice" role="status">
        {{ message }}
      </p>
      <p v-if="actionError" class="alert alert--danger job-overview__notice" role="alert">
        {{ actionError }}
      </p>

      <JobVersionConflictPanel
        v-if="conflict"
        class="job-overview__conflict"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="conflict.fields"
        @reapply="reapplyConflict"
        @cancel="cancelConflict"
      />

      <section v-if="editing" class="job-editor section-surface" aria-label="공고 정보 편집">
        <div class="job-editor__header">
          <div>
            <p class="section-kicker">내용 수정</p>
            <h3 class="section-title">공고 정보 편집</h3>
          </div>
          <button type="button" class="button button--ghost button--compact" @click="cancelEdit">
            닫기
          </button>
        </div>
        <form class="job-editor__form" novalidate @submit.prevent="saveEdit">
          <div class="job-editor__grid">
            <label class="field">
              <span class="field__label">회사명</span>
              <input
                id="job-edit-company"
                v-model="form.companyName"
                maxlength="200"
                class="control"
                :aria-invalid="Boolean(fieldErrors.companyName)"
              />
              <span v-if="fieldErrors.companyName" class="inline-error">{{
                fieldErrors.companyName
              }}</span>
            </label>
            <label class="field">
              <span class="field__label">공고 제목</span>
              <input
                id="job-edit-title"
                v-model="form.title"
                maxlength="300"
                class="control"
                :aria-invalid="Boolean(fieldErrors.title)"
              />
              <span v-if="fieldErrors.title" class="inline-error">{{ fieldErrors.title }}</span>
            </label>
            <label class="field">
              <span class="field__label">직무명</span>
              <input
                id="job-edit-position"
                v-model="form.positionName"
                maxlength="300"
                class="control"
                :aria-invalid="Boolean(fieldErrors.positionName)"
              />
              <span v-if="fieldErrors.positionName" class="inline-error">{{
                fieldErrors.positionName
              }}</span>
            </label>
            <label class="field">
              <span class="field__label">마감 일시</span>
              <input
                id="job-edit-deadline"
                v-model="form.deadlineAt"
                type="datetime-local"
                class="control"
                :aria-invalid="Boolean(fieldErrors.deadlineAt)"
              />
              <span v-if="fieldErrors.deadlineAt" class="inline-error">{{
                fieldErrors.deadlineAt
              }}</span>
            </label>
          </div>
          <label class="field">
            <span class="field__label">공고 본문</span>
            <textarea
              id="job-edit-description"
              ref="descriptionInput"
              v-model="form.descriptionText"
              maxlength="200000"
              class="control job-editor__description"
              :aria-invalid="Boolean(fieldErrors.descriptionText)"
            />
            <span v-if="fieldErrors.descriptionText" class="inline-error">{{
              fieldErrors.descriptionText
            }}</span>
          </label>
          <div class="form-actions">
            <button
              type="submit"
              class="button button--primary"
              :disabled="updateMutation.isPending.value"
            >
              {{ updateMutation.isPending.value ? '저장 중…' : '저장' }}
            </button>
            <button type="button" class="button button--secondary" @click="cancelEdit">취소</button>
          </div>
        </form>
      </section>

      <div class="job-overview__grid">
        <section class="job-description section-surface" aria-labelledby="job-description-heading">
          <div class="job-description__header">
            <div>
              <p class="section-kicker">공고 내용</p>
              <h2 id="job-description-heading" class="section-title">공고 본문</h2>
            </div>
            <div class="job-description__tools">
              <StatusBadge
                data-testid="job-extraction-status"
                :label="JOB_EXTRACTION_STATUS_LABELS[job.data.value.extractionStatus]"
                :tone="extractionTone(job.data.value.extractionStatus)"
              />
              <button
                type="button"
                class="button button--ghost button--compact"
                @click="beginEdit(true)"
              >
                본문 수정
              </button>
            </div>
          </div>
          <p class="job-description__source">
            출처:
            {{
              job.data.value.descriptionSource
                ? DESCRIPTION_SOURCE_LABELS[job.data.value.descriptionSource]
                : '미확인'
            }}
          </p>
          <JobDescriptionDocument
            v-if="job.data.value.descriptionText"
            :source="job.data.value.descriptionText"
          />
          <p v-else class="job-description__empty">
            저장된 공고 내용이 없어요. 편집에서 직접 입력해 주세요.
          </p>
        </section>

        <div class="job-overview__side">
          <section class="job-side-section section-surface">
            <p class="section-kicker">기본 정보</p>
            <h3 class="section-title">공고 정보</h3>
            <dl class="job-facts">
              <div>
                <dt>공고 링크</dt>
                <dd>
                  <a
                    class="job-facts__url"
                    :href="job.data.value.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ job.data.value.sourceUrl }}
                  </a>
                </dd>
              </div>
              <div>
                <dt>마감</dt>
                <dd>{{ formatJobInstant(job.data.value.deadlineAt) }}</dd>
                <dd class="job-facts__note">
                  {{ DEADLINE_SOURCE_LABELS[job.data.value.deadlineSource] }}
                </dd>
              </div>
              <div v-if="job.data.value.submittedAt">
                <dt>최초 서류 제출</dt>
                <dd>{{ formatJobInstant(job.data.value.submittedAt) }}</dd>
              </div>
              <div v-if="job.data.value.closedAt">
                <dt>마감 처리</dt>
                <dd>{{ formatJobInstant(job.data.value.closedAt) }}</dd>
                <dd v-if="job.data.value.closedReason" class="job-facts__note">
                  {{ CLOSED_REASON_LABELS[job.data.value.closedReason] }}
                </dd>
              </div>
              <div v-if="job.data.value.roleCategory">
                <dt>직무 분류</dt>
                <dd>{{ job.data.value.roleCategory }}</dd>
              </div>
              <div v-if="job.data.value.employmentType">
                <dt>고용 형태</dt>
                <dd>{{ job.data.value.employmentType }}</dd>
              </div>
              <div v-if="job.data.value.location">
                <dt>근무지</dt>
                <dd>{{ job.data.value.location }}</dd>
              </div>
            </dl>
          </section>

          <section v-if="monitoredRunId" class="job-side-section section-surface">
            <p class="section-kicker">공고 내용</p>
            <h3 class="section-title">공고 불러오기</h3>
            <RouterLink
              class="job-run-link"
              :to="{ name: 'agent-run-detail', params: { agentRunId: monitoredRunId } }"
            >
              작업 진행 상세 보기
            </RouterLink>
            <JobRunMonitor :user-id="userId" :job-id="jobId" :agent-run-id="monitoredRunId" />
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.job-overview__created {
  margin-bottom: var(--space-4);
}

.job-overview__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.job-status-picker {
  min-width: 0;
}

.job-status-picker select {
  width: auto;
  min-width: 7.5rem;
  font-weight: 680;
}

.job-overview__notice,
.job-overview__conflict,
.job-editor,
.job-overview__grid {
  margin-top: var(--space-5);
}

.job-editor,
.job-description,
.job-side-section {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.job-editor__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.job-editor__form {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.job-editor__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.job-editor__description {
  min-height: 18rem;
  line-height: 1.7;
}

.job-overview__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(17rem, 0.8fr);
  gap: var(--space-5);
}

.job-description__source,
.job-description__empty {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.job-description__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.job-description__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.job-overview__side {
  display: grid;
  align-content: start;
  gap: var(--space-4);
}

.job-facts {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-4);
  font-size: var(--font-size-sm);
}

.job-facts dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.job-facts dd {
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
}

.job-facts__note {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.job-facts__url,
.job-run-link {
  color: var(--color-brand-strong);
  text-decoration: underline;
  text-underline-offset: 0.16em;
}

.job-run-link {
  display: inline-block;
  margin-top: var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 650;
}

@media (max-width: 64rem) {
  .job-overview__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 40rem) {
  .job-editor__grid {
    grid-template-columns: 1fr;
  }
}
</style>
