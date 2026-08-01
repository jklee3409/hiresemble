import { expect, test, type Browser, type Page } from '@playwright/test'

test.describe('P6 actual Backend Job analysis lifecycle', () => {
  test.skip(
    process.env.P6_E2E_ENABLED !== 'true',
    'Requires the isolated P6 Spring runner with PostgreSQL, Fake AI, Vue, SSE and Chromium.',
  )

  test('analysis → exact reuse → OUTDATED → reanalysis preserves history and ownership', async ({
    browser,
    page,
  }) => {
    test.setTimeout(300_000)
    await signup(page, uniqueEmail('owner'), 'P6 Owner')
    const seeded = await seedProfileAndCareer(page)
    const created = await createManualJob(page, 'success', analyzableDescription())

    await page.goto(`/jobs/${created.jobId}/analysis`)
    await expect(page.getByText('아직 저장된 공고 분석이 없어요.')).toBeVisible()
    const firstRunId = await startAnalysis(page, '분석 시작')
    await expect(page.locator('#analysis-result-heading')).toHaveText('분석 버전 1', {
      timeout: 120_000,
    })
    await expect(page.getByText('지원 가능', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('100.00점', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('P6 Career Fixture', { exact: true }).first()).toBeVisible()
    await expect(
      page.getByText(
        '적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.',
        { exact: true },
      ),
    ).toHaveCount(3)
    await expect(page.getByRole('heading', { name: '과거 분석 이력' })).toBeVisible()
    await expectNoHorizontalOverflow(page)

    const job = await getJson<JobDetail>(page, `/api/v1/jobs/${created.jobId}`)
    const reusedRun = await postJson<RunAccepted>(
      page,
      `/api/v1/jobs/${created.jobId}/analysis`,
      {
        qualityMode: 'BALANCED',
        forceReanalyze: false,
        jobVersion: job.version,
      },
      `p6-reuse-${uniqueToken('reuse')}`,
    )
    expect(reusedRun.status).toBe(202)
    await expect
      .poll(
        async () =>
          (await getJson<AgentRun>(page, `/api/v1/agent-runs/${reusedRun.body.agentRunId}`)).status,
        { timeout: 120_000 },
      )
      .toBe('SUCCEEDED')
    expect(
      (await getJson<AnalysisPage>(page, `/api/v1/jobs/${created.jobId}/analyses`)).totalElements,
    ).toBe(1)

    await updateProfile(page)
    await updateCareer(page, seeded.career)
    await page.reload()
    await expect(page.getByText('프로필 정보가 변경됨', { exact: true })).toBeVisible()
    await expect(page.getByText('확인한 경험이 변경됨', { exact: true })).toBeVisible()
    await expect(page.locator('#analysis-result-heading')).toHaveText('분석 버전 1')

    const secondRunId = await startAnalysis(page, '재분석하기')
    await expect(page.locator('#analysis-result-heading')).toHaveText('분석 버전 2', {
      timeout: 120_000,
    })
    await expect(page.getByText('2개 버전')).toBeVisible()
    await expect(page.getByText('현재 최신', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('프로필 정보가 변경됨', { exact: true })).toHaveCount(0)
    await expect(page.getByText('확인한 경험이 변경됨', { exact: true })).toHaveCount(0)

    const ownerEvidence = await getJson<EvidencePage>(
      page,
      '/api/v1/profile/evidence?verificationStatus=VERIFIED&page=0&size=20&sort=updatedAt,desc',
    )
    expect(ownerEvidence.items).toHaveLength(1)
    await verifyOwnerIsolation(
      browser,
      created.jobId,
      ownerEvidence.items[0]!,
      firstRunId,
      secondRunId,
    )
  })

  test('zero extracted criteria fails safely without creating an analysis row', async ({
    page,
  }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('insufficient'), 'P6 Insufficient')
    const created = await createManualJob(page, 'insufficient', insufficientDescription())

    await page.goto(`/jobs/${created.jobId}/analysis`)
    const runId = await startAnalysis(page, '분석 시작')
    await expect
      .poll(async () => (await getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)).status, {
        timeout: 120_000,
      })
      .toBe('FAILED')
    const run = await getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)
    expect(run.safeError?.code).toBe('INSUFFICIENT_JOB_DATA')
    await expect(page.getByText('분석할 요구사항을 충분히 찾지 못했어요.')).toBeVisible({
      timeout: 30_000,
    })
    expect(await requestStatus(page, `/api/v1/jobs/${created.jobId}/analyses/latest`)).toBe(404)
    expect(
      (await getJson<AnalysisPage>(page, `/api/v1/jobs/${created.jobId}/analyses`)).totalElements,
    ).toBe(0)
  })
})

