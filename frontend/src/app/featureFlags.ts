export interface FeatureFlags {
  githubSourceEnabled: boolean
  githubPrivateEnabled: boolean
  careerArtifactEnabled: boolean
}

export function isEnabledBuildFlag(value: string | undefined): boolean {
  return value === 'true'
}

export function resolveFeatureFlags(
  env: Pick<
    ImportMetaEnv,
    'VITE_GITHUB_SOURCE_ENABLED' | 'VITE_GITHUB_PRIVATE_ENABLED' | 'VITE_CAREER_ARTIFACT_ENABLED'
  >,
): FeatureFlags {
  return {
    githubSourceEnabled: isEnabledBuildFlag(env.VITE_GITHUB_SOURCE_ENABLED),
    githubPrivateEnabled:
      isEnabledBuildFlag(env.VITE_GITHUB_SOURCE_ENABLED) &&
      isEnabledBuildFlag(env.VITE_GITHUB_PRIVATE_ENABLED),
    careerArtifactEnabled: isEnabledBuildFlag(env.VITE_CAREER_ARTIFACT_ENABLED),
  }
}

export const featureFlags = resolveFeatureFlags(import.meta.env)
