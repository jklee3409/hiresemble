import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as careerArtifactApi from '@/shared/api/careerArtifactApi'
import { CAREER_ARTIFACT_MIME_TYPES } from '@/shared/api/careerArtifactContracts'
import { ApiClientError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import CareerArtifactDetailPage from './CareerArtifactDetailPage.vue'

vi.mock('@/shared/api/careerArtifactApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/api/careerArtifactApi')>()
  return {
    ...original,
    getCareerArtifact: vi.fn(),
    listCareerArtifactVersions: vi.fn(),
    createCareerArtifactDownloadUrl: vi.fn(),
    archiveCareerArtifact: vi.fn(),
    unarchiveCareerArtifact: vi.fn(),
    deleteCareerArtifact: vi.fn(),
  }
})

describe('CareerArtifactDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(careerArtifactApi.getCareerArtifact).mockResolvedValue(detail())
    vi.mocked(careerArtifactApi.listCareerArtifactVersions).mockResolvedValue(
      page([version(1), version(0)]),
    )
    vi.mocked(careerArtifactApi.createCareerArtifactDownloadUrl).mockResolvedValue({
      url: 'http://localhost:9000/signed-download',
      expiresAt: '2099-08-08T00:05:00Z',
      filename: 'server-name.docx',
    })
  })

  it('keeps the prior current preview and download available after a regeneration failure', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).toContain('현재 성공 버전')
    expect(wrapper.text()).toContain('이전 성공 내용 유지')
    expect(wrapper.text()).toContain('새 생성이 진행되거나 실패해도')
    expect(wrapper.text()).toContain('과거 버전은 구조화 미리보기가 제공되지 않으며')
    expect(wrapper.text()).toContain('생성 실패')
    const download = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Word(.docx) 다운로드'))
    await download?.trigger('click')
    await flushPromises()
    expect(careerArtifactApi.createCareerArtifactDownloadUrl).toHaveBeenCalledWith(
      artifactId,
      versionId,
    )
    expect(wrapper.text()).toContain('server-name.docx')
    click.mockRestore()
  })

  it('keeps raw download URLs component-local and reissues once after known expiry', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const now = vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-08-08T00:00:00Z'))
    vi.mocked(careerArtifactApi.createCareerArtifactDownloadUrl)
      .mockResolvedValueOnce({
        url: 'http://localhost:9000/first-ticket',
        expiresAt: '2026-08-08T00:05:00Z',
        filename: 'server-name.docx',
      })
      .mockResolvedValueOnce({
        url: 'http://localhost:9000/reissued-ticket',
        expiresAt: '2026-08-08T00:11:00Z',
        filename: 'server-name.docx',
      })
    const { wrapper } = await mountDetail()
    const download = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Word(.docx) 다운로드'))

    await download?.trigger('click')
    await flushPromises()
    await download?.trigger('click')
    await flushPromises()
    expect(careerArtifactApi.createCareerArtifactDownloadUrl).toHaveBeenCalledTimes(1)

    now.mockReturnValue(Date.parse('2026-08-08T00:06:00Z'))
    await download?.trigger('click')
    await flushPromises()
    expect(careerArtifactApi.createCareerArtifactDownloadUrl).toHaveBeenCalledTimes(2)
    expect(wrapper.html()).not.toContain('reissued-ticket')
    now.mockRestore()
    click.mockRestore()
  })

  it('keeps archived artifacts read-only except preview, versions, download and restore', async () => {
    vi.mocked(careerArtifactApi.getCareerArtifact).mockResolvedValue(detail('ARCHIVED'))
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).toContain('다시 사용')
    expect(wrapper.text()).not.toContain('새 버전 만들기')
    expect(wrapper.text()).not.toContain('자료 삭제')
    expect(wrapper.text()).toContain('현재 성공 버전')
  })

  it('distinguishes owner-scoped 404 from malformed server data', async () => {
    vi.mocked(careerArtifactApi.getCareerArtifact).mockRejectedValueOnce(
      new ApiClientError({ status: 404, code: 'CAREER_ARTIFACT_NOT_FOUND', message: 'not found' }),
    )
    const notFound = await mountDetail()
    expect(notFound.wrapper.text()).toContain('이 자료를 찾을 수 없어요')
    notFound.wrapper.unmount()

    vi.mocked(careerArtifactApi.getCareerArtifact).mockRejectedValueOnce(
      new ApiClientError({ status: 0, code: 'INVALID_SERVER_RESPONSE', message: 'invalid' }),
    )
    const malformed = await mountDetail()
    expect(malformed.wrapper.text()).toContain('안전하게 표시하지 못했어요')
  })
})

async function mountDetail() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: { id: 'user-1', email: 'tester@example.com', displayName: '테스터' },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/career-artifacts',
        name: 'career-artifacts',
        component: { template: '<p>list</p>' },
      },
      {
        path: '/career-artifacts/:careerArtifactId',
        name: 'career-artifact-detail',
        component: CareerArtifactDetailPage,
      },
      { path: '/agent-runs/:agentRunId', component: { template: '<p>run</p>' } },
    ],
  })
  await router.push(`/career-artifacts/${artifactId}`)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(CareerArtifactDetailPage, {
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient }]],
      stubs: { CareerArtifactRunMonitor: { template: '<div>run monitor</div>' } },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

const now = '2026-08-08T00:00:00Z'
const artifactId = '00000000-0000-4000-8000-000000000001'
const versionId = '00000000-0000-4000-8000-000000000002'

function version(versionNo: number) {
  return {
    id: versionNo === 1 ? versionId : '00000000-0000-4000-8000-000000000009',
    artifactId,
    versionNo: versionNo + 1,
    model: 'server-model',
    templateKey: 'resume-ats-v1',
    mimeType: CAREER_ARTIFACT_MIME_TYPES.RESUME,
    fileSizeBytes: 2048,
    createdAt: now,
  }
}

function detail(lifecycleStatus: 'ACTIVE' | 'ARCHIVED' = 'ACTIVE') {
  return {
    artifact: {
      id: artifactId,
      artifactType: 'RESUME' as const,
      title: '백엔드 이력서',
      lifecycleStatus,
      generationStatus: 'FAILED' as const,
      currentVersionId: versionId,
      currentVersionNo: 2,
      latestAgentRunId: '00000000-0000-4000-8000-000000000003',
      version: 4,
      createdAt: now,
      updatedAt: now,
    },
    currentVersion: version(1),
    preview: {
      headline: '이전 성공 내용 유지',
      summary: '실패한 재생성 전 성공한 버전입니다.',
      sections: [
        {
          type: 'CAREER',
          title: '경험',
          items: [
            {
              heading: '성능 개선',
              subheading: null,
              period: null,
              bullets: ['검증된 내용'],
              evidenceRefs: [],
            },
          ],
        },
      ],
      warnings: [],
    },
    latestRun: null,
  }
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 } as never
}
