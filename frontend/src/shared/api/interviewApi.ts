import type { ZodType } from 'zod'

import { runAcceptedSchema, type RunAcceptedDto } from './agentRunContracts'
import {
  interviewAnswerVersionPageSchema,
  interviewAnswerVersionSchema,
  interviewFeedbackPageSchema,
  interviewPreparationAcceptedSchema,
  interviewQuestionSchema,
  questionSetDetailSchema,
  questionSetPageSchema,
  researchRetryAcceptedSchema,
  researchRunSchema,
  researchSourcePageSchema,
  type CreateInterviewAnswerVersionRequest,
  type CreateInterviewPreparationRequest,
  type InterviewAnswerFeedbackRequest,
  type InterviewAnswerVersionDto,
  type InterviewAnswerVersionPageDto,
  type InterviewFeedbackPageDto,
  type InterviewPreparationAcceptedDto,
  type InterviewQuestionDto,
  type QuestionSetDetailDto,
  type QuestionSetPageDto,
  type ResearchRetryAcceptedDto,
  type ResearchRetryRequest,
  type ResearchRunDto,
  type ResearchRunStatus,
  type ResearchSourcePageDto,
  type ResearchSourceType,
  type ResearchTopic,
  type SourceCoverage,
} from './interviewContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export const QUESTION_SET_SORTS = ['updatedAt,desc', 'createdAt,desc'] as const
export const RESEARCH_SOURCE_SORTS = ['providerRank,asc', 'retrievedAt,desc'] as const
export const INTERVIEW_ANSWER_SORTS = ['versionNo,desc', 'createdAt,desc'] as const
export const INTERVIEW_FEEDBACK_SORTS = ['createdAt,desc'] as const

export type QuestionSetSort = (typeof QUESTION_SET_SORTS)[number]
export type ResearchSourceSort = (typeof RESEARCH_SOURCE_SORTS)[number]
export type InterviewAnswerSort = (typeof INTERVIEW_ANSWER_SORTS)[number]
export type InterviewFeedbackSort = (typeof INTERVIEW_FEEDBACK_SORTS)[number]

export interface QuestionSetListParams {
  jobId?: string
  coverLetterId?: string
  query?: string
  sourceCoverage?: SourceCoverage
  researchStatus?: ResearchRunStatus
  page?: number
  size?: number
  sort?: QuestionSetSort
}

export interface ResearchSourceListParams {
  topic?: ResearchTopic
  sourceType?: ResearchSourceType
  page?: number
  size?: number
  sort?: ResearchSourceSort
}

export interface InterviewAnswerVersionListParams {
  page?: number
  size?: number
  sort?: InterviewAnswerSort
}

export interface InterviewFeedbackListParams {
  page?: number
  size?: number
  sort?: InterviewFeedbackSort
}

export async function createInterviewPreparation(
  jobId: string,
  request: CreateInterviewPreparationRequest,
  idempotencyKey: string,
): Promise<InterviewPreparationAcceptedDto> {
  const response = await apiClient.client.post<unknown>(
    `/jobs/${encodeURIComponent(jobId)}/interview-preparations`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 202) throw invalidServerResponse()
  return parse(interviewPreparationAcceptedSchema, response.data)
}

export async function listInterviewQuestionSets(
  params: QuestionSetListParams = {},
): Promise<QuestionSetPageDto> {
  return parse(
    questionSetPageSchema,
    await apiClient.get<unknown>('/interview-question-sets', { params }),
  )
}

export async function getInterviewQuestionSet(
  questionSetId: string,
): Promise<QuestionSetDetailDto> {
  return parse(
    questionSetDetailSchema,
    await apiClient.get<unknown>(`/interview-question-sets/${encodeURIComponent(questionSetId)}`),
  )
}

export async function getResearchRun(researchRunId: string): Promise<ResearchRunDto> {
  return parse(
    researchRunSchema,
    await apiClient.get<unknown>(`/research-runs/${encodeURIComponent(researchRunId)}`),
  )
}

export async function listResearchSources(
  researchRunId: string,
  params: ResearchSourceListParams = {},
): Promise<ResearchSourcePageDto> {
  return parse(
    researchSourcePageSchema,
    await apiClient.get<unknown>(`/research-runs/${encodeURIComponent(researchRunId)}/sources`, {
      params,
    }),
  )
}

export async function retryResearchRun(
  researchRunId: string,
  request: ResearchRetryRequest,
  idempotencyKey: string,
): Promise<ResearchRetryAcceptedDto> {
  const response = await apiClient.client.post<unknown>(
    `/research-runs/${encodeURIComponent(researchRunId)}/retry`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 202) throw invalidServerResponse()
  return parse(researchRetryAcceptedSchema, response.data)
}

export async function getInterviewQuestion(questionId: string): Promise<InterviewQuestionDto> {
  return parse(
    interviewQuestionSchema,
    await apiClient.get<unknown>(`/interview-questions/${encodeURIComponent(questionId)}`),
  )
}

export async function listInterviewAnswerVersions(
  questionId: string,
  params: InterviewAnswerVersionListParams = {},
): Promise<InterviewAnswerVersionPageDto> {
  return parse(
    interviewAnswerVersionPageSchema,
    await apiClient.get<unknown>(
      `/interview-questions/${encodeURIComponent(questionId)}/answer-versions`,
      { params },
    ),
  )
}

export async function saveInterviewAnswerVersion(
  questionId: string,
  request: CreateInterviewAnswerVersionRequest,
): Promise<InterviewAnswerVersionDto> {
  const response = await apiClient.client.post<unknown>(
    `/interview-questions/${encodeURIComponent(questionId)}/answer-versions`,
    request,
  )
  if (response.status !== 201) throw invalidServerResponse()
  return parse(interviewAnswerVersionSchema, response.data)
}

export async function requestInterviewAnswerFeedback(
  versionId: string,
  request: InterviewAnswerFeedbackRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const response = await apiClient.client.post<unknown>(
    `/interview-answer-versions/${encodeURIComponent(versionId)}/feedback`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 202) throw invalidServerResponse()
  const accepted = parse(runAcceptedSchema, response.data)
  if (accepted.resourceType !== 'INTERVIEW_ANSWER_VERSION' || accepted.resourceId !== versionId) {
    throw invalidServerResponse()
  }
  return accepted
}

export async function listInterviewAnswerFeedbacks(
  versionId: string,
  params: InterviewFeedbackListParams = {},
): Promise<InterviewFeedbackPageDto> {
  return parse(
    interviewFeedbackPageSchema,
    await apiClient.get<unknown>(
      `/interview-answer-versions/${encodeURIComponent(versionId)}/feedbacks`,
      { params },
    ),
  )
}

export function createInterviewIdempotencyKey(
  operation: 'prepare' | 'research-retry' | 'feedback',
): string {
  return `interview-${operation}:${globalThis.crypto.randomUUID()}`
}

function parse<T>(schema: ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value)
  if (result.success) return result.data
  throw invalidServerResponse()
}

function invalidServerResponse(): ApiClientError {
  return new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: '면접 준비 정보를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  })
}
