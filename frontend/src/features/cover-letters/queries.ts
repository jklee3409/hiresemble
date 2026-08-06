import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { agentRunQueryKeys, useAgentRunListQuery } from '@/features/agent-runs/queries'
import { jobQueryKeys } from '@/features/jobs/queries'
import {
  archiveCoverLetter,
  createCoverLetter,
  createCoverLetterIdempotencyKey,
  createCoverLetterQuestion,
  deleteCoverLetterQuestion,
  finalizeCoverLetter,
  generateCoverLetter,
  getCoverLetter,
  listAnswerVerifications,
  listAnswerVersions,
  listCoverLetterAiModels,
  listCoverLetters,
  reorderCoverLetterQuestions,
  restoreAnswerVersion,
  saveAnswerVersion,
  unarchiveCoverLetter,
  updateCoverLetter,
  updateCoverLetterQuestion,
  verifyAnswerVersion,
  type AnswerVersionListParams,
  type CoverLetterListParams,
  type VerificationListParams,
} from '@/shared/api/coverLetterApi'
import type {
  CoverLetterDetailDto,
  CreateQuestionRequest,
  FinalizeCoverLetterRequest,
  GenerateCoverLetterRequest,
  ReorderQuestionsRequest,
  RestoreAnswerVersionRequest,
  SaveAnswerVersionRequest,
  UpdateCoverLetterRequest,
  UpdateQuestionRequest,
  VerifyAnswerVersionRequest,
} from '@/shared/api/coverLetterContracts'
import type { AgentRunListParams } from '@/shared/api/agentRunApi'

export const coverLetterQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'coverLetters'] as const
  },
  aiModels() {
    return ['coverLetters', 'aiModels'] as const
  },
  list(userId: string, filters: CoverLetterListParams) {
    return ['user', userId, 'coverLetters', filters] as const
  },
  detail(userId: string, coverLetterId: string) {
    return ['user', userId, 'coverLetter', coverLetterId] as const
  },
  versionsRoot(userId: string, questionId: string) {
    return ['user', userId, 'coverLetterQuestion', questionId, 'versions'] as const
  },
  versions(userId: string, questionId: string, filters: AnswerVersionListParams) {
    return [...this.versionsRoot(userId, questionId), filters] as const
  },
  verificationsRoot(userId: string, versionId: string) {
    return ['user', userId, 'coverLetterAnswerVersion', versionId, 'verifications'] as const
  },
  verifications(userId: string, versionId: string, filters: VerificationListParams) {
    return [...this.verificationsRoot(userId, versionId), filters] as const
  },
}

export function useCoverLetterListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<CoverLetterListParams>,
) {
  return useQuery({
    queryKey: computed(() => coverLetterQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listCoverLetters(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useCoverLetterDetailQuery(
  userId: MaybeRefOrGetter<string>,
  coverLetterId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => coverLetterQueryKeys.detail(toValue(userId), toValue(coverLetterId))),
    queryFn: () => getCoverLetter(toValue(coverLetterId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(coverLetterId) !== ''),
  })
}

export function useCoverLetterAiModelsQuery(userId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: coverLetterQueryKeys.aiModels(),
    queryFn: listCoverLetterAiModels,
    enabled: computed(() => toValue(userId) !== ''),
    staleTime: 60 * 60 * 1000,
  })
}

export function useAnswerVersionListQuery(
  userId: MaybeRefOrGetter<string>,
  questionId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<AnswerVersionListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      coverLetterQueryKeys.versions(toValue(userId), toValue(questionId), toValue(filters)),
    ),
    queryFn: () => listAnswerVersions(toValue(questionId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(questionId) !== ''),
  })
}

export function useVerificationListQuery(
  userId: MaybeRefOrGetter<string>,
  versionId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<VerificationListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      coverLetterQueryKeys.verifications(toValue(userId), toValue(versionId), toValue(filters)),
    ),
    queryFn: () => listAnswerVerifications(toValue(versionId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(versionId) !== ''),
  })
}

export function useLatestCoverLetterRunQuery(
  userId: MaybeRefOrGetter<string>,
  coverLetterId: MaybeRefOrGetter<string>,
) {
  const filters = computed<AgentRunListParams>(() => ({
    workflowType: ['COVER_LETTER_GENERATION', 'COVER_LETTER_VERIFICATION'],
    resourceType: 'COVER_LETTER',
    resourceId: toValue(coverLetterId),
    page: 0,
    size: 1,
    sort: 'queuedAt,desc',
  }))
  return useAgentRunListQuery(userId, filters)
}

export function useCreateCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: { jobId: string; title: string }) => {
      const identity = `${input.jobId}/${input.title.trim()}`
      const key = keys.get(identity) ?? createCoverLetterIdempotencyKey('create')
      keys.set(identity, key)
      return createCoverLetter(input.jobId, { title: input.title }, key).then((detail) => {
        keys.delete(identity)
        return detail
      })
    },
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export function useUpdateCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; request: UpdateCoverLetterRequest }) =>
      updateCoverLetter(input.coverLetterId, input.request),
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export function useCreateQuestionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; request: CreateQuestionRequest }) =>
      createCoverLetterQuestion(input.coverLetterId, input.request),
    onSuccess: async (_question, input) =>
      invalidateCoverLetterQueries(cache, toValue(userId), input.coverLetterId),
  })
}

