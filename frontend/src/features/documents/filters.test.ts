import { describe, expect, it } from 'vitest'

import { canonicalDocumentQuery, parseDocumentFilters } from './filters'

describe('document URL filters', () => {
  it('parses allowed filters, pagination and sort', () => {
    expect(
      parseDocumentFilters({
        documentType: 'RESUME',
        parseStatus: 'PARSED',
        evidenceExtractionStatus: 'FAILED',
        page: '2',
        size: '50',
        sort: 'updatedAt,desc',
      }),
    ).toEqual({
      documentType: 'RESUME',
      parseStatus: 'PARSED',
      evidenceExtractionStatus: 'FAILED',
      page: 2,
      size: 50,
      sort: 'updatedAt,desc',
    })
  })
  it('canonicalizes invalid URL values to documented defaults', () => {
    const parsed = parseDocumentFilters({
      documentType: 'HTML',
      parseStatus: 'DONE',
      evidenceExtractionStatus: 'BROKEN',
      page: '-1',
      size: '500',
      sort: 'name,asc',
    })
    expect(parsed).toEqual({
      documentType: undefined,
      parseStatus: undefined,
      evidenceExtractionStatus: undefined,
      page: 0,
      size: 20,
      sort: 'uploadedAt,desc',
    })
    expect(canonicalDocumentQuery(parsed)).toEqual({})
  })
})
