import { afterEach, describe, expect, it, vi } from 'vitest'

import { agentRunDetail, RUN_ID } from '@/features/agent-runs/testFixtures'

import { cancelAgentRun, createRetryIdempotencyKey, retryAgentRun } from './agentRunApi'
import { apiClient } from './http'

describe('Agent Run commands', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses a reusable caller-owned Idempotency-Key for retry', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      agentRunId: RUN_ID,
      status: 'QUEUED',
      resourceType: null,
      resourceId: null,
      replayed: false,
    })
    const key = createRetryIdempotencyKey()

    await retryAgentRun(RUN_ID, key)
    await retryAgentRun(RUN_ID, key)

    expect(key).toMatch(/^[A-Za-z0-9._:-]{8,128}$/)
    expect(post).toHaveBeenNthCalledWith(1, `/agent-runs/${RUN_ID}/retry`, undefined, {
      headers: { 'Idempotency-Key': key },
    })
    expect(post).toHaveBeenNthCalledWith(2, `/agent-runs/${RUN_ID}/retry`, undefined, {
      headers: { 'Idempotency-Key': key },
    })
  })

  it('sends cancel with the current stateVersion CAS value', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue(agentRunDetail({ stateVersion: 4 }))

    await cancelAgentRun(RUN_ID, 3)

    expect(post).toHaveBeenCalledWith(`/agent-runs/${RUN_ID}/cancel`, { stateVersion: 3 })
  })
})
