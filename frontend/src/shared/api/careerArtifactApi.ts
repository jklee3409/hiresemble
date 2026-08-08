import type { ZodType } from 'zod'

import {
  careerArtifactAiModelCatalogSchema,
  careerArtifactDetailSchema,
  careerArtifactDownloadUrlSchema,
  careerArtifactPageSchema,
  careerArtifactReadinessSchema,
  careerArtifactRunAcceptedSchema,
  careerArtifactVersionPageSchema,
  type CareerArtifactAiModelDto,
  type CareerArtifactDetailDto,
  type CareerArtifactDownloadUrlDto,
  type CareerArtifactLifecycle,
  type CareerArtifactPageDto,
  type CareerArtifactReadinessDto,
  type CareerArtifactType,
  type CareerArtifactVersionPageDto,
  type CreateCareerArtifactRequest,
  type GenerateCareerArtifactRequest,
} from './careerArtifactContracts'
import type { RunAcceptedDto } from './agentRunContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export type CareerArtifactListSort = 'updatedAt,desc' | 'createdAt,desc'
export type CareerArtifactVersionSort = 'versionNo,desc' | 'versionNo,asc'

export interface CareerArtifactListParams {
  artifactType?: CareerArtifactType
  lifecycleStatus?: CareerArtifactLifecycle
  page?: number
  size?: number
  sort?: CareerArtifactListSort
}

export interface CareerArtifactVersionListParams {
  page?: number
  size?: number
  sort?: CareerArtifactVersionSort
}

export function getCareerArtifactReadiness(): Promise<CareerArtifactReadinessDto> {
  return apiClient
    .get<unknown>('/career-artifacts/readiness')
    .then((value) => parseCareerArtifactResponse(careerArtifactReadinessSchema, value))
}

export function listCareerArtifactAiModels(
  artifactType: CareerArtifactType,
): Promise<CareerArtifactAiModelDto[]> {
  return apiClient
    .get<unknown>('/career-artifacts/ai-models', {
      params: new URLSearchParams({ type: artifactType }),
    })
    .then((value) => parseCareerArtifactResponse(careerArtifactAiModelCatalogSchema, value))
}

export async function createCareerArtifact(
  request: CreateCareerArtifactRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  await apiClient.ensureCsrf()
  return apiClient
    .post<unknown>('/career-artifacts', request, idempotencyHeader(idempotencyKey))
    .then((value) => parseCareerArtifactResponse(careerArtifactRunAcceptedSchema, value))
}

export function listCareerArtifacts(
  params: CareerArtifactListParams = {},
): Promise<CareerArtifactPageDto> {
  return apiClient
    .get<unknown>('/career-artifacts', { params: toCareerArtifactSearchParams(params) })
    .then((value) => parseCareerArtifactResponse(careerArtifactPageSchema, value))
}

export function getCareerArtifact(artifactId: string): Promise<CareerArtifactDetailDto> {
  return apiClient
    .get<unknown>(careerArtifactPath(artifactId))
    .then((value) => parseCareerArtifactResponse(careerArtifactDetailSchema, value))
    .then((detail) => {
      if (detail.artifact.id !== artifactId) throw invalidCareerArtifactResponse()
      return detail
    })
}

export function listCareerArtifactVersions(
  artifactId: string,
  params: CareerArtifactVersionListParams = {},
): Promise<CareerArtifactVersionPageDto> {
  return apiClient
    .get<unknown>(`${careerArtifactPath(artifactId)}/versions`, {
      params: toCareerArtifactSearchParams(params),
    })
    .then((value) => parseCareerArtifactResponse(careerArtifactVersionPageSchema, value))
    .then((page) => {
      if (page.items.some((version) => version.artifactId !== artifactId)) {
        throw invalidCareerArtifactResponse()
      }
      return page
    })
}

export async function generateCareerArtifactVersion(
  artifactId: string,
  request: GenerateCareerArtifactRequest,
  idempotencyKey: string,
): Promise<RunAcceptedDto> {
  await apiClient.ensureCsrf()
  return apiClient
    .post<unknown>(
      `${careerArtifactPath(artifactId)}/generations`,
      request,
      idempotencyHeader(idempotencyKey),
    )
    .then((value) => parseCareerArtifactResponse(careerArtifactRunAcceptedSchema, value))
    .then((accepted) => {
      if (accepted.resourceId !== artifactId) throw invalidCareerArtifactResponse()
      return accepted
    })
}

export function archiveCareerArtifact(
  artifactId: string,
  version: number,
): Promise<CareerArtifactDetailDto> {
  return mutateLifecycle(artifactId, 'archive', version)
}

export function unarchiveCareerArtifact(
  artifactId: string,
  version: number,
): Promise<CareerArtifactDetailDto> {
  return mutateLifecycle(artifactId, 'unarchive', version)
}

export async function createCareerArtifactDownloadUrl(
  artifactId: string,
  versionId: string,
): Promise<CareerArtifactDownloadUrlDto> {
  await apiClient.ensureCsrf()
  return apiClient
    .post<unknown>(
      `${careerArtifactPath(artifactId)}/versions/${encodeURIComponent(versionId)}/download-url`,
    )
    .then((value) => parseCareerArtifactResponse(careerArtifactDownloadUrlSchema, value))
}

export async function deleteCareerArtifact(artifactId: string, version: number): Promise<void> {
  await apiClient.ensureCsrf()
  return apiClient.delete<void>(careerArtifactPath(artifactId), { params: { version } })
}

export function createCareerArtifactIdempotencyKey(action: 'create' | 'regenerate'): string {
  return `career-artifact-${action}:${globalThis.crypto.randomUUID()}`
}

export function toCareerArtifactSearchParams(
  params: CareerArtifactListParams | CareerArtifactVersionListParams,
): URLSearchParams {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return query
}

async function mutateLifecycle(
  artifactId: string,
  action: 'archive' | 'unarchive',
  version: number,
): Promise<CareerArtifactDetailDto> {
  await apiClient.ensureCsrf()
  return apiClient
    .post<unknown>(`${careerArtifactPath(artifactId)}/${action}`, { version })
    .then((value) => parseCareerArtifactResponse(careerArtifactDetailSchema, value))
    .then((detail) => {
      if (detail.artifact.id !== artifactId) throw invalidCareerArtifactResponse()
      return detail
    })
}

function parseCareerArtifactResponse<T>(schema: ZodType<T>, value: unknown): T {
  const parsed = schema.safeParse(value)
  if (parsed.success) return parsed.data
  throw invalidCareerArtifactResponse()
}

function invalidCareerArtifactResponse(): ApiClientError {
  return new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: '생성 자료 정보를 확인하는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
  })
}

function careerArtifactPath(artifactId: string): string {
  return `/career-artifacts/${encodeURIComponent(artifactId)}`
}

function idempotencyHeader(idempotencyKey: string) {
  return { headers: { 'Idempotency-Key': idempotencyKey } }
}
