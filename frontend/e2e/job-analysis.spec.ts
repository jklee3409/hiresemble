import { expect, test, type Page, type Route } from '@playwright/test'

const USER_ID = '30000000-0000-4000-8000-000000000001'
const JOB_ID = '50000000-0000-4000-8000-000000000001'
const ANALYSIS_ID = '50000000-0000-4000-8000-000000000003'
const RUN_ID = '50000000-0000-4000-8000-000000000004'
const EVIDENCE_ID = '50000000-0000-4000-8000-000000000005'
const NOW = '2026-07-29T00:00:00Z'

test('Job analysis stays owner-scoped, accessible and overflow-free at desktop and mobile', async ({
  page,
}) => {
  await installFixtureRoutes(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`/jobs/${JOB_ID}/analysis`)

  await expect(page.getByRole('heading', { name: '공고 분석', exact: true }).last()).toBeVisible()
  await expect(page.getByRole('link', { name: '공고 분석', exact: true })).toHaveAttribute(
    'aria-current',
    'page',
  )
  await expect(page.getByText('필수 조건 미충족', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('82.50점', { exact: true }).first()).toBeVisible()
  await expect(
    page.getByText(
      '적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.',
      { exact: true },
    ),
  ).toHaveCount(3)
  await expect(page.getByText('공고 내용이 변경됨', { exact: true })).toBeVisible()
  await expect(page.getByText('프로필 정보가 변경됨', { exact: true })).toBeVisible()
  await expect(page.getByText('승인된 경험 정보가 변경됨', { exact: true })).toBeVisible()
  await expect(page.getByText('결제 API 개선 프로젝트', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('heading', { name: '과거 분석 이력' })).toBeVisible()
  await expect(page.getByText('자기소개서', { exact: true })).toHaveCount(0)
  await expect(page.getByText('면접 준비', { exact: true })).toHaveCount(0)
  await expectNoHorizontalOverflow(page)

  const analysisTab = page.getByRole('link', { name: '공고 분석', exact: true })
  await analysisTab.focus()
  await expect(analysisTab).toBeFocused()
  await analysisTab.press('Enter')
  await expect(page).toHaveURL(new RegExp(`/jobs/${JOB_ID}/analysis$`))

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await expect(page.getByText('82.50점', { exact: true }).first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

async function installFixtureRoutes(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/auth/me') {
      await json(route, {
        id: USER_ID,
        email: 'fixture@example.com',
        displayName: '브라우저 Fixture',
      })
      return
    }
    if (url.pathname === '/api/v1/auth/csrf') {
      await json(route, {
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'fixture-csrf-token',
      })
      return
    }
    if (url.pathname === '/api/v1/profile') {
      await json(route, {
        legalName: '브라우저 Fixture',
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
      return
    }
    if (url.pathname === `/api/v1/jobs/${JOB_ID}/analyses/latest`) {
      await json(route, analysisDetail())
      return
    }
    if (url.pathname === `/api/v1/jobs/${JOB_ID}/analyses`) {
      await json(route, pageResponse([analysisSummary()]))
      return
    }
    if (url.pathname === `/api/v1/jobs/${JOB_ID}`) {
      await json(route, jobDetail())
      return
    }
    if (url.pathname === '/api/v1/agent-runs') {
      await json(route, pageResponse([]))
      return
    }
    await json(route, { message: `Unhandled fixture route: ${url.pathname}` }, 500)
  })
}

async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  await expect
    .poll(() =>
      page.evaluate(() => ({
        viewport: window.innerWidth,
        document: document.documentElement.scrollWidth,
      })),
    )
    .toEqual(
      expect.objectContaining({
        document: await page.evaluate(() => window.innerWidth),
      }),
    )
}

function jobDetail() {
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
    latestFitScore: 82.5,
    analysisOutdated: true,
    outdatedReasons: ['JOB_CONTENT_CHANGED', 'PROFILE_CHANGED', 'EVIDENCE_CHANGED'],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 2,
    createdAt: NOW,
    updatedAt: NOW,
    sourceUrl: 'https://jobs.example.com/openings/1',
    canonicalUrl: 'https://jobs.example.com/openings/1',
    roleCategory: null,
    employmentType: null,
    location: '서울',
    descriptionText: 'Spring 기반 백엔드 API를 개발합니다.',
    descriptionSource: 'AUTO_EXTRACTED',
    extractionError: null,
    closedAt: null,
    closedReason: null,
    latestAnalysis: analysisSummary(),
    coverLetterId: null,
    latestQuestionSetId: null,
    latestMockSessionId: null,
  }
}

function analysisSummary() {
  return {
    id: ANALYSIS_ID,
    analysisVersion: 2,
    eligibility: 'INELIGIBLE',
    fitScore: 82.5,
    analysisOutdated: true,
    outdatedReasons: ['JOB_CONTENT_CHANGED', 'PROFILE_CHANGED', 'EVIDENCE_CHANGED'],
    createdAt: NOW,
    agentRunId: RUN_ID,
  }
}

function analysisDetail() {
  const evidence = {
    id: EVIDENCE_ID,
    title: '결제 API 개선 프로젝트',
    evidenceCategory: 'CAREER_PROJECT',
    verificationStatus: 'VERIFIED',
    sourceType: 'CAREER',
    sourceDeleted: false,
  }
  return {
    ...analysisSummary(),
    scoreBreakdown: [
      {
        category: 'REQUIRED_QUALIFICATION',
        criterion: 'Java 개발 경력 3년',
        weight: 40,
        matchLevel: 'PARTIAL',
        score: 20,
        evidenceRefs: [evidence],
        explanation: '승인된 경력에서 Java 서비스 개발 경험을 일부 확인했어요.',
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
    analysisSummary: '필수 경력은 부족하지만 핵심 기술 경험은 높은 일치를 보여요.',
  }
}

function pageResponse(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  }
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}
