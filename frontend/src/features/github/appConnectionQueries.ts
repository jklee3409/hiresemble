import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import {
  disconnectGitHubAppConnection,
  getGitHubAppCapability,
  listGitHubAppConnections,
  refreshGitHubAppConnection,
} from '@/shared/api/githubAppConnectionApi'

export const gitHubAppConnectionQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'githubAppConnections'] as const
  },
  capability(userId: string) {
    return [...this.root(userId), 'capability'] as const
  },
  list(userId: string) {
    return [...this.root(userId), 'list'] as const
  },
}

export function useGitHubAppCapabilityQuery(
  userId: MaybeRefOrGetter<string>,
  enabled: MaybeRefOrGetter<boolean>,
) {
  return useQuery({
    queryKey: computed(() => gitHubAppConnectionQueryKeys.capability(toValue(userId))),
    queryFn: ({ signal }) => getGitHubAppCapability(signal),
    enabled: computed(() => toValue(enabled) && toValue(userId) !== ''),
    retry: false,
  })
}

export function useGitHubAppConnectionListQuery(
  userId: MaybeRefOrGetter<string>,
  enabled: MaybeRefOrGetter<boolean>,
) {
  return useQuery({
    queryKey: computed(() => gitHubAppConnectionQueryKeys.list(toValue(userId))),
    queryFn: ({ signal }) => listGitHubAppConnections(signal),
    enabled: computed(() => toValue(enabled) && toValue(userId) !== ''),
    retry: false,
  })
}

export function useRefreshGitHubAppConnectionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (input: { connectionId: string; version: number }) =>
      refreshGitHubAppConnection(input.connectionId, { version: input.version }),
    onSuccess: async () => {
      await cache.invalidateQueries({
        queryKey: gitHubAppConnectionQueryKeys.root(toValue(userId)),
      })
    },
  })
}

export function useDisconnectGitHubAppConnectionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (input: { connectionId: string; version: number }) =>
      disconnectGitHubAppConnection(input.connectionId, input.version),
    onSuccess: async () => {
      await cache.invalidateQueries({
        queryKey: gitHubAppConnectionQueryKeys.root(toValue(userId)),
      })
    },
  })
}
