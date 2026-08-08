import { expect, test, type Page, type Route } from '@playwright/test'

test.skip(
  process.env.VITE_CAREER_ARTIFACT_ENABLED !== 'true',
  'Gate 4 E2E는 VITE_CAREER_ARTIFACT_ENABLED=true로 실행합니다.',
)

const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  artifact: '10000000-0000-4000-8000-000000000001',
  portfolio: '10000000-0000-4000-8000-000000000002',
  version: '20000000-0000-4000-8000-000000000001',
  portfolioVersion: '20000000-0000-4000-8000-000000000002',
  run: '30000000-0000-4000-8000-000000000001',
  retryRun: '30000000-0000-4000-8000-000000000002',
  step: '40000000-0000-4000-8000-000000000001',
  experience: '50000000-0000-4000-8000-000000000001',
  evidence: '60000000-0000-4000-8000-000000000001',
}
const NOW = '2026-08-08T00:00:00Z'

test('Resume wizard, idempotent create, SSE, prior preview, lifecycle, download and delete', async ({
  page,
}) => {
  test.setTimeout(60_000)
  const fixture = await installResumeRoutes(page)

  await page.goto('/career-artifacts')
  await expect(
    page.getByRole('heading', { name: 'AI로 만든 이력서·포트폴리오 초안' }),
  ).toBeAttached()
  await page.getByRole('link', { name: '이력서 DOCX 만들기' }).click()
  await expect(page).toHaveURL(/\/career-artifacts\/new\?type=RESUME$/)

  await page.getByRole('button', { name: '다음' }).click()
  await expect(page.getByText('확인된 경험을 선택하세요')).toBeVisible()
  await page.getByRole('checkbox', { name: /결제 전환 개선/ }).check()
  await expect(page.getByText('선택 1개')).toBeVisible()
  await page.getByRole('button', { name: '다음' }).click()
  const exactModel = page.getByRole('radio', { name: /서버 전용 모델/ })
  await exactModel.check()
  await expect(page.getByText('추천', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '다음' }).click()

  await expect(
    page.getByText('파일 생성에만 사용되며 AI 문맥으로 전송되지 않습니다.'),
  ).toBeVisible()
  expect(fixture.createRequests).toBe(0)
  await page.getByLabel('제목').fill('지원용 백엔드 이력서')
  await page.getByRole('button', { name: '파일 생성 요청' }).click()

  await expect(page).toHaveURL(`/career-artifacts/${ids.artifact}`)
  await expect(page.getByText('이전 성공 이력서 내용')).toBeVisible({ timeout: 10_000 })
  expect(fixture.createRequests).toBe(1)
  expect(fixture.createIdempotencyKeys[0]).toMatch(/^career-artifact-create:/)
  expect(fixture.createBodies[0]).toMatchObject({
    artifactType: 'RESUME',
    model: 'server-model-exact',
    experienceItemIds: [ids.experience],
  })
  expect(fixture.sseSequence).toEqual(['QUEUED', 'RUNNING', 'SUCCEEDED'])

  const firstDownload = page.getByRole('button', { name: 'Word(.docx) 다운로드' }).first()
  const downloadEvent = page.waitForEvent('download')
  await firstDownload.click()
  const download = await downloadEvent
  expect(download.suggestedFilename()).toBe('server-resume.docx')
  await expect(page.getByText(/server-resume\.docx/).first()).toBeVisible()

  await page.getByRole('button', { name: '새 버전 만들기' }).click()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('checkbox', { name: /결제 전환 개선/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  const regenerateModel = page.getByRole('radio', { name: /서버 전용 모델/ })
  if (!(await regenerateModel.isChecked())) await regenerateModel.check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('button', { name: '새 버전 생성 요청' }).click()

  await expect(page.getByText('재생성 작업을 마치지 못했어요.')).toBeVisible({ timeout: 10_000 })
  await expect(page.getByText('이전 성공 이력서 내용')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Word(.docx) 다운로드' }).first()).toBeEnabled()
  expect(fixture.regenerateRequests).toBe(1)
  expect(fixture.regenerateIdempotencyKeys[0]).toMatch(/^career-artifact-regenerate:/)

  await page.getByRole('button', { name: '보관', exact: true }).click()
  await expect(page.getByText('보관됨')).toBeVisible()
  expect(fixture.archiveVersions).toEqual([3])
  await expect(page.getByRole('button', { name: '새 버전 만들기' })).toHaveCount(0)
  await page.getByRole('button', { name: '다시 사용' }).click()
  await expect(page.getByText('사용 중')).toBeVisible()
  expect(fixture.unarchiveVersions).toEqual([4])

  await page.getByRole('button', { name: '삭제', exact: true }).click()
  const dialog = page.getByRole('alertdialog', { name: '이 생성 자료를 삭제할까요?' })
  await expect(dialog).toContainText(
    '원본으로 사용한 업로드 문서와 경험 보관함의 경험은 그대로 유지',
  )
  await dialog.getByRole('button', { name: '자료 삭제' }).click()
  await expect(page).toHaveURL('/career-artifacts')
  await expect(page.getByText('아직 만든 초안이 없어요')).toBeVisible()
  expect(fixture.deleteVersions).toEqual([5])
})

test('Portfolio keyboard preview and PPTX download stay responsive at 1440px and 390px', async ({
  page,
}) => {
  test.setTimeout(30_000)
  await installPortfolioRoutes(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`/career-artifacts/${ids.portfolio}`)
  await expect(page.getByRole('heading', { name: '포트폴리오 슬라이드 6장' })).toBeVisible()
  const tabs = page.getByRole('tab')
  await tabs.first().focus()
  await page.keyboard.press('End')
  await expect(tabs.last()).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tabpanel')).toContainText('마무리')
  await page.keyboard.press('Home')
  await expect(tabs.first()).toHaveAttribute('aria-selected', 'true')
  expect(await hasHorizontalOverflow(page)).toBe(false)

  const downloadEvent = page.waitForEvent('download')
  await page.getByRole('button', { name: 'PowerPoint(.pptx) 다운로드' }).first().click()
  expect((await downloadEvent).suggestedFilename()).toBe('server-portfolio.pptx')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await expect(page.getByRole('heading', { name: '포트폴리오 슬라이드 6장' })).toBeVisible()
  expect(await hasHorizontalOverflow(page)).toBe(false)
  await page.keyboard.press('Tab')
  expect(
    await page.evaluate(
      () => document.activeElement !== null && document.activeElement !== document.body,
    ),
  ).toBe(true)
})

test('a 409 conflict is shown once without automatic create retry and keeps valid selections', async ({
  page,
}) => {
  test.setTimeout(30_000)
  const fixture = await installResumeRoutes(page, { conflictOnCreate: true })
  await page.goto('/career-artifacts/new?type=RESUME')
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('checkbox', { name: /결제 전환 개선/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('radio', { name: /서버 전용 모델/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByLabel('제목').fill('충돌 확인 이력서')
  await page.getByRole('button', { name: '파일 생성 요청' }).click()

  await expect(page.getByText(/최신 내용을 확인한 뒤 다시 선택/)).toBeVisible()
  await page.waitForTimeout(500)
  expect(fixture.createRequests).toBe(1)
  await expect(page.getByLabel('제목')).toHaveValue('충돌 확인 이력서')
})

async function installResumeRoutes(page: Page, options: { conflictOnCreate?: boolean } = {}) {
  let phase:
    | 'empty'
    | 'queued'
    | 'succeeded'
    | 'regenerating'
    | 'failed'
    | 'archived'
    | 'active'
    | 'deleted' = 'empty'
  let createRequests = 0
  let regenerateRequests = 0
  const deleteVersions: number[] = []
  const createIdempotencyKeys: string[] = []
  const regenerateIdempotencyKeys: string[] = []
  const createBodies: unknown[] = []
  const sseSequence: string[] = []
  const archiveVersions: number[] = []
  const unarchiveVersions: number[] = []

  await page.route('**/fixture-downloads/**', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'Content-Disposition': 'attachment; filename="server-resume.docx"',
      },
      body: 'fixture-docx-bytes',
    })
  })

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') return json(route, currentUser())
    if (path === '/auth/csrf') return json(route, csrf())
    if (path === '/agent-runs' && request.method() === 'GET') return json(route, pageOf([]))
    if (path === '/career-artifacts/readiness') return json(route, readiness())
    if (path === '/career-artifacts/ai-models') return json(route, [model()])
    if (path === '/profile/experiences') return json(route, experiencePage())

    if (path === '/career-artifacts' && request.method() === 'POST') {
      createRequests += 1
      createIdempotencyKeys.push(request.headers()['idempotency-key'] ?? '')
      createBodies.push(request.postDataJSON())
      if (options.conflictOnCreate) return apiError(route, 'RESOURCE_VERSION_CONFLICT')
      phase = 'queued'
      return json(route, accepted(ids.run), 202)
    }
    if (path === '/career-artifacts' && request.method() === 'GET') {
      return json(
        route,
        pageOf(phase === 'empty' || phase === 'deleted' ? [] : [artifactSummary(phase)]),
      )
    }
    if (path === `/career-artifacts/${ids.artifact}` && request.method() === 'GET') {
      return json(route, resumeDetail(phase))
    }
    if (path === `/career-artifacts/${ids.artifact}/versions` && request.method() === 'GET') {
      return json(route, pageOf(hasSuccessfulVersion(phase) ? [resumeVersion()] : []))
    }
    if (path === `/career-artifacts/${ids.artifact}/generations`) {
      regenerateRequests += 1
      regenerateIdempotencyKeys.push(request.headers()['idempotency-key'] ?? '')
      phase = 'regenerating'
      return json(route, accepted(ids.retryRun), 202)
    }
    if (path === `/career-artifacts/${ids.artifact}/archive`) {
      archiveVersions.push((request.postDataJSON() as { version: number }).version)
      phase = 'archived'
      return json(route, resumeDetail(phase))
    }
    if (path === `/career-artifacts/${ids.artifact}/unarchive`) {
      unarchiveVersions.push((request.postDataJSON() as { version: number }).version)
      phase = 'active'
      return json(route, resumeDetail(phase))
    }
    if (path === `/career-artifacts/${ids.artifact}` && request.method() === 'DELETE') {
      deleteVersions.push(Number(url.searchParams.get('version')))
      phase = 'deleted'
      return route.fulfill({ status: 204, body: '' })
    }
    if (path === `/career-artifacts/${ids.artifact}/versions/${ids.version}/download-url`) {
      return json(route, {
        url: `${url.origin}/fixture-downloads/server-resume.docx`,
        expiresAt: '2026-08-08T00:05:00Z',
        filename: 'server-resume.docx',
      })
    }
    if (path === `/agent-runs/${ids.run}` && request.method() === 'GET') {
      return json(route, runDetail(ids.run, 'QUEUED', 'RESUME_GENERATION'))
    }
    if (path === `/agent-runs/${ids.retryRun}` && request.method() === 'GET') {
      return json(
        route,
        runDetail(
          ids.retryRun,
          ['failed', 'archived', 'active'].includes(phase) ? 'FAILED' : 'RUNNING',
          'RESUME_GENERATION',
        ),
      )
    }
    if (path === `/agent-runs/${ids.run}/events`) {
      sseSequence.splice(0, sseSequence.length, 'QUEUED', 'RUNNING', 'SUCCEEDED')
      if (phase === 'queued') phase = 'succeeded'
      return eventStream(route, [
        sse('snapshot', snapshot(ids.run, 'QUEUED', 'RESUME_GENERATION')),
        sse('progress', progress(ids.run, 2, 'RUNNING', 'RENDER_DOCX')),
        sse('terminal', terminal(ids.run, 3, 'SUCCEEDED')),
      ])
    }
    if (path === `/agent-runs/${ids.retryRun}/events`) {
      if (phase === 'regenerating') phase = 'failed'
      return eventStream(route, [
        sse('snapshot', snapshot(ids.retryRun, 'RUNNING', 'RESUME_GENERATION')),
        sse('terminal', {
          ...terminal(ids.retryRun, 2, 'FAILED'),
          retryable: true,
          safeError: { code: 'RENDER_FAILED', message: '재생성 작업을 마치지 못했어요.' },
        }),
      ])
    }
    return json(route, pageOf([]))
  })

  return {
    get createRequests() {
      return createRequests
    },
    get regenerateRequests() {
      return regenerateRequests
    },
    deleteVersions,
    createIdempotencyKeys,
    regenerateIdempotencyKeys,
    createBodies,
    sseSequence,
    archiveVersions,
    unarchiveVersions,
  }
}

async function installPortfolioRoutes(page: Page) {
  await page.route('**/fixture-downloads/**', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'Content-Disposition': 'attachment; filename="server-portfolio.pptx"',
      },
      body: 'fixture-pptx-bytes',
    })
  })
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '')
    if (path === '/auth/me') return json(route, currentUser())
    if (path === '/auth/csrf') return json(route, csrf())
    if (path === '/agent-runs') return json(route, pageOf([]))
    if (path === `/career-artifacts/${ids.portfolio}`) return json(route, portfolioDetail())
    if (path === `/career-artifacts/${ids.portfolio}/versions`) {
      return json(route, pageOf([portfolioVersion()]))
    }
    if (
      path === `/career-artifacts/${ids.portfolio}/versions/${ids.portfolioVersion}/download-url`
    ) {
      return json(route, {
        url: `${url.origin}/fixture-downloads/server-portfolio.pptx`,
        expiresAt: '2026-08-08T00:05:00Z',
        filename: 'server-portfolio.pptx',
      })
    }
    return json(route, pageOf([]))
  })
}

