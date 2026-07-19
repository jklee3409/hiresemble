import { expect, test, type Page, type Route } from '@playwright/test'

const RUNNING_ID = '20000000-0000-4000-8000-000000000001'
const WAITING_ID = '20000000-0000-4000-8000-000000000002'
const FAILED_ID = '20000000-0000-4000-8000-000000000003'
const SUCCESSOR_ID = '20000000-0000-4000-8000-000000000004'
const ROOT_ID = '20000000-0000-4000-8000-000000000010'
const STEP_ID = '20000000-0000-4000-8000-000000000020'

test('snapshot → progress → disconnect → 1/2/5 reconnect → polling terminal recovery', async ({
  page,
}) => {
  test.setTimeout(45_000)
  let streamRequests = 0
  let detailRequests = 0
  let listRequests = 0
  let terminalDelivered = false

  await installCommonRoutes(page, async (route, url) => {
    if (url.pathname === '/api/v1/agent-runs') {
      listRequests += 1
      const summary = runDetail(RUNNING_ID, terminalDelivered ? 'SUCCEEDED' : 'RUNNING', {
        progressPercent: terminalDelivered ? 100 : 20,
        stateVersion: terminalDelivered ? 5 : 1,
        completedAt: terminalDelivered ? '2026-07-19T00:00:20Z' : null,
        cancellable: !terminalDelivered,
      })
      await json(route, pageResponse([summary], 7))
      return true
    }

    if (url.pathname === `/api/v1/agent-runs/${RUNNING_ID}`) {
      detailRequests += 1
      if (detailRequests === 1) {
        await json(route, runDetail(RUNNING_ID, 'RUNNING'))
      } else {
        terminalDelivered = true
        await json(
          route,
          runDetail(RUNNING_ID, 'SUCCEEDED', {
            progressPercent: 100,
            currentStep: 'APPLY_FIXTURE',
            stateVersion: 5,
            actualCostUsd: 0.025,
            completedAt: '2026-07-19T00:00:20Z',
            durationMs: 20_000,
            cancellable: false,
          }),
        )
      }
      return true
    }

    if (url.pathname === `/api/v1/agent-runs/${RUNNING_ID}/events`) {
      streamRequests += 1
      if (streamRequests > 1) {
        await route.abort('connectionfailed')
        return true
      }

      const snapshot = runDetail(RUNNING_ID, 'RUNNING', { stateVersion: 2 })
      const body = [
        sse('snapshot', 2, {
          agentRunId: RUNNING_ID,
          stateVersion: 2,
          occurredAt: '2026-07-19T00:00:02Z',
          run: snapshot,
        }),
        sse('progress', 3, {
          agentRunId: RUNNING_ID,
          stateVersion: 3,
          occurredAt: '2026-07-19T00:00:03Z',
          status: 'RUNNING',
          currentStep: 'TRANSFORM_FIXTURE',
          progressPercent: 45,
          actualCostUsd: 0.02,
        }),
        sse('step', 4, {
          agentRunId: RUNNING_ID,
          stateVersion: 4,
          occurredAt: '2026-07-19T00:00:04Z',
          step: {
            id: STEP_ID,
            stepKey: 'TRANSFORM_FIXTURE',
            scopeKey: null,
            stepOrder: 2,
            status: 'SUCCEEDED',
            attempt: 1,
            maxAttempts: 3,
            startedAt: '2026-07-19T00:00:02Z',
            completedAt: '2026-07-19T00:00:04Z',
            safeError: null,
          },
        }),
      ].join('')
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'Cache-Control': 'no-cache' },
        body,
      })
      return true
    }

    return false
  })

  await page.goto('/agent-runs')
  await expect(page.getByRole('heading', { name: 'Agent Run 작업 기록' })).toBeVisible()
  await expect(page.getByRole('button', { name: '진행 작업 7' })).toBeVisible()
  await page.getByRole('link', { name: '상세 보기' }).click()

  await expect(page.getByText('TRANSFORM_FIXTURE', { exact: true })).toBeVisible()
  await expect(page.getByRole('progressbar')).toHaveAttribute('value', '45')
  await expect(page.getByText('시도 1/3')).toBeVisible()
  await expect(page.getByText(/마지막 상태를 유지하며 다시 연결/)).toBeVisible()
  await expect(page.getByText(/5초마다 서버 상태를 확인/)).toBeVisible({ timeout: 12_000 })
  await expect(page.getByRole('heading', { name: '완료' })).toBeVisible({ timeout: 9_000 })

  expect(streamRequests).toBe(4)
  expect(detailRequests).toBeGreaterThanOrEqual(2)
  expect(listRequests).toBeGreaterThanOrEqual(2)

  const settledDetailRequests = detailRequests
  await page.waitForTimeout(5_500)
  expect(detailRequests).toBe(settledDetailRequests)
})

