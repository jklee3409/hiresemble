import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { featureFlags } from '@/app/featureFlags'
import * as careerArtifactApi from '@/shared/api/careerArtifactApi'
import { useAuthStore } from '@/stores/auth'

import CareerArtifactSuggestion from './CareerArtifactSuggestion.vue'

vi.mock('@/shared/api/careerArtifactApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/api/careerArtifactApi')>()
  return { ...original, getCareerArtifactReadiness: vi.fn() }
})

describe('CareerArtifactSuggestion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    featureFlags.careerArtifactEnabled = true
    vi.mocked(careerArtifactApi.getCareerArtifactReadiness).mockResolvedValue(readiness())
  })

  afterEach(() => {
    featureFlags.careerArtifactEnabled = false
  })

  it('shows only missing artifact suggestions when at least one GitHub experience is verified', async () => {
    const wrapper = await mountSuggestion()
    expect(wrapper.text()).toContain('이력서 초안')
    expect(wrapper.text()).toContain('포트폴리오 초안')
    expect(careerArtifactApi.getCareerArtifactReadiness).toHaveBeenCalledOnce()
  })

  it('dismisses only for the current mount without writing browser storage', async () => {
    const localSet = vi.spyOn(Storage.prototype, 'setItem')
    const wrapper = await mountSuggestion()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '나중에')
      ?.trigger('click')
    expect(wrapper.text()).not.toContain('이력서 초안 만들기')
    expect(wrapper.text()).toContain('포트폴리오 초안 만들기')
    expect(localSet).not.toHaveBeenCalled()
    localSet.mockRestore()

    const remounted = await mountSuggestion()
    expect(remounted.text()).toContain('이력서 초안')
  })

  it('does not request readiness or render anything when the Gate 4 flag is false', async () => {
    featureFlags.careerArtifactEnabled = false
    const wrapper = await mountSuggestion()
    expect(wrapper.text()).toBe('')
    expect(careerArtifactApi.getCareerArtifactReadiness).not.toHaveBeenCalled()
  })
})

async function mountSuggestion() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: {
      id: '00000000-0000-4000-8000-000000000001',
      email: 'tester@example.com',
      displayName: '테스터',
    },
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(CareerArtifactSuggestion, {
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  await flushPromises()
  return wrapper
}

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: false,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 2,
    verifiedGitHubExperienceCount: 1,
    verifiedStrengthCount: 1,
    canGenerateResume: true,
    canGeneratePortfolio: true,
    warnings: [],
  }
}
