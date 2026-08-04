<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useCreateJobMutation } from '@/features/jobs/queries'
import { type JobCreateForm, validateJobCreateForm } from '@/features/jobs/validation'
import { createJobIdempotencyKey } from '@/shared/api/jobApi'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const createMutation = useCreateJobMutation(userId)
const form = reactive<JobCreateForm>(emptyForm())
const deadline = reactive({ date: '', period: 'PM' as 'AM' | 'PM', time: '11:30' })
const deadlineTimes = [
  '12:00',
  '12:30',
  ...Array.from({ length: 11 }, (_, index) => {
    const hour = String(index + 1).padStart(2, '0')
    return [`${hour}:00`, `${hour}:30`]
  }).flat(),
] as const
const fieldErrors = ref<Record<string, string>>({})
const actionError = ref('')
let idempotencyKey = ''
let submitting = false

async function submit(): Promise<void> {
  if (submitting) return
  actionError.value = ''
  form.deadlineAt = deadlineToLocalDateTime()
  const validation = validateJobCreateForm(form)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) {
    if (fieldErrors.value.descriptionText) {
      document.querySelector('.job-create-section--optional')?.setAttribute('open', '')
    }
    await nextTick()
    focusFirstInvalidControl()
    return
  }

  submitting = true
  try {
    const result = await createMutation.mutateAsync({
      request: validation.data,
      idempotencyKey: idempotencyKey || (idempotencyKey = createJobIdempotencyKey('create')),
    })
    const query =
      result.httpStatus === 202
        ? { created: 'async', run: result.job.agentRunId }
        : { created: 'manual' }
    idempotencyKey = ''
    await router.push({
      name: 'job-overview',
      params: { jobId: result.job.jobId },
      query,
    })
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    actionError.value =
      apiError.code === 'DUPLICATE_JOB_URL'
        ? '이미 등록한 공고 링크예요. 관심 공고에서 기존 항목을 확인해 주세요.'
        : apiError.message
  } finally {
    submitting = false
  }
}

function reset(): void {
  Object.assign(form, emptyForm())
  Object.assign(deadline, { date: '', period: 'PM', time: '11:30' })
  fieldErrors.value = {}
  actionError.value = ''
  idempotencyKey = ''
}

function deadlineToLocalDateTime(): string {
  if (deadline.date === '') return ''
  const [hourText, minute] = deadline.time.split(':')
  const hour12 = Number(hourText)
  if (minute === undefined || hour12 < 1 || hour12 > 12) return 'invalid'
  const hour24 = (hour12 % 12) + (deadline.period === 'PM' ? 12 : 0)
  return `${deadline.date}T${String(hour24).padStart(2, '0')}:${minute}`
}

function emptyForm(): JobCreateForm {
  return {
    sourceUrl: '',
    companyName: '',
    positionName: '',
    descriptionText: '',
    deadlineAt: '',
  }
}
</script>

