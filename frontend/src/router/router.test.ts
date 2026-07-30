import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import App from '@/App.vue'
import * as agentRunApi from '@/shared/api/agentRunApi'
import * as authApi from '@/shared/api/authApi'
import type { AuthSessionDto, ErrorResponseDto, ProfileDto } from '@/shared/api/contracts'
import * as documentApi from '@/shared/api/documentApi'
import { ApiClientError } from '@/shared/api/errors'
import * as jobApi from '@/shared/api/jobApi'
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

vi.mock('@/shared/api/documentApi', () => ({
  listDocuments: vi.fn(),
}))

vi.mock('@/shared/api/jobApi', () => ({
  listJobs: vi.fn(),
}))

vi.mock('@/shared/api/agentRunApi', () => ({
  listAgentRuns: vi.fn(),
}))

describe('authentication route policy', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
    vi.mocked(profileApi.listEducations).mockResolvedValue(emptyPage())
    vi.mocked(documentApi.listDocuments).mockResolvedValue(emptyPage())
    vi.mocked(jobApi.listJobs).mockResolvedValue(emptyPage())
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(emptyPage())
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

    await flushPromises()
    expect(wrapper.get('h2').text()).toContain('user-1님의 지원 현황')
    expect(wrapper.text()).toContain('프로필 작성')
    expect(wrapper.text()).toContain('문서 업로드')
    expect(wrapper.text()).not.toContain('다음 단계에서 연결됩니다')
    await router.push('/onboarding')
    await flushPromises()
    expect(wrapper.text()).toContain('나에게 맞게 시작해 볼까요?')

    await router.push('/missing-page')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('not-found')
    expect(wrapper.text()).toContain('페이지를 찾을 수 없어요')
    expect(wrapper.get('a.button[href="/dashboard"]').text()).toContain('지원 홈으로 돌아가기')
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

  it('protects P7 Job routes, keeps the base redirect on overview, and resolves analysis and cover letter', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(authApi.login).mockResolvedValueOnce(session('user-1'))
    await useAuthStore(pinia).login({ email: 'one@example.com', password: 'password-123' })
    const router = createAppRouter({ history: createMemoryHistory(), pinia })
    const jobId = '10000000-0000-4000-8000-000000000001'

    await router.push(`/jobs/${jobId}`)
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('job-overview')
    expect(router.currentRoute.value.fullPath).toBe(`/jobs/${jobId}/overview`)
    expect(router.resolve(`/jobs/${jobId}/analysis`).name).toBe('job-analysis')
    expect(router.resolve(`/jobs/${jobId}/analysis`).meta.profileRecommended).toBe(true)
    expect(router.resolve(`/jobs/${jobId}/cover-letter`).name).toBe('job-cover-letter')
    expect(router.resolve('/cover-letters').name).toBe('cover-letters')
    expect(router.resolve(`/cover-letters/${jobId}/edit`).name).toBe('cover-letter-edit')
  })

  it('adds lazy Document, Job, Cover Letter and Agent Run pages while preserving earlier routes', () => {
    const protectedShell = routes.find(
      (route) => route.path === '/' && route.meta?.requiresAuth === true,
    )
    const children = protectedShell?.children ?? []
    const listRoute = children.find((route) => route.name === 'agent-runs')
    const detailRoute = children.find((route) => route.name === 'agent-run-detail')
    const documentsRoute = children.find((route) => route.name === 'documents')
    const documentDetailRoute = children.find((route) => route.name === 'document-detail')
    const jobsRoute = children.find((route) => route.name === 'jobs')
    const jobNewRoute = children.find((route) => route.name === 'job-new')
    const jobDetailLayout = children.find((route) => route.path === 'jobs/:jobId')
    const coverLettersRoute = children.find((route) => route.name === 'cover-letters')
    const coverLetterEditRoute = children.find((route) => route.name === 'cover-letter-edit')

    expect(typeof documentsRoute?.component).toBe('function')
    expect(typeof documentDetailRoute?.component).toBe('function')
    expect(typeof listRoute?.component).toBe('function')
    expect(typeof detailRoute?.component).toBe('function')
    expect(typeof jobsRoute?.component).toBe('function')
    expect(typeof jobNewRoute?.component).toBe('function')
    expect(typeof jobDetailLayout?.component).toBe('function')
    expect(typeof coverLettersRoute?.component).toBe('function')
    expect(typeof coverLetterEditRoute?.component).toBe('function')
    expect(jobDetailLayout?.children?.map((route) => route.name)).toEqual([
      'job-detail',
      'job-overview',
      'job-analysis',
      'job-cover-letter',
    ])
    expect(children.map((route) => route.name)).toEqual(
      expect.arrayContaining([
        'onboarding',
        'dashboard',
        'profile-basic',
        'profile-education',
        'profile-evidence',
        'documents',
        'document-detail',
        'jobs',
        'job-new',
        'cover-letters',
        'cover-letter-edit',
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
