import { describe, expect, it } from 'vitest'

import {
  CAREER_ARTIFACT_EVIDENCE_USAGE_TYPES,
  CAREER_ARTIFACT_GENERATION_STATUSES,
  CAREER_ARTIFACT_LIFECYCLES,
  CAREER_ARTIFACT_MIME_TYPES,
  CAREER_ARTIFACT_PROFILE_SECTIONS,
  CAREER_ARTIFACT_TYPES,
  PORTFOLIO_SLIDE_TYPES,
  PORTFOLIO_VISUAL_TYPES,
  careerArtifactDetailSchema,
  careerArtifactDownloadUrlSchema,
  careerArtifactReadinessSchema,
  createCareerArtifactRequestSchema,
  normalizeCareerArtifactRenderProfile,
  portfolioArtifactPreviewSchema,
  resumeArtifactPreviewSchema,
} from './careerArtifactContracts'

describe('Career Artifact contracts', () => {
  it('keeps every public enum literal closed', () => {
    expect(CAREER_ARTIFACT_TYPES).toEqual(['RESUME', 'PORTFOLIO'])
    expect(CAREER_ARTIFACT_LIFECYCLES).toEqual(['ACTIVE', 'ARCHIVED'])
    expect(CAREER_ARTIFACT_GENERATION_STATUSES).toHaveLength(7)
    expect(CAREER_ARTIFACT_PROFILE_SECTIONS).toHaveLength(7)
    expect(CAREER_ARTIFACT_EVIDENCE_USAGE_TYPES).toHaveLength(3)
    expect(PORTFOLIO_SLIDE_TYPES).toHaveLength(7)
    expect(PORTFOLIO_VISUAL_TYPES).toHaveLength(5)
    expect(careerArtifactReadinessSchema.safeParse({ ...readiness(), warnings: [] }).success).toBe(
      true,
    )
    expect(
      careerArtifactReadinessSchema.safeParse({ ...readiness(), warnings: [], extra: 'private' })
        .success,
    ).toBe(false)
  })

  it('accepts nullable Resume fields and validates Portfolio slide unions and sequence', () => {
    expect(resumeArtifactPreviewSchema.safeParse(resumePreview()).success).toBe(true)
    expect(portfolioArtifactPreviewSchema.safeParse(portfolioPreview()).success).toBe(true)
    expect(
      portfolioArtifactPreviewSchema.safeParse({
        ...portfolioPreview(),
        slides: portfolioPreview().slides.map((slide, index) => ({
          ...slide,
          slideNo: index === 1 ? 1 : slide.slideNo,
        })),
      }).success,
    ).toBe(false)
    expect(
      portfolioArtifactPreviewSchema.safeParse({
        ...portfolioPreview(),
        slides: portfolioPreview().slides.map((slide, index) =>
          index === 0 ? { ...slide, visualType: 'SCREENSHOT' } : slide,
        ),
      }).success,
    ).toBe(false)
  })

  it('fails closed for current version, preview, MIME, template and Run resource parity', () => {
    expect(careerArtifactDetailSchema.safeParse(resumeDetail()).success).toBe(true)
    expect(
      careerArtifactDetailSchema.safeParse({
        ...resumeDetail(),
        currentVersion: {
          ...resumeDetail().currentVersion,
          mimeType: CAREER_ARTIFACT_MIME_TYPES.PORTFOLIO,
        },
      }).success,
    ).toBe(false)
    expect(
      careerArtifactDetailSchema.safeParse({ ...resumeDetail(), preview: portfolioPreview() })
        .success,
    ).toBe(false)
    expect(
      careerArtifactDetailSchema.safeParse({
        ...resumeDetail(),
        latestRun: { ...resumeDetail().latestRun, resourceId: uuid(9) },
      }).success,
    ).toBe(false)
    expect(
      careerArtifactDetailSchema.safeParse({
        ...resumeDetail(),
        currentVersion: { ...resumeDetail().currentVersion, rendererSnapshot: { private: true } },
      }).success,
    ).toBe(false)
  })

  it('requires a unique 1-20 experience selection and matching fixed template', () => {
    const request = createRequest()
    expect(createCareerArtifactRequestSchema.safeParse(request).success).toBe(true)
    expect(
      createCareerArtifactRequestSchema.safeParse({
        ...request,
        experienceItemIds: [uuid(1), uuid(1)],
      }).success,
    ).toBe(false)
    expect(
      createCareerArtifactRequestSchema.safeParse({ ...request, experienceItemIds: [] }).success,
    ).toBe(false)
    expect(
      createCareerArtifactRequestSchema.safeParse({
        ...request,
        experienceItemIds: Array.from({ length: 21 }, (_, index) => uuid(index + 1)),
      }).success,
    ).toBe(false)
    expect(
      createCareerArtifactRequestSchema.safeParse({ ...request, templateKey: 'custom-template' })
        .success,
    ).toBe(false)
    expect(
      createCareerArtifactRequestSchema.safeParse({ ...request, model: ` ${request.model}` })
        .success,
    ).toBe(false)
  })

  it('normalizes renderer-only contact out when includeContact is false', () => {
    expect(
      normalizeCareerArtifactRenderProfile({
        displayName: '  지원자  ',
        email: 'person@example.com',
        phone: '010-0000-0000',
        links: [{ label: ' GitHub ', url: 'https://github.com/example' }],
        includeContact: false,
      }),
    ).toEqual({
      displayName: '지원자',
      email: null,
      phone: null,
      links: [],
      includeContact: false,
    })
  })

  it('accepts HTTP or HTTPS download URLs and rejects executable schemes', () => {
    expect(
      careerArtifactDownloadUrlSchema.parse({
        url: 'http://localhost:9000/file',
        expiresAt: now,
        filename: ' server-resume.docx ',
      }).filename,
    ).toBe(' server-resume.docx ')
    expect(
      careerArtifactDownloadUrlSchema.safeParse({
        url: 'https://files.example/file',
        expiresAt: now,
        filename: 'portfolio.pptx',
      }).success,
    ).toBe(true)
    expect(
      careerArtifactDownloadUrlSchema.safeParse({
        url: 'javascript:alert(1)',
        expiresAt: now,
        filename: 'resume.docx',
      }).success,
    ).toBe(false)
    expect(
      careerArtifactDownloadUrlSchema.safeParse({
        url: 'data:text/plain,private',
        expiresAt: now,
        filename: 'resume.docx',
      }).success,
    ).toBe(false)
  })
})

