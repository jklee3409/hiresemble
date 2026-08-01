import { expect, test, type Page, type Route } from '@playwright/test'

const USER_ID = '71000000-0000-4000-8000-000000000001'
const JOB_ID = '71000000-0000-4000-8000-000000000002'
const EXTRACTION_RUN_ID = '71000000-0000-4000-8000-000000000003'
const ANALYSIS_RUN_ID = '71000000-0000-4000-8000-000000000004'
const ANALYSIS_ID = '71000000-0000-4000-8000-000000000005'
const NOW = '2026-08-02T00:00:00Z'

test('registration continues to BALANCED analysis and the next writing step without a browser chain call', async ({
  page,
}) => {
  let analysisReady = false
  let browserAnalysisCommands = 0
  await installRoutes(
    page,
    () => analysisReady,
    () => browserAnalysisCommands++,
  )

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/jobs/new')
  await page.locator('#job-source-url').fill('https://jobs.example.test/backend')
  await page.locator('#job-create-submit').click()

  await expect(page).toHaveURL(new RegExp(`/jobs/${JOB_ID}/overview`))
  await expect(page.getByRole('heading', { name: '플랫폼 백엔드 개발자' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '공고 분석 진행 상황' })).toBeVisible()
  await expect(page.getByText('기본 분석 · 균형 모드', { exact: true })).toBeVisible()
  await expect(page.getByText('자동 분석 중', { exact: true }).first()).toBeVisible()
  expect(browserAnalysisCommands).toBe(0)

  const analysisTab = page.getByRole('link', { name: '공고 분석', exact: true })
  await analysisTab.focus()
  await expect(analysisTab).toBeFocused()
  await analysisTab.press('Enter')
  await expect(analysisTab).toHaveAttribute('aria-current', 'page')
  await expect(page.getByText('공고 내용을 읽었어요', { exact: true })).toBeVisible()
  await expect(
    page.getByText('주요 업무와 지원 조건을 정리하고 있어요', { exact: true }),
  ).toBeVisible()
  await expect(page.locator('.analysis-command')).toHaveCount(0)

  analysisReady = true
  await page.reload()
  await expect(page.getByText('최신 분석', { exact: true })).toBeVisible()
  await expect(page.getByText('84.00점', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('link', { name: '자기소개서 준비하기' })).toBeVisible()
  expect(browserAnalysisCommands).toBe(0)

  await page
    .getByRole('navigation', { name: '공고 상세 탭' })
    .getByRole('link', { name: '자기소개서', exact: true })
    .click()
  await expect(page).toHaveURL(new RegExp(`/jobs/${JOB_ID}/cover-letter$`))

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByLabel('모바일 주요 메뉴')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    ),
  ).toBe(false)
})

async function installRoutes(
  page: Page,
  isAnalysisReady: () => boolean,
  recordAnalysisCommand: () => void,
): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/v1/auth/me') {
      return json(route, {
        id: USER_ID,
        email: 'journey@example.test',
        displayName: '여정 확인',
      })
    }
    if (path === '/api/v1/auth/csrf') {
      return json(route, {
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'safe-fixture-token',
      })
    }
    if (path === '/api/v1/profile') {
      return json(route, {
        legalName: '여정 확인',
        introduction: '백엔드 개발자',
        desiredRoles: ['백엔드 개발자'],
        desiredIndustries: ['소프트웨어'],
        desiredLocations: ['서울'],
        expectedGraduationDate: null,
        profileCompleted: true,
        missingCompletionItems: [],
        version: 1,
        createdAt: NOW,
        updatedAt: NOW,
      })
    }
    if (path === '/api/v1/jobs' && request.method() === 'POST') {
      return json(
        route,
        {
          jobId: JOB_ID,
          status: 'IN_PROGRESS',
          extractionStatus: 'QUEUED',
          agentRunId: EXTRACTION_RUN_ID,
        },
        202,
      )
    }
    if (path === `/api/v1/jobs/${JOB_ID}`) return json(route, jobDetail(isAnalysisReady()))
    if (path === `/api/v1/jobs/${JOB_ID}/analyses/latest`) {
      return isAnalysisReady()
        ? json(route, analysisDetail())
        : json(
            route,
            {
              status: 404,
              code: 'JOB_ANALYSIS_NOT_FOUND',
              message: '아직 분석 결과가 없어요.',
              fieldErrors: [],
              traceId: 'safe-fixture',
            },
            404,
          )
    }
    if (path === `/api/v1/jobs/${JOB_ID}/analyses` && request.method() === 'POST') {
      recordAnalysisCommand()
      return json(route, { message: 'The browser must not start the initial analysis.' }, 500)
    }
    if (path === `/api/v1/jobs/${JOB_ID}/analyses`) {
      return json(route, pageOf(isAnalysisReady() ? [analysisSummary()] : []))
    }
    if (path === '/api/v1/agent-runs') {
      const workflow = url.searchParams.get('workflowType')
      const run =
        workflow === 'JOB_POSTING_EXTRACTION' ? extractionRun() : analysisRun(isAnalysisReady())
      return json(route, pageOf([run]))
    }
    if (path === `/api/v1/agent-runs/${EXTRACTION_RUN_ID}`) {
      return json(route, runDetail(extractionRun()))
    }
    if (path === `/api/v1/agent-runs/${ANALYSIS_RUN_ID}`) {
      return json(route, runDetail(analysisRun(isAnalysisReady())))
    }
    if (path === '/api/v1/cover-letters') return json(route, pageOf([]))

    return json(route, { message: `Unhandled fixture route: ${request.method()} ${path}` }, 500)
  })
}

