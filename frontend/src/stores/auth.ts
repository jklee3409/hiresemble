import type { Pinia } from 'pinia'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as authApi from '@/shared/api/authApi'
import type {
  AuthSessionDto,
  CurrentUserDto,
  LoginRequest,
  SignupRequest,
} from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import { apiClient } from '@/shared/api/http'
import { sessionCleanup } from '@/shared/session/sessionCleanup'

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous'

export const useAuthStore = defineStore('auth', () => {
  const status = ref<AuthStatus>('unknown')
  const currentUser = ref<CurrentUserDto | null>(null)
  let bootstrapPromise: Promise<void> | null = null

  const isAuthenticated = computed(
    () => status.value === 'authenticated' && currentUser.value !== null,
  )

  function resetState(nextStatus: AuthStatus): void {
    status.value = nextStatus
    currentUser.value = null
    apiClient.clearCsrfToken()
  }

  async function clearUserBoundary(nextStatus: AuthStatus = 'anonymous'): Promise<void> {
    const previousUserId = currentUser.value?.id ?? null

    await sessionCleanup.cleanup({
      userId: previousUserId,
      resetAuthStore: () => resetState(nextStatus),
    })
  }

  async function establishSession(session: AuthSessionDto): Promise<void> {
    const previousUserId = currentUser.value?.id
    if (previousUserId !== undefined && previousUserId !== session.user.id) {
      await clearUserBoundary()
    }

    apiClient.setCsrfToken(session.csrf)
    currentUser.value = session.user
    status.value = 'authenticated'
  }

  async function bootstrap(force = false): Promise<void> {
    if (!force && status.value !== 'unknown') {
      return
    }

    if (bootstrapPromise !== null) {
      return bootstrapPromise
    }

    bootstrapPromise = (async () => {
      try {
        const user = await authApi.getCurrentUser()
        const previousUserId = currentUser.value?.id
        if (previousUserId !== undefined && previousUserId !== user.id) {
          await clearUserBoundary()
        }
        currentUser.value = user
        status.value = 'authenticated'
      } catch (error) {
        const apiError = normalizeApiError(error)
        if (apiError.status === 401 || apiError.code === 'AUTHENTICATION_REQUIRED') {
          await handleUnauthorized()
          return
        }

        resetState('anonymous')
      }
    })()

    try {
      await bootstrapPromise
    } finally {
      bootstrapPromise = null
    }
  }

  async function signup(request: SignupRequest): Promise<CurrentUserDto> {
    const session = await authApi.signup(request)
    await establishSession(session)
    return session.user
  }

  async function login(request: LoginRequest): Promise<CurrentUserDto> {
    const session = await authApi.login(request)
    await establishSession(session)
    return session.user
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
      await clearUserBoundary()
    } catch (error) {
      const apiError = normalizeApiError(error)
      if (apiError.status === 401) {
        await handleUnauthorized()
      }
      throw apiError
    }
  }

  async function handleUnauthorized(): Promise<void> {
    if (status.value === 'anonymous' && currentUser.value === null) {
      apiClient.clearCsrfToken()
      return
    }

    await clearUserBoundary()
  }

  return {
    status,
    currentUser,
    isAuthenticated,
    bootstrap,
    signup,
    login,
    logout,
    handleUnauthorized,
  }
})

export function installUnauthorizedReset(pinia: Pinia): () => void {
  return apiClient.setUnauthorizedHandler(async () => {
    const authStore = useAuthStore(pinia)
    await authStore.handleUnauthorized()
  })
}
