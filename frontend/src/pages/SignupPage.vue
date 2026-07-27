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
const passwordVisible = ref(false)
const passwordConfirmVisible = ref(false)

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
      fieldErrors.value.email = '이미 가입된 이메일이에요. 로그인해 주세요.'
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
    <p class="page-eyebrow">나만의 지원 준비 시작</p>
    <h1 class="page-title">회원가입</h1>
    <p class="page-description">취업 준비를 한곳에서 시작할 계정을 만들어 보세요.</p>

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
        <div class="password-control">
          <input
            id="signup-password"
            v-model="form.password"
            class="control"
            :type="passwordVisible ? 'text' : 'password'"
            autocomplete="new-password"
            :aria-invalid="Boolean(fieldErrors.password)"
            :aria-describedby="
              fieldErrors.password
                ? 'signup-password-help signup-password-error'
                : 'signup-password-help'
            "
            :disabled="isSubmitting"
          />
          <button
            type="button"
            class="password-control__toggle"
            :aria-label="passwordVisible ? '비밀번호 숨기기' : '비밀번호 보기'"
            :aria-pressed="passwordVisible"
            :disabled="isSubmitting"
            @click="passwordVisible = !passwordVisible"
          >
            {{ passwordVisible ? '숨기기' : '보기' }}
          </button>
        </div>
        <p id="signup-password-help" class="field-help">
          다른 곳에서 사용하지 않는 비밀번호를 입력해 주세요.
        </p>
        <p v-if="fieldErrors.password" id="signup-password-error" class="field-error">
          {{ fieldErrors.password }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-passwordConfirm"> 비밀번호 확인 </label>
        <div class="password-control">
          <input
            id="signup-passwordConfirm"
            v-model="form.passwordConfirm"
            class="control"
            :type="passwordConfirmVisible ? 'text' : 'password'"
            autocomplete="new-password"
            :aria-invalid="Boolean(fieldErrors.passwordConfirm)"
            :aria-describedby="
              fieldErrors.passwordConfirm ? 'signup-passwordConfirm-error' : undefined
            "
            :disabled="isSubmitting"
          />
          <button
            type="button"
            class="password-control__toggle"
            :aria-label="passwordConfirmVisible ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'"
            :aria-pressed="passwordConfirmVisible"
            :disabled="isSubmitting"
            @click="passwordConfirmVisible = !passwordConfirmVisible"
          >
            {{ passwordConfirmVisible ? '숨기기' : '보기' }}
          </button>
        </div>
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
            <small>계정을 만들고 서비스를 제공하는 데 꼭 필요한 동의예요.</small>
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
            <small>이력서와 공고를 정리하려면 꼭 필요한 동의예요.</small>
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

.password-control {
  position: relative;
}

.password-control .control {
  padding-right: 4rem;
}

.password-control__toggle {
  position: absolute;
  top: 50%;
  right: 0.5rem;
  min-width: 2.75rem;
  min-height: 2.75rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-brand);
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 720;
  transform: translateY(-50%);
}

.password-control__toggle:hover:not(:disabled) {
  background: var(--color-brand-soft);
}
</style>
