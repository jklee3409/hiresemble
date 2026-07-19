<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { type SignupFormValues, validateSignupForm } from '@/features/auth/formValidation'
import { authErrorMessage, fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const form = reactive<SignupFormValues>({
  email: '',
  password: '',
  passwordConfirm: '',
  displayName: '',
  termsAgreed: false,
  aiConsent: false,
})
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const isSubmitting = ref(false)

async function submit(): Promise<void> {
  generalError.value = ''
  const validation = validateSignupForm(form)
  fieldErrors.value = validation.fieldErrors

  if (validation.data === null) {
    await focusFirstError()
    return
  }

  isSubmitting.value = true
  try {
    await authStore.signup({
      email: validation.data.email,
      password: validation.data.password,
      displayName: validation.data.displayName,
      termsAgreed: validation.data.termsAgreed,
      aiConsent: validation.data.aiConsent,
    })
    await router.replace({ name: 'onboarding' })
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (apiError.code === 'EMAIL_ALREADY_REGISTERED' && fieldErrors.value.email === undefined) {
      fieldErrors.value.email = '이미 가입된 이메일입니다.'
    }
    generalError.value = authErrorMessage(apiError)
    isSubmitting.value = false
    await focusFirstError()
  } finally {
    isSubmitting.value = false
  }
}

async function focusFirstError(): Promise<void> {
  await nextTick()
  const firstField = Object.keys(fieldErrors.value)[0]
  if (firstField === undefined) {
    return
  }

  document.getElementById(`signup-${firstField}`)?.focus()
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold">회원가입</h1>
    <p class="mt-2 text-sm text-slate-600">취업 준비를 위한 개인 계정을 만드세요.</p>

    <form class="mt-6 space-y-4" novalidate @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="signup-email">이메일</label>
        <input
          id="signup-email"
          v-model="form.email"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="email"
          autocomplete="email"
          autofocus
          :aria-invalid="Boolean(fieldErrors.email)"
          :aria-describedby="fieldErrors.email ? 'signup-email-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.email" id="signup-email-error" class="mt-1 text-sm text-red-700">
          {{ fieldErrors.email }}
        </p>
      </div>

      <div>
        <label class="block text-sm font-medium" for="signup-displayName">표시 이름</label>
        <input
          id="signup-displayName"
          v-model="form.displayName"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="text"
          autocomplete="name"
          :aria-invalid="Boolean(fieldErrors.displayName)"
          :aria-describedby="fieldErrors.displayName ? 'signup-displayName-error' : undefined"
          :disabled="isSubmitting"
        />
        <p
          v-if="fieldErrors.displayName"
          id="signup-displayName-error"
          class="mt-1 text-sm text-red-700"
        >
          {{ fieldErrors.displayName }}
        </p>
      </div>

      <div>
        <label class="block text-sm font-medium" for="signup-password">비밀번호</label>
        <input
          id="signup-password"
          v-model="form.password"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="password"
          autocomplete="new-password"
          :aria-invalid="Boolean(fieldErrors.password)"
          :aria-describedby="fieldErrors.password ? 'signup-password-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.password" id="signup-password-error" class="mt-1 text-sm text-red-700">
          {{ fieldErrors.password }}
        </p>
      </div>

      <div>
        <label class="block text-sm font-medium" for="signup-passwordConfirm">
          비밀번호 확인
        </label>
        <input
          id="signup-passwordConfirm"
          v-model="form.passwordConfirm"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="password"
          autocomplete="new-password"
          :aria-invalid="Boolean(fieldErrors.passwordConfirm)"
          :aria-describedby="
            fieldErrors.passwordConfirm ? 'signup-passwordConfirm-error' : undefined
          "
          :disabled="isSubmitting"
        />
        <p
          v-if="fieldErrors.passwordConfirm"
          id="signup-passwordConfirm-error"
          class="mt-1 text-sm text-red-700"
        >
          {{ fieldErrors.passwordConfirm }}
        </p>
      </div>

      <div>
        <label class="flex items-start gap-2" for="signup-termsAgreed">
          <input
            id="signup-termsAgreed"
            v-model="form.termsAgreed"
            class="mt-1"
            type="checkbox"
            :aria-invalid="Boolean(fieldErrors.termsAgreed)"
            :aria-describedby="fieldErrors.termsAgreed ? 'signup-termsAgreed-error' : undefined"
            :disabled="isSubmitting"
          />
          <span class="text-sm">이용약관과 개인정보 처리에 동의합니다.</span>
        </label>
        <p
          v-if="fieldErrors.termsAgreed"
          id="signup-termsAgreed-error"
          class="mt-1 text-sm text-red-700"
        >
          {{ fieldErrors.termsAgreed }}
        </p>
      </div>

      <div>
        <label class="flex items-start gap-2" for="signup-aiConsent">
          <input
            id="signup-aiConsent"
            v-model="form.aiConsent"
            class="mt-1"
            type="checkbox"
            :aria-invalid="Boolean(fieldErrors.aiConsent)"
            :aria-describedby="fieldErrors.aiConsent ? 'signup-aiConsent-error' : undefined"
            :disabled="isSubmitting"
          />
          <span class="text-sm">취업 준비 지원을 위한 AI 처리에 동의합니다.</span>
        </label>
        <p
          v-if="fieldErrors.aiConsent"
          id="signup-aiConsent-error"
          class="mt-1 text-sm text-red-700"
        >
          {{ fieldErrors.aiConsent }}
        </p>
      </div>

      <p v-if="generalError" class="text-sm text-red-700" role="alert">
        {{ generalError }}
      </p>

      <button
        class="w-full rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
        type="submit"
        :disabled="isSubmitting"
      >
        {{ isSubmitting ? '가입 중…' : '가입하기' }}
      </button>
    </form>

    <p class="mt-5 text-center text-sm">
      이미 계정이 있나요?
      <RouterLink class="font-semibold text-indigo-700" to="/login">로그인</RouterLink>
    </p>
  </div>
</template>
