import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AgentRunDetailDto, AgentRunSseEventMap } from '@/shared/api/agentRunContracts'
import { SessionCleanupCoordinator } from '@/shared/session/sessionCleanup'

import { agentRunDetail, RUN_ID, STEP_ID } from './testFixtures'
import {
  AGENT_RUN_POLL_INTERVAL_MS,
  AGENT_RUN_RECONNECT_DELAYS_MS,
  AgentRunStreamController,
  closeAgentRunStreamsForResource,
  mergeAgentRunEvent,
  type AgentRunConnectionState,
  type EventSourceFactory,
} from './stream'

class FakeEventSource {
  private openListener: (() => void) | null = null
  private errorListener: (() => void) | null = null
  private readonly listeners = new Map<string, (data: string) => void>()
  closed = false

  onOpen(listener: () => void): void {
    this.openListener = listener
  }

  onError(listener: () => void): void {
    this.errorListener = listener
  }

  onMessage(type: keyof AgentRunSseEventMap, listener: (data: string) => void): void {
    this.listeners.set(type, listener)
  }

  open(): void {
    this.openListener?.()
  }

  error(): void {
    this.errorListener?.()
  }

  emit(type: keyof AgentRunSseEventMap, event: unknown): void {
    this.listeners.get(type)?.(JSON.stringify(event))
  }

  close(): void {
    this.closed = true
  }
}

