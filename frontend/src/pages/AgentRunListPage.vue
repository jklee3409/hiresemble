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
  formatRunProgressLabel,
} from '@/features/agent-runs/presentation'
import { useAgentRunListQuery } from '@/features/agent-runs/queries'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

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

function statusTone(value: AgentRunStatus): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return (
    {
      QUEUED: 'neutral',
      RUNNING: 'info',
      WAITING_USER: 'warning',
      SUCCEEDED: 'success',
      FAILED: 'danger',
      CANCELLED: 'neutral',
      INTERRUPTED: 'warning',
    } as const
  )[value]
}
</script>

<template>
  <section class="run-list-page app-page" aria-labelledby="run-list-heading">
    <PageHeader
      heading-id="run-list-heading"
      title="AI 작업"
      description="이력서와 공고를 정리하는 작업의 진행 상황을 확인하세요."
      eyebrow="준비 진행 상황"
    />

    <form class="run-filters filter-toolbar" @submit.prevent>
      <fieldset class="run-filter-group run-filter-group--workflow">
        <legend>작업 종류</legend>
        <div class="run-filter-options">
          <label
            v-for="workflowType in WORKFLOW_TYPES"
            :key="workflowType"
            class="run-filter-option"
          >
            <input
              type="checkbox"
              :checked="filters.workflowType.includes(workflowType)"
              @change="toggleWorkflow(workflowType, $event)"
            />
            <span>{{ WORKFLOW_LABELS[workflowType] }}</span>
          </label>
        </div>
      </fieldset>
      <fieldset class="run-filter-group">
        <legend>상태</legend>
        <div class="run-filter-options">
          <label v-for="status in AGENT_RUN_STATUSES" :key="status" class="run-filter-option">
            <input
              type="checkbox"
              :checked="filters.status.includes(status)"
              @change="toggleStatus(status, $event)"
            />
            <span>{{ STATUS_LABELS[status] }}</span>
          </label>
        </div>
      </fieldset>
      <div class="run-filter-selects">
        <label class="field">
          <span class="field__label">재시도 가능</span>
          <select
            class="control control--compact"
            :value="filters.retryable === undefined ? '' : String(filters.retryable)"
            @change="changeRetryable"
          >
            <option value="">전체</option>
            <option value="true">가능</option>
            <option value="false">불가능</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">정렬</span>
          <select class="control control--compact" :value="filters.sort" @change="changeSort">
            <option value="queuedAt,desc">최근 접수순</option>
            <option value="updatedAt,desc">최근 갱신순</option>
          </select>
        </label>
      </div>
    </form>

    <StatePanel
      v-if="runs.isLoading.value"
      class="run-list-page__state"
      kind="loading"
      title="AI 작업을 불러오는 중…"
      description="시작한 작업과 최신 진행 상황을 확인하고 있어요."
    />
    <StatePanel
      v-else-if="runs.isError.value"
      class="run-list-page__state"
      kind="error"
      title="AI 작업을 불러오지 못했어요."
      :description="errorMessage"
    />
    <StatePanel
      v-else-if="runs.data.value?.items.length === 0"
      class="run-list-page__state"
      kind="empty"
      title="조건에 맞는 AI 작업이 없어요."
      description="필터를 바꾸거나 자료와 공고를 등록하면 진행 상황이 이곳에 표시돼요."
    />
    <div v-else class="run-list data-list">
      <article v-for="run in runs.data.value?.items" :key="run.id" class="run-row data-card">
        <div class="run-row__header">
          <div class="run-row__identity">
            <div class="run-row__title">
              <h3>{{ WORKFLOW_LABELS[run.workflowType] }}</h3>
              <StatusBadge :label="STATUS_LABELS[run.status]" :tone="statusTone(run.status)" />
            </div>
            <p>{{ formatRunProgressLabel(run.status) }}</p>
          </div>
          <RouterLink
            class="button button--secondary button--compact"
            :to="`/agent-runs/${run.id}`"
          >
            상세 보기
          </RouterLink>
        </div>
        <div class="run-row__progress" aria-label="진행률">
          <progress class="progress-track" :value="run.progressPercent" max="100">
            {{ run.progressPercent }}%
          </progress>
          <span>{{ run.progressPercent }}%</span>
        </div>
        <dl class="run-row__meta">
          <div>
            <dt>접수</dt>
            <dd>{{ formatInstant(run.queuedAt) }}</dd>
          </div>
          <div>
            <dt>최근 갱신</dt>
            <dd>{{ formatInstant(run.updatedAt) }}</dd>
          </div>
          <div>
            <dt>예상 사용 비용</dt>
            <dd>{{ formatCost(run.actualCostUsd) }}</dd>
          </div>
          <div>
            <dt>재시도</dt>
            <dd>{{ run.retryable ? '가능' : '불가능' }}</dd>
          </div>
        </dl>
      </article>
    </div>

    <PaginationNav
      v-if="runs.data.value && runs.data.value.totalPages > 0"
      :page="filters.page"
      :total-pages="runs.data.value.totalPages"
      label="AI 작업 페이지"
      @change="changePage"
    />
  </section>
</template>

<style scoped>
.run-filters,
.run-list-page__state,
.run-list {
  margin-top: var(--space-5);
}

.run-filters {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-5);
}

.run-filter-group legend {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.run-filter-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.run-filter-option {
  display: flex;
  min-height: 2.5rem;
  cursor: pointer;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.run-filter-option:has(input:checked) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-weight: 700;
}

.run-filter-option input {
  width: 1rem;
  height: 1rem;
  accent-color: var(--color-brand);
}

.run-filter-selects {
  display: grid;
  grid-template-columns: repeat(2, minmax(10rem, 14rem));
  gap: var(--space-3);
}

.run-row {
  padding: var(--space-5);
}

.run-row__header,
.run-row__title,
.run-row__progress {
  display: flex;
  align-items: center;
}

.run-row__header {
  justify-content: space-between;
  gap: var(--space-4);
}

.run-row__identity {
  min-width: 0;
}

.run-row__title {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.run-row__title h3 {
  font-weight: 750;
}

.run-row__identity > p {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.run-row__progress {
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.run-row__progress progress {
  flex: 1;
}

.run-row__progress span {
  min-width: 3rem;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.run-row__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.run-row__meta dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.run-row__meta dd {
  margin-top: var(--space-1);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

@media (max-width: 64rem) {
  .run-filter-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .run-row__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .run-filter-options,
  .run-filter-selects,
  .run-row__meta {
    grid-template-columns: 1fr;
  }

  .run-row__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
