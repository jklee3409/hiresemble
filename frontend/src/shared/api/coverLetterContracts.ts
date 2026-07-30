import { z } from 'zod'

import { AI_QUALITY_MODES } from './agentRunContracts'
import { evidenceRefSchema } from './jobContracts'

export const COVER_LETTER_STATUSES = ['DRAFT', 'FINALIZED', 'ARCHIVED'] as const
export const COVER_LETTER_VERSION_SOURCES = [
  'AI_GENERATED',
  'USER_EDITED',
  'AI_REVISED',
  'RESTORED',
] as const
export const ANSWER_CREATED_BY_VALUES = ['USER', 'AI'] as const
export const VERIFICATION_STATUSES = ['PENDING', 'PASSED', 'WARNING', 'FAILED'] as const
export const VERIFICATION_ISSUE_CODES = [
  'UNVERIFIED_CLAIM',
  'CONTRADICTION',
  'REQUIREMENT_MISSING',
  'LENGTH_VIOLATION',
  'SOURCE_DELETED',
  'OTHER',
] as const
export const ISSUE_SEVERITIES = ['WARNING', 'ERROR'] as const

export type CoverLetterStatus = (typeof COVER_LETTER_STATUSES)[number]
export type CoverLetterVersionSource = (typeof COVER_LETTER_VERSION_SOURCES)[number]
export type AnswerCreatedBy = (typeof ANSWER_CREATED_BY_VALUES)[number]
export type VerificationStatus = (typeof VERIFICATION_STATUSES)[number]
export type VerificationIssueCode = (typeof VERIFICATION_ISSUE_CODES)[number]
export type IssueSeverity = (typeof ISSUE_SEVERITIES)[number]
export type AiQualityMode = (typeof AI_QUALITY_MODES)[number]

export interface TipTapMarkDto {
  type: 'bold' | 'italic'
}

export interface TipTapNodeDto {
  type: 'paragraph' | 'text' | 'hardBreak' | 'bulletList' | 'orderedList' | 'listItem'
  text: string | null
  marks: TipTapMarkDto[]
  content: TipTapNodeDto[]
}

export interface TipTapDocumentDto {
  type: 'doc'
  content: TipTapNodeDto[]
}

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nonnegativeIntegerSchema = z.number().int().nonnegative()

export const tipTapMarkSchema = z.object({
  type: z.enum(['bold', 'italic']),
})

export const tipTapNodeSchema: z.ZodType<TipTapNodeDto> = z.lazy(() =>
  z.object({
    type: z.enum(['paragraph', 'text', 'hardBreak', 'bulletList', 'orderedList', 'listItem']),
    text: z.string().min(1).max(20_000).nullable(),
    marks: z.array(tipTapMarkSchema).max(2),
    content: z.array(tipTapNodeSchema).max(1_000),
  }),
)

export const tipTapDocumentSchema: z.ZodType<TipTapDocumentDto> = z.object({
  type: z.literal('doc'),
  content: z.array(tipTapNodeSchema).max(1_000),
})

export const jobRefSchema = z.object({
  id: uuidSchema,
  companyName: z.string().max(200).nullable(),
  positionName: z.string().max(300).nullable(),
  title: z.string().max(300).nullable(),
})

export const verificationIssueSchema = z.object({
  code: z.enum(VERIFICATION_ISSUE_CODES),
  severity: z.enum(ISSUE_SEVERITIES),
  message: z.string().min(1).max(1_000),
  relatedText: z.string().max(1_000).nullable(),
  evidenceRefs: z.array(evidenceRefSchema).max(20),
})

export const verifiedClaimSchema = z.object({
  claim: z.string().min(1).max(2_000),
  supported: z.boolean(),
  evidenceRefs: z.array(evidenceRefSchema).max(20),
})

export const answerVersionSchema = z.object({
  id: uuidSchema,
  questionId: uuidSchema,
  parentVersionId: uuidSchema.nullable(),
  restoredFromVersionId: uuidSchema.nullable(),
  versionNo: z.number().int().min(1),
  contentJson: tipTapDocumentSchema,
  plainText: z.string().max(20_000),
  characterCount: z.number().int().min(0).max(20_000),
  sourceType: z.enum(COVER_LETTER_VERSION_SOURCES),
  isCurrent: z.boolean(),
  createdBy: z.enum(ANSWER_CREATED_BY_VALUES),
  createdAt: instantSchema,
})