function resumeDetail(phase: string) {
  const successful = hasSuccessfulVersion(phase)
  const currentRunId = phase === 'regenerating' || phase === 'failed' ? ids.retryRun : ids.run
  return {
    artifact: artifactSummary(phase),
    currentVersion: successful ? resumeVersion() : null,
    preview: successful ? resumePreview() : null,
    latestRun:
      phase === 'empty' || phase === 'deleted'
        ? null
        : runSummary(
            currentRunId,
            phase === 'queued'
              ? 'QUEUED'
              : phase === 'regenerating'
                ? 'RUNNING'
                : phase === 'failed' || phase === 'archived' || phase === 'active'
                  ? 'FAILED'
                  : 'SUCCEEDED',
            'RESUME_GENERATION',
          ),
  }
}

function artifactSummary(phase: string) {
  const successful = hasSuccessfulVersion(phase)
  const lifecycleStatus = phase === 'archived' ? 'ARCHIVED' : 'ACTIVE'
  const generationStatus =
    phase === 'queued'
      ? 'QUEUED'
      : phase === 'regenerating'
        ? 'RUNNING'
        : phase === 'failed' || phase === 'archived' || phase === 'active'
          ? 'FAILED'
          : 'SUCCEEDED'
  const version =
    phase === 'queued'
      ? 1
      : phase === 'succeeded'
        ? 2
        : phase === 'regenerating'
          ? 2
          : phase === 'failed'
            ? 3
            : phase === 'archived'
              ? 4
              : 5
  return {
    id: ids.artifact,
    artifactType: 'RESUME',
    title: '지원용 백엔드 이력서',
    lifecycleStatus,
    generationStatus,
    currentVersionId: successful ? ids.version : null,
    currentVersionNo: successful ? 1 : null,
    latestAgentRunId: phase === 'queued' || phase === 'succeeded' ? ids.run : ids.retryRun,
    version,
    createdAt: NOW,
    updatedAt: NOW,
  }
}