<template>
  <section class="job-new app-page app-page--narrow" aria-labelledby="job-new-heading">
    <RouterLink class="back-link" :to="{ name: 'jobs' }">
      <AppIcon name="arrow-left" />
      공고 목록
    </RouterLink>
    <PageHeader
      heading-id="job-new-heading"
      title="관심 공고 추가"
      description="공고 링크를 넣으면 본문을 읽고, 내 경험과 비교하는 분석까지 자동으로 이어져요."
      variant="editor"
    />

    <form id="job-create-form" class="job-create-form" novalidate @submit.prevent="submit">
      <section
        class="job-create-section job-create-section--primary"
        aria-labelledby="job-url-title"
      >
        <div class="job-create-section__heading">
          <span class="job-create-section__index" aria-hidden="true">1</span>
          <div>
            <h2 id="job-url-title" class="section-title">공고 링크</h2>
            <p>채용 사이트의 공고 주소를 그대로 붙여 넣어 주세요.</p>
          </div>
        </div>
        <label class="field">
          <span class="field__label">공고 링크 <span aria-hidden="true">*</span></span>
          <input
            id="job-source-url"
            v-model="form.sourceUrl"
            type="url"
            required
            maxlength="2000"
            class="control"
            :aria-invalid="Boolean(fieldErrors.sourceUrl)"
            aria-describedby="job-source-url-help job-source-url-error"
          />
          <span id="job-source-url-help" class="field__help"
            >등록 후 공고 내용을 자동으로 읽고 기본 분석을 시작해요.</span
          >
          <span
            v-if="fieldErrors.sourceUrl"
            id="job-source-url-error"
            class="inline-error"
            role="alert"
          >
            {{ fieldErrors.sourceUrl }}
          </span>
        </label>
      </section>

      <section class="job-create-section" aria-labelledby="job-detail-title">
        <div class="job-create-section__heading">
          <span class="job-create-section__index" aria-hidden="true">2</span>
          <div>
            <h2 id="job-detail-title" class="section-title">알고 있는 정보</h2>
            <p>회사명이나 직무를 알고 있다면 함께 적어 주세요. 비워 두어도 괜찮아요.</p>
          </div>
        </div>
        <div class="job-create-grid">
          <label class="field">
            <span class="field__label">회사명 <span class="field__optional">(선택)</span></span>
            <input
              id="job-company-name"
              v-model="form.companyName"
              maxlength="200"
              class="control"
              :aria-invalid="Boolean(fieldErrors.companyName)"
            />
            <span v-if="fieldErrors.companyName" class="inline-error" role="alert">
              {{ fieldErrors.companyName }}
            </span>
          </label>
          <label class="field">
            <span class="field__label">직무명 <span class="field__optional">(선택)</span></span>
            <input
              id="job-position-name"
              v-model="form.positionName"
              maxlength="300"
              class="control"
              :aria-invalid="Boolean(fieldErrors.positionName)"
            />
            <span v-if="fieldErrors.positionName" class="inline-error" role="alert">
              {{ fieldErrors.positionName }}
            </span>
          </label>
          <div class="field job-deadline-field">
            <span id="job-deadline-label" class="field__label">
              마감 일시 <span class="field__optional">(선택)</span>
            </span>
            <div class="job-deadline-controls" role="group" aria-labelledby="job-deadline-label">
              <input
                id="job-deadline-date"
                v-model="deadline.date"
                type="date"
                class="control"
                aria-label="마감 날짜"
                :aria-invalid="Boolean(fieldErrors.deadlineAt)"
                :aria-describedby="
                  fieldErrors.deadlineAt
                    ? 'job-deadline-help job-deadline-error'
                    : 'job-deadline-help'
                "
              />
              <select
                id="job-deadline-period"
                v-model="deadline.period"
                class="control"
                aria-label="마감 오전 또는 오후"
                :aria-invalid="Boolean(fieldErrors.deadlineAt)"
                :aria-describedby="
                  fieldErrors.deadlineAt
                    ? 'job-deadline-help job-deadline-error'
                    : 'job-deadline-help'
                "
              >
                <option value="AM">오전</option>
                <option value="PM">오후</option>
              </select>
              <select
                id="job-deadline-time"
                v-model="deadline.time"
                class="control"
                aria-label="마감 시간"
                :aria-invalid="Boolean(fieldErrors.deadlineAt)"
                :aria-describedby="
                  fieldErrors.deadlineAt
                    ? 'job-deadline-help job-deadline-error'
                    : 'job-deadline-help'
                "
              >
                <option v-for="time in deadlineTimes" :key="time" :value="time">
                  {{ time }}
                </option>
              </select>
            </div>
            <span id="job-deadline-help" class="field__help">
              시간은 30분 단위로 선택할 수 있어요.
            </span>
            <span
              v-if="fieldErrors.deadlineAt"
              id="job-deadline-error"
              class="inline-error"
              role="alert"
            >
              {{ fieldErrors.deadlineAt }}
            </span>
          </div>
        </div>
      </section>

      <details class="job-create-section job-create-section--optional">
        <summary class="job-create-section__heading">
          <span class="job-create-section__index" aria-hidden="true">또는</span>
          <div>
            <h2 class="section-title">공고 본문 직접 입력</h2>
            <p>링크에서 내용을 읽기 어렵거나 본문을 이미 가지고 있을 때 펼쳐 주세요.</p>
          </div>
        </summary>
        <label class="field job-create-section__optional-body">
          <span class="field__label">공고 본문 <span class="field__optional">(선택)</span></span>
          <textarea
            id="job-description"
            v-model="form.descriptionText"
            maxlength="200000"
            class="control job-create-form__description"
            :aria-invalid="Boolean(fieldErrors.descriptionText)"
          />
          <span v-if="fieldErrors.descriptionText" class="inline-error" role="alert">
            {{ fieldErrors.descriptionText }}
          </span>
        </label>
      </details>

      <p v-if="actionError" class="alert alert--danger" role="alert">
        {{ actionError }}
      </p>
      <div class="job-create-form__actions">
        <button
          id="job-create-submit"
          type="submit"
          class="button button--primary"
          :disabled="createMutation.isPending.value || submitting"
        >
          {{
            createMutation.isPending.value || submitting
              ? '공고를 등록하는 중…'
              : '등록하고 분석 시작'
          }}
        </button>
        <button
          type="button"
          class="button button--secondary"
          :disabled="createMutation.isPending.value || submitting"
          @click="reset"
        >
          모두 지우기
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.job-create-form {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-6);
}

.job-create-section {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.job-create-section--primary {
  border-color: var(--color-brand-border);
  box-shadow: inset 3px 0 var(--color-brand);
}

.job-create-section__heading {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.job-create-section--optional > summary {
  margin-bottom: 0;
  cursor: pointer;
  list-style: none;
}

.job-create-section--optional > summary::-webkit-details-marker {
  display: none;
}

.job-create-section--optional > summary::after {
  margin-left: auto;
  color: var(--color-brand);
  content: '+';
  font-size: 1.25rem;
  font-weight: 700;
}

.job-create-section--optional[open] > summary {
  margin-bottom: var(--space-5);
}

.job-create-section--optional[open] > summary::after {
  content: '−';
}

.job-create-section__optional-body {
  padding-top: var(--space-2);
}

.job-create-section__heading p {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.job-create-section__index {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.job-create-section--optional .job-create-section__index {
  width: auto;
  min-width: 2.75rem;
  padding-inline: 0.5rem;
}

.job-create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.job-deadline-controls {
  display: grid;
  grid-template-columns: minmax(9.5rem, 1fr) minmax(5.5rem, 0.45fr) minmax(6rem, 0.55fr);
  gap: 0.5rem;
}

.job-deadline-field {
  grid-column: 1 / -1;
}

.job-create-form__description {
  min-height: 17rem;
  line-height: 1.7;
}

.job-create-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding-top: var(--space-2);
}

@media (max-width: 40rem) {
  .job-create-grid {
    grid-template-columns: 1fr;
  }

  .job-deadline-controls {
    grid-template-columns: 1fr 1fr;
  }

  .job-deadline-controls > :first-child {
    grid-column: 1 / -1;
  }
}
</style>
