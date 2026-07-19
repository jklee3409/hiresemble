import { expect, test, type Page } from '@playwright/test'

test.describe('P4 actual Backend document pipeline', () => {
  test.skip(
    process.env.P4_E2E_ENABLED !== 'true',
    'Requires an isolated PostgreSQL+pgvector, MinIO, Spring Fake AI profile and Chromium environment.',
  )
  test.describe.configure({ mode: 'serial' })

  test('signup → actual SSE pipeline → pending evidence review → presign → immediate delete', async ({
    page,
  }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('success'), 'P4 Success')
    const eventRequests: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/agent-runs/') && request.url().endsWith('/events'))
        eventRequests.push(request.url())
    })

    await uploadText(page, 'long-document.txt', longDocument(), '통합 이력서')
    const documentId = idFromUrl(page.url())
    await expect(page.getByText('텍스트 준비 완료')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByText('근거 추출 완료')).toBeVisible({ timeout: 120_000 })
    expect(eventRequests.length).toBeGreaterThan(0)

    const evidenceCard = page.locator('[aria-labelledby="document-evidence-heading"] li').first()
    await expect(evidenceCard).toContainText('검토 대기')
    await evidenceCard.getByRole('button', { name: '수정' }).click()
    await evidenceCard.getByLabel('제목').fill('검토한 문서 근거')
    await evidenceCard.getByRole('button', { name: '저장' }).click()
    await evidenceCard.getByRole('button', { name: '승인' }).click()
    await expect(evidenceCard).toContainText('승인됨')
    await page.reload()
    await expect(
      page.locator('[aria-labelledby="document-evidence-heading"] li').first(),
    ).toContainText('승인됨')

    const download = await createDownloadUrl(page, documentId)
    const expiresInMs = Date.parse(download.expiresAt) - Date.now()
    expect(expiresInMs).toBeGreaterThan(4 * 60_000)
    expect(expiresInMs).toBeLessThanOrEqual(5 * 60_000 + 10_000)
    const objectResponse = await page.request.get(download.url)
    expect(objectResponse.ok()).toBeTruthy()

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: '삭제', exact: true }).click()
    await expect(page).toHaveURL(/\/documents(?:\?.*)?$/)
    expect(await requestStatus(page, `/api/v1/documents/${documentId}`)).toBe(404)
    await expect
      .poll(async () => [403, 404].includes((await page.request.get(download.url)).status()), {
        timeout: 60_000,
      })
      .toBe(true)
  })

  test('short text waits for user and manual text resumes the same Agent Run', async ({ page }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('manual'), 'P4 Manual')
    await uploadText(page, 'short-document.txt', '짧은 문서', '수동 입력 문서')
    await expect(page.getByText('텍스트 입력 필요')).toBeVisible({ timeout: 120_000 })
    const runLink = page.getByRole('link', { name: '작업 진행 상세 보기' })
    const originalRun = await runLink.getAttribute('href')
    await page.getByLabel('문서 텍스트').fill(longDocument())
    await page.getByRole('button', { name: '텍스트 저장 후 재개' }).click()
    await expect(runLink).toHaveAttribute('href', originalRun ?? '')
    await expect(page.getByText('텍스트 준비 완료')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByText('근거 추출 완료')).toBeVisible({ timeout: 120_000 })
  })

  test('parse success remains visible when configured Fake embedding/extraction fails', async ({
    page,
  }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('failure'), 'P4 Failure')
    await uploadText(page, 'failure-document.txt', failureDocument(), '부분 성공 문서')
    await expect(page.getByText('텍스트 준비 완료')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByText('근거 추출 실패')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByText('문서 업로드 실패가 아닙니다')).toBeVisible()
    await expect(page.getByText(longDocument().slice(0, 40), { exact: false })).toBeVisible()
  })

  test('another user gets 404 for detail, text, download, SSE and evidence filter', async ({
    browser,
    page,
  }) => {
    test.setTimeout(240_000)
    await signup(page, uniqueEmail('owner'), 'P4 Owner')
    await uploadText(page, 'owner-document.txt', longDocument(), '소유자 문서')
    const documentId = idFromUrl(page.url())
    const runHref = await page
      .getByRole('link', { name: '작업 진행 상세 보기' })
      .getAttribute('href')
    const runId = runHref?.split('/').pop()
    expect(runId).toBeTruthy()

    const other = await browser.newContext()
    const otherPage = await other.newPage()
    await signup(otherPage, uniqueEmail('other'), 'P4 Other')
    const csrf = await getJson<{ headerName: string; token: string }>(
      otherPage,
      '/api/v1/auth/csrf',
    )
    expect(await requestStatus(otherPage, `/api/v1/documents/${documentId}`)).toBe(404)
    expect(await requestStatus(otherPage, `/api/v1/documents/${documentId}/text`)).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/documents/${documentId}/download-url`, {
        method: 'POST',
        headers: { [csrf.headerName]: csrf.token },
      }),
    ).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/agent-runs/${runId}/events`, {
        headers: { Accept: 'text/event-stream' },
      }),
    ).toBe(404)
    expect(
      await requestStatus(otherPage, `/api/v1/profile/evidence?documentId=${documentId}`),
    ).toBe(404)
    await other.close()
  })
})

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

async function uploadText(
  page: Page,
  filename: string,
  text: string,
  displayName: string,
): Promise<void> {
  await page.goto('/documents')
  await page
    .locator('#document-file')
    .setInputFiles({ name: filename, mimeType: 'text/plain', buffer: Buffer.from(text, 'utf8') })
  await page.locator('#document-upload-type').selectOption('RESUME')
  await page.locator('#document-displayName').fill(displayName)
  await page.locator('#document-upload-submit').click()
  await page.waitForURL(/\/documents\/[0-9a-f-]+\?run=[0-9a-f-]+$/)
}

async function createDownloadUrl(
  page: Page,
  documentId: string,
): Promise<{ url: string; expiresAt: string }> {
  const csrf = await getJson<{ headerName: string; token: string }>(page, '/api/v1/auth/csrf')
  return page.evaluate(
    async ({ id, token }) => {
      const response = await fetch(`/api/v1/documents/${id}/download-url`, {
        method: 'POST',
        credentials: 'include',
        headers: { [token.headerName]: token.token },
      })
      if (!response.ok) throw new Error(`download-url failed with ${response.status}`)
      return response.json() as Promise<{ url: string; expiresAt: string }>
    },
    { id: documentId, token: csrf },
  )
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
  options: { method?: string; headers?: Record<string, string> } = {},
): Promise<number> {
  return page.evaluate(
    async ({ requestPath, requestOptions }) => {
      const response = await fetch(requestPath, {
        method: requestOptions.method,
        headers: requestOptions.headers,
        credentials: 'include',
      })
      return response.status
    },
    { requestPath: path, requestOptions: options },
  )
}

function idFromUrl(url: string): string {
  const value = new URL(url).pathname.split('/').pop()
  if (!value) throw new Error('document ID missing from URL')
  return value
}

function longDocument(): string {
  return '백엔드 개발자로서 사용자 요구사항을 분석하고 안정적인 API를 설계했습니다. '.repeat(12)
}

function failureDocument(): string {
  return `${longDocument()}\nFORCE_EMBEDDING_FAILURE`
}

function uniqueEmail(scope: string): string {
  return `p4-${scope}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@example.com`
}