function resumeVersion() {
  return {
    id: ids.version,
    artifactId: ids.artifact,
    versionNo: 1,
    model: 'server-model-exact',
    templateKey: 'resume-ats-v1',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSizeBytes: 2048,
    createdAt: NOW,
  }
}

function resumePreview() {
  return {
    headline: '이전 성공 이력서 내용',
    summary: '검증된 경험으로 작성한 이력서입니다.',
    sections: [
      {
        type: 'CAREER',
        title: '주요 경험',
        items: [
          {
            heading: '결제 전환 개선',
            subheading: '플랫폼 팀',
            period: '2025',
            bullets: ['검증된 근거로 전환 흐름을 개선했습니다.'],
            evidenceRefs: [evidenceRef()],
          },
        ],
      },
    ],
    warnings: ['지원 전에 표현을 직접 확인하세요.'],
  }
}

function portfolioDetail() {
  return {
    artifact: {
      id: ids.portfolio,
      artifactType: 'PORTFOLIO',
      title: '프로젝트 포트폴리오',
      lifecycleStatus: 'ACTIVE',
      generationStatus: 'SUCCEEDED',
      currentVersionId: ids.portfolioVersion,
      currentVersionNo: 1,
      latestAgentRunId: null,
      version: 1,
      createdAt: NOW,
      updatedAt: NOW,
    },
    currentVersion: portfolioVersion(),
    preview: {
      slides: Array.from({ length: 6 }, (_, index) => ({
        slideNo: index + 1,
        slideType: index === 0 ? 'COVER' : index === 5 ? 'CLOSING' : 'PROJECT_CASE_STUDY',
        title: index === 5 ? '마무리' : `슬라이드 ${index + 1}`,
        subtitle: index === 0 ? null : '검증된 프로젝트 경험',
        items: index === 0 || index === 5 ? [] : ['문제, 선택, 결과'],
        visualType: index === 0 ? 'NONE' : 'PROCESS',
        evidenceRefs: index === 0 || index === 5 ? [] : [evidenceRef()],
      })),
      warnings: [],
    },
    latestRun: null,
  }
}

