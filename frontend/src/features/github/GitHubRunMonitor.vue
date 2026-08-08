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
const connectionMessage = computed(() => {
  if (connectionState.value === 'reconnecting' || connectionState.value === 'polling') {
    return '진행 상태 연결 복구 중이에요. 마지막으로 확인한 상태를 유지하며 다시 확인합니다.'
  }
  if (connectionState.value === 'connecting') return '진행 상태를 연결하는 중이에요.'
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
    <p v-if="run.isPending.value" role="status">GitHub 분석 진행 상태를 불러오는 중…</p>
    <p v-else-if="run.isError.value" class="alert alert--warning" role="status">
      진행 상태를 잠시 불러오지 못했어요. source 상태는 계속 확인할 수 있어요.
    </p>
    <p v-else-if="parityError" class="alert alert--danger" role="alert">
      이 GitHub 연결과 일치하지 않는 작업 정보가 반환됐어요.
    </p>
    <template v-else-if="validRun">
      <div class="github-run-monitor__heading">
        <div>
          <p class="section-kicker">최근 AI 작업</p>
          <strong>{{ formatRunProgressLabel(validRun.status) }}</strong>
        </div>
        <StatusBadge :label="STATUS_LABELS[validRun.status]" :tone="tone(validRun.status)" />
      </div>
      <p v-if="validRun.currentStep" class="github-run-monitor__step">
        {{ formatStepName(validRun.currentStep) }}
      </p>
      <div class="github-run-monitor__progress">
        <progress :value="validRun.progressPercent" max="100">
          {{ validRun.progressPercent }}%
        </progress>
        <span>{{ validRun.progressPercent }}%</span>
      </div>
      <p v-if="connectionMessage" class="github-run-monitor__connection" role="status">
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
  gap: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4);
}

.github-run-monitor__heading,
.github-run-monitor__progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.github-run-monitor__heading strong {
  display: block;
  margin-top: var(--space-1);
}

.github-run-monitor__step,
.github-run-monitor__connection {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
}

.github-run-monitor__progress progress {
  flex: 1;
}

.github-run-monitor__progress span {
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}
</style>
