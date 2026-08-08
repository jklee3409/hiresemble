import { describe, expect, it } from 'vitest'

import { gitHubSourceQueryKeys } from './queryKeys'

describe('GitHub Source query keys', () => {
  it('separates every cache boundary by owner and source', () => {
    const sourceFilters = { page: 0, size: 20, sort: 'updatedAt,desc' as const }
    const repositoryFilters = { page: 1, size: 20, sort: 'repositoryName,asc' as const }
    expect(gitHubSourceQueryKeys.root('user-1')).toEqual(['user', 'user-1', 'githubSources'])
    expect(gitHubSourceQueryKeys.listRoot('user-1')).toEqual([
      'user',
      'user-1',
      'githubSources',
      'list',
    ])
    expect(gitHubSourceQueryKeys.list('user-1', sourceFilters)).toEqual([
      'user',
      'user-1',
      'githubSources',
      'list',
      sourceFilters,
    ])
    expect(gitHubSourceQueryKeys.detail('user-1', 'source-1')).toEqual([
      'user',
      'user-1',
      'githubSources',
      'detail',
      'source-1',
    ])
    expect(gitHubSourceQueryKeys.repositories('user-1', 'source-1', repositoryFilters)).toEqual([
      'user',
      'user-1',
      'githubSources',
      'detail',
      'source-1',
      'repositories',
      repositoryFilters,
    ])
    expect(gitHubSourceQueryKeys.root('user-1')).not.toEqual(gitHubSourceQueryKeys.root('user-2'))
  })
})