function portfolioVersion() {
  return {
    id: ids.portfolioVersion,
    artifactId: ids.portfolio,
    versionNo: 1,
    model: 'server-model-exact',
    templateKey: 'portfolio-interview-v1',
    mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    fileSizeBytes: 4096,
    createdAt: NOW,
  }
}

function runSummary(runId: string, status: string, workflowType: string) {
  return {
    id: runId,
    workflowType,
    resourceType: 'CAREER_ARTIFACT',
    resourceId: ids.artifact,
    status,
    currentStep: status === 'QUEUED' ? 'LOAD_RESUME_REQUEST' : 'RENDER_DOCX',
    progressPercent: status === 'QUEUED' ? 0 : status === 'RUNNING' ? 60 : 100,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: status === 'SUCCEEDED' ? 'BALANCED' : null,
    estimatedCostUsd: 0.02,
    reservedCostUsd: 0.03,
    actualCostUsd: status === 'SUCCEEDED' ? 0.02 : 0,
    retryable: status === 'FAILED',
    cancellable: status === 'QUEUED' || status === 'RUNNING',
    requiredUserAction: null,
    stateVersion: status === 'QUEUED' ? 1 : status === 'RUNNING' ? 1 : 3,
    queuedAt: NOW,
    updatedAt: NOW,
  }
}