export const verificationSchema = z.object({
  id: uuidSchema,
  answerVersionId: uuidSchema,
  status: z.enum(VERIFICATION_STATUSES),
  issues: z.array(verificationIssueSchema).max(100),
  suggestions: z.array(z.string().min(1).max(1_000)).max(20),
  verifiedClaims: z.array(verifiedClaimSchema).max(100),
  evidenceRefs: z.array(evidenceRefSchema).max(100),
  agentRunId: uuidSchema.nullable(),
  createdAt: instantSchema,
})

export const coverLetterQuestionSchema = z.object({
  id: uuidSchema,
  questionOrder: z.number().int().min(1).max(20),
  questionText: z.string().min(1).max(2_000),
  maxLength: z.number().int().min(1).max(10_000).nullable(),
  memo: z.string().max(2_000).nullable(),
  currentAnswer: answerVersionSchema.nullable(),
  latestVerification: verificationSchema.nullable(),
  version: nonnegativeIntegerSchema,
  deletedAt: instantSchema.nullable(),
})

const coverLetterSummaryFields = {
  id: uuidSchema,
  job: jobRefSchema,
  title: z.string().min(1).max(300),
  status: z.enum(COVER_LETTER_STATUSES),
  questionCount: z.number().int().min(0).max(20),
  answeredQuestionCount: z.number().int().min(0).max(20),
  latestVerificationStatus: z.enum(VERIFICATION_STATUSES).nullable(),
  warningCount: nonnegativeIntegerSchema,
  canEdit: z.boolean(),
  canArchive: z.boolean(),
  canUnarchive: z.boolean(),
  canFinalize: z.boolean(),
  version: nonnegativeIntegerSchema,
  finalizedAt: instantSchema.nullable(),
  archivedAt: instantSchema.nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
} as const

export const coverLetterSummarySchema = z.object(coverLetterSummaryFields)
export const coverLetterDetailSchema = z.object({
  ...coverLetterSummaryFields,
  questions: z.array(coverLetterQuestionSchema).max(20),
})

export const coverLetterPageSchema = z.object({
  items: z.array(coverLetterSummarySchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export const answerVersionPageSchema = z.object({
  items: z.array(answerVersionSchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export const verificationPageSchema = z.object({
  items: z.array(verificationSchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export type JobRefDto = z.infer<typeof jobRefSchema>
export type VerificationIssueDto = z.infer<typeof verificationIssueSchema>
export type VerifiedClaimDto = z.infer<typeof verifiedClaimSchema>
export type CoverLetterAnswerVersionDto = z.infer<typeof answerVersionSchema>
export type VerificationDto = z.infer<typeof verificationSchema>
export type CoverLetterQuestionDto = z.infer<typeof coverLetterQuestionSchema>
export type CoverLetterSummaryDto = z.infer<typeof coverLetterSummarySchema>
export type CoverLetterDetailDto = z.infer<typeof coverLetterDetailSchema>
export type CoverLetterPageDto = z.infer<typeof coverLetterPageSchema>
export type AnswerVersionPageDto = z.infer<typeof answerVersionPageSchema>
export type VerificationPageDto = z.infer<typeof verificationPageSchema>

export interface CreateCoverLetterRequest {
  title: string
}

export interface UpdateCoverLetterRequest {
  title: string
  version: number
}

export interface CreateQuestionRequest {
  questionOrder: number
  questionText: string
  maxLength: number | null
  memo: string | null
  coverLetterVersion: number
}

export interface UpdateQuestionRequest {
  questionOrder: number
  questionText: string
  maxLength: number | null
  memo: string | null
  version: number
}

export interface ReorderQuestionsRequest {
  questionIds: string[]
  version: number
}

export interface GenerateCoverLetterRequest {
  questionIds: string[]
  preferredEvidenceIds: string[]
  qualityMode: AiQualityMode
  avoidExperienceDuplication: boolean
  coverLetterVersion: number
}

export interface SaveAnswerVersionRequest {
  contentJson: TipTapDocumentDto
  parentVersionId: string | null
}

export interface RestoreAnswerVersionRequest {
  expectedCurrentVersionId: string | null
}

export interface VerifyAnswerVersionRequest {
  qualityMode: AiQualityMode
}

export interface FinalizeCoverLetterRequest {
  version: number
  acknowledgedWarningVerificationIds: string[]
}

export interface VersionCommandRequest {
  version: number
}
