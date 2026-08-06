import { expect, test, type Browser, type Page } from '@playwright/test'

test.describe('P7 actual Backend cover-letter lifecycle', () => {
  test.skip(
    process.env.P7_E2E_ENABLED !== 'true',
    'Requires the isolated P7 Spring runner with PostgreSQL, MinIO, deterministic Fake AI, Vue, SSE and Chromium.',
  )
  test.describe.configure({ mode: 'serial' })

  test('scenario B → partial retry → version restore → evidence lifecycle → archive → owner isolation', async ({
    browser,
    page,
  }) => {
    test.setTimeout(600_000)
    page.setDefaultTimeout(30_000)
    page.setDefaultNavigationTimeout(45_000)
    attachSafeBrowserDiagnostics(page)
    await signup(page, uniqueEmail('owner'), 'P7 Owner')
    await seedProfile(page)
    const document = await uploadAndApproveEvidence(page)
    const evidence = await latestVerifiedEvidence(page)
    const job = await createAndAnalyzeJob(page)

    const coverLetterId = await createCoverLetterFromJob(page, job.id)
    const firstQuestion = await addQuestion(
      page,
      '승인된 경험을 바탕으로 지원 동기와 기여 방안을 작성해 주세요.',
      1_000,
    )
    const failingQuestion = await addQuestion(
      page,
      '협업 경험을 작성해 주세요. P7_FORCE_GENERATION_FAILURE',
      1_000,
    )
    const disposableQuestion = await addQuestion(page, '삭제 보존 확인용 임시 문항입니다.', 500)
    await deleteSelectedQuestion(page, disposableQuestion.id)

    await moveQuestionUp(page, failingQuestion)
    await editQuestionMemo(page, firstQuestion.questionText, '실제 P7 문항 수정 검증')
    await exerciseQuestionConflict(page, coverLetterId, firstQuestion)
    await exerciseTitleConflict(page, coverLetterId)

    const preferredEvidence = page
      .locator('.assist__evidence li')
      .filter({ hasText: evidence.title })
      .getByRole('checkbox')
    await expect(preferredEvidence).toBeVisible()
    await preferredEvidence.check()
    await openGenerationSettings(page)
    const generationTargets = page.locator('.generation-questions input[type="checkbox"]')
    await expect(generationTargets).toHaveCount(2)
    for (let index = 0; index < (await generationTargets.count()); index += 1) {
      const target = generationTargets.nth(index)
      if (!(await target.isChecked())) await target.check()
    }

    const beforeGeneration = await getCoverLetter(page, coverLetterId)
    const firstRun = await startGeneration(page)
    const firstTerminal = await waitForTerminalRun(page, firstRun)
    expect(firstTerminal.status).toBe('FAILED')
    expect(firstTerminal.safeError?.code).toBe('COVER_LETTER_GENERATION_PARTIAL_FAILURE')
    expect(firstTerminal.partialResult?.succeededScopeKeys).toEqual([firstQuestion.id])
    expect(firstTerminal.partialResult?.failedScopeKeys).toEqual([failingQuestion.id])
    await expect(page.getByRole('heading', { name: '생성 완료 문항' })).toBeVisible({
      timeout: 60_000,
    })
    await expect(page.getByRole('heading', { name: '재시도가 필요한 문항' })).toBeVisible()
    await expect(page.getByText('성공한 답변은 보존됩니다.', { exact: false })).toBeVisible()

    const afterPartial = await getCoverLetter(page, coverLetterId)
    const firstAfterPartial = questionById(afterPartial, firstQuestion.id)
    const failedAfterPartial = questionById(afterPartial, failingQuestion.id)
    expect(firstAfterPartial.currentAnswer).not.toBeNull()
    expect(failedAfterPartial.currentAnswer).toBeNull()
    const firstVersionsAfterPartial = await listVersions(page, firstQuestion.id)
    expect(firstVersionsAfterPartial.totalElements).toBe(1)
    const generatedFirstVersion = firstAfterPartial.currentAnswer!

    await page.getByRole('link', { name: 'AI 작업 상세' }).click()
    await expect(page).toHaveURL(new RegExp(`/agent-runs/${firstRun}$`))
    const retryResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/v1/agent-runs/${firstRun}/retry` &&
        response.request().method() === 'POST' &&
        response.status() === 202,
    )
    await page.getByRole('button', { name: '재시도', exact: true }).click()
    const retryAccepted = (await (await retryResponse).json()) as RunAccepted
    await expect(page).toHaveURL(new RegExp(`/agent-runs/${retryAccepted.agentRunId}$`))
    expect((await waitForTerminalRun(page, retryAccepted.agentRunId)).status).toBe('SUCCEEDED')
    await page.getByRole('link', { name: '자기소개서 보기' }).click()
    await expect(page).toHaveURL(new RegExp(`/cover-letters/${coverLetterId}/edit$`))

    const afterRetry = await getCoverLetter(page, coverLetterId)
    expect(questionById(afterRetry, failingQuestion.id).currentAnswer).not.toBeNull()
    expect((await listVersions(page, firstQuestion.id)).totalElements).toBe(1)
    expect(questionById(afterRetry, firstQuestion.id).currentAnswer?.id).toBe(
      generatedFirstVersion.id,
    )
    expect(afterRetry.version).toBe(beforeGeneration.version + 2)

    await selectQuestion(page, firstQuestion.questionText)
    const userAnswer =
      '검토한 문서 근거를 바탕으로 성과를 크게 높였습니다. P7_FORCE_VERIFICATION_WARNING'
    const answerEditor = page.getByRole('textbox', { name: '자기소개서 답변' })
    await answerEditor.fill(userAnswer)
    await expect(page.getByText('저장 안 됨', { exact: false }).first()).toBeVisible()
    expect(
      await page.evaluate(
        ({ coverId, questionId }) =>
          Object.keys(sessionStorage).some(
            (key) => key.includes('/COVER_LETTER/') && key.includes(`/${coverId}/${questionId}/`),
          ),
        { coverId: coverLetterId, questionId: firstQuestion.id },
      ),
    ).toBe(true)

    const saveResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/cover-letter-questions/${firstQuestion.id}/versions` &&
        response.request().method() === 'POST' &&
        response.status() === 201,
    )
    await page.getByTestId('save-answer-version').click()
    const userVersion = (await (await saveResponse).json()) as AnswerVersion
    await expect(
      page.getByRole('status').filter({ hasText: `버전 ${userVersion.versionNo}을 저장했어요.` }),
    ).toBeVisible()
    expect(userVersion.sourceType).toBe('USER_EDITED')
    expect(userVersion.parentVersionId).toBe(generatedFirstVersion.id)
    expect(
      await page.evaluate(
        ({ coverId, questionId }) =>
          Object.keys(sessionStorage).some(
            (key) => key.includes('/COVER_LETTER/') && key.includes(`/${coverId}/${questionId}/`),
          ),
        { coverId: coverLetterId, questionId: firstQuestion.id },
      ),
    ).toBe(false)
    expect((await listVersions(page, firstQuestion.id)).totalElements).toBe(2)

    const firstVerificationRun = await startVerification(page)
    expect((await waitForTerminalRun(page, firstVerificationRun)).status).toBe('SUCCEEDED')
    await expect
      .poll(async () => {
        const question = questionById(await getCoverLetter(page, coverLetterId), firstQuestion.id)
        return {
          answerVersionId: question.latestVerification?.answerVersionId ?? null,
          status: question.latestVerification?.status ?? null,
        }
      })
      .toEqual({ answerVersionId: userVersion.id, status: 'WARNING' })

    const warningVerifications = await listVerifications(page, userVersion.id)
    const warningVerification = warningVerifications.items[0]
    expect(warningVerification?.status).toBe('WARNING')
    await openReviewTab(page)
    await expect(page.locator('.verification-issues blockquote').first()).toContainText(
      'P7_FORCE_VERIFICATION_WARNING',
    )
    await expect(
      page
        .locator('.verification-card .historical-evidence')
        .filter({ hasText: evidence.title })
        .first(),
    ).toBeVisible()
    const passedSuggestion = page
      .locator('.verification-suggestions')
      .filter({ hasText: 'P7_FORCE_VERIFICATION_PASSED' })
    await expect(passedSuggestion).toBeVisible()

    const versionCountBeforeSuggestion = (await listVersions(page, firstQuestion.id)).totalElements
    await passedSuggestion.getByRole('button', { name: '편집기에 넣기' }).click()
    await expect(
      page.getByText('제안을 편집기에 넣었어요. 내용을 다듬은 뒤 저장해 주세요.'),
    ).toBeVisible()
    expect((await listVersions(page, firstQuestion.id)).totalElements).toBe(
      versionCountBeforeSuggestion,
    )

    const correctedSaveResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/cover-letter-questions/${firstQuestion.id}/versions` &&
        response.request().method() === 'POST' &&
        response.status() === 201,
    )
    await page.getByTestId('save-answer-version').click()
    const correctedVersion = (await (await correctedSaveResponse).json()) as AnswerVersion
    await expect(
      page
        .getByRole('status')
        .filter({ hasText: `버전 ${correctedVersion.versionNo}을 저장했어요.` }),
    ).toBeVisible()
    expect(correctedVersion.sourceType).toBe('USER_EDITED')
    expect(correctedVersion.parentVersionId).toBe(userVersion.id)
    expect(correctedVersion.plainText).toContain('P7_FORCE_VERIFICATION_PASSED')
    expect((await listVersions(page, firstQuestion.id)).totalElements).toBe(
      versionCountBeforeSuggestion + 1,
    )

    const passedVerificationRun = await startVerification(page)
    expect((await waitForTerminalRun(page, passedVerificationRun)).status).toBe('SUCCEEDED')
    await expect
      .poll(async () => {
        const question = questionById(await getCoverLetter(page, coverLetterId), firstQuestion.id)
        return {
          answerVersionId: question.latestVerification?.answerVersionId ?? null,
          status: question.latestVerification?.status ?? null,
        }
      })
      .toEqual({ answerVersionId: correctedVersion.id, status: 'PASSED' })

    await selectQuestion(page, failingQuestion.questionText)
    const secondAfterRetry = questionById(
      await getCoverLetter(page, coverLetterId),
      failingQuestion.id,
    )
    if (
      secondAfterRetry.latestVerification?.answerVersionId !== secondAfterRetry.currentAnswer?.id
    ) {
      const secondVerificationRun = await startVerification(page)
      expect((await waitForTerminalRun(page, secondVerificationRun)).status).toBe('SUCCEEDED')
      await ensureQuestionHasFreshVerification(page, coverLetterId, failingQuestion.id)
    }

    await page.reload()
    const finalizable = await getCoverLetter(page, coverLetterId)
    for (const question of finalizable.questions.filter((item) => item.deletedAt === null)) {
      expect(question.currentAnswer).not.toBeNull()
      expect(question.latestVerification?.answerVersionId).toBe(question.currentAnswer?.id)
      expect(['PASSED', 'WARNING']).toContain(question.latestVerification?.status)
    }
    await page.getByTestId('open-completion').click()
    const warningCheckboxes = page.locator('.finalization__warnings input[type="checkbox"]')
    for (let index = 0; index < (await warningCheckboxes.count()); index += 1) {
      await warningCheckboxes.nth(index).check()
    }
    const finalizeButton = page.getByTestId('finalize-cover-letter')
    await expect(finalizeButton).toBeEnabled()
    const finalizeResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/v1/cover-letters/${coverLetterId}/finalize` &&
        response.request().method() === 'POST' &&
        response.status() === 200,
    )
    await finalizeButton.click()
    const finalized = (await (await finalizeResponse).json()) as CoverLetter
    await expect(
      page.getByRole('status').filter({ hasText: '자기소개서를 작성 완료로 표시했어요.' }),
    ).toBeVisible()
    expect(finalized.status).toBe('FINALIZED')
    expect(finalized.finalizedAt).toBeTruthy()
    await expect(
      page.getByText('공고의 지원 상태는 공고 화면에서 따로 바꿔 주세요.', {
        exact: false,
      }),
    ).toBeVisible()

    await selectQuestion(page, firstQuestion.questionText)
    const beforeRestoreVersions = await listVersions(page, firstQuestion.id)
    const generatedHistorical = beforeRestoreVersions.items.find(
      (version) => version.id === generatedFirstVersion.id,
    )
    expect(generatedHistorical).toBeDefined()
    await page.getByTestId('open-versions').click()
    await page
      .getByRole('option')
      .filter({ hasText: `v${generatedHistorical!.versionNo}` })
      .click()
    const restoreResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname.endsWith(`/versions/${generatedHistorical!.id}/restore`) &&
        response.request().method() === 'POST' &&
        response.status() === 201,
    )
    await page.getByTestId('restore-answer-version').click()
    const restored = (await (await restoreResponse).json()) as AnswerVersion
    await expect(
      page.getByRole('status').filter({
        hasText: `버전 ${generatedHistorical!.versionNo}의 내용으로 되돌린 답변을 새로 저장했어요.`,
      }),
    ).toBeVisible()
    expect(restored.sourceType).toBe('RESTORED')
    expect(restored.parentVersionId).toBe(correctedVersion.id)
    expect(restored.restoredFromVersionId).toBe(generatedHistorical!.id)
    const afterRestoreVersions = await listVersions(page, firstQuestion.id)
    expect(afterRestoreVersions.totalElements).toBe(beforeRestoreVersions.totalElements + 1)
    expect(
      afterRestoreVersions.items.find((version) => version.id === generatedHistorical!.id),
    ).toMatchObject(generatedHistorical!)
    await page.getByRole('button', { name: '버전 기록 닫기' }).click()
    expect((await getCoverLetter(page, coverLetterId)).status).toBe('DRAFT')

    const generationVerifications = await listVerifications(page, generatedHistorical!.id)
    expect(generationVerifications.items.length).toBeGreaterThan(0)
    expect(
      generationVerifications.items.some((verification) =>
        verification.evidenceRefs.some((reference) => reference.id === evidence.id),
      ),
    ).toBe(true)

    await deleteDocument(page, document.id)
    await expect
      .poll(
        async () =>
          (await listVerifications(page, generatedHistorical!.id)).items.some((verification) =>
            verification.evidenceRefs.some(
              (reference) =>
                reference.id === evidence.id &&
                reference.sourceDeleted &&
                reference.verificationStatus === 'SOURCE_DELETED',
            ),
          ),
        { timeout: 60_000 },
      )
      .toBe(true)

    await page.goto(`/cover-letters/${coverLetterId}/edit`)
    await selectQuestion(page, firstQuestion.questionText)
    await page.getByTestId('open-versions').click()
    await page
      .getByRole('option')
      .filter({ hasText: `v${generatedHistorical!.versionNo}` })
      .click()
    await expect(page.getByText('원본 삭제됨', { exact: true }).first()).toBeVisible()
    await expect(
      page.getByText('새 초안·검토에서는 쓰지 않아요', { exact: true }).first(),
    ).toBeVisible()
    await page.getByRole('button', { name: '버전 기록 닫기' }).click()

    const currentAfterRestore = await getCoverLetter(page, coverLetterId)
    const rejectedGeneration = await postJson<unknown>(
      page,
      `/api/v1/cover-letters/${coverLetterId}/generate`,
      {
        questionIds: [firstQuestion.id],
        preferredEvidenceIds: [evidence.id],
        model: 'gpt-5.6-terra',
        avoidExperienceDuplication: true,
        coverLetterVersion: currentAfterRestore.version,
      },
      `p7-source-deleted-${uniqueToken('source-deleted')}`,
    )
    expect(rejectedGeneration.status).toBe(409)

    await expectNoHorizontalOverflow(page)
    await page.setViewportSize({ width: 390, height: 844 })
    await expectNoHorizontalOverflow(page)
    await page.setViewportSize({ width: 1440, height: 900 })

    const archiveResponse = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/v1/cover-letters/${coverLetterId}/archive` &&
        response.request().method() === 'POST' &&
        response.status() === 200,
    )
    await page.getByTestId('open-completion').click()
    await page.getByRole('button', { name: '보관하기', exact: true }).click()
    const archived = (await (await archiveResponse).json()) as CoverLetter
    expect(archived.status).toBe('ARCHIVED')
    await page.getByRole('button', { name: '작성 완료 점검 닫기' }).click()
    await expect(page.getByText('보관된 자기소개서예요 · 읽기 전용', { exact: true })).toBeVisible()
    await expect(page.getByTestId('open-generation')).toHaveCount(0)
    await expect(page.getByTestId('verify-answer-version')).toHaveCount(0)

    const replacement = await postJson<CoverLetter>(
      page,
      `/api/v1/jobs/${job.id}/cover-letter`,
      { title: '새 active 자기소개서' },
      `p7-replacement-${uniqueToken('replacement')}`,
    )
    expect(replacement.status).toBe(201)
    const blockedUnarchive = await postJson<unknown>(
      page,
      `/api/v1/cover-letters/${coverLetterId}/unarchive`,
      { version: archived.version },
    )
    expect(blockedUnarchive.status).toBe(409)
    const replacementArchived = await postJson<CoverLetter>(
      page,
      `/api/v1/cover-letters/${replacement.body.id}/archive`,
      { version: replacement.body.version },
    )
    expect(replacementArchived.status).toBe(200)
    const unarchived = await postJson<CoverLetter>(
      page,
      `/api/v1/cover-letters/${coverLetterId}/unarchive`,
      { version: archived.version },
    )
    expect(unarchived.status).toBe(200)
    expect(unarchived.body.status).toBe('DRAFT')
    expect(unarchived.body.finalizedAt).toBe(finalized.finalizedAt)

    await verifyOwnerIsolation(browser, {
      coverLetterId,
      questionId: firstQuestion.id,
      answerVersionId: restored.id,
      verificationVersionId: generatedHistorical!.id,
      agentRunId: retryAccepted.agentRunId,
      documentId: document.id,
    })
  })
})

interface DocumentDetail {
  id: string
  version: number
}

interface Evidence {
  id: string
  title: string
}

interface Job {
  id: string
  version: number
}

interface Question {
  id: string
  questionOrder: number
  questionText: string
  maxLength: number | null
  memo: string | null
  version: number
  currentAnswer: AnswerVersion | null
  latestVerification: Verification | null
}

interface CoverLetter {
  id: string
  status: 'DRAFT' | 'FINALIZED' | 'ARCHIVED'
  version: number
  finalizedAt: string | null
  archivedAt: string | null
  questions: Question[]
}

interface AnswerVersion {
  id: string
  questionId: string
  parentVersionId: string | null
  restoredFromVersionId: string | null
  versionNo: number
  plainText: string
  sourceType: 'AI_GENERATED' | 'USER_EDITED' | 'AI_REVISED' | 'RESTORED'
  isCurrent: boolean
}

interface EvidenceRef {
  id: string
  verificationStatus: 'VERIFIED' | 'PENDING' | 'REJECTED' | 'SOURCE_DELETED'
  sourceDeleted: boolean
}

interface Verification {
  id: string
  answerVersionId: string
  status: 'PENDING' | 'PASSED' | 'WARNING' | 'FAILED'
  evidenceRefs: EvidenceRef[]
}

interface PageResult<T> {
  items: T[]
  totalElements: number
}

interface RunAccepted {
  agentRunId: string
}

interface AgentRun {
  status:
    'QUEUED' | 'RUNNING' | 'WAITING_USER' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED'
  retryable: boolean
  safeError: { code: string; message: string } | null
  partialResult: {
    succeededScopeKeys: string[]
    failedScopeKeys: string[]
  } | null
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

async function seedProfile(page: Page): Promise<void> {
  const profile = await getJson<{ version: number }>(page, '/api/v1/profile')
  const updated = await putJson(page, '/api/v1/profile', {
    legalName: 'P7 Owner',
    introduction: '사용자 요구사항을 분석하고 안정적인 API를 설계하는 개발자입니다.',
    desiredRoles: ['백엔드 개발자'],
    desiredIndustries: ['소프트웨어'],
    desiredLocations: ['서울'],
    expectedGraduationDate: null,
    version: profile.version,
  })
  expect(updated.status).toBe(200)
}

async function uploadAndApproveEvidence(page: Page): Promise<DocumentDetail> {
  await page.goto('/documents')
  await page.locator('#document-file').setInputFiles({
    name: 'p7-owner-resume.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from(approvedDocumentText(), 'utf8'),
  })
  await page.locator('#document-upload-type').selectOption('RESUME')
  await page.locator('#document-displayName').fill('P7 검증 이력서')
  await page.locator('#document-upload-submit').click()
  await page.waitForURL(/\/documents\/[0-9a-f-]+\?run=[0-9a-f-]+$/)
  const documentId = new URL(page.url()).pathname.split('/').pop()
  if (!documentId) throw new Error('P7 document ID is missing.')
  await expect(
    page.getByLabel('분석 결과 요약').getByText('정리 완료', { exact: true }),
  ).toBeVisible({ timeout: 120_000 })
  const evidenceCard = page.locator('[aria-labelledby="document-evidence-heading"] li').first()
  await expect(evidenceCard).toContainText('검토 대기')
  await evidenceCard.getByRole('button', { name: '수정' }).click()
  await evidenceCard.getByLabel('제목').fill('P7 승인 프로젝트 근거')
  await evidenceCard.getByRole('button', { name: '저장' }).click()
  await evidenceCard.getByRole('button', { name: '승인' }).click()
  await expect(evidenceCard).toContainText('승인됨')
  return getJson<DocumentDetail>(page, `/api/v1/documents/${documentId}`)
}

async function latestVerifiedEvidence(page: Page): Promise<Evidence> {
  const result = await getJson<PageResult<Evidence>>(
    page,
    '/api/v1/profile/evidence?verificationStatus=VERIFIED&page=0&size=20&sort=updatedAt,desc',
  )
  const value = result.items[0]
  if (!value) throw new Error('P7 VERIFIED evidence is missing.')
  return value
}

async function createAndAnalyzeJob(page: Page): Promise<Job> {
  await page.goto('/jobs/new')
  await page.locator('#job-source-url').fill(`https://manual.p7-e2e.invalid/${uniqueToken('job')}`)
  await page.getByText('직접 입력해서 등록', { exact: true }).click()
  await page.locator('#job-company-name').fill('P7 Company')
  await page.locator('#job-position-name').fill('Backend Engineer')
  await page.locator('#job-description').fill(analyzableDescription())
  const createResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/jobs' &&
      response.request().method() === 'POST' &&
      response.status() === 201,
  )
  await page.locator('#job-create-submit').click()
  const created = (await (await createResponse).json()) as { jobId: string }
  await page.goto(`/jobs/${created.jobId}/analysis`)
  const analysisResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/jobs/${created.jobId}/analysis` &&
      response.request().method() === 'POST' &&
      response.status() === 202,
  )
  await page.getByRole('button', { name: '분석 시작', exact: true }).click()
  const run = (await (await analysisResponse).json()) as RunAccepted
  expect((await waitForTerminalRun(page, run.agentRunId)).status).toBe('SUCCEEDED')
  await expect(page.locator('#analysis-result-heading')).toHaveText('분석 버전 1', {
    timeout: 120_000,
  })
  return getJson<Job>(page, `/api/v1/jobs/${created.jobId}`)
}

async function createCoverLetterFromJob(page: Page, jobId: string): Promise<string> {
  await page.goto(`/jobs/${jobId}/cover-letter`)
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/jobs/${jobId}/cover-letter` &&
      response.request().method() === 'POST' &&
      response.status() === 201,
  )
  await page.getByTestId('create-cover-letter').click()
  const detail = (await (await responsePromise).json()) as CoverLetter
  await expect(page).toHaveURL(new RegExp(`/cover-letters/${detail.id}/edit$`))
  return detail.id
}

