import { expect, test, type Page } from '@playwright/test'

const viewportWidths = [1440, 1024, 768, 390] as const

test('protected app shell stays usable without horizontal overflow at required widths', async ({
  page,
}) => {
  await installAuthenticatedRoutes(page)
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '오늘의 지원 준비를 이어가세요.' })).toBeVisible()

  const progressTrigger = page.getByRole('button', { name: /진행 중인 분석\s*0/ })
  await progressTrigger.click()
  const progressDialog = page.getByRole('dialog', { name: '진행 중인 분석' })
  await expect(progressDialog).toBeVisible()
  await progressDialog.getByRole('button', { name: '진행 중인 분석 닫기' }).click()
  await expect(progressDialog).toBeHidden()
  await expect(progressTrigger).toBeFocused()

  for (const width of viewportWidths) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(hasHorizontalOverflow, `${width}px에서 가로 overflow가 없어야 합니다.`).toBe(false)

    if (width >= 1024) {
      await expect(page.getByLabel('서비스 탐색')).toBeVisible()
      await expect(page.getByRole('button', { name: '주요 메뉴 열기' })).toBeHidden()
      continue
    }

    await expect(page.getByLabel('서비스 탐색')).toBeHidden()
    const trigger = page.getByRole('button', { name: '주요 메뉴 열기' })
    await expect(trigger).toBeVisible()
    await trigger.click()
    const drawer = page.getByRole('dialog', { name: 'Hiresemble 메뉴' })
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
}) => {
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

    if (width === 1024) {
      await expect(page.getByLabel('프로필 메뉴')).toBeVisible()
      await expect(page.getByLabel('프로필 항목 선택')).toBeHidden()
    } else {
      await expect(page.getByLabel('프로필 메뉴')).toBeHidden()
      const sectionSelector = page.getByLabel('프로필 항목 선택')
      await expect(sectionSelector).toBeVisible()
      await sectionSelector.selectOption('/profile/education')
      await page.waitForURL(/\/profile\/education$/)
      await expect(page.getByRole('heading', { name: '학력', level: 2 })).toBeVisible()
      await page.goto('/profile/basic')
    }

    await page.goto('/documents')
    await expect(page.getByRole('heading', { name: '이력서·자료', level: 2 })).toBeVisible()
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
}
