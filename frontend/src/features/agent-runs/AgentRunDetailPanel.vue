<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type {
  AgentRunDetailDto,
  AgentRunStatus,
  AgentStepStatus,
} from '@/shared/api/agentRunContracts'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

import {
  STATUS_LABELS,
  WORKFLOW_LABELS,
  formatDuration,
  formatInstant,
  formatRunProgressLabel,
  formatStepName,
  formatUsage,
  safeRequiredActionRoute,
  usagePercent,
} from './presentation'
import type { AgentRunConnectionState } from './stream'

const props = defineProps<{
  run: AgentRunDetailDto
  connectionState: AgentRunConnectionState
  retryPending?: boolean
  cancelPending?: boolean
}>()

defineEmits<{
  retry: []
  cancel: []
}>()

const canRetry = computed(
  () =>
    ['FAILED', 'INTERRUPTED'].includes(props.run.status) &&
    props.run.retryable &&
    props.run.status !== 'WAITING_USER',
)
const canCancel = computed(
  () => ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(props.run.status) && props.run.cancellable,
)
const actionRoute = computed(() =>
  safeRequiredActionRoute(props.run.requiredUserAction?.route ?? null),
)
const jobAnalysisRoute = computed(() =>
  props.run.workflowType === 'JOB_ANALYSIS' &&
  props.run.resourceType === 'JOB' &&
  props.run.resourceId !== null
    ? { name: 'job-analysis', params: { jobId: props.run.resourceId } }
    : null,
)
const coverLetterRoute = computed(() =>
  props.run.resourceType === 'COVER_LETTER' && props.run.resourceId !== null
    ? {
        name: 'cover-letter-edit',
        params: { coverLetterId: props.run.resourceId },
      }
    : null,
)
const connectionMessage = computed(() => {
  if (props.connectionState === 'reconnecting') {
    return '진행 상황을 다시 확인하는 중이에요. 마지막으로 확인한 상태는 그대로 유지돼요.'
  }
  if (props.connectionState === 'polling') {
    return '진행 상황을 다시 확인하는 중이에요. 분석이 실패한 것은 아니에요.'
  }
  if (props.connectionState === 'connecting') return '진행 상황을 연결하는 중이에요.'
  return ''
})
const taskUsagePercent = computed(() =>
  usagePercent(props.run.actualCostUsd, props.run.reservedCostUsd),
)
const taskRemainingPercent = computed(() =>
  taskUsagePercent.value === null ? null : Math.max(0, 100 - taskUsagePercent.value),
)
const safeRunErrorMessage = computed(() =>
  props.run.retryable
    ? '작업을 마치지 못했어요. 잠시 후 다시 시도해 주세요. 등록한 원본과 기존 결과는 그대로 유지됩니다.'
    : '지금은 이 작업을 진행할 수 없어요. 등록한 원본과 기존 결과는 그대로 유지됩니다.',
)

function runTone(value: AgentRunStatus): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return (
    {
      QUEUED: 'neutral',
      RUNNING: 'info',
      WAITING_USER: 'warning',
      SUCCEEDED: 'success',
      FAILED: 'danger',
      CANCELLED: 'neutral',
      INTERRUPTED: 'warning',
    } as const
  )[value]
}

function stepLabel(value: AgentStepStatus): string {
  return {
    PENDING: '대기',
    RUNNING: '진행 중',
    WAITING_USER: '정보 입력 필요',
    SUCCEEDED: '완료',
    FAILED: '실패',
    SKIPPED: '건너뜀',
    REUSED: '결과 재사용',
    CANCELLED: '취소됨',
    INTERRUPTED: '중단됨',
  }[value]
}

function stepTone(value: AgentStepStatus): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (value === 'RUNNING') return 'info'
  if (value === 'WAITING_USER' || value === 'INTERRUPTED') return 'warning'
  if (value === 'SUCCEEDED' || value === 'REUSED') return 'success'
  if (value === 'FAILED') return 'danger'
  return 'neutral'
}
</script>

