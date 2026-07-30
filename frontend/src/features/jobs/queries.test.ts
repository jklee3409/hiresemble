import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'

import { agentRunQueryKeys } from '@/features/agent-runs/queries'

import { finalizeJobDeletion, invalidateJobAnalysisQueries, jobQueryKeys } from './queries'

describe('P5 Job query keys and deletion cleanup', () => {
  it('uses exact user-scoped list/detail keys and existing Agent Run resource filters', () => {
    const filters = { status: 'IN_PROGRESS' as const, page: 0, size: 20 }
    const analysisFilters = { page: 0, size: 20, sort: 'analysisVersion,desc' as const }
    expect(jobQueryKeys.list('user-1', filters)).toEqual(['user', 'user-1', 'jobs', filters])
    expect(jobQueryKeys.detail('user-1', 'job-1')).toEqual(['user', 'user-1', 'job', 'job-1'])
    expect(jobQueryKeys.analysisList('user-1', 'job-1', analysisFilters)).toEqual([
      'user',
      'user-1',
      'job',
      'job-1',
      'analyses',
      'list',
      analysisFilters,
    ])
    expect(jobQueryKeys.latestAnalysis('user-1', 'job-1')).toEqual([
      'user',
      'user-1',
      'job',
      'job-1',
      'analysis',
      'latest',
    ])
    expect(
      agentRunQueryKeys.list('user-1', {
        resourceType: 'JOB',
        resourceId: 'job-1',
        page: 0,
        size: 1,
        sort: 'queuedAt,desc',
      }),
    ).not.toEqual(
      agentRunQueryKeys.list('user-2', {
        resourceType: 'JOB',
        resourceId: 'job-1',
        page: 0,
        size: 1,
        sort: 'queuedAt,desc',
      }),
    )
  })

  it('removes the deleted detail and marks both Job list and related runs stale', async () => {
    const cache = new QueryClient()
    cache.setQueryData(jobQueryKeys.detail('user-1', 'job-1'), { id: 'job-1' })
    cache.setQueryData(jobQueryKeys.list('user-1', {}), { items: [{ id: 'job-1' }] })
    cache.setQueryData(
      agentRunQueryKeys.list('user-1', {
        resourceType: 'JOB',
        resourceId: 'job-1',
      }),
      { items: [{ id: 'run-1' }] },
    )

    await finalizeJobDeletion(cache, 'user-1', 'job-1')

    expect(cache.getQueryData(jobQueryKeys.detail('user-1', 'job-1'))).toBeUndefined()
    expect(cache.getQueryState(jobQueryKeys.list('user-1', {}))?.isInvalidated).toBe(true)
    expect(
      cache.getQueryState(
        agentRunQueryKeys.list('user-1', {
          resourceType: 'JOB',
          resourceId: 'job-1',
        }),
      )?.isInvalidated,
    ).toBe(true)
  })

  it('invalidates the current Job, analysis history/latest and related run after a terminal run', async () => {
    const cache = new QueryClient()
    cache.setQueryData(jobQueryKeys.detail('user-1', 'job-1'), { id: 'job-1' })
    cache.setQueryData(jobQueryKeys.analysisList('user-1', 'job-1', {}), { items: [] })
    cache.setQueryData(jobQueryKeys.latestAnalysis('user-1', 'job-1'), { id: 'analysis-1' })

    await invalidateJobAnalysisQueries(cache, 'user-1', 'job-1')

    expect(cache.getQueryState(jobQueryKeys.detail('user-1', 'job-1'))?.isInvalidated).toBe(true)
    expect(
      cache.getQueryState(jobQueryKeys.analysisList('user-1', 'job-1', {}))?.isInvalidated,
    ).toBe(true)
    expect(cache.getQueryState(jobQueryKeys.latestAnalysis('user-1', 'job-1'))?.isInvalidated).toBe(
      true,
    )
  })
})
