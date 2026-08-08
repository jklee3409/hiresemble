import { describe, expect, it } from 'vitest'

import { PendingGitHubIdempotencyKeys } from './idempotency'

describe('GitHub pending idempotency keys', () => {
  it('reuses a pending identity and rotates after input change or success', () => {
    let sequence = 0
    const keys = new PendingGitHubIdempotencyKeys((action) => `${action}-key-${++sequence}`)
    const first = keys.keyFor('selection:source-1', 'ids=a,version=1', 'selection')
    expect(keys.keyFor('selection:source-1', 'ids=a,version=1', 'selection')).toBe(first)

    const changed = keys.keyFor('selection:source-1', 'ids=b,version=1', 'selection')
    expect(changed).not.toBe(first)
    keys.complete('selection:source-1', 'ids=b,version=1')
    expect(keys.keyFor('selection:source-1', 'ids=b,version=1', 'selection')).not.toBe(changed)
  })

  it('keeps create, selection, and refresh scopes independent', () => {
    let sequence = 0
    const keys = new PendingGitHubIdempotencyKeys((action) => `${action}-${++sequence}`)
    expect(keys.keyFor('create', 'url', 'create')).toBe('create-1')
    expect(keys.keyFor('selection:source', 'ids', 'selection')).toBe('selection-2')
    expect(keys.keyFor('refresh:source', 'version', 'refresh')).toBe('refresh-3')
  })
})
