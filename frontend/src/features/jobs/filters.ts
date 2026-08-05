import {
  JOB_POSTING_HALVES,
  JOB_STATUSES,
  type JobPostingHalf,
  type JobStatus,
} from '@/shared/api/jobContracts'
import { JOB_SORTS, type JobListParams } from '@/shared/api/jobApi'

export interface JobListFilters extends Required<
  Pick<JobListParams, 'status' | 'page' | 'size' | 'sort'>
> {
  query?: string
  postingYear?: number
  postingHalf?: JobPostingHalf
  postingStartFrom?: string
}

export type JobQuery = Record<string, string>

export function parseJobFilters(query: Record<string, unknown>): JobListFilters {
  let postingYear = parseInteger(firstString(query.postingYear), 2_000, 9_999)
  let postingHalf = oneOf(firstString(query.postingHalf), JOB_POSTING_HALVES)
  const postingStartFrom = normalizeLocalDate(firstString(query.postingStartFrom))

  if (postingYear === undefined || postingHalf === undefined || postingStartFrom !== undefined) {
    postingYear = undefined
    postingHalf = undefined
  }

  const search = firstString(query.query)?.trim()
  return {
    status: oneOf(firstString(query.status), JOB_STATUSES) ?? 'IN_PROGRESS',
    query: search !== undefined && search.length > 0 && search.length <= 200 ? search : undefined,
    postingYear,
    postingHalf,
    postingStartFrom,
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER) ?? 0,
    size: parseInteger(firstString(query.size), 1, 100) ?? 20,
    sort: oneOf(firstString(query.sort), JOB_SORTS) ?? 'createdAt,desc',
  }
}

export function canonicalJobQuery(filters: JobListFilters): JobQuery {
  const query: JobQuery = {}
  if (filters.status !== 'IN_PROGRESS') query.status = filters.status
  if (filters.query !== undefined) query.query = filters.query
  if (filters.postingStartFrom !== undefined) {
    query.postingStartFrom = filters.postingStartFrom
  } else if (filters.postingYear !== undefined && filters.postingHalf !== undefined) {
    query.postingYear = String(filters.postingYear)
    query.postingHalf = filters.postingHalf
  }
  if (filters.page !== 0) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  if (filters.sort !== 'createdAt,desc') query.sort = filters.sort
  return query
}

export function jobQuerySignature(query: Record<string, unknown>): string {
  return Object.keys(query)
    .sort()
    .flatMap((key) =>
      stringValues(query[key]).map(
        (value) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
      ),
    )
    .join('&')
}

export function jobFiltersForStatus(filters: JobListFilters, status: JobStatus): JobListFilters {
  return { ...filters, status, page: 0 }
}

export function jobFiltersForPage(filters: JobListFilters, page: number): JobListFilters {
  return { ...filters, page }
}

function normalizeLocalDate(value: string | undefined): string | undefined {
  if (value === undefined || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined
  const date = new Date(`${value}T00:00:00Z`)
  return Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== value
    ? undefined
    : value
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
