import { describe, expect, it } from 'vitest'

import { MAX_DOCUMENT_BYTES, validateManualText, validateUpload } from './validation'

describe('document client validation', () => {
  it('validates file extension, zero bytes and the exact 20MiB boundary', () => {
    expect(
      validateUpload({ file: new File([], 'empty.txt'), documentType: 'RESUME', displayName: '' })
        .file,
    ).toContain('빈')
    expect(
      validateUpload({
        file: new File(['x'], 'payload.svg'),
        documentType: 'OTHER',
        displayName: '',
      }).file,
    ).toContain('PDF')
    const boundary = new File([new Uint8Array(MAX_DOCUMENT_BYTES)], 'boundary.txt')
    expect(validateUpload({ file: boundary, documentType: 'RESUME', displayName: '' })).toEqual({})
    const over = new File([new Uint8Array(MAX_DOCUMENT_BYTES + 1)], 'over.txt')
    expect(validateUpload({ file: over, documentType: 'RESUME', displayName: '' }).file).toContain(
      '20MB',
    )
  })
  it('uses non-whitespace Unicode code points for manual text minimum', () => {
    expect(validateManualText('가'.repeat(99) + ' '.repeat(100))).toContain('100자')
    expect(validateManualText('가'.repeat(100))).toBeNull()
  })
})
