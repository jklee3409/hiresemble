import type { ZodType } from 'zod'

import { runAcceptedSchema, type RunAcceptedDto } from './agentRunContracts'
import {
  answerVersionPageSchema,
  answerVersionSchema,
  coverLetterDetailSchema,
  coverLetterPageSchema,
  coverLetterQuestionSchema,
  verificationPageSchema,
  type AnswerVersionPageDto,
  type CoverLetterAnswerVersionDto,
  type CoverLetterDetailDto,
  type CoverLetterPageDto,
  type CoverLetterQuestionDto,
  type CoverLetterStatus,
  type CreateCoverLetterRequest,
  type CreateQuestionRequest,
  type FinalizeCoverLetterRequest,
  type GenerateCoverLetterRequest,
  type ReorderQuestionsRequest,
  type RestoreAnswerVersionRequest,
  type SaveAnswerVersionRequest,
  type UpdateCoverLetterRequest,
  type UpdateQuestionRequest,
  type VerificationPageDto,
  type VerifyAnswerVersionRequest,
  type VersionCommandRequest,
} from './coverLetterContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export const COVER_LETTER_SORTS = ['updatedAt,desc', 'createdAt,desc', 'title,asc'] as const
export const ANSWER_VERSION_SORTS = ['versionNo,desc', 'createdAt,desc'] as const
export const VERIFICATION_SORTS = ['createdAt,desc'] as const

export type CoverLetterSort = (typeof COVER_LETTER_SORTS)[number]
export type AnswerVersionSort = (typeof ANSWER_VERSION_SORTS)[number]
export type VerificationSort = (typeof VERIFICATION_SORTS)[number]

export interface CoverLetterListParams {
  jobId?: string
  status?: CoverLetterStatus
  query?: string
  page?: number
  size?: number
  sort?: CoverLetterSort
}

export interface AnswerVersionListParams {
  page?: number
  size?: number
  sort?: AnswerVersionSort
}

export interface VerificationListParams {
  page?: number
  size?: number
  sort?: VerificationSort
}

export async function createCoverLetter(
  jobId: string,
  request: CreateCoverLetterRequest,
  idempotencyKey: string,
): Promise<CoverLetterDetailDto> {
  const response = await apiClient.client.post<unknown>(
    `/jobs/${encodeURIComponent(jobId)}/cover-letter`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 201) throw invalidServerResponse()
  return parse(coverLetterDetailSchema, response.data)
}

export async function listCoverLetters(
  params: CoverLetterListParams = {},
): Promise<CoverLetterPageDto> {
  const value = await apiClient.get<unknown>('/cover-letters', { params })
  return parse(coverLetterPageSchema, value)
}

export async function getCoverLetter(coverLetterId: string): Promise<CoverLetterDetailDto> {
  const value = await apiClient.get<unknown>(`/cover-letters/${encodeURIComponent(coverLetterId)}`)
  return parse(coverLetterDetailSchema, value)
}

export async function updateCoverLetter(
  coverLetterId: string,
  request: UpdateCoverLetterRequest,
): Promise<CoverLetterDetailDto> {
  const value = await apiClient.put<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}`,
    request,
  )
  return parse(coverLetterDetailSchema, value)
}

export async function createCoverLetterQuestion(
  coverLetterId: string,
  request: CreateQuestionRequest,
): Promise<CoverLetterQuestionDto> {
  const response = await apiClient.client.post<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/questions`,
    request,
  )
  if (response.status !== 201) throw invalidServerResponse()
  return parse(coverLetterQuestionSchema, response.data)
}

export async function updateCoverLetterQuestion(
  coverLetterId: string,
  questionId: string,
  request: UpdateQuestionRequest,
): Promise<CoverLetterQuestionDto> {
  const value = await apiClient.put<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/questions/${encodeURIComponent(questionId)}`,
    request,
  )
  return parse(coverLetterQuestionSchema, value)
}

export function deleteCoverLetterQuestion(
  coverLetterId: string,
  questionId: string,
  version: number,
): Promise<void> {
  return apiClient.delete(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/questions/${encodeURIComponent(questionId)}`,
    { params: { version } },
  )
}

