<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'

import AgentRunDetailPanel from '@/features/agent-runs/AgentRunDetailPanel.vue'
import {
  useAgentRunDetailQuery,
  useCancelAgentRunMutation,
  useRetryAgentRunMutation,
} from '@/features/agent-runs/queries'
import {
  AgentRunStreamController,
  type AgentRunConnectionState,
} from '@/features/agent-runs/stream'
import { normalizeApiError } from '@/shared/api/errors'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const agentRunId = computed(() => String(route.params.agentRunId ?? ''))
const detail = useAgentRunDetailQuery(userId, agentRunId)
const retryMutation = useRetryAgentRunMutation(userId)
const cancelMutation = useCancelAgentRunMutation(userId)
const connectionState = ref<AgentRunConnectionState>('connecting')
const actionError = ref('')
let stream: AgentRunStreamController | null = null
let streamIdentity = ''

const loadError = computed(() =>
  detail.error.value ? normalizeApiError(detail.error.value).message : '',
)

watch(
  [() => detail.data.value, userId, agentRunId],
  ([run, currentUserId, currentRunId]) => {
    const identity = `${currentUserId}/${currentRunId}`
    if (streamIdentity !== identity) {
      stream?.close()
      stream = null
      streamIdentity = identity
    }
    if (run === undefined || stream !== null) return

    stream = new AgentRunStreamController({
      userId: currentUserId,
      agentRunId: currentRunId,
      initialRun: run,
      cache,
      onConnectionState: (state) => {
        connectionState.value = state
      },
    })
    stream.start()
  },
  { immediate: true },
)

onBeforeUnmount(() => stream?.close())

async function retry(): Promise<void> {
  actionError.value = ''
  try {
    const accepted = await retryMutation.mutateAsync(agentRunId.value)
    await router.push({ name: 'agent-run-detail', params: { agentRunId: accepted.agentRunId } })
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function cancel(): Promise<void> {
  if (detail.data.value === undefined) return
  actionError.value = ''
  try {
    await cancelMutation.mutateAsync({
      agentRunId: detail.data.value.id,
      stateVersion: detail.data.value.stateVersion,
    })
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}
</script>

<template>
  <section class="agent-run-detail-page app-page">
    <RouterLink class="back-link" to="/agent-runs">
      <AppIcon name="arrow-left" />
      작업 기록
    </RouterLink>
    <StatePanel
      v-if="detail.isLoading.value"
      class="agent-run-detail-page__state"
      kind="loading"
      title="Agent Run 상태를 불러오는 중…"
      description="진행률과 단계 기록을 확인하고 있습니다."
    />
    <StatePanel
      v-else-if="detail.isError.value"
      class="agent-run-detail-page__state"
      kind="error"
      title="작업 상태를 불러오지 못했습니다."
      :description="loadError"
    >
      <template #actions>
        <RouterLink class="button button--secondary" to="/agent-runs">
          작업 기록으로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>
    <template v-else-if="detail.data.value">
      <p v-if="actionError" class="alert alert--danger agent-run-detail-page__message" role="alert">
        {{ actionError }}
      </p>
      <AgentRunDetailPanel
        :run="detail.data.value"
        :connection-state="connectionState"
        :retry-pending="retryMutation.isPending.value"
        :cancel-pending="cancelMutation.isPending.value"
        @retry="retry"
        @cancel="cancel"
      />
    </template>
  </section>
</template>

<style scoped>
.agent-run-detail-page__state,
.agent-run-detail-page__message {
  margin-top: var(--space-5);
}
</style>