const now = '2026-08-08T00:00:00Z'

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: false,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 3,
    verifiedGitHubExperienceCount: 2,
    verifiedStrengthCount: 1,
    canGenerateResume: true,
    canGeneratePortfolio: true,
  }
}

function evidenceRef() {
  return {
    experienceItemId: uuid(3),
    evidenceId: uuid(4),
    usageType: 'PRIMARY_EXPERIENCE',
    title: '결제 전환 개선',
  }
}

function resumePreview() {
  return {
    headline: null,
    summary: '검증된 경험을 바탕으로 작성한 요약',
    sections: [
      {
        type: 'EXPERIENCE',
        title: '주요 경험',
        items: [
          {
            heading: '결제 전환 개선',
            subheading: null,
            period: null,
            bullets: ['검증된 전환 개선 경험'],
            evidenceRefs: [evidenceRef()],
          },
        ],
      },
    ],
    warnings: [],
  }
}

function portfolioPreview() {
  return {
    slides: Array.from({ length: 6 }, (_, index) => ({
      slideNo: index + 1,
      slideType: index === 0 ? 'COVER' : index === 5 ? 'CLOSING' : 'PROJECT_CASE_STUDY',
      title: `슬라이드 ${index + 1}`,
      subtitle: index === 0 ? null : '검증된 경험',
      items: index === 0 || index === 5 ? [] : ['문제와 해결'],
      visualType: index === 0 ? 'NONE' : 'PROCESS',
      evidenceRefs: index === 0 || index === 5 ? [] : [evidenceRef()],
    })),
    warnings: [],
  }
}

function resumeDetail() {
  return {
    artifact: {
      id: uuid(1),
      artifactType: 'RESUME',
      title: '백엔드 이력서',
      lifecycleStatus: 'ACTIVE',
      generationStatus: 'SUCCEEDED',
      currentVersionId: uuid(2),
      currentVersionNo: 1,
      latestAgentRunId: uuid(5),
      version: 1,
      createdAt: now,
      updatedAt: now,
    },
    currentVersion: {
      id: uuid(2),
      artifactId: uuid(1),
      versionNo: 1,
      model: 'gpt-5-mini',
      templateKey: 'resume-ats-v1',
      mimeType: CAREER_ARTIFACT_MIME_TYPES.RESUME,
      fileSizeBytes: 1024,
      createdAt: now,
    },
    preview: resumePreview(),
    latestRun: {
      id: uuid(5),
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

function createRequest() {
  return {
    artifactType: 'RESUME',
    title: '백엔드 이력서',
    experienceItemIds: [uuid(3)],
    model: 'gpt-5-mini',
    templateKey: 'resume-ats-v1',
    includeProfileSections: ['PROFILE', 'CAREERS'],
    renderProfile: {
      displayName: '지원자',
      email: null,
      phone: null,
      links: [],
      includeContact: false,
    },
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
