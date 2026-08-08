import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from './errors'
import { apiClient } from './http'
import * as careerArtifactApi from './careerArtifactApi'
import { CAREER_ARTIFACT_MIME_TYPES } from './careerArtifactContracts'

describe('Career Artifact API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(apiClient, 'ensureCsrf').mockResolvedValue({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token',
    })
  })

  it('maps all eleven public operations with exact paths, queries, bodies and headers', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(readiness())
      .mockResolvedValueOnce([model()])
      .mockResolvedValueOnce(page([summary()]))
      .mockResolvedValueOnce(detail())
      .mockResolvedValueOnce(page([version()]))
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce(accepted())
      .mockResolvedValueOnce(accepted())
      .mockResolvedValueOnce(detail('ARCHIVED'))
      .mockResolvedValueOnce(detail('ACTIVE'))
      .mockResolvedValueOnce(download())
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)

    await careerArtifactApi.getCareerArtifactReadiness()
    await careerArtifactApi.listCareerArtifactAiModels('RESUME')
    await careerArtifactApi.createCareerArtifact(createRequest(), 'create-key')
    await careerArtifactApi.listCareerArtifacts({
      artifactType: 'RESUME',
      lifecycleStatus: 'ACTIVE',
      page: 1,
      size: 10,
      sort: 'createdAt,desc',
    })
    await careerArtifactApi.getCareerArtifact(uuid(1))
    await careerArtifactApi.listCareerArtifactVersions(uuid(1), {
      page: 2,
      size: 20,
      sort: 'versionNo,asc',
    })
    await careerArtifactApi.generateCareerArtifactVersion(uuid(1), regenerateRequest(), 'regen-key')
    await careerArtifactApi.archiveCareerArtifact(uuid(1), 1)
    await careerArtifactApi.unarchiveCareerArtifact(uuid(1), 2)
    await careerArtifactApi.createCareerArtifactDownloadUrl(uuid(1), uuid(2))
    await careerArtifactApi.deleteCareerArtifact(uuid(1), 3)

    expect(get).toHaveBeenNthCalledWith(1, '/career-artifacts/readiness')
    expect(get).toHaveBeenNthCalledWith(2, '/career-artifacts/ai-models', {
      params: new URLSearchParams('type=RESUME'),
    })
    expect(post).toHaveBeenNthCalledWith(1, '/career-artifacts', createRequest(), {
      headers: { 'Idempotency-Key': 'create-key' },
    })
    expect(get).toHaveBeenNthCalledWith(3, '/career-artifacts', {
      params: new URLSearchParams(
        'artifactType=RESUME&lifecycleStatus=ACTIVE&page=1&size=10&sort=createdAt%2Cdesc',
      ),
    })
    expect(get).toHaveBeenNthCalledWith(4, `/career-artifacts/${uuid(1)}`)
    expect(get).toHaveBeenNthCalledWith(5, `/career-artifacts/${uuid(1)}/versions`, {
      params: new URLSearchParams('page=2&size=20&sort=versionNo%2Casc'),
    })
    expect(post).toHaveBeenNthCalledWith(
      2,
      `/career-artifacts/${uuid(1)}/generations`,
      regenerateRequest(),
      { headers: { 'Idempotency-Key': 'regen-key' } },
    )
    expect(post).toHaveBeenNthCalledWith(3, `/career-artifacts/${uuid(1)}/archive`, { version: 1 })
    expect(post).toHaveBeenNthCalledWith(4, `/career-artifacts/${uuid(1)}/unarchive`, {
      version: 2,
    })
    expect(post).toHaveBeenNthCalledWith(
      5,
      `/career-artifacts/${uuid(1)}/versions/${uuid(2)}/download-url`,
    )
    expect(remove).toHaveBeenCalledWith(`/career-artifacts/${uuid(1)}`, {
      params: { version: 3 },
    })
    expect(apiClient.ensureCsrf).toHaveBeenCalledTimes(6)
  })

  it('never adds Idempotency-Key to archive, unarchive, download, or delete', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce(detail('ARCHIVED'))
      .mockResolvedValueOnce(detail('ACTIVE'))
      .mockResolvedValueOnce(download())
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)
    await careerArtifactApi.archiveCareerArtifact(uuid(1), 1)
    await careerArtifactApi.unarchiveCareerArtifact(uuid(1), 2)
    await careerArtifactApi.createCareerArtifactDownloadUrl(uuid(1), uuid(2))
    await careerArtifactApi.deleteCareerArtifact(uuid(1), 3)
    expect(post.mock.calls.every((call) => call.length <= 2)).toBe(true)
    expect(remove.mock.calls[0]?.[1]).toEqual({ params: { version: 3 } })
  })

  it('fails closed on malformed enums, detail parity, version ownership, and Run ownership', async () => {
    vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce(page([{ ...summary(), generationStatus: 'DONE' }]))
      .mockResolvedValueOnce({ ...detail(), artifact: { ...summary(), id: uuid(9) } })
      .mockResolvedValueOnce(page([{ ...version(), artifactId: uuid(9) }]))
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValue({ ...accepted(), resourceId: uuid(9) })

    await expect(careerArtifactApi.listCareerArtifacts()).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
    await expect(careerArtifactApi.getCareerArtifact(uuid(1))).rejects.toBeInstanceOf(
      ApiClientError,
    )
    await expect(careerArtifactApi.listCareerArtifactVersions(uuid(1))).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
    await expect(
      careerArtifactApi.generateCareerArtifactVersion(uuid(1), regenerateRequest(), 'key'),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' })
    expect(post).toHaveBeenCalledTimes(1)
  })

  it('encodes artifact and version identifiers in paths', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue(download())
    await careerArtifactApi.createCareerArtifactDownloadUrl('artifact/../other', 'version/1')
    expect(post).toHaveBeenCalledWith(
      '/career-artifacts/artifact%2F..%2Fother/versions/version%2F1/download-url',
    )
  })
})

