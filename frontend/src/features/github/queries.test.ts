import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from '@/shared/api/errors'

import {
  useCreateGitHubSourceMutation,
  useDeleteGitHubSourceMutation,
  useRefreshGitHubSourceMutation,
  useSelectGitHubRepositoriesMutation,
} from './queries'

const apiMocks = vi.hoisted(() => ({
  createGitHubSource: vi.fn(),
  selectGitHubRepositories: vi.fn(),
  refreshGitHubSource: vi.fn(),
  deleteGitHubSource: vi.fn(),
  createGitHubIdempotencyKey: vi.fn(),
  closeStreams: vi.fn(),
}))

vi.mock('@/shared/api/githubSourceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/githubSourceApi')>()),
  createGitHubSource: apiMocks.createGitHubSource,
  selectGitHubRepositories: apiMocks.selectGitHubRepositories,
  refreshGitHubSource: apiMocks.refreshGitHubSource,
  deleteGitHubSource: apiMocks.deleteGitHubSource,
  createGitHubIdempotencyKey: apiMocks.createGitHubIdempotencyKey,
}))

vi.mock('@/features/agent-runs/stream', () => ({
  closeAgentRunStreamsForResource: apiMocks.closeStreams,
}))

describe('GitHub Source mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    let key = 0
    apiMocks.createGitHubIdempotencyKey.mockImplementation(
      (action: string) => `${action}-key-${++key}`,
    )
  })

  it('reuses the same create key after a network failure and invalidates source/run roots on success', async () => {
    apiMocks.createGitHubSource
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(accepted())
    const { hook, queryClient } = mountHook(useCreateGitHubSourceMutation)
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const request = { url: 'https://github.com/openai', participationConfirmed: true as const }

    await expect(hook.mutateAsync(request)).rejects.toThrow('network')
    await expect(hook.mutateAsync(request)).resolves.toMatchObject({
      resourceType: 'GITHUB_SOURCE',
    })
    expect(apiMocks.createGitHubSource).toHaveBeenNthCalledWith(1, request, 'create-key-1')
    expect(apiMocks.createGitHubSource).toHaveBeenNthCalledWith(2, request, 'create-key-1')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user', 'user-1', 'githubSources'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user', 'user-1', 'agentRuns'] })
  })

  it('never auto-retries a 409 selection and rotates the key when the version changes', async () => {
    apiMocks.selectGitHubRepositories
      .mockRejectedValueOnce(
        new ApiClientError({
          status: 409,
          code: 'RESOURCE_VERSION_CONFLICT',
          message: 'conflict',
        }),
      )
      .mockResolvedValueOnce(accepted())
    const { hook } = mountHook(useSelectGitHubRepositoriesMutation)
    const input = {
      sourceId: uuid(1),
      request: { repositoryIds: [uuid(3)], version: 1 },
    }
    await expect(hook.mutateAsync(input)).rejects.toMatchObject({ status: 409 })
    expect(apiMocks.selectGitHubRepositories).toHaveBeenCalledTimes(1)
    await hook.mutateAsync({ ...input, request: { ...input.request, version: 2 } })
    expect(apiMocks.selectGitHubRepositories.mock.calls[0]?.[2]).toBe('selection-key-1')
    expect(apiMocks.selectGitHubRepositories.mock.calls[1]?.[2]).toBe('selection-key-2')
  })

  it('uses narrow unchanged refresh invalidation and expands changed refresh invalidation', async () => {
    apiMocks.refreshGitHubSource
      .mockResolvedValueOnce({ changed: false, source: detail('READY'), run: null })
      .mockResolvedValueOnce({ changed: true, source: detail('QUEUED'), run: accepted() })
    const { hook, queryClient } = mountHook(useRefreshGitHubSourceMutation)
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    await hook.mutateAsync({ sourceId: uuid(1), request: { version: 1 } })
    const callsAfterUnchanged = invalidate.mock.calls.length
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['user', 'user-1', 'githubSources', 'list'],
    })
    expect(
      invalidate.mock.calls.some((call) => {
        const filters = call[0]
        return (
          filters !== undefined &&
          typeof filters !== 'function' &&
          JSON.stringify(filters.queryKey).includes('repositories')
        )
      }),
    ).toBe(false)

    await hook.mutateAsync({ sourceId: uuid(1), request: { version: 2 } })
    expect(invalidate.mock.calls.length).toBeGreaterThan(callsAfterUnchanged)
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['user', 'user-1', 'githubSources', 'detail', uuid(1), 'repositories'],
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user', 'user-1', 'agentRuns'] })
  })

  it('removes deleted source caches, closes its stream, and invalidates experience provenance', async () => {
    apiMocks.deleteGitHubSource.mockResolvedValue(undefined)
    const { hook, queryClient } = mountHook(useDeleteGitHubSourceMutation)
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const remove = vi.spyOn(queryClient, 'removeQueries')
    await hook.mutateAsync({ sourceId: uuid(1), version: 4 })

    expect(apiMocks.deleteGitHubSource).toHaveBeenCalledWith(uuid(1), 4)
    expect(apiMocks.closeStreams).toHaveBeenCalledWith('user-1', 'GITHUB_SOURCE', uuid(1))
    expect(remove).toHaveBeenCalledWith({
      queryKey: ['user', 'user-1', 'githubSources', 'detail', uuid(1)],
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user', 'user-1', 'experiences'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user', 'user-1', 'evidence'] })
  })
})

function mountHook<T>(factory: (userId: Ref<string>) => T) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const userId = ref('user-1')
  let hook!: T
  const Harness = defineComponent({
    setup() {
      hook = factory(userId)
      return () => h('div')
    },
  })
  mount(Harness, { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  void flushPromises()
  return { hook, queryClient }
}

const now = '2026-08-08T00:00:00Z'

function accepted() {
  return {
    agentRunId: uuid(2),
    status: 'QUEUED',
    resourceType: 'GITHUB_SOURCE',
    resourceId: uuid(1),
    replayed: false,
  }
}

function detail(status: 'READY' | 'QUEUED') {
  return {
    source: {
      id: uuid(1),
      sourceKind: 'REPOSITORY',
      accountType: null,
      canonicalUrl: 'https://github.com/openai/hiresemble',
      ownerLogin: 'openai',
      repositoryName: 'hiresemble',
      status,
      discoveredRepositoryCount: 1,
      selectedRepositoryCount: 1,
      repositoryDiscoveryTruncated: false,
      newExperienceCount: 1,
      corroboratedExperienceCount: 0,
      reviewRequiredCount: 0,
      rejectedCandidateCount: 0,
      snapshotIncomplete: false,
      latestAgentRunId: uuid(2),
      lastSuccessfulSyncAt: now,
      version: 2,
      createdAt: now,
      updatedAt: now,
    },
    requiredUserAction: null,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
