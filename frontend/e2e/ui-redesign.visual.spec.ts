import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect, test, type Page, type Route } from '@playwright/test'

const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  document: '00000000-0000-4000-8000-000000000010',
  job: '00000000-0000-4000-8000-000000000020',
  analysis: '00000000-0000-4000-8000-000000000021',
  run: '00000000-0000-4000-8000-000000000022',
  coverLetter: '00000000-0000-4000-8000-000000000030',
  coverQuestion: '00000000-0000-4000-8000-000000000031',
  answerVersion: '00000000-0000-4000-8000-000000000032',
  questionSet: '00000000-0000-4000-8000-000000000040',
  research: '00000000-0000-4000-8000-000000000041',
  interviewQuestion: '00000000-0000-4000-8000-000000000042',
} as const

const now = '2026-08-01T09:00:00Z'
const updated = '2026-08-02T00:20:00Z'

const profile = {
  legalName: '김하이어',
  introduction:
    '사용자가 이해하기 쉬운 흐름을 설계하고 안정적인 서버를 만드는 백엔드 개발자입니다.',
  desiredRoles: ['백엔드 개발자'],
  desiredIndustries: ['IT·소프트웨어'],
  desiredLocations: ['서울'],
  expectedGraduationDate: '2027-02-28',
  profileCompleted: true,
  missingCompletionItems: [],
  version: 3,
  createdAt: now,
  updatedAt: updated,
}

const analysisSummary = {
  id: ids.analysis,
  analysisVersion: 1,
  eligibility: 'ELIGIBLE',
  fitScore: 82,
  analysisOutdated: false,
  outdatedReasons: [],
  createdAt: updated,
  agentRunId: ids.run,
}

const jobSummary = {
  id: ids.job,
  companyName: '모아테크',
  title: '플랫폼 백엔드 개발자',
  positionName: '백엔드 개발자',
  status: 'IN_PROGRESS',
  extractionStatus: 'EXTRACTED',
  submittedAt: null,
  deadlineAt: '2026-08-18T14:59:59Z',
  deadlineSource: 'AUTO_EXTRACTED',
  latestFitScore: 82,
  analysisOutdated: false,
  outdatedReasons: [],
  coverLetterStatus: 'DRAFT',
  interviewPreparationCount: 1,
  version: 2,
  createdAt: now,
  updatedAt: updated,
}

const jobDetail = {
  ...jobSummary,
  sourceUrl: 'https://jobs.example.test/moatech/backend',
  canonicalUrl: 'https://jobs.example.test/moatech/backend',
  roleCategory: '개발',
  employmentType: '정규직',
  location: '서울 성동구',
  descriptionText: [
    '주요 업무',
    '- Java와 Spring Boot 기반 API를 설계하고 운영합니다.',
    '- 서비스 지표를 바탕으로 병목을 찾고 개선합니다.',
    '',
    '지원 자격',
    '• 관계형 데이터베이스와 SQL을 활용한 경험',
    '• 협업 과정에서 문제와 선택 근거를 설명할 수 있는 분',
    '',
    '우대 사항',
    '1. 메시지 큐를 활용한 비동기 처리 경험',
    '2. 테스트 자동화와 운영 관측성 개선 경험',
    '',
    '전형 절차',
    '서류 검토 → 직무 인터뷰 → 컬처 인터뷰',
    '',
    '원본 공고: https://jobs.example.test/moatech/backend',
  ].join('\n'),
  descriptionSource: 'AUTO_EXTRACTED',
  extractionError: null,
  automaticAnalysis: {
    state: 'LAUNCHED',
    qualityMode: 'BALANCED',
    agentRunId: ids.run,
    error: null,
  },
  closedAt: null,
  closedReason: null,
  latestAnalysis: analysisSummary,
  coverLetterId: ids.coverLetter,
  latestQuestionSetId: ids.questionSet,
  latestMockSessionId: null,
}

const evidenceRef = {
  id: '00000000-0000-4000-8000-000000000060',
  title: '주문 처리 지연 35% 개선',
  evidenceCategory: 'PROJECT',
  verificationStatus: 'VERIFIED',
  sourceType: 'CAREER',
  sourceDeleted: false,
}

