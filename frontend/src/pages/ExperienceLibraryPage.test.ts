import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'
import DocumentEvidencePanel from '@/features/documents/DocumentEvidencePanel.vue'
import ExperienceLibraryPage from '@/pages/ExperienceLibraryPage.vue'
import type {
  EvidenceDto,
  ExperienceItemDetailDto,
  ExperienceItemDto,
  PageResponse,
} from '@/shared/api/contracts'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api/profileApi', () => ({
  listExperiences: vi.fn(),
  getExperience: vi.fn(),
  updateExperience: vi.fn(),
  verifyExperience: vi.fn(),
  resolveExperienceMatch: vi.fn(),
  listEvidence: vi.fn(),
  updateEvidence: vi.fn(),
  verifyEvidence: vi.fn(),
  verifyEvidenceBatch: vi.fn(),
}))

describe('ExperienceLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    featureFlags.githubSourceEnabled = false
  })

  it('shows one canonical card, compares a similar experience, and keeps it separate', async () => {
    const candidate = experience({
      id: 'experience-candidate',
      title: '결제 처리 지연 개선',
      matchKind: 'RELATED_DIFFERENT',
      matchedExperienceItemId: 'experience-target',
      matchSimilarity: 0.87,
      reviewRequired: true,
    })
    const target = experience({
      id: 'experience-target',
      title: '주문 처리 병목 개선',
      matchKind: 'NEW',
      reviewRequired: false,
    })
    vi.mocked(profileApi.listExperiences).mockResolvedValue(pageOf([candidate]))
    vi.mocked(profileApi.getExperience).mockImplementation(async (id) =>
      id === target.id ? detail(target) : detail(candidate),
    )
    vi.mocked(profileApi.resolveExperienceMatch).mockResolvedValue(
      detail({
        ...candidate,
        matchKind: 'NEW',
        matchedExperienceItemId: null,
        reviewRequired: false,
      }),
    )

    const wrapper = await mountWithApp(ExperienceLibraryPage, '/profile/experiences')

    expect(wrapper.get('h1').text()).toBe('경험 보관함')
    expect(wrapper.text()).toContain('같은 경험은 카드 하나로 모으고')
    expect(wrapper.findAll('[data-testid^="experience-card-"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('비슷한 경험 확인')
    // 안내 aside는 제거했고 문서 출처는 개수 대신 실제 문서 이름을 보여 준다.
    expect(wrapper.text()).not.toContain('같은 경험은 새 카드로 반복하지 않아요')
    expect(wrapper.get('.experience-card__meta-wide').text()).toContain('지원용 이력서.pdf 외 1곳')

    await clickButton(wrapper, '비교해서 확인')
    await flushPromises()

    expect(wrapper.text()).toContain('새로 찾은 경험')
    expect(wrapper.text()).toContain('기존 경험')
    expect(wrapper.text()).toContain('주문 처리 병목 개선')
    expect(wrapper.text()).toContain('보강 출처')

    await clickButton(wrapper, '별도 경험으로 유지')
    await flushPromises()

    expect(profileApi.resolveExperienceMatch).toHaveBeenCalledWith(candidate.id, {
      resolution: 'KEEP_SEPARATE',
      targetExperienceItemId: null,
      version: candidate.version,
    })
  })

  it('approves and edits an experience inside its own card without opening a detail panel', async () => {
    const item = experience({ reviewRequired: false, matchKind: 'NEW', documentSourceCount: 1 })
    vi.mocked(profileApi.listExperiences).mockResolvedValue(pageOf([item]))
    vi.mocked(profileApi.verifyExperience).mockResolvedValue(
      detail({ ...item, verificationStatus: 'VERIFIED' }),
    )
    vi.mocked(profileApi.updateExperience).mockResolvedValue(
      detail({ ...item, title: '결제 지연 개선' }),
    )

    const wrapper = await mountWithApp(ExperienceLibraryPage, '/profile/experiences')

    expect(wrapper.get('.experience-card__meta-wide').text()).toContain('지원용 이력서.pdf')
    expect(wrapper.get('.experience-card__meta-wide').text()).not.toContain('외 ')

    await clickButton(wrapper, '활용 승인')
    await flushPromises()
    expect(profileApi.verifyExperience).toHaveBeenCalledWith(item.id, {
      status: 'VERIFIED',
      version: item.version,
    })

    await clickButton(wrapper, '수정')
    await flushPromises()

    const editor = wrapper.get('.experience-card__editor')
    await editor.get('input.control').setValue('결제 지연 개선')
    await editor.trigger('submit')
    await flushPromises()

    expect(profileApi.updateExperience).toHaveBeenCalledWith(item.id, {
      title: '결제 지연 개선',
      content: item.content,
      version: item.version,
    })
    expect(wrapper.find('.experience-card__editor').exists()).toBe(false)
    // 편집은 카드 안에서만 일어나고 상세 패널을 열지 않는다.
    expect(wrapper.find('.experience-detail').exists()).toBe(false)
  })

  it('renders corroborating document evidence as an existing source without review actions', async () => {
    vi.mocked(profileApi.listEvidence).mockResolvedValue(pageOf([corroboratingEvidence()]))
    const wrapper = await mountWithApp(DocumentEvidencePanel, '/', {
      userId: 'user-1',
      documentId: 'document-id',
      documentName: '포트폴리오.pdf',
    })

    expect(wrapper.text()).toContain('기존 경험에 출처 추가됨')
    expect(wrapper.text()).toContain('새 카드로 만들지 않았어요')
    expect(wrapper.get('a').attributes('href')).toContain(
      '/profile/experiences?selected=experience-candidate',
    )
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(0)
    expect(wrapper.findAll('button').some((button) => button.text().includes('활용 승인'))).toBe(
      false,
    )
  })

  it('shows safe GitHub provenance, repository count, and a deleted-source tombstone', async () => {
    const item = experience({
      reviewRequired: false,
      matchKind: 'NEW',
      documentSourceCount: 0,
      githubRepositorySourceCount: 2,
      primaryDocumentName: null,
    })
    vi.mocked(profileApi.listExperiences).mockResolvedValue(pageOf([item]))
    vi.mocked(profileApi.getExperience).mockResolvedValue({
      item,
      sources: [
        {
          evidenceId: 'github-evidence',
          sourceType: 'GITHUB_REPOSITORY',
          documentId: null,
          verificationStatus: 'VERIFIED',
          relationKind: 'PRIMARY_SOURCE',
          similarity: null,
          githubSourceId: '00000000-0000-4000-8000-000000000001',
          githubRepositoryId: '00000000-0000-4000-8000-000000000002',
          repositoryName: 'openai/hiresemble',
          repositoryUrl: 'https://github.com/openai/hiresemble',
          commitShaShort: 'abcdef123456',
          capturedAt: '2026-08-08T00:00:00Z',
          sourceExcerpt: '공개 README와 변경 기록에서 확인한 안전한 요약입니다.',
          sourceDeletedAt: '2026-08-08T01:00:00Z',
          createdAt: '2026-08-08T00:00:00Z',
        },
      ],
    })
    featureFlags.githubSourceEnabled = true

    const wrapper = await mountWithApp(
      ExperienceLibraryPage,
      '/profile/experiences?selected=experience-candidate',
    )

    expect(wrapper.text()).toContain('GitHub 출처')
    expect(wrapper.text()).toContain('GitHub 저장소')
    expect(wrapper.text()).toContain('2곳')
    expect(wrapper.text()).toContain('openai/hiresemble')
    expect(wrapper.text()).toContain('abcdef123456')
    expect(wrapper.text()).toContain('안전한 요약')
    expect(wrapper.text()).toContain('GitHub 연결은 삭제됐지만')
    expect(wrapper.get('a[href="https://github.com/openai/hiresemble"]').attributes('rel')).toBe(
      'noopener noreferrer',
    )
    expect(wrapper.get('a[href^="/profile/github?source="]').attributes('href')).toContain(
      '00000000-0000-4000-8000-000000000001',
    )
  })
})

