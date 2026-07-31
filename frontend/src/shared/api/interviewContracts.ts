import { z } from 'zod'

import { AGENT_RUN_STATUSES, safeErrorSchema } from './agentRunContracts'
import { COVER_LETTER_STATUSES, jobRefSchema } from './coverLetterContracts'
import { evidenceRefSchema } from './jobContracts'

export const RESEARCH_QUALITIES = ['BASIC', 'ADVANCED'] as const
export const RESEARCH_RUN_STATUSES = [
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
] as const
export const SOURCE_COVERAGES = ['SUFFICIENT', 'LIMITED', 'NONE'] as const
export const RESEARCH_TOPICS = ['COMPANY', 'INTERVIEW_PROCESS', 'ROLE_TECHNICAL'] as const
export const RESEARCH_SOURCE_TYPES = [
  'OFFICIAL',
  'TECH_BLOG',
  'NEWS',
  'INTERVIEW_REVIEW',
  'COMMUNITY',
  'OTHER',
] as const
export const INTERVIEW_QUESTION_TYPES = [
  'COVER_LETTER',
  'RESUME',
  'PORTFOLIO',
  'TECHNICAL',
  'PROJECT_DEEP_DIVE',
  'BEHAVIORAL',
  'COMPANY_MOTIVATION',
  'FOLLOW_UP',
] as const
export const PREPARATION_QUESTION_TYPES = INTERVIEW_QUESTION_TYPES.filter(
  (value) => value !== 'FOLLOW_UP',
)
export const INTERVIEW_ANSWER_SOURCES = ['USER_EDITED'] as const

export type ResearchQuality = (typeof RESEARCH_QUALITIES)[number]
export type ResearchRunStatus = (typeof RESEARCH_RUN_STATUSES)[number]
export type SourceCoverage = (typeof SOURCE_COVERAGES)[number]
export type ResearchTopic = (typeof RESEARCH_TOPICS)[number]
export type ResearchSourceType = (typeof RESEARCH_SOURCE_TYPES)[number]
export type InterviewQuestionType = (typeof INTERVIEW_QUESTION_TYPES)[number]
export type PreparationQuestionType = (typeof PREPARATION_QUESTION_TYPES)[number]

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const nonnegativeIntegerSchema = z.number().int().nonnegative()

export const coverLetterRefSchema = z.object({
  id: uuidSchema,
  title: z.string().min(1).max(300),
  status: z.enum(COVER_LETTER_STATUSES),
})

export const agentRunRefSchema = z.object({
  id: uuidSchema,
  status: z.enum(AGENT_RUN_STATUSES),
  currentStep: z.string().min(1).max(100).nullable(),
  progressPercent: z.number().int().min(0).max(100),
})

export const feedbackScoreSchema = z.object({
  criterion: z.string().min(1).max(100),
  score: z.number().min(0).max(100),
  explanation: z.string().max(1_000).nullable(),
})

export const interviewAnswerVersionSchema = z.object({
  id: uuidSchema,
  questionId: uuidSchema,
  parentVersionId: uuidSchema.nullable(),
  versionNo: z.number().int().min(1),
  content: z.string().min(1).max(20_000),
  sourceType: z.enum(INTERVIEW_ANSWER_SOURCES),
  isCurrent: z.boolean(),
  createdAt: instantSchema,
})

export const interviewFeedbackSchema = z.object({
  id: uuidSchema,
  answerVersionId: uuidSchema,
  scores: z.array(feedbackScoreSchema).min(1).max(20),
  strengths: z.array(z.string().min(1).max(1_000)).max(20),
  weaknesses: z.array(z.string().min(1).max(1_000)).max(20),
  suggestions: z.array(z.string().min(1).max(1_000)).max(20),
  revisedExample: z.string().max(10_000).nullable(),
  agentRunId: uuidSchema,
  createdAt: instantSchema,
})

export const researchRunSchema = z.object({
  id: uuidSchema,
  retryOfResearchRunId: uuidSchema.nullable(),
  researchQuality: z.enum(RESEARCH_QUALITIES),
  status: z.enum(RESEARCH_RUN_STATUSES),
  sourceCoverage: z.enum(SOURCE_COVERAGES).nullable(),
  missingCoverageTopics: z.array(z.string().min(1).max(200)).max(20),
  summary: z.string().max(10_000).nullable(),
  agentRunId: uuidSchema,
  retryable: z.boolean(),
  safeError: safeErrorSchema.nullable(),
  createdAt: instantSchema,
  startedAt: nullableInstantSchema,
  completedAt: nullableInstantSchema,
})

export const researchSourceSchema = z.object({
  id: uuidSchema,
  topic: z.enum(RESEARCH_TOPICS),
  sourceUrl: z.url().max(2_000),
  title: z.string().max(500).nullable(),
  sourceType: z.enum(RESEARCH_SOURCE_TYPES),
  publishedAt: nullableInstantSchema,
  retrievedAt: instantSchema,
  snippet: z.string().max(2_000).nullable(),
  reliabilityNotice: z.string().min(1).max(500),
})

