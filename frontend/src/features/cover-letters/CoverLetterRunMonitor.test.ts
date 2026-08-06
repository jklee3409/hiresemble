import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CoverLetterRunMonitor from './CoverLetterRunMonitor.vue'

const runMocks = vi.hoisted(() => ({
  detail: {
    data: { value: undefined as unknown },
    isLoading: { value: false },
    isError: { value: false },
  },
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({}),
}))

vi.mock('@/features/agent-runs/presentation', () => ({
  STATUS_LABELS: { RUNNING: '진행 중', SUCCEEDED: '완료' },
}))

vi.mock('@/features/agent-runs/queries', () => ({
  useAgentRunDetailQuery: () => runMocks.detail,
}))

vi.mock('@/features/agent-runs/stream', () => ({
  AgentRunStreamController: class {
    start(): void {}
    close(): void {}
  },
}))

describe('CoverLetterRunMonitor', () => {
  beforeEach(() => {
    runMocks.detail.data.value = {
      id: 'run-id',
      workflowType: 'COVER_LETTER_GENERATION',
      resourceType: 'COVER_LETTER',
      resourceId: 'cover-letter-id',
      status: 'RUNNING',
      stateVersion: 2,
      progressPercent: 60,
      partialResult: {
        succeededScopeKeys: ['question-id'],
        failedScopeKeys: [],
      },
    }
    runMocks.detail.isLoading.value = false
    runMocks.detail.isError.value = false
  })

  it('shows a compact completed-question preview while preserving the full accessible label', () => {
    const fullQuestion =
      '1. AI 개발 직무와 관련하여 본인이 가장 자신 있는 기술 또는 역량을 소개하고 이를 활용한 프로젝트 경험과 성과를 구체적으로 작성해 주세요.'
    const wrapper = mount(CoverLetterRunMonitor, {
      props: {
        userId: 'user-id',
        coverLetterId: 'cover-letter-id',
        agentRunId: 'run-id',
        questionLabels: { 'question-id': fullQuestion },
      },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    const preview = wrapper.get('.cover-run-monitor__scope-preview')
    expect(preview.text()).not.toBe(fullQuestion)
    expect(preview.text()).toHaveLength(49)
    expect(preview.text()).toMatch(/…$/)
    expect(preview.attributes('aria-label')).toBe(fullQuestion)
    expect(preview.attributes('title')).toBe(fullQuestion)
  })

  it('shows nothing once the run finished successfully', () => {
    runMocks.detail.data.value = {
      id: 'run-id',
      workflowType: 'COVER_LETTER_GENERATION',
      resourceType: 'COVER_LETTER',
      resourceId: 'cover-letter-id',
      status: 'SUCCEEDED',
      stateVersion: 3,
      progressPercent: 100,
      partialResult: { succeededScopeKeys: ['question-id'], failedScopeKeys: [] },
    }
    const wrapper = mount(CoverLetterRunMonitor, {
      props: {
        userId: 'user-id',
        coverLetterId: 'cover-letter-id',
        agentRunId: 'run-id',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.find('.cover-run-monitor__box').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('초안을 다 썼어요')
    expect(wrapper.emitted('terminal')).toHaveLength(1)
  })
})
