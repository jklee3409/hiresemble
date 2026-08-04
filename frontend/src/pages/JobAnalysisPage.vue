<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { STATUS_LABELS, safeRequiredActionRoute } from '@/features/agent-runs/presentation'
import { useAgentRunDetailQuery, useRetryAgentRunMutation } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import {
  ELIGIBILITY_LABELS,
  FIT_CRITERION_CATEGORY_LABELS,
  MATCH_LEVEL_LABELS,
  OUTDATED_REASON_LABELS,
  evidenceCurrentStateLabel,
  formatAnalysisInstant,
  formatFitScore,
  formatRequirementSourceLocation,
  isCurrentlyVerifiedEvidence,
  jobAnalysisFailureCopy,
} from '@/features/jobs/analysisPresentation'
import JobPreparationJourney from '@/features/jobs/JobPreparationJourney.vue'
import {
  useAnalyzeJobMutation,
  useJobAnalysisHistoryQuery,
  useJobDetailQuery,
  useLatestJobAnalysisQuery,
  useLatestJobAnalysisRunQuery,
} from '@/features/jobs/queries'
import type { AgentRunStatus } from '@/shared/api/agentRunContracts'
import { normalizeApiError, type ApiClientError } from '@/shared/api/errors'
import type { Eligibility, FitCriterionCategory, MatchLevel } from '@/shared/api/jobContracts'
import type { JobAnalysisListParams } from '@/shared/api/jobApi'
import { getProfile } from '@/shared/api/profileApi'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const SCORE_DISCLAIMER =
  '적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.'
const REMOVED_SUMMARY_SENTENCE =
  /확인 가능한 근거 유형만 반영했으며,\s*일부 요건은 추가 확인이 필요합니다\.?/g
const ACTIVE_RUN_STATUSES: AgentRunStatus[] = ['QUEUED', 'RUNNING', 'WAITING_USER']
const TERMINAL_FAILURE_STATUSES: AgentRunStatus[] = ['FAILED', 'CANCELLED', 'INTERRUPTED']
type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'
const ELIGIBILITY_TONES = {
  ELIGIBLE: 'success',
  CONDITIONAL: 'warning',
  INELIGIBLE: 'danger',
  UNKNOWN: 'neutral',
} as const satisfies Record<Eligibility, StatusTone>
const RUN_TONES = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  WAITING_USER: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELLED: 'neutral',
  INTERRUPTED: 'warning',
} as const satisfies Record<AgentRunStatus, StatusTone>
const MATCH_TONES = {
  MATCHED: 'success',
  PARTIAL: 'warning',
  MISSING: 'danger',
  UNKNOWN: 'neutral',
} as const satisfies Record<MatchLevel, StatusTone>

const route = useRoute()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const acceptedRunId = ref('')
const actionError = ref<ApiClientError | null>(null)
const historyPage = ref(0)
const selectedHistoryId = ref<string | null>(null)
const criterionFilter = ref<MatchLevel | 'ALL'>('ALL')
const connectionState = ref<AgentRunConnectionState>('connecting')

const job = useJobDetailQuery(userId, jobId)
const profile = useQuery({
  queryKey: computed(() => profileQueryKeys.profile(userId.value)),
  queryFn: getProfile,
  enabled: computed(() => userId.value !== ''),
})
const latestAnalysis = useLatestJobAnalysisQuery(userId, jobId)
const historyFilters = computed<JobAnalysisListParams>(() => ({
  page: historyPage.value,
  size: 10,
  sort: 'analysisVersion,desc',
}))
const history = useJobAnalysisHistoryQuery(userId, jobId, historyFilters)
const latestAnalysisRun = useLatestJobAnalysisRunQuery(userId, jobId)
const analyzeMutation = useAnalyzeJobMutation(userId)
const retryMutation = useRetryAgentRunMutation(userId)
const recoveredRunId = computed(() => latestAnalysisRun.data.value?.items[0]?.id ?? '')
const automaticRunId = computed(() => job.data.value?.automaticAnalysis.agentRunId ?? '')
const currentRunId = computed(
  () => acceptedRunId.value || recoveredRunId.value || automaticRunId.value,
)
const currentRun = useAgentRunDetailQuery(userId, currentRunId)

let stream: AgentRunStreamController | null = null
let streamIdentity = ''

watch(
  [() => currentRun.data.value, userId, currentRunId],
  ([run, ownerId, runId]) => {
    const nextIdentity = `${ownerId}/${runId}`
    if (streamIdentity !== nextIdentity) {
      stream?.close()
      stream = null
      streamIdentity = nextIdentity
      connectionState.value = 'connecting'
    }
    if (
      run === undefined ||
      stream !== null ||
      run.workflowType !== 'JOB_ANALYSIS' ||
      run.resourceType !== 'JOB' ||
      run.resourceId !== jobId.value
    ) {
      return
    }

    stream = new AgentRunStreamController({
      userId: ownerId,
      agentRunId: runId,
      initialRun: run,
      cache,
      onConnectionState: (state) => {
        connectionState.value = state
      },
    })
    stream.start()
  },
  { immediate: true },
)

watch(historyPage, () => {
  selectedHistoryId.value = null
})

onBeforeUnmount(() => stream?.close())

