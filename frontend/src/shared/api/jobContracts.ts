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
export const ELIGIBILITIES = ['ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE', 'UNKNOWN'] as const
export const OUTDATED_REASONS = [
  'JOB_CONTENT_CHANGED',
  'PROFILE_CHANGED',
  'EVIDENCE_CHANGED',
] as const
export const FIT_CRITERION_CATEGORIES = [
  'REQUIRED_QUALIFICATION',
  'CORE_RESPONSIBILITY_OR_SKILL',
  'PREFERRED_QUALIFICATION',
  'RELATED_EXPERIENCE_OR_DOMAIN',
  'EDUCATION_CERTIFICATION_LANGUAGE',
] as const
export const MATCH_LEVELS = ['MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN'] as const
export const JOB_ANALYSIS_QUALITY_MODES = ['ECONOMY', 'BALANCED'] as const
export const AUTOMATIC_ANALYSIS_STATES = [
  'WAITING_FOR_CONTENT',
  'NOT_REQUESTED',
  'PENDING',
  'LAUNCHED',
  'BLOCKED',
  'SUPERSEDED',
] as const
export const COVER_LETTER_STATUSES = ['DRAFT', 'FINALIZED', 'ARCHIVED'] as const
export const EVIDENCE_SOURCE_TYPES = [
  'EDUCATION',
  'CERTIFICATION',
  'LANGUAGE_SCORE',
  'AWARD',
  'CAREER',
  'DOCUMENT_CHUNK',
  'MANUAL',
] as const
export const EVIDENCE_VERIFICATION_STATUSES = [
  'PENDING',
  'VERIFIED',
  'REJECTED',
  'SOURCE_DELETED',
] as const

export type JobStatus = (typeof JOB_STATUSES)[number]
export type JobExtractionStatus = (typeof JOB_EXTRACTION_STATUSES)[number]
export type DeadlineSource = (typeof DEADLINE_SOURCES)[number]
export type ClosedReason = (typeof CLOSED_REASONS)[number]
export type JobDescriptionSource = (typeof JOB_DESCRIPTION_SOURCES)[number]
export type Eligibility = (typeof ELIGIBILITIES)[number]
export type OutdatedReason = (typeof OUTDATED_REASONS)[number]
export type FitCriterionCategory = (typeof FIT_CRITERION_CATEGORIES)[number]
export type MatchLevel = (typeof MATCH_LEVELS)[number]
export type JobAnalysisQualityMode = (typeof JOB_ANALYSIS_QUALITY_MODES)[number]
export type AutomaticAnalysisState = (typeof AUTOMATIC_ANALYSIS_STATES)[number]

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const versionSchema = z.number().int().nonnegative()
const scoreSchema = z.number().min(0).max(100)
const outdatedReasonsSchema = z.array(z.enum(OUTDATED_REASONS)).max(3)
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

export const evidenceRefSchema = z.object({
  id: uuidSchema,
  title: z.string().min(1).max(250),
  evidenceCategory: z.string().min(1).max(80),
  verificationStatus: z.enum(EVIDENCE_VERIFICATION_STATUSES),
  sourceType: z.enum(EVIDENCE_SOURCE_TYPES),
  sourceDeleted: z.boolean(),
})

export const requirementItemSchema = z.object({
  category: z.enum(FIT_CRITERION_CATEGORIES),
  text: z.string().min(1).max(2_000),
  required: z.boolean(),
  sourceLocation: z.string().max(500).nullable(),
})

export const scoreCriterionSchema = z
  .object({
    category: z.enum(FIT_CRITERION_CATEGORIES),
    criterion: z.string().min(1).max(2_000),
    weight: scoreSchema,
    matchLevel: z.enum(MATCH_LEVELS),
    score: scoreSchema,
    evidenceRefs: z.array(evidenceRefSchema).max(100),
    explanation: z.string().min(1).max(2_000),
  })
  .superRefine((value, context) => {
    if (value.score > value.weight) {
      context.addIssue({
        code: 'custom',
        path: ['score'],
        message: '기준 점수는 배점보다 클 수 없습니다.',
      })
    }
  })

const jobAnalysisSummaryFields = {
  id: uuidSchema,
  analysisVersion: z.number().int().min(1),
  eligibility: z.enum(ELIGIBILITIES),
  fitScore: scoreSchema.nullable(),
  analysisOutdated: z.boolean(),
  outdatedReasons: outdatedReasonsSchema,
  createdAt: instantSchema,
  agentRunId: uuidSchema,
} as const

export const jobAnalysisSummarySchema = z.object(jobAnalysisSummaryFields)

export const jobAnalysisDetailSchema = z.object({
  ...jobAnalysisSummaryFields,
  scoreBreakdown: z.array(scoreCriterionSchema).max(100),
  requiredQualifications: z.array(requirementItemSchema).max(100),
  preferredQualifications: z.array(requirementItemSchema).max(100),
  responsibilities: z.array(requirementItemSchema).max(100),
  strengths: z.array(z.string().min(1).max(1_000)).max(20),
  gaps: z.array(z.string().min(1).max(1_000)).max(20),
  matchedEvidenceRefs: z.array(evidenceRefSchema).max(100),
  analysisSummary: z.string().max(10_000).nullable(),
})

export const jobAnalysisPageSchema = z.object({
  items: z.array(jobAnalysisSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
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
  latestFitScore: scoreSchema.nullable(),
  analysisOutdated: z.boolean(),
  outdatedReasons: outdatedReasonsSchema,
  coverLetterStatus: z.enum(COVER_LETTER_STATUSES).nullable(),
  interviewPreparationCount: z.number().int().nonnegative(),
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
  automaticAnalysis: z.object({
    state: z.enum(AUTOMATIC_ANALYSIS_STATES),
    qualityMode: z.literal('BALANCED'),
    agentRunId: uuidSchema.nullable(),
    error: safeErrorSchema.nullable(),
  }),
  closedAt: nullableInstantSchema,
  closedReason: z.enum(CLOSED_REASONS).nullable(),
  latestAnalysis: jobAnalysisSummarySchema.nullable(),
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
export type EvidenceRefDto = z.infer<typeof evidenceRefSchema>
export type RequirementItemDto = z.infer<typeof requirementItemSchema>
export type ScoreCriterionDto = z.infer<typeof scoreCriterionSchema>
export type JobAnalysisSummaryDto = z.infer<typeof jobAnalysisSummarySchema>
export type JobAnalysisDetailDto = z.infer<typeof jobAnalysisDetailSchema>
export type JobAnalysisPageDto = z.infer<typeof jobAnalysisPageSchema>

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

export interface AnalyzeJobRequest {
  qualityMode: JobAnalysisQualityMode
  forceReanalyze: boolean
  jobVersion: number
}
