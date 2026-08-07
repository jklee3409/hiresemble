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
import type { FitCriterionCategory, MatchLevel } from '@/shared/api/jobContracts'
import type { JobAnalysisListParams } from '@/shared/api/jobApi'
import { getProfile } from '@/shared/api/profileApi'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const SCORE_DISCLAIMER =
  '적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.'
const REMOVED_SUMMARY_SENTENCE =
  /확인 가능한 근거 유형만 반영했으며,\s*일부 요건은 추가 확인이 필요합니다\.?/g
const ACTIVE_RUN_STATUSES: AgentRunStatus[] = ['QUEUED', 'RUNNING', 'WAITING_USER']
const TERMINAL_FAILURE_STATUSES: AgentRunStatus[] = ['FAILED', 'CANCELLED', 'INTERRUPTED']
const CRITERION_PAGE_SIZE = 5
type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'
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
const criterionFilter = ref<MatchLevel | 'ALL'>('ALL')
const criterionPage = ref(0)
const connectionState = ref<AgentRunConnectionState>('connecting')

const job = useJobDetailQuery(userId, jobId)
const profile = useQuery({
  queryKey: computed(() => profileQueryKeys.profile(userId.value)),
  queryFn: getProfile,
  enabled: computed(() => userId.value !== ''),
})
const latestAnalysis = useLatestJobAnalysisQuery(userId, jobId)
const historyFilters = computed<JobAnalysisListParams>(() => ({
  page: 0,
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

watch(criterionFilter, () => {
  criterionPage.value = 0
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
const selectedHistory = computed(() => latestAnalysis.data.value ?? null)
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
const criterionTotalPages = computed(() =>
  Math.ceil(filteredCriteria.value.length / CRITERION_PAGE_SIZE),
)
const paginatedCriteria = computed(() => {
  const start = criterionPage.value * CRITERION_PAGE_SIZE
  return filteredCriteria.value.slice(start, start + CRITERION_PAGE_SIZE)
})
const criterionRangeLabel = computed(() => {
  const total = filteredCriteria.value.length
  if (total === 0) return '0개 조건'
  const start = criterionPage.value * CRITERION_PAGE_SIZE + 1
  const end = Math.min(start + CRITERION_PAGE_SIZE - 1, total)
  return `총 ${total}개 중 ${start}–${end}`
})

watch(criterionTotalPages, (totalPages) => {
  criterionPage.value = Math.min(criterionPage.value, Math.max(totalPages - 1, 0))
})

const displayedAnalysisSummary = computed(() =>
  (latestAnalysis.data.value?.analysisSummary ?? '')
    .replace(REMOVED_SUMMARY_SENTENCE, '')
    .replace(/\s{2,}/g, ' ')
    .trim(),
)
const decisionHeading = computed(() => {
  const analysis = latestAnalysis.data.value
  if (analysis === undefined) return ''
  const score = formatFitScore(analysis.fitScore)
  return {
    ELIGIBLE: `적합도 ${score}, 지원을 준비할 만한 강점이 보여요.`,
    CONDITIONAL: `적합도 ${score}, 지원 전에 확인할 조건이 있어요.`,
    INELIGIBLE: `적합도 ${score}, 필수 조건 중 추가 확인이 필요해요.`,
    UNKNOWN: `적합도 ${score}, 지원 조건을 판단할 정보가 더 필요해요.`,
  }[analysis.eligibility]
})
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
const matchDistributionLabel = computed(() =>
  matchOverview.value.map((item) => `${MATCH_LEVEL_LABELS[item.level]} ${item.count}개`).join(', '),
)
/*
 * 요건 하나를 캡슐 하나로 보여 준다. 길이 비율이 아니라 "몇 개 중 몇 개"를 세어 읽게 한다.
 * 캡슐은 언제나 일치 → 일부 일치 → 확인되지 않음 → 판단 정보 부족 순으로 늘어놓아
 * 색을 구분하지 못해도 위치만으로 어디까지가 무엇인지 읽을 수 있게 한다.
 */
const MATCH_LEVEL_ORDER = ['MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN'] as const

const matchCapsules = computed(() =>
  (latestAnalysis.data.value?.scoreBreakdown ?? [])
    .map((criterion, index) => ({
      key: `${criterion.category}/${criterion.criterion}/${index}`,
      level: criterion.matchLevel,
      label: `${FIT_CRITERION_CATEGORY_LABELS[criterion.category]} · ${criterion.criterion} — ${
        MATCH_LEVEL_LABELS[criterion.matchLevel]
      }`,
    }))
    .sort(
      (left, right) =>
        MATCH_LEVEL_ORDER.indexOf(left.level) - MATCH_LEVEL_ORDER.indexOf(right.level),
    ),
)
const matchedRatio = computed(() => {
  const total = latestAnalysis.data.value?.scoreBreakdown.length ?? 0
  const matched = matchOverview.value.find((item) => item.level === 'MATCHED')?.count ?? 0
  return { total, matched, percent: total > 0 ? Math.round((matched / total) * 100) : null }
})

// 적합도 게이지: 중심 (100,100), 270° 아크. 외부 r=76(적합도), 내부 r=62(분석 커버리지).
const GAUGE_SCORE_ARC = 358.14
const GAUGE_COVERAGE_ARC = 292.17
const MATCH_ICONS = {
  MATCHED: 'check',
  PARTIAL: 'half',
  MISSING: 'cross',
  UNKNOWN: 'question',
} as const

const fitScoreValue = computed(() => {
  const value = latestAnalysis.data.value?.fitScore
  return value === null || value === undefined ? null : roundToFive(value)
})
const coverageValue = computed(() => {
  const value = latestAnalysis.data.value?.analysisCoverage
  return value === null || value === undefined ? null : roundToFive(value)
})
const gaugeScoreOffset = computed(() =>
  fitScoreValue.value === null ? null : GAUGE_SCORE_ARC * (1 - fitScoreValue.value / 100),
)
const gaugeCoverageOffset = computed(() =>
  coverageValue.value === null ? null : GAUGE_COVERAGE_ARC * (1 - coverageValue.value / 100),
)
const gaugeLabel = computed(() => {
  const score = fitScoreValue.value === null ? '산정하지 못함' : `${String(fitScoreValue.value)}점`
  const coverage = coverageValue.value === null ? '기록 없음' : `${String(coverageValue.value)}%`
  return `적합도 ${score}, 분석 커버리지 ${coverage}. ${SCORE_DISCLAIMER}`
})

// 적합도 추이: x축은 analysisVersion 정수 축이며 날짜 축으로 바꾸지 않는다(간격 불균등).
const trendChart = computed(() => {
  const items = (history.data.value?.items ?? [])
    .filter((item) => item.fitScore !== null)
    .slice()
    .sort((left, right) => left.analysisVersion - right.analysisVersion)
  if (items.length < 2) return null

  const scores = items.map((item) => roundToFive(item.fitScore ?? 0))
  const bottom = Math.max(0, Math.floor((Math.min(...scores) - 5) / 10) * 10)
  const top = Math.min(100, Math.max(bottom + 20, Math.ceil((Math.max(...scores) + 5) / 10) * 10))
  const span = top - bottom
  const points = items.map((item, index) => ({
    id: item.id,
    version: item.analysisVersion,
    score: scores[index] ?? 0,
    x: 40 + (index * 270) / (items.length - 1),
    y: 110 - (((scores[index] ?? 0) - bottom) / span) * 90,
  }))
  const path = points.map((point) => `${String(point.x)} ${String(point.y)}`).join('L')
  const first = points[0]
  const last = points[points.length - 1]
  if (first === undefined || last === undefined) return null

  return {
    points,
    last,
    line: `M${path}`,
    area: `M${path}L${String(last.x)} 110L${String(first.x)} 110Z`,
    axis: [top, Math.round((top + bottom) / 2), bottom],
    label: `적합도 추이: ${points
      .map((point) => `${String(point.version)}차 분석 ${String(point.score)}점`)
      .join(', ')}`,
  }
})
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
  if (latestAnalysis.data.value) return false
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

function runTone(value: AgentRunStatus): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return RUN_TONES[value]
}

function matchTone(value: MatchLevel): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return MATCH_TONES[value]
}

function matchIcon(value: MatchLevel): (typeof MATCH_ICONS)[MatchLevel] {
  return MATCH_ICONS[value]
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
              class="button"
              :class="latestAnalysis.data.value ? 'button--secondary' : 'button--primary'"
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
        <details v-if="latestAnalysis.data.value.analysisOutdated" class="analysis-outdated">
          <summary>
            <strong>분석 이후 정보가 변경됐어요.</strong>
            <span>{{ latestAnalysis.data.value.outdatedReasons.length }}개 변경</span>
          </summary>
          <div class="analysis-outdated__body">
            <ul>
              <li v-for="reason in latestAnalysis.data.value.outdatedReasons" :key="reason">
                {{ OUTDATED_REASON_LABELS[reason] }}
              </li>
            </ul>
            <p>아래 기존 결과는 그대로 유지돼요. 필요할 때 현재 정보로 재분석해 주세요.</p>
          </div>
        </details>

        <section class="analysis-result__hero">
          <div class="analysis-result__heading">
            <div class="analysis-result__heading-copy">
              <p class="section-kicker">지원 판단</p>
              <h2 id="analysis-result-heading">{{ decisionHeading }}</h2>
            </div>
            <div class="analysis-result__meta">
              <span class="analysis-result__desktop-date">
                {{ formatAnalysisInstant(latestAnalysis.data.value.createdAt) }}
              </span>
              <span class="analysis-result__mobile-meta">
                커버리지 {{ formatCoverage(latestAnalysis.data.value.analysisCoverage) }} ·
                {{ formatAnalysisInstant(latestAnalysis.data.value.createdAt) }}
              </span>
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
          </div>
          <div class="analysis-result__decision">
            <div class="analysis-result__copy">
              <p v-if="displayedAnalysisSummary" class="analysis-result__summary">
                {{ displayedAnalysisSummary }}
              </p>
              <p v-else class="analysis-result__summary">
                등록한 정보와 공고 요구사항을 비교한 최신 결과예요.
              </p>
              <div class="analysis-result__actions">
                <RouterLink
                  class="button button--primary"
                  :to="{ name: 'job-cover-letter', params: { jobId } }"
                >
                  자기소개서 준비하기
                  <AppIcon name="arrow-right" />
                </RouterLink>
                <nav aria-label="분석 보조 행동">
                  <RouterLink :to="{ name: 'profile-basic' }">내 정보 보완</RouterLink>
                  <RouterLink :to="{ name: 'job-overview', params: { jobId } }">
                    공고 내용 수정
                  </RouterLink>
                  <button
                    type="button"
                    :disabled="commandUnavailable"
                    @click="requestAnalysis(true)"
                  >
                    {{ submissionPending ? '분석 진행 중…' : '다시 분석하기' }}
                  </button>
                </nav>
              </div>
            </div>
            <dl class="analysis-result__metrics" aria-label="지원 판단 요약">
              <div class="analysis-metric analysis-metric--score">
                <dt>적합도 <abbr :title="SCORE_DISCLAIMER">안내</abbr></dt>
                <dd>
                  <svg
                    v-if="gaugeScoreOffset !== null"
                    class="analysis-gauge"
                    viewBox="0 0 200 200"
                    role="img"
                    :aria-label="gaugeLabel"
                  >
                    <path
                      class="analysis-gauge__arc analysis-gauge__track"
                      d="M46.26 153.74A76 76 0 1 1 153.74 153.74"
                      stroke-width="14"
                    />
                    <path
                      class="analysis-gauge__arc analysis-gauge__score"
                      d="M46.26 153.74A76 76 0 1 1 153.74 153.74"
                      stroke-width="14"
                      stroke-dasharray="358.14"
                      :stroke-dashoffset="gaugeScoreOffset"
                    />
                    <template v-if="gaugeCoverageOffset !== null">
                      <path
                        class="analysis-gauge__arc analysis-gauge__track analysis-gauge__coverage-track"
                        d="M56.16 143.84A62 62 0 1 1 143.84 143.84"
                        stroke-width="6"
                      />
                      <path
                        class="analysis-gauge__arc analysis-gauge__coverage"
                        d="M56.16 143.84A62 62 0 1 1 143.84 143.84"
                        stroke-width="6"
                        stroke-dasharray="292.17"
                        :stroke-dashoffset="gaugeCoverageOffset"
                      />
                    </template>
                    <!-- prettier-ignore -->
                    <text x="100" y="118" text-anchor="middle"><tspan class="analysis-gauge__number">{{ fitScoreValue }}</tspan><tspan class="analysis-gauge__unit">점</tspan></text>
                    <text
                      v-if="coverageValue !== null"
                      class="analysis-gauge__caption"
                      x="100"
                      y="143"
                      text-anchor="middle"
                    >
                      커버리지 {{ coverageValue }}%
                    </text>
                  </svg>
                  <span v-else class="analysis-metric__fallback">
                    {{ formatFitScore(latestAnalysis.data.value.fitScore) }}
                  </span>
                </dd>
              </div>
              <div class="analysis-metric">
                <dt><AppIcon name="scale" />지원 가능성</dt>
                <dd :data-eligibility="latestAnalysis.data.value.eligibility">
                  {{ ELIGIBILITY_LABELS[latestAnalysis.data.value.eligibility] }}
                </dd>
              </div>
              <div class="analysis-metric">
                <dt><AppIcon name="shield" />분석 커버리지</dt>
                <dd>{{ formatCoverage(latestAnalysis.data.value.analysisCoverage) }}</dd>
              </div>
            </dl>
          </div>
          <details class="analysis-result__disclaimer">
            <summary>점수는 어떻게 계산되나요?</summary>
            <p>{{ SCORE_DISCLAIMER }}</p>
          </details>
        </section>

        <section class="analysis-overview" aria-labelledby="match-overview-heading">
          <div class="analysis-overview__heading">
            <h3 id="match-overview-heading" class="section-title">
              <AppIcon name="chart" />요건 매칭 현황
            </h3>
            <span>총 {{ latestAnalysis.data.value.scoreBreakdown.length }}개 기준</span>
          </div>
          <p class="analysis-overview__headline">
            <strong>{{ matchedRatio.matched }}</strong>
            <span>/ {{ matchedRatio.total }}개 요건이 내 정보와 일치해요</span>
            <em v-if="matchedRatio.percent !== null">{{ matchedRatio.percent }}%</em>
          </p>
          <div
            class="analysis-overview__capsules"
            role="img"
            :aria-label="`요건 분포: ${matchDistributionLabel}`"
          >
            <span
              v-for="capsule in matchCapsules"
              :key="capsule.key"
              :data-match-level="capsule.level"
              :title="capsule.label"
            />
          </div>
          <dl class="analysis-overview__statuses">
            <div
              v-for="item in matchOverview"
              :key="item.level"
              :data-match-level="item.level"
              :data-empty="item.count === 0"
            >
              <dt>
                <span class="analysis-overview__dot">
                  <AppIcon :name="matchIcon(item.level)" />
                </span>
                {{ MATCH_LEVEL_LABELS[item.level] }}
              </dt>
              <dd>
                <strong>{{ item.count }}</strong
                >개
              </dd>
            </div>
          </dl>
          <ul class="analysis-overview__categories analysis-overview__categories--desktop">
            <li v-for="item in categoryOverview" :key="item.category">
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
            </li>
          </ul>
          <details class="analysis-overview__categories-mobile">
            <summary>
              <span>카테고리별 충족도</span>
              <small>{{ categoryOverview.length }}개 영역</small>
            </summary>
            <ul class="analysis-overview__categories">
              <li v-for="item in categoryOverview" :key="item.category">
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
              </li>
            </ul>
          </details>
        </section>

        <section class="analysis-requirements" aria-labelledby="job-summary-heading">
          <div class="analysis-section-heading">
            <h3 id="job-summary-heading" class="section-title">
              <AppIcon name="documents" />공고 핵심
            </h3>
          </div>
          <div v-if="displayedAnalysisSummary" class="analysis-requirements__summary">
            <span>핵심 요약</span>
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

        <section class="analysis-insights" aria-labelledby="analysis-insights-heading">
          <div class="analysis-section-heading">
            <h3 id="analysis-insights-heading" class="section-title">
              <AppIcon name="spark" />강점과 보완 포인트
            </h3>
          </div>
          <div class="analysis-insights__grid">
            <article class="analysis-insight analysis-insight--strength">
              <div class="analysis-insight__heading">
                <div>
                  <h4>내 강점</h4>
                  <small>자기소개서에서 먼저 강조하세요.</small>
                </div>
                <strong>{{ latestAnalysis.data.value.strengths.length }}개</strong>
              </div>
              <ul v-if="latestAnalysis.data.value.strengths.length">
                <li
                  v-for="(strength, index) in latestAnalysis.data.value.strengths"
                  :key="strength"
                  :class="{ 'analysis-insight__mobile-extra': index > 0 }"
                >
                  <AppIcon name="check" />
                  <p>{{ strength }}</p>
                </li>
              </ul>
              <details
                v-if="latestAnalysis.data.value.strengths.length > 1"
                class="analysis-insight__mobile-more"
              >
                <summary>{{ latestAnalysis.data.value.strengths.length - 1 }}개 더 보기</summary>
                <ul>
                  <li
                    v-for="strength in latestAnalysis.data.value.strengths.slice(1)"
                    :key="`mobile/${strength}`"
                  >
                    <AppIcon name="check" />
                    <p>{{ strength }}</p>
                  </li>
                </ul>
              </details>
              <p v-else class="analysis-empty-copy">확인한 경험에서 찾은 강점이 아직 없어요.</p>
            </article>
            <article class="analysis-insight analysis-insight--gap">
              <div class="analysis-insight__heading">
                <div>
                  <h4>보완 포인트</h4>
                  <small>지원 전에 확인하거나 경험을 보강하세요.</small>
                </div>
                <strong>{{ latestAnalysis.data.value.gaps.length }}개</strong>
              </div>
              <ul v-if="latestAnalysis.data.value.gaps.length">
                <li
                  v-for="(gap, index) in latestAnalysis.data.value.gaps"
                  :key="gap"
                  :class="{ 'analysis-insight__mobile-extra': index > 0 }"
                >
                  <AppIcon name="lift" />
                  <p>{{ gap }}</p>
                </li>
              </ul>
              <details
                v-if="latestAnalysis.data.value.gaps.length > 1"
                class="analysis-insight__mobile-more"
              >
                <summary>{{ latestAnalysis.data.value.gaps.length - 1 }}개 더 보기</summary>
                <ul>
                  <li v-for="gap in latestAnalysis.data.value.gaps.slice(1)" :key="`mobile/${gap}`">
                    <AppIcon name="lift" />
                    <p>{{ gap }}</p>
                  </li>
                </ul>
              </details>
              <p v-else class="analysis-empty-copy">현재 확인된 보완 포인트가 없어요.</p>
            </article>
          </div>
        </section>

        <section class="analysis-evidence">
          <div class="analysis-section-heading">
            <h3 class="section-title"><AppIcon name="evidence" />점수에 활용한 경험</h3>
          </div>
          <p
            v-if="hasEvidenceWithChangedState"
            class="analysis-evidence__notice alert alert--warning"
            role="status"
          >
            이 결과에 사용한 경험 중 현재 상태가 달라진 항목이 있어요. 저장된 결과와 점수는 그대로
            두고, 다시 분석할 때는 지금 확인된 경험만 사용해요.
          </p>
          <ul
            v-if="latestAnalysis.data.value.matchedEvidenceRefs.length"
            class="analysis-evidence__list"
          >
            <li
              v-for="evidence in latestAnalysis.data.value.matchedEvidenceRefs"
              :key="evidence.id"
              class="analysis-evidence__item"
              :class="{
                'analysis-evidence__item--changed': !isCurrentlyVerifiedEvidence(evidence),
              }"
            >
              <div>
                <strong>{{ evidence.title }}</strong>
                <span>{{ evidence.evidenceCategory }}</span>
              </div>
              <small
                :class="{
                  'analysis-evidence__state--changed': !isCurrentlyVerifiedEvidence(evidence),
                }"
              >
                {{ evidenceCurrentStateLabel(evidence) }}
              </small>
            </li>
          </ul>
          <p v-else class="analysis-empty-copy">점수에 연결된 확인한 경험이 없어요.</p>
        </section>

        <section class="analysis-breakdown">
          <div class="analysis-section-heading analysis-breakdown__heading">
            <h3 class="section-title"><AppIcon name="target" />조건별 확인 결과</h3>
            <span class="analysis-breakdown__range">{{ criterionRangeLabel }}</span>
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
              v-for="criterion in paginatedCriteria"
              :key="`${criterion.category}/${criterion.criterion}`"
              class="analysis-criterion"
              :data-match-level="criterion.matchLevel"
            >
              <div class="analysis-criterion__header">
                <span class="analysis-criterion__mark" :data-match-level="criterion.matchLevel">
                  <AppIcon :name="matchIcon(criterion.matchLevel)" />
                </span>
                <div>
                  <span>{{ FIT_CRITERION_CATEGORY_LABELS[criterion.category] }}</span>
                  <h4>{{ criterion.criterion }}</h4>
                </div>
                <StatusBadge
                  :label="MATCH_LEVEL_LABELS[criterion.matchLevel]"
                  :tone="matchTone(criterion.matchLevel)"
                />
              </div>
              <div class="analysis-criterion__meter" aria-hidden="true">
                <span :style="{ width: `${(criterion.score / (criterion.weight || 1)) * 100}%` }" />
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
          <PaginationNav
            v-if="criterionTotalPages > 1"
            class="analysis-breakdown__pagination"
            :page="criterionPage"
            :total-pages="criterionTotalPages"
            label="조건별 확인 결과 페이지"
            @change="criterionPage = $event"
          />
        </section>
      </article>

      <details
        v-if="history.data.value?.items.length"
        class="analysis-history"
        aria-labelledby="analysis-history-heading"
      >
        <summary class="analysis-history__summary">
          <div>
            <h3 id="analysis-history-heading" class="section-title">
              <AppIcon name="history" />분석 결과 기록
            </h3>
            <p>공고나 내 정보가 바뀌기 전 결과도 다시 확인할 수 있어요.</p>
          </div>
          <span>총 {{ history.data.value.totalElements }}개</span>
        </summary>
        <svg
          v-if="trendChart"
          class="analysis-trend"
          viewBox="0 0 340 150"
          role="img"
          :aria-label="trendChart.label"
        >
          <defs>
            <linearGradient id="analysis-trend-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="var(--chart-brand)" stop-opacity="0.14" />
              <stop offset="100%" stop-color="var(--chart-brand)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <g class="analysis-trend__grid">
            <line x1="34" y1="20" x2="330" y2="20" />
            <line x1="34" y1="65" x2="330" y2="65" />
            <line x1="34" y1="110" x2="330" y2="110" />
          </g>
          <g class="analysis-trend__axis" text-anchor="end">
            <text
              v-for="(value, index) in trendChart.axis"
              :key="value"
              x="26"
              :y="24 + index * 45"
            >
              {{ value }}
            </text>
          </g>
          <path class="analysis-trend__area" :d="trendChart.area" />
          <path class="analysis-trend__line" :d="trendChart.line" />
          <circle
            v-for="point in trendChart.points"
            :key="point.id"
            class="analysis-trend__dot"
            :class="{ 'analysis-trend__dot--last': point.id === trendChart.last.id }"
            :cx="point.x"
            :cy="point.y"
            :r="point.id === trendChart.last.id ? 6 : 5"
          />
          <text
            class="analysis-trend__value"
            :x="trendChart.last.x"
            :y="trendChart.last.y - 14"
            text-anchor="end"
          >
            {{ trendChart.last.score }}점
          </text>
          <g class="analysis-trend__axis" text-anchor="middle">
            <text v-for="point in trendChart.points" :key="point.id" :x="point.x" y="130">
              {{ point.version }}차
            </text>
          </g>
        </svg>
        <div class="analysis-history__layout">
          <article v-if="selectedHistory" class="analysis-history__selection">
            <h4>현재 분석 결과</h4>
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
              분석 과정
            </RouterLink>
          </article>
        </div>
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

      <section
        v-if="showAnalysisCommand"
        class="analysis-command"
        aria-labelledby="analysis-command-heading"
      >
        <div>
          <h3 id="analysis-command-heading" class="section-title">
            {{ latestAnalysis.data.value ? '최신 정보로 다시 분석' : '분석 다시 시도' }}
          </h3>
          <p class="analysis-command__description">
            현재 공고 내용과 내가 확인한 경험을 기준으로 새 결과를 만들어요.
          </p>
        </div>
        <button
          type="button"
          class="button"
          :class="latestAnalysis.data.value ? 'button--secondary' : 'button--primary'"
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
    </template>
  </section>
</template>

<style scoped>
/*
 * 공고 분석 결과 화면.
 * 결과는 카드를 반복하지 않고 하나의 report surface 안에서 구분선과 여백으로 나눈다.
 * 상태 색은 항상 아이콘·한글 라벨과 함께 쓰고 색만으로 의미를 전달하지 않는다.
 * 설계 근거는 docs/design/job-analysis-page-design-guide.html에 있다.
 */

.job-analysis {
  min-width: 0;
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
.analysis-history,
.analysis-history-state {
  margin-top: var(--space-5);
}

/* ---------------------------------------------------------------- 공통 */

.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.section-title .icon {
  width: 1.25rem;
  height: 1.25rem;
  flex: 0 0 auto;
  color: var(--color-brand);
}

.analysis-section-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.analysis-empty-copy {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

/* ------------------------------------------------- 선행 조건과 진행 상태 */

.analysis-prerequisite,
.analysis-run__failure,
.analysis-run__waiting {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.analysis-prerequisite h3,
.analysis-run__failure h4 {
  font-weight: 750;
}

.analysis-prerequisite p,
.analysis-run__failure p {
  margin-top: var(--space-1);
}

.analysis-run {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.analysis-run__header {
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

/* ------------------------------------------------------- 리포트 컨테이너 */

.analysis-result {
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
  overflow: hidden;
}

.analysis-outdated,
.analysis-result__hero,
.analysis-overview,
.analysis-requirements,
.analysis-insights,
.analysis-evidence,
.analysis-breakdown {
  padding: clamp(var(--space-5), 3vw, var(--space-8));
}

.analysis-outdated + *,
.analysis-result__hero + *,
.analysis-overview + *,
.analysis-requirements + *,
.analysis-insights + *,
.analysis-evidence + * {
  border-top: 1px solid var(--color-border);
}

/* 정보 변경 안내 */

.analysis-outdated > summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  cursor: pointer;
  color: var(--color-brand-strong);
}

.analysis-outdated > summary strong {
  font-weight: 750;
}

.analysis-outdated > summary span {
  border-radius: var(--radius-pill);
  background: var(--color-brand-soft);
  padding: 0.125rem 0.5rem;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.analysis-outdated__body {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.analysis-outdated__body ul {
  margin-bottom: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
}

/* ------------------------------------------------------------ 지원 판단 */

.analysis-result__hero {
  position: relative;
  overflow: hidden;
}

.analysis-result__hero::before {
  content: '';
  position: absolute;
  inset: -55% -18% auto auto;
  width: 30rem;
  height: 30rem;
  background: radial-gradient(
    closest-side,
    rgb(49 87 255 / 13%),
    rgb(116 138 255 / 5%) 55%,
    transparent 72%
  );
  pointer-events: none;
}

.analysis-result__heading,
.analysis-criterion__header {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.analysis-result__heading h2 {
  margin-top: var(--space-1);
  max-width: 40rem;
  font-size: clamp(1.35rem, 3vw, 1.9rem);
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: var(--color-ink-title);
}

.analysis-result__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-result__mobile-meta {
  display: none;
}

.analysis-result__decision {
  position: relative;
  display: grid;
  gap: var(--space-6);
  margin-top: var(--space-5);
}

.analysis-result__copy p {
  max-width: 46rem;
  color: var(--color-text-secondary);
}

.analysis-result__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3) var(--space-4);
  margin-top: var(--space-6);
}

.analysis-result__actions nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3) var(--space-4);
}

.analysis-result__actions nav a,
.analysis-result__actions nav button {
  border: 0;
  background: none;
  padding: 0;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: underline;
  text-underline-offset: 0.16em;
}

.analysis-result__actions nav button:disabled {
  color: var(--color-text-muted);
  text-decoration: none;
}

.analysis-result__disclaimer {
  position: relative;
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-result__disclaimer summary {
  width: fit-content;
  cursor: pointer;
  font-weight: 700;
}

.analysis-result__disclaimer p {
  margin-top: var(--space-2);
  max-width: 52rem;
}

/* 요약 지표 */

.analysis-result__metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(11rem, 100%), 1fr));
  gap: var(--space-3);
}

.analysis-metric {
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4) var(--space-5);
}

.analysis-metric dt {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.analysis-metric dt .icon {
  width: 0.9375rem;
  height: 0.9375rem;
}

.analysis-metric dt abbr {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-decoration: underline dotted;
  text-underline-offset: 0.16em;
  cursor: help;
}

.analysis-metric dd {
  margin-top: var(--space-2);
  font-size: var(--font-size-lg);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--color-ink-title);
}

.analysis-metric--score {
  display: grid;
  justify-items: center;
  grid-column: 1 / -1;
  background: none;
  padding: 0;
}

.analysis-metric--score dt {
  justify-content: center;
}

.analysis-metric--score dd {
  margin-top: 0;
}

.analysis-metric__fallback {
  font-size: var(--font-size-xl);
}

/* 적합도 게이지 */

.analysis-gauge {
  display: block;
  width: 12.5rem;
  height: 12.5rem;
}

.analysis-gauge__arc {
  fill: none;
  stroke-linecap: round;
}

.analysis-gauge__track {
  stroke: var(--chart-track);
}

/*
 * fill-mode는 backwards만 쓴다. from 키프레임만 선언한 애니메이션에 forwards를 주면
 * 종료 후에도 0 상태가 유지되어 값이 보이지 않는다.
 */
.analysis-gauge__score {
  --analysis-arc-full: 358.14;

  stroke: var(--chart-brand);
  animation: analysis-arc 900ms cubic-bezier(0.2, 0, 0, 1) backwards;
}

.analysis-gauge__coverage {
  --analysis-arc-full: 292.17;

  stroke: var(--hs-blue-300);
  animation: analysis-arc 900ms 120ms cubic-bezier(0.2, 0, 0, 1) backwards;
}

.analysis-gauge__number {
  font-size: 3.25rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  fill: var(--color-ink-title);
  font-variant-numeric: tabular-nums;
}

.analysis-gauge__unit {
  font-size: 1.125rem;
  font-weight: 750;
  fill: var(--color-text-muted);
}

.analysis-gauge__caption {
  font-size: 0.8125rem;
  font-weight: 700;
  fill: var(--color-brand-strong);
}

@keyframes analysis-arc {
  from {
    stroke-dashoffset: var(--analysis-arc-full);
  }
}

/* ------------------------------------------------------- 요건 매칭 현황 */

.analysis-overview__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.analysis-overview__heading span {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

/* 몇 개 중 몇 개가 맞았는지를 문장보다 먼저 읽는다. */
.analysis-overview__headline {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.analysis-overview__headline strong {
  color: var(--chart-matched-strong);
  font-size: var(--font-size-3xl);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
  line-height: 1;
}

.analysis-overview__headline span {
  color: var(--color-muted-strong);
  font-size: var(--font-size-md);
  font-weight: 700;
}

.analysis-overview__headline em {
  margin-left: auto;
  border-radius: var(--radius-pill);
  background: var(--chart-matched-soft);
  color: var(--chart-matched-strong);
  padding: 0.25rem 0.75rem;
  font-size: var(--font-size-sm);
  font-style: normal;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/*
 * 요건 하나 = 캡슐 하나. 개수를 세어 읽을 수 있도록 사이를 벌리고 알약 형태로 굴린다.
 * 요건이 많아도 줄바꿈하지 않고 같은 폭으로 나눠 전체 비율을 유지한다.
 */
.analysis-overview__capsules {
  display: flex;
  gap: 0.375rem;
  height: 3rem;
}

@media (max-width: 40rem) {
  .analysis-overview__capsules {
    gap: 0.25rem;
    height: 2.25rem;
  }
}

.analysis-overview__capsules > span {
  position: relative;
  min-width: 0.25rem;
  flex: 1 1 0;
  overflow: hidden;
  border-radius: var(--radius-pill);
  transition:
    transform var(--motion-fast),
    filter var(--motion-fast);
}

/* 캡슐 윗면에 옅은 빛을 넣어 평평한 색 띠로 보이지 않게 한다. */
.analysis-overview__capsules > span::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgb(255 255 255 / 26%), rgb(255 255 255 / 0%) 52%);
  content: '';
}

.analysis-overview__capsules > span:hover {
  filter: saturate(1.15);
  transform: translateY(-2px);
}

/*
 * 색 외 2차 인코딩.
 * 캡슐에는 무늬를 넣지 않는다. 대신 항상 같은 순서로 정렬한 위치와,
 * 아이콘·한글 라벨·개수를 함께 적은 범례로 네 상태를 구분한다.
 */
[data-match-level='MATCHED'] {
  --analysis-match-color: var(--chart-matched);
  --analysis-match-strong: var(--chart-matched-strong);
  --analysis-match-soft: var(--chart-matched-soft);
}

[data-match-level='PARTIAL'] {
  --analysis-match-color: var(--chart-partial);
  --analysis-match-strong: var(--chart-partial-strong);
  --analysis-match-soft: var(--chart-partial-soft);
}

[data-match-level='MISSING'] {
  --analysis-match-color: var(--chart-missing);
  --analysis-match-strong: var(--chart-missing-strong);
  --analysis-match-soft: var(--chart-missing-soft);
}

[data-match-level='UNKNOWN'] {
  --analysis-match-color: var(--chart-unknown);
  --analysis-match-strong: var(--chart-unknown-strong);
  --analysis-match-soft: var(--chart-unknown-soft);
}

.analysis-overview__capsules > span {
  background-color: var(--analysis-match-color);
}

/*
 * 캡슐 색이 무슨 뜻인지 바로 옆에서 읽는 범례.
 * 칩 하나에 아이콘·한글 라벨·개수를 함께 두어 색만으로 뜻을 전달하지 않는다.
 */
.analysis-overview__statuses {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.analysis-overview__statuses > div {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--analysis-match-soft);
  padding: var(--space-2) var(--space-4) var(--space-2) var(--space-2);
}

/* 0건인 상태는 있는 그대로 두되 시선을 덜 끌게 한다. */
.analysis-overview__statuses > div[data-empty='true'] {
  background: var(--color-fill);
}

.analysis-overview__statuses > div[data-empty='true'] dt,
.analysis-overview__statuses > div[data-empty='true'] dd strong {
  color: var(--color-text-muted);
}

.analysis-overview__statuses > div[data-empty='true'] .analysis-overview__dot {
  background: var(--color-border-strong);
}

.analysis-overview__statuses dt {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: center;
  gap: var(--space-2);
  color: var(--analysis-match-strong);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.analysis-overview__statuses dd {
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.analysis-overview__statuses dd strong {
  margin-right: 0.0625rem;
  color: var(--analysis-match-strong);
  font-size: var(--font-size-lg);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.analysis-overview__dot {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  border-radius: var(--radius-md);
  background: var(--analysis-match-color);
  color: #fff;
}

.analysis-overview__dot .icon {
  width: 1rem;
  height: 1rem;
}

/* 카테고리별 충족도 */

.analysis-overview__categories {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-7);
}

.analysis-overview__categories-mobile {
  display: none;
}

.analysis-overview__categories li {
  display: grid;
  grid-template-columns: 10rem minmax(0, 1fr) 6rem;
  align-items: center;
  gap: var(--space-4);
}

.analysis-overview__categories li > div:first-child strong {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-text-secondary);
}

.analysis-overview__categories li > div:first-child span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-overview__bar {
  position: relative;
  height: 0.75rem;
  border-radius: var(--radius-pill);
  background: var(--chart-track);
}

.analysis-overview__bar span {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 4px;
  background: var(--chart-brand);
  transform-origin: left;
  animation: analysis-grow 700ms cubic-bezier(0.2, 0, 0, 1) backwards;
}

.analysis-overview__categories small {
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

@keyframes analysis-grow {
  from {
    transform: scaleX(0);
  }
}

/* ------------------------------------------------------------ 공고 핵심 */

.analysis-requirements__summary {
  display: grid;
  gap: var(--space-1);
  margin-bottom: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4) var(--space-5);
}

.analysis-requirements__summary span {
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.analysis-requirements__summary p {
  color: var(--color-text-secondary);
}

.analysis-requirement-group {
  border-top: 1px solid var(--color-border);
}

.analysis-requirement-group:last-of-type {
  border-bottom: 1px solid var(--color-border);
}

.analysis-requirement-group > summary {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-1);
  cursor: pointer;
  list-style: none;
}

.analysis-requirement-group > summary::-webkit-details-marker {
  display: none;
}

.analysis-requirement-group > summary > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-4);
}

.analysis-requirement-group > summary > div > span {
  flex: 0 0 7rem;
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.analysis-requirement-group > summary > div > strong {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-requirement-group__count {
  flex: 0 0 auto;
  border-radius: var(--radius-pill);
  background: var(--color-brand-soft);
  padding: 0.1875rem 0.5625rem;
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.analysis-requirement-group ul {
  padding: 0 var(--space-1) var(--space-5) 7.5rem;
}

.analysis-requirement-group li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  color: var(--color-text-secondary);
}

.analysis-requirement-group li + li {
  border-top: 1px dashed var(--color-border);
}

.analysis-requirement-group li small {
  border-radius: 4px;
  background: var(--color-fill);
  padding: 0.0625rem 0.375rem;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-requirement-group > .analysis-empty-copy {
  padding: 0 var(--space-1) var(--space-5) 7.5rem;
}

/* ------------------------------------------------------ 강점과 보완 포인트 */

.analysis-insights__grid {
  display: grid;
  /* min()이 없으면 320px에서 19rem 최소폭이 컨테이너를 넘어 가로 스크롤이 생긴다. */
  grid-template-columns: repeat(auto-fit, minmax(min(19rem, 100%), 1fr));
  gap: var(--space-3);
}

.analysis-insight {
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-5);
}

.analysis-insight__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.analysis-insight__heading h4 {
  font-size: var(--font-size-md);
  font-weight: 800;
}

.analysis-insight__heading small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-insight__heading strong {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.analysis-insight ul {
  display: grid;
  gap: var(--space-2);
}

.analysis-insight li {
  display: flex;
  gap: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3) var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.analysis-insight li .icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  margin-top: 0.1875rem;
}

.analysis-insight--strength li .icon {
  color: var(--color-success);
}

.analysis-insight--gap li .icon {
  color: var(--color-warning);
}

.analysis-insight__mobile-more {
  display: none;
}

/* ------------------------------------------------------ 점수에 활용한 경험 */

.analysis-evidence__notice {
  margin-bottom: var(--space-4);
}

.analysis-evidence__list {
  display: grid;
  gap: 2px;
  border-radius: var(--radius-lg);
  background: var(--color-border);
  overflow: hidden;
}

.analysis-evidence__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  background: var(--color-surface);
  padding: var(--space-4) var(--space-5);
}

.analysis-evidence__item > div {
  min-width: 0;
}

.analysis-evidence__item strong {
  display: block;
  font-weight: 700;
}

.analysis-evidence__item > div > span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-evidence__item small {
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.analysis-evidence__item--changed {
  background: var(--color-warning-soft);
}

.analysis-evidence__state--changed {
  color: var(--color-warning);
}

/* ---------------------------------------------------- 조건별 확인 결과 */

.analysis-breakdown__heading {
  margin-bottom: var(--space-4);
}

.analysis-breakdown__range {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.analysis-breakdown__filters {
  display: flex;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
  overflow-x: auto;
  padding-bottom: var(--space-1);
  scrollbar-width: none;
}

.analysis-breakdown__filters::-webkit-scrollbar {
  display: none;
}

.analysis-breakdown__filters button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  flex: 0 0 auto;
  min-height: 2.375rem;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  padding: 0 var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  transition:
    background var(--motion-fast),
    color var(--motion-fast);
}

.analysis-breakdown__filters button span {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.analysis-breakdown__filters button:hover:not(.analysis-breakdown__filter--active) {
  background: var(--color-fill-strong);
}

.analysis-breakdown__filters button.analysis-breakdown__filter--active {
  background: var(--color-ink-title);
  color: var(--color-surface);
}

.analysis-breakdown__filters button.analysis-breakdown__filter--active span {
  color: rgb(255 255 255 / 70%);
}

.analysis-criterion {
  border-top: 1px solid var(--color-border);
  padding: var(--space-4) var(--space-1);
}

.analysis-criterion:last-of-type {
  border-bottom: 1px solid var(--color-border);
}

.analysis-criterion__header {
  flex-wrap: nowrap;
  align-items: center;
}

.analysis-criterion__mark {
  display: grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  flex: 0 0 auto;
  border-radius: var(--radius-pill);
  background: var(--analysis-match-color);
  color: #fff;
}

.analysis-criterion__mark .icon {
  width: 1rem;
  height: 1rem;
}

.analysis-criterion__header > div {
  min-width: 0;
  flex: 1;
}

.analysis-criterion__header > div > span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.analysis-criterion__header h4 {
  font-weight: 700;
  line-height: 1.5;
}

.analysis-criterion__meter {
  position: relative;
  height: 0.375rem;
  margin: var(--space-3) 0 var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--chart-track);
}

.analysis-criterion__meter span {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 4px;
  background: var(--chart-brand);
}

.analysis-criterion__score {
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.analysis-criterion__detail {
  margin-top: var(--space-3);
}

.analysis-criterion__detail > summary {
  color: var(--color-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

.analysis-criterion__detail > p {
  max-width: 52rem;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.analysis-criterion__evidence {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-3);
}

.analysis-criterion__evidence li {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  padding: 0.3125rem 0.6875rem;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.analysis-criterion__evidence-item--changed {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.analysis-breakdown__empty {
  padding: var(--space-6) 0;
  color: var(--color-text-muted);
  text-align: center;
}

.analysis-breakdown__pagination {
  margin-top: var(--space-5);
}

/* ------------------------------------------------------- 분석 결과 기록 */

.analysis-history {
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  box-shadow: var(--shadow-panel);
}

.analysis-history__summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  cursor: pointer;
  list-style: none;
}

.analysis-history__summary::-webkit-details-marker {
  display: none;
}

.analysis-history__summary p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.analysis-history__summary > span {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

/* 적합도 추이 */

.analysis-trend {
  display: block;
  width: 100%;
  max-width: 30rem;
  height: auto;
  margin-top: var(--space-5);
}

.analysis-trend__grid line {
  stroke: var(--chart-grid);
  stroke-width: 1;
}

.analysis-trend__axis {
  fill: var(--color-text-muted);
  font-size: 10px;
  font-weight: 600;
}

.analysis-trend__area {
  fill: url('#analysis-trend-fill');
}

.analysis-trend__line {
  fill: none;
  stroke: var(--chart-brand);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.analysis-trend__dot {
  fill: var(--color-surface);
  stroke: var(--chart-brand);
  stroke-width: 2;
}

.analysis-trend__dot--last {
  fill: var(--chart-brand);
}

.analysis-trend__value {
  fill: var(--color-ink-title);
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.analysis-history__layout {
  margin-top: var(--space-4);
}

.analysis-history__selection {
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-5);
}

.analysis-history__selection h4 {
  font-weight: 780;
}

.analysis-history__selection dl {
  display: grid;
  gap: var(--space-2);
  margin: var(--space-3) 0;
}

.analysis-history__selection dl > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.analysis-history__selection dt {
  color: var(--color-text-muted);
}

.analysis-history__selection dd {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.analysis-history__selection > p {
  margin-bottom: var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.analysis-history-state {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

/* ------------------------------------------------------------ 재분석 명령 */

.analysis-command {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--space-5);
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  box-shadow: var(--shadow-panel);
}

.analysis-command__description {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.analysis-command > .button {
  justify-self: end;
}

/* ---------------------------------------------------------------- 반응형 */

@media (max-width: 60rem) {
  .analysis-overview__categories li {
    grid-template-columns: minmax(0, 1fr) 5.5rem;
    row-gap: var(--space-2);
  }

  .analysis-overview__bar {
    grid-column: 1 / -1;
    order: 3;
  }

  .analysis-history__layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 48rem) {
  .analysis-result__hero {
    display: grid;
    grid-template-columns: 6.5rem minmax(0, 1fr);
    column-gap: var(--space-4);
    align-items: center;
  }

  .analysis-result__heading {
    display: block;
    grid-column: 2;
    grid-row: 1;
  }

  .analysis-result__heading h2 {
    margin-top: var(--space-1);
    font-size: 1.0625rem;
    line-height: 1.45;
  }

  .analysis-result__meta {
    align-items: flex-start;
    margin-top: var(--space-2);
    font-size: 0.75rem;
  }

  .analysis-result__desktop-date,
  .analysis-result__run-link {
    display: none;
  }

  .analysis-result__mobile-meta {
    display: inline;
  }

  .analysis-result__decision {
    display: contents;
  }

  .analysis-result__copy {
    display: flex;
    flex-direction: column;
    grid-column: 1 / -1;
    grid-row: 2;
    min-width: 0;
  }

  .analysis-result__summary {
    order: 2;
    margin-top: var(--space-4);
    font-size: var(--font-size-sm);
    line-height: 1.65;
  }

  .analysis-result__actions {
    order: 1;
    margin-top: var(--space-5);
  }

  .analysis-result__actions > .button--primary {
    width: 100%;
    min-height: 3.25rem;
  }

  .analysis-result__actions nav {
    width: 100%;
  }

  .analysis-result__metrics {
    display: contents;
  }

  .analysis-metric {
    display: none;
  }

  .analysis-metric--score {
    display: grid;
    grid-column: 1;
    grid-row: 1;
    align-self: center;
  }

  .analysis-metric--score dt {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
    white-space: nowrap;
  }

  .analysis-gauge {
    width: 6.5rem;
    height: 6.5rem;
  }

  .analysis-gauge__coverage-track,
  .analysis-gauge__coverage,
  .analysis-gauge__caption {
    display: none;
  }

  .analysis-gauge__number {
    font-size: 3.5rem;
  }

  .analysis-result__disclaimer {
    grid-column: 1 / -1;
    grid-row: 3;
    margin-top: var(--space-4);
    padding-top: 0;
    border-top: 0;
    font-size: 0.75rem;
  }

  .analysis-overview__categories--desktop {
    display: none;
  }

  .analysis-overview__categories-mobile {
    display: block;
    margin-top: var(--space-5);
  }

  .analysis-overview__categories-mobile > summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    min-height: 2.75rem;
    cursor: pointer;
    color: var(--color-text-secondary);
    font-size: var(--font-size-sm);
    font-weight: 750;
  }

  .analysis-overview__categories-mobile > summary small {
    color: var(--color-text-muted);
    font-size: var(--font-size-xs);
    font-weight: 650;
  }

  .analysis-overview__categories-mobile .analysis-overview__categories {
    margin-top: var(--space-3);
  }

  .analysis-overview__statuses {
    display: grid;
    gap: 1px;
    overflow: hidden;
    border-radius: var(--radius-md);
    background: var(--color-border);
  }

  .analysis-overview__statuses > div {
    justify-content: space-between;
    border-radius: 0;
    background: var(--color-surface);
    padding: 0.5625rem var(--space-3);
  }

  .analysis-insight li.analysis-insight__mobile-extra {
    display: none;
  }

  .analysis-insight__mobile-more {
    display: block;
    margin-top: var(--space-2);
  }

  .analysis-insight__mobile-more summary {
    width: fit-content;
    cursor: pointer;
    color: var(--color-brand-strong);
    font-size: var(--font-size-sm);
    font-weight: 700;
  }

  .analysis-insight__mobile-more ul {
    margin-top: var(--space-2);
  }

  .analysis-requirement-group > summary > div > span {
    flex-basis: 100%;
  }

  .analysis-requirement-group > summary > div > strong {
    flex-basis: 100%;
    white-space: normal;
  }

  .analysis-requirement-group ul,
  .analysis-requirement-group > .analysis-empty-copy {
    padding-left: var(--space-1);
  }

  .analysis-command {
    grid-template-columns: minmax(0, 1fr);
  }

  .analysis-command > .button {
    justify-self: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  .analysis-gauge__score,
  .analysis-gauge__coverage,
  .analysis-overview__bar span {
    animation: none;
  }
}
</style>