describe('AgentRunStreamController', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('requires the authoritative snapshot, ignores lower/equal versions, and merges every live event', () => {
    const sources: FakeEventSource[] = []
    const { cache, latest, invalidations } = cacheFixture(agentRunDetail({ stateVersion: 5 }))
    const controller = new AgentRunStreamController({
      userId: 'user-1',
      agentRunId: RUN_ID,
      initialRun: latest.value,
      cache,
      eventSourceFactory: sourceFactory(sources),
    })
    controller.start()
    const source = sources[0]
    expect(source).toBeDefined()

    source?.emit('progress', progressEvent(6))
    expect(latest.value.stateVersion).toBe(5)

    source?.emit('snapshot', snapshotEvent(agentRunDetail({ stateVersion: 4 })))
    expect(latest.value.stateVersion).toBe(4)

    source?.emit('heartbeat', heartbeatEvent(4, 'RUNNING'))
    expect(latest.value.stateVersion).toBe(4)

    source?.emit('progress', progressEvent(5))
    expect(latest.value).toMatchObject({
      stateVersion: 5,
      progressPercent: 45,
      currentStep: 'TRANSFORM_FIXTURE',
    })

    source?.emit('step', stepEvent(6))
    expect(latest.value.steps).toHaveLength(1)
    expect(latest.value.steps[0]).toMatchObject({ id: STEP_ID, attempt: 1, maxAttempts: 3 })

    source?.emit('waiting_user', waitingEvent(7))
    expect(latest.value).toMatchObject({
      status: 'WAITING_USER',
      retryable: false,
      stateVersion: 7,
    })

    source?.emit('heartbeat', heartbeatEvent(8, 'WAITING_USER'))
    expect(latest.value.stateVersion).toBe(8)

    source?.emit('terminal', terminalEvent(9, 'FAILED'))
    expect(latest.value).toMatchObject({
      status: 'FAILED',
      stateVersion: 9,
      retryable: true,
      cancellable: false,
    })
    expect(source?.closed).toBe(true)
    expect(invalidations[0]).toEqual(['user', 'user-1', 'agentRuns'])
  })

  it('reconnects after 1/2/5 seconds, then polls every 5 seconds without marking a disconnect failed', async () => {
    const sources: FakeEventSource[] = []
    const states: AgentRunConnectionState[] = []
    const { cache, latest, invalidations } = cacheFixture(agentRunDetail())
    const fetchDetail = vi
      .fn<(agentRunId: string) => Promise<AgentRunDetailDto>>()
      .mockResolvedValue(
        agentRunDetail({
          status: 'SUCCEEDED',
          progressPercent: 100,
          stateVersion: 2,
          completedAt: '2026-07-19T00:00:10Z',
          cancellable: false,
        }),
      )
    const controller = new AgentRunStreamController({
      userId: 'user-1',
      agentRunId: RUN_ID,
      initialRun: latest.value,
      cache,
      eventSourceFactory: sourceFactory(sources),
      fetchDetail,
      onConnectionState: (state) => states.push(state),
    })
    controller.start()

    sources[0]?.error()
    expect(latest.value.status).toBe('RUNNING')
    expect(sources).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(AGENT_RUN_RECONNECT_DELAYS_MS[0])
    expect(sources).toHaveLength(2)

    sources[1]?.error()
    await vi.advanceTimersByTimeAsync(AGENT_RUN_RECONNECT_DELAYS_MS[1])
    expect(sources).toHaveLength(3)

    sources[2]?.error()
    await vi.advanceTimersByTimeAsync(AGENT_RUN_RECONNECT_DELAYS_MS[2])
    expect(sources).toHaveLength(4)

    sources[3]?.error()
    expect(states.at(-1)).toBe('polling')
    expect(latest.value.status).toBe('RUNNING')
    await vi.advanceTimersByTimeAsync(AGENT_RUN_POLL_INTERVAL_MS)

    expect(fetchDetail).toHaveBeenCalledWith(RUN_ID)
    expect(latest.value.status).toBe('SUCCEEDED')
    expect(states.at(-1)).toBe('closed')
    expect(invalidations).toContainEqual(['user', 'user-1', 'agentRuns'])
  })

  it('closes the concrete EventSource on logout, 401 cleanup, or user boundary change', async () => {
    const sources: FakeEventSource[] = []
    const { cache, latest } = cacheFixture(agentRunDetail())
    const cancelQueries = vi.fn(async () => undefined)
    const clear = vi.fn()
    const cleanup = new SessionCleanupCoordinator(
      { cancelQueries, clear },
      { purgeForUser: vi.fn() },
    )
    const controller = new AgentRunStreamController({
      userId: 'user-1',
      agentRunId: RUN_ID,
      initialRun: latest.value,
      cache,
      cleanup,
      eventSourceFactory: sourceFactory(sources),
    })
    controller.start()

    await cleanup.cleanup({ userId: 'user-1', resetAuthStore: vi.fn() })

    expect(sources[0]?.closed).toBe(true)
    expect(cancelQueries).toHaveBeenCalledOnce()
    expect(clear).toHaveBeenCalledOnce()
  })

  it('invalidates every document projection on terminal and can close only the deleted resource stream', () => {
    const documentId = '00000000-0000-4000-8000-000000000010'
    const sources: FakeEventSource[] = []
    const initial = agentRunDetail({ resourceType: 'DOCUMENT', resourceId: documentId })
    const { cache, invalidations } = cacheFixture(initial)
    const controller = new AgentRunStreamController({
      userId: 'user-1',
      agentRunId: RUN_ID,
      initialRun: initial,
      cache,
      eventSourceFactory: sourceFactory(sources),
    })
    controller.start()
    closeAgentRunStreamsForResource('user-2', 'DOCUMENT', documentId)
    expect(sources[0]?.closed).toBe(false)
    sources[0]?.emit('snapshot', snapshotEvent(initial))
    sources[0]?.emit('waiting_user', waitingEvent(2))
    expect(invalidations).toContainEqual(['user', 'user-1', 'documents'])
    expect(invalidations).toContainEqual(['user', 'user-1', 'document', documentId])
    sources[0]?.emit('terminal', {
      ...terminalEvent(3, 'SUCCEEDED'),
      resourceType: 'DOCUMENT',
      resourceId: documentId,
    })

    expect(invalidations).toContainEqual(['user', 'user-1', 'documents'])
    expect(invalidations).toContainEqual(['user', 'user-1', 'document', documentId])
    expect(invalidations).toContainEqual(['user', 'user-1', 'documentText', documentId])
    expect(invalidations).toContainEqual(['user', 'user-1', 'evidence'])
    expect(sources[0]?.closed).toBe(true)

    const secondSources: FakeEventSource[] = []
    const second = new AgentRunStreamController({
      userId: 'user-1',
      agentRunId: RUN_ID,
      initialRun: initial,
      cache,
      eventSourceFactory: sourceFactory(secondSources),
    })
    second.start()
    closeAgentRunStreamsForResource('user-1', 'DOCUMENT', documentId)
    expect(secondSources[0]?.closed).toBe(true)
  })
})

