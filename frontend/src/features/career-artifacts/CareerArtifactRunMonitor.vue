<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import {
  useAgentRunDetailQuery,
  useCancelAgentRunMutation,
  useRetryAgentRunMutation,
} from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import {
  STATUS_LABELS,
  formatRunProgressLabel,
  formatStepName,
} from '@/features/agent-runs/presentation'
import type { CareerArtifactType } from '@/shared/api/careerArtifactContracts'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const props = defineProps<{
  userId: string
  artifactId: string
  artifactType: CareerArtifactType
  agentRunId: string
}>()
const emit = defineEmits<{ 'track-run': [agentRunId: string] }>()

const cache = useQueryClient()
const run = useAgentRunDetailQuery(
  computed(() => props.userId),
  computed(() => props.agentRunId),
)
const cancelMutation = useCancelAgentRunMutation(computed(() => props.userId))
const retryMutation = useRetryAgentRunMutation(computed(() => props.userId))
const connection = ref<AgentRunConnectionState>('connecting')
let controller: AgentRunStreamController | null = null
let identity = ''

const expectedWorkflow = computed(() =>
  props.artifactType === 'RESUME' ? 'RESUME_GENERATION' : 'PORTFOLIO_GENERATION',
)
const validRun = computed(() => {
  const value = run.data.value
  return value?.resourceType === 'CAREER_ARTIFACT' &&
    value.resourceId === props.artifactId &&
    value.workflowType === expectedWorkflow.value
    ? value
    : null
})
const parityError = computed(() => run.data.value !== undefined && validRun.value === null)
const connectionMessage = computed(() => {
  if (connection.value === 'reconnecting' || connection.value === 'polling') {
    return '진행 상태 연결을 복구하고 있어요. 마지막으로 확인한 상태와 이전 성공 파일은 그대로 유지됩니다.'
  }
  if (connection.value === 'connecting') return '진행 상태를 연결하는 중이에요.'
  return ''
})

watch(
  [() => validRun.value, () => props.userId, () => props.artifactId, () => props.agentRunId],
  ([currentRun, userId, artifactId, agentRunId]) => {
    const nextIdentity = `${userId}/${artifactId}/${agentRunId}`
    if (identity !== nextIdentity) {
      controller?.close()
      controller = null
      identity = nextIdentity
      connection.value = 'connecting'
    }
    if (currentRun === null || controller !== null) return
    controller = new AgentRunStreamController({
      userId,
      agentRunId,
      initialRun: currentRun,
      cache,
      onConnectionState: (state) => (connection.value = state),
    })
    controller.start()
  },
  { immediate: true },
)

onBeforeUnmount(() => controller?.close())

async function cancelRun(): Promise<void> {
  if (!validRun.value?.cancellable || cancelMutation.isPending.value) return
  await cancelMutation.mutateAsync({
    agentRunId: validRun.value.id,
    stateVersion: validRun.value.stateVersion,
  })
}

async function retryRun(): Promise<void> {
  if (!validRun.value?.retryable || retryMutation.isPending.value) return
  const accepted = await retryMutation.mutateAsync(validRun.value.id)
  if (accepted.resourceType === 'CAREER_ARTIFACT' && accepted.resourceId === props.artifactId) {
    controller?.close()
    controller = null
    emit('track-run', accepted.agentRunId)
  }
}

function tone(status: string): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'INTERRUPTED' || status === 'CANCELLED') return 'warning'
  if (status === 'RUNNING') return 'info'
  return 'neutral'
}
</script>

<template>
  <section
    class="artifact-run-monitor"
    aria-live="polite"
    :aria-busy="run.isPending.value || ['QUEUED', 'RUNNING'].includes(validRun?.status ?? '')"
  >
    <p v-if="run.isPending.value" role="status">파일 생성 진행 상태를 불러오는 중…</p>
    <p v-else-if="run.isError.value" class="alert alert--warning" role="status">
      진행 상태 연결을 잠시 확인하지 못했어요. 이 연결 문제만으로 파일 생성이 실패한 것은 아닙니다.
    </p>
    <p v-else-if="parityError" class="alert alert--danger" role="alert">
      이 자료와 일치하지 않는 작업 정보가 반환되어 진행 상태를 표시하지 않았어요.
    </p>
    <template v-else-if="validRun">
      <div class="artifact-run-monitor__heading">
        <div>
          <p class="section-kicker">최근 AI 작업</p>
          <strong>{{ formatRunProgressLabel(validRun.status) }}</strong>
        </div>
        <StatusBadge :label="STATUS_LABELS[validRun.status]" :tone="tone(validRun.status)" />
      </div>
      <p v-if="validRun.currentStep" class="artifact-run-monitor__step">
        {{ formatStepName(validRun.currentStep) }}
      </p>
      <div class="artifact-run-monitor__progress">
        <progress :value="validRun.progressPercent" max="100">
          {{ validRun.progressPercent }}%
        </progress>
        <span>{{ validRun.progressPercent }}%</span>
      </div>
      <p v-if="connectionMessage" class="artifact-run-monitor__connection" role="status">
        {{ connectionMessage }}
      </p>
      <p v-if="validRun.safeError" class="alert alert--warning">
        {{ validRun.safeError.message }}
      </p>
      <div class="artifact-run-monitor__actions">
        <RouterLink class="text-link" :to="`/agent-runs/${validRun.id}`"
          >AI 작업 상세 보기</RouterLink
        >
        <button
          v-if="validRun.cancellable"
          type="button"
          class="button button--secondary"
          :disabled="cancelMutation.isPending.value"
          @click="cancelRun"
        >
          작업 취소
        </button>
        <button
          v-if="validRun.retryable && ['FAILED', 'INTERRUPTED'].includes(validRun.status)"
          type="button"
          class="button button--secondary"
          :disabled="retryMutation.isPending.value"
          @click="retryRun"
        >
          같은 설정으로 다시 시도
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.artifact-run-monitor {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.artifact-run-monitor__heading,
.artifact-run-monitor__progress,
.artifact-run-monitor__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.artifact-run-monitor__heading strong {
  display: block;
  margin-top: var(--space-1);
}

.artifact-run-monitor__progress progress {
  flex: 1;
}

.artifact-run-monitor__step,
.artifact-run-monitor__connection {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

@media (max-width: 35rem) {
  .artifact-run-monitor__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
