import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import App from '@/App.vue'
import * as authApi from '@/shared/api/authApi'
import type { AuthSessionDto, ErrorResponseDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import { createAppRouter } from './index'

vi.mock('@/shared/api/authApi', () => ({
  getCurrentUser: vi.fn(),
  initializeCsrf: vi.fn(),
  signup: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

describe('authentication route policy', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('redirects the root according to the bootstrapped auth state', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValueOnce(authenticationRequired())
    const anonymousPinia = createPinia()
    const anonymousRouter = createAppRouter({
      history: createMemoryHistory(),
      pinia: anonymousPinia,
    })
    await anonymousRouter.push('/')
    await anonymousRouter.isReady()
    expect(anonymousRouter.currentRoute.value.name).toBe('login')

    vi.mocked(authApi.getCurrentUser).mockResolvedValueOnce(session('user-1').user)
    const authenticatedPinia = createPinia()
    const authenticatedRouter = createAppRouter({
      history: createMemoryHistory(),
      pinia: authenticatedPinia,
    })
    await authenticatedRouter.push('/')
    await authenticatedRouter.isReady()
    expect(authenticatedRouter.currentRoute.value.name).toBe('dashboard')
  })

  it('protects dashboard and honors only safe public-only returnTo values', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValueOnce(authenticationRequired())
    const anonymousPinia = createPinia()
    const anonymousRouter = createAppRouter({
      history: createMemoryHistory(),
      pinia: anonymousPinia,
    })
    await anonymousRouter.push('/dashboard')
    await anonymousRouter.isReady()
    expect(anonymousRouter.currentRoute.value.name).toBe('login')
    expect(anonymousRouter.currentRoute.value.query.returnTo).toBe('/dashboard')

    const authenticatedPinia = createPinia()
    setActivePinia(authenticatedPinia)
    const authenticatedStore = useAuthStore(authenticatedPinia)
    vi.mocked(authApi.login).mockResolvedValue(session('user-1'))
    await authenticatedStore.login({ email: 'one@example.com', password: 'password-123' })
    const authenticatedRouter = createAppRouter({
      history: createMemoryHistory(),
      pinia: authenticatedPinia,
    })
    await authenticatedRouter.push({
      path: '/login',
      query: { returnTo: '/onboarding?step=welcome' },
    })
    await authenticatedRouter.isReady()
    expect(authenticatedRouter.currentRoute.value.fullPath).toBe('/onboarding?step=welcome')

    await authenticatedRouter.push({
      path: '/login',
      query: { returnTo: 'https://evil.example/dashboard' },
    })
    expect(authenticatedRouter.currentRoute.value.name).toBe('dashboard')
  })

  it('leaves a protected shell for login when a logout request returns 401', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore(pinia)
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    vi.mocked(authApi.logout).mockRejectedValueOnce(authenticationRequired())
    await store.login({ email: 'one@example.com', password: 'password-123' })

    const router = createAppRouter({ history: createMemoryHistory(), pinia })
    await router.push('/dashboard')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('dashboard')

    await expect(store.logout()).rejects.toMatchObject({ status: 401 })
    await flushPromises()

    expect(store.status).toBe('anonymous')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.returnTo).toBe('/dashboard')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('renders the protected P1 shells and a dedicated 404 page', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    await useAuthStore(pinia).login({ email: 'one@example.com', password: 'password-123' })
    const router = createAppRouter({ history: createMemoryHistory(), pinia })
    await router.push('/dashboard')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    expect(wrapper.text()).toContain('대시보드 데이터는 다음 단계에서 연결됩니다.')
    await router.push('/onboarding')
    await flushPromises()
    expect(wrapper.text()).toContain('프로필 입력은 다음 단계에서 제공됩니다.')

    await router.push('/missing-page')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('not-found')
    expect(wrapper.text()).toContain('페이지를 찾을 수 없습니다')
  })
})

function session(id: string): AuthSessionDto {
  return {
    user: { id, email: `${id}@example.com`, displayName: id },
    csrf: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: `csrf-${id}` },
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
