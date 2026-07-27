import { describe, expect, it } from 'vitest'

import {
  CLOSED_REASONS,
  DEADLINE_SOURCES,
  JOB_DESCRIPTION_SOURCES,
  JOB_EXTRACTION_STATUSES,
  JOB_STATUSES,
  jobCreationAcceptedSchema,
  jobDetailSchema,
  jobPageSchema,
} from './jobContracts'

describe('P5 Job contracts', () => {
  it('keeps the canonical P5 status axes and source enums', () => {
    expect(JOB_STATUSES).toEqual(['IN_PROGRESS', 'SUBMITTED', 'CLOSED'])
    expect(JOB_EXTRACTION_STATUSES).toEqual([
      'QUEUED',
      'EXTRACTING',
      'EXTRACTED',
      'MANUAL_INPUT_PROVIDED',
      'NEEDS_MANUAL_INPUT',
      'FAILED',
    ])
    expect(DEADLINE_SOURCES).toEqual(['USER_ENTERED', 'AUTO_EXTRACTED', 'UNKNOWN'])
    expect(CLOSED_REASONS).toEqual(['DEADLINE_PASSED', 'USER_CLOSED', 'URL_INACTIVE'])
    expect(JOB_DESCRIPTION_SOURCES).toEqual(['AUTO_EXTRACTED', 'USER_ENTERED'])
  })

  it('requires the exact create branch relationship between extraction state and run ID', () => {
    expect(
      jobCreationAcceptedSchema.safeParse({
        jobId: uuid(1),
        status: 'IN_PROGRESS',
        extractionStatus: 'QUEUED',
        agentRunId: uuid(2),
      }).success,
    ).toBe(true)
    expect(
      jobCreationAcceptedSchema.safeParse({
        jobId: uuid(1),
        status: 'IN_PROGRESS',
        extractionStatus: 'QUEUED',
        agentRunId: null,
      }).success,
    ).toBe(false)
    expect(
      jobCreationAcceptedSchema.safeParse({
        jobId: uuid(1),
        status: 'IN_PROGRESS',
        extractionStatus: 'MANUAL_INPUT_PROVIDED',
        agentRunId: uuid(2),
      }).success,
    ).toBe(false)
  })

  it('requires the P5-only null projections and rejects premature P6 payloads', () => {
    const value = jobDetailSchema.parse(detail())
    expect(value.latestAnalysis).toBeNull()
    expect(value.coverLetterId).toBeNull()
    expect(value.latestQuestionSetId).toBeNull()
    expect(value.latestMockSessionId).toBeNull()
    expect(value).not.toHaveProperty('latestAgentRunId')

    expect(jobDetailSchema.safeParse({ ...detail(), extractionStatus: 'DONE' }).success).toBe(false)
    expect(jobDetailSchema.safeParse({ ...detail(), latestAnalysis: {} }).success).toBe(false)
    for (const override of [
      { latestFitScore: 75 },
      { analysisOutdated: true },
      { outdatedReasons: ['JOB_CONTENT_CHANGED'] },
      { coverLetterStatus: 'DRAFT' },
      { interviewPreparationCount: 1 },
    ]) {
      expect(
        jobPageSchema.safeParse({
          items: [{ ...detail(), ...override }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }).success,
      ).toBe(false)
    }
    expect(
      jobDetailSchema.safeParse({ ...detail(), sourceUrl: 'file:///private/job.html' }).success,
    ).toBe(false)
    expect(
      jobPageSchema.safeParse({
        items: [{ ...detail(), companyName: 'x'.repeat(201) }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }).success,
    ).toBe(false)
  })
})

const now = '2026-07-27T00:00:00Z'

function detail() {
  return {
    id: uuid(1),
    companyName: 'Hiresemble',
    title: 'Backend Engineer',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS',
    extractionStatus: 'EXTRACTED',
    submittedAt: null,
    deadlineAt: null,
    deadlineSource: 'UNKNOWN',
    latestFitScore: null,
    analysisOutdated: false,
    outdatedReasons: [],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 1,
    createdAt: now,
    updatedAt: now,
    sourceUrl: 'https://jobs.example.com/openings/1',
    canonicalUrl: 'https://jobs.example.com/openings/1',
    roleCategory: null,
    employmentType: null,
    location: null,
    descriptionText: '공고 본문',
    descriptionSource: 'AUTO_EXTRACTED',
    extractionError: null,
    closedAt: null,
    closedReason: null,
    latestAnalysis: null,
    coverLetterId: null,
    latestQuestionSetId: null,
    latestMockSessionId: null,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
