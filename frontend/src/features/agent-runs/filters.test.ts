import { describe, expect, it } from 'vitest'

import { toAgentRunSearchParams } from '@/shared/api/agentRunApi'

import { agentRunQuerySignature, canonicalAgentRunQuery, parseAgentRunFilters } from './filters'

describe('Agent Run list URL filters', () => {
  it('normalizes repeatable values and removes invalid filters from the canonical URL', () => {
    const filters = parseAgentRunFilters({
      workflowType: ['INVALID', 'JOB_ANALYSIS', 'JOB_ANALYSIS', 'DOCUMENT_INGESTION'],
      status: ['FAILED', 'UNKNOWN', 'INTERRUPTED'],
      retryable: 'sometimes',
      page: '-1',
      size: '1000',
      sort: 'cost,asc',
      resourceType: 'document',
      resourceId: 'invalid',
      unknown: 'drop-me',
    })

    expect(filters).toEqual({
      workflowType: ['DOCUMENT_INGESTION', 'JOB_ANALYSIS'],
      status: ['FAILED', 'INTERRUPTED'],
      page: 0,
      size: 20,
      sort: 'queuedAt,desc',
    })
    const canonical = canonicalAgentRunQuery(filters)
    expect(canonical).toEqual({
      workflowType: ['DOCUMENT_INGESTION', 'JOB_ANALYSIS'],
      status: ['FAILED', 'INTERRUPTED'],
    })
    expect(agentRunQuerySignature(canonical)).not.toContain('unknown')
  })

  it('preserves pagination and both allowed sorts', () => {
    const filters = parseAgentRunFilters({
      retryable: 'false',
      page: '3',
      size: '50',
      sort: 'updatedAt,desc',
    })
    expect(filters).toMatchObject({ retryable: false, page: 3, size: 50, sort: 'updatedAt,desc' })
    expect(canonicalAgentRunQuery(filters)).toMatchObject({
      retryable: 'false',
      page: '3',
      size: '50',
      sort: 'updatedAt,desc',
    })
  })

  it('serializes workflow and status filters as repeatable parameters', () => {
    const query = toAgentRunSearchParams({
      workflowType: ['JOB_ANALYSIS', 'DOCUMENT_INGESTION'],
      status: ['FAILED', 'INTERRUPTED'],
      retryable: true,
      page: 2,
      size: 20,
      sort: 'updatedAt,desc',
    })
    expect(query.getAll('workflowType')).toEqual(['JOB_ANALYSIS', 'DOCUMENT_INGESTION'])
    expect(query.getAll('status')).toEqual(['FAILED', 'INTERRUPTED'])
    expect(query.get('retryable')).toBe('true')
    expect(query.get('sort')).toBe('updatedAt,desc')
  })
})
