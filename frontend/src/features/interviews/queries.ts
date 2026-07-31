import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { agentRunQueryKeys } from '@/features/agent-runs/queries'
import { jobQueryKeys } from '@/features/jobs/queries'
import {
  createInterviewIdempotencyKey,
  createInterviewPreparation,
  getInterviewQuestion,
  getInterviewQuestionSet,
  getResearchRun,
  listInterviewAnswerFeedbacks,
  listInterviewAnswerVersions,
  listInterviewQuestionSets,
  listResearchSources,
  requestInterviewAnswerFeedback,
  retryResearchRun,
  saveInterviewAnswerVersion,
  type InterviewAnswerVersionListParams,
  type InterviewFeedbackListParams,
  type QuestionSetListParams,
  type ResearchSourceListParams,
} from '@/shared/api/interviewApi'
import type {
  CreateInterviewAnswerVersionRequest,
  CreateInterviewPreparationRequest,
  InterviewAnswerFeedbackRequest,
  QuestionSetDetailDto,
  ResearchRetryRequest,
} from '@/shared/api/interviewContracts'

export const interviewQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'interviews'] as const
  },
  questionSetList(userId: string, filters: QuestionSetListParams) {
    return [...this.root(userId), 'questionSets', filters] as const
  },
  questionSet(userId: string, questionSetId: string) {
    return [...this.root(userId), 'questionSet', questionSetId] as const
  },
  research(userId: string, researchRunId: string) {
    return [...this.root(userId), 'research', researchRunId] as const
  },
  researchSourcesRoot(userId: string, researchRunId: string) {
    return [...this.root(userId), 'research', researchRunId, 'sources'] as const
  },
  researchSources(userId: string, researchRunId: string, filters: ResearchSourceListParams) {
    return [...this.researchSourcesRoot(userId, researchRunId), filters] as const
  },
  question(userId: string, questionId: string) {
    return [...this.root(userId), 'question', questionId] as const
  },
  answerVersionsRoot(userId: string, questionId: string) {
    return [...this.root(userId), 'question', questionId, 'answerVersions'] as const
  },
  answerVersions(userId: string, questionId: string, filters: InterviewAnswerVersionListParams) {
    return [...this.answerVersionsRoot(userId, questionId), filters] as const
  },
  feedbacksRoot(userId: string, answerVersionId: string) {
    return [...this.root(userId), 'answerVersion', answerVersionId, 'feedbacks'] as const
  },
  feedbacks(userId: string, answerVersionId: string, filters: InterviewFeedbackListParams) {
    return [...this.feedbacksRoot(userId, answerVersionId), filters] as const
  },
}

export function useQuestionSetListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<QuestionSetListParams>,
) {
  return useQuery({
    queryKey: computed(() => interviewQueryKeys.questionSetList(toValue(userId), toValue(filters))),
    queryFn: () => listInterviewQuestionSets(toValue(filters)),
    enabled: computed(() => toValue(userId) !== ''),
  })
}

export function useQuestionSetDetailQuery(
  userId: MaybeRefOrGetter<string>,
  questionSetId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() =>
      interviewQueryKeys.questionSet(toValue(userId), toValue(questionSetId)),
    ),
    queryFn: () => getInterviewQuestionSet(toValue(questionSetId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(questionSetId) !== ''),
  })
}

export function useResearchRunQuery(
  userId: MaybeRefOrGetter<string>,
  researchRunId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => interviewQueryKeys.research(toValue(userId), toValue(researchRunId))),
    queryFn: () => getResearchRun(toValue(researchRunId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(researchRunId) !== ''),
  })
}

export function useResearchSourceListQuery(
  userId: MaybeRefOrGetter<string>,
  researchRunId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<ResearchSourceListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      interviewQueryKeys.researchSources(toValue(userId), toValue(researchRunId), toValue(filters)),
    ),
    queryFn: () => listResearchSources(toValue(researchRunId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(researchRunId) !== ''),
  })
}

export function useInterviewQuestionQuery(
  userId: MaybeRefOrGetter<string>,
  questionId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => interviewQueryKeys.question(toValue(userId), toValue(questionId))),
    queryFn: () => getInterviewQuestion(toValue(questionId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(questionId) !== ''),
  })
}

export function useInterviewAnswerVersionListQuery(
  userId: MaybeRefOrGetter<string>,
  questionId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<InterviewAnswerVersionListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      interviewQueryKeys.answerVersions(toValue(userId), toValue(questionId), toValue(filters)),
    ),
    queryFn: () => listInterviewAnswerVersions(toValue(questionId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(questionId) !== ''),
  })
}