function runDetail(runId: string, status: string, workflowType: string) {
  return {
    ...runSummary(runId, status, workflowType),
    retryOfRunId: runId === ids.retryRun ? ids.run : null,
    rootRunId: ids.run,
    runAttemptNo: runId === ids.retryRun ? 2 : 1,
    durationMs: status === 'FAILED' ? 2_000 : null,
    startedAt: status === 'QUEUED' ? null : NOW,
    completedAt: status === 'FAILED' ? '2026-08-08T00:00:02Z' : null,
    safeError:
      status === 'FAILED'
        ? { code: 'RENDER_FAILED', message: '재생성 작업을 마치지 못했어요.' }
        : null,
    partialResult: null,
    steps: [
      {
        id: ids.step,
        stepKey: 'RENDER_DOCX',
        scopeKey: null,
        stepOrder: 6,
        status: status === 'QUEUED' ? 'PENDING' : status === 'FAILED' ? 'FAILED' : 'RUNNING',
        attempt: 1,
        maxAttempts: 2,
        startedAt: status === 'QUEUED' ? null : NOW,
        completedAt: status === 'FAILED' ? '2026-08-08T00:00:02Z' : null,
        safeError:
          status === 'FAILED'
            ? { code: 'RENDER_FAILED', message: '재생성 작업을 마치지 못했어요.' }
            : null,
      },
    ],
  }
}

