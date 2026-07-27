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
import JobVersionConflictPanel from '@/features/jobs/JobVersionConflictPanel.vue'
import {
  CLOSED_REASON_LABELS,
  DEADLINE_SOURCE_LABELS,
  DESCRIPTION_SOURCE_LABELS,
  JOB_EXTRACTION_STATUS_LABELS,
  JOB_STATUS_LABELS,
  formatJobInstant,
  jobCompanyLabel,
  jobDisplayTitle,
  jobExtractionGuidance,
} from '@/features/jobs/presentation'
import {
  finalizeJobDeletion,
  jobQueryKeys,
  useDeleteJobMutation,
  useJobDetailQuery,
  useLatestJobRunQuery,
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
  type JobStatus,
  type UpdateJobRequest,
} from '@/shared/api/jobContracts'
import { useAuthStore } from '@/stores/auth'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const latestRun = useLatestJobRunQuery(userId, jobId)
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
const loadError = computed(() => {
  if (job.error.value === null) return ''
  const error = normalizeApiError(job.error.value)
  return error.status === 404 ? '공고를 찾을 수 없습니다.' : error.message
})
const createdMessage = computed(() => {
  if (route.query.created === 'manual') {
    return '수동 본문으로 공고를 등록했습니다. URL 추출 작업은 만들지 않았습니다.'
  }
  if (route.query.created === 'async') {
    return '공고를 등록하고 URL 추출 작업을 시작했습니다.'
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
  if (validation.data === null) return
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
        ? '공고 정보를 저장하고 수동 본문을 적용했습니다.'
        : '공고 정보를 저장했습니다.'
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
    message.value = `공고 상태를 ${JOB_STATUS_LABELS[saved.status]}(으)로 변경했습니다.`
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (isJobVersionConflict(apiError)) {
      const draft = editableRequest(current)
      draft.status = selectedStatus.value
      await establishConflict(draft, 'status', [{ key: 'status', label: '업무 상태' }])
      return
    }
    selectedStatus.value = current.status
    actionError.value = apiError.message
  }
}

async function retryExtraction(): Promise<void> {
  const current = job.data.value
  if (current === undefined) return
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
        ? '기존 URL 추출 작업을 다시 확인했습니다.'
        : 'URL 추출 재시도를 접수했습니다.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (isJobVersionConflict(apiError)) {
      await refreshLatest()
      actionError.value = '공고가 변경되었습니다. 최신 버전을 확인한 뒤 다시 시도해 주세요.'
      return
    }
    actionError.value = apiError.message
  }
}

