import { expect, test, type Page } from '@playwright/test'

const viewportWidths = [1440, 1024, 768, 390] as const

test('protected app shell stays usable without horizontal overflow at required widths', async ({
  page,
}, testInfo) => {
  await installAuthenticatedRoutes(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/dashboard')
  await expect(
    page.getByRole('heading', { name: '반응형 확인 사용자님의 지원 준비 현황' }),
  ).toBeVisible()
  const titleName = page.locator('.dashboard-title__name')
  const titleSuffix = page.locator('.dashboard-title__suffix')
  expect(await titleName.evaluate((element) => getComputedStyle(element).color)).not.toBe(
    await titleSuffix.evaluate((element) => getComputedStyle(element).color),
  )
  await expect(page.getByRole('heading', { name: '지원 준비 요약' })).toHaveClass(/sr-only/)
  await expect(page.getByText('한눈에 보기', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '오늘로 이동' })).toHaveCount(0)
  await expect(page.getByText('모든 날짜와 시각은 Asia/Seoul 기준으로 표시합니다.')).toHaveCount(0)
  const dashboardShortcuts = page.getByRole('complementary', { name: '대시보드 바로가기' })
  expect(
    await page
      .getByRole('heading', { name: '공고 마감 캘린더' })
      .evaluate((element) => getComputedStyle(element).fontFamily),
  ).toContain('Noto Sans KR Variable')
  await expect(dashboardShortcuts.getByRole('link')).toHaveCount(4)
  expect(await dashboardShortcuts.evaluate((element) => getComputedStyle(element).position)).toBe(
    'sticky',
  )
  const dashboardContent = page.locator('.dashboard-content')
  const dashboardHeader = page.locator('.dashboard > .page-header')
  const dashboardActions = dashboardHeader.locator('.page-header__actions')
  const [contentBox, headerBox, actionsBox, shortcutsBox] = await Promise.all([
    dashboardContent.boundingBox(),
    dashboardHeader.boundingBox(),
    dashboardActions.boundingBox(),
    dashboardShortcuts.boundingBox(),
  ])
  expect(contentBox).not.toBeNull()
  expect(headerBox).not.toBeNull()
  expect(actionsBox).not.toBeNull()
  expect(shortcutsBox).not.toBeNull()
  expect(Math.abs((contentBox?.x ?? 0) + (contentBox?.width ?? 0) / 2 - 720)).toBeLessThan(1)
  expect(Math.abs((headerBox?.x ?? 0) - (contentBox?.x ?? 0))).toBeLessThan(1)
  expect(
    Math.abs(
      (headerBox?.x ?? 0) +
        (headerBox?.width ?? 0) -
        ((contentBox?.x ?? 0) + (contentBox?.width ?? 0)),
    ),
  ).toBeLessThan(1)
  expect(
    Math.abs(
      (actionsBox?.x ?? 0) +
        (actionsBox?.width ?? 0) -
        ((contentBox?.x ?? 0) + (contentBox?.width ?? 0)),
    ),
  ).toBeLessThan(1)
  expect(shortcutsBox?.x ?? 0).toBeGreaterThan((contentBox?.x ?? 0) + (contentBox?.width ?? 0))
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await expect
    .poll(async () => (await dashboardShortcuts.boundingBox())?.y ?? -1)
    .toBeGreaterThanOrEqual(60)
  await page.evaluate(() => window.scrollTo(0, 0))
  await expect(page.locator('#app-content')).toBeFocused()
  expect(
    await page.locator('#app-content').evaluate((element) => ({
      outline: getComputedStyle(element).outlineStyle,
      boxShadow: getComputedStyle(element).boxShadow,
    })),
  ).toEqual({ outline: 'none', boxShadow: 'none' })
  await dashboardShortcuts.getByRole('link', { name: '마감 캘린더' }).click()
  await expect(page).toHaveURL(/#dashboard-deadlines$/)
  await expect(page.locator('#dashboard-deadlines')).toBeInViewport()

  const currentDate = page.getByRole('button', { name: /^2026-08-02,/ })
  const nextDate = page.getByRole('button', { name: /^2026-08-03,/ })
  await currentDate.click()
  await nextDate.hover()
  const currentDateBox = await currentDate.boundingBox()
  const nextDateBox = await nextDate.boundingBox()
  expect(currentDateBox).not.toBeNull()
  expect(nextDateBox).not.toBeNull()
  expect(
    (nextDateBox?.x ?? 0) - ((currentDateBox?.x ?? 0) + (currentDateBox?.width ?? 0)),
  ).toBeGreaterThan(3)
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.locator('.calendar-card').screenshot({
      path: testInfo.outputPath('calendar-hover-1440.png'),
    })
  }

  const deadlineDate = page.getByRole('button', { name: /^2026-08-15,/ })
  await expect(deadlineDate).toContainText('1건')
  await deadlineDate.click()
  await expect(page.locator('.deadline-detail--desktop')).toContainText('플랫폼 엔지니어')

  const guideTrigger = page.locator('.guide-card').first()
  await guideTrigger.click()
  const guideDialog = page.getByRole('dialog', { name: '공고 분석 전에 확인할 항목' })
  await expect(guideDialog).toBeVisible()
  await expect(guideDialog.locator('.guide-modal__content p')).toHaveCount(3)
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.screenshot({ path: testInfo.outputPath('guide-modal-1440.png') })
  }
  await page.keyboard.press('Escape')
  await expect(guideDialog).toBeHidden()
  await expect(guideTrigger).toBeFocused()
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({
      path: testInfo.outputPath('dashboard-1440.png'),
      fullPage: true,
    })
  }

  const progressTrigger = page.getByRole('button', { name: /진행 중인 분석\s*0/ })
  await progressTrigger.click()
  const progressDialog = page.getByRole('dialog', { name: '진행 중인 분석' })
  await expect(progressDialog).toBeVisible()
  await progressDialog.getByRole('button', { name: '진행 중인 분석 닫기' }).click()
  await expect(progressDialog).toBeHidden()
  await expect(progressTrigger).toBeFocused()

  for (const width of [1920, ...viewportWidths.slice(0, 1), 1280, ...viewportWidths.slice(1)]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
    await page.evaluate(() => window.scrollTo(0, 0))

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(hasHorizontalOverflow, `${width}px에서 가로 overflow가 없어야 합니다.`).toBe(false)
    if ((width === 1024 || width === 390) && process.env.UI_SCREENSHOTS === 'true') {
      await page.screenshot({
        path: testInfo.outputPath(`dashboard-${width}.png`),
        fullPage: true,
      })
    }

    if (width >= 1120) {
      await expect(page.getByLabel('서비스 탐색')).toBeVisible()
      await expect(page.getByLabel('모바일 주요 메뉴')).toBeHidden()
      continue
    }

    await expect(page.getByLabel('서비스 탐색')).toBeHidden()
    await expect(page.getByLabel('모바일 주요 메뉴')).toBeVisible()
    const trigger = page.getByRole('button', { name: '더보기' })
    await expect(trigger).toBeVisible()
    await trigger.click()
    const drawer = page.getByRole('dialog', { name: '더보기' })
    await expect(drawer).toBeVisible()
    const bounds = await drawer.boundingBox()
    expect(bounds?.width).toBeLessThanOrEqual(width)
    expect(bounds?.height).toBeLessThanOrEqual(width === 390 ? 844 : 900)
    await page.keyboard.press('Escape')
    await expect(drawer).toBeHidden()
    await expect(trigger).toBeFocused()
  }
})

