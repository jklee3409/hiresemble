<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { AgentRunStatus } from '@/shared/api/agentRunContracts'
import { useAuthStore } from '@/stores/auth'

import { useAgentRunListQuery } from './queries'
import { STATUS_LABELS, WORKFLOW_LABELS } from './presentation'

const authStore = useAuthStore()
const open = ref(false)
const userId = computed(() => authStore.currentUser?.id ?? '')
const activeFilters = {
  status: ['QUEUED', 'RUNNING', 'WAITING_USER'] as AgentRunStatus[],
  page: 0,
  size: 5,
  sort: 'updatedAt,desc' as const,
}
const activeRuns = useAgentRunListQuery(userId, activeFilters)
const items = computed(() => activeRuns.data.value?.items ?? [])
const activeCount = computed(() => activeRuns.data.value?.totalElements ?? 0)
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="open = !open"
    >
      진행 작업 {{ activeCount }}
    </button>
    <section
      v-if="open"
      class="absolute right-0 z-20 mt-2 w-80 rounded-xl border border-slate-200 bg-white p-4 shadow-xl"
      role="dialog"
      aria-label="Agent Run 진행 현황"
    >
      <div class="flex items-center justify-between">
        <h2 class="font-semibold">최근 활성 작업</h2>
        <button type="button" class="text-sm text-slate-600" @click="open = false">닫기</button>
      </div>
      <p class="mt-1 text-xs text-slate-500">
        조회된 최근 활성 작업 {{ items.length }}개 (최대 5개)
      </p>
      <p v-if="activeRuns.isLoading.value" class="mt-4 text-sm text-slate-500">불러오는 중…</p>
      <p v-else-if="activeRuns.isError.value" class="mt-4 text-sm text-red-700">
        진행 상태를 불러오지 못했습니다.
      </p>
      <p v-else-if="items.length === 0" class="mt-4 text-sm text-slate-500">
        현재 표시할 활성 작업이 없습니다.
      </p>
      <ul v-else class="mt-3 space-y-2">
        <li v-for="run in items" :key="run.id">
          <RouterLink
            class="block rounded-lg border border-slate-200 p-3 hover:bg-slate-50"
            :to="`/agent-runs/${run.id}`"
            @click="open = false"
          >
            <strong class="block text-sm">{{ WORKFLOW_LABELS[run.workflowType] }}</strong>
            <span class="text-xs text-slate-600"
              >{{ STATUS_LABELS[run.status] }} · {{ run.progressPercent }}%</span
            >
          </RouterLink>
        </li>
      </ul>
      <RouterLink
        class="mt-4 inline-block text-sm font-semibold text-indigo-700"
        to="/agent-runs"
        @click="open = false"
      >
        작업 기록 전체 보기
      </RouterLink>
    </section>
  </div>
</template>
