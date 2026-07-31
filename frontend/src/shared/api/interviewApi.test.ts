import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  INTERVIEW_AGENT_RUN_ID,
  INTERVIEW_ANSWER_ID,
  INTERVIEW_COVER_LETTER_ID,
  INTERVIEW_FEEDBACK_RUN_ID,
  INTERVIEW_JOB_ID,
  INTERVIEW_QUESTION_ID,
  INTERVIEW_QUESTION_SET_ID,
  INTERVIEW_RESEARCH_RUN_ID,
  answerFixture,
  feedbackFixture,
  page,
  questionFixture,
  questionSetDetailFixture,
  questionSetSummaryFixture,
  researchFixture,
  sourceFixture,
} from '@/features/interviews/testFixtures'

import { ApiClientError } from './errors'
import { apiClient } from './http'
import * as interviewApi from './interviewApi'

describe('P8 interview API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps all eleven operations and exact idempotency/status contracts', async () => {
    const rawPost = vi
      .spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({
        status: 202,
        data: preparationAccepted(),
      })
      .mockResolvedValueOnce({
        status: 202,
        data: researchRetryAccepted(),
      })
      .mockResolvedValueOnce({
        status: 201,
        data: answerFixture(),
      })
      .mockResolvedValueOnce({
        status: 202,
        data: feedbackAccepted(),
      })
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(page([questionSetSummaryFixture()]))
      .mockResolvedValueOnce(questionSetDetailFixture())
      .mockResolvedValueOnce(researchFixture())
      .mockResolvedValueOnce(page([sourceFixture()]))
      .mockResolvedValueOnce(questionFixture())
      .mockResolvedValueOnce(page([answerFixture()]))
      .mockResolvedValueOnce(page([feedbackFixture()]))

    await interviewApi.createInterviewPreparation(
      INTERVIEW_JOB_ID,
      {
        coverLetterId: INTERVIEW_COVER_LETTER_ID,
        researchQuality: 'BASIC',
        qualityMode: 'BALANCED',
        questionTypes: ['TECHNICAL', 'BEHAVIORAL'],
        questionCount: 8,
      },
      'interview-prepare:key',
    )
    await interviewApi.listInterviewQuestionSets({
      jobId: INTERVIEW_JOB_ID,
      sourceCoverage: 'SUFFICIENT',
      sort: 'updatedAt,desc',
    })
    await interviewApi.getInterviewQuestionSet(INTERVIEW_QUESTION_SET_ID)
    await interviewApi.getResearchRun(INTERVIEW_RESEARCH_RUN_ID)
    await interviewApi.listResearchSources(INTERVIEW_RESEARCH_RUN_ID, {
      topic: 'COMPANY',
      sourceType: 'OFFICIAL',
      sort: 'providerRank,asc',
    })
    await interviewApi.retryResearchRun(
      INTERVIEW_RESEARCH_RUN_ID,
      { researchQuality: 'ADVANCED', qualityMode: 'BALANCED' },
      'interview-retry:key',
    )
    await interviewApi.getInterviewQuestion(INTERVIEW_QUESTION_ID)
    await interviewApi.listInterviewAnswerVersions(INTERVIEW_QUESTION_ID)
    await interviewApi.saveInterviewAnswerVersion(INTERVIEW_QUESTION_ID, {
      content: '명시적으로 저장한 답변',
      parentVersionId: INTERVIEW_ANSWER_ID,
    })
    await interviewApi.requestInterviewAnswerFeedback(
      INTERVIEW_ANSWER_ID,
      { qualityMode: 'BALANCED' },
      'interview-feedback:key',
    )
    await interviewApi.listInterviewAnswerFeedbacks(INTERVIEW_ANSWER_ID)

    expect(rawPost).toHaveBeenNthCalledWith(
      1,
      `/jobs/${INTERVIEW_JOB_ID}/interview-preparations`,
      expect.objectContaining({ questionCount: 8 }),
      { headers: { 'Idempotency-Key': 'interview-prepare:key' } },
    )
    expect(get).toHaveBeenNthCalledWith(1, '/interview-question-sets', {
      params: {
        jobId: INTERVIEW_JOB_ID,
        sourceCoverage: 'SUFFICIENT',
        sort: 'updatedAt,desc',
      },
    })
    expect(get).toHaveBeenNthCalledWith(4, `/research-runs/${INTERVIEW_RESEARCH_RUN_ID}/sources`, {
      params: {
        topic: 'COMPANY',
        sourceType: 'OFFICIAL',
        sort: 'providerRank,asc',
      },
    })
    expect(rawPost).toHaveBeenNthCalledWith(
      2,
      `/research-runs/${INTERVIEW_RESEARCH_RUN_ID}/retry`,
      { researchQuality: 'ADVANCED', qualityMode: 'BALANCED' },
      { headers: { 'Idempotency-Key': 'interview-retry:key' } },
    )
    expect(rawPost).toHaveBeenNthCalledWith(
      3,
      `/interview-questions/${INTERVIEW_QUESTION_ID}/answer-versions`,
      {
        content: '명시적으로 저장한 답변',
        parentVersionId: INTERVIEW_ANSWER_ID,
      },
    )
    expect(rawPost).toHaveBeenNthCalledWith(
      4,
      `/interview-answer-versions/${INTERVIEW_ANSWER_ID}/feedback`,
      { qualityMode: 'BALANCED' },
      { headers: { 'Idempotency-Key': 'interview-feedback:key' } },
    )
  })

  it('never sends client-controlled source, status, role, model or provider fields', async () => {
    const post = vi.spyOn(apiClient.client, 'post').mockResolvedValue({
      status: 201,
      data: answerFixture(),
    })
    await interviewApi.saveInterviewAnswerVersion(INTERVIEW_QUESTION_ID, {
      content: '사용자 답변',
      parentVersionId: null,
    })
    const request = post.mock.calls[0]?.[1]
    expect(request).toEqual({ content: '사용자 답변', parentVersionId: null })
    expect(request).not.toHaveProperty('sourceType')
    expect(request).not.toHaveProperty('status')
    expect(request).not.toHaveProperty('role')
    expect(request).not.toHaveProperty('model')
    expect(request).not.toHaveProperty('provider')
  })

  it('rejects wrong statuses, malformed payloads and mismatched feedback resources', async () => {
    vi.spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({ status: 201, data: preparationAccepted() })
      .mockResolvedValueOnce({
        status: 202,
        data: {
          ...feedbackAccepted(),
          resourceId: INTERVIEW_QUESTION_SET_ID,
        },
      })
    await expect(
      interviewApi.createInterviewPreparation(
        INTERVIEW_JOB_ID,
        {
          coverLetterId: INTERVIEW_COVER_LETTER_ID,
          researchQuality: 'BASIC',
          qualityMode: 'BALANCED',
          questionTypes: ['TECHNICAL'],
          questionCount: 1,
        },
        'key',
      ),
    ).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    } satisfies Partial<ApiClientError>)
    await expect(
      interviewApi.requestInterviewAnswerFeedback(
        INTERVIEW_ANSWER_ID,
        { qualityMode: 'BALANCED' },
        'key',
      ),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' })

    vi.spyOn(apiClient, 'get').mockResolvedValue({ items: [{ id: 'bad' }] })
    await expect(interviewApi.listInterviewQuestionSets()).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
  })
})

function preparationAccepted() {
  return {
    questionSetId: INTERVIEW_QUESTION_SET_ID,
    researchRunId: INTERVIEW_RESEARCH_RUN_ID,
    agentRunId: INTERVIEW_AGENT_RUN_ID,
    status: 'QUEUED',
  }
}

function researchRetryAccepted() {
  return {
    ...preparationAccepted(),
    retryOfResearchRunId: INTERVIEW_RESEARCH_RUN_ID,
  }
}

function feedbackAccepted() {
  return {
    agentRunId: INTERVIEW_FEEDBACK_RUN_ID,
    status: 'QUEUED',
    resourceType: 'INTERVIEW_ANSWER_VERSION',
    resourceId: INTERVIEW_ANSWER_ID,
    replayed: false,
  }
}
