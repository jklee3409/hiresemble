<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { AgentRunDetailDto } from '@/shared/api/agentRunContracts'

import {
  MODEL_TIER_LABELS,
  QUALITY_LABELS,
  STATUS_LABELS,
  WORKFLOW_LABELS,
  formatCost,
  formatDuration,
  formatInstant,
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
    return '실시간 연결이 끊겨 마지막 상태를 유지하며 다시 연결하고 있습니다.'
  }
  if (props.connectionState === 'polling') {
    return '실시간 연결 복구에 실패해 5초마다 서버 상태를 확인하고 있습니다.'
  }
  if (props.connectionState === 'connecting') return '실시간 진행 상태에 연결하고 있습니다.'
  return ''
})
</script>

<template>
  <article class="space-y-6" data-testid="agent-run-detail">
    <section class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-sm font-medium text-indigo-700">{{ WORKFLOW_LABELS[run.workflowType] }}</p>
          <h2 class="mt-1 text-2xl font-semibold">{{ STATUS_LABELS[run.status] }}</h2>
          <p class="mt-1 text-sm text-slate-600">실행 시도 {{ run.runAttemptNo }}</p>
        </div>
        <div class="flex gap-2">
          <button
            v-if="canRetry"
            type="button"
            class="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            :disabled="retryPending"
            @click="$emit('retry')"
          >
            {{ retryPending ? '재시도 접수 중…' : '재시도' }}
          </button>
          <button
            v-if="canCancel"
            type="button"
            class="rounded-lg border border-red-300 px-4 py-2 text-sm font-semibold text-red-700 disabled:opacity-60"
            :disabled="cancelPending"
            @click="$emit('cancel')"
          >
            {{ cancelPending ? '취소 요청 중…' : '실행 취소' }}
          </button>
        </div>
      </div>

      <div class="mt-5" aria-label="진행률">
        <div class="mb-1 flex justify-between text-sm">
          <span>{{ run.currentStep ?? '단계 준비 중' }}</span>
          <span>{{ run.progressPercent }}%</span>
        </div>
        <progress class="h-3 w-full" :value="run.progressPercent" max="100">
          {{ run.progressPercent }}%
        </progress>
      </div>

      <p
        v-if="connectionMessage"
        class="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
        role="status"
      >
        {{ connectionMessage }}
      </p>
    </section>

    <section
      v-if="run.status === 'WAITING_USER' && run.requiredUserAction"
      class="rounded-xl border border-amber-300 bg-amber-50 p-5"
    >
      <h3 class="font-semibold">사용자 입력이 필요합니다</h3>
      <p class="mt-2 text-sm">{{ run.requiredUserAction.message }}</p>
      <RouterLink
        v-if="actionRoute"
        class="mt-3 inline-block font-semibold text-indigo-700 underline"
        :to="actionRoute"
      >
        필요한 정보 입력하기
      </RouterLink>
    </section>

    <section class="grid gap-4 md:grid-cols-2">
      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h3 class="font-semibold">실행 정보</h3>
        <dl class="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-slate-500">요청 품질</dt>
          <dd>
            {{
              run.requestedQualityMode ? QUALITY_LABELS[run.requestedQualityMode] : '정책 기본값'
            }}
          </dd>
          <dt class="text-slate-500">사용 등급</dt>
          <dd>
            {{
              run.highestModelTierUsed
                ? MODEL_TIER_LABELS[run.highestModelTierUsed]
                : '아직 사용하지 않음'
            }}
          </dd>
          <dt class="text-slate-500">접수</dt>
          <dd>{{ formatInstant(run.queuedAt) }}</dd>
          <dt class="text-slate-500">시작</dt>
          <dd>{{ formatInstant(run.startedAt) }}</dd>
          <dt class="text-slate-500">완료</dt>
          <dd>{{ formatInstant(run.completedAt) }}</dd>
          <dt class="text-slate-500">소요 시간</dt>
          <dd>{{ formatDuration(run.durationMs) }}</dd>
        </dl>
      </div>

      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h3 class="font-semibold">비용</h3>
        <dl class="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-slate-500">예상</dt>
          <dd>{{ formatCost(run.estimatedCostUsd) }}</dd>
          <dt class="text-slate-500">예약</dt>
          <dd>{{ formatCost(run.reservedCostUsd) }}</dd>
          <dt class="text-slate-500">실제 사용 추정</dt>
          <dd>{{ formatCost(run.actualCostUsd) }}</dd>
        </dl>
        <p class="mt-4 text-xs leading-5 text-slate-600">
          실제 사용 비용은 provider의 확정 청구액이 아니라 접수 시 고정된 가격 catalog로 계산한
          billable estimate입니다.
        </p>
      </div>
    </section>

    <section
      v-if="run.safeError"
      class="rounded-xl border border-red-200 bg-red-50 p-5"
      role="alert"
    >
      <h3 class="font-semibold text-red-900">안전한 오류 안내</h3>
      <p class="mt-2 text-sm text-red-800">{{ run.safeError.message }}</p>
      <p class="mt-1 text-xs text-red-700">오류 코드: {{ run.safeError.code }}</p>
    </section>

    <section v-if="run.partialResult" class="rounded-xl border border-slate-200 bg-white p-5">
      <h3 class="font-semibold">부분 처리 결과</h3>
      <p class="mt-2 text-sm">
        완료 범위: {{ run.partialResult.succeededScopeKeys.join(', ') || '없음' }}
      </p>
      <p class="mt-1 text-sm">
        실패 범위: {{ run.partialResult.failedScopeKeys.join(', ') || '없음' }}
      </p>
      <ul v-if="run.partialResult.resultRefs.length" class="mt-2 list-inside list-disc text-sm">
        <li v-for="reference in run.partialResult.resultRefs" :key="reference.resourceId">
          {{ reference.displayLabel ?? reference.resourceType }}
        </li>
      </ul>
    </section>

    <section class="rounded-xl border border-slate-200 bg-white p-5">
      <h3 class="font-semibold">단계 Timeline</h3>
      <p v-if="run.steps.length === 0" class="mt-3 text-sm text-slate-500">
        아직 기록된 단계가 없습니다.
      </p>
      <ol v-else class="mt-4 space-y-3">
        <li v-for="step in run.steps" :key="step.id" class="rounded-lg border border-slate-200 p-4">
          <div class="flex flex-wrap justify-between gap-2">
            <strong>{{ step.stepOrder }}. {{ step.stepKey }}</strong>
            <span class="text-sm">{{ step.status }}</span>
          </div>
          <p class="mt-1 text-sm text-slate-600">시도 {{ step.attempt }}/{{ step.maxAttempts }}</p>
          <p v-if="step.safeError" class="mt-2 text-sm text-red-700">
            {{ step.safeError.message }}
          </p>
        </li>
      </ol>
    </section>
  </article>
</template>