const jobError = computed(() => (job.error.value ? normalizeApiError(job.error.value) : null))
const latestAnalysisError = computed(() =>
  latestAnalysis.error.value ? normalizeApiError(latestAnalysis.error.value) : null,
)
const noAnalysis = computed(
  () =>
    latestAnalysisError.value?.status === 404 &&
    latestAnalysisError.value.code === 'JOB_ANALYSIS_NOT_FOUND',
)
const latestAnalysisFailed = computed(() => latestAnalysis.isError.value && !noAnalysis.value)
const hasUsableDescription = computed(() => {
  const value = job.data.value
  return (
    value !== undefined &&
    value.extractionStatus !== 'NEEDS_MANUAL_INPUT' &&
    (value.descriptionText?.trim().length ?? 0) > 0
  )
})
const profileIncomplete = computed(
  () => profile.data.value !== undefined && !profile.data.value.profileCompleted,
)
const runIsActive = computed(
  () =>
    currentRun.data.value !== undefined &&
    ACTIVE_RUN_STATUSES.includes(currentRun.data.value.status),
)
const runFailed = computed(
  () =>
    currentRun.data.value !== undefined &&
    TERMINAL_FAILURE_STATUSES.includes(currentRun.data.value.status),
)
const runFailureCopy = computed(() =>
  jobAnalysisFailureCopy(
    currentRun.data.value?.safeError?.code,
    currentRun.data.value?.safeError?.message,
    currentRun.data.value?.status,
  ),
)
const automaticFailureCopy = computed(() => {
  const error = job.data.value?.automaticAnalysis.error
  return jobAnalysisFailureCopy(error?.code, error?.message)
})
const actionFailureCopy = computed(() =>
  jobAnalysisFailureCopy(actionError.value?.code, actionError.value?.message),
)
const acceptedRunPending = computed(
  () => acceptedRunId.value !== '' && currentRun.data.value === undefined,
)
const runStateUnresolved = computed(
  () => currentRunId.value !== '' && (currentRun.isLoading.value || currentRun.isError.value),
)
const submissionPending = computed(
  () =>
    analyzeMutation.isPending.value ||
    retryMutation.isPending.value ||
    runIsActive.value ||
    acceptedRunPending.value,
)
const commandUnavailable = computed(
  () =>
    submissionPending.value ||
    !hasUsableDescription.value ||
    latestAnalysis.isLoading.value ||
    latestAnalysisFailed.value ||
    latestAnalysisRun.isLoading.value ||
    latestAnalysisRun.isError.value ||
    runStateUnresolved.value,
)
const insufficientData = computed(
  () =>
    actionError.value?.code === 'INSUFFICIENT_JOB_DATA' ||
    currentRun.data.value?.safeError?.code === 'INSUFFICIENT_JOB_DATA',
)
const selectedHistory = computed(
  () =>
    history.data.value?.items.find((item) => item.id === selectedHistoryId.value) ??
    latestAnalysis.data.value ??
    null,
)
const hasEvidenceWithChangedState = computed(() => {
  const analysis = latestAnalysis.data.value
  if (analysis === undefined) return false
  return [
    ...analysis.matchedEvidenceRefs,
    ...analysis.scoreBreakdown.flatMap((criterion) => criterion.evidenceRefs),
  ].some((evidence) => !isCurrentlyVerifiedEvidence(evidence))
})
const matchOverview = computed(() => {
  const criteria = latestAnalysis.data.value?.scoreBreakdown ?? []
  return (['MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN'] as const).map((level) => ({
    level,
    count: criteria.filter((criterion) => criterion.matchLevel === level).length,
  }))
})
const filteredCriteria = computed(() => {
  const criteria = latestAnalysis.data.value?.scoreBreakdown ?? []
  return criterionFilter.value === 'ALL'
    ? criteria
    : criteria.filter((criterion) => criterion.matchLevel === criterionFilter.value)
})
const displayedAnalysisSummary = computed(() =>
  (latestAnalysis.data.value?.analysisSummary ?? '')
    .replace(REMOVED_SUMMARY_SENTENCE, '')
    .replace(/\s{2,}/g, ' ')
    .trim(),
)
const roundToFive = (value: number) => Math.round(value / 5) * 5
const formatScore = (value: number) => String(roundToFive(value))
const categoryOverview = computed(() => {
  const criteria = latestAnalysis.data.value?.scoreBreakdown ?? []
  return Object.entries(FIT_CRITERION_CATEGORY_LABELS)
    .map(([category, label]) => {
      const items = criteria.filter((criterion) => criterion.category === category)
      const score = items.reduce((sum, criterion) => sum + criterion.score, 0)
      const weight = items.reduce((sum, criterion) => sum + criterion.weight, 0)
      return {
        category: category as FitCriterionCategory,
        label,
        count: items.length,
        score,
        weight,
        percentage: weight > 0 ? roundToFive((score / weight) * 100) : null,
      }
    })
    .filter((item) => item.count > 0)
})
const formatCoverage = (value: number | null) =>
  value === null ? '기록 없음' : `${roundToFive(value)}%`
const connectionLabel = computed(() => {
  if (!runIsActive.value) return '진행 상황 확인 완료'
  return {
    connecting: '실시간 진행 상황을 연결하는 중',
    connected: '실시간 진행 상황 연결됨',
    reconnecting: '진행 상황을 다시 연결하는 중',
    polling: '진행 상황을 주기적으로 다시 확인하는 중',
    closed: '진행 상황 확인 완료',
  }[connectionState.value]
})
const actionRoute = computed(() =>
  safeRequiredActionRoute(currentRun.data.value?.requiredUserAction?.route ?? null),
)
const progressMessage = computed(() => {
  const progress = currentRun.data.value?.progressPercent ?? 0
  if (progress < 25) return '공고 내용을 읽고 있어요.'
  if (progress < 60) return '주요 업무와 지원 조건을 정리하고 있어요.'
  if (progress < 90) return '내 경험과 비교하고 있어요.'
  return '분석 결과를 마무리하고 있어요.'
})
const showAnalysisCommand = computed(() => {
  if (runFailed.value) return false
  if (latestAnalysis.data.value) return true
  return ['NOT_REQUESTED', 'BLOCKED', 'SUPERSEDED'].includes(
    job.data.value?.automaticAnalysis.state ?? '',
  )
})

async function requestAnalysis(forceReanalyze: boolean): Promise<void> {
  if (job.data.value === undefined || !hasUsableDescription.value || submissionPending.value) {
    return
  }
  actionError.value = null
  try {
    const accepted = await analyzeMutation.mutateAsync({
      jobId: jobId.value,
      request: {
        qualityMode: 'BALANCED',
        forceReanalyze,
        jobVersion: job.data.value.version,
      },
    })
    acceptedRunId.value = accepted.agentRunId
    connectionState.value = 'connecting'
  } catch (error) {
    actionError.value = normalizeApiError(error)
    if (actionError.value.code === 'RESOURCE_VERSION_CONFLICT') {
      await job.refetch()
    }
  }
}

async function retryFailedRun(): Promise<void> {
  const run = currentRun.data.value
  if (run === undefined || !run.retryable || retryMutation.isPending.value) return
  actionError.value = null
  try {
    const accepted = await retryMutation.mutateAsync(run.id)
    acceptedRunId.value = accepted.agentRunId
    connectionState.value = 'connecting'
  } catch (error) {
    actionError.value = normalizeApiError(error)
  }
}

async function restartFailedAnalysis(): Promise<void> {
  const run = currentRun.data.value
  if (run === undefined || submissionPending.value || !hasUsableDescription.value) return
  if (run.retryable) {
    await retryFailedRun()
    return
  }
  await requestAnalysis(true)
}

function eligibilityTone(
  value: Eligibility,
): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return ELIGIBILITY_TONES[value]
}

function runTone(value: AgentRunStatus): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return RUN_TONES[value]
}

function matchTone(value: MatchLevel): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return MATCH_TONES[value]
}
</script>

