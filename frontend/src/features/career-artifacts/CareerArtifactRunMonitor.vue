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
  agentRunFailureCopy,
  formatRunProgressLabel,
  formatStepName,
} from '@/features/agent-runs/presentation'
import type { CareerArtifactType } from '@/shared/api/careerArtifactContracts'
import InlineNotice from '@/shared/ui/InlineNotice.vue'
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
const failureCopy = computed(() =>
  validRun.value?.safeError
    ? agentRunFailureCopy(validRun.value.safeError, {
        retryable: validRun.value.retryable,
        status: validRun.value.status,
      })
    : null,
)
const connectionMessage = computed(() => {
  if (connection.value === 'reconnecting' || connection.value === 'polling') {
    return '연결이 잠시 끊겨 다시 잇고 있어요. 지금까지 만들어 둔 파일은 그대로 있어요.'
  }
  if (connection.value === 'connecting') return '진행 상태를 확인하는 중이에요.'
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
    <p v-if="run.isPending.value" role="status">진행 상태를 불러오는 중…</p>
    <InlineNotice
      v-else-if="run.isError.value"
      title="진행 상태를 잠시 확인하지 못했어요"
      description="파일 만들기가 멈춘 것은 아니에요. 잠시 뒤 다시 확인해 볼게요."
      tone="notice"
    />
    <InlineNotice
      v-else-if="parityError"
      title="진행 상태를 표시하지 않았어요"
      description="다른 자료의 작업 정보가 도착했어요. 화면을 새로 고치면 다시 확인할 수 있어요."
      tone="danger"
      role="alert"
    />
    <template v-else-if="validRun">
      <div class="artifact-run-monitor__heading">
        <div>
          <p class="section-kicker">
            {{ ['QUEUED', 'RUNNING'].includes(validRun.status) ? '진행 중인 작업' : '최근 작업' }}
          </p>
          <strong>{{ formatRunProgressLabel(validRun.status) }}</strong>
        </div>
        <StatusBadge :label="STATUS_LABELS[validRun.status]" :tone="tone(validRun.status)" />
      </div>
      <div class="artifact-run-monitor__progress">
        <div class="artifact-run-monitor__progress-label">
          <span v-if="validRun.currentStep">{{ formatStepName(validRun.currentStep) }}</span>
          <span v-else>준비하고 있어요</span>
          <strong>{{ validRun.progressPercent }}%</strong>
        </div>
        <progress
          class="progress-track"
          :value="validRun.progressPercent"
          max="100"
          :aria-label="`진행률 ${validRun.progressPercent}%`"
        >
          {{ validRun.progressPercent }}%
        </progress>
      </div>
      <p v-if="connectionMessage" class="artifact-run-monitor__connection" role="status">
        {{ connectionMessage }}
      </p>
      <InlineNotice
        v-if="failureCopy"
        :title="failureCopy.title"
        :description="failureCopy.description"
        tone="warning"
        role="alert"
      />
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
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border-radius: var(--radius-surface);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.artifact-run-monitor__heading,
.artifact-run-monitor__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.artifact-run-monitor__heading strong {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-ink-title);
  font-size: 1.0625rem;
  font-weight: 750;
  letter-spacing: -0.02em;
}

/*
 * 진행 표시는 현재 단계와 비율을 한 줄로 읽고 그 아래에 막대를 둔다.
 * 막대는 공용 `.progress-track`이라 OS 기본 초록색이 아니라 brand 색을 쓴다.
 */
.artifact-run-monitor__progress {
  display: grid;
  gap: var(--space-2);
}

.artifact-run-monitor__progress-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.artifact-run-monitor__progress-label strong {
  color: var(--color-brand-ink);
  font-variant-numeric: tabular-nums;
  font-weight: 750;
}

.artifact-run-monitor__connection {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.artifact-run-monitor__actions {
  gap: var(--space-2);
}

@media (max-width: 35rem) {
  .artifact-run-monitor__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
