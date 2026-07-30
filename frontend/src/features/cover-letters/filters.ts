import { COVER_LETTER_STATUSES, type CoverLetterStatus } from '@/shared/api/coverLetterContracts'
import {
  COVER_LETTER_SORTS,
  type CoverLetterListParams,
  type CoverLetterSort,
} from '@/shared/api/coverLetterApi'

export interface CoverLetterListFilters {
  jobId?: string
  status?: CoverLetterStatus
  query?: string
  page: number
  size: number
  sort: CoverLetterSort
}

export type CoverLetterQuery = Record<string, string>

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function parseCoverLetterFilters(query: Record<string, unknown>): CoverLetterListFilters {
  const search = firstString(query.query)?.trim()
  const jobId = firstString(query.jobId)
  return {
    jobId: jobId !== undefined && UUID.test(jobId) ? jobId : undefined,
    status: oneOf(firstString(query.status), COVER_LETTER_STATUSES),
    query: search !== undefined && search.length > 0 && search.length <= 200 ? search : undefined,
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER) ?? 0,
    size: parseInteger(firstString(query.size), 1, 100) ?? 20,
    sort: oneOf(firstString(query.sort), COVER_LETTER_SORTS) ?? 'updatedAt,desc',
  }
}

export function canonicalCoverLetterQuery(filters: CoverLetterListFilters): CoverLetterQuery {
  const query: CoverLetterQuery = {}
  if (filters.jobId !== undefined) query.jobId = filters.jobId
  if (filters.status !== undefined) query.status = filters.status
  if (filters.query !== undefined) query.query = filters.query
  if (filters.page !== 0) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  if (filters.sort !== 'updatedAt,desc') query.sort = filters.sort
  return query
}

export function coverLetterQuerySignature(query: Record<string, unknown>): string {
  return Object.keys(query)
    .sort()
    .flatMap((key) =>
      stringValues(query[key]).map(
        (value) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
      ),
    )
    .join('&')
}

export function coverLetterApiFilters(filters: CoverLetterListFilters): CoverLetterListParams {
  return { ...filters }
}

function oneOf<const T extends readonly string[]>(
  value: string | undefined,
  allowed: T,
): T[number] | undefined {
  return allowed.find((candidate) => candidate === value)
}

function parseInteger(value: string | undefined, min: number, max: number): number | undefined {
  if (value === undefined || !/^\d+$/.test(value)) return undefined
  const number = Number(value)
  return Number.isSafeInteger(number) && number >= min && number <= max ? number : undefined
}

function firstString(value: unknown): string | undefined {
  return stringValues(value)[0]
}

function stringValues(value: unknown): string[] {
  if (typeof value === 'string') return [value]
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}
