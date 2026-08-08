import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'
import * as careerArtifactApi from '@/shared/api/careerArtifactApi'
import { useAuthStore } from '@/stores/auth'

import CareerArtifactListPage from './CareerArtifactListPage.vue'

vi.mock('@/shared/api/careerArtifactApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/api/careerArtifactApi')>()
  return {
    ...original,
    listCareerArtifacts: vi.fn(),
    getCareerArtifactReadiness: vi.fn(),
    archiveCareerArtifact: vi.fn(),
    unarchiveCareerArtifact: vi.fn(),
  }
})

describe('CareerArtifactListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    featureFlags.careerArtifactEnabled = true
    vi.mocked(careerArtifactApi.getCareerArtifactReadiness).mockResolvedValue(readiness())
    vi.mocked(careerArtifactApi.listCareerArtifacts).mockImplementation(async (params) =>
      page([summary(params?.lifecycleStatus === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE')]),
    )
    vi.mocked(careerArtifactApi.archiveCareerArtifact).mockResolvedValue(detail('ARCHIVED'))
    vi.mocked(careerArtifactApi.unarchiveCareerArtifact).mockResolvedValue(detail('ACTIVE'))
  })

  it('renders active artifacts, exact create CTAs, pagination data and safe labels', async () => {
    const { wrapper } = await mountList('/career-artifacts')
    expect(wrapper.text()).toContain('이력서 DOCX 만들기')
    expect(wrapper.text()).toContain('포트폴리오 PPTX 만들기')
    expect(wrapper.text()).toContain('사용 중')
    expect(wrapper.text()).toContain('생성 완료')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).not.toContain('ARCHIVED')
    expect(wrapper.text()).not.toContain('SUCCEEDED')
    expect(
      wrapper.find('a[href="/agent-runs/00000000-0000-4000-8000-000000000003"]').exists(),
    ).toBe(true)
  })

  it('archives explicitly and does not expose a regeneration control in the list', async () => {
    const { wrapper } = await mountList('/career-artifacts')
    expect(wrapper.text()).not.toContain('새 버전')
    const archiveButton = wrapper.findAll('button').find((button) => button.text() === '보관')
    await archiveButton?.trigger('click')
    await flushPromises()
    expect(careerArtifactApi.archiveCareerArtifact).toHaveBeenCalledWith(artifactId, 1)
  })

  it('canonicalizes invalid filters and shows archived items with only restore management', async () => {
    const { wrapper, router } = await mountList(
      '/career-artifacts?artifactType=BAD&lifecycleStatus=ARCHIVED&page=-1&secret=value',
    )
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/career-artifacts?lifecycleStatus=ARCHIVED')
    expect(wrapper.text()).toContain('보관됨')
    expect(wrapper.text()).toContain('다시 사용')
    expect(wrapper.text()).not.toContain('ARCHIVED')
  })
})

async function mountList(path: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: { id: 'user-1', email: 'tester@example.com', displayName: '테스터' },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/career-artifacts', name: 'career-artifacts', component: CareerArtifactListPage },
      {
        path: '/career-artifacts/new',
        name: 'career-artifact-new',
        component: { template: '<p>new</p>' },
      },
      {
        path: '/career-artifacts/:careerArtifactId',
        name: 'career-artifact-detail',
        component: { template: '<p>detail</p>' },
      },
      { path: '/agent-runs/:agentRunId', component: { template: '<p>run</p>' } },
      { path: '/profile/experiences', component: { template: '<p>experiences</p>' } },
      { path: '/documents', component: { template: '<p>documents</p>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(CareerArtifactListPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return { wrapper, router }
}

const now = '2026-08-08T00:00:00Z'
const artifactId = '00000000-0000-4000-8000-000000000001'

function summary(lifecycleStatus: 'ACTIVE' | 'ARCHIVED') {
  return {
    id: artifactId,
    artifactType: 'RESUME' as const,
    title: '백엔드 이력서',
    lifecycleStatus,
    generationStatus: 'SUCCEEDED' as const,
    currentVersionId: '00000000-0000-4000-8000-000000000002',
    currentVersionNo: 1,
    latestAgentRunId: '00000000-0000-4000-8000-000000000003',
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

function detail(lifecycleStatus: 'ACTIVE' | 'ARCHIVED') {
  return {
    artifact: summary(lifecycleStatus),
    currentVersion: null,
    preview: null,
    latestRun: null,
  } as never
}

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: true,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 2,
    verifiedGitHubExperienceCount: 1,
    verifiedStrengthCount: 1,
    canGenerateResume: true,
    canGeneratePortfolio: true,
    warnings: [],
  }
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 } as never
}
