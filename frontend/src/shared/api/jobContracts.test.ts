import { describe, expect, it } from 'vitest'

import {
  CLOSED_REASONS,
  DEADLINE_SOURCES,
  ELIGIBILITIES,
  FIT_CRITERION_CATEGORIES,
  JOB_DESCRIPTION_SOURCES,
  JOB_EXTRACTION_STATUSES,
  MATCH_LEVELS,
  OUTDATED_REASONS,
  JOB_STATUSES,
  jobCreationAcceptedSchema,
  jobAnalysisDetailSchema,
  jobDetailSchema,
  jobPageSchema,
} from './jobContracts'

describe('P6 Job contracts', () => {
  it('keeps the canonical Job and analysis enums', () => {
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
    expect(ELIGIBILITIES).toEqual(['ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE', 'UNKNOWN'])
    expect(OUTDATED_REASONS).toEqual(['JOB_CONTENT_CHANGED', 'PROFILE_CHANGED', 'EVIDENCE_CHANGED'])
    expect(FIT_CRITERION_CATEGORIES).toEqual([
      'REQUIRED_QUALIFICATION',
      'CORE_RESPONSIBILITY_OR_SKILL',
      'PREFERRED_QUALIFICATION',
      'RELATED_EXPERIENCE_OR_DOMAIN',
      'EDUCATION_CERTIFICATION_LANGUAGE',
    ])
    expect(MATCH_LEVELS).toEqual(['MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN'])
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

  it('accepts P6 analysis, P7 cover-letter, and P8 interview projections', () => {
    const value = jobDetailSchema.parse(detail())
    expect(value.latestAnalysis).toBeNull()
    expect(value.coverLetterId).toBeNull()
    expect(value.latestQuestionSetId).toBeNull()
    expect(value.latestMockSessionId).toBeNull()
    expect(value).not.toHaveProperty('latestAgentRunId')

    expect(jobDetailSchema.safeParse({ ...detail(), extractionStatus: 'DONE' }).success).toBe(false)
    expect(
      jobDetailSchema.safeParse({
        ...detail(),
        latestFitScore: 75.25,
        analysisOutdated: true,
        outdatedReasons: ['PROFILE_CHANGED'],
        latestAnalysis: analysisSummary(),
      }).success,
    ).toBe(true)
    expect(
      jobDetailSchema.safeParse({
        ...detail(),
        coverLetterStatus: 'DRAFT',
        coverLetterId: uuid(20),
      }).success,
    ).toBe(true)
    expect(
      jobDetailSchema.safeParse({
        ...detail(),
        interviewPreparationCount: 1,
        latestQuestionSetId: uuid(21),
      }).success,
    ).toBe(true)
    expect(jobDetailSchema.safeParse({ ...detail(), latestAnalysis: {} }).success).toBe(false)
    for (const override of [
      { latestFitScore: 101 },
      { outdatedReasons: ['NOT_A_REASON'] },
      { coverLetterStatus: 'DELETED' },
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

  it('keeps historical analysis readable across canonical current evidence states', () => {
    const value = jobAnalysisDetailSchema.parse(analysisDetail())
    expect(value.eligibility).toBe('INELIGIBLE')
    expect(value.fitScore).toBe(82.5)
    expect(value.scoreBreakdown[0]?.score).toBe(20)
    expect(value.matchedEvidenceRefs[0]?.verificationStatus).toBe('VERIFIED')

    for (const currentState of [
      { verificationStatus: 'PENDING', sourceDeleted: false },
      { verificationStatus: 'REJECTED', sourceDeleted: false },
      { verificationStatus: 'SOURCE_DELETED', sourceDeleted: true },
    ] as const) {
      const historicalAnalysis = jobAnalysisDetailSchema.safeParse({
        ...analysisDetail(),
        analysisOutdated: true,
        outdatedReasons: ['EVIDENCE_CHANGED'],
        matchedEvidenceRefs: [{ ...evidence(), ...currentState }],
        scoreBreakdown: [
          {
            ...analysisDetail().scoreBreakdown[0],
            evidenceRefs: [{ ...evidence(), ...currentState }],
          },
        ],
      })
      expect(historicalAnalysis.success).toBe(true)
      if (historicalAnalysis.success) {
        expect(historicalAnalysis.data.fitScore).toBe(82.5)
      }
    }

    expect(
      jobAnalysisDetailSchema.safeParse({
        ...analysisDetail(),
        matchedEvidenceRefs: [
          { ...evidence(), verificationStatus: 'NOT_A_STATUS', sourceDeleted: false },
        ],
      }).success,
    ).toBe(false)
  })

  it('rejects a criterion score that exceeds its stored weight', () => {
    expect(
      jobAnalysisDetailSchema.safeParse({
        ...analysisDetail(),
        scoreBreakdown: [{ ...analysisDetail().scoreBreakdown[0], score: 41, weight: 40 }],
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

function analysisSummary() {
  return {
    id: uuid(10),
    analysisVersion: 1,
    eligibility: 'INELIGIBLE',
    fitScore: 82.5,
    analysisOutdated: false,
    outdatedReasons: [],
    createdAt: now,
    agentRunId: uuid(11),
  }
}

function analysisDetail() {
  return {
    ...analysisSummary(),
    scoreBreakdown: [
      {
        category: 'REQUIRED_QUALIFICATION',
        criterion: 'Java 경력',
        weight: 40,
        matchLevel: 'PARTIAL',
        score: 20,
        evidenceRefs: [evidence()],
        explanation: '승인된 경력 정보에서 일부 일치를 확인했어요.',
      },
    ],
    requiredQualifications: [
      {
        category: 'REQUIRED_QUALIFICATION',
        text: 'Java 3년 이상',
        required: true,
        sourceLocation: '지원 자격',
      },
    ],
    preferredQualifications: [],
    responsibilities: [
      {
        category: 'CORE_RESPONSIBILITY_OR_SKILL',
        text: '백엔드 서비스 개발',
        required: false,
        sourceLocation: '주요 업무',
      },
    ],
    strengths: ['Spring 서비스 경험'],
    gaps: ['경력 기간 확인 필요'],
    matchedEvidenceRefs: [evidence()],
    analysisSummary: '필수 경력은 확인이 더 필요하지만 기술 일치도는 높아요.',
  }
}

function evidence() {
  return {
    id: uuid(12),
    title: '결제 API 개발',
    evidenceCategory: 'CAREER_PROJECT',
    verificationStatus: 'VERIFIED',
    sourceType: 'CAREER',
    sourceDeleted: false,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
