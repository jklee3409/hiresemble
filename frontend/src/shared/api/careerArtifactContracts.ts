import { z } from 'zod'

import { agentRunSummarySchema, runAcceptedSchema } from './agentRunContracts'

export const CAREER_ARTIFACT_TYPES = ['RESUME', 'PORTFOLIO'] as const
export const CAREER_ARTIFACT_LIFECYCLES = ['ACTIVE', 'ARCHIVED'] as const
export const CAREER_ARTIFACT_GENERATION_STATUSES = [
  'NOT_STARTED',
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'INTERRUPTED',
] as const
export const CAREER_ARTIFACT_PROFILE_SECTIONS = [
  'PROFILE',
  'EDUCATIONS',
  'CERTIFICATIONS',
  'LANGUAGE_SCORES',
  'AWARDS',
  'CAREERS',
  'ACTIVITIES',
] as const
export const CAREER_ARTIFACT_EVIDENCE_USAGE_TYPES = [
  'PRIMARY_EXPERIENCE',
  'STRENGTH',
  'SUPPORTING_FACT',
] as const
export const PORTFOLIO_SLIDE_TYPES = [
  'COVER',
  'PROFILE_SUMMARY',
  'STRENGTH_OVERVIEW',
  'PROJECT_CASE_STUDY',
  'TECHNICAL_DECISION',
  'IMPACT_AND_LEARNING',
  'CLOSING',
] as const
export const PORTFOLIO_VISUAL_TYPES = [
  'NONE',
  'PROCESS',
  'ARCHITECTURE',
  'TIMELINE',
  'IMPACT_METRICS',
] as const

export const CAREER_ARTIFACT_TEMPLATES = {
  RESUME: 'resume-ats-v1',
  PORTFOLIO: 'portfolio-interview-v1',
} as const

export const CAREER_ARTIFACT_MIME_TYPES = {
  RESUME: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  PORTFOLIO: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
} as const

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nonBlank = (maximum: number) => z.string().trim().min(1).max(maximum)
const exactIdentifier = (maximum: number) =>
  z
    .string()
    .min(1)
    .max(maximum)
    .refine((value) => value.trim() === value, 'identifier must not contain outer whitespace')
const warningSchema = nonBlank(500)

export const careerArtifactTypeSchema = z.enum(CAREER_ARTIFACT_TYPES)
export const careerArtifactLifecycleSchema = z.enum(CAREER_ARTIFACT_LIFECYCLES)
export const careerArtifactGenerationStatusSchema = z.enum(CAREER_ARTIFACT_GENERATION_STATUSES)
export const careerArtifactProfileSectionSchema = z.enum(CAREER_ARTIFACT_PROFILE_SECTIONS)
export const careerArtifactEvidenceUsageSchema = z.enum(CAREER_ARTIFACT_EVIDENCE_USAGE_TYPES)
export const portfolioSlideTypeSchema = z.enum(PORTFOLIO_SLIDE_TYPES)
export const portfolioVisualTypeSchema = z.enum(PORTFOLIO_VISUAL_TYPES)

export const careerArtifactReadinessSchema = z
  .object({
    hasUploadedResume: z.boolean(),
    hasUploadedPortfolio: z.boolean(),
    hasGeneratedResume: z.boolean(),
    hasGeneratedPortfolio: z.boolean(),
    verifiedExperienceCount: z.number().int().nonnegative(),
    verifiedGitHubExperienceCount: z.number().int().nonnegative(),
    verifiedStrengthCount: z.number().int().nonnegative(),
    canGenerateResume: z.boolean(),
    canGeneratePortfolio: z.boolean(),
    warnings: z.array(warningSchema).max(100),
  })
  .strict()

export const careerArtifactAiModelSchema = z
  .object({
    id: exactIdentifier(64),
    displayName: nonBlank(120),
    description: nonBlank(500),
    recommended: z.boolean(),
  })
  .strict()

export const careerArtifactAiModelCatalogSchema = z.array(careerArtifactAiModelSchema).max(100)

