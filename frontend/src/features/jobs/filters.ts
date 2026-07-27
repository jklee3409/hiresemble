import {
  JOB_EXTRACTION_STATUSES,
  JOB_STATUSES,
  type JobExtractionStatus,
  type JobStatus,
} from '@/shared/api/jobContracts'
import { JOB_SORTS, type JobListParams } from '@/shared/api/jobApi'

export interface JobListFilters extends Required<
  Pick<JobListParams, 'status' | 'page' | 'size' | 'sort'>
> {
  extractionStatus?: JobExtractionStatus
  query?: string
  deadlineFrom?: string
  deadlineTo?: string
  deadlineWithinDays?: number
}

export type JobQuery = Record<string, string>

export function parseJobFilters(query: Record<string, unknown>): JobListFilters {
  const deadlineWithinDays = parseInteger(firstString(query.deadlineWithinDays), 1, 30)
  let deadlineFrom = normalizeInstant(firstString(query.deadlineFrom))
  let deadlineTo = normalizeInstant(firstString(query.deadlineTo))

  if (deadlineWithinDays !== undefined) {
    deadlineFrom = undefined
    deadlineTo = undefined
  } else if (
    deadlineFrom !== undefined &&
    deadlineTo !== undefined &&
    Date.parse(deadlineFrom) > Date.parse(deadlineTo)
  ) {
    deadlineFrom = undefined
    deadlineTo = undefined
  }

  const search = firstString(query.query)?.trim()
  return {
    status: oneOf(firstString(query.status), JOB_STATUSES) ?? 'IN_PROGRESS',
    extractionStatus: oneOf(firstString(query.extractionStatus), JOB_EXTRACTION_STATUSES),
    query: search !== undefined && search.length > 0 && search.length <= 200 ? search : undefined,
    deadlineFrom,
    deadlineTo,
    deadlineWithinDays,
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER) ?? 0,
    size: parseInteger(firstString(query.size), 1, 100) ?? 20,
    sort: oneOf(firstString(query.sort), JOB_SORTS) ?? 'createdAt,desc',
  }
}

export function canonicalJobQuery(filters: JobListFilters): JobQuery {
  const query: JobQuery = {}
  if (filters.status !== 'IN_PROGRESS') query.status = filters.status
  if (filters.extractionStatus !== undefined) query.extractionStatus = filters.extractionStatus
  if (filters.query !== undefined) query.query = filters.query
  if (filters.deadlineWithinDays !== undefined) {
    query.deadlineWithinDays = String(filters.deadlineWithinDays)
  } else {
    if (filters.deadlineFrom !== undefined) query.deadlineFrom = filters.deadlineFrom
    if (filters.deadlineTo !== undefined) query.deadlineTo = filters.deadlineTo
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

export function deadlineInputValue(value: string | undefined): string {
  return value?.slice(0, 10) ?? ''
}

export function deadlineFromInput(value: string): string | undefined {
  return value === '' ? undefined : `${value}T00:00:00.000Z`
}

export function deadlineToInput(value: string): string | undefined {
  return value === '' ? undefined : `${value}T23:59:59.999Z`
}

function normalizeInstant(value: string | undefined): string | undefined {
  if (value === undefined || Number.isNaN(Date.parse(value))) return undefined
  try {
    return new Date(value).toISOString()
  } catch {
    return undefined
  }
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
