import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import OnboardingPage from './OnboardingPage.vue'
import type { ProfileDto } from '@/shared/api/contracts'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api/profileApi', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  listEducations: vi.fn(),
  createEducation: vi.fn(),
}))

describe('P2 onboarding', () => {
  beforeEach(() => vi.clearAllMocks())

  it('runs basic profile, primary education, desired fields, and completion without document upload', async () => {
    const initial = profile(0, false)
    const basicSaved = { ...initial, legalName: 'Onboarding User', version: 1 }
    const completed = {
      ...basicSaved,
      desiredRoles: ['Backend'],
      desiredIndustries: ['IT'],
      desiredLocations: ['Seoul'],
      profileCompleted: true,
      missingCompletionItems: [],
      version: 2,
    }
    vi.mocked(profileApi.getProfile).mockResolvedValue(initial)
    vi.mocked(profileApi.listEducations).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(profileApi.updateProfile)
      .mockResolvedValueOnce(basicSaved)
      .mockResolvedValueOnce(completed)
    vi.mocked(profileApi.createEducation).mockResolvedValue({
      id: 'education-id',
      schoolName: 'School',
      major: null,
      degree: null,
      educationStatus: 'ENROLLED',
      admissionDate: null,
      graduationDate: null,
      gpa: null,
      gpaScale: null,
      isPrimary: true,
      description: null,
      version: 0,
      createdAt: '2026-07-19T00:00:00Z',
      updatedAt: '2026-07-19T00:00:00Z',
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore(pinia)
    store.status = 'authenticated'
    store.currentUser = { id: 'user-1', email: 'one@example.com', displayName: 'One' }
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/dashboard', component: { template: '<div />' } },
        { path: '/profile/basic', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(OnboardingPage, {
      global: {
        plugins: [pinia, router, [VueQueryPlugin, { queryClient: new QueryClient() }]],
      },
    })
    await flushPromises()

    await wrapper.get('#onboarding-legalName').setValue('Onboarding User')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.updateProfile).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ legalName: 'Onboarding User', version: 0 }),
    )

    await wrapper.get('#onboarding-schoolName').setValue('School')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.createEducation).toHaveBeenCalledWith(
      expect.objectContaining({ schoolName: 'School', isPrimary: true }),
    )

    await addDesired(wrapper, '#onboarding-desiredRoles', 'Backend')
    await addDesired(wrapper, '#onboarding-desiredIndustries', 'IT')
    await addDesired(wrapper, '#onboarding-desiredLocations', 'Seoul')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(profileApi.updateProfile).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        desiredRoles: ['Backend'],
        desiredIndustries: ['IT'],
        desiredLocations: ['Seoul'],
      }),
    )
    expect(wrapper.text()).toContain('현재 프로필: 완료')
    expect(wrapper.text()).toContain('문서 업로드는 P4에서 제공됩니다')
    expect(wrapper.text()).not.toContain('파일 선택')
  })
})

async function addDesired(
  wrapper: ReturnType<typeof mount>,
  selector: string,
  value: string,
): Promise<void> {
  const input = wrapper.get(selector)
  await input.setValue(value)
  const fieldset = input.element.closest('fieldset')
  const button = fieldset?.querySelector('button')
  if (button === null || button === undefined) throw new Error('add button not found')
  button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

function profile(version: number, completed: boolean): ProfileDto {
  return {
    legalName: null,
    introduction: null,
    desiredRoles: [],
    desiredIndustries: [],
    desiredLocations: [],
    expectedGraduationDate: null,
    profileCompleted: completed,
    missingCompletionItems: [
      'LEGAL_NAME',
      'DESIRED_ROLE',
      'DESIRED_INDUSTRY',
      'DESIRED_LOCATION',
      'PRIMARY_EDUCATION',
    ],
    version,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}