export function useUpdateQuestionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      coverLetterId: string
      questionId: string
      request: UpdateQuestionRequest
    }) => updateCoverLetterQuestion(input.coverLetterId, input.questionId, input.request),
    onSuccess: async (_question, input) =>
      invalidateCoverLetterQueries(cache, toValue(userId), input.coverLetterId),
  })
}

export function useDeleteQuestionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; questionId: string; version: number }) =>
      deleteCoverLetterQuestion(input.coverLetterId, input.questionId, input.version),
    onSuccess: async (_value, input) => {
      cache.removeQueries({
        queryKey: coverLetterQueryKeys.versionsRoot(toValue(userId), input.questionId),
      })
      await invalidateCoverLetterQueries(cache, toValue(userId), input.coverLetterId)
    },
  })
}

export function useReorderQuestionsMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; request: ReorderQuestionsRequest }) =>
      reorderCoverLetterQuestions(input.coverLetterId, input.request),
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export function useGenerateCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; request: GenerateCoverLetterRequest }) => {
      const identity = [
        input.coverLetterId,
        input.request.coverLetterVersion,
        input.request.model,
        input.request.avoidExperienceDuplication,
        [...input.request.questionIds].sort().join(','),
        [...input.request.preferredEvidenceIds].sort().join(','),
      ].join('/')
      const key = keys.get(identity) ?? createCoverLetterIdempotencyKey('generate')
      keys.set(identity, key)
      return generateCoverLetter(input.coverLetterId, input.request, key).then((accepted) => {
        keys.delete(identity)
        return accepted
      })
    },
    onSuccess: async (_accepted, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'COVER_LETTER',
            input.coverLetterId,
          ),
        }),
      ])
    },
  })
}

export function useSaveAnswerVersionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      coverLetterId: string
      questionId: string
      request: SaveAnswerVersionRequest
    }) => saveAnswerVersion(input.questionId, input.request),
    onSuccess: async (answer, input) => {
      await Promise.all([
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.detail(toValue(userId), input.coverLetterId),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.root(toValue(userId)),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.versionsRoot(toValue(userId), input.questionId),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.verificationsRoot(toValue(userId), answer.id),
        }),
      ])
    },
  })
}

export function useRestoreAnswerVersionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      coverLetterId: string
      questionId: string
      versionId: string
      request: RestoreAnswerVersionRequest
    }) => restoreAnswerVersion(input.questionId, input.versionId, input.request),
    onSuccess: async (_answer, input) => {
      await Promise.all([
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.detail(toValue(userId), input.coverLetterId),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.root(toValue(userId)),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.versionsRoot(toValue(userId), input.questionId),
        }),
      ])
    },
  })
}

export function useVerifyAnswerVersionMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: {
      coverLetterId: string
      versionId: string
      request: VerifyAnswerVersionRequest
    }) => {
      const identity = `${input.versionId}/${input.request.model}`
      const key = keys.get(identity) ?? createCoverLetterIdempotencyKey('verify')
      keys.set(identity, key)
      return verifyAnswerVersion(input.versionId, input.request, key).then((accepted) => {
        keys.delete(identity)
        return accepted
      })
    },
    onSuccess: async (_accepted, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'COVER_LETTER',
            input.coverLetterId,
          ),
        }),
        cache.invalidateQueries({
          queryKey: coverLetterQueryKeys.verificationsRoot(toValue(userId), input.versionId),
        }),
      ])
    },
  })
}

export function useFinalizeCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; request: FinalizeCoverLetterRequest }) =>
      finalizeCoverLetter(input.coverLetterId, input.request),
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export function useArchiveCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; version: number }) =>
      archiveCoverLetter(input.coverLetterId, { version: input.version }),
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export function useUnarchiveCoverLetterMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: { coverLetterId: string; version: number }) =>
      unarchiveCoverLetter(input.coverLetterId, { version: input.version }),
    onSuccess: (detail) => applyCoverLetterDetail(cache, toValue(userId), detail),
  })
}

export async function applyCoverLetterDetail(
  cache: QueryClient,
  userId: string,
  detail: CoverLetterDetailDto,
): Promise<void> {
  cache.setQueryData(coverLetterQueryKeys.detail(userId, detail.id), detail)
  await Promise.all([
    cache.invalidateQueries({ queryKey: coverLetterQueryKeys.root(userId) }),
    cache.invalidateQueries({ queryKey: jobQueryKeys.root(userId) }),
    cache.invalidateQueries({ queryKey: jobQueryKeys.detail(userId, detail.job.id) }),
  ])
}

export async function invalidateCoverLetterQueries(
  cache: Pick<QueryClient, 'invalidateQueries'>,
  userId: string,
  coverLetterId: string,
): Promise<void> {
  await Promise.all([
    cache.invalidateQueries({ queryKey: coverLetterQueryKeys.root(userId) }),
    cache.invalidateQueries({
      queryKey: coverLetterQueryKeys.detail(userId, coverLetterId),
    }),
    cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(userId) }),
    cache.invalidateQueries({
      queryKey: agentRunQueryKeys.relatedResource(userId, 'COVER_LETTER', coverLetterId),
    }),
    cache.invalidateQueries({ queryKey: jobQueryKeys.root(userId) }),
  ])
}
