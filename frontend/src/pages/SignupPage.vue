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
  <div class="auth-page">
    <p class="page-eyebrow">Create account</p>
    <h1 class="page-title">회원가입</h1>
    <p class="page-description">취업 준비 자료와 지원 과정을 관리할 개인 계정을 만드세요.</p>

    <form class="auth-page__form" novalidate :aria-busy="isSubmitting" @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="signup-email">이메일</label>
        <input
          id="signup-email"
          v-model="form.email"
          class="control"
          type="email"
          autocomplete="email"
          autofocus
          :aria-invalid="Boolean(fieldErrors.email)"
          :aria-describedby="fieldErrors.email ? 'signup-email-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.email" id="signup-email-error" class="field-error">
          {{ fieldErrors.email }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-displayName">표시 이름</label>
        <input
          id="signup-displayName"
          v-model="form.displayName"
          class="control"
          type="text"
          autocomplete="name"
          :aria-invalid="Boolean(fieldErrors.displayName)"
          :aria-describedby="fieldErrors.displayName ? 'signup-displayName-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.displayName" id="signup-displayName-error" class="field-error">
          {{ fieldErrors.displayName }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-password">비밀번호</label>
        <input
          id="signup-password"
          v-model="form.password"
          class="control"
          type="password"
          autocomplete="new-password"
          :aria-invalid="Boolean(fieldErrors.password)"
          :aria-describedby="
            fieldErrors.password
              ? 'signup-password-help signup-password-error'
              : 'signup-password-help'
          "
          :disabled="isSubmitting"
        />
        <p id="signup-password-help" class="field-help">UTF-8 기준 10~72바이트로 입력해 주세요.</p>
        <p v-if="fieldErrors.password" id="signup-password-error" class="field-error">
          {{ fieldErrors.password }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-passwordConfirm"> 비밀번호 확인 </label>
        <input
          id="signup-passwordConfirm"
          v-model="form.passwordConfirm"
          class="control"
          type="password"
          autocomplete="new-password"
          :aria-invalid="Boolean(fieldErrors.passwordConfirm)"
          :aria-describedby="
            fieldErrors.passwordConfirm ? 'signup-passwordConfirm-error' : undefined
          "
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.passwordConfirm" id="signup-passwordConfirm-error" class="field-error">
          {{ fieldErrors.passwordConfirm }}
        </p>
      </div>

      <div class="consent-option">
        <label class="consent-option__label" for="signup-termsAgreed">
          <input
            id="signup-termsAgreed"
            v-model="form.termsAgreed"
            class="checkbox-control"
            type="checkbox"
            :aria-invalid="Boolean(fieldErrors.termsAgreed)"
            :aria-describedby="fieldErrors.termsAgreed ? 'signup-termsAgreed-error' : undefined"
            :disabled="isSubmitting"
          />
          <span>
            <strong>이용약관·개인정보 처리 동의</strong>
            <small>계정 생성과 서비스 제공에 필요한 동의입니다.</small>
          </span>
        </label>
        <p
          v-if="fieldErrors.termsAgreed"
          id="signup-termsAgreed-error"
          class="field-error consent-option__error"
        >
          {{ fieldErrors.termsAgreed }}
        </p>
      </div>

      <div class="consent-option">
        <label class="consent-option__label" for="signup-aiConsent">
          <input
            id="signup-aiConsent"
            v-model="form.aiConsent"
            class="checkbox-control"
            type="checkbox"
            :aria-invalid="Boolean(fieldErrors.aiConsent)"
            :aria-describedby="fieldErrors.aiConsent ? 'signup-aiConsent-error' : undefined"
            :disabled="isSubmitting"
          />
          <span>
            <strong>취업 준비 지원을 위한 AI 처리 동의</strong>
            <small>문서와 공고 처리 등 요청한 작업에 필요한 동의입니다.</small>
          </span>
        </label>
        <p
          v-if="fieldErrors.aiConsent"
          id="signup-aiConsent-error"
          class="field-error consent-option__error"
        >
          {{ fieldErrors.aiConsent }}
        </p>
      </div>

      <p v-if="generalError" class="alert alert--danger" role="alert">
        {{ generalError }}
      </p>

      <button
        class="button button--primary auth-page__submit"
        type="submit"
        :disabled="isSubmitting"
      >
        <span v-if="isSubmitting" class="button-spinner" aria-hidden="true" />
        {{ isSubmitting ? '가입 중…' : '가입하기' }}
      </button>
    </form>

    <p class="auth-page__alternate">
      이미 계정이 있나요?
      <RouterLink class="text-link" to="/login">로그인</RouterLink>
    </p>
  </div>
</template>

<style scoped>
.auth-page__form {
  display: grid;
  gap: 1rem;
  margin-top: 1.75rem;
}

.auth-page__submit {
  width: 100%;
  margin-top: 0.25rem;
}

.auth-page__alternate {
  margin: 1.5rem 0 0;
  color: var(--color-muted);
  font-size: 0.875rem;
  text-align: center;
}

.consent-option {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: 0.75rem;
}

.consent-option__label {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.consent-option__label strong,
.consent-option__label small {
  display: block;
}

.consent-option__label strong {
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
}

.consent-option__label small {
  margin-top: 0.125rem;
  color: var(--color-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}

.consent-option__error {
  margin: 0.5rem 0 0 1.75rem;
}
</style>
