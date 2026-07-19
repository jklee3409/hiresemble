import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import App from '@/App.vue'
import * as authApi from '@/shared/api/authApi'
import type { AuthSessionDto, ErrorResponseDto, ProfileDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'
import * as profileApi from '@/shared/api/profileApi'

import { createAppRouter, routes } from './index'

vi.mock('@/shared/api/authApi', () => ({
  getCurrentUser: vi.fn(),
  initializeCsrf: vi.fn(),
  signup: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/shared/api/profileApi', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  listEducations: vi.fn(),
  createEducation: vi.fn(),
}))

describe('authentication route policy', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
    vi.mocked(profileApi.listEducations).mockResolvedValue(emptyPage())
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
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, [VueQueryPlugin, { queryClient: new QueryClient() }]],
      },
    })

    expect(wrapper.text()).toContain('대시보드 데이터는 다음 단계에서 연결됩니다.')
    await router.push('/onboarding')
    await flushPromises()
    expect(wrapper.text()).toContain('필요한 정보를 단계별로 입력하세요.')

    await router.push('/missing-page')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('not-found')
    expect(wrapper.text()).toContain('페이지를 찾을 수 없습니다')
  })

  it('redirects /profile to /profile/basic without gating an incomplete profile', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    await useAuthStore(pinia).login({ email: 'one@example.com', password: 'password-123' })
    const router = createAppRouter({ history: createMemoryHistory(), pinia })

    await router.push('/profile')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('profile-basic')
    expect(router.currentRoute.value.fullPath).toBe('/profile/basic')
  })

  it('adds lazy Document and Agent Run pages while preserving P1 and P2 routes', () => {
    const protectedShell = routes.find(
      (route) => route.path === '/' && route.meta?.requiresAuth === true,
    )
    const children = protectedShell?.children ?? []
    const listRoute = children.find((route) => route.name === 'agent-runs')
    const detailRoute = children.find((route) => route.name === 'agent-run-detail')
    const documentsRoute = children.find((route) => route.name === 'documents')
    const documentDetailRoute = children.find((route) => route.name === 'document-detail')

    expect(typeof documentsRoute?.component).toBe('function')
    expect(typeof documentDetailRoute?.component).toBe('function')
    expect(typeof listRoute?.component).toBe('function')
    expect(typeof detailRoute?.component).toBe('function')
    expect(children.map((route) => route.name)).toEqual(
      expect.arrayContaining([
        'onboarding',
        'dashboard',
        'profile-basic',
        'profile-education',
        'profile-evidence',
        'documents',
        'document-detail',
        'agent-runs',
        'agent-run-detail',
      ]),
    )
  })
})

function session(id: string): AuthSessionDto {
  return {
    user: { id, email: `${id}@example.com`, displayName: id },
    csrf: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: `csrf-${id}` },
  }
}

function emptyProfile(): ProfileDto {
  return {
    legalName: null,
    introduction: null,
    desiredRoles: [],
    desiredIndustries: [],
    desiredLocations: [],
    expectedGraduationDate: null,
    profileCompleted: false,
    missingCompletionItems: [
      'LEGAL_NAME',
      'DESIRED_ROLE',
      'DESIRED_INDUSTRY',
      'DESIRED_LOCATION',
      'PRIMARY_EDUCATION',
    ],
    version: 0,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function emptyPage() {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
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
