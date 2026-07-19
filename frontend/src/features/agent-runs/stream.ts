import type { ZodType } from 'zod'

import {
  heartbeatEventSchema,
  progressEventSchema,
  snapshotEventSchema,
  stepEventSchema,
  terminalEventSchema,
  waitingUserEventSchema,
  type AgentRunDetailDto,
  type AgentRunSseEventMap,
  type AgentRunStatus,
  type AgentStepDto,
} from '@/shared/api/agentRunContracts'
import { getAgentRun } from '@/shared/api/agentRunApi'
import { documentQueryKeys } from '@/features/documents/queries'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import {
  sessionCleanup,
  type EventSourceCleanupPort,
  type SessionCleanupCoordinator,
} from '@/shared/session/sessionCleanup'

import { agentRunQueryKeys } from './queries'

export const AGENT_RUN_RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000] as const
export const AGENT_RUN_POLL_INTERVAL_MS = 5_000
const activeStreams = new Set<AgentRunStreamController>()

export type AgentRunConnectionState =
  'connecting' | 'connected' | 'reconnecting' | 'polling' | 'closed'

interface EventSourcePort {
  onOpen(listener: () => void): void
  onError(listener: () => void): void
  onMessage(type: keyof AgentRunSseEventMap, listener: (data: string) => void): void
  close(): void
}

export type EventSourceFactory = (url: string) => EventSourcePort

interface Scheduler {
  setTimeout(callback: () => void, delay: number): ReturnType<typeof setTimeout>
  clearTimeout(handle: ReturnType<typeof setTimeout>): void
}

export interface AgentRunStreamOptions {
  userId: string
  agentRunId: string
  initialRun: AgentRunDetailDto
  cache: AgentRunStreamCache
  cleanup?: SessionCleanupCoordinator
  eventSourceFactory?: EventSourceFactory
  fetchDetail?: (agentRunId: string) => Promise<AgentRunDetailDto>
  scheduler?: Scheduler
  onConnectionState?: (state: AgentRunConnectionState) => void
}

export interface AgentRunStreamCache {
  setQueryData(queryKey: readonly unknown[], detail: AgentRunDetailDto): unknown
  invalidateQueries(filters: { queryKey: readonly unknown[] }): Promise<unknown>
}

export class AgentRunStreamController implements EventSourceCleanupPort {
  private readonly sourceFactory: EventSourceFactory
  private readonly fetchDetail: (agentRunId: string) => Promise<AgentRunDetailDto>
  private readonly scheduler: Scheduler
  private readonly cleanup: SessionCleanupCoordinator
  private source: EventSourcePort | null = null
  private timer: ReturnType<typeof setTimeout> | null = null
  private unregisterCleanup: (() => void) | null = null
  private reconnectAttempts = 0
  private snapshotReceived = false
  private stopped = false
  private currentRun: AgentRunDetailDto

  constructor(private readonly options: AgentRunStreamOptions) {
    this.sourceFactory = options.eventSourceFactory ?? browserEventSourceFactory
    this.fetchDetail = options.fetchDetail ?? getAgentRun
    this.scheduler = options.scheduler ?? browserScheduler
    this.cleanup = options.cleanup ?? sessionCleanup
    this.currentRun = options.initialRun
  }

  start(): void {
    if (this.stopped || this.source !== null || this.timer !== null) return
    activeStreams.add(this)
    this.unregisterCleanup = this.cleanup.registerEventSource(this)
    if (isTerminal(this.currentRun.status)) {
      this.finish(this.currentRun)
      return
    }
    this.connect('connecting')
  }

  close(): void {
    if (this.stopped) return
    this.stopped = true
    activeStreams.delete(this)
    this.stopTransport()
    this.unregisterCleanup?.()
    this.unregisterCleanup = null
    this.emitConnectionState('closed')
  }

  private connect(state: Extract<AgentRunConnectionState, 'connecting' | 'reconnecting'>): void {
    if (this.stopped) return
    this.emitConnectionState(state)
    this.snapshotReceived = false
    const source = this.sourceFactory(agentRunEventsUrl(this.options.agentRunId))
    this.source = source
    source.onOpen(() => {
      if (!this.stopped && this.source === source) this.emitConnectionState('connected')
    })
    source.onError(() => this.handleDisconnect(source))
    source.onMessage('snapshot', (data) => this.handleSnapshot(source, data))
    source.onMessage('progress', (data) =>
      this.handleLiveEvent(source, 'progress', progressEventSchema, data),
    )
    source.onMessage('step', (data) => this.handleLiveEvent(source, 'step', stepEventSchema, data))
    source.onMessage('waiting_user', (data) =>
      this.handleLiveEvent(source, 'waiting_user', waitingUserEventSchema, data),
    )
    source.onMessage('heartbeat', (data) =>
      this.handleLiveEvent(source, 'heartbeat', heartbeatEventSchema, data),
    )
    source.onMessage('terminal', (data) =>
      this.handleLiveEvent(source, 'terminal', terminalEventSchema, data),
    )
  }

