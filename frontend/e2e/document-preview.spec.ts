import { expect, test, type Locator, type Page } from '@playwright/test'

const DOCUMENT_ID = '00000000-0000-4000-8000-000000000010'

test('shows the original PDF one page at a time and pages the extracted experiences', async ({
  page,
}, testInfo) => {
  await installDocumentRoutes(page, { mimeType: 'application/pdf', evidenceCount: 7 })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`/documents/${DOCUMENT_ID}`)

  const preview = page.locator('.page-preview')
  const pager = preview.getByLabel('원본 페이지')
  await expect(preview.locator('canvas')).toBeVisible({ timeout: 20_000 })
  await expect(pager).toContainText('1 / 3 페이지')
  await expect(page.locator('.document-text__preview')).toHaveCount(0)

  // 한 페이지가 스크롤 없이 통째로 보이도록 화면 높이 안에서 맞춘다.
  const frame = await preview.locator('.page-preview__frame').boundingBox()
  expect(frame?.height ?? 0).toBeLessThan(1000)

  // 첫 페이지도 빈 canvas가 아니라 실제로 그려져 있어야 한다.
  await expect.poll(() => inkPixels(preview)).toBeGreaterThan(0)
  const firstPage = await preview.locator('canvas').screenshot()

  await pager.getByRole('button', { name: '다음' }).click()
  await expect(pager).toContainText('2 / 3 페이지')
  await expect
    .poll(async () => Buffer.compare(firstPage, await preview.locator('canvas').screenshot()))
    .not.toBe(0)
  if (process.env.UI_SCREENSHOTS === 'true') {
    await preview.screenshot({ path: testInfo.outputPath('document-page-preview.png') })
  }

  await pager.getByRole('button', { name: '이전' }).click()
  await expect(pager).toContainText('1 / 3 페이지')
  await expect(pager.getByRole('button', { name: '이전' })).toBeDisabled()

  const review = page.locator('.evidence-review')
  await expect(review.locator('.evidence-card')).toHaveCount(5)
  await expect(review.locator('.review-toolbar')).toContainText('7개 소재 중 1–5번째')
  await expect(review.getByLabel('찾은 경험 페이지')).toContainText('1 / 2 페이지')

  await review.getByLabel('찾은 경험 페이지').getByRole('button', { name: '다음' }).click()
  await expect(review.locator('.evidence-card')).toHaveCount(2)
  await expect(review.locator('.review-toolbar')).toContainText('7개 소재 중 6–7번째')
})

