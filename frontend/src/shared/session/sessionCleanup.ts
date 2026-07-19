import type { QueryClient } from '@tanstack/vue-query'

import { queryClient } from '@/app/queryClient'

export interface EventSourceCleanupPort {
  close(): void
}

export interface StoreResetPort {
  reset(): void
}

export interface DraftPurgePort {
  purgeForUser(userId: string): void
}

interface SessionCleanupInput {
  userId: string | null
  resetAuthStore: () => void
}

export class SessionCleanupCoordinator {
  private readonly eventSources = new Set<EventSourceCleanupPort>()
  private readonly storeResetPorts = new Set<StoreResetPort>()

  constructor(
    private readonly cache: Pick<QueryClient, 'cancelQueries' | 'clear'>,
    private readonly drafts: DraftPurgePort,
  ) {}

  registerEventSource(source: EventSourceCleanupPort): () => void {
    this.eventSources.add(source)
    return () => this.eventSources.delete(source)
  }

  registerStoreReset(port: StoreResetPort): () => void {
    this.storeResetPorts.add(port)
    return () => this.storeResetPorts.delete(port)
  }

  async cleanup(input: SessionCleanupInput): Promise<void> {
    for (const source of this.eventSources) {
      source.close()
    }
    this.eventSources.clear()

    await this.cache.cancelQueries()
    this.cache.clear()

    for (const port of this.storeResetPorts) {
      port.reset()
    }
    input.resetAuthStore()

    if (input.userId !== null) {
      this.drafts.purgeForUser(input.userId)
    }
  }
}

export class SessionStorageDraftPurgePort implements DraftPurgePort {
  purgeForUser(userId: string): void {
    if (typeof window === 'undefined') {
      return
    }

    const keysToDelete: string[] = []
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index)
      if (key === null) {
        continue
      }

      const segments = key.split('/')
      if (segments.length >= 6 && segments[1] === userId) {
        keysToDelete.push(key)
      }
    }

    for (const key of keysToDelete) {
      window.sessionStorage.removeItem(key)
    }
  }
}

export const sessionCleanup = new SessionCleanupCoordinator(
  queryClient,
  new SessionStorageDraftPurgePort(),
)