  private handleSnapshot(source: EventSourcePort, data: string): void {
    if (this.stopped || this.source !== source) return
    const parsed = parseEvent(snapshotEventSchema, data)
    if (
      parsed === null ||
      parsed.agentRunId !== this.options.agentRunId ||
      parsed.run.id !== this.options.agentRunId ||
      parsed.run.stateVersion !== parsed.stateVersion
    ) {
      return
    }

    this.snapshotReceived = true
    this.reconnectAttempts = 0
    this.applyRun(parsed.run)
    if (isTerminal(parsed.run.status)) this.finish(parsed.run)
  }

  private handleLiveEvent<K extends Exclude<keyof AgentRunSseEventMap, 'snapshot'>>(
    source: EventSourcePort,
    type: K,
    schema: ZodType<AgentRunSseEventMap[K]>,
    data: string,
  ): void {
    if (this.stopped || this.source !== source || !this.snapshotReceived) return
    const event = parseEvent(schema, data)
    if (
      event === null ||
      event.agentRunId !== this.options.agentRunId ||
      event.stateVersion <= this.currentRun.stateVersion
    ) {
      return
    }

    const next = mergeAgentRunEvent(this.currentRun, type, event)
    this.applyRun(next)
    if (type === 'terminal' || isTerminal(next.status)) this.finish(next)
  }

  private handleDisconnect(source: EventSourcePort): void {
    if (this.stopped || this.source !== source || isTerminal(this.currentRun.status)) return
    source.close()
    this.source = null
    this.snapshotReceived = false

    if (this.reconnectAttempts < AGENT_RUN_RECONNECT_DELAYS_MS.length) {
      const delay = AGENT_RUN_RECONNECT_DELAYS_MS[this.reconnectAttempts]
      this.reconnectAttempts += 1
      this.emitConnectionState('reconnecting')
      this.timer = this.scheduler.setTimeout(() => {
        this.timer = null
        this.connect('reconnecting')
      }, delay)
      return
    }

    this.startPolling()
  }

  private startPolling(): void {
    if (this.stopped) return
    this.emitConnectionState('polling')
    this.timer = this.scheduler.setTimeout(() => {
      this.timer = null
      void this.pollOnce()
    }, AGENT_RUN_POLL_INTERVAL_MS)
  }

  private async pollOnce(): Promise<void> {
    if (this.stopped) return
    try {
      const detail = await this.fetchDetail(this.options.agentRunId)
      if (detail.stateVersion > this.currentRun.stateVersion) this.applyRun(detail)
      if (isTerminal(detail.status)) {
        this.finish(detail)
        return
      }
    } catch {
      // The last committed snapshot remains visible. A 401 cleanup closes this controller
      // through SessionCleanupCoordinator before the rejected request reaches this branch.
    }
    if (!this.stopped) this.startPolling()
  }

  private applyRun(run: AgentRunDetailDto): void {
    this.currentRun = run
    this.options.cache.setQueryData(
      agentRunQueryKeys.detail(this.options.userId, this.options.agentRunId),
      run,
    )
    if (
      run.status === 'WAITING_USER' &&
      run.resourceType === 'DOCUMENT' &&
      run.resourceId !== null
    ) {
      void this.options.cache.invalidateQueries({
        queryKey: documentQueryKeys.root(this.options.userId),
      })
      void this.options.cache.invalidateQueries({
        queryKey: documentQueryKeys.detail(this.options.userId, run.resourceId),
      })
    }
  }

  private finish(run: AgentRunDetailDto): void {
    if (!isTerminal(run.status)) return
    this.currentRun = run
    this.stopped = true
    activeStreams.delete(this)
    this.stopTransport()
    this.unregisterCleanup?.()
    this.unregisterCleanup = null
    this.emitConnectionState('closed')

    void this.options.cache.invalidateQueries({
      queryKey: agentRunQueryKeys.root(this.options.userId),
    })
    if (run.resourceType !== null && run.resourceId !== null) {
      void this.options.cache.invalidateQueries({
        queryKey: agentRunQueryKeys.relatedResource(
          this.options.userId,
          run.resourceType,
          run.resourceId,
        ),
      })
      if (run.resourceType === 'DOCUMENT') {
        void this.options.cache.invalidateQueries({
          queryKey: documentQueryKeys.root(this.options.userId),
        })
        void this.options.cache.invalidateQueries({
          queryKey: documentQueryKeys.detail(this.options.userId, run.resourceId),
        })
        void this.options.cache.invalidateQueries({
          queryKey: documentQueryKeys.text(this.options.userId, run.resourceId),
        })
        void this.options.cache.invalidateQueries({
          queryKey: profileQueryKeys.evidenceRoot(this.options.userId),
        })
      }
    }
  }

