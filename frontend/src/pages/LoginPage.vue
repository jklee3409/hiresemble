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
  <div>
    <h1 class="text-2xl font-bold">로그인</h1>
    <p class="mt-2 text-sm text-slate-600">내 취업 준비 공간으로 이동합니다.</p>

    <form class="mt-6 space-y-4" novalidate @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="login-email">이메일</label>
        <input
          id="login-email"
          v-model="form.email"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="email"
          autocomplete="email"
          autofocus
          :aria-invalid="Boolean(fieldErrors.email)"
          :aria-describedby="fieldErrors.email ? 'login-email-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.email" id="login-email-error" class="mt-1 text-sm text-red-700">
          {{ fieldErrors.email }}
        </p>
      </div>

      <div>
        <label class="block text-sm font-medium" for="login-password">비밀번호</label>
        <input
          id="login-password"
          v-model="form.password"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          type="password"
          autocomplete="current-password"
          :aria-invalid="Boolean(fieldErrors.password)"
          :aria-describedby="fieldErrors.password ? 'login-password-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.password" id="login-password-error" class="mt-1 text-sm text-red-700">
          {{ fieldErrors.password }}
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
        {{ isSubmitting ? '로그인 중…' : '로그인' }}
      </button>
    </form>

    <p class="mt-5 text-center text-sm">
      처음이신가요?
      <RouterLink class="font-semibold text-indigo-700" to="/signup">회원가입</RouterLink>
    </p>
  </div>
</template>