<template>
  <article class="run-detail" data-testid="agent-run-detail">
    <section class="run-summary section-surface">
      <div class="run-summary__header">
        <div class="run-summary__identity">
          <p class="section-kicker">{{ WORKFLOW_LABELS[run.workflowType] }}</p>
          <div class="run-summary__title">
            <h2>{{ STATUS_LABELS[run.status] }}</h2>
            <StatusBadge :label="STATUS_LABELS[run.status]" :tone="runTone(run.status)" />
          </div>
          <p>{{ run.runAttemptNo }}번째 시도</p>
        </div>
        <div class="run-summary__actions">
          <RouterLink
            v-if="jobAnalysisRoute"
            class="button button--secondary"
            :to="jobAnalysisRoute"
          >
            공고 분석 보기
          </RouterLink>
          <RouterLink
            v-else-if="coverLetterRoute"
            class="button button--secondary"
            :to="coverLetterRoute"
          >
            자기소개서 보기
          </RouterLink>
          <button
            v-if="canRetry"
            type="button"
            class="button button--primary"
            :disabled="retryPending"
            @click="$emit('retry')"
          >
            {{ retryPending ? '재시도 접수 중…' : '재시도' }}
          </button>
          <button
            v-if="canCancel"
            type="button"
            class="button button--danger"
            :disabled="cancelPending"
            @click="$emit('cancel')"
          >
            {{ cancelPending ? '취소 요청 중…' : '실행 취소' }}
          </button>
        </div>
      </div>

      <div class="run-summary__progress" aria-label="진행률">
        <div>
          <span>{{ formatRunProgressLabel(run.status) }}</span>
          <strong>{{ run.progressPercent }}%</strong>
        </div>
        <progress class="progress-track" :value="run.progressPercent" max="100">
          {{ run.progressPercent }}%
        </progress>
      </div>

      <p
        v-if="connectionMessage"
        class="alert alert--warning run-summary__connection"
        role="status"
      >
        {{ connectionMessage }}
      </p>
    </section>

    <section
      v-if="run.status === 'WAITING_USER' && run.requiredUserAction"
      class="run-required-action"
    >
      <p class="section-kicker">다음 할 일</p>
      <h3 class="section-title">정보를 입력해 주세요.</h3>
      <p>{{ run.requiredUserAction.message }}</p>
      <RouterLink v-if="actionRoute" class="button button--primary" :to="actionRoute">
        필요한 정보 입력하기
      </RouterLink>
    </section>

    <section class="run-info-grid">
      <div class="run-info-section section-surface">
        <p class="section-kicker">작업 정보</p>
        <h3 class="section-title">진행 시간</h3>
        <dl class="run-definition-list">
          <dt>접수</dt>
          <dd>{{ formatInstant(run.queuedAt) }}</dd>
          <dt>시작</dt>
          <dd>{{ formatInstant(run.startedAt) }}</dd>
          <dt>완료</dt>
          <dd>{{ formatInstant(run.completedAt) }}</dd>
          <dt>소요 시간</dt>
          <dd>{{ formatDuration(run.durationMs) }}</dd>
        </dl>
      </div>

      <div class="run-info-section section-surface">
        <p class="section-kicker">AI 사용량</p>
        <h3 class="section-title">이번 작업 사용량</h3>
        <dl class="run-definition-list">
          <dt>작업 한도 대비</dt>
          <dd>{{ formatUsage(run.actualCostUsd, run.reservedCostUsd) }}</dd>
          <dt>남은 작업 한도</dt>
          <dd>
            {{ taskRemainingPercent === null ? '집계 정보 없음' : `${taskRemainingPercent}%` }}
          </dd>
        </dl>
        <progress
          v-if="taskUsagePercent !== null"
          class="progress-track run-usage-progress"
          :value="taskUsagePercent"
          max="100"
        >
          {{ taskUsagePercent }}%
        </progress>
        <p class="run-cost-note">
          제공사가 집계한 사용량을 이 작업에 미리 확보한 한도와 비교한 값이에요. 결제 금액이나 월간
          전체 한도를 뜻하지 않아요.
        </p>
      </div>
    </section>

    <section v-if="run.safeError" class="alert alert--danger run-safe-error" role="alert">
      <h3>문제가 생겼어요</h3>
      <p>{{ safeRunErrorMessage }}</p>
    </section>

    <p
      v-if="run.partialResult?.failedScopeKeys.length"
      class="alert alert--warning run-safe-error"
      role="status"
    >
      일부 항목은 완료하지 못했어요. 완료 {{ run.partialResult.succeededScopeKeys.length }}개 · 확인
      필요 {{ run.partialResult.failedScopeKeys.length }}개
    </p>

    <details class="run-timeline section-surface">
      <summary>
        <span
          ><span class="section-kicker">선택 정보</span
          ><strong class="section-title">분석 과정 자세히 보기</strong></span
        >
        <span>{{ run.steps.length }}개 과정</span>
      </summary>
      <p v-if="run.steps.length === 0" class="run-timeline__empty">아직 기록된 과정이 없어요.</p>
      <ol v-else class="run-timeline__list">
        <li v-for="step in run.steps" :key="step.id" class="run-step">
          <span class="run-step__marker" aria-hidden="true" />
          <div class="run-step__body">
            <div class="run-step__header">
              <strong>{{ formatStepName(step.stepKey) }}</strong>
              <StatusBadge :label="stepLabel(step.status)" :tone="stepTone(step.status)" />
            </div>
            <p v-if="step.attempt > 1">{{ step.attempt }}번째 시도 중</p>
            <p v-if="step.safeError" class="run-step__error">
              이 과정을 완료하지 못했어요. 다시 시도할 수 있는지 확인해 주세요.
            </p>
          </div>
        </li>
      </ol>
    </details>
  </article>
