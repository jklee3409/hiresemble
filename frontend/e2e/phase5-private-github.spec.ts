import { expect, test, type Page, type Route } from '@playwright/test'

test.skip(
  process.env.VITE_GITHUB_SOURCE_ENABLED !== 'true' ||
    process.env.VITE_GITHUB_PRIVATE_ENABLED !== 'true' ||
    process.env.VITE_CAREER_ARTIFACT_ENABLED !== 'true',
  'Phase 5 E2E는 public/private GitHub와 Career Artifact flag가 모두 true일 때 실행합니다.',
)

const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  connection: '10000000-0000-4000-8000-000000000001',
  source: '20000000-0000-4000-8000-000000000001',
  repository: '30000000-0000-4000-8000-000000000001',
  githubRun: '40000000-0000-4000-8000-000000000001',
  githubStep: '40000000-0000-4000-8000-000000000002',
  experience: '50000000-0000-4000-8000-000000000001',
  evidence: '60000000-0000-4000-8000-000000000001',
  resume: '70000000-0000-4000-8000-000000000001',
  portfolio: '70000000-0000-4000-8000-000000000002',
  resumeVersion: '80000000-0000-4000-8000-000000000001',
  portfolioVersion: '80000000-0000-4000-8000-000000000002',
  resumeRun: '90000000-0000-4000-8000-000000000001',
  portfolioRun: '90000000-0000-4000-8000-000000000002',
}
const NOW = '2026-08-09T00:00:00Z'

