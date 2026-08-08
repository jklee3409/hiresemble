import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { agentRunDetail } from '@/features/agent-runs/testFixtures'

import GitHubRunMonitor from './GitHubRunMonitor.vue'

const mocks = vi.hoisted(() => ({
  run: null as unknown,
  streamOptions: null as Record<string, unknown> | null,
  close: vi.fn(),
}))

vi.mock('@/features/agent-runs/queries', () => ({
  useAgentRunDetailQuery: () => ({
    data: {
      get value() {
        return mocks.run
      },
    },
    isPending: { value: false },
    isError: { value: false },
  }),
}))

vi.mock('@/features/agent-runs/stream', () => ({
  AgentRunStreamController: class {
    constructor(options: Record<string, unknown>) {
      mocks.streamOptions = options
    }
    start() {
      const callback = mocks.streamOptions?.onConnectionState as
        ((state: string) => void) | undefined
      callback?.('reconnecting')
    }
    close() {
      mocks.close()
    }
  },
}))

describe('GitHubRunMonitor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.streamOptions = null
    mocks.run = agentRunDetail({
      workflowType: 'GITHUB_INGESTION',
      resourceType: 'GITHUB_SOURCE',
      resourceId: sourceId,
      currentStep: 'EXTRACT_GITHUB_CANDIDATES',
      progressPercent: 65,
    })
  })

  it('shows safe GitHub progress and treats reconnect as transport recovery', () => {
    const wrapper = mountMonitor()
    expect(wrapper.text()).toContain('경험 후보 찾기')
    expect(wrapper.text()).toContain('65%')
    expect(wrapper.text()).toContain('진행 상태 연결 복구 중')
    expect(wrapper.text()).not.toContain('EXTRACT_GITHUB_CANDIDATES')
    expect(mocks.streamOptions).toMatchObject({
      userId: 'user-1',
      agentRunId: expect.any(String),
    })
  })

  it('refuses to monitor a Run whose resource does not match the focused source', () => {
    mocks.run = agentRunDetail({
      workflowType: 'GITHUB_INGESTION',
      resourceType: 'GITHUB_SOURCE',
      resourceId: '00000000-0000-4000-8000-000000000099',
    })
    const wrapper = mountMonitor()
    expect(wrapper.text()).toContain('일치하지 않는 작업 정보')
    expect(mocks.streamOptions).toBeNull()
  })
})

function mountMonitor() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(GitHubRunMonitor, {
    props: {
      userId: 'user-1',
      sourceId,
      agentRunId: '10000000-0000-4000-8000-000000000001',
    },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

const sourceId = '70000000-0000-4000-8000-000000000001'