test('public authentication shell keeps the form readable at desktop and mobile widths', async ({
  page,
}) => {
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
        message: '로그인이 필요합니다.',
        fieldErrors: [],
        traceId: 'ui-shell',
      }),
    })
  })
  await page.goto('/login')

  for (const width of [1440, 390] as const) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible()
    await expect(page.getByLabel('이메일')).toBeVisible()
    await expect(page.getByLabel('비밀번호', { exact: true })).toBeVisible()
    expect(
      await page.locator('h1, h2').evaluateAll((headings) => headings.map((item) => item.tagName)),
    ).toEqual(['H1', 'H2'])
    const passwordToggleBox = await page
      .getByRole('button', { name: '비밀번호 보기', exact: true })
      .boundingBox()
    expect(passwordToggleBox?.width).toBeGreaterThanOrEqual(44)
    expect(passwordToggleBox?.height).toBeGreaterThanOrEqual(44)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${width}px 인증 화면에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)

    await page.goto('/signup')
    await expect(page.getByRole('heading', { name: '회원가입' })).toBeVisible()
    await expect(
      page.getByText('다른 곳에서 사용하지 않는 비밀번호를 입력해 주세요.'),
    ).toBeVisible()
    await page.getByRole('button', { name: '비밀번호 보기', exact: true }).click()
    await expect(page.getByRole('button', { name: '비밀번호 숨기기', exact: true })).toBeVisible()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${width}px 회원가입 화면에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)
    await page.goto('/login')
  }

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  expect(
    await page
      .locator('.brand-orbit__ring--outer')
      .evaluate((element) => getComputedStyle(element).animationName),
  ).toBe('none')
})

