import { describe, expect, it } from 'vitest'

import {
  canonicalCoverLetterQuery,
  coverLetterQuerySignature,
  parseCoverLetterFilters,
} from './filters'

describe('P7 cover letter URL filters', () => {
  it('canonicalizes status/query/sort/page/size and drops unknown or invalid values', () => {
    const parsed = parseCoverLetterFilters({
      status: 'ARCHIVED',
      query: '  hiresemble  ',
      sort: 'title,asc',
      page: '2',
      size: '50',
      jobId: 'not-a-uuid',
      unknown: 'x',
    })
    expect(parsed).toEqual({
      status: 'ARCHIVED',
      query: 'hiresemble',
      sort: 'title,asc',
      page: 2,
      size: 50,
      jobId: undefined,
    })
    expect(canonicalCoverLetterQuery(parsed)).toEqual({
      status: 'ARCHIVED',
      query: 'hiresemble',
      sort: 'title,asc',
      page: '2',
      size: '50',
    })
  })

  it('uses allowlisted defaults and a stable query signature', () => {
    expect(parseCoverLetterFilters({ status: 'DELETED', page: '-1', size: '200' })).toEqual({
      jobId: undefined,
      status: undefined,
      query: undefined,
      page: 0,
      size: 20,
      sort: 'updatedAt,desc',
    })
    expect(coverLetterQuerySignature({ status: 'DRAFT', query: 'a b' })).toBe(
      'query=a%20b&status=DRAFT',
    )
  })
})
