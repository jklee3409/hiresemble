import { describe, expect, it } from 'vitest'

import { isEnabledBuildFlag, resolveFeatureFlags } from './featureFlags'

describe('feature flags', () => {
  it('enables GitHub Source only for the exact lowercase true value', () => {
    expect(isEnabledBuildFlag('true')).toBe(true)
    expect(isEnabledBuildFlag(undefined)).toBe(false)
    expect(isEnabledBuildFlag('TRUE')).toBe(false)
    expect(isEnabledBuildFlag('1')).toBe(false)
    expect(isEnabledBuildFlag(' true ')).toBe(false)
  })

  it('defaults Gate 2 to disabled when the build variable is absent', () => {
    expect(resolveFeatureFlags({})).toEqual({ githubSourceEnabled: false })
    expect(resolveFeatureFlags({ VITE_GITHUB_SOURCE_ENABLED: 'true' })).toEqual({
      githubSourceEnabled: true,
    })
  })
})
