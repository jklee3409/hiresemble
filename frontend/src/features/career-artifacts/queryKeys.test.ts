import { describe, expect, it } from 'vitest'

import { careerArtifactQueryKeys } from './queryKeys'

describe('Career Artifact query keys', () => {
  it('scopes every projection to its user and separates version roots from lists', () => {
    expect(careerArtifactQueryKeys.root('user-1')).toEqual(['user', 'user-1', 'careerArtifacts'])
    expect(careerArtifactQueryKeys.readiness('user-1')).toEqual([
      'user',
      'user-1',
      'careerArtifacts',
      'readiness',
    ])
    expect(careerArtifactQueryKeys.modelCatalog('user-1', 'RESUME')).toEqual([
      'user',
      'user-1',
      'careerArtifacts',
      'aiModels',
      'RESUME',
    ])
    expect(careerArtifactQueryKeys.detail('user-1', 'artifact-1')).not.toEqual(
      careerArtifactQueryKeys.detail('user-2', 'artifact-1'),
    )
    expect(careerArtifactQueryKeys.versionRoot('user-1', 'artifact-1')).toEqual([
      'user',
      'user-1',
      'careerArtifacts',
      'detail',
      'artifact-1',
      'versions',
    ])
  })
})
