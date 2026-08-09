import { z } from 'zod'

export const GITHUB_APP_CONNECTION_STATUSES = [
  'ACTIVE',
  'SUSPENDED',
  'DISCONNECTING',
  'DISCONNECTED',
  'REVOKED',
] as const
export const GITHUB_REPOSITORY_SELECTIONS = ['ALL', 'SELECTED'] as const

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })

export const githubAppCapabilitySchema = z
  .object({
    enabled: z.boolean(),
    configured: z.boolean(),
    requiredPermissions: z
      .array(z.enum(['metadata:read', 'contents:read']))
      .length(2)
      .refine((items) => new Set(items).size === 2),
  })
  .strict()

export const githubInstallationRequestSchema = z
  .object({
    installationUrl: z.url().max(2048),
    expiresAt: instantSchema,
  })
  .strict()

export const githubAppConnectionSchema = z
  .object({
    id: uuidSchema,
    targetAccountLogin: z.string().min(1).max(100),
    targetAccountType: z.enum(['USER', 'ORGANIZATION']),
    repositorySelection: z.enum(GITHUB_REPOSITORY_SELECTIONS),
    status: z.enum(GITHUB_APP_CONNECTION_STATUSES),
    manageUrl: z.url().max(2048),
    version: z.number().int().nonnegative(),
    connectedAt: instantSchema,
    verifiedAt: instantSchema,
    lastCheckedAt: instantSchema,
    disconnectedAt: instantSchema.nullable(),
  })
  .strict()

export const githubAppConnectionListSchema = z
  .object({ items: z.array(githubAppConnectionSchema) })
  .strict()

export const githubAppRefreshRequestSchema = z
  .object({ version: z.number().int().nonnegative() })
  .strict()

export type GitHubAppCapabilityDto = z.infer<typeof githubAppCapabilitySchema>
export type GitHubInstallationRequestDto = z.infer<typeof githubInstallationRequestSchema>
export type GitHubAppConnectionDto = z.infer<typeof githubAppConnectionSchema>
export type GitHubAppConnectionListDto = z.infer<typeof githubAppConnectionListSchema>
export type GitHubAppRefreshRequest = z.infer<typeof githubAppRefreshRequestSchema>
export type GitHubAppConnectionStatus = (typeof GITHUB_APP_CONNECTION_STATUSES)[number]