async function addQuestion(page: Page, questionText: string, maxLength: number): Promise<Question> {
  await page.getByRole('button', { name: '문항 추가', exact: true }).first().click()
  const form = page.locator('.question-add')
  await form.getByLabel('문항 내용').fill(questionText)
  await form.getByLabel('최대 글자 수').fill(String(maxLength))
  expect(await form.evaluate((element) => (element as HTMLFormElement).checkValidity())).toBe(true)
  const responsePromise = page.waitForResponse(
    (response) =>
      /\/api\/v1\/cover-letters\/[^/]+\/questions$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'POST' &&
      response.status() === 201,
  )
  await page.getByRole('button', { name: '추가', exact: true }).click()
  const question = (await (await responsePromise).json()) as Question
  await expect(form).toHaveCount(0)
  return question
}

async function openQuestionForm(page: Page): Promise<void> {
  if (
    await page
      .locator('.question-meta__form')
      .isVisible()
      .catch(() => false)
  )
    return
  await page.getByTestId('open-question-form').click()
  await expect(page.locator('.question-meta__form')).toBeVisible()
}

async function closeQuestionForm(page: Page): Promise<void> {
  const close = page.getByRole('button', { name: '문항 수정 닫기' })
  if (await close.isVisible().catch(() => false)) await close.click()
}

