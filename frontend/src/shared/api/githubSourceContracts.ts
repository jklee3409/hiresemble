import { z } from 'zod'

import { requiredUserActionSchema, runAcceptedSchema } from './agentRunContracts'

export const GITHUB_SOURCE_STATUSES = [
  'DISCOVERING',
  'WAITING_USER',
  'QUEUED',
  'RUNNING',
  'READY',
  'PARTIAL',
  'FAILED',
] as const
export const GITHUB_SOURCE_KINDS = ['ACCOUNT', 'REPOSITORY'] as const
export const GITHUB_ACCOUNT_TYPES = ['USER', 'ORGANIZATION'] as const
export const GITHUB_SOURCE_SORTS = ['updatedAt,desc', 'createdAt,desc'] as const
export const GITHUB_REPOSITORY_SORTS = ['pushedAt,desc', 'repositoryName,asc'] as const

export type GitHubSourceStatus = (typeof GITHUB_SOURCE_STATUSES)[number]
export type GitHubSourceKind = (typeof GITHUB_SOURCE_KINDS)[number]
export type GitHubAccountType = (typeof GITHUB_ACCOUNT_TYPES)[number]
export type GitHubSourceSort = (typeof GITHUB_SOURCE_SORTS)[number]
export type GitHubRepositorySort = (typeof GITHUB_REPOSITORY_SORTS)[number]

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const nonNegativeIntegerSchema = z.number().int().nonnegative()

export const gitHubRepositorySchema = z.object({
  id: uuidSchema,
  ownerLogin: z.string().min(1).max(39),
  repositoryName: z.string().min(1).max(100),
  canonicalUrl: z.url().max(500),
  description: z.string().max(500).nullable(),
  defaultBranch: z.string().min(1).max(255),
  fork: z.boolean(),
  archived: z.boolean(),
  selected: z.boolean(),
  pushedAt: nullableInstantSchema,
})

export const gitHubSourceSummarySchema = z
  .object({
    id: uuidSchema,
    sourceKind: z.enum(GITHUB_SOURCE_KINDS),
    accountType: z.enum(GITHUB_ACCOUNT_TYPES).nullable(),
    canonicalUrl: z.url().max(500),
    ownerLogin: z.string().min(1).max(39),
    repositoryName: z.string().min(1).max(100).nullable(),
    status: z.enum(GITHUB_SOURCE_STATUSES),
    discoveredRepositoryCount: nonNegativeIntegerSchema,
    selectedRepositoryCount: nonNegativeIntegerSchema.max(10),
    repositoryDiscoveryTruncated: z.boolean(),
    newExperienceCount: nonNegativeIntegerSchema,
    corroboratedExperienceCount: nonNegativeIntegerSchema,
    reviewRequiredCount: nonNegativeIntegerSchema,
    rejectedCandidateCount: nonNegativeIntegerSchema,
    snapshotIncomplete: z.boolean(),
    latestAgentRunId: uuidSchema.nullable(),
    lastSuccessfulSyncAt: nullableInstantSchema,
    version: nonNegativeIntegerSchema,
    createdAt: instantSchema,
    updatedAt: instantSchema,
  })
  .superRefine((value, context) => {
    const accountShape = value.accountType !== null && value.repositoryName === null
    const repositoryShape = value.accountType === null && value.repositoryName !== null
    if (
      (value.sourceKind === 'ACCOUNT' && !accountShape) ||
      (value.sourceKind === 'REPOSITORY' && !repositoryShape)
    ) {
      context.addIssue({
        code: 'custom',
        path: ['sourceKind'],
        message: 'GitHub source kind와 account/repository 필드가 일치하지 않습니다.',
      })
    }
  })

