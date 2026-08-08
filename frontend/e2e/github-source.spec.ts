import { expect, test, type Page, type Route } from '@playwright/test'

test.skip(
  process.env.VITE_GITHUB_SOURCE_ENABLED !== 'true',
  'Gate 2 E2E는 VITE_GITHUB_SOURCE_ENABLED=true로 실행합니다.',
)

const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  source: '10000000-0000-4000-8000-000000000001',
  run: '20000000-0000-4000-8000-000000000001',
  step: '30000000-0000-4000-8000-000000000001',
  repositoryA: '40000000-0000-4000-8000-000000000001',
  repositoryB: '40000000-0000-4000-8000-000000000002',
  experience: '50000000-0000-4000-8000-000000000001',
  evidence: '60000000-0000-4000-8000-000000000001',
}
const NOW = '2026-08-08T00:00:00Z'

test('GitHub account selection, SSE completion, provenance, unchanged refresh, and delete', async ({
  page,
}) => {
  test.setTimeout(40_000)
  const fixture = await installGitHubRoutes(page)

  await page.goto('/profile/github')
  await expect(page.getByRole('heading', { name: 'GitHub 연결', level: 1 })).toBeVisible()
  await expect(page.locator('option[value="/profile/github"]')).toHaveText('GitHub')
  await expect(page.getByText('저장소 코드를 실행하지 않으며')).toBeVisible()

  await page.getByLabel('GitHub 계정 또는 저장소 URL').fill('https://github.com/openai')
  await page.getByLabel('제가 직접 참여한 공개 계정 또는 저장소입니다.').check()
  await page.getByRole('button', { name: 'GitHub 연결 등록' }).click()

  await expect(page).toHaveURL(new RegExp(`source=${ids.source}`))
  await expect(page.getByText('저장소 선택 필요').first()).toBeVisible()
  await page.getByLabel('저장소 검색').fill('career')
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await expect(page.getByText('openai/career-alpha')).toBeVisible()
  await page
    .getByRole('navigation', { name: 'GitHub 저장소 페이지' })
    .getByRole('button', { name: '다음' })
    .click()
  await expect(page.getByText('openai/career-beta')).toBeVisible()
  await page.getByLabel('openai/career-beta 선택').check()
  await expect(page.getByText('1개 선택', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '선택 저장하고 분석 시작' }).click()

  await expect(page.getByText('경험 후보 찾기')).toBeVisible()
  await expect(page.getByText('완료').first()).toBeVisible({ timeout: 10_000 })
  await expect(page.getByText('새 경험').locator('..')).toContainText('2')
  await expect(page.getByText('기존 경험 보강').locator('..')).toContainText('1')
  expect(fixture.selectedRepositoryIds).toEqual([ids.repositoryB])
  expect(fixture.sseRequests).toBeGreaterThanOrEqual(1)

  await page.goto(`/profile/experiences?selected=${ids.experience}`)
  await expect(page.getByText('GitHub 출처')).toBeVisible()
  await expect(page.getByText('openai/career-beta')).toBeVisible()
  await expect(page.getByText('abcdef123456')).toBeVisible()
  await expect(page.getByText('공개 README와 commit 요약에서 확인한 근거입니다.')).toBeVisible()
  const repositoryLink = page.getByRole('link', { name: '공개 저장소 열기' })
  await expect(repositoryLink).toHaveAttribute('href', 'https://github.com/openai/career-beta')
  await expect(repositoryLink).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(page.getByRole('link', { name: 'GitHub 연결 보기' })).toHaveAttribute(
    'href',
    `/profile/github?source=${ids.source}`,
  )

  await page.goto(`/profile/github?source=${ids.source}`)
  await page.getByRole('button', { name: '새로고침' }).click()
  await expect(
    page.getByText('GitHub에 새로운 변경이 없어 기존 분석 결과를 유지합니다.'),
  ).toBeVisible()
  expect(fixture.refreshRequests).toBe(1)

  await page.getByRole('button', { name: '삭제', exact: true }).click()
  const dialog = page.getByRole('alertdialog', { name: '이 GitHub 연결을 삭제할까요?' })
  await expect(dialog).toContainText('이미 검토하고 승인한 경험은 경험 보관함에 유지될 수 있어요')
  await dialog.getByRole('button', { name: 'GitHub 연결 삭제' }).click()
  await expect(page.getByText('아직 등록한 GitHub 연결이 없어요.')).toBeVisible()
  expect(fixture.deleteVersion).toBe(4)
})

