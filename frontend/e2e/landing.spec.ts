import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect, test, type Page, type Route } from '@playwright/test'

const userId = '00000000-0000-4000-8000-000000000001'

test('anonymous users understand the service before choosing login or signup', async ({
  page,
}, testInfo) => {
  await installAnonymousSession(page)
  const captureDirectory = resolve(process.cwd(), '..', 'output', 'playwright', 'landing')
  if (process.env.UI_SCREENSHOTS === 'true') mkdirSync(captureDirectory, { recursive: true })

  for (const viewport of [
    { width: 1440, height: 1000, suffix: '1440' },
    { width: 390, height: 844, suffix: '390' },
    { width: 320, height: 760, suffix: '320' },
  ] as const) {
    await page.setViewportSize(viewport)
    await page.goto('/')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: /흩어진 취업 준비를/ })).toBeVisible()
    await expect(page.locator('h1')).toHaveCount(1)
    await expect(page).toHaveTitle('내 경험을, 다음 기회로 | Hiresemble')
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
      ),
      `${viewport.width}px landing에서 가로 overflow가 없어야 합니다.`,
    ).toBe(false)

    if (viewport.width === 1440) {
      const heroMetrics = await page.locator('.landing-hero').evaluate((hero) => {
        const heading = hero.querySelector('h1')
        const copy = hero.querySelector('.landing-hero__copy')
        const lines = Array.from(hero.querySelectorAll('h1 > span'))
        if (!heading || !copy || lines.length !== 2) return null
        const lineHeight = Number.parseFloat(getComputedStyle(heading).lineHeight)
        return {
          headingWidth: heading.getBoundingClientRect().width,
          copyWidth: copy.getBoundingClientRect().width,
          lineHeight,
          lineHeights: lines.map((line) => line.getBoundingClientRect().height),
          lineTops: lines.map((line) => line.getBoundingClientRect().top),
        }
      })
      expect(heroMetrics).not.toBeNull()
      expect(heroMetrics!.headingWidth).toBeGreaterThan(heroMetrics!.copyWidth * 1.8)
      expect(
        heroMetrics!.lineHeights.every((height) => height <= heroMetrics!.lineHeight * 1.15),
      ).toBe(true)
      expect(heroMetrics!.lineTops[1]).toBeGreaterThan(heroMetrics!.lineTops[0])
      await page.locator('.landing-navigation a[href="#journey"]').click()
      await expect(page).toHaveURL(/#journey$/)
      await expect(page.locator('#journey')).toBeInViewport()
    }

    if (process.env.UI_SCREENSHOTS === 'true' && viewport.width !== 320) {
      await page.locator('.landing-demo').scrollIntoViewIfNeeded()
      for (const section of await page.locator('[data-reveal-section]').all()) {
        await section.scrollIntoViewIfNeeded()
        await expect(section).toHaveClass(/is-revealed/)
        await expect(section.locator('[data-reveal-item]').first()).toBeVisible()
      }
      await page.evaluate(() => window.scrollTo(0, 0))
      await page.screenshot({
        path: resolve(captureDirectory, `landing-${viewport.suffix}.png`),
        fullPage: true,
        animations: 'disabled',
      })
      await page.screenshot({
        path: resolve(captureDirectory, `landing-hero-${viewport.suffix}.png`),
        fullPage: false,
        animations: 'disabled',
      })
    }
  }

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '본문으로 건너뛰기' })).toBeFocused()
  await page.locator('.landing-header a[href="/login"]').click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: '로그인', level: 1 })).toBeVisible()
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.screenshot({
      path: resolve(captureDirectory, 'public-login-1440.png'),
      fullPage: true,
      animations: 'disabled',
    })
  }
  await page.locator('.brand-canvas > .auth-brand').click()
  await expect(page).toHaveURL(/\/$/)
  await page.locator('.landing-header a[href="/signup"]').click()
  await expect(page).toHaveURL(/\/signup$/)
  await expect(page.getByRole('heading', { name: '회원가입', level: 1 })).toBeVisible()

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  const reducedScene = await page.locator('.landing-demo').getAttribute('data-scene-index')
  await page.waitForTimeout(2800)
  expect(await page.locator('.landing-demo').getAttribute('data-scene-index')).toBe(reducedScene)
  await expect(page.locator('.landing-demo button')).toHaveCount(0)
  await page.locator('#ai-principles').scrollIntoViewIfNeeded()
  await expect(page.locator('#ai-principles h2')).toBeVisible()
  expect(
    await page
      .locator('#ai-principles h2')
      .evaluate((element) => getComputedStyle(element).opacity),
  ).toBe('1')
  expect(
    await page
      .locator('.landing-hero')
      .evaluate((element) => getComputedStyle(element).animationDuration),
  ).toMatch(/^(?:1e-05|0\.00001)s$/)
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({
      path: resolve(captureDirectory, 'landing-hero-reduced-motion.png'),
      fullPage: false,
      animations: 'disabled',
    })
  }

  await testInfo.attach('public-route-policy', {
    body: Buffer.from('anonymous / -> landing -> login -> landing -> signup'),
    contentType: 'text/plain',
  })
})

