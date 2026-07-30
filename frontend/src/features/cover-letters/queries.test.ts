import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'

import { agentRunQueryKeys } from '@/features/agent-runs/queries'
import { jobQueryKeys } from '@/features/jobs/queries'

import { coverLetterQueryKeys, invalidateCoverLetterQueries } from './queries'

describe('P7 cover letter query ownership and terminal invalidation', () => {
  it('uses user-scoped list/detail/version/verification keys', () => {
    expect(coverLetterQueryKeys.detail('user-1', 'cover-1')).not.toEqual(
      coverLetterQueryKeys.detail('user-2', 'cover-1'),
    )
    expect(coverLetterQueryKeys.versions('user-1', 'question-1', {})).toEqual([
      'user',
      'user-1',
      'coverLetterQuestion',
      'question-1',
      'versions',
      {},
    ])
    expect(coverLetterQueryKeys.verifications('user-1', 'version-1', {})).toEqual([
      'user',
      'user-1',
      'coverLetterAnswerVersion',
      'version-1',
      'verifications',
      {},
    ])
  })

  it('invalidates detail/list, related Agent Run and Job projections after terminal work', async () => {
    const cache = new QueryClient()
    cache.setQueryData(coverLetterQueryKeys.detail('user-1', 'cover-1'), { id: 'cover-1' })
    cache.setQueryData(coverLetterQueryKeys.list('user-1', {}), { items: [] })
    cache.setQueryData(agentRunQueryKeys.relatedResource('user-1', 'COVER_LETTER', 'cover-1'), {
      items: [],
    })
    cache.setQueryData(jobQueryKeys.list('user-1', {}), { items: [] })

    await invalidateCoverLetterQueries(cache, 'user-1', 'cover-1')

    expect(
      cache.getQueryState(coverLetterQueryKeys.detail('user-1', 'cover-1'))?.isInvalidated,
    ).toBe(true)
    expect(
      cache.getQueryState(agentRunQueryKeys.relatedResource('user-1', 'COVER_LETTER', 'cover-1'))
        ?.isInvalidated,
    ).toBe(true)
    expect(cache.getQueryState(jobQueryKeys.list('user-1', {}))?.isInvalidated).toBe(true)
  })
})
