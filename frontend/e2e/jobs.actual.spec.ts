import { expect, test, type Page } from '@playwright/test'

test.describe('P5 actual Backend Job lifecycle', () => {
  test.skip(
    process.env.P5_E2E_ENABLED !== 'true',
    'Requires the isolated P5 Spring runner with PostgreSQL, Fake Job gateway/workflow, Vue and Chromium.',
  )
  test('manual 201 → submit → close → reopen preserves the first submittedAt', async ({ page }) => {
    test.setTimeout(180_000)
    await signup(page, uniqueEmail('manual-status'), 'P5 Manual Status')
    const created = await createJob(page, {
      sourceUrl: manualUrl('manual-status'),
      descriptionText: manualDescription(),
    })
    expect(created.status).toBe(201)
    await expect(page.getByTestId('job-extraction-status')).toContainText('직접 입력 완료')
    await expect(page.getByRole('status')).toContainText('직접 입력한 본문으로 공고를 등록했어요.')
    await expect
      .poll(
        async () =>
          (await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)).automaticAnalysis,
      )
      .toMatchObject({ state: 'LAUNCHED', qualityMode: 'BALANCED' })

    await changeStatus(page, 'SUBMITTED', '서류 제출')
    const submitted = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    expect(submitted.submittedAt).toBeTruthy()

    await changeStatus(page, 'CLOSED', '마감')
    await expect(page.getByText('서류 제출 이력 있음')).toBeVisible()
    await changeStatus(page, 'IN_PROGRESS', '지원 중')

    const reopened = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    expect(reopened.submittedAt).toBe(submitted.submittedAt)
    expect(reopened.closedAt).toBeNull()
    expect(reopened.closedReason).toBeNull()
    await expect(page.getByText('최초 서류 제출')).toBeVisible()
  })

  test('URL-only 202 runs through the Fake HTML gateway to EXTRACTED', async ({ page }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('auto-success'), 'P5 Auto Success')
    const created = await createJob(page, {
      sourceUrl: fixtureUrl(requiredEnv('P5_E2E_SUCCESS_JOB_URL'), 'auto-success'),
    })
    expect(created.status).toBe(202)
    expect(created.runId).toMatch(UUID_PATTERN)
    await expect(page.getByTestId('job-extraction-status')).toContainText('불러오기 완료', {
      timeout: 120_000,
    })

    const detail = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    expect(detail.extractionStatus).toBe('EXTRACTED')
    expect(detail.descriptionSource).toBe('AUTO_EXTRACTED')
    expect(detail.descriptionText?.trim().length).toBeGreaterThan(0)
    await expect(page.locator('.job-document__content')).not.toBeEmpty()

    await expect
      .poll(
        async () =>
          (await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)).automaticAnalysis,
        { timeout: 120_000 },
      )
      .toMatchObject({ state: 'LAUNCHED', qualityMode: 'BALANCED' })

    await page.getByRole('link', { name: '공고 분석', exact: true }).click()
    await expect(page.getByRole('link', { name: '공고 분석', exact: true })).toHaveAttribute(
      'aria-current',
      'page',
    )
    await expect(
      page
        .getByRole('heading', { name: '공고 분석 진행 상황' })
        .or(page.getByText('최신 분석', { exact: true })),
    ).toBeVisible({ timeout: 120_000 })
    await page.getByRole('link', { name: '자기소개서', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/jobs/${created.jobId}/cover-letter$`))
  })

  test('login/empty HTML waits for user and manual input resumes the same Agent Run', async ({
    page,
  }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('manual-resume'), 'P5 Manual Resume')
    const created = await createJob(page, {
      sourceUrl: fixtureUrl(requiredEnv('P5_E2E_EMPTY_JOB_URL'), 'manual-resume'),
    })
    expect(created.status).toBe(202)
    await expect(page.getByTestId('job-extraction-status')).toContainText('본문 입력 필요', {
      timeout: 120_000,
    })
    const runLink = page.getByRole('link', { name: '작업 진행 상세 보기' })
    const originalRunHref = await runLink.getAttribute('href')
    expect(originalRunHref).toContain(created.runId ?? '')

    await page.locator('#job-manual-input').click()
    await page.locator('#job-edit-description').fill(manualDescription())
    await page
      .locator('section')
      .filter({ has: page.getByRole('heading', { name: '공고 정보 편집' }) })
      .getByRole('button', { name: '저장', exact: true })
      .click()

    await expect(page.getByTestId('job-extraction-status')).toContainText('직접 입력 완료', {
      timeout: 120_000,
    })
    await expect(runLink).toHaveAttribute('href', originalRunHref ?? '')
    const detail = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    expect(detail.descriptionSource).toBe('USER_ENTERED')
    expect(detail.descriptionText).toContain('사용자 승인 근거')
    expect(detail.automaticAnalysis).toMatchObject({ state: 'LAUNCHED', qualityMode: 'BALANCED' })
    expect(await latestJobExtractionRunId(page, created.jobId)).toBe(created.runId)
  })

  test('another user receives 404 for the Job and its Agent Run', async ({ browser, page }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('owner'), 'P5 Owner')
    const created = await createJob(page, {
      sourceUrl: fixtureUrl(requiredEnv('P5_E2E_SUCCESS_JOB_URL'), 'owner'),
    })
    expect(created.status).toBe(202)
    expect(created.runId).toMatch(UUID_PATTERN)

    const other = await browser.newContext()
    const otherPage = await other.newPage()
    await signup(otherPage, uniqueEmail('other'), 'P5 Other')
    const csrf = await getJson<{ headerName: string; token: string }>(
      otherPage,
      '/api/v1/auth/csrf',
    )
    expect(await requestStatus(otherPage, `/api/v1/jobs/${created.jobId}`)).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/jobs/${created.jobId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({
          companyName: 'Foreign attempt',
          title: 'Foreign attempt',
          positionName: 'Foreign attempt',
          descriptionText: manualDescription(),
          deadlineAt: null,
          version: 0,
        }),
      }),
    ).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/jobs/${created.jobId}?version=0`, {
        method: 'DELETE',
        headers: { [csrf.headerName]: csrf.token },
      }),
    ).toBe(404)
    expect(await requestStatus(otherPage, `/api/v1/agent-runs/${created.runId}`)).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/agent-runs/${created.runId}/events`, {
        headers: { Accept: 'text/event-stream' },
      }),
    ).toBe(404)
    await other.close()
  })

  test('the deadline scheduler closes an expired Job and exposes DEADLINE_PASSED', async ({
    page,
  }) => {
    test.setTimeout(180_000)
    await signup(page, uniqueEmail('scheduler'), 'P5 Scheduler')
    const expired = new Date(Date.now() - 60_000)
    const created = await createJob(page, {
      sourceUrl: manualUrl('scheduler'),
      descriptionText: manualDescription(),
      deadlineAt: localDateTimeInput(expired),
    })
    expect(created.status).toBe(201)

    const timeout = schedulerTimeout()
    await expect
      .poll(async () => (await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)).status, {
        timeout,
      })
      .toBe('CLOSED')
    const closed = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    expect(closed.closedReason).toBe('DEADLINE_PASSED')
    expect(closed.closedAt).toBeTruthy()

    await page.reload()
    await expect(page.getByTestId('job-business-status')).toContainText('마감')
    await expect(page.getByText('마감일 경과')).toBeVisible()
  })
})

interface CreateInput {
  sourceUrl: string
  descriptionText?: string
  deadlineAt?: string
}

interface CreatedJob {
  status: number
  jobId: string
  runId: string | null
}

interface JobDetail {
  id: string
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'CLOSED'
  extractionStatus:
    | 'QUEUED'
    | 'EXTRACTING'
    | 'EXTRACTED'
    | 'MANUAL_INPUT_PROVIDED'
    | 'NEEDS_MANUAL_INPUT'
    | 'FAILED'
  submittedAt: string | null
  descriptionText: string | null
  descriptionSource: 'AUTO_EXTRACTED' | 'USER_ENTERED' | null
  automaticAnalysis: {
    state:
      'WAITING_FOR_CONTENT' | 'NOT_REQUESTED' | 'PENDING' | 'LAUNCHED' | 'BLOCKED' | 'SUPERSEDED'
    qualityMode: 'BALANCED'
    agentRunId: string | null
    error: { code: string; message: string } | null
  }
  closedAt: string | null
  closedReason: 'DEADLINE_PASSED' | 'USER_CLOSED' | 'URL_INACTIVE' | null
}

async function signup(page: Page, email: string, displayName: string): Promise<void> {
  await page.goto('/signup')
  await page.locator('#signup-email').fill(email)
  await page.locator('#signup-displayName').fill(displayName)
  await page.locator('#signup-password').fill('password-123')
  await page.locator('#signup-passwordConfirm').fill('password-123')
  await page.locator('#signup-termsAgreed').check()
  await page.locator('#signup-aiConsent').check()
  await page.locator('form button[type="submit"]').click()
  await page.waitForURL(/\/onboarding$/)
}

async function createJob(page: Page, input: CreateInput): Promise<CreatedJob> {
  await page.goto('/jobs/new')
  await page.locator('#job-source-url').fill(input.sourceUrl)
  if (input.descriptionText !== undefined) {
    await page.getByText('공고 본문 직접 입력', { exact: true }).click()
    await page.locator('#job-description').fill(input.descriptionText)
  }
  if (input.deadlineAt !== undefined) {
    const [date, time] = input.deadlineAt.split('T')
    if (date === undefined || time === undefined) throw new Error('invalid deadline fixture')
    const [hourText, minute] = time.split(':')
    const hour24 = Number(hourText)
    const hour12 = String(hour24 % 12 || 12).padStart(2, '0')
    await page.locator('#job-deadline-date').fill(date)
    await page.locator('#job-deadline-period').selectOption(hour24 >= 12 ? 'PM' : 'AM')
    await page.locator('#job-deadline-time').selectOption(`${hour12}:${minute}`)
  }
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/jobs' &&
      response.request().method() === 'POST' &&
      (response.status() === 201 || response.status() === 202),
  )
  await page.locator('#job-create-submit').click()
  const response = await responsePromise
  const body = (await response.json()) as {
    jobId: string
    agentRunId: string | null
  }
  await expect(page).toHaveURL(new RegExp(`/jobs/${body.jobId}/overview(?:\\?.*)?$`))
  return {
    status: response.status(),
    jobId: body.jobId,
    runId: body.agentRunId,
  }
}

async function changeStatus(page: Page, status: JobDetail['status'], label: string): Promise<void> {
  const select = page.locator('#job-status-select')
  const submit = page.locator('#job-status-submit')
  await expect(submit).toHaveText('상태 변경')
  await select.selectOption(status)
  await expect(select).toHaveValue(status)
  await expect(submit).toBeEnabled()
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.endsWith('/status') &&
      response.request().method() === 'PATCH' &&
      response.status() === 200,
  )
  await submit.click()
  expect((await responsePromise).status()).toBe(200)
  await expect(page.getByTestId('job-business-status')).toContainText(label)
  await expect(submit).toHaveText('상태 변경')
}

async function latestJobExtractionRunId(page: Page, jobId: string): Promise<string | null> {
  const response = await getJson<{ items: Array<{ id: string }> }>(
    page,
    `/api/v1/agent-runs?workflowType=JOB_POSTING_EXTRACTION&resourceType=JOB&resourceId=${jobId}&page=0&size=1&sort=queuedAt,desc`,
  )
  return response.items[0]?.id ?? null
}

async function getJson<T>(page: Page, path: string): Promise<T> {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'include' })
    if (!response.ok) throw new Error(`GET ${requestPath} failed with ${response.status}`)
    return response.json() as Promise<T>
  }, path)
}

async function requestStatus(
  page: Page,
  path: string,
  options: { method?: string; headers?: Record<string, string>; body?: string } = {},
): Promise<number> {
  return page.evaluate(
    async ({ requestPath, requestOptions }) => {
      const response = await fetch(requestPath, {
        method: requestOptions.method,
        headers: requestOptions.headers,
        body: requestOptions.body,
        credentials: 'include',
      })
      return response.status
    },
    { requestPath: path, requestOptions: options },
  )
}

function fixtureUrl(base: string, scope: string): string {
  const url = new URL(base.replace('{nonce}', uniqueToken(scope)))
  if (!base.includes('{nonce}')) url.searchParams.set('e2eNonce', uniqueToken(scope))
  return url.toString()
}

function manualUrl(scope: string): string {
  return `https://manual.p5-e2e.invalid/${scope}/${uniqueToken(scope)}`
}

function manualDescription(): string {
  return '사용자 승인 근거를 바탕으로 안정적인 API를 설계하고 운영한 백엔드 개발자 공고입니다. '.repeat(
    8,
  )
}

function localDateTimeInput(value: Date): string {
  const rounded = new Date(value)
  rounded.setMinutes(rounded.getMinutes() < 30 ? 0 : 30, 0, 0)
  const local = new Date(rounded.getTime() - rounded.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function schedulerTimeout(): number {
  const value = Number(process.env.P5_E2E_SCHEDULER_TIMEOUT_MS ?? '60000')
  if (!Number.isFinite(value) || value < 1_000) {
    throw new Error('P5_E2E_SCHEDULER_TIMEOUT_MS must be at least 1000.')
  }
  return value
}

function requiredEnv(name: 'P5_E2E_SUCCESS_JOB_URL' | 'P5_E2E_EMPTY_JOB_URL'): string {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required when P5_E2E_ENABLED=true.`)
  return value
}

function uniqueEmail(scope: string): string {
  return `p5-${scope}-${uniqueToken(scope)}@example.com`
}

function uniqueToken(scope: string): string {
  return `${scope}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f-]{27}$/i
