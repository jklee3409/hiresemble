import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { agentRunQueryKeys } from '@/features/agent-runs/queries'
import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import {
  createGitHubSource,
  deleteGitHubSource,
  getGitHubSource,
  listGitHubRepositories,
  listGitHubSources,
  refreshGitHubSource,
  selectGitHubRepositories,
  type GitHubRepositoryListParams,
  type GitHubSourceListParams,
} from '@/shared/api/githubSourceApi'
import type {
  CreateGitHubSourceRequest,
  GitHubRefreshRequest,
  GitHubRepositorySelectionRequest,
} from '@/shared/api/githubSourceContracts'

import { PendingGitHubIdempotencyKeys } from './idempotency'
import { gitHubSourceQueryKeys } from './queryKeys'

export interface GitHubSelectionMutationInput {
  sourceId: string
  request: GitHubRepositorySelectionRequest
}

export interface GitHubRefreshMutationInput {
  sourceId: string
  request: GitHubRefreshRequest
}

export interface GitHubDeleteMutationInput {
  sourceId: string
  version: number
}

export function useGitHubSourceListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<GitHubSourceListParams>,
) {
  return useQuery({
    queryKey: computed(() => gitHubSourceQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listGitHubSources(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useGitHubSourceDetailQuery(
  userId: MaybeRefOrGetter<string>,
  sourceId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => gitHubSourceQueryKeys.detail(toValue(userId), toValue(sourceId))),
    queryFn: () => getGitHubSource(toValue(sourceId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(sourceId) !== ''),
  })
}

export function useGitHubRepositoryListQuery(
  userId: MaybeRefOrGetter<string>,
  sourceId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<GitHubRepositoryListParams>,
  enabled: MaybeRefOrGetter<boolean> = true,
) {
  return useQuery({
    queryKey: computed(() =>
      gitHubSourceQueryKeys.repositories(toValue(userId), toValue(sourceId), toValue(filters)),
    ),
    queryFn: () => listGitHubRepositories(toValue(sourceId), toValue(filters)),
    enabled: computed(() => toValue(enabled) && toValue(userId) !== '' && toValue(sourceId) !== ''),
  })
}

export function useCreateGitHubSourceMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new PendingGitHubIdempotencyKeys()
  return useMutation({
    retry: false,
    mutationFn: async (request: CreateGitHubSourceRequest) => {
      const identity = JSON.stringify(request)
      const key = keys.keyFor('create', identity, 'create')
      const result = await createGitHubSource(request, key)
      keys.complete('create', identity)
      return result
    },
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: gitHubSourceQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
      ])
    },
  })
}

export function useSelectGitHubRepositoriesMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new PendingGitHubIdempotencyKeys()
  return useMutation({
    retry: false,
    mutationFn: async (input: GitHubSelectionMutationInput) => {
      const identity = JSON.stringify({
        repositoryIds: [...input.request.repositoryIds].sort(),
        version: input.request.version,
      })
      const scope = `selection:${input.sourceId}`
      const key = keys.keyFor(scope, identity, 'selection')
      const result = await selectGitHubRepositories(input.sourceId, input.request, key)
      keys.complete(scope, identity)
      return result
    },
    onSuccess: async (_result, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: gitHubSourceQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: gitHubSourceQueryKeys.detail(toValue(userId), input.sourceId),
        }),
        cache.invalidateQueries({
          queryKey: gitHubSourceQueryKeys.repositoryRoot(toValue(userId), input.sourceId),
        }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
      ])
    },
  })
}

export function useRefreshGitHubSourceMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new PendingGitHubIdempotencyKeys()
  return useMutation({
    retry: false,
    mutationFn: async (input: GitHubRefreshMutationInput) => {
      const identity = String(input.request.version)
      const scope = `refresh:${input.sourceId}`
      const key = keys.keyFor(scope, identity, 'refresh')
      const result = await refreshGitHubSource(input.sourceId, input.request, key)
      keys.complete(scope, identity)
      return result
    },
    onSuccess: async (result, input) => {
      cache.setQueryData(
        gitHubSourceQueryKeys.detail(toValue(userId), input.sourceId),
        result.source,
      )
      const invalidations = [
        cache.invalidateQueries({ queryKey: gitHubSourceQueryKeys.listRoot(toValue(userId)) }),
      ]
      if (result.changed) {
        invalidations.push(
          cache.invalidateQueries({
            queryKey: gitHubSourceQueryKeys.repositoryRoot(toValue(userId), input.sourceId),
          }),
          cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        )
      }
      await Promise.all(invalidations)
    },
  })
}

export function useDeleteGitHubSourceMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (input: GitHubDeleteMutationInput) =>
      deleteGitHubSource(input.sourceId, input.version),
    onSuccess: async (_result, input) => {
      const ownerId = toValue(userId)
      closeAgentRunStreamsForResource(ownerId, 'GITHUB_SOURCE', input.sourceId)
      cache.removeQueries({ queryKey: gitHubSourceQueryKeys.detail(ownerId, input.sourceId) })
      await Promise.all([
        cache.invalidateQueries({ queryKey: gitHubSourceQueryKeys.root(ownerId) }),
        cache.invalidateQueries({ queryKey: profileQueryKeys.experiencesRoot(ownerId) }),
        cache.invalidateQueries({ queryKey: profileQueryKeys.evidenceRoot(ownerId) }),
      ])
    },
  })
}
