import {
  AGENT_RUN_STATUSES,
  WORKFLOW_TYPES,
  type AgentRunStatus,
  type WorkflowType,
} from '@/shared/api/agentRunContracts'
import {
  AGENT_RUN_SORTS,
  type AgentRunListParams,
  type AgentRunSort,
} from '@/shared/api/agentRunApi'

export interface AgentRunListFilters extends Required<
  Pick<AgentRunListParams, 'page' | 'size' | 'sort'>
> {
  workflowType: WorkflowType[]
  status: AgentRunStatus[]
  resourceType?: string
  resourceId?: string
  retryable?: boolean
}

export type AgentRunQuery = Record<string, string | string[]>

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function parseAgentRunFilters(query: Record<string, unknown>): AgentRunListFilters {
  const resourceType = firstString(query.resourceType)?.trim()
  const resourceId = firstString(query.resourceId)
  const validResourcePair =
    resourceType !== undefined &&
    resourceType.length >= 1 &&
    resourceType.length <= 50 &&
    resourceId !== undefined &&
    UUID_PATTERN.test(resourceId)

  const filters: AgentRunListFilters = {
    workflowType: canonicalEnums(query.workflowType, WORKFLOW_TYPES),
    status: canonicalEnums(query.status, AGENT_RUN_STATUSES),
    page: parseInteger(firstString(query.page), 0, Number.MAX_SAFE_INTEGER, 0),
    size: parseInteger(firstString(query.size), 1, 100, 20),
    sort: parseSort(firstString(query.sort)),
  }

  const retryable = firstString(query.retryable)
  if (retryable === 'true' || retryable === 'false') {
    filters.retryable = retryable === 'true'
  }
  if (validResourcePair) {
    filters.resourceType = resourceType
    filters.resourceId = resourceId.toLowerCase()
  }
  return filters
}

export function canonicalAgentRunQuery(filters: AgentRunListFilters): AgentRunQuery {
  const query: AgentRunQuery = {}
  if (filters.workflowType.length > 0) query.workflowType = filters.workflowType
  if (filters.status.length > 0) query.status = filters.status
  if (filters.resourceType !== undefined && filters.resourceId !== undefined) {
    query.resourceType = filters.resourceType
    query.resourceId = filters.resourceId
  }
  if (filters.retryable !== undefined) query.retryable = String(filters.retryable)
  if (filters.page !== 0) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  if (filters.sort !== 'queuedAt,desc') query.sort = filters.sort
  return query
}

export function agentRunQuerySignature(query: Record<string, unknown>): string {
  const entries: string[] = []
  for (const key of Object.keys(query).sort()) {
    for (const value of stringValues(query[key])) {
      entries.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  }
  return entries.join('&')
}

function canonicalEnums<const T extends readonly string[]>(
  value: unknown,
  allowed: T,
): T[number][] {
  const requested = new Set(stringValues(value))
  return allowed.filter((candidate) => requested.has(candidate))
}

function parseSort(value: string | undefined): AgentRunSort {
  return AGENT_RUN_SORTS.find((sort) => sort === value) ?? 'queuedAt,desc'
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