async function openGenerationSettings(page: Page): Promise<void> {
  if (
    await page
      .locator('.generation-panel')
      .isVisible()
      .catch(() => false)
  )
    return
  await page.getByTestId('open-generation').click()
  await expect(page.locator('.generation-panel')).toBeVisible()
}

async function openReviewTab(page: Page): Promise<void> {
  await page.getByTestId('assist-tab-review').click()
}

function attachSafeBrowserDiagnostics(page: Page): void {
  page.on('response', (response) => {
    const method = response.request().method()
    if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') return
    const path = new URL(response.url()).pathname
    console.log(`[P7_BROWSER_RESPONSE] method=${method} path=${path} status=${response.status()}`)
  })
  page.on('requestfailed', (request) => {
    const path = new URL(request.url()).pathname
    console.log(
      `[P7_BROWSER_REQUEST_FAILED] method=${request.method()} path=${path} reason=${request.failure()?.errorText ?? 'unknown'}`,
    )
  })
  page.on('pageerror', (error) => {
    console.log(`[P7_BROWSER_PAGE_ERROR] name=${error.name} message=${error.message}`)
  })
}

async function deleteSelectedQuestion(page: Page, questionId: string): Promise<void> {
  await openQuestionForm(page)
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.endsWith(`/questions/${questionId}`) &&
      response.request().method() === 'DELETE' &&
      response.status() === 204,
  )
  await page.getByRole('button', { name: '문항 삭제', exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await responsePromise
  await expect(page.getByRole('status').filter({ hasText: '문항을 삭제했어요.' })).toBeVisible()
  await expect(page.getByRole('button', { name: '삭제 확인', exact: true })).toHaveCount(0)
}

async function moveQuestionUp(page: Page, question: Question): Promise<void> {
  await selectQuestion(page, question.questionText)
  await openQuestionForm(page)
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.endsWith('/questions/order') &&
      response.request().method() === 'PATCH' &&
      response.status() === 200,
  )
  await page.getByRole('button', { name: `${question.questionOrder}번 문항 앞으로 이동` }).click()
  await responsePromise
  await expect(
    page.getByRole('status').filter({ hasText: '문항 순서를 저장했어요.' }),
  ).toBeVisible()
  await closeQuestionForm(page)
}

