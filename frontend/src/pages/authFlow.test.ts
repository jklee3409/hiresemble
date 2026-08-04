import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, type Pinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, type Router } from 'vue-router'

import App from '@/App.vue'
import { createAppRouter } from '@/router'
import * as authApi from '@/shared/api/authApi'
import type {
  AuthSessionDto,
  ErrorResponseDto,
  ProfileDto,
  ProfileEligibilityDto,
} from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'

vi.mock('@/shared/api/authApi', () => ({
  getCurrentUser: vi.fn(),
  initializeCsrf: vi.fn(),
  signup: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/shared/api/profileApi', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  getProfileEligibility: vi.fn(),
  updateProfileEligibility: vi.fn(),
  listEducations: vi.fn(),
  createEducation: vi.fn(),
}))

describe('P1 authentication forms', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(authenticationRequired())
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
    vi.mocked(profileApi.getProfileEligibility).mockResolvedValue(emptyEligibility())
    vi.mocked(profileApi.listEducations).mockResolvedValue(emptyPage())
  })

  afterEach(() => {
    document.body.replaceChildren()
  })

  it('sends the exact signup request and moves to the onboarding shell', async () => {
    vi.mocked(authApi.signup).mockResolvedValueOnce(session('signup-user'))
    const { router, wrapper } = await mountAt('/signup')

    await fillSignup(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.signup).toHaveBeenCalledWith({
      email: 'signup@example.com',
      password: 'password-123',
      displayName: 'Signup User',
      termsAgreed: true,
      aiConsent: true,
    })
    expect(router.currentRoute.value.name).toBe('onboarding')
    expect(wrapper.text()).toContain('지금 아는 만큼만 입력해도 괜찮아요.')
    expect(wrapper.get('[aria-current="step"]').text()).toContain('기본 정보')
  })

  it('uses a safe returnTo after login and rejects an external one', async () => {
    vi.mocked(authApi.login)
      .mockResolvedValueOnce(session('login-user'))
      .mockResolvedValueOnce(session('login-user-2'))
    const safe = await mountAt({
      path: '/login',
      query: { returnTo: '/onboarding?step=welcome' },
    })
    await fillLogin(safe.wrapper)
    await safe.wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(safe.router.currentRoute.value.fullPath).toBe('/onboarding?step=welcome')

    const unsafe = await mountAt({
      path: '/login',
      query: { returnTo: 'https://evil.example/dashboard' },
    })
    await fillLogin(unsafe.wrapper)
    await unsafe.wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(unsafe.router.currentRoute.value.name).toBe('dashboard')
  })

  it('maps typed server field errors to the accessible signup field', async () => {
    const response: ErrorResponseDto = {
      timestamp: '2026-07-19T00:00:00Z',
      status: 400,
      code: 'VALIDATION_ERROR',
      message: '입력값을 확인해 주세요.',
      fieldErrors: [{ field: 'email', reason: 'INVALID_FORMAT' }],
      requestId: '00000000-0000-0000-0000-000000000002',
    }
    vi.mocked(authApi.signup).mockRejectedValueOnce(ApiClientError.fromServer(response))
    const { wrapper } = await mountAt('/signup')

    await fillSignup(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const email = wrapper.get<HTMLInputElement>('#signup-email')
    expect(wrapper.text()).toContain('입력 형식을 확인해 주세요.')
    expect(email.attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(email.element)
  })

  it('shows and focuses the client email format error on signup and login', async () => {
    const signup = await mountAt('/signup')
    await fillSignup(signup.wrapper)
    await signup.wrapper.get('#signup-email').setValue('signup@invalid')
    await signup.wrapper.get('form').trigger('submit')
    await flushPromises()

    const signupEmail = signup.wrapper.get<HTMLInputElement>('#signup-email')
    expect(signup.wrapper.text()).toContain('이메일 형식을 확인해 주세요.')
    expect(signupEmail.attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(signupEmail.element)
    expect(authApi.signup).not.toHaveBeenCalled()

    signup.wrapper.unmount()
    document.body.replaceChildren()

    const login = await mountAt('/login')
    await fillLogin(login.wrapper)
    await login.wrapper.get('#login-email').setValue('login@invalid')
    await login.wrapper.get('form').trigger('submit')
    await flushPromises()

    const loginEmail = login.wrapper.get<HTMLInputElement>('#login-email')
    expect(login.wrapper.text()).toContain('이메일 형식을 확인해 주세요.')
    expect(loginEmail.attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(loginEmail.element)
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('marks invalid signup credentials as soon as the user leaves each field', async () => {
    const { wrapper } = await mountAt('/signup')
    const email = wrapper.get<HTMLInputElement>('#signup-email')
    const password = wrapper.get<HTMLInputElement>('#signup-password')

    expect(wrapper.text()).not.toContain('예시처럼 @와 도메인 주소를 모두 입력해 주세요.')

    await email.setValue('signup@invalid')
    await email.trigger('blur')
    expect(email.attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('이메일 형식을 확인해 주세요.')

    await email.setValue('signup@example.com')
    expect(email.attributes('aria-invalid')).toBe('false')

    await password.setValue('abcdefghij')
    await password.trigger('blur')
    expect(password.attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('문자, 숫자, 특수문자를 각각 1개 이상')

    await password.setValue('Abcdefg1!x')
    expect(password.attributes('aria-invalid')).toBe('false')
    expect(authApi.signup).not.toHaveBeenCalled()
  })

  it('toggles password visibility with explicit accessible names', async () => {
    const { wrapper } = await mountAt('/signup')
    const password = wrapper.get<HTMLInputElement>('#signup-password')
    const confirm = wrapper.get<HTMLInputElement>('#signup-passwordConfirm')

    expect(password.attributes('type')).toBe('password')
    expect(confirm.attributes('type')).toBe('password')

    const passwordToggle = wrapper.get('button[aria-label="비밀번호 보기"]')
    await passwordToggle.trigger('click')
    expect(password.attributes('type')).toBe('text')
    expect(passwordToggle.attributes('aria-label')).toBe('비밀번호 숨기기')

    const confirmToggle = wrapper.get('button[aria-label="비밀번호 확인 보기"]')
    await confirmToggle.trigger('click')
    expect(confirm.attributes('type')).toBe('text')
    expect(confirmToggle.attributes('aria-label')).toBe('비밀번호 확인 숨기기')
  })

  it('shows readable consent details in an accessible modal and restores trigger focus', async () => {
    const { wrapper } = await mountAt('/signup')
    const serviceTrigger = wrapper.get<HTMLButtonElement>(
      'button[aria-label="이용약관·개인정보 상세 보기"]',
    )

    await serviceTrigger.trigger('click')
    await flushPromises()

    const serviceDialog = document.body.querySelector<HTMLElement>('[role="dialog"]')
    const serviceClose = document.body.querySelector<HTMLButtonElement>(
      'button[aria-label="이용약관·개인정보 상세 닫기"]',
    )
    expect(serviceDialog?.getAttribute('aria-modal')).toBe('true')
    expect(serviceDialog?.textContent).toContain('안전하게 저장해요')
    expect(serviceDialog?.textContent).toContain('수집하는 정보')
    expect(serviceDialog?.textContent).toContain('24시간 안에 삭제해요')
    expect(serviceDialog?.textContent).not.toContain('BCrypt')
    expect(serviceDialog?.textContent).not.toContain('해시')
    expect(document.activeElement).toBe(serviceClose)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(serviceTrigger.element)

    const aiTrigger = wrapper.get<HTMLButtonElement>('button[aria-label="AI 처리 상세 보기"]')
    await aiTrigger.trigger('click')
    await flushPromises()

    const aiDialog = document.body.querySelector<HTMLElement>('[role="dialog"]')
    expect(aiDialog?.textContent).toContain('OpenAI 기반으로 처리해요')
    expect(aiDialog?.textContent).toContain('OpenAI 서비스를 개선하는 데 사용되지 않아요')
    expect(aiDialog?.textContent).toContain('최대 30일 동안 보관할 수 있어요')
    expect(aiDialog?.textContent).toContain('전화번호, 이메일, 상세 주소')
    expect(aiDialog?.textContent).not.toContain('Embedding')
    expect(aiDialog?.textContent).not.toContain('임베딩')
    expect(aiDialog?.textContent).not.toContain('API')
    expect(aiDialog?.textContent).not.toContain('마스킹')

    const confirmButton = Array.from(aiDialog?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.trim() === '내용을 확인했어요',
    )
    confirmButton?.click()
    await flushPromises()
    expect(document.activeElement).toBe(aiTrigger.element)
  })
})

async function mountAt(
  location: string | { path: string; query: Record<string, string> },
): Promise<{ pinia: Pinia; router: Router; wrapper: VueWrapper }> {
  const pinia = createPinia()
  const router = createAppRouter({ history: createMemoryHistory(), pinia })
  await router.push(location)
  await router.isReady()
  const wrapper = mount(App, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient: new QueryClient() }]],
    },
  })
  return { pinia, router, wrapper }
}

function emptyProfile(): ProfileDto {
  return {
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
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function emptyPage() {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function emptyEligibility(): ProfileEligibilityDto {
  return {
    id: 'eligibility-id',
    workAvailableDate: null,
    militaryStatus: 'UNSPECIFIED',
    overseasTravelEligibility: 'UNSPECIFIED',
    employmentDisqualificationStatus: 'UNSPECIFIED',
    version: 0,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

async function fillSignup(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#signup-email').setValue('signup@example.com')
  await wrapper.get('#signup-displayName').setValue('Signup User')
  await wrapper.get('#signup-password').setValue('password-123')
  await wrapper.get('#signup-passwordConfirm').setValue('password-123')
  await wrapper.get('#signup-termsAgreed').setValue(true)
  await wrapper.get('#signup-aiConsent').setValue(true)
}

async function fillLogin(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#login-email').setValue('login@example.com')
  await wrapper.get('#login-password').setValue('password-123')
}

function session(id: string): AuthSessionDto {
  return {
    user: { id, email: `${id}@example.com`, displayName: id },
    csrf: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: `csrf-${id}` },
  }
}

function authenticationRequired(): ApiClientError {
  return ApiClientError.fromServer({
    timestamp: '2026-07-19T00:00:00Z',
    status: 401,
    code: 'AUTHENTICATION_REQUIRED',
    message: '로그인이 필요합니다.',
    fieldErrors: [],
    requestId: '00000000-0000-0000-0000-000000000001',
  })
}