<template>
  <section class="job-analysis app-page" aria-label="공고 분석">
    <StatePanel
      v-if="job.isLoading.value"
      class="job-analysis__state"
      kind="loading"
      title="공고 정보를 불러오는 중…"
      description="분석할 공고와 현재 버전을 확인하고 있어요."
    />
    <StatePanel
      v-else-if="job.isError.value"
      class="job-analysis__state"
      kind="error"
      :title="
        jobError?.status === 404 ? '공고를 찾을 수 없어요.' : '공고 정보를 불러오지 못했어요.'
      "
      :description="jobError?.message ?? '잠시 후 다시 시도해 주세요.'"
    >
      <template #actions>
        <RouterLink class="button button--secondary" :to="{ name: 'jobs' }">
          관심 공고로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>

    <template v-else-if="job.data.value">
      <header class="analysis-context-header">
        <div>
          <h2>공고 분석</h2>
          <p>공고에서 찾은 조건을 내가 확인한 경험과 비교했어요.</p>
        </div>
        <RouterLink
          class="button button--secondary"
          :to="{ name: 'job-overview', params: { jobId } }"
        >
          공고 본문 보기
        </RouterLink>
      </header>

      <JobPreparationJourney
        v-if="!latestAnalysis.data.value"
        class="job-analysis__journey"
        :job="job.data.value"
      />
      <section
        v-if="!hasUsableDescription"
        class="analysis-prerequisite alert alert--warning"
        role="status"
      >
        <div>
          <h3>분석할 공고 본문이 필요해요.</h3>
          <p>
            공고 내용을 불러오는 중이거나 직접 입력이 필요해요. 공고 정보에서 본문을 확인해 주세요.
          </p>
        </div>
        <RouterLink
          class="button button--secondary"
          :to="{ name: 'job-overview', params: { jobId } }"
        >
          공고 정보 확인
        </RouterLink>
      </section>

      <section
        v-if="profileIncomplete"
        class="analysis-prerequisite alert alert--warning"
        role="status"
      >
        <div>
          <h3>프로필을 더 채우면 비교 근거가 풍부해져요.</h3>
          <p>지금도 분석할 수 있으며, 누락된 정보는 판단 정보 부족으로 표시될 수 있어요.</p>
        </div>
        <RouterLink class="button button--secondary" :to="{ name: 'profile-basic' }">
          프로필 확인
        </RouterLink>
      </section>

      <section
        v-if="insufficientData"
        class="analysis-prerequisite alert alert--warning"
        role="alert"
      >
        <div>
          <h3>분석할 요구사항을 충분히 찾지 못했어요.</h3>
          <p>
            공고 본문에 주요 업무나 지원 자격을 보완한 뒤 다시 분석해 주세요. 완료되지 않은 분석
            결과는 저장되지 않아요.
          </p>
        </div>
        <RouterLink
          class="button button--secondary"
          :to="{ name: 'job-overview', params: { jobId } }"
        >
          공고 본문 보완
        </RouterLink>
      </section>

      <p
        v-if="actionError && !insufficientData"
        class="alert alert--danger job-analysis__message"
        role="alert"
      >
        {{
          actionError.code === 'RESOURCE_VERSION_CONFLICT'
            ? '공고가 변경됐어요. 최신 내용을 확인한 뒤 다시 분석해 주세요.'
            : actionFailureCopy.description
        }}
      </p>
      <p
        v-if="latestAnalysisRun.isError.value"
        class="alert alert--warning job-analysis__message"
        role="status"
      >
        진행 중인 AI 작업이 있는지 확인하지 못했어요. 중복 요청을 막기 위해 AI 작업을 확인한 뒤 다시
        시도해 주세요.
      </p>

      <section
        v-if="
          currentRunId &&
          (currentRun.isLoading.value || currentRun.isError.value || runIsActive || runFailed)
        "
        class="analysis-run section-surface"
        aria-labelledby="analysis-run-heading"
      >
        <div class="analysis-run__header">
          <div>
            <p class="section-kicker">진행 상태</p>
            <h3 id="analysis-run-heading" class="section-title">
              {{ runIsActive ? progressMessage : '최근 분석 작업' }}
            </h3>
          </div>
          <StatusBadge
            v-if="currentRun.data.value"
            :label="STATUS_LABELS[currentRun.data.value.status]"
            :tone="runTone(currentRun.data.value.status)"
          />
        </div>

        <StatePanel
          v-if="currentRun.isLoading.value"
          kind="loading"
          title="AI 작업 상태를 확인하는 중…"
          description="접수된 공고 분석을 불러오고 있어요."
        />
        <p v-else-if="currentRun.isError.value" class="alert alert--warning" role="status">
          AI 작업 상태를 다시 확인하는 중이에요. 이 연결 상태만으로 분석 실패를 의미하지 않아요.
        </p>
        <template v-else-if="currentRun.data.value">
          <div
            v-if="runIsActive"
            class="analysis-run__progress"
            :aria-label="`공고 분석 진행률 ${currentRun.data.value.progressPercent}%`"
          >
            <progress
              class="progress-track"
              :value="currentRun.data.value.progressPercent"
              max="100"
              :aria-label="`공고 분석 진행률 ${currentRun.data.value.progressPercent}%`"
            >
              {{ currentRun.data.value.progressPercent }}%
            </progress>
            <strong>{{ currentRun.data.value.progressPercent }}%</strong>
          </div>
          <p v-if="runIsActive" class="analysis-run__connection" role="status">
            {{ connectionLabel }}. 연결이 잠시 끊겨도 분석 실패로 처리하지 않아요.
          </p>
          <div
            v-if="
              currentRun.data.value.status === 'WAITING_USER' &&
              currentRun.data.value.requiredUserAction
            "
            class="analysis-run__waiting alert alert--warning"
            role="status"
          >
            <p>{{ currentRun.data.value.requiredUserAction.message }}</p>
            <RouterLink v-if="actionRoute" class="button button--secondary" :to="actionRoute">
              필요한 정보 입력
            </RouterLink>
          </div>
          <div v-if="runFailed" class="analysis-run__failure alert alert--danger" role="alert">
            <div>
              <h4>{{ runFailureCopy.title }}</h4>
              <p>{{ runFailureCopy.description }}</p>
            </div>
            <button
              type="button"
              class="button button--primary"
              :disabled="submissionPending || !hasUsableDescription"
              @click="restartFailedAnalysis"
            >
              {{ submissionPending ? '재실행 접수 중…' : '공고 분석 재실행' }}
            </button>
          </div>
          <RouterLink
            class="analysis-run__detail-link"
            :to="{
              name: 'agent-run-detail',
              params: { agentRunId: currentRun.data.value.id },
            }"
          >
            작업 자세히 보기
          </RouterLink>
        </template>
      </section>

      <section
        v-if="showAnalysisCommand"
        class="analysis-command section-surface"
        aria-labelledby="analysis-command-heading"
      >
        <div>
          <p class="section-kicker">필요할 때만</p>
          <h3 id="analysis-command-heading" class="section-title">
            {{ latestAnalysis.data.value ? '최신 정보로 다시 분석' : '분석 다시 시도' }}
          </h3>
          <p class="analysis-command__description">
            현재 공고 내용과 내가 확인한 경험을 기준으로 새 결과를 만들어요.
          </p>
        </div>
        <button
          type="button"
          class="button button--primary"
          :disabled="commandUnavailable"
          @click="requestAnalysis(Boolean(latestAnalysis.data.value))"
        >
          {{
            submissionPending
              ? '분석 진행 중…'
              : latestAnalysisRun.isLoading.value
                ? 'AI 작업 확인 중…'
                : latestAnalysisRun.isError.value
                  ? 'AI 작업 확인 필요'
                  : runStateUnresolved
                    ? 'AI 작업 확인 중…'
                    : latestAnalysis.isLoading.value
                      ? '결과 확인 중…'
                      : latestAnalysis.data.value
                        ? '최신 정보로 다시 분석'
                        : '분석 다시 시도'
          }}
        </button>
      </section>

      <StatePanel
        v-if="latestAnalysis.isLoading.value"
        class="job-analysis__state"
        kind="loading"
        title="최근 분석 결과를 확인하는 중…"
        description="공고에 저장된 가장 최근 결과를 불러오고 있어요."
      />
      <StatePanel
        v-else-if="latestAnalysisFailed"
        class="job-analysis__state"
        kind="error"
        title="분석 결과를 불러오지 못했어요."
        :description="latestAnalysisError?.message ?? '잠시 후 다시 시도해 주세요.'"
      >
        <template #actions>
          <button type="button" class="button button--secondary" @click="latestAnalysis.refetch()">
            다시 불러오기
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="noAnalysis"
        class="job-analysis__state"
        :kind="job.data.value.automaticAnalysis.state === 'BLOCKED' ? 'error' : 'loading'"
        :title="
          job.data.value.automaticAnalysis.error
            ? automaticFailureCopy.title
            : job.data.value.automaticAnalysis.state === 'BLOCKED'
              ? '자동 분석을 시작하지 못했어요.'
              : job.data.value.automaticAnalysis.state === 'WAITING_FOR_CONTENT'
                ? '공고 본문을 확인하고 있어요.'
                : '분석 결과를 준비하고 있어요.'
        "
        :description="
          job.data.value.automaticAnalysis.error
            ? automaticFailureCopy.description
            : '공고 등록 뒤 분석이 자동으로 이어져요. 이 페이지를 닫아도 작업은 계속됩니다.'
        "
      />

      <article
        v-else-if="latestAnalysis.data.value"
        class="analysis-result"
        aria-labelledby="analysis-result-heading"
      >
        <section
          v-if="latestAnalysis.data.value.analysisOutdated"
          class="analysis-outdated alert alert--warning"
          role="status"
        >
          <div>
            <div class="analysis-outdated__title">
              <StatusBadge label="OUTDATED" tone="warning" />
              <h3>분석 이후 정보가 변경됐어요.</h3>
            </div>
            <ul>
              <li v-for="reason in latestAnalysis.data.value.outdatedReasons" :key="reason">
                {{ OUTDATED_REASON_LABELS[reason] }}
              </li>
            </ul>
            <p>아래 기존 결과는 그대로 유지돼요. 필요할 때 현재 정보로 재분석해 주세요.</p>
          </div>
        </section>

        <section class="analysis-result__hero section-surface">
          <div class="analysis-result__heading">
            <div>
              <h2 id="analysis-result-heading">공고와 잘 맞는 강점을 분석했어요.</h2>
              <p>{{ formatAnalysisInstant(latestAnalysis.data.value.createdAt) }}</p>
            </div>
            <RouterLink
              class="analysis-result__run-link"
              :to="{
                name: 'agent-run-detail',
                params: { agentRunId: latestAnalysis.data.value.agentRunId },
              }"
            >
              분석 과정 보기
            </RouterLink>
          </div>
          <div class="analysis-result__metrics">
            <div class="analysis-metric">
              <span>지원 가능 여부</span>
              <StatusBadge
                :label="ELIGIBILITY_LABELS[latestAnalysis.data.value.eligibility]"
                :tone="eligibilityTone(latestAnalysis.data.value.eligibility)"
              />
            </div>
            <div class="analysis-metric analysis-metric--score">
              <span>적합도 <abbr :title="SCORE_DISCLAIMER">안내</abbr></span>
              <strong>{{ formatFitScore(latestAnalysis.data.value.fitScore) }}</strong>
              <p>공고 조건과 내 정보의 일치도예요.</p>
            </div>
            <div class="analysis-metric analysis-metric--coverage">
              <span>확인한 요건</span>
              <strong>{{ formatCoverage(latestAnalysis.data.value.analysisCoverage) }}</strong>
              <p>등록한 정보로 확인할 수 있었던 요건 비율이에요.</p>
            </div>
            <div class="analysis-metric">
              <span>잘 맞는 경험</span>
              <strong>{{ latestAnalysis.data.value.strengths.length }}개</strong>
            </div>
            <div class="analysis-metric">
              <span>보완하면 좋은 점</span>
              <strong>{{ latestAnalysis.data.value.gaps.length }}개</strong>
            </div>
          </div>
          <div class="analysis-result__next" aria-label="분석 다음 단계">
            <RouterLink
              class="button button--primary"
              :to="{ name: 'job-cover-letter', params: { jobId } }"
            >
              자기소개서 준비하기
            </RouterLink>
            <RouterLink class="button button--secondary" :to="{ name: 'profile-basic' }">
              내 정보 보완하기
            </RouterLink>
            <RouterLink
              class="button button--ghost"
              :to="{ name: 'job-overview', params: { jobId } }"
            >
              공고 내용 수정
            </RouterLink>
            <RouterLink
              v-if="job.data.value.latestQuestionSetId"
              class="button button--ghost"
              :to="{ name: 'job-interview', params: { jobId } }"
            >
              면접 준비 보기
            </RouterLink>
          </div>
        </section>

        <section class="analysis-overview section-surface" aria-labelledby="match-overview-heading">
          <div class="analysis-overview__heading">
            <div>
              <p class="section-kicker">매칭 요약</p>
              <h3 id="match-overview-heading" class="section-title">요건 매칭 현황</h3>
            </div>
            <p>등록한 정보와 공고 조건을 비교한 결과예요.</p>
          </div>
          <div class="analysis-overview__statuses">
            <article v-for="item in matchOverview" :key="item.level">
              <StatusBadge :label="MATCH_LEVEL_LABELS[item.level]" :tone="matchTone(item.level)" />
              <strong>{{ item.count }}</strong>
              <span>개 요건</span>
            </article>
          </div>
          <div class="analysis-overview__categories">
            <article v-for="item in categoryOverview" :key="item.category">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.count }}개 기준</span>
              </div>
              <div class="analysis-overview__bar" aria-hidden="true">
                <span :style="{ width: `${item.percentage ?? 0}%` }"></span>
              </div>
              <small v-if="item.percentage !== null">
                {{ formatScore(item.score) }} / {{ formatScore(item.weight) }}점
              </small>
              <small v-else>판정 가능한 근거가 아직 없어요.</small>
            </article>
          </div>
        </section>

        <section
          class="analysis-requirements section-surface"
          aria-labelledby="job-summary-heading"
        >
          <div class="analysis-section-heading">
            <div>
              <p class="section-kicker">공고 핵심 정리</p>
              <h3 id="job-summary-heading" class="section-title">먼저 중요한 내용만 확인하세요</h3>
            </div>
            <p>원문에서 추출한 세부 내용은 항목별로 펼쳐볼 수 있어요.</p>
          </div>
          <div v-if="displayedAnalysisSummary" class="analysis-requirements__summary">
            <span>AI 핵심 요약</span>
            <p>{{ displayedAnalysisSummary }}</p>
          </div>
          <div class="analysis-requirements__details">
            <details class="analysis-requirement-group">
              <summary>
                <div>
                  <span>주요 업무</span>
                  <strong>
                    {{
                      latestAnalysis.data.value.responsibilities[0]?.text ?? '확인된 내용이 없어요.'
                    }}
                  </strong>
                </div>
                <span class="analysis-requirement-group__count">
                  {{ latestAnalysis.data.value.responsibilities.length }}개
                </span>
              </summary>
              <ul v-if="latestAnalysis.data.value.responsibilities.length">
                <li
                  v-for="item in latestAnalysis.data.value.responsibilities"
                  :key="`${item.category}/${item.text}`"
                >
                  <span>{{ item.text }}</span>
                  <small v-if="formatRequirementSourceLocation(item.sourceLocation)">
                    {{ formatRequirementSourceLocation(item.sourceLocation) }}
                  </small>
                </li>
              </ul>
              <p v-else class="analysis-empty-copy">확인된 주요 업무가 없어요.</p>
            </details>
            <details class="analysis-requirement-group">
              <summary>
                <div>
                  <span>필수 지원 자격</span>
                  <strong>
                    {{
                      latestAnalysis.data.value.requiredQualifications[0]?.text ??
                      '확인된 내용이 없어요.'
                    }}
                  </strong>
                </div>
                <span class="analysis-requirement-group__count">
                  {{ latestAnalysis.data.value.requiredQualifications.length }}개
                </span>
              </summary>
              <ul v-if="latestAnalysis.data.value.requiredQualifications.length">
                <li
                  v-for="item in latestAnalysis.data.value.requiredQualifications"
                  :key="`${item.category}/${item.text}`"
                >
                  <span>{{ item.text }}</span>
                  <small v-if="formatRequirementSourceLocation(item.sourceLocation)">
                    {{ formatRequirementSourceLocation(item.sourceLocation) }}
                  </small>
                </li>
              </ul>
              <p v-else class="analysis-empty-copy">확인된 필수 지원 자격이 없어요.</p>
            </details>
            <details class="analysis-requirement-group">
              <summary>
                <div>
                  <span>우대 사항</span>
                  <strong>
                    {{
                      latestAnalysis.data.value.preferredQualifications[0]?.text ??
                      '확인된 내용이 없어요.'
                    }}
                  </strong>
                </div>
                <span class="analysis-requirement-group__count">
                  {{ latestAnalysis.data.value.preferredQualifications.length }}개
                </span>
              </summary>
              <ul v-if="latestAnalysis.data.value.preferredQualifications.length">
                <li
                  v-for="item in latestAnalysis.data.value.preferredQualifications"
                  :key="`${item.category}/${item.text}`"
                >
                  <span>{{ item.text }}</span>
                  <small v-if="formatRequirementSourceLocation(item.sourceLocation)">
                    {{ formatRequirementSourceLocation(item.sourceLocation) }}
                  </small>
                </li>
              </ul>
              <p v-else class="analysis-empty-copy">확인된 우대 사항이 없어요.</p>
            </details>
          </div>
        </section>

        <section
          class="analysis-insights section-surface"
          aria-labelledby="analysis-insights-heading"
        >
          <div class="analysis-section-heading">
            <div>
              <p class="section-kicker">지원 전략</p>
              <h3 id="analysis-insights-heading" class="section-title">강점과 보완 포인트</h3>
            </div>
            <p>자기소개서에서 강조할 내용과 보완할 정보를 나눠봤어요.</p>
          </div>
          <div class="analysis-insights__grid">
            <article class="analysis-insight analysis-insight--strength">
              <div class="analysis-insight__heading">
                <span aria-hidden="true">✓</span>
                <div>
                  <small>잘 맞는 부분</small>
                  <h4>내 강점</h4>
                </div>
                <strong>{{ latestAnalysis.data.value.strengths.length }}</strong>
              </div>
              <ol v-if="latestAnalysis.data.value.strengths.length">
                <li
                  v-for="(strength, index) in latestAnalysis.data.value.strengths"
                  :key="strength"
                >
                  <span>{{ index + 1 }}</span>
                  <p>{{ strength }}</p>
                </li>
              </ol>
              <p v-else class="analysis-empty-copy">확인한 경험에서 찾은 강점이 아직 없어요.</p>
            </article>
            <article class="analysis-insight analysis-insight--gap">
              <div class="analysis-insight__heading">
                <span aria-hidden="true">↑</span>
                <div>
                  <small>준비하면 좋은 부분</small>
                  <h4>보완 포인트</h4>
                </div>
                <strong>{{ latestAnalysis.data.value.gaps.length }}</strong>
              </div>
              <ol v-if="latestAnalysis.data.value.gaps.length">
                <li v-for="(gap, index) in latestAnalysis.data.value.gaps" :key="gap">
                  <span>{{ index + 1 }}</span>
                  <p>{{ gap }}</p>
                </li>
              </ol>
              <p v-else class="analysis-empty-copy">현재 확인된 보완 포인트가 없어요.</p>
            </article>
          </div>
        </section>

        <section class="analysis-evidence section-surface">
          <p class="section-kicker">내 경험 연결</p>
          <h3 class="section-title">점수에 활용한 경험</h3>
          <p
            v-if="hasEvidenceWithChangedState"
            class="analysis-evidence__notice alert alert--warning"
            role="status"
          >
            이 결과에 사용한 경험 중 현재 상태가 달라진 항목이 있어요. 저장된 결과와 점수는 그대로
            두고, 다시 분석할 때는 지금 확인된 경험만 사용해요.
          </p>
          <div
            v-if="latestAnalysis.data.value.matchedEvidenceRefs.length"
            class="analysis-evidence__grid"
          >
            <article
              v-for="evidence in latestAnalysis.data.value.matchedEvidenceRefs"
              :key="evidence.id"
              class="analysis-evidence__item"
              :class="{
                'analysis-evidence__item--changed': !isCurrentlyVerifiedEvidence(evidence),
              }"
            >
              <strong>{{ evidence.title }}</strong>
              <span>{{ evidence.evidenceCategory }}</span>
              <small
                :class="{
                  'analysis-evidence__state--changed': !isCurrentlyVerifiedEvidence(evidence),
                }"
              >
                {{ evidenceCurrentStateLabel(evidence) }}
              </small>
            </article>
          </div>
          <p v-else class="analysis-empty-copy">점수에 연결된 확인한 경험이 없어요.</p>
        </section>

        <section class="analysis-breakdown section-surface">
          <div class="analysis-section-heading analysis-breakdown__heading">
            <div>
              <p class="section-kicker">세부 매칭</p>
              <h3 class="section-title">조건별 확인 결과</h3>
            </div>
            <p>상태를 선택하면 필요한 조건만 모아볼 수 있어요.</p>
          </div>
          <div class="analysis-breakdown__filters" aria-label="조건별 확인 결과 필터">
            <button
              type="button"
              :class="{ 'analysis-breakdown__filter--active': criterionFilter === 'ALL' }"
              :aria-pressed="criterionFilter === 'ALL'"
              @click="criterionFilter = 'ALL'"
            >
              전체
              <span>{{ latestAnalysis.data.value.scoreBreakdown.length }}</span>
            </button>
            <button
              v-for="item in matchOverview"
              :key="item.level"
              type="button"
              :class="{ 'analysis-breakdown__filter--active': criterionFilter === item.level }"
              :aria-pressed="criterionFilter === item.level"
              @click="criterionFilter = item.level"
            >
              {{ MATCH_LEVEL_LABELS[item.level] }}
              <span>{{ item.count }}</span>
            </button>
          </div>
          <div class="analysis-breakdown__list">
            <article
              v-for="criterion in filteredCriteria"
              :key="`${criterion.category}/${criterion.criterion}`"
              class="analysis-criterion"
              :data-match-level="criterion.matchLevel"
            >
              <div class="analysis-criterion__header">
                <div>
                  <span>{{ FIT_CRITERION_CATEGORY_LABELS[criterion.category] }}</span>
                  <h4>{{ criterion.criterion }}</h4>
                </div>
                <StatusBadge
                  :label="MATCH_LEVEL_LABELS[criterion.matchLevel]"
                  :tone="matchTone(criterion.matchLevel)"
                />
              </div>
              <p class="analysis-criterion__score">
                {{ formatScore(criterion.score) }} / {{ formatScore(criterion.weight) }}점
              </p>
              <details class="analysis-criterion__detail">
                <summary>판단한 이유와 연결 경험</summary>
                <p>{{ criterion.explanation }}</p>
                <ul v-if="criterion.evidenceRefs.length" class="analysis-criterion__evidence">
                  <li
                    v-for="reference in criterion.evidenceRefs"
                    :key="reference.id"
                    :class="{
                      'analysis-criterion__evidence-item--changed':
                        !isCurrentlyVerifiedEvidence(reference),
                    }"
                  >
                    <span>{{ reference.title }}</span>
                    <small>{{ evidenceCurrentStateLabel(reference) }}</small>
                  </li>
                </ul>
                <p v-else class="analysis-empty-copy">연결된 경험 근거가 없어요.</p>
              </details>
            </article>
            <p v-if="!filteredCriteria.length" class="analysis-breakdown__empty">
              이 상태에 해당하는 조건이 없어요.
            </p>
          </div>
        </section>
      </article>

      <details
        v-if="history.data.value?.items.length"
        class="analysis-history section-surface"
        aria-labelledby="analysis-history-heading"
      >
        <summary class="analysis-history__summary">
          <div>
            <p class="section-kicker">이전 결과</p>
            <h3 id="analysis-history-heading" class="section-title">분석 결과 기록</h3>
            <p>공고나 내 정보가 바뀌기 전 결과도 다시 확인할 수 있어요.</p>
          </div>
          <span>총 {{ history.data.value.totalElements }}개</span>
        </summary>
        <div class="analysis-history__layout">
          <ol class="analysis-history__list">
            <li v-for="item in history.data.value.items" :key="item.id">
              <button
                type="button"
                :class="{
                  'analysis-history__button--selected': selectedHistory?.id === item.id,
                }"
                :aria-pressed="selectedHistory?.id === item.id"
                @click="selectedHistoryId = item.id"
              >
                <span>
                  {{
                    latestAnalysis.data.value?.id === item.id
                      ? '현재 결과'
                      : formatAnalysisInstant(item.createdAt)
                  }}
                </span>
                <small>
                  {{ formatFitScore(item.fitScore) }} ·
                  {{ ELIGIBILITY_LABELS[item.eligibility] }}
                </small>
              </button>
            </li>
          </ol>
          <article v-if="selectedHistory" class="analysis-history__selection">
            <div>
              <p class="section-kicker">선택한 결과</p>
              <h4>
                {{
                  latestAnalysis.data.value?.id === selectedHistory.id
                    ? '현재 분석 결과'
                    : formatAnalysisInstant(selectedHistory.createdAt)
                }}
              </h4>
            </div>
            <dl>
              <div>
                <dt>지원 가능 여부</dt>
                <dd>{{ ELIGIBILITY_LABELS[selectedHistory.eligibility] }}</dd>
              </div>
              <div>
                <dt>적합도 점수</dt>
                <dd>{{ formatFitScore(selectedHistory.fitScore) }}</dd>
              </div>
              <div>
                <dt>분석 커버리지</dt>
                <dd>{{ formatCoverage(selectedHistory.analysisCoverage) }}</dd>
              </div>
              <div>
                <dt>분석 시각</dt>
                <dd>{{ formatAnalysisInstant(selectedHistory.createdAt) }}</dd>
              </div>
              <div>
                <dt>결과 상태</dt>
                <dd>
                  {{
                    latestAnalysis.data.value?.id === selectedHistory.id
                      ? '현재 사용 중인 결과'
                      : '이전에 저장된 결과'
                  }}
                </dd>
              </div>
            </dl>
            <p>{{ SCORE_DISCLAIMER }}</p>
            <RouterLink
              :to="{
                name: 'agent-run-detail',
                params: { agentRunId: selectedHistory.agentRunId },
              }"
            >
              이 결과가 만들어진 과정 보기
            </RouterLink>
          </article>
        </div>
        <PaginationNav
          v-if="history.data.value.totalPages > 1"
          :page="historyPage"
          :total-pages="history.data.value.totalPages"
          label="공고 분석 이력 페이지"
          @change="historyPage = $event"
        />
      </details>
      <p v-else-if="history.isLoading.value" class="analysis-history-state" role="status">
        과거 분석 이력을 불러오는 중이에요.
      </p>
      <p
        v-else-if="history.isError.value"
        class="analysis-history-state alert alert--warning"
        role="status"
      >
        과거 분석 이력을 불러오지 못했어요. 최신 결과는 그대로 확인할 수 있어요.
      </p>
    </template>
  </section>
