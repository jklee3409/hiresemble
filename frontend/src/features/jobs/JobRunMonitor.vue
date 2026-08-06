<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import { STATUS_LABELS } from '@/features/agent-runs/presentation'

const props = defineProps<{ userId: string; jobId: string; agentRunId: string }>()
const cache = useQueryClient()
const userId = computed(() => props.userId)
const runId = computed(() => props.agentRunId)
const detail = useAgentRunDetailQuery(userId, runId)
const connection = ref<AgentRunConnectionState>('connecting')
let stream: AgentRunStreamController | null = null
let identity = ''

watch(
  [() => detail.data.value, userId, runId],
  ([run, ownerId, agentRunId]) => {
    const nextIdentity = `${ownerId}/${agentRunId}`
    if (identity !== nextIdentity) {
      stream?.close()
      stream = null
      identity = nextIdentity
    }
    if (
      run === undefined ||
      stream !== null ||
      run.resourceType !== 'JOB' ||
      run.resourceId !== props.jobId
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

onBeforeUnmount(() => stream?.close())

const connectionLabel = computed(
  () =>
    ({
      connecting: '진행 상황 연결 중',
      connected: '진행 상황 확인 중',
      reconnecting: '진행 상황을 다시 확인하는 중',
      polling: '진행 상황을 다시 확인하는 중',
      closed: '진행 상황 확인 완료',
    })[connection.value],
)
</script>

<template>
  <div class="job-run-monitor" aria-live="polite">
    <p v-if="detail.isPending.value">공고 분석 진행 상황을 확인하는 중…</p>
    <p v-else-if="detail.isError.value" class="job-run-monitor__warning">
      진행 상황을 다시 확인하는 중이에요. 저장한 공고 상태는 그대로 유지돼요.
    </p>
    <template v-else-if="detail.data.value">
      <div class="job-run-monitor__summary">
        <span>공고 분석: {{ STATUS_LABELS[detail.data.value.status] }}</span>
        <strong>{{ detail.data.value.progressPercent }}%</strong>
      </div>
      <progress class="progress-track" :value="detail.data.value.progressPercent" max="100">
        {{ detail.data.value.progressPercent }}%
      </progress>
      <p>{{ connectionLabel }} — 연결이 잠시 끊겨도 공고 불러오기가 실패한 것은 아니에요.</p>
    </template>
  </div>
</template>

<style scoped>
.job-run-monitor {
  margin-top: var(--space-4);
  padding: var(--space-4);
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  font-size: var(--font-size-sm);
}

.job-run-monitor__summary {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-weight: 650;
}

.job-run-monitor progress {
  margin-top: var(--space-2);
}

.job-run-monitor p:last-child {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.job-run-monitor__warning {
  color: var(--color-warning-strong) !important;
}
</style>