test('product demo advances without scroll and pauses offscreen', async ({ page }) => {
  await installAnonymousSession(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  const pageErrors: Error[] = []
  page.on('pageerror', (error) => pageErrors.push(error))
  await page.goto('/')

  const demo = page.locator('.landing-demo')
  await expect(demo).toBeInViewport()
  const firstScene = await demo.getAttribute('data-scene-index')
  await page.waitForFunction(
    (scene) => document.querySelector('.landing-demo')?.getAttribute('data-scene-index') !== scene,
    firstScene,
    { timeout: 5500 },
  )
  await page.waitForTimeout(700)

  const captureDirectory = resolve(process.cwd(), '..', 'output', 'playwright', 'landing')
  if (process.env.UI_SCREENSHOTS === 'true') {
    mkdirSync(captureDirectory, { recursive: true })
    await page.locator('.landing-header').evaluate((header) => {
      header.style.visibility = 'hidden'
    })
    await page.locator('.landing-demo__chrome').screenshot({
      path: resolve(captureDirectory, 'landing-demo-scene-2.png'),
      animations: 'disabled',
    })
    await page.locator('.landing-header').evaluate((header) => {
      header.style.visibility = ''
    })
  }

  await page.waitForFunction(
    (scene) => document.querySelector('.landing-demo')?.getAttribute('data-scene-index') !== scene,
    await demo.getAttribute('data-scene-index'),
    { timeout: 5500 },
  )
  await page.waitForTimeout(700)
  if (process.env.UI_SCREENSHOTS === 'true') {
    await page.locator('.landing-header').evaluate((header) => {
      header.style.visibility = 'hidden'
    })
    await page.locator('.landing-demo__chrome').screenshot({
      path: resolve(captureDirectory, 'landing-demo-scene-3.png'),
      animations: 'disabled',
    })
    await page.locator('.landing-header').evaluate((header) => {
      header.style.visibility = ''
    })
  }

  await page.locator('#ai-principles').scrollIntoViewIfNeeded()
  await expect(demo).not.toBeInViewport()
  await page.waitForTimeout(300)
  const offscreenScene = await demo.getAttribute('data-scene-index')
  await page.waitForTimeout(2800)
  expect(await demo.getAttribute('data-scene-index')).toBe(offscreenScene)

  await demo.scrollIntoViewIfNeeded()
  await expect(demo).toBeInViewport()
  await page.waitForFunction(
    (scene) => document.querySelector('.landing-demo')?.getAttribute('data-scene-index') !== scene,
    offscreenScene,
    { timeout: 5500 },
  )

  await page.goto('/login')
  await page.waitForTimeout(2800)
  expect(pageErrors).toEqual([])
})

test('root navigation waits for authentication and preserves protected returnTo', async ({
  page,
}) => {
  await page.addInitScript(() => {
    const state = window as typeof window & { __landingObserved?: boolean }
    state.__landingObserved = false
    new MutationObserver(() => {
      if (document.querySelector('#landing-heading') !== null) state.__landingObserved = true
    }).observe(document, { childList: true, subtree: true })
  })
  await installDashboardFixture(page, { profile: false, documents: 0, jobs: 0 })

  await page.goto('/')
  await expect(page).toHaveURL(/\/dashboard$/)
  // 대시보드 제목은 낭독기용으로만 남아 있어 화면에는 보이지 않는다.
  await expect(
    page.getByRole('heading', { name: '랜딩 확인 사용자님의 지원 준비 현황' }),
  ).toBeAttached()
  expect(
    await page.evaluate(
      () => (window as typeof window & { __landingObserved?: boolean }).__landingObserved,
    ),
  ).toBe(false)

  await page.unrouteAll({ behavior: 'wait' })
  await installAnonymousSession(page)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/dashboard')
})

for (const scenario of [
  { label: '0-of-3', profile: false, documents: 0, jobs: 0, count: '0 / 3' },
  { label: '1-of-3', profile: true, documents: 0, jobs: 0, count: '1 / 3' },
  { label: '2-of-3', profile: true, documents: 1, jobs: 0, count: '2 / 3' },
  { label: '3-of-3', profile: true, documents: 1, jobs: 1, count: null },
] as const) {
  test(`dashboard checklist renders ${scenario.label} without hiding product status`, async ({
    page,
  }) => {
    await installDashboardFixture(page, scenario)
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto('/dashboard')
    await expect(
      page.getByRole('heading', { name: '랜딩 확인 사용자님의 지원 준비 현황' }),
    ).toBeAttached()
    await expect(page.getByRole('heading', { name: '최근 활동' })).toBeVisible()

    if (scenario.count === null) {
      await expect(page.locator('.start-checklist')).toHaveCount(0)
    } else {
      await expect(page.locator('.start-checklist__heading')).toContainText(
        `${scenario.count} 완료`,
      )
      await expect(page.getByRole('link', { name: /전체 이용 순서 보기/ })).toBeVisible()
    }

    if (scenario.count === '2 / 3' && process.env.UI_SCREENSHOTS === 'true') {
      const output = resolve(process.cwd(), '..', 'output', 'playwright', 'landing')
      mkdirSync(output, { recursive: true })
      await page.screenshot({
        path: resolve(output, 'dashboard-checklist-2-of-3.png'),
        fullPage: true,
        animations: 'disabled',
      })
    }
  })
}

async function installAnonymousSession(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/me', async (route) => {
    await json(
      route,
      {
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
        message: '로그인이 필요합니다.',
        fieldErrors: [],
        requestId: '00000000-0000-4000-8000-000000000099',
      },
      401,
    )
  })
}

