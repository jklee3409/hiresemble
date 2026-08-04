import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { agentRunDetail, agentRunSummary } from '@/features/agent-runs/testFixtures'
import {
  JOB_ANALYSIS_RUN_ID,
  JOB_ID,
  jobAnalysisDetailFixture,
  jobAnalysisSummaryFixture,
  jobDetailFixture,
} from '@/features/jobs/testFixtures'
import type { AgentRunDetailDto, AgentRunStatus } from '@/shared/api/agentRunContracts'
import type { ProfileDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import * as agentRunApi from '@/shared/api/agentRunApi'
import * as jobApi from '@/shared/api/jobApi'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

import JobAnalysisPage from './JobAnalysisPage.vue'

vi.mock('@/shared/api/jobApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/api/jobApi')>()
  return {
    ...actual,
    getJob: vi.fn(),
    getLatestJobAnalysis: vi.fn(),
    listJobAnalyses: vi.fn(),
    analyzeJob: vi.fn(),
    createJobIdempotencyKey: vi.fn(() => 'job-analysis:key-1234'),
  }
})

vi.mock('@/shared/api/profileApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/api/profileApi')>()
  return { ...actual, getProfile: vi.fn() }
})

vi.mock('@/shared/api/agentRunApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/api/agentRunApi')>()
  return {
    ...actual,
    listAgentRuns: vi.fn(),
    getAgentRun: vi.fn(),
    retryAgentRun: vi.fn(),
    createRetryIdempotencyKey: vi.fn(() => 'agent-run-retry:key-1234'),
  }
})

vi.mock('@/features/agent-runs/stream', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/agent-runs/stream')>()
  return {
    ...actual,
    AgentRunStreamController: class {
      constructor(
        private readonly options: {
          onConnectionState?: (state: 'connected' | 'closed') => void
        },
      ) {}

      start() {
        this.options.onConnectionState?.('connected')
      }

      close() {
        this.options.onConnectionState?.('closed')
      }
    },
  }
})