async function remove(): Promise<void> {
  const current = job.data.value
  if (current === undefined || !window.confirm('이 채용 공고를 삭제할까요?')) return
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
      actionError.value = '공고가 변경되었습니다. 최신 버전을 확인한 뒤 삭제를 다시 선택해 주세요.'
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
  message.value =
    '선택한 내 값을 최신 서버 버전에 재적용했습니다. 내용을 확인하고 다시 저장해 주세요.'
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
</script>

<template>
  <div class="pt-6">
    <p v-if="job.isPending.value" aria-live="polite">공고 상세를 불러오는 중…</p>
    <div v-else-if="job.isError.value" class="rounded-xl bg-red-50 p-5 text-red-800" role="alert">
      <p>{{ loadError }}</p>
      <div class="mt-3 flex flex-wrap gap-3">
        <button type="button" class="underline" @click="job.refetch()">다시 시도</button>
        <RouterLink class="underline" :to="{ name: 'jobs' }">공고 목록으로 돌아가기</RouterLink>
      </div>
    </div>

    <template v-else-if="job.data.value">
      <p
        v-if="createdMessage"
        class="mb-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800"
        role="status"
      >
        {{ createdMessage }}
      </p>
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <p class="text-sm font-medium text-slate-600">
            {{ jobCompanyLabel(job.data.value.companyName) }}
          </p>
          <h2 class="mt-1 break-words text-2xl font-bold">
            {{ jobDisplayTitle(job.data.value) }}
          </h2>
          <p v-if="job.data.value.title && job.data.value.positionName" class="mt-1 text-slate-600">
            직무: {{ job.data.value.positionName }}
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="rounded-lg border border-slate-300 px-3 py-2"
            @click="beginEdit(false)"
          >
            편집
          </button>
          <button
            v-if="['NEEDS_MANUAL_INPUT', 'FAILED'].includes(job.data.value.extractionStatus)"
            id="job-manual-input"
            type="button"
            class="rounded-lg border border-amber-500 px-3 py-2 text-amber-900"
            @click="beginEdit(true)"
          >
            본문 직접 입력
          </button>
          <button
            v-if="job.data.value.extractionStatus === 'FAILED'"
            id="job-retry-extraction"
            type="button"
            class="rounded-lg border border-slate-300 px-3 py-2 disabled:opacity-50"
            :disabled="retryMutation.isPending.value"
            @click="retryExtraction"
          >
            {{ retryMutation.isPending.value ? '재시도 접수 중…' : 'URL 추출 재시도' }}
          </button>
          <button
            id="job-delete"
            type="button"
            class="rounded-lg border border-red-300 px-3 py-2 text-red-700 disabled:opacity-50"
            :disabled="deleteMutation.isPending.value"
            @click="remove"
          >
            {{ deleteMutation.isPending.value ? '삭제 중…' : '삭제' }}
          </button>
        </div>
      </div>

      <div class="mt-5 flex flex-wrap gap-2">
        <span
          data-testid="job-business-status"
          class="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-800"
        >
          업무 · {{ JOB_STATUS_LABELS[job.data.value.status] }}
        </span>
        <span
          data-testid="job-extraction-status"
          class="rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-800"
        >
          추출 · {{ JOB_EXTRACTION_STATUS_LABELS[job.data.value.extractionStatus] }}
        </span>
        <span
          v-if="job.data.value.status === 'CLOSED' && job.data.value.submittedAt"
          class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800"
        >
          서류 제출 이력 있음
        </span>
      </div>

      <p class="mt-4 rounded-lg bg-sky-50 p-3 text-sm text-sky-900">
        {{ jobExtractionGuidance(job.data.value.extractionStatus) }}
      </p>
      <p
        v-if="
          job.data.value.extractionStatus === 'FAILED' && job.data.value.extractionError !== null
        "
        class="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
        role="alert"
      >
        {{ job.data.value.extractionError.message }}
        <span class="mt-1 block text-xs">오류 코드: {{ job.data.value.extractionError.code }}</span>
      </p>
      <p v-if="message" class="mt-3 text-sm text-emerald-700" role="status">{{ message }}</p>
      <p v-if="actionError" class="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">
        {{ actionError }}
      </p>

      <JobVersionConflictPanel
        v-if="conflict"
        class="mt-5"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="conflict.fields"
        @reapply="reapplyConflict"
        @cancel="cancelConflict"
      />

      <section v-if="editing" class="mt-6 rounded-2xl bg-white p-6 shadow-sm">
        <h3 class="text-lg font-semibold">공고 정보 편집</h3>
        <form class="mt-4 space-y-4" novalidate @submit.prevent="saveEdit">
          <div class="grid gap-4 md:grid-cols-2">
            <label class="text-sm font-medium">
              회사명
              <input
                id="job-edit-company"
                v-model="form.companyName"
                maxlength="200"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              />
              <span v-if="fieldErrors.companyName" class="mt-1 block text-red-700">{{
                fieldErrors.companyName
              }}</span>
            </label>
            <label class="text-sm font-medium">
              공고 제목
              <input
                id="job-edit-title"
                v-model="form.title"
                maxlength="300"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              />
              <span v-if="fieldErrors.title" class="mt-1 block text-red-700">{{
                fieldErrors.title
              }}</span>
            </label>
            <label class="text-sm font-medium">
              직무명
              <input
                id="job-edit-position"
                v-model="form.positionName"
                maxlength="300"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              />
              <span v-if="fieldErrors.positionName" class="mt-1 block text-red-700">{{
                fieldErrors.positionName
              }}</span>
            </label>
            <label class="text-sm font-medium">
              마감 일시
              <input
                id="job-edit-deadline"
                v-model="form.deadlineAt"
                type="datetime-local"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              />
              <span v-if="fieldErrors.deadlineAt" class="mt-1 block text-red-700">{{
                fieldErrors.deadlineAt
              }}</span>
            </label>
          </div>
          <label class="block text-sm font-medium">
            공고 본문
            <textarea
              id="job-edit-description"
              ref="descriptionInput"
              v-model="form.descriptionText"
              maxlength="200000"
              class="mt-1 min-h-72 w-full rounded-lg border border-slate-300 p-3"
            />
            <span v-if="fieldErrors.descriptionText" class="mt-1 block text-red-700">{{
              fieldErrors.descriptionText
            }}</span>
          </label>
          <div class="flex flex-wrap gap-2">
            <button
              type="submit"
              class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
              :disabled="updateMutation.isPending.value"
            >
              {{ updateMutation.isPending.value ? '저장 중…' : '저장' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-slate-300 px-4 py-2"
              @click="cancelEdit"
            >
              취소
            </button>
          </div>
        </form>
      </section>

      <div class="mt-6 grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
        <section
          class="rounded-2xl bg-white p-6 shadow-sm"
          aria-labelledby="job-description-heading"
        >
          <h3 id="job-description-heading" class="font-semibold">공고 본문</h3>
          <p class="mt-1 text-xs text-slate-500">
            출처:
            {{
              job.data.value.descriptionSource
                ? DESCRIPTION_SOURCE_LABELS[job.data.value.descriptionSource]
                : '미확인'
            }}
          </p>
          <pre
            v-if="job.data.value.descriptionText"
            class="mt-4 max-h-[40rem] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-4 text-sm"
            >{{ job.data.value.descriptionText }}</pre>
          <p v-else class="mt-4 text-sm text-slate-600">
            저장된 공고 본문이 없습니다. 편집에서 직접 입력해 주세요.
          </p>
        </section>

        <div class="space-y-5">
          <section class="rounded-2xl bg-white p-5 shadow-sm">
            <h3 class="font-semibold">공고 정보</h3>
            <dl class="mt-3 space-y-3 text-sm">
              <div>
                <dt class="font-medium text-slate-500">원본 URL</dt>
                <dd class="mt-1 break-all">
                  <a
                    class="text-indigo-700 underline"
                    :href="job.data.value.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ job.data.value.sourceUrl }}
                  </a>
                </dd>
              </div>
              <div>
                <dt class="font-medium text-slate-500">마감</dt>
                <dd class="mt-1">{{ formatJobInstant(job.data.value.deadlineAt) }}</dd>
                <dd class="text-xs text-slate-500">
                  {{ DEADLINE_SOURCE_LABELS[job.data.value.deadlineSource] }}
                </dd>
              </div>
              <div v-if="job.data.value.submittedAt">
                <dt class="font-medium text-slate-500">최초 서류 제출</dt>
                <dd class="mt-1">{{ formatJobInstant(job.data.value.submittedAt) }}</dd>
              </div>
              <div v-if="job.data.value.closedAt">
                <dt class="font-medium text-slate-500">마감 처리</dt>
                <dd class="mt-1">{{ formatJobInstant(job.data.value.closedAt) }}</dd>
                <dd v-if="job.data.value.closedReason" class="text-xs text-slate-500">
                  {{ CLOSED_REASON_LABELS[job.data.value.closedReason] }}
                </dd>
              </div>
              <div v-if="job.data.value.roleCategory">
                <dt class="font-medium text-slate-500">직무 분류</dt>
                <dd>{{ job.data.value.roleCategory }}</dd>
              </div>
              <div v-if="job.data.value.employmentType">
                <dt class="font-medium text-slate-500">고용 형태</dt>
                <dd>{{ job.data.value.employmentType }}</dd>
              </div>
              <div v-if="job.data.value.location">
                <dt class="font-medium text-slate-500">근무지</dt>
                <dd>{{ job.data.value.location }}</dd>
              </div>
            </dl>
          </section>

          <section class="rounded-2xl bg-white p-5 shadow-sm">
            <h3 class="font-semibold">업무 상태 변경</h3>
            <form class="mt-3" @submit.prevent="changeStatus">
              <label class="text-sm font-medium">
                상태
                <select
                  id="job-status-select"
                  v-model="selectedStatus"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                >
                  <option v-for="status in JOB_STATUSES" :key="status" :value="status">
                    {{ JOB_STATUS_LABELS[status] }}
                  </option>
                </select>
              </label>
              <button
                id="job-status-submit"
                type="submit"
                class="mt-3 rounded-lg bg-indigo-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                :disabled="
                  selectedStatus === job.data.value.status || statusMutation.isPending.value
                "
              >
                {{ statusMutation.isPending.value ? '변경 중…' : '상태 변경' }}
              </button>
            </form>
          </section>

          <section v-if="monitoredRunId" class="rounded-2xl bg-white p-5 shadow-sm">
            <h3 class="font-semibold">URL 추출 Agent Run</h3>
            <RouterLink
              class="mt-2 inline-block text-indigo-700 underline"
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