async function editQuestionMemo(page: Page, questionText: string, memo: string): Promise<void> {
  await selectQuestion(page, questionText)
  await openQuestionForm(page)
  await page.locator('.question-meta__form').getByLabel('메모').fill(memo)
  const responsePromise = page.waitForResponse(
    (response) =>
      /\/api\/v1\/cover-letters\/[^/]+\/questions\/[^/]+$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'PUT' &&
      response.status() === 200,
  )
  await page.getByRole('button', { name: '문항 저장', exact: true }).click()
  await responsePromise
  await expect(
    page.getByRole('status').filter({ hasText: '문항 정보를 저장했어요.' }),
  ).toBeVisible()
  await closeQuestionForm(page)
}

async function exerciseTitleConflict(page: Page, coverLetterId: string): Promise<void> {
  const current = await getCoverLetter(page, coverLetterId)
  const serverUpdate = await putJson<CoverLetter>(page, `/api/v1/cover-letters/${coverLetterId}`, {
    title: '서버에서 먼저 변경한 제목',
    version: current.version,
  })
  expect(serverUpdate.status).toBe(200)
  await page.getByTestId('open-completion').click()
  await page.getByRole('button', { name: '제목 수정', exact: true }).click()
  await page.getByLabel('자기소개서 제목').fill('브라우저의 미저장 제목')
  const conflictResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/cover-letters/${coverLetterId}` &&
      response.request().method() === 'PUT' &&
      response.status() === 409,
  )
  await page.getByRole('button', { name: '제목 저장', exact: true }).click()
  await conflictResponse
  const conflict = page.getByRole('alertdialog')
  await expect(conflict).toContainText('지금 저장된 내용')
  await expect(conflict).toContainText('브라우저의 미저장 제목')
  const reappliedResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/cover-letters/${coverLetterId}` &&
      response.request().method() === 'PUT' &&
      response.status() === 200,
  )
  await conflict.getByRole('button', { name: '내가 쓰던 내용으로 저장' }).click()
  await reappliedResponse
  await expect(page.getByRole('status').filter({ hasText: '제목을 저장했어요.' })).toBeVisible()
  await expect(conflict).toHaveCount(0)
}

