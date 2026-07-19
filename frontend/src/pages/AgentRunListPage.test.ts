import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import { agentRunSummary } from '@/features/agent-runs/testFixtures'

import AgentRunListPage from './AgentRunListPage.vue'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/agent-runs/queries', () => ({
  useAgentRunListQuery: () => ({
    data: ref({
      items: [agentRunSummary()],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 2,
    }),
    error: ref(null),
    isLoading: ref(false),
    isError: ref(false),
  }),
}))

describe('AgentRunListPage URL state', () => {
  it('canonicalizes invalid filters and drives sort and pagination through the URL', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/agent-runs', component: AgentRunListPage },
        { path: '/agent-runs/:agentRunId', component: { template: '<div />' } },
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
    expect(wrapper.text()).toContain('USD 0.010000')

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