export const researchSourceRefSchema = z.object({
  id: uuidSchema,
  topic: z.enum(RESEARCH_TOPICS),
  title: z.string().max(500).nullable(),
  sourceUrl: z.url().max(2_000),
  sourceType: z.enum(RESEARCH_SOURCE_TYPES),
  retrievedAt: instantSchema,
})

export const interviewQuestionSchema = z.object({
  id: uuidSchema,
  questionOrder: z.number().int().min(1).max(20),
  questionType: z.enum(INTERVIEW_QUESTION_TYPES),
  questionText: z.string().min(1).max(2_000),
  intent: z.string().max(2_000).nullable(),
  evaluationPoints: z.array(z.string().min(1).max(500)).max(20),
  answerGuide: z.string().max(10_000).nullable(),
  followUpQuestions: z.array(z.string().min(1).max(2_000)).max(10),
  relatedEvidenceRefs: z.array(evidenceRefSchema).max(20),
  sourceRefs: z.array(researchSourceRefSchema).max(50),
  sourceBased: z.boolean(),
  currentAnswer: interviewAnswerVersionSchema.nullable(),
  latestFeedback: interviewFeedbackSchema.nullable(),
})

const questionSetSummaryFields = {
  id: uuidSchema,
  job: jobRefSchema,
  coverLetter: coverLetterRefSchema,
  title: z.string().min(1).max(300),
  questionCount: z.number().int().min(0).max(20),
  researchRunId: uuidSchema,
  sourceCoverage: z.enum(SOURCE_COVERAGES).nullable(),
  agentRun: agentRunRefSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema,
} as const

export const questionSetSummarySchema = z.object(questionSetSummaryFields)
export const questionSetDetailSchema = z.object({
  ...questionSetSummaryFields,
  research: researchRunSchema,
  questions: z.array(interviewQuestionSchema).max(20),
})

export const interviewPreparationAcceptedSchema = z.object({
  questionSetId: uuidSchema,
  researchRunId: uuidSchema,
  agentRunId: uuidSchema,
  status: z.literal('QUEUED'),
})

export const researchRetryAcceptedSchema = z.object({
  questionSetId: uuidSchema,
  researchRunId: uuidSchema,
  agentRunId: uuidSchema,
  retryOfResearchRunId: uuidSchema,
  status: z.literal('QUEUED'),
})

export const questionSetPageSchema = z.object({
  items: z.array(questionSetSummarySchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export const researchSourcePageSchema = z.object({
  items: z.array(researchSourceSchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export const interviewAnswerVersionPageSchema = z.object({
  items: z.array(interviewAnswerVersionSchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export const interviewFeedbackPageSchema = z.object({
  items: z.array(interviewFeedbackSchema),
  page: nonnegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonnegativeIntegerSchema,
  totalPages: nonnegativeIntegerSchema,
})

export type ResearchRunDto = z.infer<typeof researchRunSchema>
export type ResearchSourceDto = z.infer<typeof researchSourceSchema>
export type ResearchSourceRefDto = z.infer<typeof researchSourceRefSchema>
export type InterviewQuestionDto = z.infer<typeof interviewQuestionSchema>
export type InterviewAnswerVersionDto = z.infer<typeof interviewAnswerVersionSchema>
export type InterviewFeedbackDto = z.infer<typeof interviewFeedbackSchema>
export type QuestionSetSummaryDto = z.infer<typeof questionSetSummarySchema>
export type QuestionSetDetailDto = z.infer<typeof questionSetDetailSchema>
export type InterviewPreparationAcceptedDto = z.infer<typeof interviewPreparationAcceptedSchema>
export type ResearchRetryAcceptedDto = z.infer<typeof researchRetryAcceptedSchema>
export type QuestionSetPageDto = z.infer<typeof questionSetPageSchema>
export type ResearchSourcePageDto = z.infer<typeof researchSourcePageSchema>
export type InterviewAnswerVersionPageDto = z.infer<typeof interviewAnswerVersionPageSchema>
export type InterviewFeedbackPageDto = z.infer<typeof interviewFeedbackPageSchema>

export interface CreateInterviewPreparationRequest {
  coverLetterId: string
  researchQuality: ResearchQuality
  qualityMode: 'ECONOMY' | 'BALANCED'
  questionTypes: PreparationQuestionType[]
  questionCount: number
}

export interface ResearchRetryRequest {
  researchQuality?: ResearchQuality
  qualityMode?: 'ECONOMY' | 'BALANCED'
}

export interface CreateInterviewAnswerVersionRequest {
  content: string
  parentVersionId: string | null
}

export interface InterviewAnswerFeedbackRequest {
  qualityMode: 'ECONOMY' | 'BALANCED' | 'HIGH_QUALITY'
}
