import { z } from 'zod'

import { safeErrorSchema } from './agentRunContracts'

export const JOB_STATUSES = ['IN_PROGRESS', 'SUBMITTED', 'CLOSED'] as const
export const JOB_EXTRACTION_STATUSES = [
  'QUEUED',
  'EXTRACTING',
  'EXTRACTED',
  'MANUAL_INPUT_PROVIDED',
  'NEEDS_MANUAL_INPUT',
  'FAILED',
] as const
export const DEADLINE_SOURCES = ['USER_ENTERED', 'AUTO_EXTRACTED', 'UNKNOWN'] as const
export const CLOSED_REASONS = ['DEADLINE_PASSED', 'USER_CLOSED', 'URL_INACTIVE'] as const
export const JOB_DESCRIPTION_SOURCES = ['AUTO_EXTRACTED', 'USER_ENTERED'] as const

export type JobStatus = (typeof JOB_STATUSES)[number]
export type JobExtractionStatus = (typeof JOB_EXTRACTION_STATUSES)[number]
export type DeadlineSource = (typeof DEADLINE_SOURCES)[number]
export type ClosedReason = (typeof CLOSED_REASONS)[number]
export type JobDescriptionSource = (typeof JOB_DESCRIPTION_SOURCES)[number]

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const versionSchema = z.number().int().nonnegative()
const httpUrlSchema = z
  .string()
  .min(1)
  .max(2_000)
  .refine((value) => {
    try {
      const url = new URL(value)
      return url.protocol === 'http:' || url.protocol === 'https:'
    } catch {
      return false
    }
  }, 'HTTP(S) 절대 URL이어야 합니다.')

export const jobCreationAcceptedSchema = z
  .object({
    jobId: uuidSchema,
    status: z.literal('IN_PROGRESS'),
    extractionStatus: z.enum(['QUEUED', 'MANUAL_INPUT_PROVIDED']),
    agentRunId: uuidSchema.nullable(),
  })
  .superRefine((value, context) => {
    const valid =
      (value.extractionStatus === 'QUEUED' && value.agentRunId !== null) ||
      (value.extractionStatus === 'MANUAL_INPUT_PROVIDED' && value.agentRunId === null)
    if (!valid) {
      context.addIssue({
        code: 'custom',
        path: ['agentRunId'],
        message: '공고 정보를 확인하지 못했어요. 다시 불러와 주세요.',
      })
    }
  })

const jobSummaryFields = {
  id: uuidSchema,
  companyName: z.string().max(200).nullable(),
  title: z.string().max(300).nullable(),
  positionName: z.string().max(300).nullable(),
  status: z.enum(JOB_STATUSES),
  extractionStatus: z.enum(JOB_EXTRACTION_STATUSES),
  submittedAt: nullableInstantSchema,
  deadlineAt: nullableInstantSchema,
  deadlineSource: z.enum(DEADLINE_SOURCES),
  latestFitScore: z.null(),
  analysisOutdated: z.literal(false),
  outdatedReasons: z.tuple([]),
  coverLetterStatus: z.null(),
  interviewPreparationCount: z.literal(0),
  version: versionSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema,
} as const

export const jobSummarySchema = z.object(jobSummaryFields)

export const jobDetailSchema = z.object({
  ...jobSummaryFields,
  sourceUrl: httpUrlSchema,
  canonicalUrl: httpUrlSchema,
  roleCategory: z.string().max(100).nullable(),
  employmentType: z.string().max(100).nullable(),
  location: z.string().max(200).nullable(),
  descriptionText: z.string().max(200_000).nullable(),
  descriptionSource: z.enum(JOB_DESCRIPTION_SOURCES).nullable(),
  extractionError: safeErrorSchema.nullable(),
  closedAt: nullableInstantSchema,
  closedReason: z.enum(CLOSED_REASONS).nullable(),
  latestAnalysis: z.null(),
  coverLetterId: uuidSchema.nullable(),
  latestQuestionSetId: uuidSchema.nullable(),
  latestMockSessionId: uuidSchema.nullable(),
})

export const jobPageSchema = z.object({
  items: z.array(jobSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
})

export type JobCreationAcceptedDto = z.infer<typeof jobCreationAcceptedSchema>
export type JobSummaryDto = z.infer<typeof jobSummarySchema>
export type JobDetailDto = z.infer<typeof jobDetailSchema>
export type JobPageDto = z.infer<typeof jobPageSchema>

export interface CreateJobRequest {
  sourceUrl: string
  companyName?: string | null
  positionName?: string | null
  descriptionText?: string | null
  deadlineAt?: string | null
}

export interface UpdateJobRequest {
  companyName?: string | null
  title?: string | null
  positionName?: string | null
  descriptionText?: string | null
  deadlineAt?: string | null
  version: number
}

export interface UpdateJobStatusRequest {
  status: JobStatus
  version: number
}

export interface RetryJobExtractionRequest {
  version: number
}
