import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AgentRunDetailPanel from './AgentRunDetailPanel.vue'
import { agentRunDetail } from './testFixtures'

const global = {
  stubs: {
    RouterLink: { template: '<a><slot /></a>' },
  },
}

describe('AgentRunDetailPanel', () => {
  it('projects safe detail fields and the catalog cost notice without internal provider data', () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({
          partialResult: {
            succeededScopeKeys: ['scope-a'],
            failedScopeKeys: ['scope-b'],
            resultRefs: [],
          },
        }),
        connectionState: 'connected',
      },
      global,
    })

    expect(wrapper.text()).toContain('공고 살펴보기')
    expect(wrapper.text()).toContain('균형형')
    expect(wrapper.text()).toContain('빠른 처리')
    expect(wrapper.text()).toContain('예상 사용 비용')
    expect(wrapper.text()).toContain('실제 결제 금액과 다를 수 있어요')
    expect(wrapper.text()).not.toContain('billable estimate')
    expect(wrapper.text()).toContain('완료된 항목 1개')
    expect(wrapper.text()).toContain('완료하지 못한 항목 1개')
    expect(wrapper.text()).not.toContain('scope-a')
    expect(wrapper.text()).not.toContain('scope-b')
    expect(wrapper.text()).not.toContain('LOAD_FIXTURE')
    expect(wrapper.text()).not.toContain('provider-model-private')
    expect(wrapper.text()).not.toContain('prompt')
    expect(wrapper.text()).not.toContain('claimToken')
    expect(wrapper.text()).not.toContain('inputHash')
  })

  it('shows a safe WAITING_USER action, disables generic retry, and trusts server cancellable', async () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({
          status: 'WAITING_USER',
          retryable: true,
          cancellable: true,
          requiredUserAction: {
            type: 'PROVIDE_DOCUMENT_TEXT',
            resource: null,
            route: '/profile/basic',
            message: '프로필 정보를 확인해 주세요.',
          },
        }),
        connectionState: 'connected',
      },
      global,
    })

    expect(wrapper.text()).toContain('프로필 정보를 확인해 주세요.')
    expect(wrapper.text()).toContain('필요한 정보 입력하기')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('재시도')
    const cancel = wrapper.findAll('button').find((button) => button.text() === '실행 취소')
    expect(cancel).toBeDefined()
    await cancel?.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('shows retry only for server-retryable FAILED/INTERRUPTED and cancel only for active runs', async () => {
    const failed = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({ status: 'FAILED', retryable: true, cancellable: false }),
        connectionState: 'closed',
      },
      global,
    })
    const retry = failed.findAll('button').find((button) => button.text() === '재시도')
    expect(retry).toBeDefined()
    await retry?.trigger('click')
    expect(failed.emitted('retry')).toHaveLength(1)

    const interrupted = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({ status: 'INTERRUPTED', retryable: false, cancellable: false }),
        connectionState: 'closed',
      },
      global,
    })
    expect(interrupted.findAll('button').map((button) => button.text())).not.toContain('재시도')
    expect(interrupted.findAll('button').map((button) => button.text())).not.toContain('실행 취소')
  })

  it('keeps the last run status when the SSE connection is recovering', () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: { run: agentRunDetail({ status: 'RUNNING' }), connectionState: 'polling' },
      global,
    })
    expect(wrapper.get('h2').text()).toBe('진행 중')
    expect(wrapper.text()).toContain('진행 상황을 다시 확인하는 중이에요')
    expect(wrapper.get('h2').text()).not.toBe('실패')
  })
})