</template>

<style scoped>
.run-detail {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-5);
}

.run-summary,
.run-info-section,
.run-partial,
.run-timeline {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.run-summary__header,
.run-summary__title,
.run-summary__actions,
.run-summary__progress > div,
.run-step__header {
  display: flex;
  align-items: center;
}

.run-summary__header,
.run-summary__progress > div,
.run-step__header {
  justify-content: space-between;
  gap: var(--space-4);
}

.run-summary__title,
.run-summary__actions {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.run-summary__title h2 {
  font-size: clamp(1.5rem, 2.5vw, 2rem);
  font-weight: 780;
}

.run-summary__identity > p:last-child {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.run-summary__progress {
  margin-top: var(--space-6);
}

.run-summary__progress strong {
  font-variant-numeric: tabular-nums;
}

.run-summary__progress progress {
  margin-top: var(--space-2);
}

.run-summary__connection {
  margin-top: var(--space-4);
}

.run-required-action {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  border: 0;
  border-radius: var(--radius-lg);
  box-shadow: inset 0 0 0 1px var(--color-warning-border);
  background: var(--color-warning-soft);
}

.run-required-action p:not(.section-kicker) {
  margin: var(--space-2) 0 var(--space-4);
  color: var(--color-text-secondary);
}

.run-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.run-definition-list {
  display: grid;
  grid-template-columns: minmax(7rem, auto) 1fr;
  gap: var(--space-2) var(--space-4);
  margin-top: var(--space-4);
  font-size: var(--font-size-sm);
}

.run-definition-list dt {
  color: var(--color-text-muted);
}

.run-definition-list dd {
  overflow-wrap: anywhere;
}

.run-cost-note,
.run-partial > p,
.run-timeline__empty,
.run-step p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.run-cost-note {
  margin-top: var(--space-4);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.run-usage-progress {
  margin-top: var(--space-4);
}

.run-safe-error {
  display: grid;
  gap: var(--space-1);
}

.run-safe-error h3 {
  font-weight: 750;
}

.run-safe-error small {
  font-size: var(--font-size-xs);
}

.run-partial > p {
  margin-top: var(--space-2);
}

.run-partial ul {
  margin-top: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
  font-size: var(--font-size-sm);
}

.run-timeline__empty {
  margin-top: var(--space-4);
}

.run-timeline > summary {
  display: flex;
  cursor: pointer;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  list-style: none;
}

.run-timeline > summary::-webkit-details-marker {
  display: none;
}
.run-timeline > summary > span:first-child {
  display: grid;
  gap: var(--space-1);
}
.run-timeline > summary > span:last-child {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.run-timeline__list {
  margin-top: var(--space-5);
  padding-left: 0.4rem;
}

.run-step {
  display: grid;
  position: relative;
  grid-template-columns: 1rem 1fr;
  gap: var(--space-3);
  padding-bottom: var(--space-5);
}

.run-step:not(:last-child)::before {
  position: absolute;
  top: 0.8rem;
  bottom: 0;
  left: 0.34rem;
  width: 1px;
  background: var(--color-border-strong);
  content: '';
}

.run-step__marker {
  z-index: 1;
  width: 0.75rem;
  height: 0.75rem;
  margin-top: 0.2rem;
  border: 2px solid var(--color-brand);
  border-radius: 50%;
  background: var(--color-surface);
}

.run-step__body {
  min-width: 0;
}

.run-step__header strong {
  overflow-wrap: anywhere;
}

.run-step p {
  margin-top: var(--space-1);
}

.run-step__error {
  color: var(--color-danger-strong) !important;
}

@media (max-width: 48rem) {
  .run-info-grid {
    grid-template-columns: 1fr;
  }

  .run-summary__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
