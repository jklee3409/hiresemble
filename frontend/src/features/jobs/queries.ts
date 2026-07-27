import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { agentRunQueryKeys, useAgentRunListQuery } from '@/features/agent-runs/queries'
import {
  createJob,
  createJobIdempotencyKey,
  deleteJob,
  getJob,
  listJobs,
  retryJobExtraction,
  updateJob,
  updateJobStatus,
  type JobListParams,
} from '@/shared/api/jobApi'
import type {
  CreateJobRequest,
  JobDetailDto,
  JobStatus,
  UpdateJobRequest,
} from '@/shared/api/jobContracts'

export const jobQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'jobs'] as const
  },
  list(userId: string, filters: JobListParams) {
    return ['user', userId, 'jobs', filters] as const
  },
  detail(userId: string, jobId: string) {
    return ['user', userId, 'job', jobId] as const
  },
}

export function useJobListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<JobListParams>,
) {
  return useQuery({
    queryKey: computed(() => jobQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listJobs(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useJobDetailQuery(
  userId: MaybeRefOrGetter<string>,
  jobId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => jobQueryKeys.detail(toValue(userId), toValue(jobId))),
    queryFn: () => getJob(toValue(jobId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(jobId) !== ''),
  })
}

export function useLatestJobRunQuery(
  userId: MaybeRefOrGetter<string>,
  jobId: MaybeRefOrGetter<string>,
) {
  const filters = computed(() => ({
    resourceType: 'JOB',
    resourceId: toValue(jobId),
    page: 0,
    size: 1,
    sort: 'queuedAt,desc' as const,
  }))
  return useAgentRunListQuery(userId, filters)
}

export function useCreateJobMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { request: CreateJobRequest; idempotencyKey: string }) =>
      createJob(input.request, input.idempotencyKey),
    onSuccess: () => cache.invalidateQueries({ queryKey: jobQueryKeys.root(toValue(userId)) }),
  })
}

export function useUpdateJobMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { jobId: string; request: UpdateJobRequest }) =>
      updateJob(input.jobId, input.request),
    onSuccess: (detail) => applyJobDetailAndInvalidate(cache, toValue(userId), detail),
  })
}

export function useUpdateJobStatusMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { jobId: string; status: JobStatus; version: number }) =>
      updateJobStatus(input.jobId, { status: input.status, version: input.version }),
    onSuccess: (detail) => applyJobDetailAndInvalidate(cache, toValue(userId), detail),
  })
}

export function useRetryJobExtractionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const retryKeys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: { jobId: string; version: number }) => {
      const identity = `${input.jobId}/${input.version}`
      const key = retryKeys.get(identity) ?? createJobIdempotencyKey('retry-extraction')
      retryKeys.set(identity, key)
      return retryJobExtraction(input.jobId, { version: input.version }, key)
    },
    onSuccess: async (_accepted, input) => {
      retryKeys.delete(`${input.jobId}/${input.version}`)
      await invalidateJobAndRunQueries(cache, toValue(userId), input.jobId)
    },
  })
}

export function useDeleteJobMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { jobId: string; version: number }) =>
      deleteJob(input.jobId, input.version),
    onSuccess: async (_value, input) => {
      await finalizeJobDeletion(cache, toValue(userId), input.jobId)
    },
  })
}

export async function applyJobDetailAndInvalidate(
  cache: QueryClient,
  userId: string,
  detail: JobDetailDto,
): Promise<void> {
  cache.setQueryData(jobQueryKeys.detail(userId, detail.id), detail)
  await invalidateJobAndRunQueries(cache, userId, detail.id)
}

export async function invalidateJobAndRunQueries(
  cache: Pick<QueryClient, 'invalidateQueries'>,
  userId: string,
  jobId: string,
): Promise<void> {
  await Promise.all([
    cache.invalidateQueries({ queryKey: jobQueryKeys.root(userId) }),
    cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(userId) }),
    cache.invalidateQueries({
      queryKey: agentRunQueryKeys.relatedResource(userId, 'JOB', jobId),
    }),
  ])
}

export async function finalizeJobDeletion(
  cache: Pick<QueryClient, 'removeQueries' | 'invalidateQueries'>,
  userId: string,
  jobId: string,
): Promise<void> {
  cache.removeQueries({ queryKey: jobQueryKeys.detail(userId, jobId) })
  await invalidateJobAndRunQueries(cache, userId, jobId)
}
