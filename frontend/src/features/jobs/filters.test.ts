import { describe, expect, it } from 'vitest'

import {
  canonicalJobQuery,
  jobFiltersForPage,
  jobFiltersForStatus,
  parseJobFilters,
} from './filters'

describe('P5 Job URL filters', () => {
  it('parses canonical status tabs, extraction, search, deadline, pagination and sort', () => {
    expect(
      parseJobFilters({
        status: 'CLOSED',
        extractionStatus: 'NEEDS_MANUAL_INPUT',
        query: '  Hiresemble  ',
        deadlineFrom: '2026-07-01T09:00:00+09:00',
        deadlineTo: '2026-07-31T23:59:59Z',
        page: '2',
        size: '50',
        sort: 'deadlineAt,asc',
      }),
    ).toEqual({
      status: 'CLOSED',
      extractionStatus: 'NEEDS_MANUAL_INPUT',
      query: 'Hiresemble',
      deadlineFrom: '2026-07-01T00:00:00.000Z',
      deadlineTo: '2026-07-31T23:59:59.000Z',
      deadlineWithinDays: undefined,
      page: 2,
      size: 50,
      sort: 'deadlineAt,asc',
    })
  })

  it('canonicalizes invalid values and defaults to the in-progress first tab', () => {
    const filters = parseJobFilters({
      status: 'OPEN',
      extractionStatus: 'DONE',
      query: 'x'.repeat(201),
      deadlineFrom: 'not-an-instant',
      deadlineTo: '2026-01-01T00:00:00Z',
      deadlineWithinDays: '31',
      page: '-1',
      size: '101',
      sort: 'companyName,asc',
    })
    expect(filters).toEqual({
      status: 'IN_PROGRESS',
      extractionStatus: undefined,
      query: undefined,
      deadlineFrom: undefined,
      deadlineTo: '2026-01-01T00:00:00.000Z',
      deadlineWithinDays: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
    expect(canonicalJobQuery(filters)).toEqual({
      deadlineTo: '2026-01-01T00:00:00.000Z',
    })
  })

  it('enforces relative-vs-absolute deadline xor and drops reversed ranges', () => {
    expect(
      parseJobFilters({
        deadlineWithinDays: '7',
        deadlineFrom: '2026-07-01T00:00:00Z',
        deadlineTo: '2026-07-02T00:00:00Z',
      }),
    ).toMatchObject({
      deadlineWithinDays: 7,
      deadlineFrom: undefined,
      deadlineTo: undefined,
    })
    expect(
      parseJobFilters({
        deadlineFrom: '2026-07-03T00:00:00Z',
        deadlineTo: '2026-07-02T00:00:00Z',
      }),
    ).toMatchObject({
      deadlineWithinDays: undefined,
      deadlineFrom: undefined,
      deadlineTo: undefined,
    })
  })

  it('resets page for tab and filter changes while keeping explicit pagination separate', () => {
    const filters = parseJobFilters({ status: 'SUBMITTED', page: '3', size: '50' })
    expect(jobFiltersForStatus(filters, 'CLOSED')).toMatchObject({ status: 'CLOSED', page: 0 })
    expect(jobFiltersForPage(filters, 4)).toMatchObject({ status: 'SUBMITTED', page: 4 })
  })
})
