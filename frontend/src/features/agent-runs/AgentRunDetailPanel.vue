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
  MODEL_TIER_LABELS,
  QUALITY_LABELS,
  STATUS_LABELS,
  WORKFLOW_LABELS,
  formatCost,
  formatDuration,
  formatInstant,
  formatRunProgressLabel,
  formatStepName,
  safeRequiredActionRoute,
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
const connectionMessage = computed(() => {
  if (props.connectionState === 'reconnecting') {
    return '진행 상황을 다시 확인하는 중이에요. 마지막으로 확인한 상태는 그대로 유지돼요.'
  }
  if (props.connectionState === 'polling') {
    return '진행 상황을 다시 확인하는 중이에요. AI 작업이 실패한 것은 아니에요.'
  }
  if (props.connectionState === 'connecting') return '진행 상황을 연결하는 중이에요.'
  return ''
})

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
          <dt>요청 품질</dt>
          <dd>
            {{
              run.requestedQualityMode ? QUALITY_LABELS[run.requestedQualityMode] : '정책 기본값'
            }}
          </dd>
          <dt>처리 방식</dt>
          <dd>
            {{
              run.highestModelTierUsed
                ? MODEL_TIER_LABELS[run.highestModelTierUsed]
                : '아직 사용하지 않음'
            }}
          </dd>
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
        <p class="section-kicker">비용 안내</p>
        <h3 class="section-title">예상 사용 비용</h3>
        <dl class="run-definition-list">
          <dt>예상</dt>
          <dd>{{ formatCost(run.estimatedCostUsd) }}</dd>
          <dt>예약</dt>
          <dd>{{ formatCost(run.reservedCostUsd) }}</dd>
          <dt>현재까지 예상</dt>
          <dd>{{ formatCost(run.actualCostUsd) }}</dd>
        </dl>
        <p class="run-cost-note">
          표시된 금액은 작업을 시작할 때의 기준으로 계산한 예상치예요. 실제 결제 금액과 다를 수
          있어요.
        </p>
      </div>
    </section>

    <section v-if="run.safeError" class="alert alert--danger run-safe-error" role="alert">
      <h3>문제가 생겼어요</h3>
      <p>{{ run.safeError.message }}</p>
    </section>

    <section v-if="run.partialResult" class="run-partial section-surface">
      <p class="section-kicker">정리된 결과</p>
      <h3 class="section-title">일부 작업 결과</h3>
      <p>완료된 항목 {{ run.partialResult.succeededScopeKeys.length }}개</p>
      <p>완료하지 못한 항목 {{ run.partialResult.failedScopeKeys.length }}개</p>
      <ul v-if="run.partialResult.resultRefs.length">
        <li v-for="reference in run.partialResult.resultRefs" :key="reference.resourceId">
          {{ reference.displayLabel ?? '정리된 결과' }}
        </li>
      </ul>
    </section>

    <section class="run-timeline section-surface">
      <p class="section-kicker">작업 흐름</p>
      <h3 class="section-title">진행 단계</h3>
      <p v-if="run.steps.length === 0" class="run-timeline__empty">아직 기록된 단계가 없어요.</p>
      <ol v-else class="run-timeline__list">
        <li v-for="step in run.steps" :key="step.id" class="run-step">
          <span class="run-step__marker" aria-hidden="true" />
          <div class="run-step__body">
            <div class="run-step__header">
              <strong>{{ formatStepName(step.stepOrder) }}</strong>
              <StatusBadge :label="stepLabel(step.status)" :tone="stepTone(step.status)" />
            </div>
            <p>시도 {{ step.attempt }}/{{ step.maxAttempts }}</p>
            <p v-if="step.safeError" class="run-step__error">
              {{ step.safeError.message }}
            </p>
          </div>
        </li>
      </ol>
    </section>
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
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
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