export async function reorderCoverLetterQuestions(
  coverLetterId: string,
  request: ReorderQuestionsRequest,
): Promise<CoverLetterDetailDto> {
  const value = await apiClient.patch<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/questions/order`,
    request,
  )
  return parse(coverLetterDetailSchema, value)
}

export async function generateCoverLetter(
  coverLetterId: string,
  request: GenerateCoverLetterRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const response = await apiClient.client.post<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/generate`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 202) throw invalidServerResponse()
  const accepted = parse(runAcceptedSchema, response.data)
  if (accepted.resourceType !== 'COVER_LETTER' || accepted.resourceId !== coverLetterId) {
    throw invalidServerResponse()
  }
  return accepted
}

export async function listAnswerVersions(
  questionId: string,
  params: AnswerVersionListParams = {},
): Promise<AnswerVersionPageDto> {
  const value = await apiClient.get<unknown>(
    `/cover-letter-questions/${encodeURIComponent(questionId)}/versions`,
    { params },
  )
  return parse(answerVersionPageSchema, value)
}

export async function saveAnswerVersion(
  questionId: string,
  request: SaveAnswerVersionRequest,
): Promise<CoverLetterAnswerVersionDto> {
  const response = await apiClient.client.post<unknown>(
    `/cover-letter-questions/${encodeURIComponent(questionId)}/versions`,
    request,
  )
  if (response.status !== 201) throw invalidServerResponse()
  return parse(answerVersionSchema, response.data)
}

export async function restoreAnswerVersion(
  questionId: string,
  versionId: string,
  request: RestoreAnswerVersionRequest,
): Promise<CoverLetterAnswerVersionDto> {
  const response = await apiClient.client.post<unknown>(
    `/cover-letter-questions/${encodeURIComponent(questionId)}/versions/${encodeURIComponent(versionId)}/restore`,
    request,
  )
  if (response.status !== 201) throw invalidServerResponse()
  return parse(answerVersionSchema, response.data)
}

export async function verifyAnswerVersion(
  versionId: string,
  request: VerifyAnswerVersionRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const response = await apiClient.client.post<unknown>(
    `/cover-letter-answer-versions/${encodeURIComponent(versionId)}/verify`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (response.status !== 202) throw invalidServerResponse()
  const accepted = parse(runAcceptedSchema, response.data)
  if (accepted.resourceType !== 'COVER_LETTER' || accepted.resourceId === null) {
    throw invalidServerResponse()
  }
  return accepted
}

export async function listAnswerVerifications(
  versionId: string,
  params: VerificationListParams = {},
): Promise<VerificationPageDto> {
  const value = await apiClient.get<unknown>(
    `/cover-letter-answer-versions/${encodeURIComponent(versionId)}/verifications`,
    { params },
  )
  return parse(verificationPageSchema, value)
}

export async function finalizeCoverLetter(
  coverLetterId: string,
  request: FinalizeCoverLetterRequest,
): Promise<CoverLetterDetailDto> {
  const value = await apiClient.post<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/finalize`,
    request,
  )
  return parse(coverLetterDetailSchema, value)
}

export async function archiveCoverLetter(
  coverLetterId: string,
  request: VersionCommandRequest,
): Promise<CoverLetterDetailDto> {
  const value = await apiClient.post<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/archive`,
    request,
  )
  return parse(coverLetterDetailSchema, value)
}

export async function unarchiveCoverLetter(
  coverLetterId: string,
  request: VersionCommandRequest,
): Promise<CoverLetterDetailDto> {
  const value = await apiClient.post<unknown>(
    `/cover-letters/${encodeURIComponent(coverLetterId)}/unarchive`,
    request,
  )
  return parse(coverLetterDetailSchema, value)
}

export function createCoverLetterIdempotencyKey(
  operation: 'create' | 'generate' | 'verify',
): string {
  return `cover-letter-${operation}:${globalThis.crypto.randomUUID()}`
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
    message: '자기소개서 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
  })
}