async function exerciseQuestionConflict(
  page: Page,
  coverLetterId: string,
  originalQuestion: Question,
): Promise<void> {
  const current = questionById(await getCoverLetter(page, coverLetterId), originalQuestion.id)
  const serverUpdate = await putJson<Question>(
    page,
    `/api/v1/cover-letters/${coverLetterId}/questions/${current.id}`,
    {
      questionOrder: current.questionOrder,
      questionText: '서버에서 먼저 변경한 문항',
      maxLength: 777,
      memo: '서버에서 먼저 변경한 메모',
      version: current.version,
    },
  )
  expect(serverUpdate.status).toBe(200)

  await openQuestionForm(page)
  const questionForm = page.locator('.question-meta__form')
  await questionForm.getByLabel('문항 내용').fill(originalQuestion.questionText)
  await questionForm.getByLabel('최대 글자 수').fill('1000')
  await questionForm.getByLabel('메모').fill('브라우저의 미저장 메모')
  const conflictResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname ===
        `/api/v1/cover-letters/${coverLetterId}/questions/${current.id}` &&
      response.request().method() === 'PUT' &&
      response.status() === 409,
  )
  await page.getByRole('button', { name: '문항 저장', exact: true }).click()
  await conflictResponse

  const conflict = page.getByRole('alertdialog')
  await expect(conflict).toContainText('서버에서 먼저 변경한 문항')
  await expect(conflict).toContainText('최대 글자 수: 777')
  await expect(conflict).toContainText('메모: 서버에서 먼저 변경한 메모')
  await expect(conflict).toContainText(originalQuestion.questionText)
  await expect(conflict).toContainText('메모: 브라우저의 미저장 메모')

  // 충돌 안내가 보이도록 문항 수정 sheet는 닫히므로 다시 열어 편집한다.
  await openQuestionForm(page)
  await questionForm.getByLabel('문항 내용').fill('충돌 뒤 반응형 편집 값')
  const reappliedResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname ===
        `/api/v1/cover-letters/${coverLetterId}/questions/${current.id}` &&
      response.request().method() === 'PUT' &&
      response.status() === 200,
  )
  await conflict.getByRole('button', { name: '내가 쓰던 내용으로 저장' }).click()
  const reapplied = (await (await reappliedResponse).json()) as Question
  expect(reapplied.questionText).toBe(originalQuestion.questionText)
  expect(reapplied.maxLength).toBe(1_000)
  expect(reapplied.memo).toBe('브라우저의 미저장 메모')
  await expect(
    page.getByRole('status').filter({ hasText: '문항 정보를 저장했어요.' }),
  ).toBeVisible()
  await expect(conflict).toHaveCount(0)
}

