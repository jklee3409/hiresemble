<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'

const props = defineProps<{ userId: string; documentId: string; agentRunId: string }>()
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
      run.resourceType !== 'DOCUMENT' ||
      run.resourceId !== props.documentId
    )
      return
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
      connecting: '진행 연결 중',
      connected: '실시간 진행 연결됨',
      reconnecting: '진행 연결 복구 중',
      polling: 'REST 상태 확인 중',
      closed: '진행 연결 종료',
    })[connection.value],
)
</script>

<template>
  <div class="mt-3 rounded-lg bg-slate-50 p-3 text-sm" aria-live="polite">
    <p v-if="detail.isPending.value">Agent Run 상태를 불러오는 중…</p>
    <p v-else-if="detail.isError.value" class="text-amber-800">
      실시간 연결을 확인할 수 없습니다. 문서 REST 상태를 기준으로 새로고침해 주세요.
    </p>
    <template v-else-if="detail.data.value">
      <p class="font-medium">
        작업 상태: {{ detail.data.value.status }} · {{ detail.data.value.progressPercent }}%
      </p>
      <p class="mt-1 text-xs text-slate-600">
        {{ connectionLabel }} — 연결 단절은 문서 처리 실패가 아닙니다.
      </p>
    </template>
  </div>
</template>