export const careerArtifactSummarySchema = z
  .object({
    id: uuidSchema,
    artifactType: careerArtifactTypeSchema,
    title: nonBlank(120),
    lifecycleStatus: careerArtifactLifecycleSchema,
    generationStatus: careerArtifactGenerationStatusSchema,
    currentVersionId: uuidSchema.nullable(),
    currentVersionNo: z.number().int().positive().nullable(),
    latestAgentRunId: uuidSchema.nullable(),
    version: z.number().int().nonnegative(),
    createdAt: instantSchema,
    updatedAt: instantSchema,
  })
  .strict()
  .superRefine((value, context) => {
    if ((value.currentVersionId === null) !== (value.currentVersionNo === null)) {
      context.addIssue({
        code: 'custom',
        path: ['currentVersionNo'],
        message: 'current version fields must be provided together',
      })
    }
  })

export const careerArtifactVersionSummarySchema = z
  .object({
    id: uuidSchema,
    artifactId: uuidSchema,
    versionNo: z.number().int().positive(),
    model: exactIdentifier(64),
    templateKey: exactIdentifier(80),
    mimeType: nonBlank(150),
    fileSizeBytes: z.number().int().nonnegative(),
    createdAt: instantSchema,
  })
  .strict()

export const careerArtifactEvidenceRefSchema = z
  .object({
    experienceItemId: uuidSchema,
    evidenceId: uuidSchema,
    usageType: careerArtifactEvidenceUsageSchema,
    title: nonBlank(250),
  })
  .strict()

export const resumeArtifactPreviewSchema = z
  .object({
    headline: nonBlank(200).nullable(),
    summary: nonBlank(2_000).nullable(),
    sections: z
      .array(
        z
          .object({
            type: nonBlank(50),
            title: nonBlank(100),
            items: z
              .array(
                z
                  .object({
                    heading: nonBlank(250).nullable(),
                    subheading: nonBlank(250).nullable(),
                    period: nonBlank(100).nullable(),
                    bullets: z.array(nonBlank(500)).max(10),
                    evidenceRefs: z.array(careerArtifactEvidenceRefSchema).max(20),
                  })
                  .strict(),
              )
              .max(30),
          })
          .strict(),
      )
      .min(1)
      .max(12),
    warnings: z.array(warningSchema).max(20),
  })
  .strict()

export const portfolioArtifactPreviewSchema = z
  .object({
    slides: z
      .array(
        z
          .object({
            slideNo: z.number().int().min(1).max(12),
            slideType: portfolioSlideTypeSchema,
            title: nonBlank(120),
            subtitle: nonBlank(200).nullable(),
            items: z.array(nonBlank(500)).max(10),
            visualType: portfolioVisualTypeSchema,
            evidenceRefs: z.array(careerArtifactEvidenceRefSchema).max(20),
          })
          .strict(),
      )
      .min(6)
      .max(12)
      .superRefine((slides, context) => {
        const numbers = slides.map((slide) => slide.slideNo)
        if (
          new Set(numbers).size !== numbers.length ||
          numbers.some((value, index) => value !== index + 1)
        ) {
          context.addIssue({
            code: 'custom',
            message: 'slide numbers must be unique and sequential',
          })
        }
      }),
    warnings: z.array(warningSchema).max(20),
  })
  .strict()

export const careerArtifactPreviewSchema = z.union([
  resumeArtifactPreviewSchema,
  portfolioArtifactPreviewSchema,
])