async function startGeneration(page: Page): Promise<string> {
  await openGenerationSettings(page)
  const responsePromise = page.waitForResponse(
    (response) =>
      /\/api\/v1\/cover-letters\/[^/]+\/generate$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'POST' &&
      response.status() === 202,
  )
  await page.getByTestId('generate-cover-letter').click()
  return ((await (await responsePromise).json()) as RunAccepted).agentRunId
}

async function startVerification(page: Page): Promise<string> {
  const responsePromise = page.waitForResponse(
    (response) =>
      /\/api\/v1\/cover-letter-answer-versions\/[^/]+\/verify$/.test(
        new URL(response.url()).pathname,
      ) &&
      response.request().method() === 'POST' &&
      response.status() === 202,
  )
  await page.getByTestId('verify-answer-version').click()
  return ((await (await responsePromise).json()) as RunAccepted).agentRunId
}

async function ensureQuestionHasFreshVerification(
  page: Page,
  coverLetterId: string,
  questionId: string,
): Promise<void> {
  await expect
    .poll(async () => {
      const question = questionById(await getCoverLetter(page, coverLetterId), questionId)
      return {
        current: question.currentAnswer?.id ?? null,
        verified: question.latestVerification?.answerVersionId ?? null,
        status: question.latestVerification?.status ?? null,
      }
    })
    .toEqual(
      expect.objectContaining({
        current: expect.any(String),
        verified: expect.any(String),
        status: expect.stringMatching(/^(PASSED|WARNING)$/),
      }),
    )
}

