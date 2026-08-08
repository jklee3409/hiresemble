import type { ZodType } from 'zod'

import {
  gitHubRefreshResultSchema,
  gitHubRepositoryPageSchema,
  gitHubRunAcceptedSchema,
  gitHubSourceDetailSchema,
  gitHubSourcePageSchema,
  type CreateGitHubSourceRequest,
  type GitHubRefreshRequest,
  type GitHubRefreshResultDto,
  type GitHubRepositoryPageDto,
  type GitHubRepositorySelectionRequest,
  type GitHubRepositorySort,
  type GitHubSourceDetailDto,
  type GitHubSourceKind,
  type GitHubSourcePageDto,
  type GitHubSourceSort,
  type GitHubSourceStatus,
} from './githubSourceContracts'
import type { RunAcceptedDto } from './agentRunContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export interface GitHubSourceListParams {
  status?: GitHubSourceStatus
  sourceKind?: GitHubSourceKind
  page?: number
  size?: number
  sort?: GitHubSourceSort
}

export interface GitHubRepositoryListParams {
  query?: string
  selected?: boolean
  page?: number
  size?: number
  sort?: GitHubRepositorySort
}

export function createGitHubSource(
  request: CreateGitHubSourceRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  return apiClient
    .post<unknown>('/github-sources', request, idempotencyHeader(idempotencyKey))
    .then((value) => parseGitHubResponse(gitHubRunAcceptedSchema, value))
}

export function listGitHubSources(
  params: GitHubSourceListParams = {},
): Promise<GitHubSourcePageDto> {
  return apiClient
    .get<unknown>('/github-sources', { params: toSearchParams(params) })
    .then((value) => parseGitHubResponse(gitHubSourcePageSchema, value))
}

export function getGitHubSource(sourceId: string): Promise<GitHubSourceDetailDto> {
  return apiClient
    .get<unknown>(sourcePath(sourceId))
    .then((value) => parseGitHubResponse(gitHubSourceDetailSchema, value))
}

export function listGitHubRepositories(
  sourceId: string,
  params: GitHubRepositoryListParams = {},
): Promise<GitHubRepositoryPageDto> {
  return apiClient
    .get<unknown>(`${sourcePath(sourceId)}/repositories`, { params: toSearchParams(params) })
    .then((value) => parseGitHubResponse(gitHubRepositoryPageSchema, value))
}

export function selectGitHubRepositories(
  sourceId: string,
  request: GitHubRepositorySelectionRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  return apiClient
    .put<unknown>(
      `${sourcePath(sourceId)}/repository-selection`,
      request,
      idempotencyHeader(idempotencyKey),
    )
    .then((value) => parseGitHubRun(value, sourceId))
}

export function refreshGitHubSource(
  sourceId: string,
  request: GitHubRefreshRequest,
  idempotencyKey: string,
): Promise<GitHubRefreshResultDto> {
  return apiClient
    .post<unknown>(`${sourcePath(sourceId)}/refresh`, request, idempotencyHeader(idempotencyKey))
    .then((value) => {
      const result = parseGitHubResponse(gitHubRefreshResultSchema, value)
      if (result.source.source.id !== sourceId) throw invalidGitHubResponse()
      return result
    })
}

export function deleteGitHubSource(sourceId: string, version: number): Promise<void> {
  return apiClient.delete<void>(sourcePath(sourceId), { params: { version } })
}

export function createGitHubIdempotencyKey(action: 'create' | 'selection' | 'refresh'): string {
  return `github-source-${action}:${globalThis.crypto.randomUUID()}`
}

export function toSearchParams(
  params: GitHubSourceListParams | GitHubRepositoryListParams,
): URLSearchParams {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return query
}

function parseGitHubRun(value: unknown, sourceId: string): RunAcceptedDto {
  const run = parseGitHubResponse(gitHubRunAcceptedSchema, value)
  if (run.resourceId !== sourceId) throw invalidGitHubResponse()
  return run
}

function parseGitHubResponse<T>(schema: ZodType<T>, value: unknown): T {
  const parsed = schema.safeParse(value)
  if (parsed.success) return parsed.data
  throw invalidGitHubResponse()
}

function invalidGitHubResponse(): ApiClientError {
  return new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: 'GitHub 연결 정보를 불러오는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
  })
}

function sourcePath(sourceId: string): string {
  return `/github-sources/${encodeURIComponent(sourceId)}`
}

function idempotencyHeader(idempotencyKey: string) {
  return { headers: { 'Idempotency-Key': idempotencyKey } }
}
