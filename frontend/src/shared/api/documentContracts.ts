import { z } from 'zod'

import { safeErrorSchema, type RunAcceptedDto } from './agentRunContracts'

export const DOCUMENT_TYPES = [
  'RESUME',
  'PORTFOLIO',
  'CAREER_DESCRIPTION',
  'CERTIFICATE',
  'TRANSCRIPT',
  'OTHER',
] as const

export const DOCUMENT_PARSE_STATUSES = [
  'UPLOADED',
  'PARSING',
  'PARSED',
  'NEEDS_MANUAL_TEXT',
  'FAILED',
] as const

export const EVIDENCE_EXTRACTION_STATUSES = [
  'NOT_STARTED',
  'QUEUED',
  'EXTRACTING',
  'SUCCEEDED',
  'FAILED',
] as const

export type DocumentType = (typeof DOCUMENT_TYPES)[number]
export type DocumentParseStatus = (typeof DOCUMENT_PARSE_STATUSES)[number]
export type EvidenceExtractionStatus = (typeof EVIDENCE_EXTRACTION_STATUSES)[number]

const instantSchema = z.iso.datetime({ offset: true })
const uuidSchema = z.uuid()

export const documentUploadAcceptedSchema = z.object({
  documentId: uuidSchema,
  parseStatus: z.literal('UPLOADED'),
  evidenceExtractionStatus: z.literal('NOT_STARTED'),
  agentRunId: uuidSchema,
  status: z.literal('QUEUED'),
})

const documentSummaryFields = {
  id: uuidSchema,
  documentType: z.enum(DOCUMENT_TYPES),
  displayName: z.string().min(1).max(255),
  mimeType: z.string().min(1).max(100),
  fileSizeBytes: z
    .number()
    .int()
    .min(1)
    .max(20 * 1024 * 1024),
  parseStatus: z.enum(DOCUMENT_PARSE_STATUSES),
  evidenceExtractionStatus: z.enum(EVIDENCE_EXTRACTION_STATUSES),
  manualTextProvided: z.boolean(),
  safeError: safeErrorSchema.nullable(),
  latestAgentRunId: uuidSchema.nullable(),
  version: z.number().int().nonnegative(),
  uploadedAt: instantSchema,
  updatedAt: instantSchema,
} as const

export const documentSummarySchema = z.object(documentSummaryFields)

export const documentDetailSchema = z.object({
  ...documentSummaryFields,
  pageCount: z.number().int().min(1).nullable(),
  characterCount: z.number().int().nonnegative().nullable(),
  parsedAt: instantSchema.nullable(),
})

export const documentTextSchema = z.object({
  documentId: uuidSchema,
  text: z.string().max(500_000),
  characterCount: z.number().int().nonnegative(),
  manualTextProvided: z.boolean(),
  version: z.number().int().nonnegative(),
  updatedAt: instantSchema,
})

export const downloadUrlSchema = z.object({
  url: z.string().min(1).max(4096),
  expiresAt: instantSchema,
})

export const documentPageSchema = z.object({
  items: z.array(documentSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
})

export type DocumentUploadAcceptedDto = z.infer<typeof documentUploadAcceptedSchema>
export type DocumentSummaryDto = z.infer<typeof documentSummarySchema>
export type DocumentDetailDto = z.infer<typeof documentDetailSchema>
export type DocumentTextDto = z.infer<typeof documentTextSchema>
export type DownloadUrlDto = z.infer<typeof downloadUrlSchema>
export type DocumentPageDto = z.infer<typeof documentPageSchema>

export interface DocumentManualTextRequest {
  text: string
  version: number
}

export interface DocumentReparseRequest {
  version: number
}

export type DocumentRunAcceptedDto = RunAcceptedDto
