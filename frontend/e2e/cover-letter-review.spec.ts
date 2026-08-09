import { expect, test, type Page, type Route } from '@playwright/test'

const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  job: '00000000-0000-4000-8000-000000000020',
  coverLetter: '00000000-0000-4000-8000-000000000030',
  question: '00000000-0000-4000-8000-000000000031',
  answerVersion: '00000000-0000-4000-8000-000000000032',
  verification: '00000000-0000-4000-8000-000000000033',
  run: '00000000-0000-4000-8000-000000000034',
  analysis: '00000000-0000-4000-8000-000000000035',
}
const NOW = '2026-08-07T00:00:00Z'

function cardGeometry(element: Element) {
  const style = getComputedStyle(element)
  return { radius: style.borderRadius, padding: style.padding }
}

test('opens AI review in the same column and card style as the job requirements tab', async ({
  page,
}) => {
  await installRoutes(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`/cover-letters/${ids.coverLetter}/edit`)

  const assist = page.locator('.cover-workspace__assist')
  const editor = page.locator('.cover-workspace__main')
  await expect(page.getByTestId('cover-letter-editor')).toBeVisible()

  // 공고 요구사항 tab의 열 폭과 카드 모양이 두 tab의 기준이다.
  await expect(page.locator('.assist__body h3', { hasText: '공고가 원하는 것' })).toBeVisible()
  const requirementWidth = (await assist.boundingBox())?.width ?? 0
  const requirementCard = await page.locator('.assist__cards > li').first().evaluate(cardGeometry)
  const requirementTagRadius = await page
    .locator('.assist__card-tag')
    .first()
    .evaluate((element) => getComputedStyle(element).borderRadius)

  await page.getByTestId('assist-tab-review').click()
  await expect(page.locator('.verification-card')).toBeVisible()

  // 검토 결과는 별도 modal이 아니라 같은 화면의 같은 열에서 열리고 편집기는 그대로 남는다.
  await expect(page.locator('[role="dialog"]')).toHaveCount(0)
  await expect(editor.locator('.ProseMirror')).toBeVisible()
  await expect
    .poll(async () => (await assist.boundingBox())?.width ?? 0)
    .toBeCloseTo(requirementWidth, 0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(
    false,
  )

  // 검토 결과도 요구사항과 같은 제목 block으로 나뉘고, 떠 있는 카드로 감싸지 않는다.
  await expect(page.locator('.assist__body h3', { hasText: '고쳐야 할 곳' })).toBeVisible()
  const wrapper = await page
    .locator('.verification-card')
    .first()
    .evaluate((element) => {
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, shadow: style.boxShadow }
    })
  expect(wrapper.background).toBe('rgba(0, 0, 0, 0)')
  expect(wrapper.shadow).toBe('none')

  const notice = page.locator('.verification-issues > li[data-severity="WARNING"]').first()
  const error = page.locator('.verification-issues > li[data-severity="ERROR"]').first()
  await expect(notice).toContainText('확인 권장')
  await expect(error).toContainText('수정 필요')

  // 지적 사항 카드는 요구사항 카드와 같은 모서리와 여백을 쓴다.
  expect(await notice.evaluate(cardGeometry)).toEqual(requirementCard)

  // 좌측 색 띠를 걷어내고 심각도는 채움면과 알약 색으로만 알린다.
  for (const issue of [notice, error]) {
    const borders = await issue.evaluate((element) => {
      const style = getComputedStyle(element)
      return { width: style.borderLeftWidth, style: style.borderLeftStyle }
    })
    expect(borders.width === '0px' || borders.style === 'none').toBe(true)
  }

  const noticeColors = await notice.evaluate((element) => {
    const pill = element.querySelector('.verification-issues__severity') as HTMLElement
    return {
      surface: getComputedStyle(element).backgroundColor,
      pill: getComputedStyle(pill).color,
      pillRadius: getComputedStyle(pill).borderRadius,
    }
  })
  // 확인 권장은 danger의 적-주황 축을 쓰지 않는 제품 brand blue 계열이다.
  expect(noticeColors.pill).toBe('rgb(32, 57, 189)')
  expect(noticeColors.surface).toBe('rgb(227, 232, 255)')
  // 심각도 알약은 요구사항 분류 알약과 같은 알약 모양을 공유한다.
  expect(noticeColors.pillRadius).toBe(requirementTagRadius)
  await expect(error).toContainText('확인이 필요한 내용')

  await page.getByTestId('assist-tab-job').click()
  await expect
    .poll(async () => (await assist.boundingBox())?.width ?? 0)
    .toBeCloseTo(requirementWidth, 0)

  // 작성 도움은 항상 보인다. 접기 버튼 없이 tab이 열 맨 위에 붙는다.
  await expect(page.getByRole('button', { name: /작성 도움/ })).toHaveCount(0)
  const assistTop = (await assist.boundingBox())?.y ?? 0
  const editorTop = (await editor.boundingBox())?.y ?? 0
  expect(Math.abs(assistTop - editorTop)).toBeLessThan(4)
})

