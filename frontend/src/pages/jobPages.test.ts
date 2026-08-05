import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import JobListPage from '@/pages/JobListPage.vue'
import JobNewPage from '@/pages/JobNewPage.vue'
import JobOverviewPage from '@/pages/JobOverviewPage.vue'
import * as agentRunApi from '@/shared/api/agentRunApi'
import { ApiClientError } from '@/shared/api/errors'
import * as jobApi from '@/shared/api/jobApi'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'
import { agentRunSummary } from '@/features/agent-runs/testFixtures'
import {
  JOB_ID,
  JOB_RUN_ID,
  jobDetailFixture,
  jobSummaryFixture,
} from '@/features/jobs/testFixtures'

vi.mock('@/shared/api/jobApi', () => ({
  JOB_SORTS: ['createdAt,desc', 'deadlineAt,asc', 'updatedAt,desc'],
  createJob: vi.fn(),
  listJobs: vi.fn(),
  getJob: vi.fn(),
  updateJob: vi.fn(),
  updateJobStatus: vi.fn(),
  retryJobExtraction: vi.fn(),
  deleteJob: vi.fn(),
  createJobIdempotencyKey: vi.fn(() => 'job-create:key-1234'),
}))

vi.mock('@/shared/api/agentRunApi', () => ({
  listAgentRuns: vi.fn(),
  getAgentRun: vi.fn(),
  retryAgentRun: vi.fn(),
  cancelAgentRun: vi.fn(),
  createRetryIdempotencyKey: vi.fn(() => 'agent-run-retry:key-1234'),
}))

