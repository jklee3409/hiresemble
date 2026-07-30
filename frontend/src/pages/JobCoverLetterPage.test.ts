import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  COVER_LETTER_ID,
  COVER_LETTER_JOB_ID,
  coverLetterDetailFixture,
  coverLetterSummaryFixture,
  uuid,
} from '@/features/cover-letters/testFixtures'

import JobCoverLetterPage from './JobCoverLetterPage.vue'

const TestApp = { template: '<RouterView />' }

const mocks = vi.hoisted(() => ({
  job: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  list: {
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  },
  create: {
    isPending: { value: false },
    mutateAsync: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/jobs/queries', () => ({
  useJobDetailQuery: () => mocks.job,
}))

vi.mock('@/features/cover-letters/queries', () => ({
  useCoverLetterListQuery: () => mocks.list,
  useCreateCoverLetterMutation: () => mocks.create,
}))

describe('JobCoverLetterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.job.data.value = {
      id: COVER_LETTER_JOB_ID,
      companyName: 'Hiresemble',
      title: 'Frontend Engineer',
      positionName: '프론트엔드 개발자',
    }
    mocks.job.error.value = null
    mocks.job.isLoading.value = false
    mocks.job.isError.value = false
    mocks.list.data.value = {
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    }
    mocks.list.error.value = null
    mocks.list.isLoading.value = false
    mocks.list.isError.value = false
    mocks.create.mutateAsync.mockResolvedValue(coverLetterDetailFixture())
  })

  it('creates a cover letter from the Job context and opens the canonical editor', async () => {
    const { wrapper, router } = await mountPage()

    expect(wrapper.text()).toContain('아직 자기소개서가 없어요')
    expect(wrapper.get(`a[href="/jobs/${COVER_LETTER_JOB_ID}/analysis"]`).text()).toBe(
      '공고 분석 결과 확인',
    )
    await wrapper.get('[data-testid="create-cover-letter"]').trigger('click')
    await flushPromises()

    expect(mocks.create.mutateAsync).toHaveBeenCalledWith({
      jobId: COVER_LETTER_JOB_ID,
      title: 'Hiresemble 프론트엔드 개발자 자기소개서',
    })
    expect(router.currentRoute.value.fullPath).toBe(`/cover-letters/${COVER_LETTER_ID}/edit`)
  })

  it('shows one active cover letter and keeps archived history read-only', async () => {
    mocks.list.data.value = {
      items: [
        coverLetterSummaryFixture(),
        coverLetterSummaryFixture({
          id: uuid(210),
          title: '지난 지원서',
          status: 'ARCHIVED',
          canEdit: false,
          canArchive: false,
          canUnarchive: false,
          canFinalize: false,
        }),
      ],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    }

    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('현재 자기소개서')
    expect(wrapper.text()).toContain('1/1')
    expect(wrapper.text()).toContain('과거 자기소개서')
    expect(wrapper.text()).toContain('지난 지원서')
    expect(wrapper.text()).not.toContain('자기소개서 생성')
    expect(wrapper.get(`a[href="/cover-letters/${uuid(210)}/edit"]`).text()).toBe('기록 열기')
  })
})

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/jobs/:jobId/cover-letter',
        name: 'job-cover-letter',
        component: JobCoverLetterPage,
      },
      {
        path: '/jobs/:jobId/analysis',
        name: 'job-analysis',
        component: { template: '<div />' },
      },
      {
        path: '/cover-letters/:coverLetterId/edit',
        name: 'cover-letter-edit',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/jobs/${COVER_LETTER_JOB_ID}/cover-letter`)
  await router.isReady()
  const wrapper = mount(TestApp, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
