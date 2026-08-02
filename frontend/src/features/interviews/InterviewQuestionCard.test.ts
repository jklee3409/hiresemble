import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from '@/shared/api/errors'

import InterviewQuestionCard from './InterviewQuestionCard.vue'
import {
  INTERVIEW_ANSWER_ID,
  INTERVIEW_QUESTION_SET_ID,
  answerFixture,
  feedbackFixture,
  page,
  questionFixture,
} from './testFixtures'

const mocks = vi.hoisted(() => ({
  save: vi.fn(),
  requestFeedback: vi.fn(),
  answerRefetch: vi.fn(),
  feedbackRefetch: vi.fn(),
  getQuestion: vi.fn(),
  answerPage: {
    value: {
      items: [] as unknown[],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    },
  },
  feedbackPage: {
    value: {
      items: [] as unknown[],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    },
  },
  activeRuns: {
    data: {
      value: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } as unknown,
    },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
}))

vi.mock('./queries', () => ({
  useInterviewAnswerVersionListQuery: () => ({
    data: mocks.answerPage,
    isLoading: { value: false },
    isError: { value: false },
    refetch: mocks.answerRefetch,
  }),
  useInterviewFeedbackListQuery: () => ({
    data: mocks.feedbackPage,
    isLoading: { value: false },
    isError: { value: false },
    refetch: mocks.feedbackRefetch,
  }),
  useSaveInterviewAnswerMutation: () => ({
    isPending: { value: false },
    mutateAsync: mocks.save,
  }),
  useRequestInterviewFeedbackMutation: () => ({
    isPending: { value: false },
    mutateAsync: mocks.requestFeedback,
  }),
}))

vi.mock('@/shared/api/interviewApi', () => ({
  getInterviewQuestion: mocks.getQuestion,
}))

vi.mock('@/features/agent-runs/queries', () => ({
  useActiveAgentRunsQuery: () => mocks.activeRuns,
}))