describe('P5 Job pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(page([]))
    vi.mocked(jobApi.listJobs).mockResolvedValue(page([]))
  })

  it('navigates the exact 201 manual branch without an Agent Run query indicator', async () => {
    vi.mocked(jobApi.createJob).mockResolvedValue({
      httpStatus: 201,
      job: {
        jobId: JOB_ID,
        status: 'IN_PROGRESS',
        extractionStatus: 'MANUAL_INPUT_PROVIDED',
        agentRunId: null,
      },
    })
    const { wrapper, router } = await mountNew()
    await wrapper.get('#job-source-url').setValue('https://jobs.example.com/openings/1')
    await wrapper.get('#job-description').setValue('사용자가 입력한 공고 본문')
    await wrapper.get('#job-create-form').trigger('submit')
    await flushPromises()

    expect(jobApi.createJob).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceUrl: 'https://jobs.example.com/openings/1',
        descriptionText: '사용자가 입력한 공고 본문',
      }),
      'job-create:key-1234',
    )
    expect(router.currentRoute.value.fullPath).toBe(`/jobs/${JOB_ID}/overview?created=manual`)
  })

  it('builds the deadline from a date, AM/PM, and a 30-minute time choice', async () => {
    vi.mocked(jobApi.createJob).mockResolvedValue({
      httpStatus: 202,
      job: {
        jobId: JOB_ID,
        status: 'IN_PROGRESS',
        extractionStatus: 'QUEUED',
        agentRunId: JOB_RUN_ID,
      },
    })
    const { wrapper } = await mountNew()

    expect(wrapper.find('#job-deadline').exists()).toBe(false)
    expect(wrapper.get('#job-deadline-time').findAll('option')).toHaveLength(24)
    await wrapper.get('#job-source-url').setValue('https://jobs.example.com/openings/deadline')
    await wrapper.get('#job-deadline-date').setValue('2026-08-31')
    await wrapper.get('#job-deadline-period').setValue('PM')
    await wrapper.get('#job-deadline-time').setValue('11:30')
    await wrapper.get('#job-create-form').trigger('submit')
    await flushPromises()

    expect(jobApi.createJob).toHaveBeenCalledWith(
      expect.objectContaining({
        deadlineAt: new Date('2026-08-31T23:30').toISOString(),
      }),
      'job-create:key-1234',
    )
  })

  it('retains one create idempotency key across failure/retry and suppresses double submit', async () => {
    const pending = deferred<Awaited<ReturnType<typeof jobApi.createJob>>>()
    vi.mocked(jobApi.createJob)
      .mockRejectedValueOnce(
        new ApiClientError({ status: 0, code: 'NETWORK_ERROR', message: 'offline' }),
      )
      .mockImplementationOnce(() => pending.promise)
    const { wrapper, router } = await mountNew()
    await wrapper.get('#job-source-url').setValue('https://jobs.example.com/openings/2')
    await wrapper.get('#job-create-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('offline')

    await wrapper.get('#job-create-form').trigger('submit')
    await wrapper.get('#job-create-form').trigger('submit')
    await flushPromises()
    expect(jobApi.createJob).toHaveBeenCalledTimes(2)
    expect(vi.mocked(jobApi.createJob).mock.calls.map((call) => call[1])).toEqual([
      'job-create:key-1234',
      'job-create:key-1234',
    ])
    expect(jobApi.createJobIdempotencyKey).toHaveBeenCalledTimes(1)

    pending.resolve({
      httpStatus: 202,
      job: {
        jobId: JOB_ID,
        status: 'IN_PROGRESS',
        extractionStatus: 'QUEUED',
        agentRunId: JOB_RUN_ID,
      },
    })
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe(
      `/jobs/${JOB_ID}/overview?created=async&run=${JOB_RUN_ID}`,
    )
  })

  it('shows only owner periods, one direct start date, business badges and status menu', async () => {
    vi.mocked(jobApi.listJobs).mockResolvedValue(
      page(
        [
          jobSummaryFixture({
            status: 'CLOSED',
            extractionStatus: 'MANUAL_INPUT_PROVIDED',
            submittedAt: '2026-07-25T00:00:00Z',
          }),
        ],
        [{ year: 2026, half: 'SECOND_HALF' }],
      ),
    )
    vi.mocked(jobApi.updateJobStatus).mockResolvedValue(
      jobDetailFixture({ status: 'IN_PROGRESS', version: 3 }),
    )
    const { wrapper, router } = await mountList('/jobs?status=CLOSED&page=0')

    expect(wrapper.get('[data-testid="job-business-status"]').text()).toContain('마감')
    expect(wrapper.get('[data-testid="job-extraction-status"]').text()).toContain('직접 입력 완료')
    expect(wrapper.text()).toContain('서류 제출 이력 있음')
    expect(wrapper.text()).toContain('2026 하반기')
    expect(wrapper.text()).not.toContain('2026 상반기')
    expect(wrapper.text()).not.toContain('공고 불러오기 상태')
    expect(wrapper.text()).not.toContain('마감 시작')
    expect(wrapper.text()).not.toContain('마감 종료')
    expect(wrapper.findAll('.field__label').map((label) => label.text())).not.toContain('마감 임박')
    expect(wrapper.findAll('input[type="date"]')).toHaveLength(1)
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3)

    await wrapper.get('#job-posting-start-from').setValue('2026-07-01')
    await wrapper.get('.job-filters').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({
      status: 'CLOSED',
      postingStartFrom: '2026-07-01',
    })
    expect(router.currentRoute.value.query).not.toHaveProperty('postingYear')

    await wrapper
      .get(`select[aria-label="${jobSummaryFixture().title} 지원 상태 변경"]`)
      .setValue('IN_PROGRESS')
    await flushPromises()
    expect(jobApi.updateJobStatus).toHaveBeenCalledWith(JOB_ID, {
      status: 'IN_PROGRESS',
      version: 2,
    })
  })

  it('emphasizes only manual input for NEEDS_MANUAL_INPUT and keeps submittedAt on reopen', async () => {
    const submittedAt = '2026-07-25T00:00:00Z'
    vi.mocked(jobApi.getJob).mockResolvedValue(
      jobDetailFixture({
        status: 'CLOSED',
        extractionStatus: 'NEEDS_MANUAL_INPUT',
        submittedAt,
        extractionError: { code: 'JOB_TEXT_REQUIRED', message: '본문 입력이 필요합니다.' },
      }),
    )
    vi.mocked(jobApi.updateJobStatus).mockResolvedValue(
      jobDetailFixture({
        status: 'IN_PROGRESS',
        extractionStatus: 'NEEDS_MANUAL_INPUT',
        submittedAt,
        version: 3,
      }),
    )
    const { wrapper } = await mountOverview()

    expect(wrapper.text()).toContain('서류 제출 이력 있음')
    expect(wrapper.text()).toContain('공고 내용을 자동으로 충분히 읽지 못했어요')
    expect(wrapper.text()).not.toContain('OCR 사용')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    const manualButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '본문 직접 입력')
    await manualButton?.trigger('click')
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('공고 다시 불러오기')
    expect(jobApi.retryJobExtraction).not.toHaveBeenCalled()

    await wrapper.get('select').setValue('IN_PROGRESS')
    const statusForm = wrapper.findAll('form').find((form) => form.find('select').exists())
    await statusForm?.trigger('submit')
    await flushPromises()
    expect(jobApi.updateJobStatus).toHaveBeenCalledWith(JOB_ID, {
      status: 'IN_PROGRESS',
      version: 2,
    })
    expect(wrapper.text()).toContain('최초 서류 제출')
  })

  it('offers the safe error, retry, and manual input together only for FAILED', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValue(
      jobDetailFixture({
        extractionStatus: 'FAILED',
        extractionError: { code: 'JOB_EXTRACTION_FAILED', message: '본문 추출에 실패했습니다.' },
      }),
    )
    vi.mocked(jobApi.retryJobExtraction).mockResolvedValue({
      agentRunId: JOB_RUN_ID,
      status: 'QUEUED',
      resourceType: 'JOB',
      resourceId: JOB_ID,
      replayed: false,
    })
    const { wrapper } = await mountOverview()

    expect(wrapper.get('[role="alert"]').text()).toContain('본문 추출에 실패했습니다')
    expect(wrapper.get('[role="alert"]').text()).not.toContain('JOB_EXTRACTION_FAILED')
    expect(wrapper.text()).toContain('본문 직접 입력')
    expect(wrapper.text()).not.toContain('OCR 사용')
    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '공고 다시 불러오기')
    expect(retryButton).toBeDefined()

    await retryButton?.trigger('click')
    await flushPromises()
    expect(jobApi.retryJobExtraction).toHaveBeenCalledWith(
      JOB_ID,
      { version: 2 },
      expect.stringMatching(/^[A-Za-z0-9._:-]{8,128}$/),
    )
  })

  it('restores an active extraction run and blocks another extraction retry', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValue(
      jobDetailFixture({
        extractionStatus: 'FAILED',
        extractionError: { code: 'JOB_EXTRACTION_FAILED', message: '본문 추출에 실패했습니다.' },
      }),
    )
    vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(
      page([
        agentRunSummary('RUNNING', {
          id: JOB_RUN_ID,
          workflowType: 'JOB_POSTING_EXTRACTION',
          resourceType: 'JOB',
          resourceId: JOB_ID,
        }),
      ]),
    )
    const { wrapper } = await mountOverview()
    const retryButton = wrapper.findAll('button').find((button) => button.text() === '불러오는 중…')

    expect(retryButton?.attributes('disabled')).toBeDefined()
    await retryButton?.trigger('click')
    expect(jobApi.retryJobExtraction).not.toHaveBeenCalled()
  })

  it('compares a 409 draft and explicitly reapplies it with the latest version', async () => {
    const original = jobDetailFixture({ title: 'Original', version: 2 })
    const latest = jobDetailFixture({
      title: 'Server latest',
      companyName: 'Latest Co',
      version: 7,
    })
    vi.mocked(jobApi.getJob).mockResolvedValueOnce(original).mockResolvedValueOnce(latest)
    vi.mocked(jobApi.updateJob)
      .mockRejectedValueOnce(versionConflict())
      .mockResolvedValueOnce(
        jobDetailFixture({ title: 'My draft', companyName: 'Latest Co', version: 8 }),
      )
    const { wrapper } = await mountOverview()

    const editButton = wrapper.findAll('button').find((button) => button.text() === '편집')
    await editButton?.trigger('click')
    await wrapper.get('#job-edit-title').setValue('My draft')
    const editForm = wrapper.findAll('form').find((form) => form.find('textarea').exists())
    await editForm?.trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('다른 곳에서 공고가 변경됐어요')

    await wrapper.get('input[aria-label="공고 제목 내 값 재적용"]').setValue(true)
    const reapply = wrapper.findAll('button').find((button) => button.text() === '선택 항목 재적용')
    await reapply?.trigger('click')
    expect(wrapper.text()).toContain('최근 저장된 내용에 다시 적용')

    const reappliedForm = wrapper.findAll('form').find((form) => form.find('textarea').exists())
    await reappliedForm?.trigger('submit')
    await flushPromises()
    expect(jobApi.updateJob).toHaveBeenLastCalledWith(
      JOB_ID,
      expect.objectContaining({ title: 'My draft', version: 7 }),
    )
  })

  it('closes deletion as success when a subsequent delete returns 404', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValue(jobDetailFixture())
    vi.mocked(jobApi.deleteJob).mockRejectedValue(
      new ApiClientError({ status: 404, code: 'RESOURCE_NOT_FOUND', message: 'not found' }),
    )
    const { wrapper, router, cache } = await mountOverview()
    cache.setQueryData(['user', 'user-1', 'job', JOB_ID], jobDetailFixture())

    const deleteButton = wrapper.findAll('button').find((button) => button.text() === '삭제')
    const deletion = deleteButton?.trigger('click')
    useNotifications().resolveConfirmation(true)
    await deletion
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('jobs')
    expect(cache.getQueryData(['user', 'user-1', 'job', JOB_ID])).toBeUndefined()
  })
})

