import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  COVER_LETTER_ID,
  coverLetterDetailFixture,
  coverLetterSummaryFixture,
  uuid,
} from '@/features/cover-letters/testFixtures'

import CoverLetterListPage from './CoverLetterListPage.vue'

const mocks = vi.hoisted(() => ({
  list: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  archive: {
    isPending: { value: false },
    mutateAsync: vi.fn(),
  },
  unarchive: {
    isPending: { value: false },
    mutateAsync: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/cover-letters/queries', () => ({
  useCoverLetterListQuery: () => mocks.list,
  useArchiveCoverLetterMutation: () => mocks.archive,
  useUnarchiveCoverLetterMutation: () => mocks.unarchive,
}))

describe('CoverLetterListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.list.data.value = {
      items: [
        coverLetterSummaryFixture(),
        coverLetterSummaryFixture({
          id: uuid(200),
          title: '과거 자기소개서',
          status: 'ARCHIVED',
          canEdit: false,
          canArchive: false,
          canUnarchive: true,
          canFinalize: false,
        }),
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 2,
    }
    mocks.list.error.value = null
    mocks.list.isLoading.value = false
    mocks.list.isError.value = false
    mocks.archive.mutateAsync.mockResolvedValue(coverLetterDetailFixture({ status: 'ARCHIVED' }))
    mocks.unarchive.mutateAsync.mockResolvedValue(coverLetterDetailFixture({ status: 'DRAFT' }))
  })

  it('uses canonical URL filters and exposes active and archived lifecycle actions', async () => {
    const { wrapper, router } = await mountPage(
      '/cover-letters?status=INVALID&page=-1&size=999&sort=unknown&query=%20Hiresemble%20&extra=x',
    )

    expect(router.currentRoute.value.query).toEqual({ query: 'Hiresemble' })
    expect(wrapper.text()).toContain('답변 완료')
    expect(wrapper.text()).toContain('읽기 전용 · 과거 버전과 검증 기록')
    expect(wrapper.get(`a[href="/cover-letters/${COVER_LETTER_ID}/edit"]`).text()).toBe('편집하기')
    expect(wrapper.get(`a[href="/cover-letters/${uuid(200)}/edit"]`).text()).toBe(
      '읽기 전용으로 열기',
    )

    const search = wrapper.get<HTMLInputElement>('input[type="search"]')
    await search.setValue('새 검색')
    const status = wrapper.findAll('select')[0]
    await status?.setValue('ARCHIVED')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({
      query: '새 검색',
      status: 'ARCHIVED',
    })

    const archive = wrapper
      .get(`[data-testid="cover-letter-row-${COVER_LETTER_ID}"]`)
      .findAll('button')
      .find((button) => button.text() === '보관')
    await archive?.trigger('click')
    await flushPromises()
    expect(mocks.archive.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      version: 3,
    })
    expect(wrapper.text()).toContain('과거 내용은 읽기 전용으로 유지됩니다.')

    const unarchive = wrapper
      .get(`[data-testid="cover-letter-row-${uuid(200)}"]`)
      .findAll('button')
      .find((button) => button.text() === '다시 쓰기')
    await unarchive?.trigger('click')
    await flushPromises()
    expect(mocks.unarchive.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: uuid(200),
      version: 3,
    })
  })
})

async function mountPage(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cover-letters', name: 'cover-letters', component: CoverLetterListPage },
      {
        path: '/cover-letters/:coverLetterId/edit',
        name: 'cover-letter-edit',
        component: { template: '<div />' },
      },
      { path: '/jobs', name: 'jobs', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CoverLetterListPage, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