describe('P8 interview answer conflict and feedback UI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const current = answerFixture()
    mocks.answerPage.value = page([current])
    mocks.feedbackPage.value = page([])
    mocks.activeRuns.data.value = page([])
    mocks.answerRefetch.mockResolvedValue(undefined)
    mocks.feedbackRefetch.mockResolvedValue(undefined)
    mocks.save.mockResolvedValue(current)
  })

  it('keeps the immutable submitted snapshot and reapplies only after explicit confirmation', async () => {
    const serverAnswer = answerFixture({
      id: uuid(70),
      parentVersionId: INTERVIEW_ANSWER_ID,
      versionNo: 2,
      content: '서버에서 먼저 저장된 최신 답변',
    })
    mocks.save.mockRejectedValueOnce(versionConflict()).mockResolvedValueOnce(
      answerFixture({
        id: uuid(71),
        parentVersionId: serverAnswer.id,
        versionNo: 3,
        content: '내가 제출한 답변 snapshot',
      }),
    )
    mocks.getQuestion.mockResolvedValue(
      questionFixture({
        currentAnswer: serverAnswer,
      }),
    )
    const wrapper = mountCard()

    await wrapper.get('textarea').setValue('내가 제출한 답변 snapshot')
    await wrapper
      .get(`[data-testid="save-interview-answer-${questionFixture().id}"]`)
      .trigger('click')
    await flushPromises()

    const conflict = wrapper.get(`[data-testid="answer-conflict-${questionFixture().id}"]`)
    expect(conflict.text()).toContain('내가 제출한 답변 snapshot')
    expect(conflict.text()).toContain('서버에서 먼저 저장된 최신 답변')
    expect(mocks.save).toHaveBeenCalledTimes(1)

    await wrapper.get('textarea').setValue('충돌 화면 이후 다시 입력한 값')
    await wrapper
      .get(`[data-testid="reapply-interview-answer-${questionFixture().id}"]`)
      .trigger('click')
    await flushPromises()

    expect(mocks.save).toHaveBeenCalledTimes(2)
    expect(mocks.save).toHaveBeenLastCalledWith({
      questionSetId: INTERVIEW_QUESTION_SET_ID,
      questionId: questionFixture().id,
      request: {
        content: '내가 제출한 답변 snapshot',
        parentVersionId: serverAnswer.id,
      },
    })
  })

  it('cancels a conflict without another mutation and restores the server answer', async () => {
    const serverAnswer = answerFixture({
      id: uuid(72),
      parentVersionId: INTERVIEW_ANSWER_ID,
      versionNo: 2,
      content: '취소 후 사용할 서버 답변',
    })
    mocks.save.mockRejectedValueOnce(versionConflict())
    mocks.getQuestion.mockResolvedValue(questionFixture({ currentAnswer: serverAnswer }))
    const wrapper = mountCard()

    await wrapper.get('textarea').setValue('저장하려던 로컬 답변')
    await wrapper
      .get(`[data-testid="save-interview-answer-${questionFixture().id}"]`)
      .trigger('click')
    await flushPromises()
    await wrapper.get('.answer-conflict button.button--secondary').trigger('click')
    await flushPromises()

    expect(mocks.save).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.answer-conflict').exists()).toBe(false)
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe(
      '취소 후 사용할 서버 답변',
    )
  })

  it('targets one immutable answer version and shows only successful feedback history', async () => {
    const older = answerFixture({ isCurrent: false })
    const current = answerFixture({
      id: uuid(73),
      parentVersionId: older.id,
      versionNo: 2,
      content: '새 답변',
    })
    mocks.answerPage.value = page([current, older])
    mocks.feedbackPage.value = page([feedbackFixture({ answerVersionId: older.id })])
    mocks.requestFeedback.mockResolvedValue({
      agentRunId: uuid(74),
      status: 'QUEUED',
      resourceType: 'INTERVIEW_ANSWER_VERSION',
      resourceId: older.id,
      replayed: false,
    })
    const wrapper = mountCard(questionFixture({ currentAnswer: current, latestFeedback: null }))

    const oldVersionRadio = wrapper
      .findAll<HTMLInputElement>('input[type="radio"]')
      .find((radio) => radio.attributes('value') === older.id)
    await oldVersionRadio?.setValue(true)
    await flushPromises()

    expect(wrapper.text()).toContain('답변 버전 1 피드백')
    expect(wrapper.text()).toContain('본인의 역할을 분명히 설명했어요.')
    await wrapper
      .get(`[data-testid="request-interview-feedback-${questionFixture().id}"]`)
      .trigger('click')
    await flushPromises()
    expect(mocks.requestFeedback).toHaveBeenCalledWith({
      questionSetId: INTERVIEW_QUESTION_SET_ID,
      answerVersionId: older.id,
      request: { qualityMode: 'BALANCED' },
    })
  })

  it('restores feedback for the selected answer version and blocks a duplicate request', async () => {
    mocks.activeRuns.data.value = page([
      {
        id: uuid(75),
        workflowType: 'INTERVIEW_ANSWER_FEEDBACK',
        resourceType: 'INTERVIEW_ANSWER_VERSION',
        resourceId: INTERVIEW_ANSWER_ID,
        status: 'RUNNING',
      },
    ])
    const wrapper = mountCard()
    const request = wrapper.get(
      `[data-testid="request-interview-feedback-${questionFixture().id}"]`,
    )

    expect(request.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="run-monitor"]').exists()).toBe(true)
    await request.trigger('click')
    expect(mocks.requestFeedback).not.toHaveBeenCalled()
  })
})

function mountCard(question = questionFixture()) {
  return mount(InterviewQuestionCard, {
    props: {
      userId: 'user-1',
      questionSetId: INTERVIEW_QUESTION_SET_ID,
      question,
    },
    global: {
      stubs: {
        InterviewRunMonitor: { template: '<div data-testid="run-monitor" />' },
      },
    },
  })
}

function versionConflict(): ApiClientError {
  return new ApiClientError({
    message: '최신 서버 답변과 충돌했어요.',
    status: 409,
    code: 'RESOURCE_VERSION_CONFLICT',
  })
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