test('GitHub App private source to artifacts, disconnect, and terminal account deletion', async ({
  page,
}) => {
  test.setTimeout(90_000)
  const fixture = await installPhase5Routes(page)

  await page.goto('/profile/github')
  await expect(page).toHaveURL(/\/integrations$/)
  await expect(page.getByRole('heading', { name: 'GitHub App 연결' })).toBeVisible()
  await expect(page.getByText('Metadata read', { exact: false })).toBeVisible()
  await expect(page.getByText('Contents read', { exact: false })).toBeVisible()

  await page.getByRole('button', { name: 'GitHub App 연결' }).click()
  await expect(page).toHaveURL(/github\.com\/apps\/hiresemble-test\/installations\/new/)
  await page.getByRole('link', { name: 'mock-setup' }).click()
  await expect(page).toHaveURL(/github-app-connections\/setup\/callback/)
  await page.getByRole('link', { name: 'mock-oauth-start' }).click()
  await expect(page).toHaveURL(/github\.com\/login\/oauth\/authorize/)
  await page.getByRole('link', { name: 'mock-oauth' }).click()
  await expect(page).toHaveURL(/\/integrations$/)
  await expect(page.getByText('GitHub App 연결을 확인했어요.')).toBeVisible()
  await expect(page.getByText('phase5-org', { exact: true })).toBeVisible()
  await expect(page.getByText('연결됨', { exact: true })).toBeVisible()

  const privateForm = page.locator('form.private-source-form')
  await privateForm
    .getByLabel('GitHub 계정 또는 private 저장소 URL')
    .fill('https://github.com/phase5-org/private-platform')
  await privateForm.getByRole('checkbox').check()
  await privateForm.getByRole('button', { name: 'Private 저장소 불러오기' }).click()
  await expect(page).toHaveURL(new RegExp(`source=${ids.source}`))
  await expect(page.getByText('Private').first()).toBeVisible()
  await page.getByLabel('phase5-org/private-platform 선택').check()
  await page.getByRole('button', { name: '선택 저장하고 분석 시작' }).click()
  // 이 fixture는 terminal SSE가 곧바로 도착하므로 진행 중에만 보이는 단계 이름 대신
  // GitHub run monitor가 붙었는지로 확인한다.
  await expect(page.locator('.github-run-monitor')).toBeVisible()
  await expect(page.getByText('완료').first()).toBeVisible({ timeout: 10_000 })
  expect(fixture.privateSourceBody).toMatchObject({
    accessMode: 'GITHUB_APP',
    connectionId: ids.connection,
  })
  expect(fixture.selectedRepositoryIds).toEqual([ids.repository])

  await page.goto(`/profile/experiences?selected=${ids.experience}`)
  // 목록 카드와 상세 panel이 같은 제목을 함께 보여 주므로 첫 번째만 확인한다.
  await expect(page.getByText('Private GitHub 성능 개선').first()).toBeVisible()
  await page.getByRole('button', { name: '활용 승인' }).first().click()
  await expect(page.getByText('활용 승인').first()).toBeVisible()
  expect(fixture.verificationRequests).toBe(1)

  await generateArtifact(page, 'RESUME', 'Private 경험 이력서')
  await expect(page).toHaveURL(`/career-artifacts/${ids.resume}`)
  await expect(page.getByText('Private 경험 기반 이력서')).toBeVisible()
  const resumeDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Word(.docx) 다운로드' }).first().click()
  expect((await resumeDownload).suggestedFilename()).toBe('phase5-resume.docx')

  await generateArtifact(page, 'PORTFOLIO', 'Private 경험 포트폴리오')
  await expect(page).toHaveURL(`/career-artifacts/${ids.portfolio}`)
  await expect(page.getByRole('heading', { name: '포트폴리오 슬라이드 6장' })).toBeVisible()
  const portfolioDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: 'PowerPoint(.pptx) 다운로드' }).first().click()
  expect((await portfolioDownload).suggestedFilename()).toBe('phase5-portfolio.pptx')
  expect(fixture.artifactBodies.map((body) => body.model)).toEqual([
    'phase5-exact-model',
    'phase5-exact-model',
  ])

  await page.goto('/integrations')
  await page.getByRole('button', { name: '권한 다시 확인' }).click()
  await expect(page.getByText('권한과 저장소 범위를 다시 확인했어요.')).toBeVisible()
  await page.getByRole('button', { name: '연결 해제', exact: true }).click()
  const disconnectDialog = page.getByRole('alertdialog')
  await expect(disconnectDialog).toContainText('이미 승인한 경험')
  await disconnectDialog.getByRole('button', { name: '연결 해제하기' }).click()
  await expect(page.getByText('저장해 둔 private 기록을 지우고 있어요.')).toBeVisible()
  expect(fixture.uninstallConfirmed).toBe(true)
  expect(fixture.privateSnapshotRemoved).toBe(true)

  await page.goto(`/profile/experiences?selected=${ids.experience}`)
  // 목록 카드와 상세 panel이 같은 제목을 함께 보여 주므로 첫 번째만 확인한다.
  await expect(page.getByText('Private GitHub 성능 개선').first()).toBeVisible()
  await expect(page.getByText('활용 승인').first()).toBeVisible()

  await page.goto('/settings/account')
  await page.getByRole('button', { name: '회원 탈퇴' }).click()
  const deletionDialog = page.getByRole('dialog', { name: '계정을 영구 삭제할까요?' })
  await expect(deletionDialog).toContainText('GitHub App uninstall')
  await deletionDialog.getByLabel('현재 비밀번호').fill('Phase5-password1!')
  await deletionDialog.getByRole('checkbox').check()
  await deletionDialog.getByRole('button', { name: '계정 영구 삭제' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('회원 탈퇴가 접수됐어요.')).toBeVisible()
  expect(fixture.accountDeleteHadIdempotencyKey).toBe(false)
  expect(fixture.accountDeletionAccepted).toBe(true)

  await page.goto('/settings/account')
  await expect(page).toHaveURL(/\/login\?returnTo=%2Fsettings%2Faccount$/)
  expect(fixture.mockedGitHubPageRequests).toBe(2)
  expect(fixture.forbiddenExternalRequests).toEqual([])
})

