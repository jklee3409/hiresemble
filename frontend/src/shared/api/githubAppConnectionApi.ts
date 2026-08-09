import type { ZodType } from 'zod'

import {
  githubAppCapabilitySchema,
  githubAppConnectionListSchema,
  githubAppConnectionSchema,
  githubAppRefreshRequestSchema,
  githubInstallationRequestSchema,
  type GitHubAppCapabilityDto,
  type GitHubAppConnectionDto,
  type GitHubAppConnectionListDto,
  type GitHubAppRefreshRequest,
  type GitHubInstallationRequestDto,
} from './githubAppConnectionContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

const ROOT = '/github-app-connections'

export function getGitHubAppCapability(signal?: AbortSignal): Promise<GitHubAppCapabilityDto> {
  return apiClient
    .get<unknown>(`${ROOT}/capability`, { signal })
    .then((value) => parse(githubAppCapabilitySchema, value))
}

export function startGitHubAppConnection(): Promise<GitHubInstallationRequestDto> {
  return apiClient
    .post<unknown>(`${ROOT}/installation-requests`)
    .then((value) => parse(githubInstallationRequestSchema, value))
}

export function listGitHubAppConnections(
  signal?: AbortSignal,
): Promise<GitHubAppConnectionListDto> {
  return apiClient
    .get<unknown>(ROOT, { signal })
    .then((value) => parse(githubAppConnectionListSchema, value))
}

export function refreshGitHubAppConnection(
  connectionId: string,
  request: GitHubAppRefreshRequest,
): Promise<GitHubAppConnectionDto> {
  const validated = githubAppRefreshRequestSchema.parse(request)
  return apiClient
    .post<unknown>(`${ROOT}/${encodeURIComponent(connectionId)}/refresh`, validated)
    .then((value) => parse(githubAppConnectionSchema, value))
}

export function disconnectGitHubAppConnection(
  connectionId: string,
  version: number,
): Promise<GitHubAppConnectionDto> {
  return apiClient
    .delete<unknown>(`${ROOT}/${encodeURIComponent(connectionId)}`, {
      params: { version, uninstallConfirmed: true },
    })
    .then((value) => parse(githubAppConnectionSchema, value))
}

function parse<T>(schema: ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value)
  if (result.success) return result.data
  throw new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: 'GitHub App 연결 정보를 안전하게 확인하지 못했어요.',
  })
}
