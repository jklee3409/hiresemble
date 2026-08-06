<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  formatUsage,
  formatInstant,
  formatRunProgressLabel,
} from '@/features/agent-runs/presentation'
import {
  useAgentRunListQuery,
  useDeleteAgentRunMutation,
  useDeleteSelectedAgentRunsMutation,
} from '@/features/agent-runs/queries'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseAgentRunFilters(route.query))
const runs = useAgentRunListQuery(userId, filters)
const deleteRun = useDeleteAgentRunMutation(userId)
const deleteSelectedRuns = useDeleteSelectedAgentRunsMutation(userId)
const selectedRunIds = ref<string[]>([])
const commandMessage = ref('')
const commandError = ref('')
const errorMessage = computed(() =>
  runs.error.value ? normalizeApiError(runs.error.value).message : '',
)
const deletableRuns = computed(
  () => runs.data.value?.items.filter((run) => canDelete(run.status)) ?? [],
)
const allDeletableSelected = computed(
  () =>
    deletableRuns.value.length > 0 &&
    deletableRuns.value.every((run) => selectedRunIds.value.includes(run.id)),
)
const isDeleting = computed(() => deleteRun.isPending.value || deleteSelectedRuns.isPending.value)

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

watch(
  () => runs.data.value?.items.map((run) => `${run.id}:${run.status}`).join('|') ?? '',
  () => {
    const visibleDeletableIds = new Set(deletableRuns.value.map((run) => run.id))
    selectedRunIds.value = selectedRunIds.value.filter((id) => visibleDeletableIds.has(id))
  },
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

function canDelete(status: AgentRunStatus): boolean {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(status)
}

function toggleSelected(runId: string, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  selectedRunIds.value = checked
    ? [...new Set([...selectedRunIds.value, runId])]
    : selectedRunIds.value.filter((id) => id !== runId)
}

function toggleAllDeletable(event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  selectedRunIds.value = checked ? deletableRuns.value.map((run) => run.id) : []
}

async function removeOne(runId: string): Promise<void> {
  const confirmed = await notifications.confirm({
    title: '이 AI 작업을 목록에서 지울까요?',
    message: '목록에서는 보이지 않게 되지만 생성된 결과와 안전한 사용량 기록은 유지됩니다.',
    confirmLabel: '내역 삭제',
  })
  if (!confirmed) return
  commandMessage.value = ''
  commandError.value = ''
  try {
    await deleteRun.mutateAsync(runId)
    selectedRunIds.value = selectedRunIds.value.filter((id) => id !== runId)
    commandMessage.value = 'AI 작업을 목록에서 지웠어요.'
    notifications.toast('AI 작업을 목록에서 지웠어요.', 'success')
  } catch (error) {
    commandError.value = normalizeApiError(error).message
  }
}

async function removeSelected(): Promise<void> {
  const ids = [...selectedRunIds.value]
  if (ids.length === 0) return
  const confirmed = await notifications.confirm({
    title: `AI 작업 ${ids.length}개를 목록에서 지울까요?`,
    message: '목록에서는 보이지 않게 되지만 생성된 결과와 안전한 사용량 기록은 유지됩니다.',
    confirmLabel: '선택 내역 삭제',
  })
  if (!confirmed) return
  commandMessage.value = ''
  commandError.value = ''
  try {
    await deleteSelectedRuns.mutateAsync(ids)
    selectedRunIds.value = []
    commandMessage.value = `AI 작업 ${ids.length}개를 목록에서 지웠어요.`
    notifications.toast(`AI 작업 ${ids.length}개를 목록에서 지웠어요.`, 'success')
  } catch (error) {
    commandError.value = normalizeApiError(error).message
  }
}
</script>

<template>
  <section class="run-list-page app-page" aria-labelledby="run-list-heading">
    <PageHeader
      heading-id="run-list-heading"
      title="AI 작업"
      description="이력서와 공고를 읽고 정리하는 과정을 한곳에서 확인할 수 있어요."
      variant="list"
    />

    <details class="filter-disclosure run-list-page__filters" open>
      <summary>AI 작업 필터</summary>
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
                class="checkbox-control"
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
                class="checkbox-control"
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
    </details>

    <div v-if="runs.data.value?.items.length" class="run-selection-toolbar section-surface">
      <label class="run-selection-toolbar__all">
        <input
          class="checkbox-control"
          type="checkbox"
          :checked="allDeletableSelected"
          :disabled="deletableRuns.length === 0 || isDeleting"
          @change="toggleAllDeletable"
        />
        <span>선택</span>
      </label>
      <button
        type="button"
        class="button button--danger button--compact"
        :disabled="selectedRunIds.length === 0 || isDeleting"
        @click="removeSelected"
      >
        {{ isDeleting ? '삭제 중…' : `삭제(${selectedRunIds.length})` }}
      </button>
    </div>
    <p v-if="commandMessage" class="alert alert--success run-list-page__message" role="status">
      {{ commandMessage }}
    </p>
    <p v-if="commandError" class="alert alert--danger run-list-page__message" role="alert">
      {{ commandError }}
    </p>

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
          <label class="run-row__selection">
            <input
              class="checkbox-control"
              type="checkbox"
              :checked="selectedRunIds.includes(run.id)"
              :disabled="!canDelete(run.status) || isDeleting"
              :aria-label="`${WORKFLOW_LABELS[run.workflowType]} 작업 선택`"
              @change="toggleSelected(run.id, $event)"
            />
          </label>
          <div class="run-row__identity">
            <div class="run-row__title">
              <h3>{{ WORKFLOW_LABELS[run.workflowType] }}</h3>
              <StatusBadge :label="STATUS_LABELS[run.status]" :tone="statusTone(run.status)" />
            </div>
            <p>{{ formatRunProgressLabel(run.status) }}</p>
          </div>
          <div class="run-row__actions">
            <RouterLink
              class="button button--secondary button--compact"
              :to="`/agent-runs/${run.id}`"
            >
              상세 보기
            </RouterLink>
            <RouterLink
              v-if="
                run.workflowType === 'JOB_ANALYSIS' && run.resourceType === 'JOB' && run.resourceId
              "
              class="button button--secondary button--compact"
              :to="{ name: 'job-analysis', params: { jobId: run.resourceId } }"
            >
              공고 분석
            </RouterLink>
            <RouterLink
              v-else-if="run.resourceType === 'COVER_LETTER' && run.resourceId"
              class="button button--secondary button--compact"
              :to="{ name: 'cover-letter-edit', params: { coverLetterId: run.resourceId } }"
            >
              자기소개서
            </RouterLink>
            <button
              type="button"
              class="button button--danger button--compact"
              :disabled="!canDelete(run.status) || isDeleting"
              :title="
                canDelete(run.status) ? undefined : '진행 중인 작업은 종료 후 삭제할 수 있어요.'
              "
              @click="removeOne(run.id)"
            >
              삭제
            </button>
          </div>
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
            <dt>이번 작업 사용량</dt>
            <dd>{{ formatUsage(run.actualCostUsd, run.reservedCostUsd) }}</dd>
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
.run-list-page__filters,
.run-selection-toolbar,
.run-list-page__message,
.run-list-page__state,
.run-list {
  margin-top: var(--space-5);
}

.run-filters {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-5);
}

.run-list-page__filters > .run-filters {
  margin-top: var(--space-5);
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
  padding: var(--space-2) var(--space-4);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
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

.run-selection-toolbar,
.run-selection-toolbar__all {
  display: flex;
  align-items: center;
}

.run-selection-toolbar {
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
}

.run-selection-toolbar__all {
  cursor: pointer;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.run-row__selection {
  align-self: flex-start;
  padding-top: var(--space-1);
}

.run-row {
  padding: var(--space-5);
}

.run-row__header,
.run-row__title,
.run-row__progress,
.run-row__actions {
  display: flex;
  align-items: center;
}

.run-row__header {
  justify-content: space-between;
  gap: var(--space-4);
}

.run-row__identity {
  flex: 1;
  min-width: 0;
}

.run-row__title,
.run-row__actions {
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

  .run-selection-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .run-row__actions {
    width: 100%;
  }
}
</style>
