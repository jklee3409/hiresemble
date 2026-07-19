import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, type Pinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, type Router } from 'vue-router'

import App from '@/App.vue'
import { createAppRouter } from '@/router'
import * as authApi from '@/shared/api/authApi'
import type { AuthSessionDto, ErrorResponseDto, ProfileDto } from '@/shared/api/contracts'
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
  listEducations: vi.fn(),
  createEducation: vi.fn(),
}))

describe('P1 authentication forms', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(authenticationRequired())
    vi.mocked(profileApi.getProfile).mockResolvedValue(emptyProfile())
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
    expect(wrapper.text()).toContain('필요한 정보를 단계별로 입력하세요.')
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