interface JobDetail {
  id: string
  version: number
}

interface Career {
  id: string
  version: number
  organization: string
}

interface EvidencePage {
  items: Array<{
    id: string
    title: string
    content: string
    metadata: Record<string, string | number | boolean | null>
    version: number
  }>
}

interface AnalysisPage {
  totalElements: number
}

interface RunAccepted {
  agentRunId: string
}

interface AgentRun {
  status: 'QUEUED' | 'RUNNING' | 'WAITING_USER' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  safeError: { code: string; message: string } | null
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

async function seedProfileAndCareer(page: Page): Promise<{ career: Career }> {
  const profile = await getJson<{ version: number }>(page, '/api/v1/profile')
  const updated = await putJson(page, '/api/v1/profile', {
    legalName: 'P6 Owner',
    introduction: 'Spring Boot API를 설계하고 운영하는 백엔드 개발자입니다.',
    desiredRoles: ['백엔드 개발자'],
    desiredIndustries: ['소프트웨어'],
    desiredLocations: ['서울'],
    expectedGraduationDate: null,
    version: profile.version,
  })
  expect(updated.status).toBe(200)
  const career = await postJson<Career>(page, '/api/v1/profile/careers', {
    organization: 'P6 Career Fixture',
    position: 'Backend Engineer',
    employmentType: 'FULL_TIME',
    startedAt: '2019-01-01',
    endedAt: null,
    isCurrent: true,
    responsibilities: 'Spring Boot API 개발과 PostgreSQL 데이터 모델링을 담당했습니다.',
    achievements: '사용자 격리와 자동화 테스트를 적용했습니다.',
  })
  expect(career.status).toBe(201)
  return { career: career.body }
}

async function updateProfile(page: Page): Promise<void> {
  const profile = await getJson<{ version: number }>(page, '/api/v1/profile')
  const updated = await putJson(page, '/api/v1/profile', {
    legalName: 'P6 Owner',
    introduction: 'Spring Boot API와 분산 시스템을 설계하는 백엔드 개발자입니다.',
    desiredRoles: ['백엔드 개발자'],
    desiredIndustries: ['소프트웨어'],
    desiredLocations: ['서울'],
    expectedGraduationDate: null,
    version: profile.version,
  })
  expect(updated.status).toBe(200)
}

async function updateCareer(page: Page, career: Career): Promise<void> {
  const updated = await putJson(page, `/api/v1/profile/careers/${career.id}`, {
    organization: career.organization,
    position: 'Backend Engineer',
    employmentType: 'FULL_TIME',
    startedAt: '2019-01-01',
    endedAt: null,
    isCurrent: true,
    responsibilities: 'Spring Boot API 개발과 PostgreSQL 데이터 모델링을 담당했습니다.',
    achievements: '사용자 격리, RAG, 자동화 테스트를 적용했습니다.',
    version: career.version,
  })
  expect(updated.status).toBe(200)
}

async function createManualJob(
  page: Page,
  scope: string,
  descriptionText: string,
): Promise<{ jobId: string }> {
  await page.goto('/jobs/new')
  await page.locator('#job-source-url').fill(manualUrl(scope))
  await page.getByText('직접 입력해서 등록', { exact: true }).click()
  await page.locator('#job-company-name').fill('P6 Company')
  await page.locator('#job-position-name').fill('Backend Engineer')
  await page.locator('#job-description').fill(descriptionText)
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/jobs' &&
      response.request().method() === 'POST' &&
      response.status() === 201,
  )
  await page.locator('#job-create-submit').click()
  const response = await responsePromise
  const body = (await response.json()) as { jobId: string }
  await expect(page).toHaveURL(new RegExp(`/jobs/${body.jobId}/overview(?:\\?.*)?$`))
  return body
}

async function startAnalysis(page: Page, buttonName: string): Promise<string> {
  const button = page.getByRole('button', { name: buttonName, exact: true }).last()
  await expect(button).toBeEnabled({ timeout: 30_000 })
  const responsePromise = page.waitForResponse(
    (response) =>
      /\/api\/v1\/jobs\/[^/]+\/analysis$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'POST' &&
      response.status() === 202,
  )
  await button.click()
  const response = await responsePromise
  const body = (await response.json()) as RunAccepted
  return body.agentRunId
}

