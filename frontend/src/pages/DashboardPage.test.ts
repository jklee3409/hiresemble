import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as agentRunApi from '@/shared/api/agentRunApi'
import * as documentApi from '@/shared/api/documentApi'
import * as jobApi from '@/shared/api/jobApi'
import * as profileApi from '@/shared/api/profileApi'
import type { ProfileDto } from '@/shared/api/contracts'
import { useAuthStore } from '@/stores/auth'

import DashboardPage from './DashboardPage.vue'

vi.mock('@/shared/api/profileApi', () => ({ getProfile: vi.fn() }))
vi.mock('@/shared/api/documentApi', () => ({ listDocuments: vi.fn() }))
vi.mock('@/shared/api/jobApi', () => ({ listJobs: vi.fn() }))
vi.mock('@/shared/api/agentRunApi', () => ({ listAgentRuns: vi.fn() }))

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders owner-scoped totals, actionable states and recent activity for an existing user', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      legalName: '이종규',
      introduction: '소개',
      desiredRoles: ['백엔드 개발자'],
      desiredIndustries: ['IT·소프트웨어'],
      desiredLocations: ['서울'],
      expectedGraduationDate: null,
      profileCompleted: false,
      missingCompletionItems: ['PRIMARY_EDUCATION'],
      version: 3,
      createdAt: '2026-07-20T00:00:00Z',
      updatedAt: '2026-07-28T03:00:00Z',
    })
    vi.mocked(documentApi.listDocuments).mockResolvedValue({
      items: [document()],
      page: 0,
      size: 5,
      totalElements: 2,
      totalPages: 1,
    })
    vi.mocked(jobApi.listJobs).mockImplementation(async (params = {}) => {
      if (params.deadlineWithinDays !== undefined) return page([job()], 1, params.size)
      if (params.status === 'IN_PROGRESS') return page([], 3, params.size)
      if (params.status === 'SUBMITTED') return page([], 2, params.size)
      return page([job()], 5, params.size)
    })
    vi.mocked(agentRunApi.listAgentRuns).mockImplementation(async (params = {}) =>
      params.status === undefined ? page([run()], 4, params.size) : page([run()], 1, params.size),
    )

    const wrapper = await mountDashboard('이종규')

    expect(wrapper.get('h2').text()).toBe('이종규님의 지원 현황')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.text()).toContain('지원 중 공고')
    expect(wrapper.text()).toContain('서류 제출 공고')
    expect(wrapper.text()).toContain('확인이 필요한 자료')
    expect(wrapper.text()).toContain('입력을 기다리는 분석')
    expect(wrapper.text()).toContain('마감 임박 공고')
    expect(wrapper.text()).toContain('최근 활동')
  })

  it('uses a natural name fallback and a focused start state for a new user', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
    vi.mocked(documentApi.listDocuments).mockResolvedValue(page([], 0, 5))
    vi.mocked(jobApi.listJobs).mockResolvedValue(page([], 0, 5))
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(page([], 0, 5))

    const wrapper = await mountDashboard('   ')

    expect(wrapper.get('h2').text()).toBe('나의 지원 현황')
    expect(wrapper.text()).toContain('지원 준비의 기준 정보를 먼저 모아 보세요.')
    expect(wrapper.text()).toContain('프로필 작성')
    expect(wrapper.text()).toContain('문서 업로드')
    expect(wrapper.text()).toContain('공고 등록')
    expect(wrapper.text()).not.toContain('지원 준비 현황')
  })

  it('keeps available content visible when one dashboard source fails', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
    vi.mocked(documentApi.listDocuments).mockRejectedValue(new Error('offline'))
    vi.mocked(jobApi.listJobs).mockResolvedValue(page([], 0, 5))
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(page([], 0, 5))

    const wrapper = await mountDashboard('테스터')

    expect(wrapper.text()).toContain('일부 지원 현황을 불러오지 못했어요.')
    expect(wrapper.get('button').text()).toContain('다시 불러오기')
    expect(wrapper.text()).toContain('프로필 작성')
  })
})

async function mountDashboard(displayName: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: {
      id: '00000000-0000-4000-8000-000000000001',
      email: 'dashboard@example.com',
      displayName,
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', component: DashboardPage },
      { path: '/profile/basic', component: { template: '<div />' } },
      { path: '/documents', component: { template: '<div />' } },
      { path: '/documents/:documentId', component: { template: '<div />' } },
      { path: '/jobs', component: { template: '<div />' } },
      { path: '/jobs/new', component: { template: '<div />' } },
      { path: '/jobs/:jobId/overview', component: { template: '<div />' } },
      { path: '/agent-runs', component: { template: '<div />' } },
      { path: '/agent-runs/:agentRunId', component: { template: '<div />' } },
    ],
  })
  await router.push('/dashboard')
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const wrapper = mount(DashboardPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function page<T>(items: T[], totalElements: number, size = 5) {
  return {
    items,
    page: 0,
    size: size ?? 5,
    totalElements,
    totalPages: totalElements === 0 ? 0 : 1,
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
    createdAt: '2026-07-28T00:00:00Z',
    updatedAt: '2026-07-28T00:00:00Z',
  }
}

function document() {
  return {
    id: '00000000-0000-4000-8000-000000000010',
    documentType: 'RESUME' as const,
    originalFilename: 'resume.pdf',
    displayName: '이종규 이력서.pdf',
    mimeType: 'application/pdf',
    fileSizeBytes: 1024,
    parseStatus: 'NEEDS_MANUAL_TEXT' as const,
    evidenceExtractionStatus: 'NOT_STARTED' as const,
    manualTextProvided: false,
    safeError: null,
    latestAgentRunId: '00000000-0000-4000-8000-000000000030',
    version: 1,
    uploadedAt: '2026-07-27T00:00:00Z',
    updatedAt: '2026-07-28T04:00:00Z',
  }
}

function job() {
  return {
    id: '00000000-0000-4000-8000-000000000020',
    companyName: 'Hiresemble',
    title: '백엔드 개발자',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS' as const,
    extractionStatus: 'EXTRACTED' as const,
    submittedAt: null,
    deadlineAt: '2026-08-02T14:59:59Z',
    deadlineSource: 'USER_ENTERED' as const,
    latestFitScore: null,
    analysisOutdated: false as const,
    outdatedReasons: [] as [],
    coverLetterStatus: null,
    interviewPreparationCount: 0 as const,
    version: 1,
    createdAt: '2026-07-26T00:00:00Z',
    updatedAt: '2026-07-28T02:00:00Z',
  }
}

function run() {
  return {
    id: '00000000-0000-4000-8000-000000000030',
    workflowType: 'DOCUMENT_INGESTION' as const,
    resourceType: 'DOCUMENT',
    resourceId: '00000000-0000-4000-8000-000000000010',
    status: 'WAITING_USER' as const,
    currentStep: '텍스트 확인',
    progressPercent: 45,
    requestedQualityMode: null,
    highestModelTierUsed: null,
    estimatedCostUsd: 0,
    reservedCostUsd: 0,
    actualCostUsd: 0,
    retryable: false,
    cancellable: false,
    requiredUserAction: {
      type: 'PROVIDE_DOCUMENT_TEXT' as const,
      resource: null,
      route: '/documents/00000000-0000-4000-8000-000000000010',
      message: '문서 내용을 직접 입력해 주세요.',
    },
    stateVersion: 2,
    queuedAt: '2026-07-28T01:00:00Z',
    updatedAt: '2026-07-28T05:00:00Z',
  }
}
