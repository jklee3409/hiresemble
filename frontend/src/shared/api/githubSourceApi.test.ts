import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from './errors'
import * as gitHubApi from './githubSourceApi'
import { apiClient } from './http'

describe('GitHub Source API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps all seven operations with exact method, path, query, body and keys', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(page([source()]))
      .mockResolvedValueOnce(detail())
      .mockResolvedValueOnce(repositoryPage())
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce(accepted())
      .mockResolvedValueOnce({ changed: false, source: detail('READY', false), run: null })
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue(accepted())
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)

    await gitHubApi.createGitHubSource(
      { url: 'https://github.com/openai', participationConfirmed: true },
      'create-key',
    )
    await gitHubApi.listGitHubSources({
      status: 'WAITING_USER',
      sourceKind: 'ACCOUNT',
      page: 1,
      size: 10,
      sort: 'createdAt,desc',
    })
    await gitHubApi.getGitHubSource(uuid(1))
    await gitHubApi.listGitHubRepositories(uuid(1), {
      query: 'sdk',
      selected: false,
      page: 2,
      size: 20,
      sort: 'repositoryName,asc',
    })
    await gitHubApi.selectGitHubRepositories(
      uuid(1),
      { repositoryIds: [uuid(3)], version: 4 },
      'selection-key',
    )
    await gitHubApi.refreshGitHubSource(uuid(1), { version: 5 }, 'refresh-key')
    await gitHubApi.deleteGitHubSource(uuid(1), 6)

    expect(post).toHaveBeenNthCalledWith(
      1,
      '/github-sources',
      { url: 'https://github.com/openai', participationConfirmed: true },
      { headers: { 'Idempotency-Key': 'create-key' } },
    )
    expect(get).toHaveBeenNthCalledWith(1, '/github-sources', {
      params: new URLSearchParams(
        'status=WAITING_USER&sourceKind=ACCOUNT&page=1&size=10&sort=createdAt%2Cdesc',
      ),
    })
    expect(get).toHaveBeenNthCalledWith(2, `/github-sources/${uuid(1)}`)
    expect(get).toHaveBeenNthCalledWith(3, `/github-sources/${uuid(1)}/repositories`, {
      params: new URLSearchParams(
        'query=sdk&selected=false&page=2&size=20&sort=repositoryName%2Casc',
      ),
    })
    expect(put).toHaveBeenCalledWith(
      `/github-sources/${uuid(1)}/repository-selection`,
      { repositoryIds: [uuid(3)], version: 4 },
      { headers: { 'Idempotency-Key': 'selection-key' } },
    )
    expect(post).toHaveBeenNthCalledWith(
      2,
      `/github-sources/${uuid(1)}/refresh`,
      { version: 5 },
      { headers: { 'Idempotency-Key': 'refresh-key' } },
    )
    expect(remove).toHaveBeenCalledWith(`/github-sources/${uuid(1)}`, {
      params: { version: 6 },
    })
  })

  it('parses both unchanged 200 and changed 202 refresh body contracts', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({ changed: false, source: detail('READY', false), run: null })
      .mockResolvedValueOnce({
        changed: true,
        source: detail('QUEUED', false),
        run: accepted(),
      })

    await expect(
      gitHubApi.refreshGitHubSource(uuid(1), { version: 1 }, 'key-1'),
    ).resolves.toMatchObject({ changed: false, run: null })
    await expect(
      gitHubApi.refreshGitHubSource(uuid(1), { version: 2 }, 'key-2'),
    ).resolves.toMatchObject({
      changed: true,
      run: { resourceType: 'GITHUB_SOURCE', resourceId: uuid(1) },
    })
    expect(post).toHaveBeenCalledTimes(2)
  })

  it('rejects malformed responses and mismatched Run resources without retrying', async () => {
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue({
      ...accepted(),
      resourceId: uuid(9),
    })
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ items: [] })

    await expect(
      gitHubApi.selectGitHubRepositories(uuid(1), { repositoryIds: [uuid(3)], version: 1 }, 'key'),
    ).rejects.toBeInstanceOf(ApiClientError)
    await expect(gitHubApi.listGitHubSources()).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
    expect(put).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('encodes source IDs before placing them in a URL', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue(detail())
    await gitHubApi.getGitHubSource('source/../other')
    expect(get).toHaveBeenCalledWith('/github-sources/source%2F..%2Fother')
  })
})

const now = '2026-08-08T00:00:00Z'

function source(status = 'WAITING_USER') {
  return {
    id: uuid(1),
    sourceKind: 'ACCOUNT',
    accountType: 'USER',
    canonicalUrl: 'https://github.com/openai',
    ownerLogin: 'openai',
    repositoryName: null,
    status,
    discoveredRepositoryCount: 2,
    selectedRepositoryCount: 0,
    repositoryDiscoveryTruncated: false,
    newExperienceCount: 0,
    corroboratedExperienceCount: 0,
    reviewRequiredCount: 0,
    rejectedCandidateCount: 0,
    snapshotIncomplete: false,
    latestAgentRunId: uuid(2),
    lastSuccessfulSyncAt: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

function detail(status = 'WAITING_USER', withAction = true) {
  return {
    source: source(status),
    requiredUserAction: withAction
      ? {
          type: 'SELECT_GITHUB_REPOSITORIES',
          resource: {
            resourceType: 'GITHUB_SOURCE',
            resourceId: uuid(1),
            displayLabel: 'https://github.com/openai',
          },
          route: '/profile/github',
          message: '분석할 저장소를 선택해 주세요.',
        }
      : null,
  }
}

function accepted() {
  return {
    agentRunId: uuid(2),
    status: 'QUEUED',
    resourceType: 'GITHUB_SOURCE',
    resourceId: uuid(1),
    replayed: false,
  }
}

function repositoryPage() {
  return page([
    {
      id: uuid(3),
      ownerLogin: 'openai',
      repositoryName: 'sdk',
      canonicalUrl: 'https://github.com/openai/sdk',
      description: null,
      defaultBranch: 'main',
      fork: false,
      archived: false,
      selected: false,
      pushedAt: null,
    },
  ])
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
