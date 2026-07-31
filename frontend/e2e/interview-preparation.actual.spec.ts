import { expect, test, type Browser, type Page } from '@playwright/test'

test.describe('P8 actual interview research, questions, answer versions and feedback', () => {
  test.skip(
    process.env.P8_E2E_ENABLED !== 'true',
    'Requires the isolated P8 Spring runner with PostgreSQL, deterministic Fake Chat/Search, Vue, SSE and Chromium.',
  )
  test.describe.configure({ mode: 'serial' })

  test('actual vertical flow, coverage branches, retry, owner isolation and history delete', async ({
    browser,
    page,
  }) => {
    test.setTimeout(600_000)
    page.setDefaultTimeout(30_000)
    page.setDefaultNavigationTimeout(45_000)
    await page.setViewportSize({ width: 1440, height: 1000 })
    attachSafeBrowserDiagnostics(page)

    const fixture = fixtureFromEnvironment()
    await login(page, fixture.ownerEmail, fixture.password)
    await page.goto(`/jobs/${fixture.mainJobId}/interview`)
    await expect(page.getByTestId('submit-interview-preparation')).toBeVisible()
    await page.locator('form select').first().selectOption(fixture.mainCoverId)
    await page.locator('form select').nth(1).selectOption('ADVANCED')
    await page.locator('form input[type="number"]').fill('1')

    const preparationResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/jobs/${fixture.mainJobId}/interview-preparations` &&
        response.request().method() === 'POST' &&
        response.status() === 202,
    )
    await page.getByTestId('submit-interview-preparation').click()
    const mainPreparation = (await (await preparationResponse).json()) as PreparationAccepted
    expect((await waitForTerminalRun(page, mainPreparation.agentRunId)).status).toBe('SUCCEEDED')

    await page.goto(`/interview-question-sets/${mainPreparation.questionSetId}`)
    await expect(page.getByTestId('research-source-OFFICIAL').first()).toBeVisible()
    await expect(page.getByTestId('research-source-INTERVIEW_REVIEW')).toBeVisible()
    const externalSource = page
      .getByTestId('research-source-OFFICIAL')
      .first()
      .locator('a[target="_blank"]')
    await expect(externalSource).toHaveAttribute('rel', 'noopener noreferrer')

    const mainDetail = await getJson<QuestionSetDetail>(
      page,
      `/api/v1/interview-question-sets/${mainPreparation.questionSetId}`,
    )
    expect(mainDetail.research.sourceCoverage).toBe('SUFFICIENT')
    const question = required(mainDetail.questions[0], 'main interview question')
    expect(question.sourceBased).toBe(true)
    expect(question.sourceRefs.length).toBeGreaterThan(0)
    expect(question.relatedEvidenceRefs.length).toBeGreaterThan(0)

    const questionCard = page.getByTestId(`interview-question-${question.id}`)
    const editor = questionCard.locator('textarea')
    const firstAnswerText =
      'I isolated a transaction bottleneck, changed the boundary, and verified the result with production-like tests.'
    await editor.fill(firstAnswerText)
    const firstSaveResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/interview-questions/${question.id}/answer-versions` &&
        response.request().method() === 'POST' &&
        response.status() === 201,
    )
    await page.getByTestId(`save-interview-answer-${question.id}`).click()
    const firstAnswer = (await (await firstSaveResponse).json()) as AnswerVersion
    expect(firstAnswer.versionNo).toBe(1)
    expect(firstAnswer.parentVersionId).toBeNull()

    const feedbackAcceptedResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/interview-answer-versions/${firstAnswer.id}/feedback` &&
        response.request().method() === 'POST' &&
        response.status() === 202,
    )
    await page.getByTestId(`request-interview-feedback-${question.id}`).click()
    const feedbackAccepted = (await (await feedbackAcceptedResponse).json()) as RunAccepted
    expect((await waitForTerminalRun(page, feedbackAccepted.agentRunId)).status).toBe('SUCCEEDED')
    await expect
      .poll(
        async () =>
          (
            await getJson<PageResult<Feedback>>(
              page,
              `/api/v1/interview-answer-versions/${firstAnswer.id}/feedbacks?page=0&size=100&sort=createdAt,desc`,
            )
          ).totalElements,
        { timeout: 120_000 },
      )
      .toBe(1)
    await expect(questionCard.locator('[data-testid^="interview-feedback-"]')).toHaveCount(1)

    const submittedSnapshot =
      'My unsaved browser answer keeps the original wording until I explicitly reapply it.'
    await editor.fill(submittedSnapshot)
    const serverSecond = await postJson<AnswerVersion>(
      page,
      `/api/v1/interview-questions/${question.id}/answer-versions`,
      {
        content: 'A newer answer was saved by another active view.',
        parentVersionId: firstAnswer.id,
      },
    )
    expect(serverSecond.status).toBe(201)
    expect(serverSecond.body.versionNo).toBe(2)

    const conflictResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/interview-questions/${question.id}/answer-versions` &&
        response.request().method() === 'POST' &&
        response.status() === 409,
    )
    await page.getByTestId(`save-interview-answer-${question.id}`).click()
    await conflictResponse
    const conflict = page.getByTestId(`answer-conflict-${question.id}`)
    await expect(conflict).toContainText(submittedSnapshot)
    await expect(conflict).toContainText(serverSecond.body.content)
    await expect(editor).toHaveValue(submittedSnapshot)

    const reapplyResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/interview-questions/${question.id}/answer-versions` &&
        response.request().method() === 'POST' &&
        response.status() === 201,
    )
    await page.getByTestId(`reapply-interview-answer-${question.id}`).click()
    const reapplied = (await (await reapplyResponse).json()) as AnswerVersion
    expect(reapplied.versionNo).toBe(3)
    expect(reapplied.parentVersionId).toBe(serverSecond.body.id)
    expect(reapplied.content).toBe(submittedSnapshot)
    await expect(conflict).toHaveCount(0)

    await questionCard.locator(`input[value="${firstAnswer.id}"]`).check()
    await expect(questionCard.locator('[data-testid^="interview-feedback-"]')).toHaveCount(1)
    const currentQuestion = await getJson<InterviewQuestion>(
      page,
      `/api/v1/interview-questions/${question.id}`,
    )
    expect(currentQuestion.currentAnswer?.id).toBe(reapplied.id)
    expect(
      (
        await getJson<PageResult<Feedback>>(
          page,
          `/api/v1/interview-answer-versions/${firstAnswer.id}/feedbacks?page=0&size=100&sort=createdAt,desc`,
        )
      ).items[0]?.answerVersionId,
    ).toBe(firstAnswer.id)

    const limited = await createAndWaitForPreparation(
      page,
      fixture.limitedJobId,
      fixture.limitedCoverId,
      'BASIC',
      'p8-actual-limited',
    )
    const limitedResearch = await getJson<ResearchRun>(
      page,
      `/api/v1/research-runs/${limited.researchRunId}`,
    )
    expect(limitedResearch.status).toBe('SUCCEEDED')
    expect(limitedResearch.sourceCoverage).toBe('LIMITED')
    await page.goto(`/interview-question-sets/${limited.questionSetId}`)
    await expect(page.getByTestId('source-coverage-warning')).toBeVisible()

    const none = await createAndWaitForPreparation(
      page,
      fixture.noneJobId,
      fixture.noneCoverId,
      'BASIC',
      'p8-actual-none',
    )
    const noneDetail = await getJson<QuestionSetDetail>(
      page,
      `/api/v1/interview-question-sets/${none.questionSetId}`,
    )
    expect(noneDetail.research.status).toBe('SUCCEEDED')
    expect(noneDetail.research.sourceCoverage).toBe('NONE')
    expect(noneDetail.questions).toHaveLength(1)
    expect(noneDetail.questions[0]?.sourceBased).toBe(false)
    expect(noneDetail.questions[0]?.sourceRefs).toEqual([])

    const failed = await createPreparation(
      page,
      fixture.failureJobId,
      fixture.failureCoverId,
      'BASIC',
      'p8-actual-failure',
    )
    const failedRun = await waitForTerminalRun(page, failed.agentRunId)
    expect(failedRun.status).toBe('FAILED')
    expect(failedRun.retryable).toBe(true)
    const failedResearch = await getJson<ResearchRun>(
      page,
      `/api/v1/research-runs/${failed.researchRunId}`,
    )
    expect(failedResearch.status).toBe('FAILED')
    expect(failedResearch.retryable).toBe(true)

    const retryOne = await postJson<ResearchRetryAccepted>(
      page,
      `/api/v1/research-runs/${failed.researchRunId}/retry`,
      {},
      'p8-actual-research-retry',
    )
    expect(retryOne.status).toBe(202)
    const retryReplay = await postJson<ResearchRetryAccepted>(
      page,
      `/api/v1/research-runs/${failed.researchRunId}/retry`,
      {},
      'p8-actual-research-retry',
    )
    expect(retryReplay.status).toBe(202)
    expect(retryReplay.body.agentRunId).toBe(retryOne.body.agentRunId)
    expect(retryReplay.body.researchRunId).toBe(retryOne.body.researchRunId)
    const genericRetry = await mutateJson<RunAccepted>(
      page,
      'POST',
      `/api/v1/agent-runs/${failed.agentRunId}/retry`,
      undefined,
      'p8-actual-generic-retry',
    )
    expect(genericRetry.status).toBe(202)
    expect(genericRetry.body.agentRunId).toBe(retryOne.body.agentRunId)
    expect((await waitForTerminalRun(page, retryOne.body.agentRunId)).status).toBe('FAILED')

    await page.goto('/interviews?qsPage=-1&qsSort=unknown&foreignKey=remove-me')
    await expect(page).toHaveURL(/\/interviews$/)
    await expect(page.locator('.interview-list__items')).toBeVisible()
    await expectNoHorizontalOverflow(page)

    await page.goto(`/interview-question-sets/${mainPreparation.questionSetId}`)
    const zoomSession = await page.context().newCDPSession(page)
    await zoomSession.send('Emulation.setPageScaleFactor', { pageScaleFactor: 2 })
    await expect.poll(() => page.evaluate(() => window.visualViewport?.scale ?? 1)).toBe(2)
    const zoomedAnswerAction = page.getByTestId(`save-interview-answer-${question.id}`)
    await zoomedAnswerAction.scrollIntoViewIfNeeded()
    await expect(zoomedAnswerAction).toBeInViewport()
    await expectNoHorizontalOverflow(page)
    await zoomSession.send('Emulation.setPageScaleFactor', { pageScaleFactor: 1 })
    await zoomSession.detach()

    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(`/interview-question-sets/${mainPreparation.questionSetId}`)
    await expect(page.getByTestId(`interview-question-${question.id}`)).toBeVisible()
    await expectNoHorizontalOverflow(page)
    const mobileTrigger = page.locator('.mobile-menu-button')
    await mobileTrigger.click()
    await expect(page.locator('.mobile-drawer')).toBeVisible()
    await expect(page.locator('.mobile-drawer a[href="/interviews"]')).toBeVisible()
    await expect
      .poll(() =>
        page.evaluate(() => document.activeElement?.hasAttribute('data-mobile-nav-first') ?? false),
      )
      .toBe(true)
    await page.keyboard.press('Escape')
    await expect(page.locator('.mobile-drawer')).toHaveCount(0)
    await expect(mobileTrigger).toBeFocused()
    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY')

    await verifyOwnerIsolation(browser, fixture, {
      mainPreparation,
      questionId: question.id,
      answerVersionId: firstAnswer.id,
      feedbackRunId: feedbackAccepted.agentRunId,
    })

    const historyDelete = await mutateJson<undefined>(
      page,
      'DELETE',
      `/api/v1/agent-runs/${mainPreparation.agentRunId}`,
      undefined,
    )
    expect(historyDelete.status).toBe(204)
    expect(await requestStatus(page, `/api/v1/agent-runs/${mainPreparation.agentRunId}`)).toBe(404)
    expect(
      await requestStatus(page, `/api/v1/agent-runs/${mainPreparation.agentRunId}/events`),
    ).toBe(404)
    expect(
      (
        await mutateJson<unknown>(
          page,
          'POST',
          `/api/v1/agent-runs/${mainPreparation.agentRunId}/retry`,
          undefined,
          'p8-deleted-generic-retry',
        )
      ).status,
    ).toBe(404)
    expect(
      (
        await postJson<unknown>(
          page,
          `/api/v1/research-runs/${mainPreparation.researchRunId}/retry`,
          {},
          'p8-deleted-research-retry',
        )
      ).status,
    ).toBe(404)
    expect(
      await requestStatus(page, `/api/v1/interview-question-sets/${mainPreparation.questionSetId}`),
    ).toBe(200)
    expect(
      await requestStatus(page, `/api/v1/interview-answer-versions/${firstAnswer.id}/feedbacks`),
    ).toBe(200)
  })
})

interface Fixture {
  ownerEmail: string
  otherEmail: string
  password: string
  mainJobId: string
  mainCoverId: string
  limitedJobId: string
  limitedCoverId: string
  noneJobId: string
  noneCoverId: string
  failureJobId: string
  failureCoverId: string
}

interface PreparationAccepted {
  questionSetId: string
  researchRunId: string
  agentRunId: string
  status: 'QUEUED'
}

interface ResearchRetryAccepted extends PreparationAccepted {
  retryOfResearchRunId: string
}

interface RunAccepted {
  agentRunId: string
}

interface AgentRun {
  status:
    'QUEUED' | 'RUNNING' | 'WAITING_USER' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED'
  retryable: boolean
}

interface ResearchRun {
  id: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  sourceCoverage: 'SUFFICIENT' | 'LIMITED' | 'NONE' | null
  retryable: boolean
}

interface QuestionSetDetail {
  research: ResearchRun
  questions: InterviewQuestion[]
}

interface InterviewQuestion {
  id: string
  sourceBased: boolean
  sourceRefs: Array<{ id: string }>
  relatedEvidenceRefs: Array<{ id: string }>
  currentAnswer: AnswerVersion | null
}

interface AnswerVersion {
  id: string
  parentVersionId: string | null
  versionNo: number
  content: string
}

interface Feedback {
  id: string
  answerVersionId: string
}

interface PageResult<T> {
  items: T[]
  totalElements: number
}

function fixtureFromEnvironment(): Fixture {
  return {
    ownerEmail: requiredEnvironment('P8_OWNER_EMAIL'),
    otherEmail: requiredEnvironment('P8_OTHER_EMAIL'),
    password: requiredEnvironment('P8_PASSWORD'),
    mainJobId: requiredEnvironment('P8_MAIN_JOB_ID'),
    mainCoverId: requiredEnvironment('P8_MAIN_COVER_ID'),
    limitedJobId: requiredEnvironment('P8_LIMITED_JOB_ID'),
    limitedCoverId: requiredEnvironment('P8_LIMITED_COVER_ID'),
    noneJobId: requiredEnvironment('P8_NONE_JOB_ID'),
    noneCoverId: requiredEnvironment('P8_NONE_COVER_ID'),
    failureJobId: requiredEnvironment('P8_FAILURE_JOB_ID'),
    failureCoverId: requiredEnvironment('P8_FAILURE_COVER_ID'),
  }
}

function requiredEnvironment(name: string): string {
  const value = process.env[name]
  if (value === undefined || value.length === 0) {
    throw new Error(`${name} is required for the P8 actual E2E.`)
  }
  return value
}

function required<T>(value: T | undefined, label: string): T {
  if (value === undefined) throw new Error(`${label} is missing.`)
  return value
}

async function login(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(password)
  await page.locator('form button[type="submit"]').click()
  await page.waitForURL(/\/dashboard$/)
}

async function createAndWaitForPreparation(
  page: Page,
  jobId: string,
  coverLetterId: string,
  researchQuality: 'BASIC' | 'ADVANCED',
  idempotencyKey: string,
): Promise<PreparationAccepted> {
  const accepted = await createPreparation(
    page,
    jobId,
    coverLetterId,
    researchQuality,
    idempotencyKey,
  )
  expect((await waitForTerminalRun(page, accepted.agentRunId)).status).toBe('SUCCEEDED')
  return accepted
}

async function createPreparation(
  page: Page,
  jobId: string,
  coverLetterId: string,
  researchQuality: 'BASIC' | 'ADVANCED',
  idempotencyKey: string,
): Promise<PreparationAccepted> {
  const response = await postJson<PreparationAccepted>(
    page,
    `/api/v1/jobs/${jobId}/interview-preparations`,
    {
      coverLetterId,
      researchQuality,
      qualityMode: 'BALANCED',
      questionTypes: ['TECHNICAL'],
      questionCount: 1,
    },
    idempotencyKey,
  )
  expect(response.status).toBe(202)
  return response.body
}

async function waitForTerminalRun(page: Page, runId: string): Promise<AgentRun> {
  await expect
    .poll(async () => (await getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)).status, {
      timeout: 120_000,
    })
    .toMatch(/^(SUCCEEDED|FAILED|CANCELLED|INTERRUPTED)$/)
  return getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)
}

async function verifyOwnerIsolation(
  browser: Browser,
  fixture: Fixture,
  resources: {
    mainPreparation: PreparationAccepted
    questionId: string
    answerVersionId: string
    feedbackRunId: string
  },
): Promise<void> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, fixture.otherEmail, fixture.password)
  expect(await requestStatus(page, `/api/v1/jobs/${fixture.mainJobId}`)).toBe(404)
  expect(
    await requestStatus(page, `/api/v1/research-runs/${resources.mainPreparation.researchRunId}`),
  ).toBe(404)
  expect(
    await requestStatus(
      page,
      `/api/v1/research-runs/${resources.mainPreparation.researchRunId}/sources`,
    ),
  ).toBe(404)
  expect(
    await requestStatus(
      page,
      `/api/v1/interview-question-sets/${resources.mainPreparation.questionSetId}`,
    ),
  ).toBe(404)
  expect(await requestStatus(page, `/api/v1/interview-questions/${resources.questionId}`)).toBe(404)
  expect(
    await requestStatus(
      page,
      `/api/v1/interview-questions/${resources.questionId}/answer-versions`,
    ),
  ).toBe(404)
  expect(
    await requestStatus(
      page,
      `/api/v1/interview-answer-versions/${resources.answerVersionId}/feedbacks`,
    ),
  ).toBe(404)
  expect(await requestStatus(page, `/api/v1/agent-runs/${resources.feedbackRunId}`)).toBe(404)
  const feedbackAttempt = await postJson<unknown>(
    page,
    `/api/v1/interview-answer-versions/${resources.answerVersionId}/feedback`,
    { qualityMode: 'BALANCED' },
    'p8-other-feedback',
  )
  expect(feedbackAttempt.status).toBe(404)
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

async function mutateJson<T>(
  page: Page,
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
  idempotencyKey?: string,
): Promise<{ status: number; body: T }> {
  const csrf = await getJson<{ headerName: string; token: string }>(page, '/api/v1/auth/csrf')
  return page.evaluate(
    async ({ requestMethod, requestPath, requestBody, csrfHeader, csrfToken, replayKey }) => {
      const headers: Record<string, string> = { [csrfHeader]: csrfToken }
      if (requestBody !== undefined) headers['Content-Type'] = 'application/json'
      if (replayKey) headers['Idempotency-Key'] = replayKey
      const response = await fetch(requestPath, {
        method: requestMethod,
        headers,
        body: requestBody === undefined ? undefined : JSON.stringify(requestBody),
        credentials: 'include',
      })
      const text = await response.text()
      return {
        status: response.status,
        body: (text.length === 0 ? undefined : JSON.parse(text)) as T,
      }
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

function attachSafeBrowserDiagnostics(page: Page): void {
  page.on('response', (response) => {
    const method = response.request().method()
    if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') return
    const path = new URL(response.url()).pathname
    console.log(`[P8_BROWSER_RESPONSE] method=${method} path=${path} status=${response.status()}`)
  })
  page.on('requestfailed', (request) => {
    const path = new URL(request.url()).pathname
    console.log(
      `[P8_BROWSER_REQUEST_FAILED] method=${request.method()} path=${path} reason=${request.failure()?.errorText ?? 'unknown'}`,
    )
  })
  page.on('pageerror', (error) => {
    console.log(`[P8_BROWSER_PAGE_ERROR] name=${error.name} message=${error.message}`)
  })
}