async function verifyOwnerIsolation(
  browser: Browser,
  jobId: string,
  evidence: EvidencePage['items'][number],
  firstRunId: string,
  secondRunId: string,
): Promise<void> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await signup(page, uniqueEmail('other'), 'P6 Other')
  expect(await requestStatus(page, `/api/v1/jobs/${jobId}`)).toBe(404)
  expect(await requestStatus(page, `/api/v1/jobs/${jobId}/analyses`)).toBe(404)
  expect(await requestStatus(page, `/api/v1/jobs/${jobId}/analyses/latest`)).toBe(404)
  expect(
    await putStatus(page, `/api/v1/profile/evidence/${evidence.id}`, {
      title: evidence.title,
      content: evidence.content,
      metadata: evidence.metadata,
      version: evidence.version,
    }),
  ).toBe(404)
  expect(await requestStatus(page, `/api/v1/agent-runs/${firstRunId}`)).toBe(404)
  expect(await requestStatus(page, `/api/v1/agent-runs/${secondRunId}`)).toBe(404)
  await context.close()
}

async function getJson<T>(page: Page, path: string): Promise<T> {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'include' })
    if (!response.ok) throw new Error(`GET ${requestPath} failed with ${response.status}`)
    return response.json() as Promise<T>
  }, path)
}

async function postJson<T>(
  page: Page,
  path: string,
  body: unknown,
  idempotencyKey?: string,
): Promise<{ status: number; body: T }> {
  return mutateJson<T>(page, 'POST', path, body, idempotencyKey)
}

async function putJson<T = unknown>(
  page: Page,
  path: string,
  body: unknown,
): Promise<{ status: number; body: T }> {
  return mutateJson<T>(page, 'PUT', path, body)
}

async function mutateJson<T>(
  page: Page,
  method: 'POST' | 'PUT',
  path: string,
  body: unknown,
  idempotencyKey?: string,
): Promise<{ status: number; body: T }> {
  const csrf = await getJson<{ headerName: string; token: string }>(page, '/api/v1/auth/csrf')
  return page.evaluate(
    async ({ requestMethod, requestPath, requestBody, csrfHeader, csrfToken, replayKey }) => {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        [csrfHeader]: csrfToken,
      }
      if (replayKey) headers['Idempotency-Key'] = replayKey
      const response = await fetch(requestPath, {
        method: requestMethod,
        headers,
        body: JSON.stringify(requestBody),
        credentials: 'include',
      })
      const responseBody = (await response.json()) as T
      return { status: response.status, body: responseBody }
    },
    {
      requestMethod: method,
      requestPath: path,
      requestBody: body,
      csrfHeader: csrf.headerName,
      csrfToken: csrf.token,
      replayKey: idempotencyKey,
    },
  )
}

async function requestStatus(page: Page, path: string): Promise<number> {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'include' })
    return response.status
  }, path)
}

async function putStatus(page: Page, path: string, body: unknown): Promise<number> {
  const csrf = await getJson<{ headerName: string; token: string }>(page, '/api/v1/auth/csrf')
  return page.evaluate(
    async ({ requestPath, requestBody, csrfHeader, csrfToken }) => {
      const response = await fetch(requestPath, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken,
        },
        body: JSON.stringify(requestBody),
        credentials: 'include',
      })
      return response.status
    },
    {
      requestPath: path,
      requestBody: body,
      csrfHeader: csrf.headerName,
      csrfToken: csrf.token,
    },
  )
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

function analyzableDescription(): string {
  return (
    'Spring Boot와 PostgreSQL 기반 백엔드 API를 개발하고 운영합니다. ' +
    '필수 지원 자격은 백엔드 개발 경력 3년 이상이며 자동화 테스트 경험이 필요합니다. '
  ).repeat(5)
}

function insufficientDescription(): string {
  return 'NO_REQUIREMENTS_FIXTURE 본문은 저장 가능하지만 구조화할 요구사항이 없는 검증 데이터입니다. '.repeat(
    5,
  )
}

function manualUrl(scope: string): string {
  return `https://manual.p6-e2e.invalid/${scope}-${uniqueToken(scope)}`
}

function uniqueEmail(scope: string): string {
  return `p6-${scope}-${uniqueToken(scope)}@example.com`
}

function uniqueToken(scope: string): string {
  return `${scope}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
}
