import { expect, test, type Page } from '@playwright/test'

test('P2 signup, profile persistence, owner isolation, CSRF, and cache cleanup', async ({
  browser,
  page,
}) => {
  test.setTimeout(180_000)

  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
  const firstEmail = `p2-first-${runId}@example.com`
  const secondEmail = `p2-second-${runId}@example.com`
  const password = 'password-123'

  await signup(page, firstEmail, 'P2 First User', password)
  await expect(page).toHaveURL(/\/onboarding$/)

  await page.locator('#onboarding-legalName').fill('First Candidate')
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await expect(page.locator('#onboarding-schoolName')).toBeVisible()

  await page.locator('#onboarding-schoolName').fill('First University')
  await page.getByRole('button', { name: '대표 학력 저장' }).click()
  await expect(page.locator('#onboarding-desiredRoles')).toBeVisible()

  await addDesiredItem(page, '#onboarding-desiredRoles', 'Backend Engineer')
  await addDesiredItem(page, '#onboarding-desiredIndustries', 'Software')
  await addDesiredItem(page, '#onboarding-desiredLocations', 'Seoul')
  await page.getByRole('button', { name: '저장하고 확인' }).click()

  await expect(page.getByRole('heading', { name: '4. 완료 또는 추후 입력' })).toBeVisible()
  await expect(page.getByRole('strong')).toHaveText('완료')
  await expect(page.getByText('100%')).toBeVisible()

  const completedProfile = await getJson<{
    legalName: string
    profileCompleted: boolean
    missingCompletionItems: string[]
  }>(page, '/api/v1/profile')
  expect(completedProfile).toMatchObject({
    legalName: 'First Candidate',
    profileCompleted: true,
    missingCompletionItems: [],
  })

  const educationPage = await getJson<{ items: Array<{ id: string; version: number }> }>(
    page,
    '/api/v1/profile/educations?page=0&size=20&sort=createdAt,desc',
  )
  expect(educationPage.items).toHaveLength(1)
  const firstEducationId = educationPage.items[0]?.id
  expect(firstEducationId).toBeTruthy()

  await page.getByRole('button', { name: '프로필 확인' }).click()
  await expect(page).toHaveURL(/\/profile\/basic$/)
  await expect(page.locator('#profile-legalName')).toHaveValue('First Candidate')
  await page.reload()
  await expect(page.locator('#profile-legalName')).toHaveValue('First Candidate')
  await expect(page.getByText('100% 완료')).toBeVisible()

  await page.goto('/profile/education')
  await expect(page.getByRole('heading', { name: 'First University', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '수정', exact: true }).click()
  await page.locator('#education-schoolName').fill('First University Updated')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'First University Updated', exact: true }),
  ).toBeVisible()
  await page.reload()
  await expect(
    page.getByRole('heading', { name: 'First University Updated', exact: true }),
  ).toBeVisible()

  const secondContext = await browser.newContext()
  const secondPage = await secondContext.newPage()
  await signup(secondPage, secondEmail, 'P2 Second User', password)
  await expect(secondPage).toHaveURL(/\/onboarding$/)

  const missingCsrf = await mutateEducation(secondPage, firstEducationId!, null)
  expect(missingCsrf.status).toBe(403)
  expect(missingCsrf.code).toBe('CSRF_INVALID')

  const csrf = await getJson<{ headerName: string; token: string }>(secondPage, '/api/v1/auth/csrf')
  const foreignAccess = await mutateEducation(secondPage, firstEducationId!, csrf)
  expect(foreignAccess.status).toBe(404)
  expect(foreignAccess.code).toBe('RESOURCE_NOT_FOUND')
  await secondContext.close()

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/)
  await login(page, secondEmail, password)
  await page.goto('/profile/basic')
  await expect(page.locator('#profile-legalName')).toHaveValue('')
  await expect(page.locator('#profile-legalName')).not.toHaveValue('First Candidate')

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/)
  await login(page, firstEmail, password)
  await page.goto('/profile/basic')
  await expect(page.locator('#profile-legalName')).toHaveValue('First Candidate')
  await page.goto('/profile/education')
  await expect(
    page.getByRole('heading', { name: 'First University Updated', exact: true }),
  ).toBeVisible()
})

async function signup(
  page: Page,
  email: string,
  displayName: string,
  password: string,
): Promise<void> {
  await page.goto('/signup')
  await page.locator('#signup-email').fill(email)
  await page.locator('#signup-displayName').fill(displayName)
  await page.locator('#signup-password').fill(password)
  await page.locator('#signup-passwordConfirm').fill(password)
  await page.locator('#signup-termsAgreed').check()
  await page.locator('#signup-aiConsent').check()
  await page.getByRole('button', { name: '가입하기' }).click()
  await page.waitForURL(/\/onboarding$/)
}

async function login(page: Page, email: string, password: string): Promise<void> {
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.waitForURL(/\/dashboard$/)
}

async function addDesiredItem(page: Page, selector: string, value: string): Promise<void> {
  const input = page.locator(selector)
  await input.fill(value)
  await input.press('Enter')
  await expect(input).toHaveValue('')
}

async function getJson<T>(page: Page, path: string): Promise<T> {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'include' })
    if (!response.ok) throw new Error(`GET ${requestPath} failed with ${response.status}`)
    return response.json() as Promise<T>
  }, path)
}

async function mutateEducation(
  page: Page,
  educationId: string,
  csrf: { headerName: string; token: string } | null,
): Promise<{ status: number; code: string | null }> {
  return page.evaluate(
    async ({ id, csrfToken }) => {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (csrfToken !== null) headers[csrfToken.headerName] = csrfToken.token
      const response = await fetch(`/api/v1/profile/educations/${id}`, {
        method: 'PUT',
        credentials: 'include',
        headers,
        body: JSON.stringify({
          schoolName: 'Foreign Attempt',
          major: null,
          degree: null,
          educationStatus: 'ENROLLED',
          admissionDate: null,
          graduationDate: null,
          gpa: null,
          gpaScale: null,
          isPrimary: false,
          description: null,
          version: 0,
        }),
      })
      const body = (await response.json()) as { code?: string }
      return { status: response.status, code: body.code ?? null }
    },
    { id: educationId, csrfToken: csrf },
  )
}