test('profile suggestions and document registration stay keyboard-ready and responsive', async ({
  page,
}, testInfo) => {
  await installAuthenticatedRoutes(page)
  await page.route('**/api/v1/profile', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        legalName: '반응형 확인 사용자',
        introduction: '',
        desiredRoles: [],
        desiredIndustries: [],
        desiredLocations: [],
        expectedGraduationDate: null,
        profileCompleted: false,
        missingCompletionItems: [
          'DESIRED_ROLE',
          'DESIRED_INDUSTRY',
          'DESIRED_LOCATION',
          'PRIMARY_EDUCATION',
        ],
        version: 1,
        createdAt: '2026-07-28T00:00:00Z',
        updatedAt: '2026-07-28T00:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/documents?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    })
  })
  await page.route('**/api/v1/profile/educations?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    })
  })

  await page.goto('/profile/basic')
  await page.setViewportSize({ width: 1440, height: 1000 })
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.screenshot({
      path: testInfo.outputPath('profile-basic-1440.png'),
      fullPage: true,
    })
  }
  const roleInput = page.getByRole('combobox', { name: '희망 직무' })
  await roleInput.fill('프론트')
  const roleSuggestion = page.getByRole('option', { name: /프론트엔드 개발자/ })
  await expect(roleSuggestion).toBeVisible()
  await roleInput.press('ArrowDown')
  await expect(roleSuggestion).toBeFocused()
  await roleSuggestion.press('Enter')
  await expect(page.getByLabel('희망 직무 목록')).toContainText('프론트엔드 개발자')

  for (const width of [1024, 390] as const) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${width}px 프로필 화면에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)
    if (width === 390 && process.env.UI_SCREENSHOTS === 'true') {
      await page.screenshot({
        path: testInfo.outputPath('profile-basic-390.png'),
        fullPage: true,
      })
    }

    if (width === 1024) {
      await expect(page.getByLabel('프로필 메뉴')).toBeVisible()
      await expect(page.getByLabel('프로필 항목 선택')).toBeHidden()
    } else {
      await expect(page.getByLabel('프로필 메뉴')).toBeHidden()
      const sectionSelector = page.getByLabel('프로필 항목 선택')
      await expect(sectionSelector).toBeVisible()
      await sectionSelector.selectOption('/profile/education')
      await page.waitForURL(/\/profile\/education$/)
      await expect(page.getByRole('heading', { name: '학력', level: 1 })).toBeVisible()
      await page.goto('/profile/basic')
    }

    await page.goto('/documents')
    await expect(page.getByRole('heading', { name: '이력서·자료', level: 1 })).toBeVisible()
    await expect(page.getByLabel('자료 등록 순서')).toContainText('내용 분석')
    await page.locator('#document-file').setInputFiles({
      name: '지원용-이력서.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('fixture resume'),
    })
    await expect(page.getByText('지원용-이력서.txt')).toBeVisible()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${width}px 자료 등록 화면에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)

    if (width === 1024) await page.goto('/profile/basic')
  }
})