describe('mergeAgentRunEvent', () => {
  it('does not expose transport state as an AgentRun status', () => {
    const current = agentRunDetail()
    const merged = mergeAgentRunEvent(current, 'progress', progressEvent(2))
    expect(merged.status).toBe('RUNNING')
    expect(merged).not.toHaveProperty('connectionState')
  })
})

function sourceFactory(sources: FakeEventSource[]): EventSourceFactory {
  return () => {
    const source = new FakeEventSource()
    sources.push(source)
    return source
  }
}

function cacheFixture(initial: AgentRunDetailDto) {
  const latest = { value: initial }
  const invalidations: unknown[][] = []
  return {
    latest,
    invalidations,
    cache: {
      setQueryData: vi.fn((_key: unknown, value: unknown) => {
        latest.value = value as AgentRunDetailDto
      }),
      invalidateQueries: vi.fn((options: { queryKey?: readonly unknown[] }) => {
        invalidations.push([...(options.queryKey ?? [])])
        return Promise.resolve()
      }),
    },
  }
}

function eventBase(stateVersion: number) {
  return {
    agentRunId: RUN_ID,
    stateVersion,
    occurredAt: `2026-07-19T00:00:${String(stateVersion).padStart(2, '0')}Z`,
  }
}

function snapshotEvent(run: AgentRunDetailDto): AgentRunSseEventMap['snapshot'] {
  return { ...eventBase(run.stateVersion), run }
}

function progressEvent(stateVersion: number): AgentRunSseEventMap['progress'] {
  return {
    ...eventBase(stateVersion),
    status: 'RUNNING',
    currentStep: 'TRANSFORM_FIXTURE',
    progressPercent: 45,
    actualCostUsd: 0.02,
  }
}

function stepEvent(stateVersion: number): AgentRunSseEventMap['step'] {
  return {
    ...eventBase(stateVersion),
    step: {
      id: STEP_ID,
      stepKey: 'TRANSFORM_FIXTURE',
      scopeKey: null,
      stepOrder: 2,
      status: 'SUCCEEDED',
      attempt: 1,
      maxAttempts: 3,
      startedAt: '2026-07-19T00:00:02Z',
      completedAt: '2026-07-19T00:00:03Z',
      safeError: null,
    },
  }
}

function waitingEvent(stateVersion: number): AgentRunSseEventMap['waiting_user'] {
  return {
    ...eventBase(stateVersion),
    requiredUserAction: {
      type: 'PROVIDE_DOCUMENT_TEXT',
      resource: null,
      route: '/profile/basic',
      message: '필수 정보를 입력해 주세요.',
    },
  }
}

function heartbeatEvent(
  stateVersion: number,
  status: AgentRunSseEventMap['heartbeat']['status'],
): AgentRunSseEventMap['heartbeat'] {
  return {
    ...eventBase(stateVersion),
    serverTime: '2026-07-19T00:00:10Z',
    status,
  }
}

function terminalEvent(
  stateVersion: number,
  status: AgentRunSseEventMap['terminal']['status'],
): AgentRunSseEventMap['terminal'] {
  return {
    ...eventBase(stateVersion),
    status,
    completedAt: '2026-07-19T00:00:10Z',
    actualCostUsd: 0.025,
    retryable: status === 'FAILED' || status === 'INTERRUPTED',
    safeError:
      status === 'FAILED' ? { code: 'SAFE_FAILURE', message: '다시 시도해 주세요.' } : null,
    resourceType: null,
    resourceId: null,
  }
}
