import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'

import { featureFlags } from '@/app/featureFlags'
import { agentRunSummary } from '@/features/agent-runs/testFixtures'
import { useNotifications } from '@/shared/ui/notifications'

import AgentRunListPage from './AgentRunListPage.vue'

const { deleteRun, deleteSelectedRuns } = vi.hoisted(() => ({
  deleteRun: vi.fn().mockResolvedValue(undefined),
  deleteSelectedRuns: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/agent-runs/queries', () => ({
  useAgentRunListQuery: () => ({
    data: ref({
      items: [
        agentRunSummary('RUNNING', {
          resourceType: 'JOB',
          resourceId: '50000000-0000-4000-8000-000000000001',
        }),
        agentRunSummary('SUCCEEDED', {
          id: '10000000-0000-4000-8000-000000000002',
          workflowType: 'COVER_LETTER_GENERATION',
          resourceType: 'COVER_LETTER',
          resourceId: '60000000-0000-4000-8000-000000000001',
        }),
        agentRunSummary('RUNNING', {
          id: '10000000-0000-4000-8000-000000000003',
          workflowType: 'GITHUB_INGESTION',
          resourceType: 'GITHUB_SOURCE',
          resourceId: '70000000-0000-4000-8000-000000000001',
        }),
        agentRunSummary('RUNNING', {
          id: '10000000-0000-4000-8000-000000000004',
          workflowType: 'RESUME_GENERATION',
          resourceType: 'CAREER_ARTIFACT',
          resourceId: '80000000-0000-4000-8000-000000000001',
        }),
      ],
      page: 0,
      size: 20,
      totalElements: 4,
      totalPages: 2,
    }),
    error: ref(null),
    isLoading: ref(false),
    isError: ref(false),
  }),
  useDeleteAgentRunMutation: () => ({
    mutateAsync: deleteRun,
    isPending: ref(false),
  }),
  useDeleteSelectedAgentRunsMutation: () => ({
    mutateAsync: deleteSelectedRuns,
    isPending: ref(false),
  }),
}))

describe('AgentRunListPage URL state', () => {
  beforeEach(() => {
    deleteRun.mockClear()
    deleteSelectedRuns.mockClear()
    featureFlags.githubSourceEnabled = true
    featureFlags.careerArtifactEnabled = true
  })
  afterAll(() => {
    featureFlags.githubSourceEnabled = false
    featureFlags.careerArtifactEnabled = false
  })

  it('canonicalizes invalid filters and drives sort and pagination through the URL', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/agent-runs', component: AgentRunListPage },
        { path: '/agent-runs/:agentRunId', component: { template: '<div />' } },
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
        { path: '/profile/github', component: { template: '<div />' } },
        { path: '/career-artifacts/:careerArtifactId', component: { template: '<div />' } },
      ],
    })
    await router.push(
      '/agent-runs?workflowType=INVALID&status=FAILED&page=-1&size=999&sort=cost,asc&unknown=x',
    )
    await router.isReady()
    const wrapper = mount(AgentRunListPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ status: ['FAILED'] })
    expect(wrapper.text()).toContain('공고 분석')
    expect(
      wrapper.get('a[href="/jobs/50000000-0000-4000-8000-000000000001/analysis"]').text(),
    ).toBe('공고 분석')
    expect(
      wrapper.get('a[href="/cover-letters/60000000-0000-4000-8000-000000000001/edit"]').text(),
    ).toBe('자기소개서')
    expect(
      wrapper.get('a[href="/profile/github?source=70000000-0000-4000-8000-000000000001"]').text(),
    ).toBe('GitHub 연결')
    const careerArtifactRow = wrapper
      .findAll('.run-row')
      .find((row) => row.text().includes('AI 이력서 초안 만들기'))
    expect(careerArtifactRow).toBeDefined()
    expect(careerArtifactRow?.findAll('a')).toHaveLength(2)
    expect(
      careerArtifactRow
        ?.get('a[href="/career-artifacts/80000000-0000-4000-8000-000000000001"]')
        .text(),
    ).toBe('생성 자료')
    expect(wrapper.text()).toContain('작업 한도의 33%')
    const rowCheckboxes = wrapper.findAll('.run-row__selection input')
    expect(rowCheckboxes[0]?.attributes('disabled')).toBeDefined()
    expect(rowCheckboxes[1]?.attributes('disabled')).toBeUndefined()

    await rowCheckboxes[1]?.setValue(true)
    const bulkDeletion = wrapper
      .findAll('button')
      .find((button) => button.text() === '삭제(1)')
      ?.trigger('click')
    useNotifications().resolveConfirmation(true)
    await bulkDeletion
    await flushPromises()
    expect(deleteSelectedRuns).toHaveBeenCalledWith(['10000000-0000-4000-8000-000000000002'])

    const individualDelete = wrapper
      .findAll('.run-row__actions button')
      .find((button) => button.text() === '삭제' && button.attributes('disabled') === undefined)
    const individualDeletion = individualDelete?.trigger('click')
    useNotifications().resolveConfirmation(true)
    await individualDeletion
    await flushPromises()
    expect(deleteRun).toHaveBeenCalledWith('10000000-0000-4000-8000-000000000002')

    const sort = wrapper.findAll('select')[1]
    expect(sort).toBeDefined()
    await sort?.setValue('updatedAt,desc')
    await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({
      status: ['FAILED'],
      sort: 'updatedAt,desc',
    })

    const next = wrapper.findAll('button').find((button) => button.text() === '다음')
    expect(next).toBeDefined()
    await next?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({
      status: ['FAILED'],
      sort: 'updatedAt,desc',
      page: '1',
    })
  })
})
