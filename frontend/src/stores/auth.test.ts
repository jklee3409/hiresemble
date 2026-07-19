import { QueryClient } from '@tanstack/vue-query'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { queryClient } from '@/app/queryClient'
import * as authApi from '@/shared/api/authApi'
import type { AuthSessionDto, ErrorResponseDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import { sessionCleanup } from '@/shared/session/sessionCleanup'

import { useAuthStore } from './auth'

vi.mock('@/shared/api/authApi', () => ({
  getCurrentUser: vi.fn(),
  initializeCsrf: vi.fn(),
  signup: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    queryClient.clear()
    window.sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('bootstraps unknown state to authenticated or anonymous', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValueOnce(session('user-1').user)
    const authenticatedStore = useAuthStore()

    expect(authenticatedStore.status).toBe('unknown')
    await authenticatedStore.bootstrap()
    expect(authenticatedStore.status).toBe('authenticated')
    expect(authenticatedStore.currentUser?.id).toBe('user-1')

    setActivePinia(createPinia())
    vi.mocked(authApi.getCurrentUser).mockRejectedValueOnce(authenticationRequired())
    const anonymousStore = useAuthStore()

    await anonymousStore.bootstrap()
    expect(anonymousStore.status).toBe('anonymous')
    expect(anonymousStore.currentUser).toBeNull()
  })

  it('clears streams, query state, stores, and drafts after logout', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    vi.mocked(authApi.logout).mockResolvedValueOnce()
    await store.login({ email: 'one@example.com', password: 'password-123' })

    queryClient.setQueryData(['user', 'user-1', 'profile'], { displayName: 'One' })
    window.sessionStorage.setItem('1/user-1/cover/id/question/version', '{}')
    const close = vi.fn()
    const reset = vi.fn()
    sessionCleanup.registerEventSource({ close })
    const unregisterReset = sessionCleanup.registerStoreReset({ reset })

    try {
      await store.logout()
    } finally {
      unregisterReset()
    }

    expect(close).toHaveBeenCalledOnce()
    expect(reset).toHaveBeenCalledOnce()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    expect(window.sessionStorage.getItem('1/user-1/cover/id/question/version')).toBeNull()
    expect(store.status).toBe('anonymous')
    expect(store.currentUser).toBeNull()
  })

  it('resets the user boundary when logout receives a 401', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    vi.mocked(authApi.logout).mockRejectedValueOnce(authenticationRequired())
    await store.login({ email: 'one@example.com', password: 'password-123' })
    queryClient.setQueryData(['user', 'user-1', 'profile'], { displayName: 'One' })

    await expect(store.logout()).rejects.toMatchObject({ status: 401 })

    expect(store.status).toBe('anonymous')
    expect(store.currentUser).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('isolates cache and drafts when a different user authenticates', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login)
      .mockResolvedValueOnce(session('user-1'))
      .mockResolvedValueOnce(session('user-2'))

    await store.login({ email: 'one@example.com', password: 'password-123' })
    queryClient.setQueryData(['user', 'user-1', 'profile'], { displayName: 'One' })
    window.sessionStorage.setItem('1/user-1/cover/id/question/version', 'one')
    window.sessionStorage.setItem('1/user-2/cover/id/question/version', 'two')

    await store.login({ email: 'two@example.com', password: 'password-123' })

    expect(store.currentUser?.id).toBe('user-2')
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    expect(window.sessionStorage.getItem('1/user-1/cover/id/question/version')).toBeNull()
    expect(window.sessionStorage.getItem('1/user-2/cover/id/question/version')).toBe('two')
  })

  it('keeps mutation retries disabled in the application QueryClient', () => {
    const defaults = new QueryClient().getDefaultOptions()
    const applicationDefaults = queryClient.getDefaultOptions()

    expect(defaults.mutations?.retry).toBeUndefined()
    expect(applicationDefaults.mutations?.retry).toBe(false)
  })
})

function session(id: string): AuthSessionDto {
  return {
    user: {
      id,
      email: `${id}@example.com`,
      displayName: id,
    },
    csrf: {
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: `csrf-${id}`,
    },
  }
}

function authenticationRequired(): ApiClientError {
  const response: ErrorResponseDto = {
    timestamp: '2026-07-19T00:00:00Z',
    status: 401,
    code: 'AUTHENTICATION_REQUIRED',
    message: '로그인이 필요합니다.',
    fieldErrors: [],
    requestId: '00000000-0000-0000-0000-000000000001',
  }
  return ApiClientError.fromServer(response)
}