</template>

<style scoped>
.job-analysis {
  min-width: 0;
}

.analysis-context-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.analysis-context-header h2 {
  font-size: var(--font-size-xl);
  font-weight: 780;
}

.analysis-context-header p {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
}

.job-analysis__journey {
  margin-top: var(--space-6);
}

.job-analysis__state,
.job-analysis__message,
.analysis-prerequisite,
.analysis-run,
.analysis-command,
.analysis-result,
.analysis-history {
  margin-top: var(--space-5);
}

.analysis-prerequisite,
.analysis-outdated,
.analysis-run__failure,
.analysis-run__waiting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.analysis-prerequisite h3,
.analysis-outdated h3,
.analysis-run__failure h4 {
  font-weight: 750;
}

.analysis-prerequisite p,
.analysis-outdated p,
.analysis-run__failure p {
  margin-top: var(--space-1);
}

.analysis-run,
.analysis-command,
.analysis-result__hero,
.analysis-overview,
.analysis-requirements,
.analysis-insights,
.analysis-evidence,
.analysis-breakdown,
.analysis-history {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.analysis-run__header,
.analysis-result__heading,
.analysis-criterion__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.analysis-run__progress {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.analysis-run__progress progress {
  min-width: 0;
  flex: 1;
}

.analysis-run__progress strong {
  min-width: 3.25rem;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.analysis-run__connection,
.analysis-run__detail-link {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.analysis-run__waiting,
.analysis-run__failure {
  margin-top: var(--space-4);
}

.analysis-run__detail-link,
.analysis-result__run-link,
.analysis-history__selection a {
  display: inline-block;
  color: var(--color-brand-strong);
  font-weight: 680;
  text-decoration: underline;
  text-underline-offset: 0.16em;
}

.analysis-command {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--space-5);
  border-color: var(--color-brand-border);
}

.analysis-command__description {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.analysis-command > .button {
  justify-self: end;
}

.analysis-command__disclaimer {
  grid-column: 1 / -1;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.analysis-result {
  display: grid;
  gap: var(--space-5);
}

.analysis-outdated__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.analysis-outdated ul {
  margin-top: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
}

.analysis-result__heading h2 {
  margin-top: var(--space-1);
  font-size: clamp(1.35rem, 3vw, 1.75rem);
  font-weight: 780;
}

.analysis-result__heading p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-result__metrics {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-6);
}

.analysis-metric {
  display: grid;
  align-content: start;
  justify-items: start;
  grid-column: span 2;
  gap: var(--space-2);
  min-height: 8.5rem;
  padding: var(--space-4);
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface-subtle);
}

.analysis-metric--score {
  grid-column: span 4;
  background: var(--color-brand-soft);
}

.analysis-metric > span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 750;
}

.analysis-metric--score strong {
  color: var(--color-brand-strong);
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-variant-numeric: tabular-nums;
}

.analysis-metric:not(.analysis-metric--score) strong {
  font-size: 1.25rem;
  font-weight: 780;
}

.analysis-metric abbr {
  margin-left: var(--space-1);
  color: var(--color-brand-strong);
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 0.18em;
}

.analysis-metric--score p,
.analysis-metric--coverage p,
.analysis-result__summary,
.analysis-empty-copy,
.analysis-criterion > p,
.analysis-history__selection > p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.analysis-result__next {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.analysis-overview__heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.analysis-overview__heading > p {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-overview__statuses {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.analysis-overview__statuses article {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
}

.analysis-overview__statuses strong {
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
}

.analysis-overview__statuses article > span:last-child {
  grid-column: 1 / -1;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-overview__categories {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.analysis-overview__categories article,
.analysis-overview__categories article > div:first-child {
  display: grid;
  gap: var(--space-2);
}

.analysis-overview__categories article {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.analysis-overview__categories article > div:first-child {
  grid-template-columns: 1fr auto;
  align-items: baseline;
}

.analysis-overview__categories span,
.analysis-overview__categories small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-overview__bar {
  height: 0.5rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-border);
}

.analysis-overview__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand-strong);
}

.analysis-section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-5);
}

.analysis-section-heading > p {
  max-width: 30rem;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  text-align: right;
}

.analysis-requirements__summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-4);
  margin-top: var(--space-5);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
}

.analysis-requirements__summary span {
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-brand-strong);
  padding: 0.35rem 0.65rem;
  font-size: var(--font-size-xs);
  font-weight: 760;
  white-space: nowrap;
}