async function installGitHubRoutes(page: Page) {
  let phase: 'empty' | 'waiting' | 'running' | 'ready' | 'deleted' = 'empty'
  let sourceVersion = 1
  let sseRequests = 0
  let refreshRequests = 0
  let deleteVersion: number | null = null
  const selectedRepositoryIds: string[] = []

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '')

    if (path === '/auth/me') {
      return json(route, {
        id: ids.user,
        email: 'github@example.com',
        displayName: 'GitHub 사용자',
      })
    }
    if (path === '/profile') return json(route, profile())
    if (path === '/agent-runs') return json(route, pageOf([]))

    if (path === '/github-sources' && request.method() === 'POST') {
      phase = 'waiting'
      sourceVersion = 2
      return json(route, accepted(), 202)
    }
    if (path === '/github-sources' && request.method() === 'GET') {
      return json(
        route,
        pageOf(phase === 'empty' || phase === 'deleted' ? [] : [source(phase, sourceVersion)]),
      )
    }
    if (path === `/github-sources/${ids.source}` && request.method() === 'GET') {
      return json(route, sourceDetail(phase, sourceVersion))
    }
    if (path === `/github-sources/${ids.source}/repositories`) {
      const pageNumber = Number(url.searchParams.get('page') ?? '0')
      const repository = pageNumber === 0 ? repositoryA() : repositoryB()
      return json(route, {
        items: [repository],
        page: pageNumber,
        size: 20,
        totalElements: 2,
        totalPages: 2,
      })
    }
    if (path === `/github-sources/${ids.source}/repository-selection`) {
      const body = request.postDataJSON() as { repositoryIds: string[]; version: number }
      selectedRepositoryIds.splice(0, selectedRepositoryIds.length, ...body.repositoryIds)
      phase = 'running'
      sourceVersion = 3
      return json(route, accepted(), 202)
    }
    if (path === `/agent-runs/${ids.run}` && request.method() === 'GET') {
      return json(route, runDetail(phase === 'ready' ? 'SUCCEEDED' : 'RUNNING'))
    }
    if (path === `/agent-runs/${ids.run}/events`) {
      sseRequests += 1
      phase = 'ready'
      sourceVersion = 4
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'Cache-Control': 'no-cache' },
        body: [
          sse('snapshot', {
            agentRunId: ids.run,
            stateVersion: 1,
            occurredAt: NOW,
            run: runDetail('RUNNING'),
          }),
          sse('progress', {
            agentRunId: ids.run,
            stateVersion: 2,
            occurredAt: '2026-08-08T00:00:01Z',
            status: 'RUNNING',
            currentStep: 'EXTRACT_GITHUB_CANDIDATES',
            progressPercent: 70,
            actualCostUsd: 0.01,
          }),
          sse('terminal', {
            agentRunId: ids.run,
            stateVersion: 3,
            occurredAt: '2026-08-08T00:00:02Z',
            status: 'SUCCEEDED',
            completedAt: '2026-08-08T00:00:02Z',
            actualCostUsd: 0.02,
            retryable: false,
            safeError: null,
            resourceType: 'GITHUB_SOURCE',
            resourceId: ids.source,
          }),
        ].join(''),
      })
    }
    if (path === `/github-sources/${ids.source}/refresh`) {
      refreshRequests += 1
      return json(route, {
        changed: false,
        source: sourceDetail('ready', sourceVersion),
        run: null,
      })
    }
    if (path === `/github-sources/${ids.source}` && request.method() === 'DELETE') {
      deleteVersion = Number(url.searchParams.get('version'))
      phase = 'deleted'
      return route.fulfill({ status: 204, body: '' })
    }
    if (path === '/profile/experiences') return json(route, experiencePage())
    if (path === `/profile/experiences/${ids.experience}`) {
      return json(route, experienceDetail())
    }
    return json(route, pageOf([]))
  })

  return {
    selectedRepositoryIds,
    get sseRequests() {
      return sseRequests
    },
    get refreshRequests() {
      return refreshRequests
    },
    get deleteVersion() {
      return deleteVersion
    },
  }
}

function source(phase: 'waiting' | 'running' | 'ready', version: number) {
  return {
    id: ids.source,
    sourceKind: 'ACCOUNT',
    accountType: 'USER',
    canonicalUrl: 'https://github.com/openai',
    ownerLogin: 'openai',
    repositoryName: null,
    status: phase === 'waiting' ? 'WAITING_USER' : phase === 'running' ? 'QUEUED' : 'READY',
    discoveredRepositoryCount: 2,
    selectedRepositoryCount: phase === 'waiting' ? 0 : 1,
    repositoryDiscoveryTruncated: false,
    newExperienceCount: phase === 'ready' ? 2 : 0,
    corroboratedExperienceCount: phase === 'ready' ? 1 : 0,
    reviewRequiredCount: phase === 'ready' ? 1 : 0,
    rejectedCandidateCount: phase === 'ready' ? 1 : 0,
    snapshotIncomplete: false,
    latestAgentRunId: phase === 'waiting' ? null : ids.run,
    lastSuccessfulSyncAt: phase === 'ready' ? NOW : null,
    version,
    createdAt: NOW,
    updatedAt: NOW,
  }
}

