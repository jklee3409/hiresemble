import { describe, expect, it } from 'vitest'

import { documentQueryKeys } from './queries'

describe('document query keys', () => {
  it('uses the exact user-scoped document key contract', () => {
    const filters = { page: 0, size: 20, sort: 'uploadedAt,desc' as const }
    expect(documentQueryKeys.list('user-1', filters)).toEqual([
      'user',
      'user-1',
      'documents',
      filters,
    ])
    expect(documentQueryKeys.detail('user-1', 'document-1')).toEqual([
      'user',
      'user-1',
      'document',
      'document-1',
    ])
    expect(documentQueryKeys.text('user-1', 'document-1')).toEqual([
      'user',
      'user-1',
      'documentText',
      'document-1',
    ])
    expect(documentQueryKeys.list('user-1', filters)).not.toEqual(
      documentQueryKeys.list('user-2', filters),
    )
  })
})
