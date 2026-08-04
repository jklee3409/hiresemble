import type {
  JobAnalysisDetailDto,
  JobAnalysisSummaryDto,
  JobDetailDto,
  JobSummaryDto,
} from '@/shared/api/jobContracts'

export const JOB_ID = '50000000-0000-4000-8000-000000000001'
export const JOB_RUN_ID = '50000000-0000-4000-8000-000000000002'
export const JOB_ANALYSIS_ID = '50000000-0000-4000-8000-000000000003'
export const JOB_ANALYSIS_RUN_ID = '50000000-0000-4000-8000-000000000004'
export const JOB_EVIDENCE_ID = '50000000-0000-4000-8000-000000000005'
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
    automaticAnalysis: {
      state: 'LAUNCHED',
      qualityMode: 'BALANCED',
      agentRunId: JOB_ANALYSIS_RUN_ID,
      error: null,
    },
    closedAt: null,
    closedReason: null,
    latestAnalysis: null,
    coverLetterId: null,
    latestQuestionSetId: null,
    latestMockSessionId: null,
    ...overrides,
  }
}

export function jobAnalysisSummaryFixture(
  overrides: Partial<JobAnalysisSummaryDto> = {},
): JobAnalysisSummaryDto {
  return {
    id: JOB_ANALYSIS_ID,
    analysisVersion: 2,
    eligibility: 'INELIGIBLE',
    fitScore: 82.5,
    analysisCoverage: 100,
    analysisOutdated: false,
    outdatedReasons: [],
    createdAt: JOB_NOW,
    agentRunId: JOB_ANALYSIS_RUN_ID,
    ...overrides,
  }
}

export function jobAnalysisDetailFixture(
  overrides: Partial<JobAnalysisDetailDto> = {},
): JobAnalysisDetailDto {
  const evidence = {
    id: JOB_EVIDENCE_ID,
    title: '결제 API 개선 프로젝트',
    evidenceCategory: 'CAREER_PROJECT',
    verificationStatus: 'VERIFIED' as const,
    sourceType: 'CAREER' as const,
    sourceDeleted: false,
  }
  return {
    ...jobAnalysisSummaryFixture(),
    scoreBreakdown: [
      {
        category: 'REQUIRED_QUALIFICATION',
        criterion: 'Java 개발 경력 3년',
        weight: 40,
        matchLevel: 'PARTIAL',
        score: 20,
        evidenceRefs: [evidence],
        explanation: '승인된 경력에서 Java 서비스 개발 경험을 확인했지만 기간은 일부 부족해요.',
      },
      {
        category: 'CORE_RESPONSIBILITY_OR_SKILL',
        criterion: 'Spring 기반 API 개발',
        weight: 30,
        matchLevel: 'MATCHED',
        score: 30,
        evidenceRefs: [evidence],
        explanation: '승인된 프로젝트에서 Spring API 개발 경험이 확인돼요.',
      },
    ],
    requiredQualifications: [
      {
        category: 'REQUIRED_QUALIFICATION',
        text: 'Java 개발 경력 3년 이상',
        required: true,
        sourceLocation: '지원 자격',
      },
    ],
    preferredQualifications: [
      {
        category: 'PREFERRED_QUALIFICATION',
        text: '대규모 트래픽 경험',
        required: false,
        sourceLocation: '우대 사항',
      },
    ],
    responsibilities: [
      {
        category: 'CORE_RESPONSIBILITY_OR_SKILL',
        text: 'Spring 기반 백엔드 API 개발',
        required: false,
        sourceLocation: '주요 업무',
      },
    ],
    strengths: ['Spring API 개발 경험이 요구사항과 일치해요.'],
    gaps: ['필수 경력 기간은 추가 확인이 필요해요.'],
    matchedEvidenceRefs: [evidence],
    analysisSummary: '필수 경력 기간은 부족하지만 핵심 기술 경험은 높은 일치를 보여요.',
    ...overrides,
  }
}
