import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { coverLetterSummaryFixture } from '@/features/cover-letters/testFixtures'
import {
  INTERVIEW_AGENT_RUN_ID,
  INTERVIEW_COVER_LETTER_ID,
  INTERVIEW_JOB_ID,
  INTERVIEW_QUESTION_SET_ID,
  INTERVIEW_RESEARCH_RUN_ID,
  page,
} from '@/features/interviews/testFixtures'
import { ApiClientError } from '@/shared/api/errors'

import JobInterviewPage from './JobInterviewPage.vue'

const TestApp = { template: '<RouterView />' }
const mocks = vi.hoisted(() => ({
  job: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  covers: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  sets: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  prepare: {
    isPending: { value: false },
    mutateAsync: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))
vi.mock('@/features/jobs/queries', () => ({
  useJobDetailQuery: () => mocks.job,
}))
vi.mock('@/features/cover-letters/queries', () => ({
  useCoverLetterListQuery: () => mocks.covers,
}))
vi.mock('@/features/interviews/queries', () => ({
  useQuestionSetListQuery: () => mocks.sets,
  useCreateInterviewPreparationMutation: () => mocks.prepare,
}))

describe('P8 Job interview preparation page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.job.data.value = {
      id: INTERVIEW_JOB_ID,
      companyName: 'Hiresemble',
      positionName: 'Backend Engineer',
      title: 'Backend Engineer 채용',
      latestAnalysis: { id: uuid(90) },
      interviewPreparationCount: 0,
    }
    mocks.job.error.value = null
    mocks.job.isLoading.value = false
    mocks.job.isError.value = false
    mocks.covers.data.value = page([
      coverLetterSummaryFixture({
        id: INTERVIEW_COVER_LETTER_ID,
        job: {
          id: INTERVIEW_JOB_ID,
          companyName: 'Hiresemble',
          positionName: 'Backend Engineer',
          title: 'Backend Engineer 채용',
        },
        answeredQuestionCount: 1,
      }),
    ])
    mocks.covers.error.value = null
    mocks.covers.isLoading.value = false
    mocks.covers.isError.value = false
    mocks.sets.data.value = page([])
    mocks.sets.error.value = null
    mocks.sets.isLoading.value = false
    mocks.sets.isError.value = false
    mocks.prepare.isPending.value = false
    mocks.prepare.mutateAsync.mockResolvedValue({
      questionSetId: INTERVIEW_QUESTION_SET_ID,
      researchRunId: INTERVIEW_RESEARCH_RUN_ID,
      agentRunId: INTERVIEW_AGENT_RUN_ID,
      status: 'QUEUED',
    })
  })

  it('submits only the canonical preparation fields and connects the accepted run', async () => {
    const { wrapper } = await mountPage()
    const selects = wrapper.findAll<HTMLSelectElement>('.job-interview__form select')
    await selects[0]!.setValue(INTERVIEW_COVER_LETTER_ID)
    await selects[1]!.setValue('ADVANCED')
    await selects[2]!.setValue('ECONOMY')
    await wrapper.get<HTMLInputElement>('input[type="number"]').setValue('12')
    expect(selects[0]!.element.value).toBe(INTERVIEW_COVER_LETTER_ID)
    const submit = wrapper.get<HTMLButtonElement>('[data-testid="submit-interview-preparation"]')
    expect(submit.attributes('disabled')).toBeUndefined()
    await wrapper.get('form.job-interview__form-grid').trigger('submit')
    await flushPromises()

    expect(mocks.prepare.mutateAsync).toHaveBeenCalledWith({
      jobId: INTERVIEW_JOB_ID,
      request: {
        coverLetterId: INTERVIEW_COVER_LETTER_ID,
        researchQuality: 'ADVANCED',
        qualityMode: 'ECONOMY',
        questionTypes: [
          'COVER_LETTER',
          'TECHNICAL',
          'PROJECT_DEEP_DIVE',
          'BEHAVIORAL',
          'COMPANY_MOTIVATION',
        ],
        questionCount: 12,
      },
    })
    expect(wrapper.get('[data-testid="run-monitor"]').attributes()).toMatchObject({
      'agent-run-id': INTERVIEW_AGENT_RUN_ID,
      'resource-id': INTERVIEW_QUESTION_SET_ID,
    })
  })

  it('does not hard-gate on profile completion and explains server prerequisite codes', async () => {
    mocks.job.data.value = {
      id: INTERVIEW_JOB_ID,
      companyName: 'Hiresemble',
      positionName: 'Backend Engineer',
      title: 'Backend Engineer 채용',
      interviewPreparationCount: 0,
      profileCompleted: false,
      latestAnalysis: null,
    }
    mocks.prepare.mutateAsync.mockRejectedValue(
      new ApiClientError({
        status: 404,
        code: 'JOB_ANALYSIS_NOT_FOUND',
        message: '공고 분석 결과를 찾을 수 없습니다.',
      }),
    )
    const { wrapper } = await mountPage()
    const coverSelect = wrapper.findAll<HTMLSelectElement>('.job-interview__form select')[0]!
    await coverSelect.setValue(INTERVIEW_COVER_LETTER_ID)
    const submit = wrapper.get<HTMLButtonElement>('[data-testid="submit-interview-preparation"]')
    expect(submit.attributes('disabled')).toBeUndefined()
    await wrapper.get('form.job-interview__form-grid').trigger('submit')
    await flushPromises()

    expect(mocks.prepare.mutateAsync).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('프로필 완성 여부와 관계없이 접수할 수 있어요')
    expect(wrapper.text()).toContain('먼저 공고 분석을 완료해 주세요')
    expect(wrapper.get(`a[href="/jobs/${INTERVIEW_JOB_ID}/analysis"]`).text()).toBe(
      '공고 분석으로 이동',
    )
  })

  it('keeps the submit action locked while the idempotent mutation is pending', async () => {
    mocks.prepare.isPending.value = true
    const { wrapper } = await mountPage()
    const submit = wrapper.get<HTMLButtonElement>('[data-testid="submit-interview-preparation"]')
    expect(submit.attributes('disabled')).toBeDefined()
    await submit.trigger('click')
    expect(mocks.prepare.mutateAsync).not.toHaveBeenCalled()
  })
})

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/jobs/:jobId/interview',
        name: 'job-interview',
        component: JobInterviewPage,
      },
      {
        path: '/jobs/:jobId/analysis',
        name: 'job-analysis',
        component: { template: '<div />' },
      },
      {
        path: '/jobs/:jobId/cover-letter',
        name: 'job-cover-letter',
        component: { template: '<div />' },
      },
      {
        path: '/interview-question-sets/:questionSetId',
        name: 'interview-question-set',
        component: { template: '<div />' },
      },
      {
        path: '/jobs',
        name: 'jobs',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/jobs/${INTERVIEW_JOB_ID}/interview`)
  await router.isReady()
  const wrapper = mount(TestApp, {
    global: {
      plugins: [router],
      stubs: {
        InterviewRunMonitor: {
          props: ['agentRunId', 'resourceId'],
          template:
            '<div data-testid="run-monitor" :agent-run-id="agentRunId" :resource-id="resourceId" />',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
