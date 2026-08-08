import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { agentRunQueryKeys } from '@/features/agent-runs/queries'
import {
  archiveCareerArtifact,
  createCareerArtifact,
  deleteCareerArtifact,
  generateCareerArtifactVersion,
  getCareerArtifact,
  getCareerArtifactReadiness,
  listCareerArtifactAiModels,
  listCareerArtifacts,
  listCareerArtifactVersions,
  unarchiveCareerArtifact,
  type CareerArtifactListParams,
  type CareerArtifactVersionListParams,
} from '@/shared/api/careerArtifactApi'
import type {
  CareerArtifactType,
  CreateCareerArtifactRequest,
  GenerateCareerArtifactRequest,
} from '@/shared/api/careerArtifactContracts'

import { careerArtifactQueryKeys } from './queryKeys'

export function useCareerArtifactReadinessQuery(
  userId: MaybeRefOrGetter<string>,
  enabled: MaybeRefOrGetter<boolean> = true,
) {
  return useQuery({
    queryKey: computed(() => careerArtifactQueryKeys.readiness(toValue(userId))),
    queryFn: getCareerArtifactReadiness,
    enabled: computed(() => toValue(enabled) && toValue(userId) !== ''),
    retry: 1,
  })
}

export function useCareerArtifactModelCatalogQuery(
  userId: MaybeRefOrGetter<string>,
  artifactType: MaybeRefOrGetter<CareerArtifactType | null>,
  enabled: MaybeRefOrGetter<boolean> = true,
) {
  return useQuery({
    queryKey: computed(() =>
      careerArtifactQueryKeys.modelCatalog(toValue(userId), toValue(artifactType) ?? 'RESUME'),
    ),
    queryFn: () => listCareerArtifactAiModels(toValue(artifactType) ?? 'RESUME'),
    enabled: computed(
      () => toValue(enabled) && toValue(userId) !== '' && toValue(artifactType) !== null,
    ),
    retry: 1,
  })
}

export function useCareerArtifactListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<CareerArtifactListParams>,
) {
  return useQuery({
    queryKey: computed(() => careerArtifactQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listCareerArtifacts(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useCareerArtifactDetailQuery(
  userId: MaybeRefOrGetter<string>,
  artifactId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => careerArtifactQueryKeys.detail(toValue(userId), toValue(artifactId))),
    queryFn: () => getCareerArtifact(toValue(artifactId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(artifactId) !== ''),
    retry: false,
  })
}

export function useCareerArtifactVersionsQuery(
  userId: MaybeRefOrGetter<string>,
  artifactId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<CareerArtifactVersionListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      careerArtifactQueryKeys.versions(toValue(userId), toValue(artifactId), toValue(filters)),
    ),
    queryFn: () => listCareerArtifactVersions(toValue(artifactId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(artifactId) !== ''),
  })
}

export function useCreateCareerArtifactMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (input: { request: CreateCareerArtifactRequest; idempotencyKey: string }) =>
      createCareerArtifact(input.request, input.idempotencyKey),
    onSuccess: async (accepted) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'CAREER_ARTIFACT',
            accepted.resourceId!,
          ),
        }),
      ])
    },
  })
}

export function useGenerateCareerArtifactMutation(
  userId: MaybeRefOrGetter<string>,
  artifactId: MaybeRefOrGetter<string>,
) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (input: { request: GenerateCareerArtifactRequest; idempotencyKey: string }) =>
      generateCareerArtifactVersion(toValue(artifactId), input.request, input.idempotencyKey),
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
      ])
    },
  })
}

export function useCareerArtifactLifecycleMutation(
  userId: MaybeRefOrGetter<string>,
  artifactId: MaybeRefOrGetter<string>,
) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    onMutate: async () => {
      await cache.cancelQueries({
        queryKey: careerArtifactQueryKeys.detail(toValue(userId), toValue(artifactId)),
      })
    },
    mutationFn: (input: { action: 'archive' | 'unarchive'; version: number }) =>
      input.action === 'archive'
        ? archiveCareerArtifact(toValue(artifactId), input.version)
        : unarchiveCareerArtifact(toValue(artifactId), input.version),
    onSuccess: async (detail) => {
      cache.setQueryData(
        careerArtifactQueryKeys.detail(toValue(userId), toValue(artifactId)),
        detail,
      )
      await Promise.all([
        cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.listRoot(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.readiness(toValue(userId)) }),
      ])
    },
  })
}

export function useDeleteCareerArtifactMutation(
  userId: MaybeRefOrGetter<string>,
  artifactId: MaybeRefOrGetter<string>,
) {
  const cache = useQueryClient()
  return useMutation({
    retry: false,
    mutationFn: (version: number) => deleteCareerArtifact(toValue(artifactId), version),
    onSuccess: async () => {
      cache.removeQueries({
        queryKey: careerArtifactQueryKeys.detail(toValue(userId), toValue(artifactId)),
      })
      cache.removeQueries({
        queryKey: careerArtifactQueryKeys.versionRoot(toValue(userId), toValue(artifactId)),
      })
      await Promise.all([
        cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
      ])
    },
  })
}