function snapshot(runId: string, status: string, workflowType: string) {
  return {
    agentRunId: runId,
    stateVersion: 1,
    occurredAt: NOW,
    run: runDetail(runId, status, workflowType),
  }
}

function progress(runId: string, stateVersion: number, status: string, currentStep: string) {
  return {
    agentRunId: runId,
    stateVersion,
    occurredAt: '2026-08-08T00:00:01Z',
    status,
    currentStep,
    progressPercent: 70,
    actualCostUsd: 0.01,
  }
}

function terminal(runId: string, stateVersion: number, status: string) {
  return {
    agentRunId: runId,
    stateVersion,
    occurredAt: '2026-08-08T00:00:02Z',
    status,
    completedAt: '2026-08-08T00:00:02Z',
    actualCostUsd: 0.02,
    retryable: false,
    safeError: null,
    resourceType: 'CAREER_ARTIFACT',
    resourceId: ids.artifact,
  }
}

function accepted(runId: string) {
  return {
    agentRunId: runId,
    status: 'QUEUED',
    resourceType: 'CAREER_ARTIFACT',
    resourceId: ids.artifact,
    replayed: false,
  }
}

function model() {
  return {
    id: 'server-model-exact',
    displayName: '서버 전용 모델',
    description: '서버가 현재 허용한 정확한 모델',
    recommended: true,
  }
}

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: false,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 1,
    verifiedGitHubExperienceCount: 1,
    verifiedStrengthCount: 0,
    canGenerateResume: true,
    canGeneratePortfolio: true,
    warnings: [],
  }
}

function experiencePage() {
  return {
    items: [
      {
        id: ids.experience,
        evidenceCategory: 'PROJECT',
        title: '결제 전환 개선',
        content: '검증된 결제 전환 개선 경험입니다.',
        verificationStatus: 'VERIFIED',
        matchKind: 'NEW',
        matchedExperienceItemId: null,
        matchSimilarity: null,
        reviewRequired: false,
        sourceCount: 2,
        documentSourceCount: 1,
        githubRepositorySourceCount: 1,
        primaryDocumentName: 'resume.pdf',
        version: 1,
        createdAt: NOW,
        updatedAt: NOW,
      },
    ],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  }
}

function evidenceRef() {
  return {
    experienceItemId: ids.experience,
    evidenceId: ids.evidence,
    usageType: 'PRIMARY_EXPERIENCE',
    title: '결제 전환 개선',
  }
}

function currentUser() {
  return { id: ids.user, email: 'career@example.com', displayName: '커리어 사용자' }
}

function csrf() {
  return { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'fixture-csrf-token' }
}

function hasSuccessfulVersion(phase: string): boolean {
  return ['succeeded', 'regenerating', 'failed', 'archived', 'active'].includes(phase)
}

function pageOf(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function apiError(route: Route, code: string): Promise<void> {
  await json(
    route,
    {
      timestamp: NOW,
      status: 409,
      code,
      message: '최신 상태를 다시 확인해 주세요.',
      fieldErrors: [],
      requestId: 'fixture-request',
    },
    409,
  )
}

async function eventStream(route: Route, events: string[]): Promise<void> {
  await route.fulfill({
    status: 200,
    contentType: 'text/event-stream',
    headers: { 'Cache-Control': 'no-cache' },
    body: events.join(''),
  })
}

function sse(event: string, data: unknown): string {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
}

async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(
    () =>
      document.documentElement.scrollWidth > window.innerWidth ||
      document.body.scrollWidth > window.innerWidth,
  )
}
