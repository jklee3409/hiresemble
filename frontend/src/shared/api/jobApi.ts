import type { ZodType } from 'zod'

import { runAcceptedSchema, type RunAcceptedDto } from './agentRunContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'
import {
  jobCreationAcceptedSchema,
  jobDetailSchema,
  jobPageSchema,
  type CreateJobRequest,
  type JobCreationAcceptedDto,
  type JobDetailDto,
  type JobExtractionStatus,
  type JobPageDto,
  type JobStatus,
  type RetryJobExtractionRequest,
  type UpdateJobRequest,
  type UpdateJobStatusRequest,
} from './jobContracts'

export const JOB_SORTS = ['createdAt,desc', 'deadlineAt,asc', 'updatedAt,desc'] as const
export type JobSort = (typeof JOB_SORTS)[number]

export interface JobListParams {
  status?: JobStatus
  extractionStatus?: JobExtractionStatus
  query?: string
  deadlineFrom?: string
  deadlineTo?: string
  deadlineWithinDays?: number
  page?: number
  size?: number
  sort?: JobSort
}

export type JobCreationResult =
  | { httpStatus: 201; job: JobCreationAcceptedDto & { extractionStatus: 'MANUAL_INPUT_PROVIDED' } }
  | { httpStatus: 202; job: JobCreationAcceptedDto & { extractionStatus: 'QUEUED' } }

export async function createJob(
  request: CreateJobRequest,
  idempotencyKey: string,
): Promise<JobCreationResult> {
  const response = await apiClient.client.post<unknown>('/jobs', request, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  const job = parse(jobCreationAcceptedSchema, response.data)

  if (response.status === 201 && job.extractionStatus === 'MANUAL_INPUT_PROVIDED') {
    return {
      httpStatus: 201,
      job: { ...job, extractionStatus: 'MANUAL_INPUT_PROVIDED' },
    }
  }
  if (response.status === 202 && job.extractionStatus === 'QUEUED') {
    return { httpStatus: 202, job: { ...job, extractionStatus: 'QUEUED' } }
  }
  throw invalidServerResponse()
}

export async function listJobs(params: JobListParams = {}): Promise<JobPageDto> {
  const value = await apiClient.get<unknown>('/jobs', { params })
  return parse(jobPageSchema, value)
}

export async function getJob(jobId: string): Promise<JobDetailDto> {
  const value = await apiClient.get<unknown>(`/jobs/${encodeURIComponent(jobId)}`)
  return parse(jobDetailSchema, value)
}

export async function updateJob(jobId: string, request: UpdateJobRequest): Promise<JobDetailDto> {
  const value = await apiClient.put<unknown>(`/jobs/${encodeURIComponent(jobId)}`, request)
  return parse(jobDetailSchema, value)
}

export async function updateJobStatus(
  jobId: string,
  request: UpdateJobStatusRequest,
): Promise<JobDetailDto> {
  const value = await apiClient.patch<unknown>(`/jobs/${encodeURIComponent(jobId)}/status`, request)
  return parse(jobDetailSchema, value)
}

export async function retryJobExtraction(
  jobId: string,
  request: RetryJobExtractionRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const value = await apiClient.post<unknown>(
    `/jobs/${encodeURIComponent(jobId)}/retry-extraction`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return parse(runAcceptedSchema, value)
}

export function deleteJob(jobId: string, version: number): Promise<void> {
  return apiClient.delete(`/jobs/${encodeURIComponent(jobId)}`, {
    params: { version },
  })
}

export function createJobIdempotencyKey(operation: 'create' | 'retry-extraction'): string {
  return `job-${operation}:${globalThis.crypto.randomUUID()}`
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
    message: '채용 공고 응답 형식이 올바르지 않습니다.',
  })
}