.analysis-requirements__summary p {
  color: var(--color-text-secondary);
  font-weight: 620;
  line-height: 1.75;
}

.analysis-requirements__details {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.analysis-requirement-group {
  border-top: 1px solid var(--color-border);
}

.analysis-requirement-group:last-child {
  border-bottom: 1px solid var(--color-border);
}

.analysis-requirement-group summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  min-height: 5.25rem;
  padding: var(--space-4) var(--space-2);
  cursor: pointer;
  list-style: none;
}

.analysis-requirement-group summary::-webkit-details-marker {
  display: none;
}

.analysis-requirement-group summary::after {
  grid-column: 2;
  grid-row: 1;
  align-self: end;
  margin-bottom: 0.1rem;
  color: var(--color-text-muted);
  content: '⌄';
  font-size: 1.25rem;
  transition: transform 160ms ease;
}

.analysis-requirement-group[open] summary::after {
  transform: rotate(180deg);
}

.analysis-requirement-group summary > div {
  min-width: 0;
}

.analysis-requirement-group summary > div > span {
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 760;
}

.analysis-requirement-group summary strong {
  display: block;
  margin-top: var(--space-1);
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-requirement-group__count {
  grid-column: 2;
  grid-row: 1;
  align-self: start;
  min-width: 2.75rem;
  border-radius: 999px;
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  padding: 0.3rem 0.55rem;
  font-size: var(--font-size-xs);
  font-weight: 730;
  text-align: center;
}

.analysis-requirement-group ul {
  display: grid;
  gap: 0;
  margin: 0 var(--space-2) var(--space-4);
  padding: 0;
  list-style: none;
}

.analysis-requirement-group li {
  position: relative;
  padding: var(--space-3) var(--space-3) var(--space-3) 2.25rem;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.analysis-requirement-group li::before {
  position: absolute;
  top: 1.1rem;
  left: var(--space-3);
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: var(--color-brand);
  content: '';
}

.analysis-requirement-group li:nth-child(odd) {
  background: var(--color-surface-subtle);
}

.analysis-requirement-group li small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-text-muted);
}