const analysisDetail = {
  ...analysisSummary,
  scoreBreakdown: [
    {
      category: 'REQUIRED_QUALIFICATION',
      criterion: 'Java와 Spring Boot 기반 API 개발 경험',
      weight: 30,
      matchLevel: 'MATCHED',
      score: 27,
      evidenceRefs: [evidenceRef],
      explanation: '운영 API 개선 경험이 필수 조건과 직접 연결됩니다.',
    },
    {
      category: 'CORE_RESPONSIBILITY_OR_SKILL',
      criterion: '서비스 지표 기반 성능 개선',
      weight: 30,
      matchLevel: 'PARTIAL',
      score: 22,
      evidenceRefs: [evidenceRef],
      explanation: '성과는 확인되지만 관측 도구와 규모를 더 구체화하면 좋습니다.',
    },
    {
      category: 'PREFERRED_QUALIFICATION',
      criterion: '메시지 큐와 비동기 처리 경험',
      weight: 20,
      matchLevel: 'PARTIAL',
      score: 15,
      evidenceRefs: [],
      explanation: '프로젝트 설명에 비동기 처리 방식이 충분히 드러나지 않습니다.',
    },
    {
      category: 'RELATED_EXPERIENCE_OR_DOMAIN',
      criterion: '협업과 문제 해결 과정 설명',
      weight: 20,
      matchLevel: 'MATCHED',
      score: 18,
      evidenceRefs: [evidenceRef],
      explanation: '선택 근거와 결과가 함께 기록되어 있습니다.',
    },
  ],
  requiredQualifications: [
    {
      category: 'REQUIRED_QUALIFICATION',
      text: '관계형 데이터베이스와 SQL 활용 경험',
      required: true,
      sourceLocation: '지원 자격',
    },
  ],
  preferredQualifications: [
    {
      category: 'PREFERRED_QUALIFICATION',
      text: '메시지 큐를 활용한 비동기 처리 경험',
      required: false,
      sourceLocation: '우대 사항',
    },
  ],
  responsibilities: [
    {
      category: 'CORE_RESPONSIBILITY_OR_SKILL',
      text: 'Java와 Spring Boot 기반 API 설계 및 운영',
      required: true,
      sourceLocation: '주요 업무',
    },
  ],
  strengths: [
    '운영 지표를 근거로 병목을 찾아 개선한 경험이 잘 맞습니다.',
    'API 설계와 협업 경험이 확인됩니다.',
  ],
  gaps: ['메시지 큐를 선택한 이유와 처리 규모를 경험 항목에 보완해 보세요.'],
  matchedEvidenceRefs: [evidenceRef],
  analysisSummary:
    '핵심 API 개발과 성능 개선 경험이 공고와 잘 맞습니다. 비동기 처리 경험을 구체화하면 우대 조건까지 더 선명하게 설명할 수 있습니다.',
}

const runSummary = {
  id: ids.run,
  workflowType: 'JOB_ANALYSIS',
  resourceType: 'JOB',
  resourceId: ids.job,
  status: 'SUCCEEDED',
  currentStep: null,
  progressPercent: 100,
  requestedQualityMode: 'BALANCED',
  highestModelTierUsed: 'BALANCED',
  estimatedCostUsd: 0.08,
  reservedCostUsd: 0,
  actualCostUsd: 0.04,
  retryable: false,
  cancellable: false,
  requiredUserAction: null,
  stateVersion: 12,
  queuedAt: now,
  updatedAt: updated,
}

const documentSummary = {
  id: ids.document,
  documentType: 'RESUME',
  originalFilename: 'demo-resume.pdf',
  displayName: '지원용 이력서.pdf',
  mimeType: 'application/pdf',
  fileSizeBytes: 248_000,
  parseStatus: 'PARSED',
  evidenceExtractionStatus: 'SUCCEEDED',
  manualTextProvided: false,
  safeError: null,
  latestAgentRunId: null,
  version: 2,
  uploadedAt: now,
  updatedAt: updated,
}

const jobRef = {
  id: ids.job,
  companyName: '모아테크',
  positionName: '백엔드 개발자',
  title: '플랫폼 백엔드 개발자',
}