export function useInterviewFeedbackListQuery(
  userId: MaybeRefOrGetter<string>,
  answerVersionId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<InterviewFeedbackListParams>,
) {
  return useQuery({
    queryKey: computed(() =>
      interviewQueryKeys.feedbacks(toValue(userId), toValue(answerVersionId), toValue(filters)),
    ),
    queryFn: () => listInterviewAnswerFeedbacks(toValue(answerVersionId), toValue(filters)),
    enabled: computed(() => toValue(userId) !== '' && toValue(answerVersionId) !== ''),
  })
}

export function useCreateInterviewPreparationMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: { jobId: string; request: CreateInterviewPreparationRequest }) => {
      const identity = [
        input.jobId,
        input.request.coverLetterId,
        input.request.researchQuality,
        input.request.qualityMode,
        [...input.request.questionTypes].sort().join(','),
        input.request.questionCount,
      ].join('/')
      const key = keys.get(identity) ?? createInterviewIdempotencyKey('prepare')
      keys.set(identity, key)
      return createInterviewPreparation(input.jobId, input.request, key).then((accepted) => {
        keys.delete(identity)
        return accepted
      })
    },
    onSuccess: async (accepted, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: interviewQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({ queryKey: jobQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: jobQueryKeys.detail(toValue(userId), input.jobId),
        }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'QUESTION_SET',
            accepted.questionSetId,
          ),
        }),
      ])
    },
  })
}

export function useRetryResearchMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: { researchRunId: string; request: ResearchRetryRequest }) => {
      const identity = `${input.researchRunId}/${input.request.researchQuality ?? ''}/${input.request.qualityMode ?? ''}`
      const key = keys.get(identity) ?? createInterviewIdempotencyKey('research-retry')
      keys.set(identity, key)
      return retryResearchRun(input.researchRunId, input.request, key).then((accepted) => {
        keys.delete(identity)
        return accepted
      })
    },
    onSuccess: async (accepted, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: interviewQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.research(toValue(userId), input.researchRunId),
        }),
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'QUESTION_SET',
            accepted.questionSetId,
          ),
        }),
      ])
    },
  })
}

export function useSaveInterviewAnswerMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      questionSetId: string
      questionId: string
      request: CreateInterviewAnswerVersionRequest
    }) => saveInterviewAnswerVersion(input.questionId, input.request),
    onSuccess: async (answer, input) => {
      await Promise.all([
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.questionSet(toValue(userId), input.questionSetId),
        }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.question(toValue(userId), input.questionId),
        }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.answerVersionsRoot(toValue(userId), input.questionId),
        }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.feedbacksRoot(toValue(userId), answer.id),
        }),
      ])
    },
  })
}

export function useRequestInterviewFeedbackMutation(userId: MaybeRefOrGetter<string>) {
  const cache = useQueryClient()
  const keys = new Map<string, string>()
  return useMutation({
    mutationFn: (input: {
      questionSetId: string
      answerVersionId: string
      request: InterviewAnswerFeedbackRequest
    }) => {
      const identity = `${input.answerVersionId}/${input.request.qualityMode}`
      const key = keys.get(identity) ?? createInterviewIdempotencyKey('feedback')
      keys.set(identity, key)
      return requestInterviewAnswerFeedback(input.answerVersionId, input.request, key).then(
        (accepted) => {
          keys.delete(identity)
          return accepted
        },
      )
    },
    onSuccess: async (accepted, input) => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: agentRunQueryKeys.root(toValue(userId)) }),
        cache.invalidateQueries({
          queryKey: agentRunQueryKeys.relatedResource(
            toValue(userId),
            'INTERVIEW_ANSWER_VERSION',
            input.answerVersionId,
          ),
        }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.feedbacksRoot(toValue(userId), input.answerVersionId),
        }),
        cache.invalidateQueries({
          queryKey: interviewQueryKeys.questionSet(toValue(userId), input.questionSetId),
        }),
      ])
      return accepted
    },
  })
}

export async function applyQuestionSetDetail(
  cache: QueryClient,
  userId: string,
  detail: QuestionSetDetailDto,
): Promise<void> {
  cache.setQueryData(interviewQueryKeys.questionSet(userId, detail.id), detail)
  await cache.invalidateQueries({ queryKey: interviewQueryKeys.root(userId) })
}
