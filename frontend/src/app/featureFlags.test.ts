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

  it('defaults optional frontend gates to disabled when build variables are absent', () => {
    expect(resolveFeatureFlags({})).toEqual({
      githubSourceEnabled: false,
      careerArtifactEnabled: false,
    })
    expect(resolveFeatureFlags({ VITE_GITHUB_SOURCE_ENABLED: 'true' })).toEqual({
      githubSourceEnabled: true,
      careerArtifactEnabled: false,
    })
    expect(resolveFeatureFlags({ VITE_CAREER_ARTIFACT_ENABLED: 'true' })).toEqual({
      githubSourceEnabled: false,
      careerArtifactEnabled: true,
    })
  })

  it('enables Career Artifact only for the exact lowercase true value', () => {
    expect(
      resolveFeatureFlags({
        VITE_GITHUB_SOURCE_ENABLED: 'false',
        VITE_CAREER_ARTIFACT_ENABLED: 'true',
      }).careerArtifactEnabled,
    ).toBe(true)
    expect(
      resolveFeatureFlags({ VITE_CAREER_ARTIFACT_ENABLED: 'TRUE' }).careerArtifactEnabled,
    ).toBe(false)
    expect(
      resolveFeatureFlags({ VITE_CAREER_ARTIFACT_ENABLED: ' true ' }).careerArtifactEnabled,
    ).toBe(false)
  })
})