function sourceDetail(
  phase: 'waiting' | 'running' | 'ready' | 'empty' | 'deleted',
  version: number,
) {
  const actual = phase === 'empty' || phase === 'deleted' ? 'waiting' : phase
  const summary = source(actual, version)
  return {
    source: summary,
    requiredUserAction:
      actual === 'waiting'
        ? {
            type: 'SELECT_GITHUB_REPOSITORIES',
            resource: {
              resourceType: 'GITHUB_SOURCE',
              resourceId: ids.source,
              displayLabel: 'https://github.com/openai',
            },
            route: '/profile/github',
            message: '분석할 공개 저장소를 선택해 주세요.',
          }
        : null,
  }
}

function accepted() {
  return {
    agentRunId: ids.run,
    status: 'QUEUED',
    resourceType: 'GITHUB_SOURCE',
    resourceId: ids.source,
    replayed: false,
  }
}

function repositoryA() {
  return repository(ids.repositoryA, 'career-alpha')
}

function repositoryB() {
  return repository(ids.repositoryB, 'career-beta')
}

function repository(id: string, name: string) {
  return {
    id,
    ownerLogin: 'openai',
    repositoryName: name,
    canonicalUrl: `https://github.com/openai/${name}`,
    description: `${name} 공개 저장소`,
    defaultBranch: 'main',
    fork: false,
    archived: false,
    selected: false,
    pushedAt: NOW,
  }
}

function runDetail(status: 'RUNNING' | 'SUCCEEDED') {
  return {
    id: ids.run,
    workflowType: 'GITHUB_INGESTION',
    resourceType: 'GITHUB_SOURCE',
    resourceId: ids.source,
    status,
    currentStep: status === 'RUNNING' ? 'EXTRACT_GITHUB_CANDIDATES' : 'FINALIZE_GITHUB_SOURCE',
    progressPercent: status === 'RUNNING' ? 40 : 100,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: status === 'RUNNING' ? null : 'BALANCED',
    estimatedCostUsd: 0.02,
    reservedCostUsd: 0.03,
    actualCostUsd: status === 'RUNNING' ? 0.01 : 0.02,
    retryable: false,
    cancellable: status === 'RUNNING',
    requiredUserAction: null,
    stateVersion: status === 'RUNNING' ? 1 : 3,
    queuedAt: NOW,
    updatedAt: NOW,
    retryOfRunId: null,
    rootRunId: ids.run,
    runAttemptNo: 1,
    durationMs: status === 'RUNNING' ? null : 2_000,
    startedAt: NOW,
    completedAt: status === 'RUNNING' ? null : '2026-08-08T00:00:02Z',
    safeError: null,
    partialResult: null,
    steps: [
      {
        id: ids.step,
        stepKey: 'EXTRACT_GITHUB_CANDIDATES',
        scopeKey: null,
        stepOrder: 6,
        status: status === 'RUNNING' ? 'RUNNING' : 'SUCCEEDED',
        attempt: 1,
        maxAttempts: 2,
        startedAt: NOW,
        completedAt: status === 'RUNNING' ? null : '2026-08-08T00:00:02Z',
        safeError: null,
      },
    ],
  }
}

function experiencePage() {
  return pageOf([experienceItem()])
}

function experienceItem() {
  return {
    id: ids.experience,
    evidenceCategory: 'PROJECT',
    title: '공개 저장소 성능 개선',
    content: '공개 저장소의 처리 시간을 줄였습니다.',
    verificationStatus: 'PENDING',
    matchKind: 'NEW',
    matchedExperienceItemId: null,
    matchSimilarity: null,
    reviewRequired: false,
    sourceCount: 1,
    documentSourceCount: 0,
    githubRepositorySourceCount: 1,
    primaryDocumentName: null,
    version: 1,
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
        verificationStatus: 'PENDING',
        relationKind: 'PRIMARY_SOURCE',
        similarity: null,
        githubSourceId: ids.source,
        githubRepositoryId: ids.repositoryB,
        repositoryName: 'openai/career-beta',
        repositoryUrl: 'https://github.com/openai/career-beta',
        commitShaShort: 'abcdef123456',
        capturedAt: NOW,
        sourceExcerpt: '공개 README와 commit 요약에서 확인한 근거입니다.',
        sourceDeletedAt: null,
        createdAt: NOW,
      },
    ],
  }
}

function profile() {
  return {
    legalName: 'GitHub 사용자',
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
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  }
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function sse(event: string, data: unknown): string {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
}