test('WAITING action, failed retry, active cancel, and logout close remain server-driven', async ({
  page,
}) => {
  test.setTimeout(20_000)
  let retryKey = ''
  let cancelVersion: number | null = null
  let activeStreamRequests = 0

  await installCommonRoutes(page, async (route, url) => {
    if (url.pathname === '/api/v1/agent-runs') {
      await json(route, pageResponse([]))
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${WAITING_ID}`) {
      await json(
        route,
        runDetail(WAITING_ID, 'WAITING_USER', {
          requiredUserAction: {
            type: 'PROVIDE_DOCUMENT_TEXT',
            resource: null,
            route: '/profile/basic',
            message: '프로필 정보를 확인해 주세요.',
          },
          retryable: false,
          cancellable: true,
        }),
      )
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${FAILED_ID}`) {
      await json(
        route,
        runDetail(FAILED_ID, 'FAILED', {
          retryable: true,
          cancellable: false,
          completedAt: '2026-07-19T00:00:10Z',
          safeError: { code: 'SAFE_FAILURE', message: '다시 시도할 수 있습니다.' },
        }),
      )
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${SUCCESSOR_ID}`) {
      await json(route, runDetail(SUCCESSOR_ID, 'RUNNING'))
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${FAILED_ID}/retry`) {
      retryKey = (await route.request().allHeaders())['idempotency-key'] ?? ''
      await json(
        route,
        {
          agentRunId: SUCCESSOR_ID,
          status: 'QUEUED',
          resourceType: null,
          resourceId: null,
          replayed: false,
        },
        202,
      )
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${RUNNING_ID}`) {
      await json(route, runDetail(RUNNING_ID, 'RUNNING', { stateVersion: 3 }))
      return true
    }
    if (url.pathname === `/api/v1/agent-runs/${RUNNING_ID}/cancel`) {
      cancelVersion = ((await route.request().postDataJSON()) as { stateVersion: number })
        .stateVersion
      await json(route, runDetail(RUNNING_ID, 'RUNNING', { stateVersion: 4 }), 202)
      return true
    }
    if (url.pathname.endsWith('/events')) {
      if (url.pathname.includes(RUNNING_ID)) activeStreamRequests += 1
      await route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' })
      return true
    }
    return false
  })

  await page.goto(`/agent-runs/${WAITING_ID}`)
  await expect(page.getByText('프로필 정보를 확인해 주세요.')).toBeVisible()
  await expect(page.getByRole('link', { name: '필요한 정보 입력하기' })).toHaveAttribute(
    'href',
    '/profile/basic',
  )
  await expect(page.getByRole('button', { name: '재시도' })).toHaveCount(0)

  await page.goto(`/agent-runs/${FAILED_ID}`)
  await page.getByRole('button', { name: '재시도' }).click()
  await expect(page).toHaveURL(new RegExp(`${SUCCESSOR_ID}$`))
  expect(retryKey).toMatch(/^[A-Za-z0-9._:-]{8,128}$/)

  await page.goto(`/agent-runs/${RUNNING_ID}`)
  await page.getByRole('button', { name: '실행 취소' }).click()
  expect(cancelVersion).toBe(3)

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)
  const requestsAfterLogout = activeStreamRequests
  await page.waitForTimeout(1_500)
  expect(activeStreamRequests).toBe(requestsAfterLogout)
})

async function installCommonRoutes(
  page: Page,
  handleAgentRun: (route: Route, url: URL) => Promise<boolean>,
): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    if (await handleAgentRun(route, url)) return

    if (url.pathname === '/api/v1/auth/me') {
      await json(route, {
        id: '30000000-0000-4000-8000-000000000001',
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
    if (url.pathname === '/api/v1/auth/logout') {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    await json(route, { message: `Unhandled fixture route: ${url.pathname}` }, 500)
  })
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function pageResponse(items: unknown[], totalElements = items.length) {
  return { items, page: 0, size: 20, totalElements, totalPages: totalElements ? 1 : 0 }
}

function runDetail(id: string, status: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    workflowType: 'JOB_ANALYSIS',
    resourceType: null,
    resourceId: null,
    status,
    currentStep: 'LOAD_FIXTURE',
    progressPercent: 20,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: 'LOW_COST',
    estimatedCostUsd: 0.03,
    reservedCostUsd: 0.03,
    actualCostUsd: 0.01,
    retryable: false,
    cancellable: true,
    requiredUserAction: null,
    stateVersion: 1,
    queuedAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:01Z',
    retryOfRunId: null,
    rootRunId: ROOT_ID,
    runAttemptNo: 1,
    durationMs: null,
    startedAt: '2026-07-19T00:00:01Z',
    completedAt: null,
    safeError: null,
    partialResult: null,
    steps: [],
    ...overrides,
  }
}

function sse(event: string, id: number, data: unknown): string {
  return `id: ${id}\nevent: ${event}\ndata: ${JSON.stringify(data)}\n\n`
}