export const careerArtifactDetailSchema = z
  .object({
    artifact: careerArtifactSummarySchema,
    currentVersion: careerArtifactVersionSummarySchema.nullable(),
    preview: careerArtifactPreviewSchema.nullable(),
    latestRun: agentRunSummarySchema.nullable(),
  })
  .strict()
  .superRefine((value, context) => {
    const { artifact, currentVersion, preview, latestRun } = value
    const hasCurrent = artifact.currentVersionId !== null
    if (hasCurrent !== (currentVersion !== null) || hasCurrent !== (preview !== null)) {
      context.addIssue({
        code: 'custom',
        path: ['currentVersion'],
        message: 'current version and preview parity is invalid',
      })
    }
    if (currentVersion !== null) {
      if (
        currentVersion.id !== artifact.currentVersionId ||
        currentVersion.artifactId !== artifact.id ||
        currentVersion.versionNo !== artifact.currentVersionNo ||
        currentVersion.mimeType !== CAREER_ARTIFACT_MIME_TYPES[artifact.artifactType] ||
        currentVersion.templateKey !== CAREER_ARTIFACT_TEMPLATES[artifact.artifactType]
      ) {
        context.addIssue({
          code: 'custom',
          path: ['currentVersion'],
          message: 'current version does not match its artifact',
        })
      }
    }
    if (
      preview !== null &&
      ((artifact.artifactType === 'RESUME' && !('sections' in preview)) ||
        (artifact.artifactType === 'PORTFOLIO' && !('slides' in preview)))
    ) {
      context.addIssue({
        code: 'custom',
        path: ['preview'],
        message: 'preview type does not match artifact type',
      })
    }
    if (
      latestRun !== null &&
      (latestRun.resourceType !== 'CAREER_ARTIFACT' ||
        latestRun.resourceId !== artifact.id ||
        latestRun.workflowType !==
          (artifact.artifactType === 'RESUME' ? 'RESUME_GENERATION' : 'PORTFOLIO_GENERATION'))
    ) {
      context.addIssue({
        code: 'custom',
        path: ['latestRun'],
        message: 'latest run does not match artifact',
      })
    }
  })

export const careerArtifactDownloadUrlSchema = z
  .object({
    url: z.url().refine((value) => ['http:', 'https:'].includes(new URL(value).protocol)),
    expiresAt: instantSchema,
    filename: z
      .string()
      .min(1)
      .max(255)
      .refine((value) => value.trim().length > 0),
  })
  .strict()

export const careerArtifactRunAcceptedSchema = runAcceptedSchema.superRefine((value, context) => {
  if (value.resourceType !== 'CAREER_ARTIFACT' || value.resourceId === null) {
    context.addIssue({
      code: 'custom',
      path: ['resourceType'],
      message: 'accepted run must target a career artifact',
    })
  }
})

const uniqueUuidArraySchema = z
  .array(uuidSchema)
  .min(1)
  .max(20)
  .refine((values) => new Set(values).size === values.length, 'experience IDs must be unique')
const uniqueProfileSectionsSchema = z
  .array(careerArtifactProfileSectionSchema)
  .max(7)
  .refine((values) => new Set(values).size === values.length, 'profile sections must be unique')
const httpsUrlSchema = z
  .url()
  .max(500)
  .refine((value) => new URL(value).protocol === 'https:', 'link must use HTTPS')

export const careerArtifactRenderProfileSchema = z
  .object({
    displayName: nonBlank(100),
    email: z.email().min(3).max(320).nullable(),
    phone: nonBlank(30).nullable(),
    links: z
      .array(
        z
          .object({
            label: nonBlank(50),
            url: httpsUrlSchema,
          })
          .strict(),
      )
      .max(5),
    includeContact: z.boolean(),
  })
  .strict()

export const createCareerArtifactRequestSchema = z
  .object({
    artifactType: careerArtifactTypeSchema,
    title: nonBlank(120),
    experienceItemIds: uniqueUuidArraySchema,
    model: exactIdentifier(64),
    templateKey: exactIdentifier(80),
    includeProfileSections: uniqueProfileSectionsSchema,
    renderProfile: careerArtifactRenderProfileSchema,
  })
  .strict()
  .superRefine((value, context) => {
    if (value.templateKey !== CAREER_ARTIFACT_TEMPLATES[value.artifactType]) {
      context.addIssue({
        code: 'custom',
        path: ['templateKey'],
        message: 'template does not match type',
      })
    }
  })

