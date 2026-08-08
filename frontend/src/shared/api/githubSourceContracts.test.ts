import { describe, expect, it } from 'vitest'

import {
  GITHUB_ACCOUNT_TYPES,
  GITHUB_SOURCE_KINDS,
  GITHUB_SOURCE_STATUSES,
  gitHubRefreshResultSchema,
  gitHubRepositoryPageSchema,
  gitHubRepositorySelectionRequestSchema,
  gitHubSourceDetailSchema,
  gitHubSourcePageSchema,
} from './githubSourceContracts'

describe('GitHub Source contracts', () => {
  it('parses every known source, kind, and account enum and rejects unknown values', () => {
    for (const status of GITHUB_SOURCE_STATUSES) {
      expect(gitHubSourcePageSchema.safeParse(page([source({ status })])).success).toBe(true)
    }
    for (const sourceKind of GITHUB_SOURCE_KINDS) {
      const value =
        sourceKind === 'ACCOUNT'
          ? source({ sourceKind, accountType: 'USER', repositoryName: null })
          : source({ sourceKind, accountType: null, repositoryName: 'hiresemble' })
      expect(gitHubSourcePageSchema.safeParse(page([value])).success).toBe(true)
    }
    for (const accountType of GITHUB_ACCOUNT_TYPES) {
      expect(gitHubSourcePageSchema.safeParse(page([source({ accountType })])).success).toBe(true)
    }
    expect(gitHubSourcePageSchema.safeParse(page([source({ status: 'NEW_STATUS' })])).success).toBe(
      false,
    )
    expect(
      gitHubSourcePageSchema.safeParse(page([source({ sourceKind: 'PRIVATE' })])).success,
    ).toBe(false)
  })

  it('enforces summary/detail nullability and the repository page shape', () => {
    expect(
      gitHubSourcePageSchema.safeParse(
        page([source({ sourceKind: 'ACCOUNT', accountType: null, repositoryName: null })]),
      ).success,
    ).toBe(false)
    expect(
      gitHubRepositoryPageSchema.parse(
        page([
          {
            id: uuid(3),
            ownerLogin: 'openai',
            repositoryName: 'hiresemble',
            canonicalUrl: 'https://github.com/openai/hiresemble',
            description: null,
            defaultBranch: 'main',
            fork: false,
            archived: false,
            selected: true,
            pushedAt: null,
          },
        ]),
      ).items[0]?.pushedAt,
    ).toBeNull()
  })

  it('requires the exact repository selection action only while waiting', () => {
    const waiting = detail(source({ status: 'WAITING_USER' }))
    expect(gitHubSourceDetailSchema.parse(waiting).requiredUserAction?.type).toBe(
      'SELECT_GITHUB_REPOSITORIES',
    )
    expect(
      gitHubSourceDetailSchema.safeParse({
        ...waiting,
        requiredUserAction: { ...waiting.requiredUserAction, type: 'PROVIDE_DOCUMENT_TEXT' },
      }).success,
    ).toBe(false)
    expect(
      gitHubSourceDetailSchema.safeParse({
        source: source({ status: 'READY' }),
        requiredUserAction: waiting.requiredUserAction,
      }).success,
    ).toBe(false)
  })

  it('enforces refresh changed/run and Run resource parity invariants', () => {
    const unchanged = {
      changed: false,
      source: detail(source({ status: 'READY' }), false),
      run: null,
    }
    expect(gitHubRefreshResultSchema.parse(unchanged).run).toBeNull()
    expect(
      gitHubRefreshResultSchema.parse({
        changed: true,
        source: detail(source({ status: 'QUEUED' }), false),
        run: accepted(),
      }).run?.resourceType,
    ).toBe('GITHUB_SOURCE')
    expect(gitHubRefreshResultSchema.safeParse({ ...unchanged, changed: true }).success).toBe(false)
    expect(
      gitHubRefreshResultSchema.safeParse({
        ...unchanged,
        run: { ...accepted(), resourceType: 'DOCUMENT' },
      }).success,
    ).toBe(false)
    expect(
      gitHubRefreshResultSchema.safeParse({
        changed: true,
        source: detail(source({ status: 'QUEUED' }), false),
        run: { ...accepted(), resourceId: uuid(99) },
      }).success,
    ).toBe(false)
  })

  it('rejects duplicate, empty, and oversized repository selections', () => {
    expect(
      gitHubRepositorySelectionRequestSchema.safeParse({ repositoryIds: [], version: 1 }).success,
    ).toBe(false)
    expect(
      gitHubRepositorySelectionRequestSchema.safeParse({
        repositoryIds: [uuid(1), uuid(1)],
        version: 1,
      }).success,
    ).toBe(false)
    expect(
      gitHubRepositorySelectionRequestSchema.safeParse({
        repositoryIds: Array.from({ length: 11 }, (_, index) => uuid(index + 1)),
        version: 1,
      }).success,
    ).toBe(false)
  })

  it('rejects malformed pages without requiring private provider fields', () => {
    expect(gitHubSourcePageSchema.safeParse({ items: [] }).success).toBe(false)
    expect(
      gitHubSourcePageSchema.parse(page([{ ...source(), providerPayload: 'ignored' }])).items,
    ).toHaveLength(1)
  })
})

const now = '2026-08-08T00:00:00Z'

function source(overrides: Record<string, unknown> = {}) {
  return {
    id: uuid(1),
    sourceKind: 'ACCOUNT',
    accountType: 'USER',
    canonicalUrl: 'https://github.com/openai',
    ownerLogin: 'openai',
    repositoryName: null,
    status: 'DISCOVERING',
    discoveredRepositoryCount: 2,
    selectedRepositoryCount: 0,
    repositoryDiscoveryTruncated: false,
    newExperienceCount: 0,
    corroboratedExperienceCount: 0,
    reviewRequiredCount: 0,
    rejectedCandidateCount: 0,
    snapshotIncomplete: false,
    latestAgentRunId: uuid(2),
    lastSuccessfulSyncAt: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  }
}

function detail(value = source({ status: 'WAITING_USER' }), withAction = true) {
  return {
    source: value,
    requiredUserAction: withAction
      ? {
          type: 'SELECT_GITHUB_REPOSITORIES',
          resource: {
            resourceType: 'GITHUB_SOURCE',
            resourceId: value.id,
            displayLabel: value.canonicalUrl,
          },
          route: '/profile/github',
          message: '분석할 공개 저장소를 선택해 주세요.',
        }
      : null,
  }
}

function accepted() {
  return {
    agentRunId: uuid(2),
    status: 'QUEUED',
    resourceType: 'GITHUB_SOURCE',
    resourceId: uuid(1),
    replayed: false,
  }
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