const now = '2026-08-08T00:00:00Z'

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: false,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 1,
    verifiedGitHubExperienceCount: 1,
    verifiedStrengthCount: 1,
    canGenerateResume: true,
    canGeneratePortfolio: true,
    warnings: [],
  }
}

function model() {
  return {
    id: 'gpt-5-mini',
    displayName: 'GPT-5 mini',
    description: '빠른 모델',
    recommended: true,
  }
}

function summary(lifecycleStatus = 'ACTIVE') {
  return {
    id: uuid(1),
    artifactType: 'RESUME',
    title: '백엔드 이력서',
    lifecycleStatus,
    generationStatus: 'SUCCEEDED',
    currentVersionId: uuid(2),
    currentVersionNo: 1,
    latestAgentRunId: uuid(3),
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

function version() {
  return {
    id: uuid(2),
    artifactId: uuid(1),
    versionNo: 1,
    model: 'gpt-5-mini',
    templateKey: 'resume-ats-v1',
    mimeType: CAREER_ARTIFACT_MIME_TYPES.RESUME,
    fileSizeBytes: 1024,
    createdAt: now,
  }
}

function detail(lifecycleStatus = 'ACTIVE') {
  return {
    artifact: summary(lifecycleStatus),
    currentVersion: version(),
    preview: {
      headline: '백엔드 개발자',
      summary: null,
      sections: [
        {
          type: 'EXPERIENCE',
          title: '주요 경험',
          items: [
            {
              heading: '결제 전환 개선',
              subheading: null,
              period: null,
              bullets: ['검증된 경험'],
              evidenceRefs: [
                {
                  experienceItemId: uuid(4),
                  evidenceId: uuid(5),
                  usageType: 'PRIMARY_EXPERIENCE',
                  title: '결제 전환 개선',
                },
              ],
            },
          ],
        },
      ],
      warnings: [],
    },
    latestRun: {
      id: uuid(3),
      workflowType: 'RESUME_GENERATION',
      resourceType: 'CAREER_ARTIFACT',
      resourceId: uuid(1),
      status: 'SUCCEEDED',
      currentStep: 'COMPLETED',
      progressPercent: 100,
      requestedQualityMode: 'BALANCED',
      highestModelTierUsed: 'BALANCED',
      estimatedCostUsd: 0,
      reservedCostUsd: 0,
      actualCostUsd: 0,
      retryable: false,
      cancellable: false,
      requiredUserAction: null,
      stateVersion: 2,
      queuedAt: now,
      updatedAt: now,
    },
  }
}

function accepted() {
  return {
    agentRunId: uuid(3),
    status: 'QUEUED',
    resourceType: 'CAREER_ARTIFACT',
    resourceId: uuid(1),
    replayed: false,
  }
}

function createRequest() {
  return {
    artifactType: 'RESUME' as const,
    title: '백엔드 이력서',
    experienceItemIds: [uuid(4)],
    model: 'gpt-5-mini',
    templateKey: 'resume-ats-v1',
    includeProfileSections: ['PROFILE' as const],
    renderProfile: {
      displayName: '지원자',
      email: null,
      phone: null,
      links: [],
      includeContact: false,
    },
  }
}

function regenerateRequest() {
  const request = createRequest()
  return {
    experienceItemIds: request.experienceItemIds,
    model: request.model,
    templateKey: 'resume-ats-v1' as const,
    includeProfileSections: request.includeProfileSections,
    renderProfile: request.renderProfile,
    version: 1,
  }
}

function download() {
  return { url: 'http://localhost:9000/resume', expiresAt: now, filename: 'resume.docx' }
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
