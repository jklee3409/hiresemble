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
import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 작성 중에는 한 줄 진행 상태만 보여 준다.
 * 연결 상태, 문항별 결과와 실행 상세는 펼쳤을 때만 나온다.
 * 재시도가 필요한 문항이 생기면 사용자가 놓치지 않도록 처음부터 펼쳐 둔다.
 */

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

const runHeadline = computed(() => {
  const run = detail.data.value
  if (run === undefined) return 'AI가 작업하고 있어요'
  const generation = run.workflowType === 'COVER_LETTER_GENERATION'
  if (run.status === 'SUCCEEDED') {
    return generation ? '초안을 다 썼어요' : '근거 확인을 마쳤어요'
  }
  if (['FAILED', 'CANCELLED', 'INTERRUPTED'].includes(run.status)) {
    return generation ? '초안 작성을 끝내지 못했어요' : '근거 확인을 끝내지 못했어요'
  }
  return generation ? 'AI가 초안을 쓰고 있어요' : 'AI가 답변 근거를 확인하고 있어요'
})

const failedScopes = computed(() => detail.data.value?.partialResult?.failedScopeKeys ?? [])
const succeededScopes = computed(() => detail.data.value?.partialResult?.succeededScopeKeys ?? [])

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

const COMPLETED_SCOPE_PREVIEW_LENGTH = 48

function completedScopePreview(scopeKey: string): string {
  const label = scopeLabel(scopeKey).replace(/\s+/g, ' ').trim()
  const characters = Array.from(label)
  if (characters.length <= COMPLETED_SCOPE_PREVIEW_LENGTH) return label
  return `${characters.slice(0, COMPLETED_SCOPE_PREVIEW_LENGTH).join('')}…`
}
</script>

<template>
  <section
    class="cover-run-monitor"
    aria-live="polite"
    aria-label="자기소개서 AI 작업 진행"
    data-testid="cover-letter-run-monitor"
  >
    <p v-if="detail.isLoading.value" class="cover-run-monitor__line">
      AI 작업 진행 상황을 확인하는 중…
    </p>
    <p v-else-if="detail.isError.value" class="cover-run-monitor__line cover-run-monitor__warning">
      진행 연결이 잠시 끊겼어요. 저장된 답변은 그대로 남아 있어요.
    </p>
    <details
      v-else-if="detail.data.value"
      class="cover-run-monitor__box"
      :open="failedScopes.length > 0"
    >
      <summary>
        <span class="cover-run-monitor__avatar" aria-hidden="true"><AppIcon name="sparkle" /></span>
        <span class="cover-run-monitor__text">
          <strong>{{ runHeadline }}</strong>
          <small>{{ STATUS_LABELS[detail.data.value.status] }}</small>
        </span>
        <span class="cover-run-monitor__percent">{{ detail.data.value.progressPercent }}%</span>
        <progress
          class="progress-track cover-run-monitor__progress"
          :value="detail.data.value.progressPercent"
          max="100"
          aria-label="자기소개서 AI 작업 진행률"
        >
          {{ detail.data.value.progressPercent }}%
        </progress>
      </summary>

      <div class="cover-run-monitor__body">
        <p class="cover-run-monitor__connection">{{ connectionLabel }}</p>

        <div v-if="succeededScopes.length > 0" class="cover-run-monitor__scopes">
          <h4>생성 완료 문항</h4>
          <ul>
            <li v-for="scope in succeededScopes" :key="`success-${scope}`">
              <span
                class="cover-run-monitor__scope-preview"
                :aria-label="scopeLabel(scope)"
                :title="scopeLabel(scope)"
              >
                {{ completedScopePreview(scope) }}
              </span>
            </li>
          </ul>
        </div>
        <div v-if="failedScopes.length > 0" class="cover-run-monitor__scopes">
          <h4>재시도가 필요한 문항</h4>
          <ul>
            <li v-for="scope in failedScopes" :key="`failed-${scope}`">{{ scopeLabel(scope) }}</li>
          </ul>
          <p>성공한 답변은 보존됩니다. AI 작업 상세에서 실패 문항만 재시도할 수 있어요.</p>
        </div>

        <RouterLink
          class="text-link"
          :to="{ name: 'agent-run-detail', params: { agentRunId: detail.data.value.id } }"
        >
          AI 작업 상세
        </RouterLink>
      </div>
    </details>
  </section>
</template>

<style scoped>
.cover-run-monitor {
  min-width: 0;
}

.cover-run-monitor__line {
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-text-secondary);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
}

.cover-run-monitor__warning {
  color: var(--color-warning-strong);
}

.cover-run-monitor__box {
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
}

.cover-run-monitor__box > summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-1) var(--space-3);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  list-style: none;
}

.cover-run-monitor__box > summary::-webkit-details-marker {
  display: none;
}

.cover-run-monitor__avatar {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  place-items: center;
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-brand);
}

.cover-run-monitor__avatar :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.cover-run-monitor__text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.cover-run-monitor__text strong {
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.cover-run-monitor__text small,
.cover-run-monitor__percent {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.cover-run-monitor__percent {
  font-variant-numeric: tabular-nums;
}

.cover-run-monitor__progress {
  grid-column: 2 / -1;
  height: 0.25rem;
  background: var(--color-surface);
}

.cover-run-monitor__body {
  display: grid;
  gap: var(--space-3);
  border-top: 1px solid var(--color-brand-border);
  padding: var(--space-3);
}

.cover-run-monitor__connection,
.cover-run-monitor__scopes p {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.cover-run-monitor__scopes {
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3);
  font-size: var(--font-size-sm);
}

.cover-run-monitor__scopes h4 {
  font-weight: 750;
}

.cover-run-monitor__scopes ul {
  margin: var(--space-2) 0;
  padding-left: var(--space-5);
  list-style: disc;
}

.cover-run-monitor__scopes li {
  min-width: 0;
}

.cover-run-monitor__scope-preview {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-run-monitor__body .text-link {
  justify-self: start;
  font-size: var(--font-size-sm);
}
</style>