export const generateCareerArtifactRequestSchema = z
  .object({
    experienceItemIds: uniqueUuidArraySchema,
    model: exactIdentifier(64),
    templateKey: z.enum([CAREER_ARTIFACT_TEMPLATES.RESUME, CAREER_ARTIFACT_TEMPLATES.PORTFOLIO]),
    includeProfileSections: uniqueProfileSectionsSchema,
    renderProfile: careerArtifactRenderProfileSchema,
    version: z.number().int().nonnegative(),
  })
  .strict()

export const careerArtifactVersionRequestSchema = z
  .object({ version: z.number().int().nonnegative() })
  .strict()

function pageSchema<T extends z.ZodTypeAny>(item: T) {
  return z
    .object({
      items: z.array(item).max(100),
      page: z.number().int().nonnegative(),
      size: z.number().int().min(1).max(100),
      totalElements: z.number().int().nonnegative(),
      totalPages: z.number().int().nonnegative(),
    })
    .strict()
    .superRefine((value, context) => {
      if (value.items.length > value.size) {
        context.addIssue({
          code: 'custom',
          path: ['items'],
          message: 'page exceeds requested size',
        })
      }
    })
}

export const careerArtifactPageSchema = pageSchema(careerArtifactSummarySchema)
export const careerArtifactVersionPageSchema = pageSchema(careerArtifactVersionSummarySchema)

export type CareerArtifactType = z.infer<typeof careerArtifactTypeSchema>
export type CareerArtifactLifecycle = z.infer<typeof careerArtifactLifecycleSchema>
export type CareerArtifactGenerationStatus = z.infer<typeof careerArtifactGenerationStatusSchema>
export type CareerArtifactProfileSection = z.infer<typeof careerArtifactProfileSectionSchema>
export type CareerArtifactEvidenceUsage = z.infer<typeof careerArtifactEvidenceUsageSchema>
export type PortfolioSlideType = z.infer<typeof portfolioSlideTypeSchema>
export type PortfolioVisualType = z.infer<typeof portfolioVisualTypeSchema>
export type CareerArtifactReadinessDto = z.infer<typeof careerArtifactReadinessSchema>
export type CareerArtifactAiModelDto = z.infer<typeof careerArtifactAiModelSchema>
export type CareerArtifactSummaryDto = z.infer<typeof careerArtifactSummarySchema>
export type CareerArtifactVersionSummaryDto = z.infer<typeof careerArtifactVersionSummarySchema>
export type ResumeArtifactPreviewDto = z.infer<typeof resumeArtifactPreviewSchema>
export type PortfolioArtifactPreviewDto = z.infer<typeof portfolioArtifactPreviewSchema>
export type CareerArtifactPreviewDto = z.infer<typeof careerArtifactPreviewSchema>
export type CareerArtifactDetailDto = z.infer<typeof careerArtifactDetailSchema>
export type CareerArtifactDownloadUrlDto = z.infer<typeof careerArtifactDownloadUrlSchema>
export type CreateCareerArtifactRequest = z.infer<typeof createCareerArtifactRequestSchema>
export type GenerateCareerArtifactRequest = z.infer<typeof generateCareerArtifactRequestSchema>
export type CareerArtifactVersionRequest = z.infer<typeof careerArtifactVersionRequestSchema>
export type CareerArtifactRenderProfile = z.infer<typeof careerArtifactRenderProfileSchema>
export type CareerArtifactPageDto = z.infer<typeof careerArtifactPageSchema>
export type CareerArtifactVersionPageDto = z.infer<typeof careerArtifactVersionPageSchema>

export function normalizeCareerArtifactRenderProfile(
  profile: CareerArtifactRenderProfile,
): CareerArtifactRenderProfile {
  const normalized = {
    ...profile,
    displayName: profile.displayName.trim(),
    email: blankToNull(profile.email),
    phone: blankToNull(profile.phone),
    links: profile.links.map((link) => ({ label: link.label.trim(), url: link.url.trim() })),
  }
  return normalized.includeContact
    ? normalized
    : { ...normalized, email: null, phone: null, links: [] }
}

function blankToNull(value: string | null): string | null {
  if (value === null) return null
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}