export const gitHubSourceDetailSchema = z
  .object({
    source: gitHubSourceSummarySchema,
    requiredUserAction: requiredUserActionSchema.nullable(),
  })
  .superRefine((value, context) => {
    const action = value.requiredUserAction
    if (value.source.status === 'WAITING_USER') {
      if (
        action === null ||
        action.type !== 'SELECT_GITHUB_REPOSITORIES' ||
        action.route !== '/profile/github' ||
        action.resource?.resourceType !== 'GITHUB_SOURCE' ||
        action.resource.resourceId !== value.source.id
      ) {
        context.addIssue({
          code: 'custom',
          path: ['requiredUserAction'],
          message: '저장소 선택 action이 source와 일치하지 않습니다.',
        })
      }
    } else if (action !== null) {
      context.addIssue({
        code: 'custom',
        path: ['requiredUserAction'],
        message: 'WAITING_USER가 아닌 source에는 사용자 action이 없어야 합니다.',
      })
    }
  })

export const gitHubRunAcceptedSchema = runAcceptedSchema.superRefine((value, context) => {
  if (value.resourceType !== 'GITHUB_SOURCE' || value.resourceId === null) {
    context.addIssue({
      code: 'custom',
      path: ['resourceType'],
      message: 'GitHub Run resource가 올바르지 않습니다.',
    })
  }
})

export const gitHubRefreshResultSchema = z
  .object({
    changed: z.boolean(),
    source: gitHubSourceDetailSchema,
    run: gitHubRunAcceptedSchema.nullable(),
  })
  .superRefine((value, context) => {
    if (value.changed === (value.run === null)) {
      context.addIssue({
        code: 'custom',
        path: ['run'],
        message: 'refresh changed 값과 Run 존재 여부가 일치하지 않습니다.',
      })
      return
    }
    if (value.run !== null && value.run.resourceId !== value.source.source.id) {
      context.addIssue({
        code: 'custom',
        path: ['run', 'resourceId'],
        message: 'refresh Run이 요청한 GitHub source를 가리키지 않습니다.',
      })
    }
  })

export const gitHubSourcePageSchema = z.object({
  items: z.array(gitHubSourceSummarySchema),
  page: nonNegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonNegativeIntegerSchema,
  totalPages: nonNegativeIntegerSchema,
})

export const gitHubRepositoryPageSchema = z.object({
  items: z.array(gitHubRepositorySchema),
  page: nonNegativeIntegerSchema,
  size: z.number().int().min(1).max(100),
  totalElements: nonNegativeIntegerSchema,
  totalPages: nonNegativeIntegerSchema,
})

export const createGitHubSourceRequestSchema = z.object({
  url: z.string().min(1).max(500),
  participationConfirmed: z.literal(true),
})

export const gitHubRepositorySelectionRequestSchema = z.object({
  repositoryIds: z
    .array(uuidSchema)
    .min(1)
    .max(10)
    .refine((ids) => new Set(ids).size === ids.length, {
      message: 'repository ID는 중복될 수 없습니다.',
    }),
  version: nonNegativeIntegerSchema,
})

export const gitHubRefreshRequestSchema = z.object({
  version: nonNegativeIntegerSchema,
})

export type GitHubRepositoryDto = z.infer<typeof gitHubRepositorySchema>
export type GitHubSourceSummaryDto = z.infer<typeof gitHubSourceSummarySchema>
export type GitHubSourceDetailDto = z.infer<typeof gitHubSourceDetailSchema>
export type GitHubRefreshResultDto = z.infer<typeof gitHubRefreshResultSchema>
export type GitHubSourcePageDto = z.infer<typeof gitHubSourcePageSchema>
export type GitHubRepositoryPageDto = z.infer<typeof gitHubRepositoryPageSchema>
export type CreateGitHubSourceRequest = z.infer<typeof createGitHubSourceRequestSchema>
export type GitHubRepositorySelectionRequest = z.infer<
  typeof gitHubRepositorySelectionRequestSchema
>
export type GitHubRefreshRequest = z.infer<typeof gitHubRefreshRequestSchema>
