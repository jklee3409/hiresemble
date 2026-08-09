<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import {
  STATUS_LABELS,
  formatRunProgressLabel,
  formatStepName,
} from '@/features/agent-runs/presentation'
import InlineNotice from '@/shared/ui/InlineNotice.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const props = defineProps<{
  userId: string
  sourceId: string
  agentRunId: string
}>()

const cache = useQueryClient()
const run = useAgentRunDetailQuery(
  computed(() => props.userId),
  computed(() => props.agentRunId),
)
const connectionState = ref<AgentRunConnectionState>('connecting')
let controller: AgentRunStreamController | null = null
let identity = ''

const validRun = computed(() => {
  const value = run.data.value
  return value?.resourceType === 'GITHUB_SOURCE' && value.resourceId === props.sourceId
    ? value
    : null
})
const parityError = computed(() => run.data.value !== undefined && validRun.value === null)
/* 끝난 작업에는 진행률 막대를 남기지 않는다. 상태는 위 badge가 이미 알리고 있다. */
const inProgress = computed(() =>
  ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(validRun.value?.status ?? ''),
)
const connectionMessage = computed(() => {
  if (connectionState.value === 'reconnecting' || connectionState.value === 'polling') {
    return '진행 상태를 다시 연결하고 있어요. 그동안은 마지막으로 확인한 내용을 보여 드려요.'
  }
  if (connectionState.value === 'connecting') return '진행 상태를 연결하고 있어요.'
  return ''
})

watch(
  [() => validRun.value, () => props.userId, () => props.sourceId, () => props.agentRunId],
  ([currentRun, userId, sourceId, agentRunId]) => {
    const nextIdentity = `${userId}/${sourceId}/${agentRunId}`
    if (identity !== nextIdentity) {
      controller?.close()
      controller = null
      identity = nextIdentity
    }
    if (currentRun === null || controller !== null) return
    controller = new AgentRunStreamController({
      userId,
      agentRunId,
      initialRun: currentRun,
      cache,
      onConnectionState: (state) => {
        connectionState.value = state
      },
    })
    controller.start()
  },
  { immediate: true },
)

onBeforeUnmount(() => controller?.close())

function tone(status: string): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'WAITING_USER' || status === 'INTERRUPTED') return 'warning'
  if (status === 'RUNNING') return 'info'
  return 'neutral'
}
</script>

<template>
  <section
    class="github-run-monitor"
    aria-live="polite"
    :aria-busy="
      run.isPending.value || ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(validRun?.status ?? '')
    "
  >
    <p v-if="run.isPending.value" class="github-run-monitor__loading" role="status">
      진행 상태를 불러오고 있어요…
    </p>
    <InlineNotice
      v-else-if="run.isError.value"
      tone="warning"
      title="진행 상태를 잠시 불러오지 못했어요"
      description="연결 상태는 위에서 계속 확인할 수 있어요."
    />
    <InlineNotice
      v-else-if="parityError"
      tone="danger"
      role="alert"
      title="이 연결의 작업 정보를 확인하지 못했어요"
      description="화면을 새로고침한 뒤에도 같으면 AI 작업 목록에서 확인해 주세요."
    />
    <template v-else-if="validRun">
      <div class="github-run-monitor__heading">
        <div class="github-run-monitor__title">
          <p class="section-kicker">AI 작업</p>
          <strong>{{ formatRunProgressLabel(validRun.status) }}</strong>
        </div>
        <StatusBadge :label="STATUS_LABELS[validRun.status]" :tone="tone(validRun.status)" />
      </div>

      <div v-if="inProgress" class="github-run-monitor__progress">
        <div class="github-run-monitor__progress-head">
          <span class="github-run-monitor__step">
            {{
              validRun.currentStep ? formatStepName(validRun.currentStep) : '작업을 준비하고 있어요'
            }}
          </span>
          <span class="github-run-monitor__percent">{{ validRun.progressPercent }}%</span>
        </div>
        <progress
          class="progress-track"
          :value="validRun.progressPercent"
          max="100"
          aria-label="GitHub 경험 확인 진행률"
        >
          {{ validRun.progressPercent }}%
        </progress>
      </div>

      <p
        v-if="inProgress && connectionMessage"
        class="github-run-monitor__connection"
        role="status"
      >
        {{ connectionMessage }}
      </p>
      <RouterLink class="text-link" :to="`/agent-runs/${validRun.id}`">
        AI 작업 상세 보기
      </RouterLink>
    </template>
  </section>
</template>

<style scoped>
.github-run-monitor {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4);
}

.github-run-monitor__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
}

.github-run-monitor__title strong {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-ink-title);
  font-size: 1.0625rem;
}

/*
 * 진행률은 단계 이름과 숫자를 막대 위 한 줄에 두고 막대는 폭을 다 쓴다.
 * 막대를 숫자 옆에 끼워 넣으면 좁은 화면에서 막대만 짧아져 남은 양을 읽기 어렵다.
 */
.github-run-monitor__progress {
  display: grid;
  gap: var(--space-2);
}

.github-run-monitor__progress-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.github-run-monitor__loading,
.github-run-monitor__step,
.github-run-monitor__connection {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
}

.github-run-monitor__percent {
  color: var(--color-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}
</style>
