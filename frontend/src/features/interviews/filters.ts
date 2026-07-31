import {
  QUESTION_SET_SORTS,
  type QuestionSetListParams,
  type QuestionSetSort,
} from '@/shared/api/interviewApi'
import {
  RESEARCH_RUN_STATUSES,
  SOURCE_COVERAGES,
  type ResearchRunStatus,
  type SourceCoverage,
} from '@/shared/api/interviewContracts'

export interface QuestionSetListFilters {
  jobId?: string
  coverLetterId?: string
  query?: string
  sourceCoverage?: SourceCoverage
  researchStatus?: ResearchRunStatus
  page: number
  size: number
  sort: QuestionSetSort
}

export type QuestionSetUrlQuery = Record<string, string>

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function parseQuestionSetFilters(query: Record<string, unknown>): QuestionSetListFilters {
  const search = firstString(query.qsQuery)?.trim()
  const jobId = firstString(query.qsJobId)
  const coverLetterId = firstString(query.qsCoverLetterId)
  return {
    jobId: jobId !== undefined && UUID.test(jobId) ? jobId : undefined,
    coverLetterId:
      coverLetterId !== undefined && UUID.test(coverLetterId) ? coverLetterId : undefined,
    query: search !== undefined && search.length > 0 && search.length <= 200 ? search : undefined,
    sourceCoverage: oneOf(firstString(query.qsSourceCoverage), SOURCE_COVERAGES),
    researchStatus: oneOf(firstString(query.qsResearchStatus), RESEARCH_RUN_STATUSES),
    page: parseInteger(firstString(query.qsPage), 0, Number.MAX_SAFE_INTEGER) ?? 0,
    size: parseInteger(firstString(query.qsSize), 1, 100) ?? 20,
    sort: oneOf(firstString(query.qsSort), QUESTION_SET_SORTS) ?? 'updatedAt,desc',
  }
}

export function canonicalQuestionSetQuery(filters: QuestionSetListFilters): QuestionSetUrlQuery {
  const query: QuestionSetUrlQuery = {}
  if (filters.jobId !== undefined) query.qsJobId = filters.jobId
  if (filters.coverLetterId !== undefined) query.qsCoverLetterId = filters.coverLetterId
  if (filters.query !== undefined) query.qsQuery = filters.query
  if (filters.sourceCoverage !== undefined) query.qsSourceCoverage = filters.sourceCoverage
  if (filters.researchStatus !== undefined) query.qsResearchStatus = filters.researchStatus
  if (filters.sort !== 'updatedAt,desc') query.qsSort = filters.sort
  if (filters.page !== 0) query.qsPage = String(filters.page)
  if (filters.size !== 20) query.qsSize = String(filters.size)
  return query
}

export function questionSetApiFilters(filters: QuestionSetListFilters): QuestionSetListParams {
  return { ...filters }
}

export function interviewQuerySignature(query: Record<string, unknown>): string {
  return Object.keys(query)
    .sort()
    .flatMap((key) =>
      stringValues(query[key]).map(
        (value) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
      ),
    )
    .join('&')
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
