<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  AGENT_RUN_STATUSES,
  WORKFLOW_TYPES,
  type AgentRunStatus,
  type WorkflowType,
} from '@/shared/api/agentRunContracts'
import { normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'
import {
  agentRunQuerySignature,
  canonicalAgentRunQuery,
  parseAgentRunFilters,
  type AgentRunListFilters,
} from '@/features/agent-runs/filters'
import {
  STATUS_LABELS,
  WORKFLOW_LABELS,
  formatCost,
  formatInstant,
} from '@/features/agent-runs/presentation'
import { useAgentRunListQuery } from '@/features/agent-runs/queries'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseAgentRunFilters(route.query))
const runs = useAgentRunListQuery(userId, filters)
const errorMessage = computed(() =>
  runs.error.value ? normalizeApiError(runs.error.value).message : '',
)

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalAgentRunQuery(parseAgentRunFilters(query))
    if (agentRunQuerySignature(query) !== agentRunQuerySignature(canonical)) {
      void router.replace({ query: canonical })
    }
  },
  { deep: true, immediate: true },
)

function replaceFilters(next: AgentRunListFilters): void {
  void router.replace({ query: canonicalAgentRunQuery(next) })
}

function toggleWorkflow(workflowType: WorkflowType, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  replaceFilters({
    ...filters.value,
    workflowType: checked
      ? [...filters.value.workflowType, workflowType]
      : filters.value.workflowType.filter((value) => value !== workflowType),
    page: 0,
  })
}

function toggleStatus(status: AgentRunStatus, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  replaceFilters({
    ...filters.value,
    status: checked
      ? [...filters.value.status, status]
      : filters.value.status.filter((value) => value !== status),
    page: 0,
  })
}

function changeRetryable(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  const next = { ...filters.value, page: 0 }
  if (value === '') delete next.retryable
  else next.retryable = value === 'true'
  replaceFilters(next)
}

function changeSort(event: Event): void {
  replaceFilters({
    ...filters.value,
    sort: (event.target as HTMLSelectElement).value as AgentRunListFilters['sort'],
    page: 0,
  })
}

function changePage(page: number): void {
  replaceFilters({ ...filters.value, page })
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h2 class="text-2xl font-semibold">Agent Run 작업 기록</h2>
      <p class="mt-1 text-sm text-slate-600">
        접수된 비동기 작업의 안전한 상태와 비용 추정을 확인합니다.
      </p>
    </div>

    <form class="rounded-xl border border-slate-200 bg-white p-5" @submit.prevent>
      <fieldset>
        <legend class="font-semibold">Workflow</legend>
        <div class="mt-2 flex flex-wrap gap-3">
          <label v-for="workflowType in WORKFLOW_TYPES" :key="workflowType" class="text-sm">
            <input
              type="checkbox"
              class="mr-1"
              :checked="filters.workflowType.includes(workflowType)"
              @change="toggleWorkflow(workflowType, $event)"
            />
            {{ WORKFLOW_LABELS[workflowType] }}
          </label>
        </div>
      </fieldset>
      <fieldset class="mt-4">
        <legend class="font-semibold">상태</legend>
        <div class="mt-2 flex flex-wrap gap-3">
          <label v-for="status in AGENT_RUN_STATUSES" :key="status" class="text-sm">
            <input
              type="checkbox"
              class="mr-1"
              :checked="filters.status.includes(status)"
              @change="toggleStatus(status, $event)"
            />
            {{ STATUS_LABELS[status] }}
          </label>
        </div>
      </fieldset>
      <div class="mt-4 flex flex-wrap gap-4">
        <label class="text-sm">
          재시도 가능
          <select
            class="ml-2 rounded-lg border border-slate-300 px-2 py-1"
            :value="filters.retryable === undefined ? '' : String(filters.retryable)"
            @change="changeRetryable"
          >
            <option value="">전체</option>
            <option value="true">가능</option>
            <option value="false">불가능</option>
          </select>
        </label>
        <label class="text-sm">
          정렬
          <select
            class="ml-2 rounded-lg border border-slate-300 px-2 py-1"
            :value="filters.sort"
            @change="changeSort"
          >
            <option value="queuedAt,desc">최근 접수순</option>
            <option value="updatedAt,desc">최근 갱신순</option>
          </select>
        </label>
      </div>
    </form>

    <p v-if="runs.isLoading.value" class="rounded-xl bg-white p-6 text-slate-600">
      작업 기록을 불러오는 중입니다.
    </p>
    <p v-else-if="runs.isError.value" class="rounded-xl bg-red-50 p-6 text-red-800" role="alert">
      {{ errorMessage }}
    </p>
    <div
      v-else-if="runs.data.value?.items.length === 0"
      class="rounded-xl bg-white p-8 text-center text-slate-600"
    >
      조건에 맞는 작업 기록이 없습니다.
    </div>
    <div v-else class="space-y-3">
      <article
        v-for="run in runs.data.value?.items"
        :key="run.id"
        class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h3 class="font-semibold">{{ WORKFLOW_LABELS[run.workflowType] }}</h3>
            <p class="mt-1 text-sm text-slate-600">
              {{ STATUS_LABELS[run.status] }} · {{ run.progressPercent }}%
            </p>
          </div>
          <RouterLink class="font-semibold text-indigo-700" :to="`/agent-runs/${run.id}`"
            >상세 보기</RouterLink
          >
        </div>
        <dl class="mt-4 grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt class="text-slate-500">접수</dt>
            <dd>{{ formatInstant(run.queuedAt) }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">최근 갱신</dt>
            <dd>{{ formatInstant(run.updatedAt) }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">비용 추정</dt>
            <dd>{{ formatCost(run.actualCostUsd) }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">재시도</dt>
            <dd>{{ run.retryable ? '가능' : '불가능' }}</dd>
          </div>
        </dl>
      </article>
    </div>

    <nav
      v-if="runs.data.value && runs.data.value.totalPages > 0"
      class="flex items-center justify-center gap-3"
      aria-label="작업 기록 페이지"
    >
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 disabled:opacity-50"
        :disabled="filters.page === 0"
        @click="changePage(filters.page - 1)"
      >
        이전
      </button>
      <span class="text-sm">{{ filters.page + 1 }} / {{ runs.data.value.totalPages }}</span>
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 disabled:opacity-50"
        :disabled="filters.page + 1 >= runs.data.value.totalPages"
        @click="changePage(filters.page + 1)"
      >
        다음
      </button>
    </nav>
  </section>
</template>