describe('P6 Job analysis page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(jobApi.getJob).mockResolvedValue(jobDetailFixture())
    vi.mocked(jobApi.getLatestJobAnalysis).mockRejectedValue(analysisNotFound())
    vi.mocked(jobApi.listJobAnalyses).mockResolvedValue(page([]))
    vi.mocked(profileApi.getProfile).mockResolvedValue(profileFixture())
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(page([]))
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('QUEUED', { progressPercent: 0 }),
    )
    vi.mocked(jobApi.analyzeJob).mockResolvedValue({
      agentRunId: JOB_ANALYSIS_RUN_ID,
      status: 'QUEUED',
      resourceType: 'JOB',
      resourceId: JOB_ID,
      replayed: false,
    })
  })

  it('shows the automatic BALANCED journey without an initial quality selector', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(profileFixture({ profileCompleted: false }))
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('공고 분석 진행 상황')
    expect(wrapper.text()).toContain('공고 저장 후 자동으로 진행돼요')
    expect(wrapper.text()).toContain('공고 등록 뒤 분석이 자동으로 이어져요.')
    expect(wrapper.text()).toContain('프로필을 더 채우면 비교 근거가 풍부해져요')
    expect(agentRunApi.listAgentRuns).toHaveBeenCalledWith({
      workflowType: ['JOB_ANALYSIS'],
      resourceType: 'JOB',
      resourceId: JOB_ID,
      page: 0,
      size: 1,
      sort: 'queuedAt,desc',
    })
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('균형형')
    expect(wrapper.text()).not.toContain('경제형')
    expect(wrapper.text()).not.toContain('HIGH_QUALITY')
    expect(jobApi.analyzeJob).not.toHaveBeenCalled()
    expect(wrapper.get('progress').attributes('aria-label')).toBe('공고 분석 진행률 0%')
  })

  it('recovers a running JOB_ANALYSIS run and keeps WAITING_USER distinct from SSE state', async () => {
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(
      page([
        agentRunSummary('WAITING_USER', {
          id: JOB_ANALYSIS_RUN_ID,
          workflowType: 'JOB_ANALYSIS',
          resourceType: 'JOB',
          resourceId: JOB_ID,
          progressPercent: 55,
        }),
      ]),
    )
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('WAITING_USER', {
        progressPercent: 55,
        requiredUserAction: {
          type: 'PROVIDE_JOB_TEXT',
          resource: { resourceType: 'JOB', resourceId: JOB_ID, displayLabel: null },
          route: `/jobs/${JOB_ID}/overview`,
          message: '공고 본문을 보완해 주세요.',
        },
      }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('정보 입력 필요')
    expect(wrapper.text()).toContain('공고 본문을 보완해 주세요.')
    expect(wrapper.get('.analysis-run__waiting a').text()).toContain('필요한 정보 입력')
    expect(wrapper.text()).toContain('실시간 진행 상황 연결됨')
    expect(wrapper.text()).toContain('연결이 잠시 끊겨도 분석 실패로 처리하지 않아요')
    expect(wrapper.get('progress').attributes('aria-label')).toBe('공고 분석 진행률 55%')
  })

  it('shows a safe failed state and launches the server-authorized generic retry', async () => {
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(
      page([
        agentRunSummary('FAILED', {
          id: JOB_ANALYSIS_RUN_ID,
          workflowType: 'JOB_ANALYSIS',
          resourceType: 'JOB',
          resourceId: JOB_ID,
          retryable: true,
        }),
      ]),
    )
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('FAILED', {
        retryable: true,
        cancellable: false,
        safeError: {
          code: 'AI_SO_WORKFLOW_CONTEXT_INVALID',
          message: 'AI 결과의 의미 제약을 확인하지 못했습니다.',
        },
      }),
    )
    vi.mocked(agentRunApi.retryAgentRun).mockResolvedValue({
      agentRunId: '50000000-0000-4000-8000-000000000099',
      status: 'QUEUED',
      resourceType: 'JOB',
      resourceId: JOB_ID,
      replayed: false,
    })
    const { wrapper } = await mountPage()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      '분석 결과를 안정적으로 정리하지 못했어요.',
    )
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '공고와 등록한 지원 정보는 그대로 보존되어 있으니 잠시 후 다시 시도해 주세요.',
    )
    expect(wrapper.text()).not.toContain('AI 결과의 의미 제약을 확인하지 못했습니다.')
    const retry = wrapper.findAll('button').find((button) => button.text() === '공고 분석 재실행')
    expect(retry).toBeDefined()
    await retry?.trigger('click')
    await flushPromises()
    expect(agentRunApi.retryAgentRun).toHaveBeenCalledWith(
      JOB_ANALYSIS_RUN_ID,
      'agent-run-retry:key-1234',
    )
  })

  it('offers a fresh analysis execution when the failed run is not generically retryable', async () => {
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(
      page([
        agentRunSummary('FAILED', {
          id: JOB_ANALYSIS_RUN_ID,
          workflowType: 'JOB_ANALYSIS',
          resourceType: 'JOB',
          resourceId: JOB_ID,
          retryable: false,
        }),
      ]),
    )
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('FAILED', {
        retryable: false,
        cancellable: false,
        safeError: {
          code: 'RESOURCE_CONFLICT',
          message: '현재 입력으로 범용 재시도를 진행할 수 없습니다.',
        },
      }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('아래 버튼으로 공고 분석을 다시 실행할 수 있어요.')
    const restart = wrapper.findAll('button').find((button) => button.text() === '공고 분석 재실행')
    expect(restart).toBeDefined()
    await restart?.trigger('click')
    await flushPromises()

    expect(agentRunApi.retryAgentRun).not.toHaveBeenCalled()
    expect(jobApi.analyzeJob).toHaveBeenCalledWith(
      JOB_ID,
      {
        qualityMode: 'BALANCED',
        forceReanalyze: true,
        jobVersion: 2,
      },
      'job-analysis:key-1234',
    )
    expect(wrapper.text()).not.toContain('필요할 때만')
  })

  it('shows the latest successful analysis run instead of the older failed automatic run', async () => {
    const latestRunId = '50000000-0000-4000-8000-000000000099'
    const latest = jobAnalysisDetailFixture({ agentRunId: latestRunId })
    vi.mocked(jobApi.getLatestJobAnalysis).mockResolvedValue(latest)
    vi.mocked(jobApi.listJobAnalyses).mockResolvedValue(page([latest]))
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(
      page([
        agentRunSummary('SUCCEEDED', {
          id: latestRunId,
          workflowType: 'JOB_ANALYSIS',
          resourceType: 'JOB',
          resourceId: JOB_ID,
          retryable: false,
        }),
      ]),
    )
    vi.mocked(agentRunApi.getAgentRun).mockImplementation(async (runId) =>
      runId === latestRunId
        ? analysisRun('SUCCEEDED', { id: latestRunId, progressPercent: 100 })
        : analysisRun('FAILED', {
            retryable: false,
            safeError: { code: 'INTERNAL_FAILURE', message: '이전 실패' },
          }),
    )
    const { wrapper } = await mountPage()

    expect(agentRunApi.getAgentRun).toHaveBeenLastCalledWith(latestRunId)
    expect(wrapper.text()).not.toContain('공고 분석을 완료하지 못했어요.')
    expect(wrapper.find('.analysis-run').exists()).toBe(false)
    expect(wrapper.get('.analysis-result__run-link').attributes('href')).toBe(
      `/agent-runs/${latestRunId}`,
    )
  })

  it('renders eligibility separately from a high score, all evidence sections, OUTDATED reasons and history', async () => {
    const base = jobAnalysisDetailFixture()
    const latest = jobAnalysisDetailFixture({
      eligibility: 'INELIGIBLE',
      fitScore: 82.5,
      analysisSummary:
        '필수 경력 기간은 부족하지만 핵심 기술 경험은 높은 일치를 보여요. 확인 가능한 근거 유형만 반영했으며, 일부 요건은 추가 확인이 필요합니다.',
      analysisOutdated: true,
      outdatedReasons: ['JOB_CONTENT_CHANGED', 'PROFILE_CHANGED', 'EVIDENCE_CHANGED'],
      responsibilities: base.responsibilities.map((item) => ({
        ...item,
        sourceLocation: '$.untrustedJobPosting.descriptionText',
      })),
    })
    const older = jobAnalysisSummaryFixture({
      id: '50000000-0000-4000-8000-000000000090',
      analysisVersion: 1,
      eligibility: 'ELIGIBLE',
      fitScore: 45,
      analysisOutdated: false,
      createdAt: '2026-07-26T00:00:00Z',
    })
    vi.mocked(jobApi.getLatestJobAnalysis).mockResolvedValue(latest)
    vi.mocked(jobApi.listJobAnalyses).mockResolvedValue(page([latest, older]))
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('SUCCEEDED', { progressPercent: 100 }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('필수 조건 미충족')
    expect(wrapper.get('.analysis-result__hero').text()).toContain(
      '공고와 잘 맞는 강점을 분석했어요.',
    )
    expect(wrapper.get('.analysis-result__hero').text()).not.toContain('최신 분석')
    expect(wrapper.get('.analysis-result__hero').text()).not.toContain('분석 버전 2')
    expect(wrapper.text()).toContain('85점')
    expect(wrapper.text()).not.toMatch(/\d+\.\d+\s*(?:\/\s*\d+\.\d+)?점/)
    expect(wrapper.get('abbr').attributes('title')).toContain('합격 가능성')
    expect(wrapper.text()).toContain('핵심 요약')
    expect(wrapper.text()).not.toContain('AI 핵심 요약')
    expect(wrapper.text()).toContain(
      '필수 경력 기간은 부족하지만 핵심 기술 경험은 높은 일치를 보여요.',
    )
    expect(wrapper.text()).not.toContain(
      '확인 가능한 근거 유형만 반영했으며, 일부 요건은 추가 확인이 필요합니다.',
    )
    expect(
      wrapper.findAll('.analysis-requirement-group').every((item) => !item.attributes('open')),
    ).toBe(true)
    expect(wrapper.text()).toContain('Java 개발 경력 3년 이상')
    expect(wrapper.text()).toContain('대규모 트래픽 경험')
    expect(wrapper.text()).toContain('공고 본문')
    expect(wrapper.text()).not.toContain('$.untrustedJobPosting.descriptionText')
    expect(wrapper.text()).toContain('Spring API 개발 경험이 요구사항과 일치해요')
    expect(wrapper.text()).toContain('필수 경력 기간은 추가 확인이 필요해요')
    expect(wrapper.text()).toContain('결제 API 개선 프로젝트')
    expect(wrapper.text()).toContain('20 / 40점')
    expect(wrapper.get('.status-badge--warning').text()).toBe('OUTDATED')
    expect(wrapper.text()).toContain('공고 내용이 변경됨')
    expect(wrapper.text()).toContain('프로필 정보가 변경됨')
    expect(wrapper.text()).toContain('확인한 경험이 변경됨')
    expect(wrapper.text()).toContain('아래 기존 결과는 그대로 유지돼요')
    expect(wrapper.text()).toContain('분석 결과 기록')
    expect(wrapper.text()).not.toContain('분석 버전')
    expect(wrapper.text()).not.toContain('결정론적 점수')
    expect(wrapper.text()).not.toContain('판정 근거 보기')
    expect(wrapper.text()).not.toContain('읽기 전용 요약')
    expect(wrapper.text()).not.toContain('재분석 옵션')
    expect(wrapper.text()).not.toContain('균형형')
    expect(wrapper.text()).not.toContain('경제형')

    const result = wrapper.get('.analysis-result')
    expect(result.findAll('.section-surface')).toHaveLength(0)
    expect(result.findAll('.button--primary')).toHaveLength(1)
    expect(result.get('.analysis-result__next nav').text()).toContain('내 정보 보완')
    expect(result.findAll('.analysis-insight li > span')).toHaveLength(0)

    const olderButton = wrapper.findAll('.analysis-history__list button')[1]
    await olderButton?.trigger('click')
    expect(wrapper.get('.analysis-history__selection').text()).toContain('45점')
    expect(wrapper.get('.analysis-history__selection').text()).toContain('이전에 저장된 결과')

    const partialFilter = wrapper
      .findAll('.analysis-breakdown__filters button')
      .find((button) => button.text().includes('일부 일치'))
    await partialFilter?.trigger('click')
    expect(wrapper.findAll('.analysis-criterion')).toHaveLength(1)
    expect(wrapper.get('.analysis-criterion').text()).toContain('Java 개발 경력 3년')
    expect(wrapper.get('.analysis-criterion').text()).not.toContain('Spring 기반 API 개발')

    const reanalyzeButtons = wrapper
      .findAll('button')
      .filter((button) => button.text() === '최신 정보로 다시 분석')
    expect(reanalyzeButtons).toHaveLength(1)
    const reanalyze = reanalyzeButtons[0]
    await reanalyze?.trigger('click')
    await flushPromises()
    expect(jobApi.analyzeJob).toHaveBeenCalledWith(
      JOB_ID,
      expect.objectContaining({ qualityMode: 'BALANCED', forceReanalyze: true, jobVersion: 2 }),
      'job-analysis:key-1234',
    )
  })

  it('paginates criterion results by five and resets the page when the status filter changes', async () => {
    const base = jobAnalysisDetailFixture()
    const criteria = Array.from({ length: 7 }, (_, index) => ({
      ...base.scoreBreakdown[index % base.scoreBreakdown.length]!,
      criterion: `확인 조건 ${index + 1}`,
    }))
    const latest = jobAnalysisDetailFixture({ scoreBreakdown: criteria })
    vi.mocked(jobApi.getLatestJobAnalysis).mockResolvedValue(latest)
    vi.mocked(jobApi.listJobAnalyses).mockResolvedValue(page([latest]))
    vi.mocked(agentRunApi.getAgentRun).mockResolvedValue(
      analysisRun('SUCCEEDED', { progressPercent: 100 }),
    )

    const { wrapper } = await mountPage()

    expect(wrapper.findAll('.analysis-criterion')).toHaveLength(5)
    expect(wrapper.text()).toContain('총 7개 중 1–5')
    expect(wrapper.text()).not.toContain('확인 조건 6')

    const pagination = wrapper.get('nav[aria-label="조건별 확인 결과 페이지"]')
    await pagination.findAll('button')[1]?.trigger('click')
    expect(wrapper.findAll('.analysis-criterion')).toHaveLength(2)
    expect(wrapper.text()).toContain('총 7개 중 6–7')
    expect(wrapper.text()).toContain('확인 조건 6')

    const matchedFilter = wrapper
      .findAll('.analysis-breakdown__filters button')
      .find((button) => button.text().includes('일치'))
    await matchedFilter?.trigger('click')
    expect(wrapper.findAll('.analysis-criterion')).toHaveLength(3)
    expect(wrapper.text()).toContain('총 3개 중 1–3')
    expect(wrapper.find('nav[aria-label="조건별 확인 결과 페이지"]').exists()).toBe(false)
  })

  it('retains an OUTDATED result when historical evidence is now rejected or source-deleted', async () => {
    const base = jobAnalysisDetailFixture()
    const rejectedEvidence = {
      ...base.matchedEvidenceRefs[0]!,
      id: '60000000-0000-4000-8000-000000000091',
      title: '현재는 승인 거절된 과거 근거',
      verificationStatus: 'REJECTED' as const,
      sourceDeleted: false,
    }
    const sourceDeletedEvidence = {
      ...base.matchedEvidenceRefs[0]!,
      id: '60000000-0000-4000-8000-000000000092',
      title: '현재는 원본이 삭제된 과거 근거',
      verificationStatus: 'SOURCE_DELETED' as const,
      sourceDeleted: true,
    }
    const latest = jobAnalysisDetailFixture({
      fitScore: 82.5,
      analysisOutdated: true,
      outdatedReasons: ['EVIDENCE_CHANGED'],
      matchedEvidenceRefs: [rejectedEvidence, sourceDeletedEvidence],
      scoreBreakdown: base.scoreBreakdown.map((criterion, index) =>
        index === 0
          ? { ...criterion, evidenceRefs: [rejectedEvidence, sourceDeletedEvidence] }
          : criterion,
      ),
    })
    vi.mocked(jobApi.getLatestJobAnalysis).mockResolvedValue(latest)
    vi.mocked(jobApi.listJobAnalyses).mockResolvedValue(page([latest]))

    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('85점')
    expect(wrapper.text()).not.toContain('분석 버전')
    expect(wrapper.text()).toContain('확인한 경험이 변경됨')
    expect(wrapper.text()).toContain('저장된 결과와 점수는 그대로 두고')
    expect(wrapper.text()).toContain('현재는 승인 거절된 과거 근거')
    expect(wrapper.text()).toContain('현재 상태: 승인 거절됨 · 재분석 근거에서 제외')
    expect(wrapper.text()).toContain('현재는 원본이 삭제된 과거 근거')
    expect(wrapper.text()).toContain('현재 상태: 원본 삭제됨 · 재분석 근거에서 제외')
    expect(wrapper.text()).toContain('Spring API 개발 경험이 요구사항과 일치해요')
    expect(wrapper.get('.status-badge--warning').text()).toBe('OUTDATED')
  })

  it('blocks an unusable Job body and handles owner-hidden 404 without exposing analysis controls', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValueOnce(
      jobDetailFixture({
        descriptionText: null,
        extractionStatus: 'NEEDS_MANUAL_INPUT',
        automaticAnalysis: {
          state: 'WAITING_FOR_CONTENT',
          qualityMode: 'BALANCED',
          agentRunId: null,
          error: null,
        },
      }),
    )
    const first = await mountPage()
    expect(first.wrapper.text()).toContain('분석할 공고 본문이 필요해요')
    expect(first.wrapper.text()).toContain('공고 본문을 확인하고 있어요.')
    expect(first.wrapper.find('select').exists()).toBe(false)
    first.wrapper.unmount()

    vi.mocked(jobApi.getJob).mockRejectedValueOnce(
      new ApiClientError({
        status: 404,
        code: 'RESOURCE_NOT_FOUND',
        message: '공고를 찾을 수 없습니다.',
      }),
    )
    const second = await mountPage()
    expect(second.wrapper.text()).toContain('공고를 찾을 수 없어요')
    expect(second.wrapper.text()).not.toContain('새 공고 분석 시작')
  })

  it('does not auto-retry a stale version and refreshes the Job before allowing another request', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValue(
      jobDetailFixture({
        automaticAnalysis: {
          state: 'NOT_REQUESTED',
          qualityMode: 'BALANCED',
          agentRunId: null,
          error: null,
        },
      }),
    )
    vi.mocked(jobApi.analyzeJob).mockRejectedValue(
      new ApiClientError({
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: 'stale',
      }),
    )
    const { wrapper } = await mountPage()
    await wrapper.get('button.button--primary').trigger('click')
    await flushPromises()

    expect(jobApi.analyzeJob).toHaveBeenCalledTimes(1)
    expect(jobApi.getJob).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '공고가 변경됐어요. 최신 내용을 확인한 뒤 다시 분석해 주세요.',
    )
  })
})

