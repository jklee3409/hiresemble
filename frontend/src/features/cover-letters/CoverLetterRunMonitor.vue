<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { STATUS_LABELS } from '@/features/agent-runs/presentation'
import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import type { AgentRunDetailDto } from '@/shared/api/agentRunContracts'

const props = withDefaults(
  defineProps<{
    userId: string
    coverLetterId: string
    agentRunId: string
    questionLabels?: Record<string, string>
  }>(),
  { questionLabels: () => ({}) },
)
const emit = defineEmits<{ terminal: [run: AgentRunDetailDto] }>()
const cache = useQueryClient()
const runId = computed(() => props.agentRunId)
const userId = computed(() => props.userId)
const detail = useAgentRunDetailQuery(userId, runId)
const connection = ref<AgentRunConnectionState>('connecting')
let stream: AgentRunStreamController | null = null
let identity = ''
let emittedTerminalVersion = -1

watch(
  [() => detail.data.value, userId, runId],
  ([run, ownerId, agentRunId]) => {
    const nextIdentity = `${ownerId}/${agentRunId}`
    if (identity !== nextIdentity) {
      stream?.close()
      stream = null
      identity = nextIdentity
      emittedTerminalVersion = -1
    }
    if (
      run === undefined ||
      stream !== null ||
      !['COVER_LETTER_GENERATION', 'COVER_LETTER_VERIFICATION'].includes(run.workflowType) ||
      run.resourceType !== 'COVER_LETTER' ||
      run.resourceId !== props.coverLetterId
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

onBeforeUnmount(() => stream?.close())

const connectionLabel = computed(
  () =>
    ({
      connecting: '실시간 진행 상황을 연결하는 중',
      connected: '실시간 진행 상황 연결됨',
      reconnecting: '진행 상황을 다시 연결하는 중',
      polling: '진행 상황을 주기적으로 다시 확인하는 중',
      closed: '진행 상황 확인 완료',
    })[connection.value],
)

function scopeLabel(scopeKey: string): string {
  return props.questionLabels[scopeKey] ?? scopeKey
}
</script>

<template>
  <section
    class="cover-run-monitor"
    aria-live="polite"
    aria-label="자기소개서 AI 작업 진행"
    data-testid="cover-letter-run-monitor"
  >
    <p v-if="detail.isLoading.value">AI 작업 진행 상황을 확인하는 중…</p>
    <p v-else-if="detail.isError.value" class="cover-run-monitor__warning">
      진행 연결이 잠시 끊겼어요. 저장된 답변은 유지되며 AI 작업 상세에서 다시 확인할 수 있어요.
    </p>
    <template v-else-if="detail.data.value">
      <div class="cover-run-monitor__summary">
        <div>
          <strong>
            {{
              detail.data.value.workflowType === 'COVER_LETTER_GENERATION'
                ? 'AI 초안 생성'
                : '답변 검증'
            }}
          </strong>
          <span>{{ STATUS_LABELS[detail.data.value.status] }}</span>
        </div>
        <strong>{{ detail.data.value.progressPercent }}%</strong>
      </div>
      <progress
        class="progress-track"
        :value="detail.data.value.progressPercent"
        max="100"
        aria-label="자기소개서 AI 작업 진행률"
      >
        {{ detail.data.value.progressPercent }}%
      </progress>
      <p class="cover-run-monitor__connection">{{ connectionLabel }}</p>

      <div
        v-if="
          detail.data.value.partialResult &&
          (detail.data.value.partialResult.succeededScopeKeys.length > 0 ||
            detail.data.value.partialResult.failedScopeKeys.length > 0)
        "
        class="cover-run-monitor__partial"
      >
        <div v-if="detail.data.value.partialResult.succeededScopeKeys.length > 0">
          <h4>생성 완료 문항</h4>
          <ul>
            <li
              v-for="scope in detail.data.value.partialResult.succeededScopeKeys"
              :key="`success-${scope}`"
            >
              {{ scopeLabel(scope) }}
            </li>
          </ul>
        </div>
        <div v-if="detail.data.value.partialResult.failedScopeKeys.length > 0">
          <h4>재시도가 필요한 문항</h4>
          <ul>
            <li
              v-for="scope in detail.data.value.partialResult.failedScopeKeys"
              :key="`failed-${scope}`"
            >
              {{ scopeLabel(scope) }}
            </li>
          </ul>
          <p>성공한 답변은 보존됩니다. AI 작업 상세에서 실패 문항만 재시도할 수 있어요.</p>
        </div>
      </div>

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
.cover-run-monitor {
  display: grid;
  gap: var(--space-3);
  border: 1px solid var(--color-brand-border);
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  padding: var(--space-4);
}

.cover-run-monitor__summary,
.cover-run-monitor__summary > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.cover-run-monitor__summary > div span,
.cover-run-monitor__connection,
.cover-run-monitor__partial p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.cover-run-monitor__partial {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.cover-run-monitor__partial > div {
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-3);
}

.cover-run-monitor__partial ul {
  margin-top: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
}

.cover-run-monitor .button {
  justify-self: start;
}

.cover-run-monitor__warning {
  color: var(--color-warning-strong);
}

@media (max-width: 40rem) {
  .cover-run-monitor__partial {
    grid-template-columns: 1fr;
  }

  .cover-run-monitor .button {
    width: 100%;
  }
}
</style>