async function clickButton(wrapper: { findAll: (selector: string) => unknown[] }, label: string) {
  const buttons = wrapper.findAll('button') as {
    text(): string
    trigger(e: string): Promise<void>
  }[]
  const target = buttons.find((button) => button.text().trim() === label)
  if (!target) throw new Error(`"${label}" 버튼을 찾지 못했어요.`)
  return target.trigger('click')
}

async function mountWithApp(
  component: Parameters<typeof mount>[0],
  initialPath: string,
  props: Record<string, unknown> = {},
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.status = 'authenticated'
  authStore.currentUser = {
    id: 'user-1',
    email: 'user-1@example.com',
    displayName: 'User One',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/profile/:pathMatch(.*)*', component: { template: '<div />' } },
      { path: '/documents/:documentId', component: { template: '<div />' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(component, {
    props,
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function experience(overrides: Partial<ExperienceItemDto> = {}): ExperienceItemDto {
  return {
    id: 'experience-candidate',
    evidenceCategory: 'PROJECT',
    title: '결제 처리 지연 개선',
    content: '결제 처리 흐름의 병목을 찾아 응답 시간을 35% 줄였습니다.',
    verificationStatus: 'PENDING',
    matchKind: 'RELATED_DIFFERENT',
    matchedExperienceItemId: 'experience-target',
    matchSimilarity: 0.87,
    reviewRequired: true,
    sourceCount: 2,
    documentSourceCount: 2,
    githubRepositorySourceCount: 0,
    primaryDocumentName: '지원용 이력서.pdf',
    version: 3,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-07T00:00:00Z',
    ...overrides,
  }
}

function detail(item: ExperienceItemDto): ExperienceItemDetailDto {
  return {
    item,
    sources: [
      {
        evidenceId: 'evidence-primary',
        sourceType: 'DOCUMENT_CHUNK',
        documentId: 'document-id',
        verificationStatus: 'PENDING',
        relationKind: 'PRIMARY_SOURCE',
        similarity: null,
        githubSourceId: null,
        githubRepositoryId: null,
        repositoryName: null,
        repositoryUrl: null,
        commitShaShort: null,
        capturedAt: null,
        sourceExcerpt: null,
        sourceDeletedAt: null,
        createdAt: '2026-08-01T00:00:00Z',
      },
      {
        evidenceId: 'evidence-corroborating',
        sourceType: 'DOCUMENT_CHUNK',
        documentId: 'document-id-2',
        verificationStatus: 'VERIFIED',
        relationKind: 'CORROBORATING',
        similarity: 0.96,
        githubSourceId: null,
        githubRepositoryId: null,
        repositoryName: null,
        repositoryUrl: null,
        commitShaShort: null,
        capturedAt: null,
        sourceExcerpt: null,
        sourceDeletedAt: null,
        createdAt: '2026-08-07T00:00:00Z',
      },
    ],
  }
}

function corroboratingEvidence(): EvidenceDto {
  return {
    id: 'evidence-corroborating',
    sourceType: 'DOCUMENT_CHUNK',
    sourceEntityId: null,
    documentId: 'document-id',
    experienceItemId: 'experience-candidate',
    experienceLinkKind: 'CORROBORATING',
    experienceMatchKind: 'SAME_EXPERIENCE',
    sourceDeletedAt: null,
    evidenceCategory: 'PROJECT',
    title: '결제 처리 지연 개선',
    content: '같은 프로젝트 경험을 포트폴리오에서도 확인했습니다.',
    metadata: {},
    confidence: 0.97,
    verificationStatus: 'VERIFIED',
    verifiedAt: '2026-08-07T00:00:00Z',
    version: 1,
    createdAt: '2026-08-07T00:00:00Z',
    updatedAt: '2026-08-07T00:00:00Z',
  }
}

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}