async function mountPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore(pinia)
  auth.status = 'authenticated'
  auth.currentUser = { id: 'user-1', email: 'user@example.com', displayName: '사용자' }

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/jobs/:jobId/analysis', name: 'job-analysis', component: JobAnalysisPage },
      { path: '/jobs/:jobId/overview', name: 'job-overview', component: { template: '<div />' } },
      {
        path: '/jobs/:jobId/cover-letter',
        name: 'job-cover-letter',
        component: { template: '<div />' },
      },
      {
        path: '/jobs/:jobId/interview',
        name: 'job-interview',
        component: { template: '<div />' },
      },
      { path: '/jobs', name: 'jobs', component: { template: '<div />' } },
      { path: '/profile/basic', name: 'profile-basic', component: { template: '<div />' } },
      {
        path: '/agent-runs/:agentRunId',
        name: 'agent-run-detail',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/jobs/${JOB_ID}/analysis`)
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = mount(JobAnalysisPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  await flushPromises()
  return { wrapper, router, queryClient }
}

function analysisRun(status: AgentRunStatus, overrides: Partial<AgentRunDetailDto> = {}) {
  return agentRunDetail({
    id: JOB_ANALYSIS_RUN_ID,
    workflowType: 'JOB_ANALYSIS',
    resourceType: 'JOB',
    resourceId: JOB_ID,
    status,
    ...overrides,
  })
}

function profileFixture(overrides: Partial<ProfileDto> = {}): ProfileDto {
  return {
    legalName: '사용자',
    introduction: '백엔드 개발자',
    desiredRoles: ['백엔드 개발자'],
    desiredIndustries: ['소프트웨어'],
    desiredLocations: ['서울'],
    expectedGraduationDate: null,
    profileCompleted: true,
    missingCompletionItems: [],
    version: 1,
    createdAt: '2026-07-27T00:00:00Z',
    updatedAt: '2026-07-27T00:00:00Z',
    ...overrides,
  }
}

function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  }
}

function analysisNotFound(): ApiClientError {
  return new ApiClientError({
    status: 404,
    code: 'JOB_ANALYSIS_NOT_FOUND',
    message: '아직 분석이 없습니다.',
  })
}