.analysis-insights__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.analysis-insight {
  min-width: 0;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
}

.analysis-insight--strength {
  background: var(--color-success-soft);
}

.analysis-insight--gap {
  background: var(--color-warning-soft);
}

.analysis-insight__heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
}

.analysis-insight__heading > span {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 50%;
  background: rgb(255 255 255 / 72%);
  font-size: 1.1rem;
  font-weight: 850;
}

.analysis-insight--strength .analysis-insight__heading > span,
.analysis-insight--strength .analysis-insight__heading small {
  color: var(--color-success-strong);
}

.analysis-insight--gap .analysis-insight__heading > span,
.analysis-insight--gap .analysis-insight__heading small {
  color: var(--color-warning-strong);
}

.analysis-insight__heading small {
  font-size: var(--font-size-xs);
  font-weight: 720;
}

.analysis-insight__heading h4 {
  margin-top: 0.1rem;
  font-size: 1.125rem;
  font-weight: 780;
}

.analysis-insight__heading > strong {
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
}

.analysis-insight ol {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.analysis-insight li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgb(255 255 255 / 68%);
}

.analysis-insight li > span {
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 760;
}

.analysis-insight li p {
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.analysis-empty-copy {
  margin-top: var(--space-4);
}

.analysis-evidence__notice {
  margin-top: var(--space-4);
}

.analysis-evidence__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.analysis-evidence__item {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
}

.analysis-evidence__item strong,
.analysis-evidence__item span,
.analysis-evidence__item small {
  overflow-wrap: anywhere;
}

.analysis-evidence__item span,
.analysis-evidence__item small {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.analysis-evidence__item--changed {
  border-color: var(--color-warning-border);
  background: var(--color-warning-soft);
}

.analysis-evidence__state--changed {
  color: var(--color-warning-strong) !important;
  font-weight: 700;
}

.analysis-breakdown__filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.analysis-breakdown__filters button {
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: 0.55rem 0.9rem;
  font-size: var(--font-size-sm);
  font-weight: 680;
}

.analysis-breakdown__filters button:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.analysis-breakdown__filters button span {
  display: grid;
  min-width: 1.35rem;
  height: 1.35rem;
  place-items: center;
  border-radius: 999px;
  background: var(--color-surface-subtle);
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-variant-numeric: tabular-nums;
}

.analysis-breakdown__filter--active {
  border-color: var(--color-brand) !important;
  background: var(--color-brand) !important;
  color: #fff !important;
}

.analysis-breakdown__filter--active span {
  background: rgb(255 255 255 / 18%) !important;
  color: #fff !important;
}

.analysis-breakdown__list {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.analysis-criterion {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-left: 0.25rem solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.analysis-criterion[data-match-level='MATCHED'] {
  border-left-color: var(--color-success);
}

.analysis-criterion[data-match-level='PARTIAL'] {
  border-left-color: var(--color-warning);
}

.analysis-criterion[data-match-level='MISSING'] {
  border-left-color: var(--color-danger);
}

.analysis-breakdown__empty {
  padding: var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-text-muted);
  text-align: center;
}

.analysis-criterion__header span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.analysis-criterion__header h4 {
  margin-top: var(--space-1);
  font-weight: 730;
  overflow-wrap: anywhere;
}

.analysis-criterion__score {
  margin-top: var(--space-3);
  color: var(--color-brand-strong) !important;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.analysis-criterion__detail {
  margin-top: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
}

.analysis-criterion__detail summary {
  color: var(--color-brand-strong);
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.analysis-criterion__detail > p {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.analysis-criterion__evidence {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.analysis-criterion__evidence li {
  display: grid;
  gap: 0.125rem;
  max-width: 100%;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  padding: 0.25rem 0.625rem;
  font-size: var(--font-size-xs);
  overflow-wrap: anywhere;
}

.analysis-criterion__evidence li small {
  font-size: 0.6875rem;
}

.analysis-criterion__evidence-item--changed {
  background: var(--color-warning-soft) !important;
  color: var(--color-warning-strong) !important;
}

.analysis-history__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  cursor: pointer;
  list-style: none;
}

.analysis-history__summary::-webkit-details-marker {
  display: none;
}

.analysis-history__summary::after {
  color: var(--color-text-muted);
  content: '⌄';
  font-size: 1.4rem;
  transition: transform 160ms ease;
}

.analysis-history[open] .analysis-history__summary::after {
  transform: rotate(180deg);
}

.analysis-history__summary > div > p:last-child {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-history__summary > span {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.analysis-history__layout {
  display: grid;
  grid-template-columns: minmax(11rem, 0.65fr) minmax(0, 1.35fr);
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.analysis-history__list {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.analysis-history__button {
  width: 100%;
}

.analysis-history__list button {
  display: grid;
  width: 100%;
  min-height: 2.75rem;
  gap: var(--space-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: var(--space-3);
  text-align: left;
}

.analysis-history__list button:hover,
.analysis-history__button--selected {
  border-color: var(--color-brand-border) !important;
  background: var(--color-brand-soft) !important;
  color: var(--color-brand-strong) !important;
}

.analysis-history__list small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-history__selection {
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
}

.analysis-history__selection h4 {
  margin-top: var(--space-1);
  font-size: 1.125rem;
  font-weight: 750;
}

.analysis-history__selection dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.analysis-history__selection dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-history__selection dd {
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
}

.analysis-history__selection > p,
.analysis-history__selection > a {
  margin-top: var(--space-4);
}

.analysis-history-state {
  margin-top: var(--space-5);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 64rem) {
  .analysis-evidence__grid {
    grid-template-columns: 1fr;
  }

  .analysis-command {
    grid-template-columns: 1fr;
  }

  .analysis-command__controls {
    align-items: flex-start;
  }

  .analysis-overview__statuses,
  .analysis-overview__categories {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analysis-result__metrics {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .analysis-metric,
  .analysis-metric--score {
    grid-column: span 2;
  }
}

@media (max-width: 40rem) {
  .analysis-prerequisite,
  .analysis-outdated,
  .analysis-run__failure,
  .analysis-run__waiting,
  .analysis-result__heading,
  .analysis-section-heading,
  .analysis-history__summary,
  .analysis-criterion__header {
    align-items: stretch;
    flex-direction: column;
  }

  .analysis-result__metrics,
  .analysis-insights__grid,
  .analysis-overview__statuses,
  .analysis-overview__categories,
  .analysis-history__layout,
  .analysis-history__selection dl {
    grid-template-columns: 1fr;
  }

  .analysis-metric,
  .analysis-metric--score {
    grid-column: auto;
    min-height: 0;
  }

  .analysis-section-heading > p {
    text-align: left;
  }

  .analysis-requirements__summary {
    grid-template-columns: 1fr;
  }

  .analysis-requirement-group summary strong {
    white-space: normal;
  }

  .analysis-history__summary > span {
    margin-left: 0;
  }

  .analysis-context-header {
    align-items: stretch;
    flex-direction: column;
  }

  .analysis-overview__heading {
    align-items: stretch;
    flex-direction: column;
  }

  .analysis-command > .button,
  .analysis-prerequisite .button,
  .analysis-outdated .button,
  .analysis-run__failure .button {
    width: 100%;
    justify-self: stretch;
  }
}
</style>
