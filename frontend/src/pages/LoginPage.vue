<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { type LoginFormValues, validateLoginForm } from '@/features/auth/formValidation'
import { authErrorMessage, fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import { safeReturnTo } from '@/router/returnTo'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const form = reactive<LoginFormValues>({ email: '', password: '' })
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const isSubmitting = ref(false)
const passwordVisible = ref(false)

async function submit(): Promise<void> {
  generalError.value = ''
  const validation = validateLoginForm(form)
  fieldErrors.value = validation.fieldErrors

  if (validation.data === null) {
    await focusFirstError()
    return
  }

  isSubmitting.value = true
  try {
    await authStore.login(validation.data)
    await router.replace(safeReturnTo(route.query.returnTo) ?? { name: 'dashboard' })
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
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

  document.getElementById(`login-${firstField}`)?.focus()
}
</script>

<template>
  <div class="auth-page">
    <p class="page-eyebrow">다시 만나서 반가워요</p>
    <h1 class="page-title">로그인</h1>
    <p class="page-description">로그인하고 준비하던 경험 정보와 관심 공고를 이어서 확인하세요.</p>

    <form class="auth-page__form" novalidate :aria-busy="isSubmitting" @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="login-email">이메일</label>
        <input
          id="login-email"
          v-model="form.email"
          class="control"
          type="email"
          autocomplete="email"
          autofocus
          :aria-invalid="Boolean(fieldErrors.email)"
          :aria-describedby="fieldErrors.email ? 'login-email-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.email" id="login-email-error" class="field-error">
          {{ fieldErrors.email }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="login-password">비밀번호</label>
        <div class="password-control">
          <input
            id="login-password"
            v-model="form.password"
            class="control"
            :type="passwordVisible ? 'text' : 'password'"
            autocomplete="current-password"
            :aria-invalid="Boolean(fieldErrors.password)"
            :aria-describedby="fieldErrors.password ? 'login-password-error' : undefined"
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
        <p v-if="fieldErrors.password" id="login-password-error" class="field-error">
          {{ fieldErrors.password }}
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
        {{ isSubmitting ? '로그인 중…' : '로그인' }}
      </button>
    </form>

    <p class="auth-page__alternate">
      처음이신가요?
      <RouterLink class="text-link" to="/signup">회원가입</RouterLink>
    </p>
  </div>
</template>

<style scoped>
.auth-page__form {
  display: grid;
  gap: 1.125rem;
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