  matchesResource(userId: string, resourceType: string, resourceId: string): boolean {
    return (
      this.options.userId === userId &&
      this.currentRun.resourceType === resourceType &&
      this.currentRun.resourceId === resourceId
    )
  }

  private stopTransport(): void {
    this.source?.close()
    this.source = null
    if (this.timer !== null) {
      this.scheduler.clearTimeout(this.timer)
      this.timer = null
    }
  }

  private emitConnectionState(state: AgentRunConnectionState): void {
    this.options.onConnectionState?.(state)
  }
}

export function closeAgentRunStreamsForResource(
  userId: string,
  resourceType: string,
  resourceId: string,
): void {
  for (const stream of [...activeStreams]) {
    if (stream.matchesResource(userId, resourceType, resourceId)) stream.close()
  }
}

export function mergeAgentRunEvent<K extends Exclude<keyof AgentRunSseEventMap, 'snapshot'>>(
  current: AgentRunDetailDto,
  type: K,
  event: AgentRunSseEventMap[K],
): AgentRunDetailDto {
  switch (type) {
    case 'progress': {
      const progress = event as AgentRunSseEventMap['progress']
      return {
        ...current,
        status: progress.status,
        currentStep: progress.currentStep,
        progressPercent: progress.progressPercent,
        actualCostUsd: progress.actualCostUsd,
        stateVersion: progress.stateVersion,
        updatedAt: progress.occurredAt,
      }
    }
    case 'step': {
      const stepEvent = event as AgentRunSseEventMap['step']
      return {
        ...current,
        steps: mergeStep(current.steps, stepEvent.step),
        stateVersion: stepEvent.stateVersion,
        updatedAt: stepEvent.occurredAt,
      }
    }
    case 'waiting_user': {
      const waiting = event as AgentRunSseEventMap['waiting_user']
      return {
        ...current,
        status: 'WAITING_USER',
        requiredUserAction: waiting.requiredUserAction,
        retryable: false,
        stateVersion: waiting.stateVersion,
        updatedAt: waiting.occurredAt,
      }
    }
    case 'heartbeat': {
      const heartbeat = event as AgentRunSseEventMap['heartbeat']
      return {
        ...current,
        status: heartbeat.status,
        stateVersion: heartbeat.stateVersion,
        updatedAt: heartbeat.occurredAt,
      }
    }
    case 'terminal': {
      const terminal = event as AgentRunSseEventMap['terminal']
      return {
        ...current,
        status: terminal.status,
        completedAt: terminal.completedAt,
        actualCostUsd: terminal.actualCostUsd,
        retryable: terminal.retryable,
        cancellable: false,
        safeError: terminal.safeError,
        resourceType: terminal.resourceType,
        resourceId: terminal.resourceId,
        requiredUserAction: null,
        stateVersion: terminal.stateVersion,
        updatedAt: terminal.occurredAt,
      }
    }
  }
}

export function agentRunEventsUrl(agentRunId: string): string {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '')
  return `${base}/agent-runs/${encodeURIComponent(agentRunId)}/events`
}

function mergeStep(steps: AgentStepDto[], next: AgentStepDto): AgentStepDto[] {
  const existing = steps.findIndex((step) => step.id === next.id)
  const merged =
    existing < 0 ? [...steps, next] : steps.map((step) => (step.id === next.id ? next : step))
  return [...merged].sort(
    (left, right) => left.stepOrder - right.stepOrder || left.attempt - right.attempt,
  )
}

function parseEvent<T>(schema: ZodType<T>, data: string): T | null {
  try {
    const parsed = schema.safeParse(JSON.parse(data) as unknown)
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

function isTerminal(status: AgentRunStatus): boolean {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(status)
}

const browserScheduler: Scheduler = {
  setTimeout: (callback, delay) => setTimeout(callback, delay),
  clearTimeout: (handle) => clearTimeout(handle),
}

const browserEventSourceFactory: EventSourceFactory = (url) => {
  const source = new EventSource(url, { withCredentials: true })
  return {
    onOpen(listener) {
      source.onopen = listener
    },
    onError(listener) {
      source.onerror = listener
    },
    onMessage(type, listener) {
      source.addEventListener(type, (event) => listener((event as MessageEvent<string>).data))
    },
    close() {
      source.close()
    },
  }
}
