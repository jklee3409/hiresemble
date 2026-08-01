import { describe, expect, it } from 'vitest'

import {
  coverLetterDetailFixture,
  coverLetterSummaryFixture,
  tipTapDocument,
} from '@/features/cover-letters/testFixtures'

import {
  coverLetterDetailSchema,
  coverLetterSummarySchema,
  tipTapDocumentSchema,
} from './coverLetterContracts'
import { jobDetailSchema } from './jobContracts'

describe('P7 cover letter public contracts', () => {
  it('parses the exact summary/detail field set and all canonical states', () => {
    for (const status of ['DRAFT', 'FINALIZED', 'ARCHIVED'] as const) {
      expect(coverLetterSummarySchema.parse(coverLetterSummaryFixture({ status })).status).toBe(
        status,
      )
    }

    const detail = coverLetterDetailSchema.parse(coverLetterDetailFixture())
    expect(detail.questions[0]?.currentAnswer?.sourceType).toBe('USER_EDITED')
    expect(detail.questions[0]?.latestVerification?.status).toBe('WARNING')
    expect(detail.questions[0]?.latestVerification?.issues[0]?.code).toBe('UNVERIFIED_CLAIM')
  })

  it('accepts the P8 Job question-set projections while keeping P9 nullable', () => {
    const base = {
      id: '00000000-0000-4000-8000-000000000001',
      companyName: 'Hiresemble',
      title: 'Frontend Engineer',
      positionName: 'Frontend Engineer',
      status: 'IN_PROGRESS',
      extractionStatus: 'EXTRACTED',
      submittedAt: null,
      deadlineAt: null,
      deadlineSource: 'UNKNOWN',
      latestFitScore: 80,
      analysisOutdated: false,
      outdatedReasons: [],
      coverLetterStatus: 'DRAFT',
      interviewPreparationCount: 0,
      version: 1,
      createdAt: '2026-07-30T00:00:00Z',
      updatedAt: '2026-07-30T00:00:00Z',
      sourceUrl: 'https://jobs.example.com/1',
      canonicalUrl: 'https://jobs.example.com/1',
      roleCategory: null,
      employmentType: null,
      location: null,
      descriptionText: '공고 본문',
      descriptionSource: 'AUTO_EXTRACTED',
      extractionError: null,
      automaticAnalysis: {
        state: 'LAUNCHED',
        qualityMode: 'BALANCED',
        agentRunId: '00000000-0000-4000-8000-000000000009',
        error: null,
      },
      closedAt: null,
      closedReason: null,
      latestAnalysis: null,
      coverLetterId: '00000000-0000-4000-8000-000000000002',
      latestQuestionSetId: null,
      latestMockSessionId: null,
    }
    expect(jobDetailSchema.parse(base).coverLetterStatus).toBe('DRAFT')
    expect(
      jobDetailSchema.safeParse({
        ...base,
        interviewPreparationCount: 1,
        latestQuestionSetId: '00000000-0000-4000-8000-000000000003',
      }).success,
    ).toBe(true)
  })

  it('allows only the public TipTap node and mark allowlist', () => {
    expect(tipTapDocumentSchema.safeParse(tipTapDocument()).success).toBe(true)
    expect(
      tipTapDocumentSchema.safeParse({
        type: 'doc',
        content: [{ type: 'image', text: null, marks: [], content: [] }],
      }).success,
    ).toBe(false)
    expect(
      tipTapDocumentSchema.safeParse({
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            text: null,
            marks: [],
            content: [
              {
                type: 'text',
                text: '링크',
                marks: [{ type: 'link' }],
                content: [],
              },
            ],
          },
        ],
      }).success,
    ).toBe(false)
  })

  it('rejects missing, malformed and non-canonical enum fields', () => {
    expect(
      coverLetterDetailSchema.safeParse({
        ...coverLetterDetailFixture(),
        status: 'DELETED',
      }).success,
    ).toBe(false)
    expect(
      coverLetterDetailSchema.safeParse({
        ...coverLetterDetailFixture(),
        questions: [{ id: 'not-a-uuid' }],
      }).success,
    ).toBe(false)
  })

  it('enforces the active verification suggestion count and length boundaries', () => {
    const detail = coverLetterDetailFixture()
    const verification = detail.questions[0]!.latestVerification!
    expect(
      coverLetterDetailSchema.safeParse({
        ...detail,
        questions: [
          {
            ...detail.questions[0],
            latestVerification: {
              ...verification,
              suggestions: Array.from({ length: 20 }, () => 'x'.repeat(1_000)),
            },
          },
        ],
      }).success,
    ).toBe(true)
    expect(
      coverLetterDetailSchema.safeParse({
        ...detail,
        questions: [
          {
            ...detail.questions[0],
            latestVerification: {
              ...verification,
              suggestions: Array.from({ length: 21 }, () => '수정 제안'),
            },
          },
        ],
      }).success,
    ).toBe(false)
    expect(
      coverLetterDetailSchema.safeParse({
        ...detail,
        questions: [
          {
            ...detail.questions[0],
            latestVerification: {
              ...verification,
              suggestions: ['x'.repeat(1_001)],
            },
          },
        ],
      }).success,
    ).toBe(false)
  })
})
