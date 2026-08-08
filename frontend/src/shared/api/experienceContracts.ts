import { z } from 'zod'

export const EVIDENCE_SOURCE_TYPES = [
  'EDUCATION',
  'CERTIFICATION',
  'LANGUAGE_SCORE',
  'AWARD',
  'CAREER',
  'ACTIVITY',
  'DOCUMENT_CHUNK',
  'GITHUB_REPOSITORY',
  'EXPERIENCE',
  'MANUAL',
] as const

const verificationStatuses = ['PENDING', 'VERIFIED', 'REJECTED', 'SOURCE_DELETED'] as const
const matchKinds = ['NEW', 'SAME_EXPERIENCE', 'RELATED_DIFFERENT', 'CONFLICT'] as const
const linkKinds = ['PRIMARY_SOURCE', 'CORROBORATING'] as const
const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const similaritySchema = z.number().min(0).max(1).nullable()

export const experienceItemSchema = z.object({
  id: uuidSchema,
  evidenceCategory: z.string().min(1).max(100),
  title: z.string().min(1).max(500),
  content: z.string().min(1).max(20_000),
  verificationStatus: z.enum(verificationStatuses),
  matchKind: z.enum(matchKinds),
  matchedExperienceItemId: uuidSchema.nullable(),
  matchSimilarity: similaritySchema,
  reviewRequired: z.boolean(),
  sourceCount: z.number().int().nonnegative(),
  documentSourceCount: z.number().int().nonnegative(),
  githubRepositorySourceCount: z.number().int().nonnegative(),
  primaryDocumentName: z.string().max(255).nullable(),
  version: z.number().int().nonnegative(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
})

export const experienceSourceSchema = z
  .object({
    evidenceId: uuidSchema,
    sourceType: z.enum(EVIDENCE_SOURCE_TYPES),
    documentId: uuidSchema.nullable(),
    verificationStatus: z.enum(verificationStatuses),
    relationKind: z.enum(linkKinds),
    similarity: similaritySchema,
    githubSourceId: uuidSchema.nullable(),
    githubRepositoryId: uuidSchema.nullable(),
    repositoryName: z.string().max(201).nullable(),
    repositoryUrl: z.url().max(500).nullable(),
    commitShaShort: z.string().min(7).max(12).nullable(),
    capturedAt: nullableInstantSchema,
    sourceExcerpt: z.string().max(500).nullable(),
    sourceDeletedAt: nullableInstantSchema,
    createdAt: instantSchema,
  })
  .superRefine((value, context) => {
    if (value.sourceType !== 'GITHUB_REPOSITORY') return
    const hasLiveGitHubSource =
      value.githubSourceId !== null &&
      value.githubRepositoryId !== null &&
      value.repositoryName !== null &&
      value.repositoryUrl !== null &&
      value.commitShaShort !== null &&
      value.capturedAt !== null
    if (value.sourceDeletedAt === null && !hasLiveGitHubSource) {
      context.addIssue({
        code: 'custom',
        path: ['githubSourceId'],
        message: '활성 GitHub provenance 필드가 누락되었습니다.',
      })
    }
  })

export const experienceDetailSchema = z.object({
  item: experienceItemSchema,
  sources: z.array(experienceSourceSchema),
})

export const experiencePageSchema = z.object({
  items: z.array(experienceItemSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
})
