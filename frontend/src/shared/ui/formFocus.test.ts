import { afterEach, describe, expect, it } from 'vitest'

import { focusFirstInvalidControl } from './formFocus'

describe('focusFirstInvalidControl', () => {
  afterEach(() => document.body.replaceChildren())

  it('focuses the first explicitly invalid control', () => {
    document.body.innerHTML =
      '<input id="first" aria-invalid="true"><input id="second" aria-invalid="true">'
    focusFirstInvalidControl()
    expect(document.activeElement?.id).toBe('first')
  })

  it('falls back to the control linked by the first visible error', () => {
    document.body.innerHTML =
      '<label><span class="field-error">확인해 주세요</span><input id="fallback"></label>'
    focusFirstInvalidControl()
    expect(document.activeElement?.id).toBe('fallback')
  })
})
