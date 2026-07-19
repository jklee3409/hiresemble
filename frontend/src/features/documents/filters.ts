import {
  DOCUMENT_PARSE_STATUSES,
  DOCUMENT_TYPES,
  EVIDENCE_EXTRACTION_STATUSES,
  type DocumentParseStatus,
  type DocumentType,
  type EvidenceExtractionStatus,
} from '@/shared/api/documentContracts'
import { DOCUMENT_SORTS, type DocumentListParams } from '@/shared/api/documentApi'

export interface DocumentListFilters extends Required<
  Pick<DocumentListParams, 'page' | 'size' | 'sort'>
> {
  documentType?: DocumentType
  parseStatus?: DocumentParseStatus
  evidenceExtractionStatus?: EvidenceExtractionStatus
}

export type DocumentQuery = Record<string, string>

export function parseDocumentFilters(query: Record<string, unknown>): DocumentListFilters {
  return {
    documentType: oneOf(firstString(query.documentType), DOCUMENT_TYPES),
    parseStatus: oneOf(firstString(query.parseStatus), DOCUMENT_PARSE_STATUSES),
    evidenceExtractionStatus: oneOf(
      firstString(query.evidenceExtractionStatus),
      EVIDENCE_EXTRACTION_STATUSES,
    ),
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER, 0),
    size: parseInteger(firstString(query.size), 1, 100, 20),
    sort: oneOf(firstString(query.sort), DOCUMENT_SORTS) ?? 'uploadedAt,desc',
  }
}

export function canonicalDocumentQuery(filters: DocumentListFilters): DocumentQuery {
  const query: DocumentQuery = {}
  if (filters.documentType !== undefined) query.documentType = filters.documentType
  if (filters.parseStatus !== undefined) query.parseStatus = filters.parseStatus
  if (filters.evidenceExtractionStatus !== undefined)
    query.evidenceExtractionStatus = filters.evidenceExtractionStatus
  if (filters.page !== 0) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  if (filters.sort !== 'uploadedAt,desc') query.sort = filters.sort
  return query
}

export function documentQuerySignature(query: Record<string, unknown>): string {
  return Object.keys(query)
    .sort()
    .flatMap((key) => stringValues(query[key]).map((value) => `${key}=${value}`))
    .join('&')
}

function oneOf<const T extends readonly string[]>(
  value: string | undefined,
  allowed: T,
): T[number] | undefined {
  return allowed.find((candidate) => candidate === value)
}

function parseInteger(
  value: string | undefined,
  min: number,
  max: number,
  fallback: number,
): number {
  if (value === undefined || !/^\d+$/.test(value)) return fallback
  const number = Number(value)
  return Number.isSafeInteger(number) && number >= min && number <= max ? number : fallback
}

function firstString(value: unknown): string | undefined {
  return stringValues(value)[0]
}

function stringValues(value: unknown): string[] {
  if (typeof value === 'string') return [value]
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}