async function installRoutes(page: Page): Promise<void> {
  const json = (route: Route, body: unknown) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  const pageOf = (items: unknown[]) => ({
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  })

  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') {
      return json(route, { id: ids.user, email: 'review@example.com', displayName: '검토 사용자' })
    }
    if (path === '/profile') {
      return json(route, {
        legalName: '검토 사용자',
        introduction: null,
        desiredRoles: [],
        desiredIndustries: [],
        desiredLocations: [],
        expectedGraduationDate: null,
        profileCompleted: true,
        missingCompletionItems: [],
        version: 0,
        createdAt: NOW,
        updatedAt: NOW,
      })
    }
    if (path === `/cover-letters/${ids.coverLetter}`) return json(route, coverLetterDetail())
    if (path === `/jobs/${ids.job}/analyses/latest`) return json(route, analysisDetail())
    if (path === `/cover-letter-questions/${ids.question}/versions`) {
      return json(route, pageOf([answerVersion()]))
    }
    if (path === `/cover-letter-answer-versions/${ids.answerVersion}/verifications`) {
      return json(route, pageOf([verification()]))
    }
    return json(route, pageOf([]))
  })
}

/* 공고 요구사항 tab이 비교 기준이 되는 카드를 실제로 그리도록 최신 분석을 함께 준다. */
function analysisDetail() {
  return {
    id: ids.analysis,
    analysisVersion: 1,
    eligibility: 'ELIGIBLE',
    fitScore: 72,
    analysisCoverage: 80,
    analysisOutdated: false,
    outdatedReasons: [],
    createdAt: NOW,
    agentRunId: ids.run,
    scoreBreakdown: [],
    requiredQualifications: [
      {
        category: 'REQUIRED_QUALIFICATION',
        text: '백엔드 서비스 운영 경험 3년 이상',
        required: true,
        sourceLocation: null,
      },
    ],
    preferredQualifications: [],
    responsibilities: [
      {
        category: 'CORE_RESPONSIBILITY_OR_SKILL',
        text: '대규모 트래픽을 다루는 API 설계와 개선',
        required: false,
        sourceLocation: null,
      },
    ],
    strengths: [],
    gaps: ['대규모 트래픽 운영 경험을 보강하면 좋아요.'],
    matchedEvidenceRefs: [],
    analysisSummary: null,
  }
}

function answerVersion() {
  return {
    id: ids.answerVersion,
    questionId: ids.question,
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
    createdAt: NOW,
  }
}

function verification() {
  return {
    id: ids.verification,
    answerVersionId: ids.answerVersion,
    status: 'WARNING',
    issues: [
      {
        code: 'REQUIREMENT_MISSING',
        severity: 'WARNING',
        message: '공고가 요구한 대규모 트래픽 운영 경험이 답변에 드러나지 않아요.',
        relatedText: null,
        evidenceRefs: [],
      },
      {
        code: 'UNVERIFIED_CLAIM',
        severity: 'ERROR',
        message: '승인한 경험에서 확인되지 않는 수치예요. 근거를 연결하거나 표현을 바꿔 주세요.',
        relatedText: '평균 지연을 35% 줄였습니다.',
        evidenceRefs: [],
      },
    ],
    suggestions: ['결과 문장 앞에 어떤 지표를 왜 골랐는지 한 문장을 넣어 보세요.'],
    verifiedClaims: [],
    evidenceRefs: [],
    agentRunId: ids.run,
    createdAt: NOW,
  }
}

function coverLetterDetail() {
  return {
    id: ids.coverLetter,
    job: {
      id: ids.job,
      companyName: '모아테크',
      positionName: '백엔드 개발자',
      title: '플랫폼 백엔드 개발자',
    },
    title: '모아테크 백엔드 개발자 자기소개서',
    status: 'DRAFT',
    questionCount: 1,
    answeredQuestionCount: 1,
    latestVerificationStatus: 'WARNING',
    warningCount: 1,
    canEdit: true,
    canArchive: true,
    canUnarchive: false,
    canFinalize: false,
    version: 1,
    finalizedAt: null,
    archivedAt: null,
    createdAt: NOW,
    updatedAt: NOW,
    questions: [
      {
        id: ids.question,
        questionOrder: 1,
        questionText: '지원 직무를 선택한 이유와 준비 과정을 설명해 주세요.',
        maxLength: 700,
        memo: null,
        currentAnswer: answerVersion(),
        latestVerification: verification(),
        version: 1,
        deletedAt: null,
      },
    ],
  }
}