test('falls back to the extracted text when the original is not a PDF', async ({ page }) => {
  await installDocumentRoutes(page, {
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    evidenceCount: 0,
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`/documents/${DOCUMENT_ID}`)

  await expect(page.locator('.document-text__preview')).toContainText('추출한 텍스트 미리보기')
  await expect(page.locator('.page-preview')).toHaveCount(0)
  await expect(
    page.getByText('PDF가 아닌 자료는 원본 대신 읽어 낸 내용을 보여 드려요.'),
  ).toBeVisible()
})

/*
 * canvas에 실제로 칠해진 어두운 픽셀 수. 그리지 않은 canvas는 투명(alpha 0)이므로
 * alpha까지 함께 봐야 빈 canvas와 그려진 페이지를 구분할 수 있다.
 */
function inkPixels(preview: Locator): Promise<number> {
  return preview.locator('canvas').evaluate((element) => {
    const target = element as HTMLCanvasElement
    const context = target.getContext('2d')
    if (context === null || target.width === 0) return 0
    const { data } = context.getImageData(0, 0, target.width, target.height)
    let dark = 0
    for (let index = 0; index < data.length; index += 4) {
      if ((data[index + 3] ?? 0) > 0 && (data[index] ?? 255) < 200) dark += 1
    }
    return dark
  })
}

/* xref offset까지 맞춘 최소 PDF. 실제 원본 대신 페이지 넘김을 확인하는 데만 쓴다. */
function buildPdf(pageLabels: string[]): string {
  const objects: string[] = []
  const pageIds = pageLabels.map((_, index) => 3 + index * 2)
  const fontId = 3 + pageLabels.length * 2
  objects[1] = '<</Type/Catalog/Pages 2 0 R>>'
  objects[2] =
    `<</Type/Pages/Kids[${pageIds.map((id) => `${id} 0 R`).join(' ')}]` +
    `/Count ${pageLabels.length}>>`
  pageLabels.forEach((label, index) => {
    const pageId = pageIds[index] as number
    objects[pageId] =
      `<</Type/Page/Parent 2 0 R/MediaBox[0 0 420 594]/Contents ${pageId + 1} 0 R` +
      `/Resources<</Font<</F1 ${fontId} 0 R>>>>>>`
    const stream = `BT /F1 36 Tf 40 500 Td (${label}) Tj ET`
    objects[pageId + 1] = `<</Length ${stream.length}>>\nstream\n${stream}\nendstream`
  })
  objects[fontId] = '<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>'

  let pdf = '%PDF-1.4\n'
  const offsets: number[] = []
  for (let id = 1; id <= fontId; id += 1) {
    offsets[id] = pdf.length
    pdf += `${id} 0 obj\n${objects[id]}\nendobj\n`
  }
  const xrefOffset = pdf.length
  pdf += `xref\n0 ${fontId + 1}\n0000000000 65535 f \n`
  for (let id = 1; id <= fontId; id += 1) {
    pdf += `${String(offsets[id]).padStart(10, '0')} 00000 n \n`
  }
  pdf += `trailer\n<</Size ${fontId + 1}/Root 1 0 R>>\nstartxref\n${xrefOffset}\n%%EOF\n`
  return Buffer.from(pdf, 'latin1').toString('base64')
}

async function installDocumentRoutes(
  page: Page,
  options: { mimeType: string; evidenceCount: number },
): Promise<void> {
  const json = (body: unknown) => ({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })

  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill(
      json({
        id: '00000000-0000-4000-8000-000000000001',
        email: 'preview@example.com',
        displayName: '미리보기 사용자',
      }),
    ),
  )
  await page.route('**/api/v1/profile', (route) =>
    route.fulfill(
      json({
        legalName: null,
        introduction: null,
        desiredRoles: [],
        desiredIndustries: [],
        desiredLocations: [],
        expectedGraduationDate: null,
        profileCompleted: false,
        missingCompletionItems: [
          'LEGAL_NAME',
          'DESIRED_ROLE',
          'DESIRED_INDUSTRY',
          'DESIRED_LOCATION',
          'PRIMARY_EDUCATION',
        ],
        version: 0,
        createdAt: '2026-07-28T00:00:00Z',
        updatedAt: '2026-07-28T00:00:00Z',
      }),
    ),
  )
  await page.route(`**/api/v1/documents/${DOCUMENT_ID}`, (route) =>
    route.fulfill(
      json({
        id: DOCUMENT_ID,
        documentType: 'PORTFOLIO',
        displayName: '포트폴리오',
        originalFilename: 'portfolio',
        mimeType: options.mimeType,
        fileSizeBytes: 2048,
        parseStatus: 'PARSED',
        evidenceExtractionStatus: 'SUCCEEDED',
        manualTextProvided: false,
        safeError: null,
        latestAgentRunId: null,
        version: 1,
        uploadedAt: '2026-07-27T00:00:00Z',
        updatedAt: '2026-07-28T04:00:00Z',
        pageCount: 3,
        characterCount: 120,
        parsedAt: '2026-07-28T04:00:00Z',
      }),
    ),
  )
  await page.route(`**/api/v1/documents/${DOCUMENT_ID}/text`, (route) =>
    route.fulfill(
      json({
        documentId: DOCUMENT_ID,
        text: '추출한 텍스트 미리보기',
        characterCount: 12,
        manualTextProvided: false,
        version: 1,
        updatedAt: '2026-07-28T04:00:00Z',
      }),
    ),
  )
  await page.route(`**/api/v1/documents/${DOCUMENT_ID}/download-url`, (route) =>
    route.fulfill(
      json({
        url: `data:application/pdf;base64,${buildPdf(['Page One', 'Page Two', 'Page Three'])}`,
        expiresAt: '2030-01-01T00:00:00Z',
      }),
    ),
  )
  await page.route('**/api/v1/profile/evidence*', (route) =>
    route.fulfill(
      json({
        items: Array.from({ length: options.evidenceCount }, (_, index) => ({
          id: `00000000-0000-4000-8000-${String(index + 100).padStart(12, '0')}`,
          documentId: DOCUMENT_ID,
          evidenceCategory: 'PROJECT',
          title: `경험 후보 ${index + 1}`,
          content: `자료에서 찾은 경험 ${index + 1}의 핵심 내용이에요.`,
          metadata: {},
          verificationStatus: 'PENDING',
          version: 0,
          createdAt: '2026-07-28T00:00:00Z',
          updatedAt: '2026-07-28T00:00:00Z',
        })),
        page: 0,
        size: 100,
        totalElements: options.evidenceCount,
        totalPages: options.evidenceCount > 0 ? 1 : 0,
      }),
    ),
  )
  await page.route('**/api/v1/agent-runs**', (route) =>
    route.fulfill(json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
  )
}