async function mountNew() {
  const pinia = authenticatedPinia()
  const router = testRouter()
  await router.push('/jobs/new')
  await router.isReady()
  const cache = testCache()
  const wrapper = mount(JobNewPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient: cache }]] },
  })
  await flushPromises()
  return { wrapper, router, cache }
}

async function mountList(path = '/jobs') {
  const pinia = authenticatedPinia()
  const router = testRouter()
  await router.push(path)
  await router.isReady()
  const cache = testCache()
  const wrapper = mount(JobListPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient: cache }]] },
  })
  await flushPromises()
  return { wrapper, router, cache }
}

async function mountOverview() {
  const pinia = authenticatedPinia()
  const router = testRouter()
  await router.push(`/jobs/${JOB_ID}/overview`)
  await router.isReady()
  const cache = testCache()
  const wrapper = mount(JobOverviewPage, {
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient: cache }]],
      stubs: { JobRunMonitor: true },
    },
  })
  await flushPromises()
  return { wrapper, router, cache }
}

function authenticatedPinia() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore(pinia)
  auth.status = 'authenticated'
  auth.currentUser = { id: 'user-1', email: 'user-1@example.com', displayName: 'User One' }
  return pinia
}

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/jobs', name: 'jobs', component: JobListPage },
      { path: '/jobs/new', name: 'job-new', component: JobNewPage },
      { path: '/jobs/:jobId/overview', name: 'job-overview', component: JobOverviewPage },
      {
        path: '/agent-runs/:agentRunId',
        name: 'agent-run-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

function testCache() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function page<T>(
  items: T[],
  availablePeriods: { year: number; half: 'FIRST_HALF' | 'SECOND_HALF' }[] = [],
) {
  return {
    items,
    availablePeriods,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  }
}

function versionConflict() {
  return new ApiClientError({
    status: 409,
    code: 'RESOURCE_VERSION_CONFLICT',
    message: 'conflict',
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
