import type {
  CareerArtifactListParams,
  CareerArtifactListSort,
  CareerArtifactVersionListParams,
  CareerArtifactVersionSort,
} from '@/shared/api/careerArtifactApi'
import type {
  CareerArtifactLifecycle,
  CareerArtifactType,
} from '@/shared/api/careerArtifactContracts'

export interface CareerArtifactListFilters extends Required<
  Pick<CareerArtifactListParams, 'lifecycleStatus' | 'page' | 'size' | 'sort'>
> {
  artifactType?: CareerArtifactType
}

export type CareerArtifactVersionFilters = Required<CareerArtifactVersionListParams>

export type CanonicalQuery = Record<string, string>

export function parseCareerArtifactListFilters(
  query: Record<string, unknown>,
): CareerArtifactListFilters {
  const artifactType = parseEnum(firstString(query.artifactType), ['RESUME', 'PORTFOLIO'] as const)
  return {
    ...(artifactType ? { artifactType } : {}),
    lifecycleStatus:
      parseEnum(firstString(query.lifecycleStatus), ['ACTIVE', 'ARCHIVED'] as const) ?? 'ACTIVE',
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER, 0),
    size: parseInteger(firstString(query.size), 1, 100, 20),
    sort:
      parseEnum(firstString(query.sort), ['updatedAt,desc', 'createdAt,desc'] as const) ??
      'updatedAt,desc',
  }
}

export function canonicalCareerArtifactListQuery(
  filters: CareerArtifactListFilters,
): CanonicalQuery {
  const query: CanonicalQuery = {}
  if (filters.artifactType) query.artifactType = filters.artifactType
  if (filters.lifecycleStatus !== 'ACTIVE') query.lifecycleStatus = filters.lifecycleStatus
  if (filters.page !== 0) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  if (filters.sort !== 'updatedAt,desc') query.sort = filters.sort
  return query
}

export function parseCareerArtifactVersionFilters(
  query: Record<string, unknown>,
): CareerArtifactVersionFilters {
  return {
    page: parseInteger(firstString(query.versionPage), 0, Number.MAX_SAFE_INTEGER, 0),
    size: parseInteger(firstString(query.versionSize), 1, 100, 20),
    sort:
      parseEnum(firstString(query.versionSort), ['versionNo,desc', 'versionNo,asc'] as const) ??
      'versionNo,desc',
  }
}

export function parseCareerArtifactNewQuery(query: Record<string, unknown>): {
  type: CareerArtifactType | null
  step: number
} {
  return {
    type: parseEnum(firstString(query.type), ['RESUME', 'PORTFOLIO'] as const) ?? null,
    step: parseInteger(firstString(query.step), 1, 4, 1),
  }
}

export function canonicalCareerArtifactNewQuery(
  type: CareerArtifactType | null,
  step: number,
): CanonicalQuery {
  const query: CanonicalQuery = {}
  if (type !== null) query.type = type
  if (step !== 1) query.step = String(Math.min(4, Math.max(1, step)))
  return query
}

export function careerArtifactQuerySignature(query: Record<string, unknown>): string {
  const entries: string[] = []
  for (const key of Object.keys(query).sort()) {
    const value = firstString(query[key])
    if (value !== undefined) entries.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
  }
  return entries.join('&')
}

function parseEnum<const T extends readonly string[]>(
  value: string | undefined,
  allowed: T,
): T[number] | undefined {
  return allowed.find((candidate) => candidate === value)
}

function parseInteger(
  value: string | undefined,
  minimum: number,
  maximum: number,
  fallback: number,
): number {
  if (value === undefined || !/^\d+$/.test(value)) return fallback
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed >= minimum && parsed <= maximum ? parsed : fallback
}

function firstString(value: unknown): string | undefined {
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.find((item): item is string => typeof item === 'string')
  return undefined
}

export type { CareerArtifactLifecycle, CareerArtifactListSort, CareerArtifactVersionSort }
