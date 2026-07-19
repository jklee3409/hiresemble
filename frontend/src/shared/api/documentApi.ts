import type { ZodType } from 'zod'

import { runAcceptedSchema, type RunAcceptedDto } from './agentRunContracts'
import {
  documentDetailSchema,
  documentPageSchema,
  documentTextSchema,
  documentUploadAcceptedSchema,
  downloadUrlSchema,
  type DocumentDetailDto,
  type DocumentManualTextRequest,
  type DocumentPageDto,
  type DocumentParseStatus,
  type DocumentReparseRequest,
  type DocumentTextDto,
  type DocumentType,
  type DocumentUploadAcceptedDto,
  type DownloadUrlDto,
  type EvidenceExtractionStatus,
} from './documentContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export const DOCUMENT_SORTS = ['uploadedAt,desc', 'updatedAt,desc'] as const
export type DocumentSort = (typeof DOCUMENT_SORTS)[number]

export interface DocumentListParams {
  documentType?: DocumentType
  parseStatus?: DocumentParseStatus
  evidenceExtractionStatus?: EvidenceExtractionStatus
  page?: number
  size?: number
  sort?: DocumentSort
}

export async function uploadDocument(
  file: File,
  documentType: DocumentType,
  displayName: string | null,
  idempotencyKey: string,
): Promise<DocumentUploadAcceptedDto> {
  const body = new FormData()
  body.append('file', file)
  body.append('documentType', documentType)
  if (displayName !== null) body.append('displayName', displayName)

  const value = await apiClient.post<unknown>('/documents', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return parse(documentUploadAcceptedSchema, value)
}

export async function listDocuments(params: DocumentListParams = {}): Promise<DocumentPageDto> {
  const value = await apiClient.get<unknown>('/documents', { params })
  return parse(documentPageSchema, value)
}

export async function getDocument(documentId: string): Promise<DocumentDetailDto> {
  const value = await apiClient.get<unknown>(`/documents/${encodeURIComponent(documentId)}`)
  return parse(documentDetailSchema, value)
}

export async function getDocumentText(documentId: string): Promise<DocumentTextDto> {
  const value = await apiClient.get<unknown>(`/documents/${encodeURIComponent(documentId)}/text`)
  return parse(documentTextSchema, value)
}

export async function provideDocumentManualText(
  documentId: string,
  request: DocumentManualTextRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const value = await apiClient.put<unknown>(
    `/documents/${encodeURIComponent(documentId)}/manual-text`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return parse(runAcceptedSchema, value)
}

export async function reparseDocument(
  documentId: string,
  request: DocumentReparseRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  const value = await apiClient.post<unknown>(
    `/documents/${encodeURIComponent(documentId)}/reparse`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return parse(runAcceptedSchema, value)
}

export async function createDocumentDownloadUrl(documentId: string): Promise<DownloadUrlDto> {
  const value = await apiClient.post<unknown>(
    `/documents/${encodeURIComponent(documentId)}/download-url`,
  )
  return parse(downloadUrlSchema, value)
}

export function deleteDocument(documentId: string, version: number): Promise<void> {
  return apiClient.delete(`/documents/${encodeURIComponent(documentId)}`, {
    params: { version },
  })
}

export function createDocumentIdempotencyKey(
  operation: 'upload' | 'manual-text' | 'reparse',
): string {
  return `document-${operation}:${globalThis.crypto.randomUUID()}`
}

function parse<T>(schema: ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value)
  if (result.success) return result.data

  throw new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: '문서 응답 형식이 올바르지 않습니다.',
  })
}