const answerVersion = {
  id: ids.answerVersion,
  questionId: ids.coverQuestion,
  parentVersionId: null,
  restoredFromVersionId: null,
  versionNo: 1,
  contentJson: {
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        text: null,
        marks: [],
        content: [
          {
            type: 'text',
            text: '사용자 관찰과 운영 지표를 연결해 문제를 해결한 경험이 있습니다.',
            marks: [],
            content: [],
          },
        ],
      },
    ],
  },
  plainText: '사용자 관찰과 운영 지표를 연결해 문제를 해결한 경험이 있습니다.',
  characterCount: 37,
  sourceType: 'USER_EDITED',
  isCurrent: true,
  createdBy: 'USER',
  createdAt: updated,
}

const coverLetterSummary = {
  id: ids.coverLetter,
  job: jobRef,
  title: '모아테크 백엔드 개발자 자기소개서',
  status: 'DRAFT',
  questionCount: 1,
  answeredQuestionCount: 1,
  latestVerificationStatus: null,
  warningCount: 0,
  canEdit: true,
  canArchive: true,
  canUnarchive: false,
  canFinalize: true,
  version: 2,
  finalizedAt: null,
  archivedAt: null,
  createdAt: now,
  updatedAt: updated,
}

const coverLetterDetail = {
  ...coverLetterSummary,
  questions: [
    {
      id: ids.coverQuestion,
      questionOrder: 1,
      questionText: '지원 직무를 선택한 이유와 준비 과정을 설명해 주세요.',
      maxLength: 700,
      memo: null,
      currentAnswer: answerVersion,
      latestVerification: null,
      version: 1,
      deletedAt: null,
    },
  ],
}

const questionSetSummary = {
  id: ids.questionSet,
  job: jobRef,
  coverLetter: {
    id: ids.coverLetter,
    title: coverLetterSummary.title,
    status: 'DRAFT',
  },
  title: '모아테크 백엔드 개발자 면접 준비',
  questionCount: 1,
  researchRunId: ids.research,
  sourceCoverage: 'SUFFICIENT',
  agentRun: {
    id: ids.run,
    status: 'SUCCEEDED',
    currentStep: null,
    progressPercent: 100,
  },
  createdAt: now,
  updatedAt: updated,
}

const research = {
  id: ids.research,
  retryOfResearchRunId: null,
  researchQuality: 'BASIC',
  status: 'SUCCEEDED',
  sourceCoverage: 'SUFFICIENT',
  missingCoverageTopics: [],
  summary: '공식 채용 페이지와 기술 블로그에서 서비스 방향과 면접 정보를 확인했습니다.',
  agentRunId: ids.run,
  retryable: false,
  safeError: null,
  createdAt: now,
  startedAt: now,
  completedAt: updated,
}

const questionSetDetail = {
  ...questionSetSummary,
  research,
  questions: [
    {
      id: ids.interviewQuestion,
      questionOrder: 1,
      questionType: 'PROJECT_DEEP_DIVE',
      questionText: '주문 처리 지연을 개선할 때 가장 먼저 확인한 지표는 무엇이었나요?',
      intent: '문제 정의와 데이터 기반 의사결정 과정을 확인합니다.',
      evaluationPoints: ['문제 범위', '지표 선택 근거', '개선 결과'],
      answerGuide: '상황, 확인한 지표, 선택한 해결책, 결과 순서로 답해 보세요.',
      followUpQuestions: ['다시 진행한다면 무엇을 다르게 하겠나요?'],
      relatedEvidenceRefs: [evidenceRef],
      sourceRefs: [],
      sourceBased: false,
      currentAnswer: null,
      latestFeedback: null,
    },
  ],
}

const basePages = [
  ['/dashboard', 'dashboard'],
  ['/profile/basic', 'profile-basic'],
  ['/documents', 'documents'],
  [`/documents/${ids.document}`, 'document-detail'],
  ['/jobs', 'jobs'],
  ['/jobs/new', 'job-new'],
  [`/jobs/${ids.job}/overview`, 'job-overview'],
  [`/jobs/${ids.job}/analysis`, 'job-analysis'],
  ['/cover-letters', 'cover-letters'],
  [`/cover-letters/${ids.coverLetter}/edit`, 'cover-letter-edit'],
  ['/interviews', 'interviews'],
  [`/interview-question-sets/${ids.questionSet}`, 'interview-question-set'],
  ['/agent-runs', 'agent-runs'],
  ['/onboarding', 'onboarding'],
] as const

