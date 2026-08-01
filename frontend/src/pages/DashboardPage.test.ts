import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as agentRunApi from '@/shared/api/agentRunApi'
import type { ProfileCompletionItem, ProfileDto } from '@/shared/api/contracts'
import * as documentApi from '@/shared/api/documentApi'
import * as jobApi from '@/shared/api/jobApi'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

import DashboardPage from './DashboardPage.vue'

vi.mock('@/shared/api/profileApi', () => ({ getProfile: vi.fn() }))
vi.mock('@/shared/api/documentApi', () => ({ listDocuments: vi.fn() }))
vi.mock('@/shared/api/jobApi', () => ({ listJobs: vi.fn() }))
vi.mock('@/shared/api/agentRunApi', () => ({ listAgentRuns: vi.fn() }))

type DashboardSources = {
  profileCompleted?: boolean
  documents?: number
  jobs?: number
  runs?: number
  errorSource?: 'profile' | 'documents' | 'jobs'
}

describe('DashboardPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each([
    {
      label: '모두 미완료',
      sources: {},
      count: '0 / 3',
      remaining: ['기본 정보 준비', '이력서 또는 포트폴리오 등록', '첫 관심 공고 등록'],
    },
    {
      label: '프로필만 완료',
      sources: { profileCompleted: true },
      count: '1 / 3',
      remaining: ['이력서 또는 포트폴리오 등록', '첫 관심 공고 등록'],
    },
    {
      label: '문서만 등록',
      sources: { documents: 1 },
      count: '1 / 3',
      remaining: ['기본 정보 준비', '첫 관심 공고 등록'],
    },
    {
      label: '공고만 등록',
      sources: { jobs: 1 },
      count: '1 / 3',
      remaining: ['기본 정보 준비', '이력서 또는 포트폴리오 등록'],
    },
    {
      label: '두 항목 완료',
      sources: { profileCompleted: true, documents: 1 },
      count: '2 / 3',
      remaining: ['첫 관심 공고 등록'],
    },
  ])('$label 상태에서도 남은 체크리스트와 일반 대시보드를 함께 보여 준다', async (scenario) => {
    mockDashboardSources(scenario.sources)

    const wrapper = await mountDashboard('체크리스트 사용자')

    expect(wrapper.get('.start-checklist__progress').text()).toContain(scenario.count)
    for (const title of scenario.remaining) expect(wrapper.text()).toContain(title)
    expect(wrapper.text()).toContain('지원 준비 현황')
    expect(wrapper.text()).toContain('다음 할 일')
    expect(wrapper.text()).toContain('최근 활동')
    expect(wrapper.get('a[href="/guide"]').text()).toContain('전체 이용 순서 보기')
  })

  it('hides the checklist only after all three starting items are complete', async () => {
    mockDashboardSources({ profileCompleted: true, documents: 1, jobs: 1 })

    const wrapper = await mountDashboard('완료 사용자')

    expect(wrapper.find('.start-checklist').exists()).toBe(false)
    expect(wrapper.text()).toContain('지원 준비 현황')
    expect(wrapper.text()).toContain('최근 활동')
  })

  it('keeps an incomplete checklist visible even when an AI run already exists', async () => {
    mockDashboardSources({ runs: 1 })

    const wrapper = await mountDashboard('AI 작업 사용자')

    expect(wrapper.get('.start-checklist__progress').text()).toContain('0 / 3')
    expect(wrapper.text()).toContain('기본 정보 준비')
    expect(wrapper.text()).toContain('입력을 기다리는 분석')
  })

  it.each([
    { source: 'profile' as const, title: '기본 정보 준비' },
    { source: 'documents' as const, title: '이력서 또는 포트폴리오 등록' },
    { source: 'jobs' as const, title: '첫 관심 공고 등록' },
  ])('$source query 실패를 미완료나 0개로 계산하지 않는다', async ({ source, title }) => {
    mockDashboardSources({ profileCompleted: true, documents: 1, jobs: 1, errorSource: source })

    const wrapper = await mountDashboard('오류 확인 사용자')
    const unknownItem = wrapper.get('.start-checklist__item--unknown')

    expect(wrapper.text()).toContain('일부 지원 정보를 불러오지 못했어요.')
    expect(unknownItem.text()).toContain(title)
    expect(unknownItem.text()).toContain('현재 상태를 확인하지 못했어요.')
    expect(unknownItem.get('button').text()).toContain('다시 확인')
    expect(unknownItem.classes()).not.toContain('start-checklist__item--pending')
    expect(wrapper.text()).toContain('지원 준비 현황')
  })

  it('renders owner-scoped totals, actionable states and recent activity for an existing user', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(profile(false, ['PRIMARY_EDUCATION']))
    vi.mocked(documentApi.listDocuments).mockResolvedValue(page([document()], 2))
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

    expect(wrapper.get('h1').text()).toBe('이종규, 지금 준비 중인 지원')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.text()).toContain('2 / 3')
    expect(wrapper.text()).toContain('확인이 필요한 자료')
    expect(wrapper.text()).toContain('입력을 기다리는 분석')
    expect(wrapper.text()).toContain('마감 임박 공고')
    expect(wrapper.text()).toContain('최근 활동')
  })
})

function mockDashboardSources({
  profileCompleted = false,
  documents = 0,
  jobs = 0,
  runs = 0,
  errorSource,
}: DashboardSources): void {
  if (errorSource === 'profile') {
    vi.mocked(profileApi.getProfile).mockRejectedValue(new Error('profile offline'))
  } else {
    vi.mocked(profileApi.getProfile).mockResolvedValue(
      profile(profileCompleted, profileCompleted ? [] : ['LEGAL_NAME']),
    )
  }

  if (errorSource === 'documents') {
    vi.mocked(documentApi.listDocuments).mockRejectedValue(new Error('documents offline'))
  } else {
    vi.mocked(documentApi.listDocuments).mockResolvedValue(
      page(documents > 0 ? [document()] : [], documents),
    )
  }

  vi.mocked(jobApi.listJobs).mockImplementation(async (params = {}) => {
    const isRecentTotal = params.status === undefined && params.deadlineWithinDays === undefined
    if (errorSource === 'jobs' && isRecentTotal) throw new Error('jobs offline')
    return page(isRecentTotal && jobs > 0 ? [job()] : [], isRecentTotal ? jobs : 0, params.size)
  })
  vi.mocked(agentRunApi.listAgentRuns).mockImplementation(async (params = {}) =>
    page(runs > 0 ? [run()] : [], runs, params.size),
  )
}

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
      { path: '/guide', component: { template: '<div />' } },
    ],
  })
  await router.push('/dashboard')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(DashboardPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function profile(completed: boolean, missingCompletionItems: ProfileCompletionItem[]): ProfileDto {
  return {
    legalName: completed ? '완료 사용자' : null,
    introduction: null,
    desiredRoles: completed ? ['백엔드 개발자'] : [],
    desiredIndustries: completed ? ['IT'] : [],
    desiredLocations: completed ? ['서울'] : [],
    expectedGraduationDate: null,
    profileCompleted: completed,
    missingCompletionItems,
    version: 1,
    createdAt: '2026-07-28T00:00:00Z',
    updatedAt: '2026-07-28T00:00:00Z',
  }
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

function document() {
  return {
    id: '00000000-0000-4000-8000-000000000010',
    documentType: 'RESUME' as const,
    originalFilename: 'resume.pdf',
    displayName: '지원용 이력서.pdf',
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
