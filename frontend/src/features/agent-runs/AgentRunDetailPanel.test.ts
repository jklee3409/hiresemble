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

    expect(wrapper.text()).toContain('공고 분석')
    expect(wrapper.text()).toContain('이번 작업 사용량')
    expect(wrapper.text()).not.toContain('요청 품질')
    expect(wrapper.text()).not.toContain('처리 방식')
    expect(wrapper.text()).not.toContain('예약')
    expect(wrapper.text()).toContain('결제 금액이나 월간 전체 한도를 뜻하지 않아요')
    expect(wrapper.text()).not.toContain('billable estimate')
    expect(wrapper.text()).toContain('완료 1개 · 확인 필요 1개')
    expect(wrapper.text()).not.toContain('scope-a')
    expect(wrapper.text()).not.toContain('scope-b')
    expect(wrapper.text()).not.toContain('LOAD_FIXTURE')
    expect(wrapper.text()).not.toContain('provider-model-private')
    expect(wrapper.text()).not.toContain('prompt')
    expect(wrapper.text()).not.toContain('claimToken')
    expect(wrapper.text()).not.toContain('inputHash')
  })

  it('does not expose the persisted technical error message', () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({
          status: 'FAILED',
          retryable: true,
          safeError: {
            code: 'AI_PROVIDER_DISABLED',
            message: 'AI 실행 공급자가 활성화되지 않았습니다.',
          },
        }),
        connectionState: 'closed',
      },
      global,
    })

    expect(wrapper.text()).toContain('잠시 후 다시 시도해 주세요')
    expect(wrapper.text()).toContain('등록한 원본과 기존 결과는 그대로 유지됩니다')
    expect(wrapper.text()).not.toContain('공급자')
    expect(wrapper.text()).not.toContain('AI_PROVIDER_DISABLED')
  })

  it('links a Job analysis run back to the owning Job analysis page without copying results', () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({
          resourceType: 'JOB',
          resourceId: '50000000-0000-4000-8000-000000000001',
        }),
        connectionState: 'connected',
      },
      global,
    })

    expect(wrapper.text()).toContain('공고 분석 보기')
    expect(wrapper.text()).not.toContain('적합도 점수')
    expect(wrapper.text()).not.toContain('강점')
    expect(wrapper.text()).not.toContain('부족한 점')
  })

  it('links a cover-letter run back to the canonical editor without copying answer content', () => {
    const wrapper = mount(AgentRunDetailPanel, {
      props: {
        run: agentRunDetail({
          workflowType: 'COVER_LETTER_VERIFICATION',
          resourceType: 'COVER_LETTER',
          resourceId: '60000000-0000-4000-8000-000000000001',
        }),
        connectionState: 'connected',
      },
      global,
    })

    expect(wrapper.text()).toContain('자기소개서 보기')
    expect(wrapper.text()).not.toContain('답변 본문')
    expect(wrapper.text()).not.toContain('검증 제안')
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
