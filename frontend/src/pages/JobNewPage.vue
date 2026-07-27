<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useCreateJobMutation } from '@/features/jobs/queries'
import { type JobCreateForm, validateJobCreateForm } from '@/features/jobs/validation'
import { createJobIdempotencyKey } from '@/shared/api/jobApi'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const createMutation = useCreateJobMutation(userId)
const form = reactive<JobCreateForm>(emptyForm())
const fieldErrors = ref<Record<string, string>>({})
const actionError = ref('')
let idempotencyKey = ''
let submitting = false

async function submit(): Promise<void> {
  if (submitting) return
  actionError.value = ''
  const validation = validateJobCreateForm(form)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) return

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
        ? '이미 등록한 공고 URL입니다. 공고 목록에서 기존 항목을 확인해 주세요.'
        : apiError.message
  } finally {
    submitting = false
  }
}

function reset(): void {
  Object.assign(form, emptyForm())
  fieldErrors.value = {}
  actionError.value = ''
  idempotencyKey = ''
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
      title="채용 공고 등록"
      description="공고 URL을 기준으로 등록합니다. 본문 직접 입력은 URL에서 내용을 가져올 수 없을 때 사용할 수 있는 대체 경로입니다."
      eyebrow="New job"
    />

    <form id="job-create-form" class="job-create-form" novalidate @submit.prevent="submit">
      <section
        class="job-create-section job-create-section--primary"
        aria-labelledby="job-url-title"
      >
        <div class="job-create-section__heading">
          <span class="job-create-section__index" aria-hidden="true">1</span>
          <div>
            <h3 id="job-url-title" class="section-title">공고 URL</h3>
            <p>URL은 필수이며, 본문이 없으면 URL 추출 작업이 접수됩니다.</p>
          </div>
        </div>
        <label class="field">
          <span class="field__label">공고 URL <span aria-hidden="true">*</span></span>
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
            >HTTP 또는 HTTPS 주소를 입력하세요.</span
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
            <h3 id="job-detail-title" class="section-title">기본 정보</h3>
            <p>이미 알고 있는 정보만 입력해도 됩니다.</p>
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
          <label class="field">
            <span class="field__label">마감 일시 <span class="field__optional">(선택)</span></span>
            <input
              id="job-deadline"
              v-model="form.deadlineAt"
              type="datetime-local"
              class="control"
              :aria-invalid="Boolean(fieldErrors.deadlineAt)"
            />
            <span v-if="fieldErrors.deadlineAt" class="inline-error" role="alert">
              {{ fieldErrors.deadlineAt }}
            </span>
          </label>
        </div>
      </section>

      <section class="job-create-section" aria-labelledby="job-manual-title">
        <div class="job-create-section__heading">
          <span class="job-create-section__index" aria-hidden="true">3</span>
          <div>
            <h3 id="job-manual-title" class="section-title">본문 직접 입력</h3>
            <p>직접 입력하면 URL 추출 작업을 만들지 않고 바로 사용할 수 있습니다.</p>
          </div>
        </div>
        <label class="field">
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
      </section>

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
          {{ createMutation.isPending.value || submitting ? '등록 접수 중…' : '공고 등록' }}
        </button>
        <button
          type="button"
          class="button button--secondary"
          :disabled="createMutation.isPending.value || submitting"
          @click="reset"
        >
          입력 초기화
        </button>
      </div>
      <p class="job-create-form__idempotency">
        요청이 실패해 다시 시도할 때는 같은 멱등성 키를 유지하며, 성공 또는 입력 초기화 때만 새
        요청으로 전환합니다.
      </p>
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

.job-create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
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

.job-create-form__idempotency {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

@media (max-width: 40rem) {
  .job-create-grid {
    grid-template-columns: 1fr;
  }
}
</style>
