import { describe, expect, it } from 'vitest'

import {
  canonicalJobQuery,
  jobFiltersForPage,
  jobFiltersForStatus,
  parseJobFilters,
} from './filters'

describe('P5 Job URL filters', () => {
  it('parses canonical status tabs, posting period, search, pagination and sort', () => {
    expect(
      parseJobFilters({
        status: 'CLOSED',
        query: '  Hiresemble  ',
        postingYear: '2026',
        postingHalf: 'SECOND_HALF',
        page: '2',
        size: '50',
        sort: 'deadlineAt,asc',
      }),
    ).toEqual({
      status: 'CLOSED',
      query: 'Hiresemble',
      postingYear: 2026,
      postingHalf: 'SECOND_HALF',
      postingStartFrom: undefined,
      page: 2,
      size: 50,
      sort: 'deadlineAt,asc',
    })
  })

  it('canonicalizes invalid values and defaults to the in-progress first tab', () => {
    const filters = parseJobFilters({
      status: 'OPEN',
      query: 'x'.repeat(201),
      postingYear: '1999',
      postingHalf: 'AUTUMN',
      postingStartFrom: 'not-a-date',
      page: '-1',
      size: '101',
      sort: 'companyName,asc',
    })
    expect(filters).toEqual({
      status: 'IN_PROGRESS',
      query: undefined,
      postingYear: undefined,
      postingHalf: undefined,
      postingStartFrom: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
    expect(canonicalJobQuery(filters)).toEqual({})
  })

  it('uses a direct start date instead of a half-year period', () => {
    expect(
      parseJobFilters({
        postingYear: '2026',
        postingHalf: 'SECOND_HALF',
        postingStartFrom: '2025-12-13',
      }),
    ).toMatchObject({
      postingYear: undefined,
      postingHalf: undefined,
      postingStartFrom: '2025-12-13',
    })
    expect(canonicalJobQuery(parseJobFilters({ postingStartFrom: '2025-12-13' }))).toEqual({
      postingStartFrom: '2025-12-13',
    })
  })

  it('drops incomplete period pairs and impossible dates', () => {
    expect(
      parseJobFilters({
        postingYear: '2026',
        postingStartFrom: '2026-02-30',
      }),
    ).toMatchObject({
      postingYear: undefined,
      postingHalf: undefined,
      postingStartFrom: undefined,
    })
  })

  it('resets page for tab and filter changes while keeping explicit pagination separate', () => {
    const filters = parseJobFilters({ status: 'SUBMITTED', page: '3', size: '50' })
    expect(jobFiltersForStatus(filters, 'CLOSED')).toMatchObject({ status: 'CLOSED', page: 0 })
    expect(jobFiltersForPage(filters, 4)).toMatchObject({ status: 'SUBMITTED', page: 4 })
  })
})
