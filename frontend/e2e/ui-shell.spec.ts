import { expect, test, type Page } from '@playwright/test'

const viewportWidths = [1440, 1024, 768, 390] as const

test('protected app shell stays usable without horizontal overflow at required widths', async ({
  page,
}) => {
  await installAuthenticatedRoutes(page)
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: /지원 준비 공간/ })).toBeVisible()

  const progressTrigger = page.getByRole('button', { name: '진행 작업 0' })
  await progressTrigger.click()
  const progressDialog = page.getByRole('dialog', { name: 'Agent Run 진행 현황' })
  await expect(progressDialog).toBeVisible()
  await progressDialog.getByRole('button', { name: '진행 작업 닫기' }).click()
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
    await expect(page.getByLabel('비밀번호')).toBeVisible()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${width}px 인증 화면에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)
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