test('captures the product surfaces with a safe deterministic fixture', async ({ page }) => {
  await installFixtureRoutes(page)
  const phase = process.env.UI_CAPTURE_PHASE ?? 'after'
  const output = resolve(process.cwd(), '..', 'output', 'playwright', phase)
  mkdirSync(output, { recursive: true })
  const pages = phase === 'before' ? basePages : ([...basePages, ['/guide', 'guide']] as const)

  for (const viewport of [
    { width: 1440, height: 1000, suffix: '1440' },
    { width: 390, height: 844, suffix: '390' },
  ] as const) {
    await page.setViewportSize(viewport)
    for (const [path, name] of pages) {
      await page.goto(path)
      await expect(page.locator('#app-content')).toBeVisible()
      await page
        .locator('[aria-busy="true"]')
        .waitFor({ state: 'detached', timeout: 2_000 })
        .catch(() => {})
      await page.screenshot({
        path: resolve(output, `${name}-${viewport.suffix}.png`),
        fullPage: true,
        animations: 'disabled',
      })
    }
  }
})

async function installFixtureRoutes(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') {
      return json(route, {
        id: ids.user,
        email: 'demo@hiresemble.test',
        displayName: '김하이어',
      })
    }
    if (path === '/profile') return json(route, profile)
    if (path === '/profile/educations') return json(route, pageOf([]))
    if (path === '/profile/evidence') return json(route, pageOf([evidenceRef]))
    if (path.startsWith('/profile/')) return json(route, pageOf([]))

    if (path === '/documents') return json(route, pageOf([documentSummary]))
    if (path === `/documents/${ids.document}`) {
      return json(route, {
        ...documentSummary,
        pageCount: 2,
        characterCount: 1_240,
        parsedAt: updated,
      })
    }
    if (path === `/documents/${ids.document}/text`) {
      return json(route, {
        documentId: ids.document,
        text: '경력 요약\nSpring Boot API를 운영하고 주문 처리 지연을 35% 개선했습니다.',
        characterCount: 52,
        manualTextProvided: false,
        version: 2,
        updatedAt: updated,
      })
    }

    if (path === '/jobs') return json(route, pageOf([jobSummary]))
    if (path === `/jobs/${ids.job}`) return json(route, jobDetail)
    if (path === `/jobs/${ids.job}/analyses/latest`) return json(route, analysisDetail)
    if (path === `/jobs/${ids.job}/analyses`) return json(route, pageOf([analysisSummary]))

    if (path === '/agent-runs') return json(route, pageOf([runSummary]))
    if (path === `/agent-runs/${ids.run}`) {
      return json(route, {
        ...runSummary,
        retryOfRunId: null,
        rootRunId: ids.run,
        runAttemptNo: 1,
        durationMs: 18_000,
        startedAt: now,
        completedAt: updated,
        safeError: null,
        partialResult: null,
        steps: [],
      })
    }

    if (path === '/cover-letters') return json(route, pageOf([coverLetterSummary]))
    if (path === `/cover-letters/${ids.coverLetter}`) return json(route, coverLetterDetail)
    if (path === `/cover-letter-questions/${ids.coverQuestion}/versions`) {
      return json(route, pageOf([answerVersion]))
    }
    if (path === `/cover-letter-answer-versions/${ids.answerVersion}/verifications`) {
      return json(route, pageOf([]))
    }

    if (path === '/interview-question-sets') return json(route, pageOf([questionSetSummary]))
    if (path === `/interview-question-sets/${ids.questionSet}`) {
      return json(route, questionSetDetail)
    }
    if (path === `/research-runs/${ids.research}/sources`) return json(route, pageOf([]))
    if (path === `/interview-questions/${ids.interviewQuestion}/answer-versions`) {
      return json(route, pageOf([]))
    }

    return json(
      route,
      {
        status: 404,
        code: 'RESOURCE_NOT_FOUND',
        message: '테스트 fixture에 없는 요청입니다.',
        fieldErrors: [],
        traceId: 'visual-fixture',
      },
      404,
    )
  })
}

function pageOf(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
  }
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  })
}
