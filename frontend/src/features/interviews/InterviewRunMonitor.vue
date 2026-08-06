<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import { AGENT_RUN_STATUS_LABELS } from '@/features/interviews/presentation'
import type { AgentRunDetailDto, WorkflowType } from '@/shared/api/agentRunContracts'
import { normalizeApiError } from '@/shared/api/errors'

const props = defineProps<{
  userId: string
  agentRunId: string
  workflowType: Extract<WorkflowType, 'INTERVIEW_PREPARATION' | 'INTERVIEW_ANSWER_FEEDBACK'>
  resourceType: 'QUESTION_SET' | 'INTERVIEW_ANSWER_VERSION'
  resourceId: string
}>()
const emit = defineEmits<{
  terminal: [run: AgentRunDetailDto]
  unavailable: []
}>()

const cache = useQueryClient()
const userId = computed(() => props.userId)
const runId = computed(() => props.agentRunId)
const detail = useAgentRunDetailQuery(userId, runId)
const connection = ref<AgentRunConnectionState>('connecting')
let stream: AgentRunStreamController | null = null
let identity = ''
let emittedTerminalVersion = -1
let emittedUnavailable = false

watch(
  [() => detail.data.value, userId, runId],
  ([run, ownerId, agentRunId]) => {
    const nextIdentity = `${ownerId}/${agentRunId}`
    if (identity !== nextIdentity) {
      stream?.close()
      stream = null
      identity = nextIdentity
      emittedTerminalVersion = -1
      emittedUnavailable = false
      connection.value = 'connecting'
    }
    if (
      run === undefined ||
      stream !== null ||
      run.workflowType !== props.workflowType ||
      run.resourceType !== props.resourceType ||
      run.resourceId !== props.resourceId
    ) {
      return
    }
    stream = new AgentRunStreamController({
      userId: ownerId,
      agentRunId,
      initialRun: run,
      cache,
      onConnectionState: (value) => (connection.value = value),
    })
    stream.start()
  },
  { immediate: true },
)

watch(
  () => detail.data.value,
  (run) => {
    if (
      run !== undefined &&
      ['SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(run.status) &&
      run.stateVersion !== emittedTerminalVersion
    ) {
      emittedTerminalVersion = run.stateVersion
      emit('terminal', run)
    }
  },
  { immediate: true },
)

watch(
  () => detail.error.value,
  (error) => {
    if (error !== null && normalizeApiError(error).status === 404 && !emittedUnavailable) {
      emittedUnavailable = true
      stream?.close()
      stream = null
      emit('unavailable')
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => stream?.close())

const connectionLabel = computed(
  () =>
    ({
      connecting: '진행 상황을 연결하는 중',
      connected: '실시간 진행 상황 연결됨',
      reconnecting: '진행 상황을 다시 연결하는 중',
      polling: '진행 상황을 주기적으로 확인하는 중',
      closed: '진행 상황 확인 완료',
    })[connection.value],
)
</script>

<template>
  <section class="interview-run" aria-live="polite" data-testid="interview-run-monitor">
    <p v-if="detail.isLoading.value">AI 작업 진행 상황을 확인하는 중…</p>
    <p
      v-else-if="detail.isError.value && normalizeApiError(detail.error.value).status === 404"
      class="interview-run__history-note"
    >
      이 AI 작업은 목록에서 정리되었어요. 이미 만든 면접 준비 결과와 답변 피드백은 그대로
      유지됩니다.
    </p>
    <p v-else-if="detail.isError.value" class="interview-run__warning">
      진행 연결이 잠시 끊겼어요. AI 작업에서 다시 확인할 수 있어요.
    </p>
    <template v-else-if="detail.data.value">
      <div class="interview-run__summary">
        <div>
          <strong>
            {{ workflowType === 'INTERVIEW_PREPARATION' ? '면접 조사와 질문 생성' : '답변 피드백' }}
          </strong>
          <span>{{ AGENT_RUN_STATUS_LABELS[detail.data.value.status] }}</span>
        </div>
        <strong>{{ detail.data.value.progressPercent }}%</strong>
      </div>
      <progress
        class="progress-track"
        :value="detail.data.value.progressPercent"
        max="100"
        aria-label="면접 준비 AI 작업 진행률"
      >
        {{ detail.data.value.progressPercent }}%
      </progress>
      <p class="interview-run__connection">{{ connectionLabel }}</p>
      <p v-if="detail.data.value.safeError" class="interview-run__warning" role="alert">
        {{ detail.data.value.safeError.message }}
      </p>
      <RouterLink
        class="button button--secondary"
        :to="{ name: 'agent-run-detail', params: { agentRunId: detail.data.value.id } }"
      >
        AI 작업 상세
      </RouterLink>
    </template>
  </section>
</template>

<style scoped>
.interview-run {
  display: grid;
  gap: var(--space-3);
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  padding: var(--space-4);
}

.interview-run__summary,
.interview-run__summary > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.interview-run__summary > div span,
.interview-run__connection,
.interview-run__history-note {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.interview-run__warning {
  color: var(--color-danger-strong);
}

.interview-run .button {
  justify-self: start;
}

@media (max-width: 40rem) {
  .interview-run .button {
    width: 100%;
  }
}
</style>