async function selectQuestion(page: Page, questionText: string): Promise<void> {
  await page.getByRole('tab', { name: questionText }).click()
}

async function getCoverLetter(page: Page, coverLetterId: string): Promise<CoverLetter> {
  return getJson<CoverLetter>(page, `/api/v1/cover-letters/${coverLetterId}`)
}

async function listVersions(page: Page, questionId: string): Promise<PageResult<AnswerVersion>> {
  return getJson<PageResult<AnswerVersion>>(
    page,
    `/api/v1/cover-letter-questions/${questionId}/versions?page=0&size=100&sort=versionNo,desc`,
  )
}

async function listVerifications(page: Page, versionId: string): Promise<PageResult<Verification>> {
  return getJson<PageResult<Verification>>(
    page,
    `/api/v1/cover-letter-answer-versions/${versionId}/verifications?page=0&size=100&sort=createdAt,desc`,
  )
}

async function waitForTerminalRun(page: Page, runId: string): Promise<AgentRun> {
  await expect
    .poll(async () => (await getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)).status, {
      timeout: 120_000,
    })
    .toMatch(/^(SUCCEEDED|FAILED|CANCELLED|INTERRUPTED)$/)
  return getJson<AgentRun>(page, `/api/v1/agent-runs/${runId}`)
}

