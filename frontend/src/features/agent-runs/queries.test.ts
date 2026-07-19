import { describe, expect, it, vi } from 'vitest'

import type { AgentRunListParams } from '@/shared/api/agentRunApi'

import { agentRunDetail, RUN_ID } from './testFixtures'
import { agentRunQueryKeys, applyAgentRunDetail } from './queries'

describe('Agent Run query namespace', () => {
  it('includes the authenticated user and stable filter projection', () => {
    const filters: AgentRunListParams = {
      workflowType: ['JOB_ANALYSIS'],
      status: ['FAILED'],
      page: 2,
      size: 20,
      sort: 'updatedAt,desc',
    }
    expect(agentRunQueryKeys.list('user-1', filters)).toEqual([
      'user',
      'user-1',
      'agentRuns',
      'list',
      filters,
    ])
    expect(agentRunQueryKeys.detail('user-2', RUN_ID)).toEqual([
      'user',
      'user-2',
      'agentRuns',
      'detail',
      RUN_ID,
    ])
  })

  it('applies cancel and REST snapshots only to the current user detail key', () => {
    const setQueryData = vi.fn()
    const detail = agentRunDetail({ stateVersion: 3 })
    applyAgentRunDetail({ setQueryData }, 'user-1', detail)
    expect(setQueryData).toHaveBeenCalledWith(
      ['user', 'user-1', 'agentRuns', 'detail', RUN_ID],
      detail,
    )
  })
})
