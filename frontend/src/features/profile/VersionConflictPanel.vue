<script setup lang="ts">
import { reactive } from 'vue'

import AppIcon from '@/shared/ui/AppIcon.vue'

import { reapplySelectedFields } from './conflict'

interface ConflictField {
  key: string
  label: string
}

const props = defineProps<{
  draft: Record<string, unknown>
  latest: Record<string, unknown>
  fields: ConflictField[]
}>()

const emit = defineEmits<{
  cancel: []
  reapply: [value: Record<string, unknown>]
}>()

const selected = reactive<Record<string, boolean>>(
  Object.fromEntries(props.fields.map((field) => [field.key, true])),
)

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '비어 있음'
  if (Array.isArray(value)) return value.length === 0 ? '비어 있음' : value.join(', ')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function reapply(): void {
  const fields = props.fields.filter((field) => selected[field.key]).map((field) => field.key)
  emit('reapply', reapplySelectedFields(props.latest, props.draft, fields))
}
</script>

<template>
  <section class="conflict-panel" aria-labelledby="version-conflict-title" role="alert">
    <header class="conflict-panel__header">
      <AppIcon name="alert" />
      <div>
        <h3 id="version-conflict-title">다른 곳에서 최신 내용이 저장됐어요</h3>
        <p>
          자동으로 덮어쓰지 않아요. 유지할 내 입력을 선택해 최신 내용에 재적용한 뒤 다시 저장해
          주세요.
        </p>
      </div>
    </header>

    <div class="conflict-panel__legend" aria-hidden="true">
      <span>내 미저장 값</span>
      <span>최근 저장된 값</span>
    </div>
    <ul class="conflict-list">
      <li v-for="field in fields" :key="field.key" class="conflict-item">
        <label class="conflict-item__choice">
          <input
            v-model="selected[field.key]"
            class="checkbox-control"
            type="checkbox"
            :aria-label="`${field.label} 내 값 재적용`"
          />
          <strong>{{ field.label }}</strong>
        </label>
        <div class="conflict-value conflict-value--draft">
          <span>내 미저장 값</span>
          <p>{{ display(draft[field.key]) }}</p>
        </div>
        <div class="conflict-value">
          <span>최근 저장된 값</span>
          <p>{{ display(latest[field.key]) }}</p>
        </div>
      </li>
    </ul>

    <div class="conflict-panel__actions">
      <button type="button" class="button button--primary" @click="reapply">
        선택 항목 재적용
      </button>
      <button type="button" class="button button--secondary" @click="emit('cancel')">
        취소하고 최신값 사용
      </button>
    </div>
  </section>
</template>

<style scoped>
.conflict-panel {
  overflow: hidden;
  border: 1px solid #dfbf69;
  border-radius: var(--radius-lg);
  background: #fffaf0;
}

.conflict-panel__header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  border-bottom: 1px solid #ead7a3;
  background: var(--color-warning-soft);
  color: #704905;
  padding: 1rem;
}

.conflict-panel__header > .icon {
  margin-top: 0.15rem;
}

.conflict-panel__header h3 {
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 730;
}

.conflict-panel__header p {
  margin: 0.25rem 0 0;
  font-size: 0.8125rem;
  line-height: 1.6;
}

.conflict-panel__legend {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-left: min(15rem, 30%);
  border-bottom: 1px solid var(--color-border);
  color: var(--color-muted);
  padding: 0.625rem 1rem;
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
}

.conflict-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.conflict-item {
  display: grid;
  grid-template-columns: minmax(8rem, 0.7fr) minmax(0, 1fr) minmax(0, 1fr);
  gap: 0.75rem;
  align-items: stretch;
  padding: 0.75rem 1rem;
}

.conflict-item + .conflict-item {
  border-top: 1px solid var(--color-border);
}

.conflict-item__choice {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 0.5rem;
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
}

.conflict-value {
  min-width: 0;
  border-left: 2px solid var(--color-border);
  padding-left: 0.75rem;
}

.conflict-value--draft {
  border-color: #6eaab1;
}

.conflict-value span {
  display: none;
  color: var(--color-muted);
  font-size: 0.6875rem;
  font-weight: 700;
}

.conflict-value p {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
  line-height: 1.55;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.conflict-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: 1rem;
}

@media (max-width: 639px) {
  .conflict-panel__legend {
    display: none;
  }

  .conflict-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.625rem;
  }

  .conflict-value span {
    display: block;
    margin-bottom: 0.2rem;
  }

  .conflict-panel__actions > .button {
    flex: 1 1 100%;
  }
}
</style>
