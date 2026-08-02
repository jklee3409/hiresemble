import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import type { AgentRunDetailDto } from '@/shared/api/agentRunContracts'
import {
  cancelAgentRun,
  createRetryIdempotencyKey,
  deleteAgentRun,
  deleteAgentRuns,
  getAgentRun,
  listAgentRuns,
  retryAgentRun,
  type AgentRunListParams,
} from '@/shared/api/agentRunApi'

export const agentRunQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'agentRuns'] as const
  },
  list(userId: string, filters: AgentRunListParams) {
    return [...this.root(userId), 'list', filters] as const
  },
  detail(userId: string, agentRunId: string) {
    return [...this.root(userId), 'detail', agentRunId] as const
  },
  relatedResource(userId: string, resourceType: string, resourceId: string) {
    return ['user', userId, 'resource', resourceType, resourceId] as const
  },
}

export const ACTIVE_AGENT_RUN_STATUSES = ['QUEUED', 'RUNNING', 'WAITING_USER'] as const

export function useAgentRunListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<AgentRunListParams>,
) {
  return useQuery({
    queryKey: computed(() => agentRunQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listAgentRuns(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useActiveAgentRunsQuery(userId: MaybeRefOrGetter<string>) {
  const filters = computed<AgentRunListParams>(() => ({
    status: [...ACTIVE_AGENT_RUN_STATUSES],
    page: 0,
    size: 100,
    sort: 'updatedAt,desc',
  }))
  return useAgentRunListQuery(userId, filters)
}

export function useAgentRunDetailQuery(
  userId: MaybeRefOrGetter<string>,
  agentRunId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => agentRunQueryKeys.detail(toValue(userId), toValue(agentRunId))),
    queryFn: () => getAgentRun(toValue(agentRunId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(agentRunId) !== ''),
  })
}

export function useRetryAgentRunMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const retryKeys = new Map<string, string>()
  return useMutation({
    mutationFn: async (predecessorRunId: string) => {
      const key = retryKeys.get(predecessorRunId) ?? createRetryIdempotencyKey()
      retryKeys.set(predecessorRunId, key)
      return retryAgentRun(predecessorRunId, key)
    },
    onSuccess: async (accepted, predecessorRunId) => {
      retryKeys.delete(predecessorRunId)
      await cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) })
      return accepted
    },
  })
}

export function useCancelAgentRunMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: ({ agentRunId, stateVersion }: { agentRunId: string; stateVersion: number }) =>
      cancelAgentRun(agentRunId, stateVersion),
    onSuccess: (detail) => applyAgentRunDetail(cache, toValue(userId), detail),
  })
}

export function useDeleteAgentRunMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (agentRunId: string) => deleteAgentRun(agentRunId),
    onSuccess: async (_result, agentRunId) => {
      cache.removeQueries({
        queryKey: agentRunQueryKeys.detail(toValue(userId), agentRunId),
        exact: true,
      })
      await cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) })
    },
  })
}

export function useDeleteSelectedAgentRunsMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (agentRunIds: string[]) => deleteAgentRuns(agentRunIds),
    onSuccess: async (_result, agentRunIds) => {
      for (const agentRunId of agentRunIds) {
        cache.removeQueries({
          queryKey: agentRunQueryKeys.detail(toValue(userId), agentRunId),
          exact: true,
        })
      }
      await cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) })
    },
  })
}

export function applyAgentRunDetail(
  cache: Pick<QueryClient, 'setQueryData'>,
  userId: string,
  detail: AgentRunDetailDto,
): void {
  cache.setQueryData(agentRunQueryKeys.detail(userId, detail.id), detail)
}