async function installAuthenticatedRoutes(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: '00000000-0000-4000-8000-000000000001',
        email: 'responsive@example.com',
        displayName: '반응형 확인 사용자',
      }),
    })
  })
  await page.route('**/api/v1/dashboard?*', async (route) => {
    const month = new URL(route.request().url()).searchParams.get('month') ?? '2026-08'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        generatedAt: '2026-08-02T03:00:00Z',
        month,
        profile: {
          displayName: '반응형 확인 사용자',
          legalName: null,
          desiredRoles: [],
          desiredLocations: [],
          completed: false,
          completionPercent: 0,
          missingItems: [
            'LEGAL_NAME',
            'DESIRED_ROLE',
            'DESIRED_INDUSTRY',
            'DESIRED_LOCATION',
            'PRIMARY_EDUCATION',
          ],
          primaryEducation: null,
        },
        documents: { registeredCount: 1, processingCount: 0, needsActionCount: 0 },
        jobs: { registeredCount: 2, preparingCount: 1, submittedCount: 1 },
        agentRuns: { activeCount: 0 },
        deadlineDays:
          month === '2026-08'
            ? [
                {
                  date: '2026-08-15',
                  count: 1,
                  items: [
                    {
                      id: '00000000-0000-4000-8000-000000000099',
                      companyName: '하이어셈블랩',
                      title: '플랫폼 엔지니어',
                      positionName: '플랫폼 엔지니어',
                      status: 'IN_PROGRESS',
                      deadlineAt: '2026-08-15T05:30:00Z',
                    },
                  ],
                },
              ]
            : [],
      }),
    })
  })
  await page.route('**/api/v1/career-guides', async (route) => {
    const topics = [
      ['공고 분석', '공고 분석 전에 확인할 항목'],
      ['경험 정리', '경험을 자기소개서 소재로 정리하는 방법'],
      ['강점 선택', '지원 직무에 맞는 강점 선택 방법'],
      ['면접 준비', '면접 답변을 간결하게 구성하는 방법'],
      ['최종 점검', '마감 전 최종 점검 체크리스트'],
    ]
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        topics.map(([category, title], index) => ({
          id: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
          status: 'PUBLISHED',
          displayOrder: (index + 1) * 10,
          category,
          title,
          summary: '핵심을 빠르게 확인하고 내 지원 준비에 바로 적용해 보세요.',
          body: '담당 업무에서 반복되는 동사와 기대 결과를 찾아 역할의 중심을 정리해 보세요. 직무명만으로 판단하지 않고 실제로 해결할 문제를 확인하는 것이 먼저입니다.\n\n필수 조건과 우대 조건을 나누고, 각 항목에 연결할 수 있는 내 경험의 행동과 결과를 한 줄씩 남겨 보세요. 사용하지 않은 기술은 과장하지 않고 비슷한 문제를 해결한 근거를 찾습니다.\n\n마지막으로 근무 조건과 마감 시각을 다시 확인하고, 자기소개서와 면접에서 강조할 핵심 업무 세 가지를 골라 준비에 활용하세요.',
          publishedAt: '2026-08-01T00:00:00Z',
          version: 2,
        })),
      ),
    })
  })
  await page.route('**/api/v1/agent-runs**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    })
  })
  await page.route('**/api/v1/profile', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        legalName: null,
        introduction: null,
        desiredRoles: [],
        desiredIndustries: [],
        desiredLocations: [],
        expectedGraduationDate: null,
        profileCompleted: false,
        missingCompletionItems: [
          'LEGAL_NAME',
          'DESIRED_ROLE',
          'DESIRED_INDUSTRY',
          'DESIRED_LOCATION',
          'PRIMARY_EDUCATION',
        ],
        version: 0,
        createdAt: '2026-07-28T00:00:00Z',
        updatedAt: '2026-07-28T00:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/documents?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: '00000000-0000-4000-8000-000000000010',
            documentType: 'RESUME',
            displayName: '지원용 이력서.pdf',
            mimeType: 'application/pdf',
            fileSizeBytes: 1024,
            parseStatus: 'PARSED',
            evidenceExtractionStatus: 'SUCCEEDED',
            manualTextProvided: false,
            safeError: null,
            latestAgentRunId: null,
            version: 1,
            uploadedAt: '2026-07-27T00:00:00Z',
            updatedAt: '2026-07-28T04:00:00Z',
          },
        ],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })
  await page.route('**/api/v1/jobs?*', async (route) => {
    const status = new URL(route.request().url()).searchParams.get('status')
    const baseJob = {
      id: '00000000-0000-4000-8000-000000000020',
      companyName: 'Hiresemble',
      title: '백엔드 개발자',
      positionName: '백엔드 개발자',
      extractionStatus: 'EXTRACTED',
      submittedAt: null,
      deadlineAt: '2026-08-02T14:59:59Z',
      deadlineSource: 'USER_ENTERED',
      latestFitScore: null,
      analysisOutdated: false,
      outdatedReasons: [],
      coverLetterStatus: null,
      interviewPreparationCount: 0,
      version: 1,
      createdAt: '2026-07-26T00:00:00Z',
      updatedAt: '2026-07-28T02:00:00Z',
    }
    const items =
      status === 'SUBMITTED'
        ? [{ ...baseJob, status: 'SUBMITTED', submittedAt: '2026-07-28T01:00:00Z' }]
        : [{ ...baseJob, status: 'IN_PROGRESS' }]
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items,
        page: 0,
        size: 5,
        totalElements: status === null ? 2 : 1,
        totalPages: 1,
      }),
    })
  })
}
