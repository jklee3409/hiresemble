import type {
  CoverLetterAnswerVersionDto,
  CoverLetterDetailDto,
  CoverLetterQuestionDto,
  CoverLetterSummaryDto,
  TipTapDocumentDto,
  VerificationDto,
} from '@/shared/api/coverLetterContracts'

export const COVER_LETTER_ID = uuid(100)
export const COVER_LETTER_JOB_ID = uuid(101)
export const COVER_LETTER_QUESTION_ID = uuid(102)
export const COVER_LETTER_ANSWER_ID = uuid(103)
export const COVER_LETTER_VERIFICATION_ID = uuid(104)
export const COVER_LETTER_RUN_ID = uuid(105)
export const COVER_LETTER_EVIDENCE_ID = uuid(106)
export const COVER_LETTER_NOW = '2026-07-30T00:00:00Z'

export function tipTapDocument(
  text = '승인된 근거를 바탕으로 작성한 답변입니다.',
): TipTapDocumentDto {
  return {
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        text: null,
        marks: [],
        content: text ? [{ type: 'text', text, marks: [], content: [] }] : [],
      },
    ],
  }
}

export function answerVersionFixture(
  overrides: Partial<CoverLetterAnswerVersionDto> = {},
): CoverLetterAnswerVersionDto {
  const plainText = overrides.plainText ?? '승인된 근거를 바탕으로 작성한 답변입니다.'
  return {
    id: COVER_LETTER_ANSWER_ID,
    questionId: COVER_LETTER_QUESTION_ID,
    parentVersionId: null,
    restoredFromVersionId: null,
    versionNo: 1,
    contentJson: tipTapDocument(plainText),
    plainText,
    characterCount: Array.from(plainText).length,
    sourceType: 'USER_EDITED',
    isCurrent: true,
    createdBy: 'USER',
    createdAt: COVER_LETTER_NOW,
    ...overrides,
  }
}

export function verificationFixture(overrides: Partial<VerificationDto> = {}): VerificationDto {
  return {
    id: COVER_LETTER_VERIFICATION_ID,
    answerVersionId: COVER_LETTER_ANSWER_ID,
    status: 'WARNING',
    issues: [
      {
        code: 'UNVERIFIED_CLAIM',
        severity: 'WARNING',
        message: '성과 표현을 한 번 더 확인해 주세요.',
        relatedText: '성과를 크게 높였습니다.',
        evidenceRefs: [
          {
            id: COVER_LETTER_EVIDENCE_ID,
            title: '프로젝트 성과',
            evidenceCategory: 'PROJECT',
            verificationStatus: 'VERIFIED',
            sourceType: 'CAREER',
            sourceDeleted: false,
          },
        ],
      },
    ],
    suggestions: ['성과의 범위와 본인의 역할을 구체적으로 적어 주세요.'],
    verifiedClaims: [],
    evidenceRefs: [],
    agentRunId: COVER_LETTER_RUN_ID,
    createdAt: COVER_LETTER_NOW,
    ...overrides,
  }
}

export function questionFixture(
  overrides: Partial<CoverLetterQuestionDto> = {},
): CoverLetterQuestionDto {
  return {
    id: COVER_LETTER_QUESTION_ID,
    questionOrder: 1,
    questionText: '지원 동기와 입사 후 목표를 작성해 주세요.',
    maxLength: 1_000,
    memo: null,
    currentAnswer: answerVersionFixture(),
    latestVerification: verificationFixture(),
    version: 2,
    deletedAt: null,
    ...overrides,
  }
}

export function coverLetterSummaryFixture(
  overrides: Partial<CoverLetterSummaryDto> = {},
): CoverLetterSummaryDto {
  return {
    id: COVER_LETTER_ID,
    job: {
      id: COVER_LETTER_JOB_ID,
      companyName: 'Hiresemble',
      positionName: 'Frontend Engineer',
      title: 'Frontend Engineer',
    },
    title: 'Hiresemble 지원 자기소개서',
    status: 'DRAFT',
    questionCount: 1,
    answeredQuestionCount: 1,
    latestVerificationStatus: 'WARNING',
    warningCount: 1,
    canEdit: true,
    canArchive: true,
    canUnarchive: false,
    canFinalize: true,
    version: 3,
    finalizedAt: null,
    archivedAt: null,
    createdAt: COVER_LETTER_NOW,
    updatedAt: COVER_LETTER_NOW,
    ...overrides,
  }
}

export function coverLetterDetailFixture(
  overrides: Partial<CoverLetterDetailDto> = {},
): CoverLetterDetailDto {
  return {
    ...coverLetterSummaryFixture(),
    questions: [questionFixture()],
    ...overrides,
  }
}

export function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
