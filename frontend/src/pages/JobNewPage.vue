<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useCreateJobMutation } from '@/features/jobs/queries'
import { type JobCreateForm, validateJobCreateForm } from '@/features/jobs/validation'
import { createJobIdempotencyKey } from '@/shared/api/jobApi'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
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
  <section class="mx-auto max-w-3xl" aria-labelledby="job-new-heading">
    <RouterLink class="text-sm font-semibold text-indigo-700" :to="{ name: 'jobs' }">
      ← 공고 목록
    </RouterLink>
    <h2 id="job-new-heading" class="mt-4 text-2xl font-bold">채용 공고 등록</h2>
    <p class="mt-2 text-slate-600">
      URL은 필수입니다. 본문을 직접 입력하면 URL 추출 작업 없이 즉시 사용할 수 있습니다.
    </p>

    <form
      id="job-create-form"
      class="mt-6 space-y-5 rounded-2xl bg-white p-6 shadow-sm"
      novalidate
      @submit.prevent="submit"
    >
      <label class="block text-sm font-medium">
        공고 URL <span aria-hidden="true">*</span>
        <input
          id="job-source-url"
          v-model="form.sourceUrl"
          type="url"
          required
          maxlength="2000"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          aria-describedby="job-source-url-error"
        />
        <span
          v-if="fieldErrors.sourceUrl"
          id="job-source-url-error"
          class="mt-1 block text-red-700"
          role="alert"
        >
          {{ fieldErrors.sourceUrl }}
        </span>
      </label>
      <div class="grid gap-4 md:grid-cols-2">
        <label class="text-sm font-medium">
          회사명 (선택)
          <input
            id="job-company-name"
            v-model="form.companyName"
            maxlength="200"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          />
          <span v-if="fieldErrors.companyName" class="mt-1 block text-red-700" role="alert">
            {{ fieldErrors.companyName }}
          </span>
        </label>
        <label class="text-sm font-medium">
          직무명 (선택)
          <input
            id="job-position-name"
            v-model="form.positionName"
            maxlength="300"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          />
          <span v-if="fieldErrors.positionName" class="mt-1 block text-red-700" role="alert">
            {{ fieldErrors.positionName }}
          </span>
        </label>
      </div>
      <label class="block text-sm font-medium">
        공고 본문 직접 입력 (선택)
        <textarea
          id="job-description"
          v-model="form.descriptionText"
          maxlength="200000"
          class="mt-1 min-h-64 w-full rounded-lg border border-slate-300 p-3"
        />
        <span v-if="fieldErrors.descriptionText" class="mt-1 block text-red-700" role="alert">
          {{ fieldErrors.descriptionText }}
        </span>
      </label>
      <label class="block text-sm font-medium">
        마감 일시 (선택)
        <input
          id="job-deadline"
          v-model="form.deadlineAt"
          type="datetime-local"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
        />
        <span v-if="fieldErrors.deadlineAt" class="mt-1 block text-red-700" role="alert">
          {{ fieldErrors.deadlineAt }}
        </span>
      </label>

      <p v-if="actionError" class="rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">
        {{ actionError }}
      </p>
      <div class="flex flex-wrap gap-3">
        <button
          id="job-create-submit"
          type="submit"
          class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
          :disabled="createMutation.isPending.value || submitting"
        >
          {{ createMutation.isPending.value || submitting ? '등록 접수 중…' : '공고 등록' }}
        </button>
        <button
          type="button"
          class="rounded-lg border border-slate-300 px-4 py-2 font-semibold"
          :disabled="createMutation.isPending.value || submitting"
          @click="reset"
        >
          입력 초기화
        </button>
      </div>
      <p class="text-xs text-slate-500">
        요청이 실패해 다시 시도할 때는 같은 멱등성 키를 유지하며, 성공 또는 입력 초기화 때만 새
        요청으로 전환합니다.
      </p>
    </form>
  </section>
</template>
