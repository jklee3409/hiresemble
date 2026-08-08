import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { agentRunDetail } from '@/features/agent-runs/testFixtures'

import CareerArtifactRunMonitor from './CareerArtifactRunMonitor.vue'

const mocks = vi.hoisted(() => ({
  run: null as unknown,
  streamOptions: null as Record<string, unknown> | null,
  close: vi.fn(),
  cancel: vi.fn(),
  retry: vi.fn(),
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
  useCancelAgentRunMutation: () => ({ isPending: { value: false }, mutateAsync: mocks.cancel }),
  useRetryAgentRunMutation: () => ({ isPending: { value: false }, mutateAsync: mocks.retry }),
}))

vi.mock('@/features/agent-runs/stream', () => ({
  AgentRunStreamController: class {
    constructor(options: Record<string, unknown>) {
      mocks.streamOptions = options
    }
    start() {
      const callback = mocks.streamOptions?.onConnectionState as
        ((state: string) => void) | undefined
      callback?.('polling')
    }
    close() {
      mocks.close()
    }
  },
}))

describe('CareerArtifactRunMonitor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.streamOptions = null
    mocks.run = agentRunDetail({
      workflowType: 'RESUME_GENERATION',
      resourceType: 'CAREER_ARTIFACT',
      resourceId: artifactId,
      currentStep: 'RENDER_DOCX',
      progressPercent: 70,
    })
  })

  it('tracks a matching Run and treats polling as connection recovery', () => {
    const wrapper = mountMonitor()
    expect(wrapper.text()).toContain('Word 파일 생성')
    expect(wrapper.text()).toContain('70%')
    expect(wrapper.text()).toContain('연결을 복구')
    expect(wrapper.text()).not.toContain('RENDER_DOCX')
    expect(mocks.streamOptions).toMatchObject({ userId: 'user-1', agentRunId: expect.any(String) })
  })

  it('fails closed when resource or workflow parity does not match', () => {
    mocks.run = agentRunDetail({
      workflowType: 'PORTFOLIO_GENERATION',
      resourceType: 'CAREER_ARTIFACT',
      resourceId: artifactId,
    })
    const wrapper = mountMonitor()
    expect(wrapper.text()).toContain('일치하지 않는 작업 정보')
    expect(mocks.streamOptions).toBeNull()
  })
})

function mountMonitor() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(CareerArtifactRunMonitor, {
    props: {
      userId: 'user-1',
      artifactId,
      artifactType: 'RESUME',
      agentRunId: '10000000-0000-4000-8000-000000000001',
    },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' }, StatusBadge: true },
    },
  })
}

const artifactId = '70000000-0000-4000-8000-000000000001'
