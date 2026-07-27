import type { JobDetailDto, JobSummaryDto } from '@/shared/api/jobContracts'

export const JOB_ID = '50000000-0000-4000-8000-000000000001'
export const JOB_RUN_ID = '50000000-0000-4000-8000-000000000002'
export const JOB_NOW = '2026-07-27T00:00:00Z'

export function jobSummaryFixture(overrides: Partial<JobSummaryDto> = {}): JobSummaryDto {
  return {
    id: JOB_ID,
    companyName: 'Hiresemble',
    title: 'Backend Engineer',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS',
    extractionStatus: 'EXTRACTED',
    submittedAt: null,
    deadlineAt: '2026-08-01T09:00:00Z',
    deadlineSource: 'AUTO_EXTRACTED',
    latestFitScore: null,
    analysisOutdated: false,
    outdatedReasons: [],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 2,
    createdAt: JOB_NOW,
    updatedAt: JOB_NOW,
    ...overrides,
  }
}

export function jobDetailFixture(overrides: Partial<JobDetailDto> = {}): JobDetailDto {
  return {
    ...jobSummaryFixture(),
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
    ...overrides,
  }
}
