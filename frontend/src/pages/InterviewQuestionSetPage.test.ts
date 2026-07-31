import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  INTERVIEW_QUESTION_SET_ID,
  page,
  questionSetDetailFixture,
  sourceFixture,
} from '@/features/interviews/testFixtures'

import InterviewQuestionSetPage from './InterviewQuestionSetPage.vue'

const TestApp = { template: '<RouterView />' }
const mocks = vi.hoisted(() => ({
  detail: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  sources: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  retry: {
    isPending: { value: false },
    mutateAsync: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/interviews/queries', () => ({
  useQuestionSetDetailQuery: () => mocks.detail,
  useResearchSourceListQuery: () => mocks.sources,
  useRetryResearchMutation: () => mocks.retry,
}))

describe('P8 interview question-set page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.detail.data.value = questionSetDetailFixture()
    mocks.detail.error.value = null
    mocks.detail.isLoading.value = false
    mocks.detail.isError.value = false
    mocks.sources.data.value = page([
      sourceFixture(),
      sourceFixture({
        id: uuid(80),
        sourceType: 'INTERVIEW_REVIEW',
        topic: 'INTERVIEW_PROCESS',
        title: '익명 면접 후기',
        sourceUrl: 'https://reviews.example.com/interview',
        reliabilityNotice: '익명 후기는 개인 경험이며 사실을 보장하지 않습니다.',
      }),
    ])
    mocks.sources.error.value = null
    mocks.sources.isLoading.value = false
    mocks.sources.isError.value = false
  })

  it('distinguishes official and review sources with safe external links', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('공식 출처')
    expect(wrapper.text()).toContain('면접 후기')
    expect(wrapper.text()).toContain('익명 후기는 개인 경험이며 사실을 보장하지 않습니다.')
    const links = wrapper.findAll<HTMLAnchorElement>('a[target="_blank"]')
    expect(links).toHaveLength(2)
    for (const link of links) {
      expect(link.attributes('rel')).toBe('noopener noreferrer')
    }
  })

  it('treats LIMITED and NONE as successful warnings, not provider failures', async () => {
    mocks.detail.data.value = questionSetDetailFixture({
      sourceCoverage: 'LIMITED',
      research: {
        ...questionSetDetailFixture().research,
        sourceCoverage: 'LIMITED',
        missingCoverageTopics: ['유사 직무 면접'],
      },
    })
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="source-coverage-warning"]').text()).toContain(
      '공개 출처가 제한적',
    )
    expect(wrapper.text()).not.toContain('조사 실패')
    expect(wrapper.find('[data-testid="retry-research"]').exists()).toBe(false)
  })

  it('offers a lineage-preserving retry only for retryable provider failure', async () => {
    mocks.detail.data.value = questionSetDetailFixture({
      agentRun: {
        ...questionSetDetailFixture().agentRun,
        status: 'FAILED',
        progressPercent: 50,
      },
      research: {
        ...questionSetDetailFixture().research,
        status: 'FAILED',
        sourceCoverage: null,
        retryable: true,
        safeError: {
          code: 'AI_PROVIDER_UNAVAILABLE',
          message: '검색 제공자에 연결하지 못했어요.',
        },
      },
      questions: [],
      questionCount: 0,
    })
    mocks.retry.mutateAsync.mockResolvedValue({
      questionSetId: uuid(81),
      researchRunId: uuid(82),
      agentRunId: uuid(83),
      retryOfResearchRunId: questionSetDetailFixture().research.id,
      status: 'QUEUED',
    })
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="retry-research"]').trigger('click')
    await flushPromises()

    expect(mocks.retry.mutateAsync).toHaveBeenCalledWith({
      researchRunId: questionSetDetailFixture().research.id,
      request: {},
    })
    expect(router.currentRoute.value.params.questionSetId).toBe(uuid(81))
  })
})

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/interview-question-sets/:questionSetId',
        name: 'interview-question-set',
        component: InterviewQuestionSetPage,
      },
      {
        path: '/interviews',
        name: 'interviews',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/interview-question-sets/${INTERVIEW_QUESTION_SET_ID}`)
  await router.isReady()
  const wrapper = mount(TestApp, {
    global: {
      plugins: [router],
      stubs: {
        InterviewRunMonitor: { template: '<div data-testid="run-monitor" />' },
        InterviewQuestionCard: {
          props: ['question'],
          template: '<article data-testid="question-card">{{ question.questionText }}</article>',
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
