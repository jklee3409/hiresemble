import type { ZodType } from 'zod'

import {
  agentRunDetailSchema,
  agentRunPageSchema,
  runAcceptedSchema,
  type AgentRunDetailDto,
  type AgentRunPageDto,
  type AgentRunStatus,
  type RunAcceptedDto,
  type WorkflowType,
} from './agentRunContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export const AGENT_RUN_SORTS = ['queuedAt,desc', 'updatedAt,desc'] as const
export type AgentRunSort = (typeof AGENT_RUN_SORTS)[number]

export interface AgentRunListParams {
  workflowType?: WorkflowType[]
  status?: AgentRunStatus[]
  resourceType?: string
  resourceId?: string
  retryable?: boolean
  page?: number
  size?: number
  sort?: AgentRunSort
}

export function listAgentRuns(params: AgentRunListParams = {}): Promise<AgentRunPageDto> {
  return apiClient
    .get<unknown>('/agent-runs', { params: toAgentRunSearchParams(params) })
    .then((value) => parseServerResponse(agentRunPageSchema, value))
}

export function getAgentRun(agentRunId: string): Promise<AgentRunDetailDto> {
  return apiClient
    .get<unknown>(`/agent-runs/${encodeURIComponent(agentRunId)}`)
    .then((value) => parseServerResponse(agentRunDetailSchema, value))
}

export function retryAgentRun(agentRunId: string, idempotencyKey: string): Promise<RunAcceptedDto> {
  return apiClient
    .post<unknown>(`/agent-runs/${encodeURIComponent(agentRunId)}/retry`, undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    .then((value) => parseServerResponse(runAcceptedSchema, value))
}

export function cancelAgentRun(
  agentRunId: string,
  stateVersion: number,
): Promise<AgentRunDetailDto> {
  return apiClient
    .post<unknown>(`/agent-runs/${encodeURIComponent(agentRunId)}/cancel`, { stateVersion })
    .then((value) => parseServerResponse(agentRunDetailSchema, value))
}

export function createRetryIdempotencyKey(): string {
  return `agent-run-retry:${globalThis.crypto.randomUUID()}`
}

export function toAgentRunSearchParams(params: AgentRunListParams): URLSearchParams {
  const query = new URLSearchParams()
  for (const workflowType of params.workflowType ?? []) {
    query.append('workflowType', workflowType)
  }
  for (const status of params.status ?? []) {
    query.append('status', status)
  }
  if (params.resourceType !== undefined) query.set('resourceType', params.resourceType)
  if (params.resourceId !== undefined) query.set('resourceId', params.resourceId)
  if (params.retryable !== undefined) query.set('retryable', String(params.retryable))
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.sort !== undefined) query.set('sort', params.sort)
  return query
}

function parseServerResponse<T>(schema: ZodType<T>, value: unknown): T {
  const parsed = schema.safeParse(value)
  if (parsed.success) return parsed.data

  throw new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: 'Agent Run 응답 형식이 올바르지 않습니다.',
  })
}
