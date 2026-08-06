<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import type { AgentRunStatus } from '@/shared/api/agentRunContracts'
import { useAuthStore } from '@/stores/auth'

import { useAgentRunListQuery } from './queries'
import { STATUS_LABELS, WORKFLOW_LABELS, formatRunProgressLabel } from './presentation'

const authStore = useAuthStore()
const open = ref(false)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
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

function openDrawer(): void {
  open.value = true
  void nextTick(() => panel.value?.querySelector<HTMLElement>('button, a[href]')?.focus())
}

function closeDrawer(restoreFocus = true): void {
  open.value = false
  if (restoreFocus) void nextTick(() => trigger.value?.focus())
}

function toggleDrawer(): void {
  if (open.value) closeDrawer()
  else openDrawer()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDrawer()
    return
  }
  if (event.key !== 'Tab' || panel.value === null) return
  const focusable = Array.from(
    panel.value.querySelectorAll<HTMLElement>('a[href], button:not([disabled])'),
  )
  const first = focusable[0]
  const last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function tone(status: AgentRunStatus) {
  return status === 'WAITING_USER' ? 'warning' : status === 'RUNNING' ? 'brand' : 'info'
}
</script>

<template>
  <div class="run-progress">
    <button
      ref="trigger"
      type="button"
      class="run-progress__trigger"
      aria-haspopup="dialog"
      aria-controls="agent-run-progress-panel"
      :aria-expanded="open"
      @click="toggleDrawer"
    >
      <AppIcon name="clock" />
      <span class="run-progress__trigger-label">진행 중인 분석</span>
      <span class="run-progress__count">{{ activeCount }}</span>
    </button>
    <section
      v-if="open"
      id="agent-run-progress-panel"
      ref="panel"
      class="run-drawer"
      role="dialog"
      aria-label="진행 중인 분석"
      @keydown="onKeydown"
    >
      <header class="run-drawer__header">
        <div>
          <p class="page-eyebrow">지금 진행 중</p>
          <h2 id="agent-run-progress-title">진행 중인 분석</h2>
        </div>
        <button
          type="button"
          class="button button--ghost button--icon"
          aria-label="진행 중인 분석 닫기"
          @click="closeDrawer()"
        >
          <AppIcon name="close" />
        </button>
      </header>

      <div class="run-drawer__body">
        <p class="run-drawer__summary">
          최근 진행 중인 분석 {{ items.length }}개를 보여 드려요. 전체 {{ activeCount }}개
        </p>
        <div v-if="activeRuns.isLoading.value" class="run-drawer__state" role="status">
          <div class="skeleton-stack" aria-hidden="true">
            <div class="skeleton-line" />
            <div class="skeleton-line" />
          </div>
          <span>분석 진행 상황을 불러오는 중…</span>
        </div>
        <div v-else-if="activeRuns.isError.value" class="alert alert--danger" role="alert">
          분석 진행 상황을 불러오지 못했어요.
        </div>
        <div v-else-if="items.length === 0" class="run-drawer__state">
          <AppIcon name="check" />
          <span>지금 진행 중인 분석이 없어요.</span>
        </div>
        <ul v-else class="run-drawer__list">
          <li v-for="run in items" :key="run.id">
            <RouterLink
              class="run-drawer__item"
              :to="`/agent-runs/${run.id}`"
              @click="closeDrawer(false)"
            >
              <span class="run-drawer__item-top">
                <strong>{{ WORKFLOW_LABELS[run.workflowType] }}</strong>
                <StatusBadge :label="STATUS_LABELS[run.status]" :tone="tone(run.status)" />
              </span>
              <span class="run-drawer__progress-label">
                <span>{{ formatRunProgressLabel(run.status) }}</span>
                <span>{{ run.progressPercent }}%</span>
              </span>
              <progress class="progress-track" :value="run.progressPercent" max="100">
                {{ run.progressPercent }}%
              </progress>
            </RouterLink>
          </li>
        </ul>
      </div>

      <footer class="run-drawer__footer">
        <RouterLink class="text-link" to="/agent-runs" @click="closeDrawer(false)">
          AI 작업 전체 보기
          <AppIcon name="arrow-right" />
        </RouterLink>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.run-progress {
  position: relative;
}

.run-progress__trigger {
  display: inline-flex;
  min-height: 2.375rem;
  align-items: center;
  gap: 0.4rem;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  color: var(--color-ink-soft);
  padding: 0.4rem 0.5rem 0.4rem 0.625rem;
  font-size: 0.8125rem;
  font-weight: 680;
}

.run-progress__trigger:hover {
  background: var(--color-fill-strong);
}

.run-progress__count {
  display: inline-grid;
  min-width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  padding-inline: 0.3rem;
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
}

.run-drawer {
  position: fixed;
  top: 4.5rem;
  right: 0.75rem;
  left: 0.75rem;
  z-index: 60;
  max-height: calc(100dvh - 5.25rem);
  overflow: auto;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  animation: run-drawer-enter 240ms cubic-bezier(0.2, 0, 0, 1) both;
}

.run-drawer__header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: 1rem;
}

.run-drawer__header h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 1rem;
  font-weight: 720;
}

.run-drawer__body {
  padding: 1rem;
}

.run-drawer__summary {
  margin: 0 0 0.875rem;
  color: var(--color-muted);
  font-size: 0.75rem;
}

.run-drawer__state {
  display: grid;
  justify-items: center;
  gap: 0.75rem;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-muted);
  padding: 1.5rem 1rem;
  font-size: 0.8125rem;
  text-align: center;
}

.run-drawer__list {
  display: grid;
  gap: 0.625rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.run-drawer__item {
  display: grid;
  gap: 0.625rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  color: var(--color-ink-soft);
  padding: 0.875rem;
  text-decoration: none;
}

.run-drawer__item:hover {
  background: var(--color-brand-soft);
}

.run-drawer__item-top,
.run-drawer__progress-label {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.run-drawer__item-top strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 0.8125rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-drawer__progress-label {
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.run-drawer__progress-label span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-drawer__footer {
  border-top: 1px solid var(--color-border);
  padding: 0.875rem 1rem;
}

.run-drawer__footer .text-link {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
}

@media (min-width: 640px) {
  .run-drawer {
    position: absolute;
    top: calc(100% + 0.625rem);
    right: 0;
    left: auto;
    width: 24rem;
    max-height: min(38rem, calc(100dvh - 6rem));
  }
}

@media (max-width: 479px) {
  .run-progress__trigger-label {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
  }
}

@keyframes run-drawer-enter {
  from {
    opacity: 0;
    transform: translateY(-0.5rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .run-drawer {
    animation: none;
  }
}
</style>