function jobDetail(ready: boolean) {
  const latestAnalysis = ready ? analysisSummary() : null
  return {
    id: JOB_ID,
    companyName: '모아테크',
    title: '플랫폼 백엔드 개발자',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS',
    extractionStatus: 'EXTRACTED',
    submittedAt: null,
    deadlineAt: '2026-08-31T14:59:59Z',
    deadlineSource: 'AUTO_EXTRACTED',
    latestFitScore: ready ? 84 : null,
    analysisOutdated: false,
    outdatedReasons: [],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 1,
    createdAt: NOW,
    updatedAt: NOW,
    sourceUrl: 'https://jobs.example.test/backend',
    canonicalUrl: 'https://jobs.example.test/backend',
    roleCategory: '개발',
    employmentType: '정규직',
    location: '서울',
    descriptionText: '주요 업무\n- Spring API 개발\n\n지원 자격\n- Java 개발 경험',
    descriptionSource: 'AUTO_EXTRACTED',
    extractionError: null,
    automaticAnalysis: {
      state: 'LAUNCHED',
      qualityMode: 'BALANCED',
      agentRunId: ANALYSIS_RUN_ID,
      error: null,
    },
    closedAt: null,
    closedReason: null,
    latestAnalysis,
    coverLetterId: null,
    latestQuestionSetId: null,
    latestMockSessionId: null,
  }
}

function analysisSummary() {
  return {
    id: ANALYSIS_ID,
    analysisVersion: 1,
    eligibility: 'ELIGIBLE',
    fitScore: 84,
    analysisOutdated: false,
    outdatedReasons: [],
    createdAt: NOW,
    agentRunId: ANALYSIS_RUN_ID,
  }
}

function analysisDetail() {
  return {
    ...analysisSummary(),
    scoreBreakdown: [],
    requiredQualifications: [],
    preferredQualifications: [],
    responsibilities: [],
    strengths: ['Spring API 개발 경험이 잘 맞아요.'],
    gaps: ['운영 규모를 더 구체적으로 적어 보세요.'],
    matchedEvidenceRefs: [],
    analysisSummary: '핵심 개발 경험이 공고와 잘 맞아요.',
  }
}

function extractionRun() {
  return runSummary(EXTRACTION_RUN_ID, 'JOB_POSTING_EXTRACTION', 'SUCCEEDED', 100)
}

function analysisRun(ready: boolean) {
  return runSummary(
    ANALYSIS_RUN_ID,
    'JOB_ANALYSIS',
    ready ? 'SUCCEEDED' : 'RUNNING',
    ready ? 100 : 62,
  )
}

function runSummary(id: string, workflowType: string, status: string, progressPercent: number) {
  return {
    id,
    workflowType,
    resourceType: 'JOB',
    resourceId: JOB_ID,
    status,
    currentStep: status === 'RUNNING' ? 'ANALYZE_JOB' : null,
    progressPercent,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: status === 'SUCCEEDED' ? 'BALANCED' : null,
    estimatedCostUsd: 0.05,
    reservedCostUsd: status === 'RUNNING' ? 0.05 : 0,
    actualCostUsd: status === 'SUCCEEDED' ? 0.03 : null,
    retryable: false,
    cancellable: status === 'RUNNING',
    requiredUserAction: null,
    stateVersion: 3,
    queuedAt: NOW,
    updatedAt: NOW,
  }
}

function runDetail(summary: ReturnType<typeof extractionRun> | ReturnType<typeof analysisRun>) {
  return {
    ...summary,
    retryOfRunId: null,
    rootRunId: summary.id,
    runAttemptNo: 1,
    durationMs: summary.status === 'RUNNING' ? null : 1_500,
    startedAt: NOW,
    completedAt: summary.status === 'RUNNING' ? null : NOW,
    safeError: null,
    partialResult: null,
    steps: [],
  }
}

function pageOf(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
  }
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  })
}