async function generateArtifact(page: Page, type: 'RESUME' | 'PORTFOLIO', title: string) {
  await page.goto(`/career-artifacts/new?type=${type}`)
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('checkbox', { name: /Private GitHub 성능 개선/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('radio', { name: /Phase 5 정확 모델/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByLabel('제목').fill(title)
  await page.getByRole('button', { name: '파일 생성 요청' }).click()
}

async function installPhase5Routes(page: Page) {
  let localOrigin = ''
  let connected = false
  let sourcePhase: 'none' | 'waiting' | 'running' | 'ready' = 'none'
  let sourceVersion = 1
  let verified = false
  let withdrawn = false
  let mockedGitHubPageRequests = 0
  let privateSourceBody: Record<string, unknown> | null = null
  let verificationRequests = 0
  let uninstallConfirmed = false
  let privateSnapshotRemoved = false
  let accountDeleteHadIdempotencyKey = false
  let accountDeletionAccepted = false
  const selectedRepositoryIds: string[] = []
  const artifactBodies: Array<Record<string, unknown>> = []
  const forbiddenExternalRequests: string[] = []

  await page.context().route('https://**/*', async (route) => {
    forbiddenExternalRequests.push(route.request().url())
    await route.abort('blockedbyclient')
  })

  await page.context().route(/^https:\/\/github\.com\/.*$/, async (route) => {
    mockedGitHubPageRequests += 1
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/installations/new')) {
      return route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<meta charset="utf-8"><a aria-label="mock-setup" href="${localOrigin}/api/v1/github-app-connections/setup/callback?state=${'a'.repeat(43)}&installation_id=91&setup_action=install">continue</a>`,
      })
    }
    if (url.pathname === '/login/oauth/authorize') {
      return route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<meta charset="utf-8"><a aria-label="mock-oauth" href="${localOrigin}/api/v1/github-app-connections/oauth/callback?state=${'b'.repeat(43)}&code=mock-one-time-code">authorize</a>`,
      })
    }
    await route.abort('blockedbyclient')
  })

  await page.route('**/fixture-downloads/**', async (route) => {
    const portfolio = route.request().url().endsWith('.pptx')
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': portfolio
          ? 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
          : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'Content-Disposition': `attachment; filename="${portfolio ? 'phase5-portfolio.pptx' : 'phase5-resume.docx'}"`,
      },
      body: portfolio ? 'fixture-pptx' : 'fixture-docx',
    })
  })

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    localOrigin = url.origin
    const path = url.pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') {
      if (withdrawn) return apiError(route, 401, 'AUTHENTICATION_REQUIRED')
      return json(route, currentUser())
    }
    if (path === '/auth/csrf') return json(route, csrf())
    if (path === '/profile') return json(route, profile())
    if (path === '/agent-runs' && request.method() === 'GET') return json(route, pageOf([]))

    if (path === '/github-app-connections/capability') {
      return json(route, {
        enabled: true,
        configured: true,
        requiredPermissions: ['metadata:read', 'contents:read'],
      })
    }
    if (path === '/github-app-connections/installation-requests') {
      return json(route, {
        installationUrl: `https://github.com/apps/hiresemble-test/installations/new?state=${'a'.repeat(43)}`,
        expiresAt: '2026-08-09T00:10:00Z',
      })
    }
    if (path === '/github-app-connections/setup/callback') {
      return route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<meta charset="utf-8"><a aria-label="mock-oauth-start" href="https://github.com/login/oauth/authorize?client_id=fixture-client&amp;state=${'b'.repeat(43)}&amp;code_challenge=fixture&amp;code_challenge_method=S256">continue</a>`,
      })
    }
    if (path === '/github-app-connections/oauth/callback') {
      connected = true
      return route.fulfill({
        status: 302,
        headers: { Location: `${localOrigin}/profile/github?githubAppResult=connected` },
      })
    }
    if (path === '/github-app-connections' && request.method() === 'GET') {
      return json(route, { items: connected ? [connection()] : [] })
    }
    if (path === `/github-app-connections/${ids.connection}/refresh`) {
      return json(route, connection({ version: 2 }))
    }
    if (path === `/github-app-connections/${ids.connection}` && request.method() === 'DELETE') {
      uninstallConfirmed = url.searchParams.get('uninstallConfirmed') === 'true'
      privateSnapshotRemoved = true
      connected = false
      sourcePhase = 'none'
      return json(route, connection({ status: 'DISCONNECTING', version: 2 }), 202)
    }

    if (path === '/github-sources' && request.method() === 'POST') {
      privateSourceBody = request.postDataJSON() as Record<string, unknown>
      sourcePhase = 'waiting'
      sourceVersion = 2
      return json(route, accepted(ids.githubRun, ids.source, 'GITHUB_SOURCE'), 202)
    }
    if (path === '/github-sources' && request.method() === 'GET') {
      return json(route, pageOf(sourcePhase === 'none' ? [] : [sourceSummary()]))
    }
    if (path === `/github-sources/${ids.source}` && request.method() === 'GET') {
      return json(route, sourceDetail())
    }
    if (path === `/github-sources/${ids.source}/repositories`) {
      return json(route, pageOf([privateRepository()]))
    }
    if (path === `/github-sources/${ids.source}/repository-selection`) {
      const body = request.postDataJSON() as { repositoryIds: string[] }
      selectedRepositoryIds.splice(0, selectedRepositoryIds.length, ...body.repositoryIds)
      sourcePhase = 'running'
      sourceVersion = 3
      return json(route, accepted(ids.githubRun, ids.source, 'GITHUB_SOURCE'), 202)
    }
    if (path === `/agent-runs/${ids.githubRun}` && request.method() === 'GET') {
      return json(route, githubRun(sourcePhase === 'ready' ? 'SUCCEEDED' : 'RUNNING'))
    }
    if (path === `/agent-runs/${ids.githubRun}/events`) {
      sourcePhase = 'ready'
      sourceVersion = 4
      return eventStream(route, [
        sse('snapshot', {
          agentRunId: ids.githubRun,
          stateVersion: 1,
          occurredAt: NOW,
          run: githubRun('RUNNING'),
        }),
        sse('terminal', {
          agentRunId: ids.githubRun,
          stateVersion: 2,
          occurredAt: NOW,
          status: 'SUCCEEDED',
          completedAt: NOW,
          actualCostUsd: 0.01,
          retryable: false,
          safeError: null,
          resourceType: 'GITHUB_SOURCE',
          resourceId: ids.source,
        }),
      ])
    }

    if (path === '/profile/experiences' && request.method() === 'GET') {
      return json(route, pageOf([experienceItem()]))
    }
    if (path === `/profile/experiences/${ids.experience}` && request.method() === 'GET') {
      return json(route, experienceDetail())
    }
    if (path === `/profile/experiences/${ids.experience}/verification`) {
      verificationRequests += 1
      verified = true
      return json(route, experienceDetail())
    }

    if (path === '/career-artifacts/readiness') return json(route, readiness())
    if (path === '/career-artifacts/ai-models') return json(route, [model()])
    if (path === '/career-artifacts' && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      artifactBodies.push(body)
      const portfolio = body.artifactType === 'PORTFOLIO'
      return json(
        route,
        accepted(
          portfolio ? ids.portfolioRun : ids.resumeRun,
          portfolio ? ids.portfolio : ids.resume,
          'CAREER_ARTIFACT',
        ),
        202,
      )
    }
    if (path === '/career-artifacts' && request.method() === 'GET') {
      return json(
        route,
        pageOf(artifactBodies.map((body) => artifactSummary(body.artifactType === 'PORTFOLIO'))),
      )
    }
    if (path === `/career-artifacts/${ids.resume}`) return json(route, artifactDetail(false))
    if (path === `/career-artifacts/${ids.portfolio}`) return json(route, artifactDetail(true))
    if (path === `/career-artifacts/${ids.resume}/versions`) {
      return json(route, pageOf([artifactVersion(false)]))
    }
    if (path === `/career-artifacts/${ids.portfolio}/versions`) {
      return json(route, pageOf([artifactVersion(true)]))
    }
    if (path.includes('/download-url')) {
      const portfolio = path.includes(ids.portfolioVersion)
      return json(route, {
        url: `${localOrigin}/fixture-downloads/${portfolio ? 'phase5-portfolio.pptx' : 'phase5-resume.docx'}`,
        expiresAt: '2026-08-09T00:05:00Z',
        filename: portfolio ? 'phase5-portfolio.pptx' : 'phase5-resume.docx',
      })
    }

    if (path === '/account' && request.method() === 'DELETE') {
      accountDeleteHadIdempotencyKey = request.headers()['idempotency-key'] !== undefined
      accountDeletionAccepted = true
      withdrawn = true
      return json(
        route,
        {
          deletionRequestId: 'a0000000-0000-4000-8000-000000000001',
          purgeBy: '2026-08-10T00:00:00Z',
        },
        202,
      )
    }

    return json(route, pageOf([]))
  })

  return {
    get privateSourceBody() {
      return privateSourceBody
    },
    selectedRepositoryIds,
    get verificationRequests() {
      return verificationRequests
    },
    artifactBodies,
    get uninstallConfirmed() {
      return uninstallConfirmed
    },
    get privateSnapshotRemoved() {
      return privateSnapshotRemoved
    },
    get accountDeleteHadIdempotencyKey() {
      return accountDeleteHadIdempotencyKey
    },
    get accountDeletionAccepted() {
      return accountDeletionAccepted
    },
    get mockedGitHubPageRequests() {
      return mockedGitHubPageRequests
    },
    forbiddenExternalRequests,
  }

  function sourceSummary() {
    return {
      id: ids.source,
      sourceKind: 'ACCOUNT',
      accountType: 'ORGANIZATION',
      canonicalUrl: 'https://github.com/phase5-org',
      ownerLogin: 'phase5-org',
      repositoryName: null,
      accessMode: 'GITHUB_APP',
      connectionId: ids.connection,
      status:
        sourcePhase === 'waiting' ? 'WAITING_USER' : sourcePhase === 'running' ? 'QUEUED' : 'READY',
      discoveredRepositoryCount: 1,
      selectedRepositoryCount: sourcePhase === 'waiting' ? 0 : 1,
      repositoryDiscoveryTruncated: false,
      newExperienceCount: sourcePhase === 'ready' ? 1 : 0,
      corroboratedExperienceCount: 0,
      reviewRequiredCount: sourcePhase === 'ready' ? 1 : 0,
      rejectedCandidateCount: 0,
      snapshotIncomplete: false,
      latestAgentRunId: sourcePhase === 'waiting' ? null : ids.githubRun,
      lastSuccessfulSyncAt: sourcePhase === 'ready' ? NOW : null,
      version: sourceVersion,
      createdAt: NOW,
      updatedAt: NOW,
    }
  }

  function sourceDetail() {
    return {
      source: sourceSummary(),
      requiredUserAction:
        sourcePhase === 'waiting'
          ? {
              type: 'SELECT_GITHUB_REPOSITORIES',
              resource: {
                resourceType: 'GITHUB_SOURCE',
                resourceId: ids.source,
                displayLabel: 'phase5-org',
              },
              route: '/profile/github',
              message: '분석할 저장소를 선택해 주세요.',
            }
          : null,
    }
  }

  function experienceItem() {
    return {
      id: ids.experience,
      evidenceCategory: 'PROJECT',
      title: 'Private GitHub 성능 개선',
      content: 'private repository의 처리 시간을 줄인 승인 가능한 경험입니다.',
      verificationStatus: verified ? 'VERIFIED' : 'PENDING',
      matchKind: 'NEW',
      matchedExperienceItemId: null,
      matchSimilarity: null,
      reviewRequired: false,
      sourceCount: 1,
      documentSourceCount: 0,
      githubRepositorySourceCount: 1,
      primaryDocumentName: null,
      primaryGitHubRepositoryName: 'phase5-org/private-platform',
      version: verified ? 2 : 1,
      createdAt: NOW,
      updatedAt: NOW,
    }
  }

  function experienceDetail() {
    return {
      item: experienceItem(),
      sources: [
        {
          evidenceId: ids.evidence,
          sourceType: 'GITHUB_REPOSITORY',
          documentId: null,
          verificationStatus: verified ? 'VERIFIED' : 'PENDING',
          relationKind: 'PRIMARY_SOURCE',
          similarity: null,
          githubSourceId: ids.source,
          githubRepositoryId: ids.repository,
          repositoryName: 'phase5-org/private-platform',
          repositoryUrl: null,
          commitShaShort: 'abcdef123456',
          capturedAt: NOW,
          sourceExcerpt: '민감한 원문 없이 검토 가능한 요약 근거입니다.',
          sourceDeletedAt: privateSnapshotRemoved ? NOW : null,
          createdAt: NOW,
        },
      ],
    }
  }
}

function connection(overrides: Record<string, unknown> = {}) {
  return {
    id: ids.connection,
    targetAccountLogin: 'phase5-org',
    targetAccountType: 'ORGANIZATION',
    repositorySelection: 'SELECTED',
    status: 'ACTIVE',
    manageUrl: 'https://github.com/settings/installations/91',
    version: 1,
    connectedAt: NOW,
    verifiedAt: NOW,
    lastCheckedAt: NOW,
    disconnectedAt: null,
    ...overrides,
  }
}

function privateRepository() {
  return {
    id: ids.repository,
    ownerLogin: 'phase5-org',
    repositoryName: 'private-platform',
    canonicalUrl: 'https://github.com/phase5-org/private-platform',
    description: '선택한 private repository',
    defaultBranch: 'main',
    visibility: 'PRIVATE',
    fork: false,
    archived: false,
    selected: false,
    pushedAt: NOW,
  }
}

function githubRun(status: 'RUNNING' | 'SUCCEEDED') {
  return {
    id: ids.githubRun,
    workflowType: 'GITHUB_INGESTION',
    resourceType: 'GITHUB_SOURCE',
    resourceId: ids.source,
    status,
    currentStep: status === 'RUNNING' ? 'EXTRACT_GITHUB_CANDIDATES' : 'FINALIZE_GITHUB_SOURCE',
    progressPercent: status === 'RUNNING' ? 60 : 100,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: status === 'SUCCEEDED' ? 'BALANCED' : null,
    estimatedCostUsd: 0.01,
    reservedCostUsd: 0.02,
    actualCostUsd: status === 'SUCCEEDED' ? 0.01 : 0,
    retryable: false,
    cancellable: status === 'RUNNING',
    requiredUserAction: null,
    stateVersion: status === 'RUNNING' ? 1 : 2,
    queuedAt: NOW,
    updatedAt: NOW,
    retryOfRunId: null,
    rootRunId: ids.githubRun,
    runAttemptNo: 1,
    durationMs: status === 'SUCCEEDED' ? 2_000 : null,
    startedAt: NOW,
    completedAt: status === 'SUCCEEDED' ? NOW : null,
    safeError: null,
    partialResult: null,
    steps: [
      {
        id: ids.githubStep,
        stepKey: 'EXTRACT_GITHUB_CANDIDATES',
        scopeKey: null,
        stepOrder: 6,
        status,
        attempt: 1,
        maxAttempts: 2,
        startedAt: NOW,
        completedAt: status === 'SUCCEEDED' ? NOW : null,
        safeError: null,
      },
    ],
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

function model() {
  return {
    id: 'phase5-exact-model',
    displayName: 'Phase 5 정확 모델',
    description: '사용자가 선택한 exact model fixture',
    recommended: true,
  }
}

function artifactSummary(portfolio: boolean) {
  return {
    id: portfolio ? ids.portfolio : ids.resume,
    artifactType: portfolio ? 'PORTFOLIO' : 'RESUME',
    title: portfolio ? 'Private 경험 포트폴리오' : 'Private 경험 이력서',
    lifecycleStatus: 'ACTIVE',
    generationStatus: 'SUCCEEDED',
    currentVersionId: portfolio ? ids.portfolioVersion : ids.resumeVersion,
    currentVersionNo: 1,
    latestAgentRunId: portfolio ? ids.portfolioRun : ids.resumeRun,
    version: 1,
    createdAt: NOW,
    updatedAt: NOW,
  }
}

function artifactVersion(portfolio: boolean) {
  return {
    id: portfolio ? ids.portfolioVersion : ids.resumeVersion,
    artifactId: portfolio ? ids.portfolio : ids.resume,
    versionNo: 1,
    model: 'phase5-exact-model',
    templateKey: portfolio ? 'portfolio-interview-v1' : 'resume-ats-v1',
    mimeType: portfolio
      ? 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
      : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSizeBytes: 4096,
    createdAt: NOW,
  }
}

function artifactDetail(portfolio: boolean) {
  return {
    artifact: artifactSummary(portfolio),
    currentVersion: artifactVersion(portfolio),
    preview: portfolio ? portfolioPreview() : resumePreview(),
    latestRun: null,
  }
}

function resumePreview() {
  return {
    headline: 'Private 경험 기반 이력서',
    summary: '승인된 canonical experience만 사용했습니다.',
    sections: [
      {
        type: 'PROJECT',
        title: '주요 경험',
        items: [
          {
            heading: 'Private GitHub 성능 개선',
            subheading: 'phase5-org',
            period: '2026',
            bullets: ['검증된 근거로 처리 시간을 개선했습니다.'],
            evidenceRefs: [evidenceRef()],
          },
        ],
      },
    ],
    warnings: [],
  }
}

function portfolioPreview() {
  return {
    slides: Array.from({ length: 6 }, (_, index) => ({
      slideNo: index + 1,
      slideType: index === 0 ? 'COVER' : index === 5 ? 'CLOSING' : 'PROJECT_CASE_STUDY',
      title: index === 5 ? '마무리' : `Private 프로젝트 ${index + 1}`,
      subtitle: index === 0 ? null : '승인된 canonical experience',
      items: index === 0 || index === 5 ? [] : ['문제, 선택, 결과'],
      visualType: index === 0 ? 'NONE' : 'PROCESS',
      evidenceRefs: index === 0 || index === 5 ? [] : [evidenceRef()],
    })),
    warnings: [],
  }
}

function evidenceRef() {
  return {
    experienceItemId: ids.experience,
    evidenceId: ids.evidence,
    usageType: 'PRIMARY_EXPERIENCE',
    title: 'Private GitHub 성능 개선',
  }
}

function accepted(runId: string, resourceId: string, resourceType: string) {
  return { agentRunId: runId, status: 'QUEUED', resourceType, resourceId, replayed: false }
}

function currentUser() {
  return { id: ids.user, email: 'phase5@example.com', displayName: 'Phase 5 사용자' }
}

function csrf() {
  return { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'phase5-csrf-fixture' }
}

function profile() {
  return {
    legalName: 'Phase 5 사용자',
    introduction: null,
    desiredRoles: [],
    desiredIndustries: [],
    desiredLocations: [],
    expectedGraduationDate: null,
    profileCompleted: true,
    missingCompletionItems: [],
    version: 1,
    createdAt: NOW,
    updatedAt: NOW,
  }
}

function pageOf(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function apiError(route: Route, status: number, code: string): Promise<void> {
  await json(
    route,
    {
      timestamp: NOW,
      status,
      code,
      message: '요청을 처리할 수 없습니다.',
      fieldErrors: [],
      requestId: 'phase5-fixture-request',
    },
    status,
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
