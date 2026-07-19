import { describe, expect, it } from 'vitest'

import { isVersionConflict, reapplySelectedFields } from './conflict'

describe('profile version conflict recovery', () => {
  it('starts with the latest snapshot and reapplies only selected draft fields', () => {
    expect(
      reapplySelectedFields(
        { legalName: 'Server', introduction: 'Latest', version: 3 },
        { legalName: 'Draft', introduction: 'Unsaved', version: 2 },
        ['introduction'],
      ),
    ).toEqual({ legalName: 'Server', introduction: 'Unsaved', version: 3 })
  })

  it('recognizes only the approved version conflict code', () => {
    expect(isVersionConflict({ status: 409, code: 'RESOURCE_VERSION_CONFLICT' })).toBe(true)
    expect(isVersionConflict({ status: 409, code: 'RESOURCE_STATE_CONFLICT' })).toBe(false)
  })
})