async function deleteDocument(page: Page, documentId: string): Promise<void> {
  const detail = await getJson<DocumentDetail>(page, `/api/v1/documents/${documentId}`)
  const response = await mutateJson<unknown>(
    page,
    'DELETE',
    `/api/v1/documents/${documentId}?version=${detail.version}`,
    undefined,
  )
  expect(response.status).toBe(204)
}

async function verifyOwnerIsolation(
  browser: Browser,
  resources: {
    coverLetterId: string
    questionId: string
    answerVersionId: string
    verificationVersionId: string
    agentRunId: string
    documentId: string
  },
): Promise<void> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await signup(page, uniqueEmail('other'), 'P7 Other')
  expect(await requestStatus(page, `/api/v1/cover-letters/${resources.coverLetterId}`)).toBe(404)
  expect(
    await requestStatus(page, `/api/v1/cover-letter-questions/${resources.questionId}/versions`),
  ).toBe(404)
  expect(
    await requestStatus(
      page,
      `/api/v1/cover-letter-answer-versions/${resources.verificationVersionId}/verifications`,
    ),
  ).toBe(404)
  expect(await requestStatus(page, `/api/v1/agent-runs/${resources.agentRunId}`)).toBe(404)
  expect(await requestStatus(page, `/api/v1/documents/${resources.documentId}`)).toBe(404)
  const verifyAttempt = await postJson<unknown>(
    page,
    `/api/v1/cover-letter-answer-versions/${resources.answerVersionId}/verify`,
    { model: 'gpt-5.6-terra' },
    `p7-other-verify-${uniqueToken('other')}`,
  )
  expect(verifyAttempt.status).toBe(404)
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

function questionById(coverLetter: CoverLetter, questionId: string): Question {
  const question = coverLetter.questions.find((candidate) => candidate.id === questionId)
  if (!question) throw new Error(`Question ${questionId} is missing.`)
  return question
}

function approvedDocumentText(): string {
  return (
    '사용자 요구사항을 분석하고 Spring Boot와 PostgreSQL 기반 API를 설계했습니다. ' +
    '자동화 테스트와 사용자 격리를 적용해 안정적인 서비스를 운영했습니다. '
  ).repeat(10)
}

function analyzableDescription(): string {
  return (
    'Spring Boot와 PostgreSQL 기반 백엔드 API를 개발하고 운영합니다. ' +
    '필수 지원 자격은 백엔드 개발 경력 3년 이상이며 자동화 테스트 경험이 필요합니다. '
  ).repeat(5)
}

function uniqueEmail(scope: string): string {
  return `p7-${scope}-${uniqueToken(scope)}@example.com`
}

function uniqueToken(scope: string): string {
  return `${scope}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
}