async function installDashboardFixture(
  page: Page,
  state: { profile: boolean; documents: number; jobs: number },
): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') {
      return json(route, {
        id: userId,
        email: 'landing-check@example.com',
        displayName: '랜딩 확인 사용자',
      })
    }
    if (path === '/dashboard') {
      return json(route, {
        generatedAt: '2026-08-02T03:00:00Z',
        month: url.searchParams.get('month') ?? '2026-08',
        profile: {
          displayName: '랜딩 확인 사용자',
          legalName: state.profile ? '랜딩 확인 사용자' : null,
          desiredRoles: state.profile ? ['백엔드 개발자'] : [],
          desiredLocations: state.profile ? ['서울'] : [],
          completed: state.profile,
          completionPercent: state.profile ? 100 : 0,
          missingItems: state.profile
            ? []
            : [
                'LEGAL_NAME',
                'DESIRED_ROLE',
                'DESIRED_INDUSTRY',
                'DESIRED_LOCATION',
                'PRIMARY_EDUCATION',
              ],
          primaryEducation: null,
        },
        documents: {
          registeredCount: state.documents,
          processingCount: 0,
          needsActionCount: 0,
        },
        jobs: {
          registeredCount: state.jobs,
          preparingCount: state.jobs,
          submittedCount: 0,
        },
        agentRuns: { activeCount: 0 },
        deadlineDays: [],
      })
    }
    if (path === '/career-guides') return json(route, [])
    if (path === '/profile') {
      return json(route, {
        legalName: state.profile ? '랜딩 확인 사용자' : null,
        introduction: null,
        desiredRoles: state.profile ? ['백엔드 개발자'] : [],
        desiredIndustries: state.profile ? ['IT'] : [],
        desiredLocations: state.profile ? ['서울'] : [],
        expectedGraduationDate: null,
        profileCompleted: state.profile,
        missingCompletionItems: state.profile ? [] : ['LEGAL_NAME'],
        version: 1,
        createdAt: '2026-08-02T00:00:00Z',
        updatedAt: '2026-08-02T00:00:00Z',
      })
    }
    if (path === '/documents') {
      return json(route, pageOf(state.documents > 0 ? [documentSummary()] : [], state.documents))
    }
    if (path === '/jobs') {
      const isRecent =
        url.searchParams.get('status') === null &&
        url.searchParams.get('deadlineWithinDays') === null
      return json(
        route,
        pageOf(isRecent && state.jobs > 0 ? [jobSummary()] : [], isRecent ? state.jobs : 0),
      )
    }
    if (path === '/agent-runs') return json(route, pageOf([], 0))

    return json(route, { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  })
}

function pageOf(items: unknown[], totalElements: number) {
  return { items, page: 0, size: 5, totalElements, totalPages: totalElements > 0 ? 1 : 0 }
}

function documentSummary() {
  return {
    id: '00000000-0000-4000-8000-000000000010',
    documentType: 'RESUME',
    originalFilename: 'resume.pdf',
    displayName: '지원용 이력서.pdf',
    mimeType: 'application/pdf',
    fileSizeBytes: 1024,
    parseStatus: 'PARSED',
    evidenceExtractionStatus: 'SUCCEEDED',
    manualTextProvided: false,
    safeError: null,
    latestAgentRunId: null,
    version: 1,
    uploadedAt: '2026-08-02T00:00:00Z',
    updatedAt: '2026-08-02T00:00:00Z',
  }
}

function jobSummary() {
  return {
    id: '00000000-0000-4000-8000-000000000020',
    companyName: 'Hiresemble',
    title: '백엔드 개발자',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS',
    extractionStatus: 'EXTRACTED',
    submittedAt: null,
    deadlineAt: null,
    deadlineSource: 'USER_ENTERED',
    latestFitScore: null,
    analysisOutdated: false,
    outdatedReasons: [],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 1,
    createdAt: '2026-08-02T00:00:00Z',
    updatedAt: '2026-08-02T00:00:00Z',
  }
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  })
}
