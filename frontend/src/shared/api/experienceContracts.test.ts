import { describe, expect, it } from 'vitest'

import {
  EVIDENCE_SOURCE_TYPES,
  experienceDetailSchema,
  experiencePageSchema,
} from './experienceContracts'

describe('experience GitHub provenance contracts', () => {
  it('includes every evidence source type and rejects unknown values', () => {
    expect(EVIDENCE_SOURCE_TYPES).toContain('GITHUB_REPOSITORY')
    for (const sourceType of EVIDENCE_SOURCE_TYPES) {
      const value = sourceType === 'GITHUB_REPOSITORY' ? gitHubSource() : documentSource(sourceType)
      expect(experienceDetailSchema.safeParse({ item: item(), sources: [value] }).success).toBe(
        true,
      )
    }
    expect(
      experienceDetailSchema.safeParse({
        item: item(),
        sources: [{ ...documentSource('DOCUMENT_CHUNK'), sourceType: 'RAW_SOURCE' }],
      }).success,
    ).toBe(false)
  })

  it('parses the additive GitHub fields and sanitized excerpt', () => {
    const detail = experienceDetailSchema.parse({ item: item(), sources: [gitHubSource()] })
    expect(detail.item.githubRepositorySourceCount).toBe(1)
    expect(detail.sources[0]).toMatchObject({
      githubSourceId: uuid(3),
      githubRepositoryId: uuid(4),
      repositoryName: 'openai/hiresemble',
      commitShaShort: 'abcdef123456',
      sourceExcerpt: '검증된 공개 설명',
    })
  })

  it('permits a deleted-source tombstone but rejects malformed live provenance', () => {
    expect(
      experienceDetailSchema.safeParse({
        item: item(),
        sources: [
          {
            ...gitHubSource(),
            githubSourceId: null,
            githubRepositoryId: null,
            repositoryName: null,
            repositoryUrl: null,
            commitShaShort: null,
            capturedAt: null,
            sourceDeletedAt: now,
          },
        ],
      }).success,
    ).toBe(true)
    expect(
      experienceDetailSchema.safeParse({
        item: item(),
        sources: [{ ...gitHubSource(), commitShaShort: null }],
      }).success,
    ).toBe(false)
  })

  it('parses pagination and rejects missing additive fields', () => {
    expect(
      experiencePageSchema.parse({
        items: [item()],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
      }).items,
    ).toHaveLength(1)
    const malformed = { ...item() }
    delete (malformed as Partial<ReturnType<typeof item>>).githubRepositorySourceCount
    expect(
      experiencePageSchema.safeParse({
        items: [malformed],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
      }).success,
    ).toBe(false)
  })
})

const now = '2026-08-08T00:00:00Z'

function item() {
  return {
    id: uuid(1),
    evidenceCategory: 'PROJECT',
    title: '공개 프로젝트 개선',
    content: '공개 저장소의 처리 흐름을 개선했습니다.',
    verificationStatus: 'PENDING',
    matchKind: 'NEW',
    matchedExperienceItemId: null,
    matchSimilarity: null,
    reviewRequired: false,
    sourceCount: 1,
    documentSourceCount: 0,
    githubRepositorySourceCount: 1,
    primaryDocumentName: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

function gitHubSource() {
  return {
    evidenceId: uuid(2),
    sourceType: 'GITHUB_REPOSITORY',
    documentId: null,
    verificationStatus: 'PENDING',
    relationKind: 'PRIMARY_SOURCE',
    similarity: null,
    githubSourceId: uuid(3),
    githubRepositoryId: uuid(4),
    repositoryName: 'openai/hiresemble',
    repositoryUrl: 'https://github.com/openai/hiresemble',
    commitShaShort: 'abcdef123456',
    capturedAt: now,
    sourceExcerpt: '검증된 공개 설명',
    sourceDeletedAt: null,
    createdAt: now,
  }
}

function documentSource(sourceType: (typeof EVIDENCE_SOURCE_TYPES)[number]) {
  return {
    evidenceId: uuid(2),
    sourceType,
    documentId: sourceType === 'DOCUMENT_CHUNK' ? uuid(5) : null,
    verificationStatus: 'PENDING',
    relationKind: 'PRIMARY_SOURCE',
    similarity: null,
    githubSourceId: null,
    githubRepositoryId: null,
    repositoryName: null,
    repositoryUrl: null,
    commitShaShort: null,
    capturedAt: null,
    sourceExcerpt: null,
    sourceDeletedAt: null,
    createdAt: now,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
