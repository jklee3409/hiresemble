import type {
  InterviewAnswerVersionDto,
  InterviewFeedbackDto,
  InterviewQuestionDto,
  QuestionSetDetailDto,
  QuestionSetSummaryDto,
  ResearchRunDto,
  ResearchSourceDto,
} from '@/shared/api/interviewContracts'

export const INTERVIEW_JOB_ID = uuid(1)
export const INTERVIEW_COVER_LETTER_ID = uuid(2)
export const INTERVIEW_QUESTION_SET_ID = uuid(3)
export const INTERVIEW_RESEARCH_RUN_ID = uuid(4)
export const INTERVIEW_AGENT_RUN_ID = uuid(5)
export const INTERVIEW_QUESTION_ID = uuid(6)
export const INTERVIEW_ANSWER_ID = uuid(7)
export const INTERVIEW_FEEDBACK_ID = uuid(8)
export const INTERVIEW_FEEDBACK_RUN_ID = uuid(9)
export const INTERVIEW_SOURCE_ID = uuid(10)
export const INTERVIEW_EVIDENCE_ID = uuid(11)

const NOW = '2026-07-31T01:00:00Z'

export function answerFixture(
  overrides: Partial<InterviewAnswerVersionDto> = {},
): InterviewAnswerVersionDto {
  return {
    id: INTERVIEW_ANSWER_ID,
    questionId: INTERVIEW_QUESTION_ID,
    parentVersionId: null,
    versionNo: 1,
    content: '상황과 역할, 행동, 결과 순서로 작성한 답변입니다.',
    sourceType: 'USER_EDITED',
    isCurrent: true,
    createdAt: NOW,
    ...overrides,
  }
}

export function feedbackFixture(
  overrides: Partial<InterviewFeedbackDto> = {},
): InterviewFeedbackDto {
  return {
    id: INTERVIEW_FEEDBACK_ID,
    answerVersionId: INTERVIEW_ANSWER_ID,
    scores: [
      {
        criterion: '질문 적합성',
        score: 82,
        explanation: '질문에 직접 답하고 있어요.',
      },
    ],
    strengths: ['본인의 역할을 분명히 설명했어요.'],
    weaknesses: ['결과 수치를 더 구체화할 수 있어요.'],
    suggestions: ['핵심 결과를 첫 문단에 배치하세요.'],
    revisedExample: '핵심 결과를 먼저 제시한 개선 답변입니다.',
    agentRunId: INTERVIEW_FEEDBACK_RUN_ID,
    createdAt: NOW,
    ...overrides,
  }
}

export function sourceFixture(overrides: Partial<ResearchSourceDto> = {}): ResearchSourceDto {
  return {
    id: INTERVIEW_SOURCE_ID,
    topic: 'COMPANY',
    sourceUrl: 'https://careers.example.com/interview',
    title: '공식 채용 안내',
    sourceType: 'OFFICIAL',
    publishedAt: '2026-07-01T00:00:00Z',
    retrievedAt: NOW,
    snippet: '공개된 채용 절차 안내입니다.',
    reliabilityNotice: '회사가 공개한 공식 정보입니다.',
    ...overrides,
  }
}

export function researchFixture(overrides: Partial<ResearchRunDto> = {}): ResearchRunDto {
  return {
    id: INTERVIEW_RESEARCH_RUN_ID,
    retryOfResearchRunId: null,
    researchQuality: 'BASIC',
    status: 'SUCCEEDED',
    sourceCoverage: 'SUFFICIENT',
    missingCoverageTopics: [],
    summary: '공식 채용 과정과 유사 직무 면접 정보를 확인했어요.',
    agentRunId: INTERVIEW_AGENT_RUN_ID,
    retryable: false,
    safeError: null,
    createdAt: NOW,
    startedAt: NOW,
    completedAt: NOW,
    ...overrides,
  }
}

export function questionFixture(
  overrides: Partial<InterviewQuestionDto> = {},
): InterviewQuestionDto {
  return {
    id: INTERVIEW_QUESTION_ID,
    questionOrder: 1,
    questionType: 'PROJECT_DEEP_DIVE',
    questionText: '프로젝트에서 가장 어려웠던 기술적 문제와 해결 과정을 설명해 주세요.',
    intent: '문제 해결 과정과 본인 역할을 확인합니다.',
    evaluationPoints: ['문제 정의', '본인 역할', '정량적 결과'],
    answerGuide: '상황, 본인 역할, 행동, 결과 순서로 답하세요.',
    followUpQuestions: ['다시 한다면 무엇을 바꾸시겠어요?'],
    relatedEvidenceRefs: [
      {
        id: INTERVIEW_EVIDENCE_ID,
        title: '검색 성능 개선',
        evidenceCategory: 'PROJECT',
        verificationStatus: 'VERIFIED',
        sourceType: 'CAREER',
        sourceDeleted: false,
      },
    ],
    sourceRefs: [
      {
        id: INTERVIEW_SOURCE_ID,
        topic: 'COMPANY',
        title: '공식 채용 안내',
        sourceUrl: 'https://careers.example.com/interview',
        sourceType: 'OFFICIAL',
        retrievedAt: NOW,
      },
    ],
    sourceBased: true,
    currentAnswer: answerFixture(),
    latestFeedback: feedbackFixture(),
    ...overrides,
  }
}

export function questionSetSummaryFixture(
  overrides: Partial<QuestionSetSummaryDto> = {},
): QuestionSetSummaryDto {
  return {
    id: INTERVIEW_QUESTION_SET_ID,
    job: {
      id: INTERVIEW_JOB_ID,
      companyName: 'Hiresemble',
      positionName: 'Backend Engineer',
      title: 'Backend Engineer 채용',
    },
    coverLetter: {
      id: INTERVIEW_COVER_LETTER_ID,
      title: 'Hiresemble 지원 자기소개서',
      status: 'DRAFT',
    },
    title: 'Hiresemble Backend Engineer 예상 질문',
    questionCount: 1,
    researchRunId: INTERVIEW_RESEARCH_RUN_ID,
    sourceCoverage: 'SUFFICIENT',
    agentRun: {
      id: INTERVIEW_AGENT_RUN_ID,
      status: 'SUCCEEDED',
      currentStep: null,
      progressPercent: 100,
    },
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  }
}

export function questionSetDetailFixture(
  overrides: Partial<QuestionSetDetailDto> = {},
): QuestionSetDetailDto {
  return {
    ...questionSetSummaryFixture(),
    research: researchFixture(),
    questions: [questionFixture()],
    ...overrides,
  }
}

export function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
