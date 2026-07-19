import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SessionCleanupCoordinator, SessionStorageDraftPurgePort } from './sessionCleanup'

describe('SessionCleanupCoordinator', () => {
  it('closes streams, cancels requests, clears cache, resets Pinia, then purges drafts', async () => {
    const order: string[] = []
    const cache = {
      cancelQueries: vi.fn(async () => {
        order.push('cancel-queries')
      }),
      clear: vi.fn(() => order.push('clear-cache')),
    }
    const drafts = {
      purgeForUser: vi.fn(() => order.push('purge-drafts')),
    }
    const coordinator = new SessionCleanupCoordinator(cache, drafts)
    coordinator.registerEventSource({ close: () => order.push('close-event-source') })
    coordinator.registerStoreReset({ reset: () => order.push('reset-related-store') })

    await coordinator.cleanup({
      userId: 'user-1',
      resetAuthStore: () => order.push('reset-auth-store'),
    })

    expect(order).toEqual([
      'close-event-source',
      'cancel-queries',
      'clear-cache',
      'reset-related-store',
      'reset-auth-store',
      'purge-drafts',
    ])
  })
})

describe('SessionStorageDraftPurgePort', () => {
  beforeEach(() => window.sessionStorage.clear())

  it('purges only the current user draft key shape', () => {
    window.sessionStorage.setItem('1/user-1/cover/id/question/version', '{}')
    window.sessionStorage.setItem('1/user-2/cover/id/question/version', '{}')
    window.sessionStorage.setItem('unrelated/user-1', 'keep')

    new SessionStorageDraftPurgePort().purgeForUser('user-1')

    expect(window.sessionStorage.getItem('1/user-1/cover/id/question/version')).toBeNull()
    expect(window.sessionStorage.getItem('1/user-2/cover/id/question/version')).toBe('{}')
    expect(window.sessionStorage.getItem('unrelated/user-1')).toBe('keep')
  })
})
