<script setup lang="ts">
import { ref } from 'vue'

import AppIcon from '@/shared/ui/AppIcon.vue'

const props = defineProps<{
  id: string
  label: string
  modelValue: string[]
  error?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const input = ref('')
const localError = ref('')

function add(): void {
  const value = input.value.trim()
  localError.value = ''
  if (value.length === 0) return
  if (value.length > 100) {
    localError.value = '항목은 100자 이하로 입력해 주세요.'
    return
  }
  if (props.modelValue.length >= 10) {
    localError.value = '최대 10개까지 입력할 수 있습니다.'
    return
  }
  if (
    props.modelValue.some((item) => item.trim().toLocaleLowerCase() === value.toLocaleLowerCase())
  ) {
    localError.value = '중복 항목은 추가할 수 없습니다.'
    return
  }
  emit('update:modelValue', [...props.modelValue, value])
  input.value = ''
}

function remove(index: number): void {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, itemIndex) => itemIndex !== index),
  )
}
</script>

<template>
  <fieldset class="string-list">
    <legend class="field-label">{{ label }}</legend>
    <div class="string-list__input-row">
      <input
        :id="id"
        v-model="input"
        class="control"
        type="text"
        maxlength="100"
        :aria-describedby="error || localError ? `${id}-help ${id}-error` : `${id}-help`"
        :aria-invalid="Boolean(error || localError)"
        @keydown.enter.prevent="add"
      />
      <button
        type="button"
        class="button button--secondary"
        :disabled="modelValue.length >= 10"
        @click="add"
      >
        <AppIcon name="plus" />
        추가
      </button>
    </div>
    <p :id="`${id}-help`" class="field-help">
      Enter 또는 추가 버튼으로 등록 · 최대 10개 · 현재 {{ modelValue.length }}개
    </p>
    <p v-if="error || localError" :id="`${id}-error`" class="field-error">
      {{ error || localError }}
    </p>
    <ul v-if="modelValue.length > 0" class="string-list__items" :aria-label="`${label} 목록`">
      <li v-for="(item, index) in modelValue" :key="`${item}-${index}`" class="string-list__item">
        <span>{{ item }}</span>
        <button
          type="button"
          class="string-list__remove"
          :aria-label="`${item} 삭제`"
          @click="remove(index)"
        >
          <AppIcon name="close" />
        </button>
      </li>
    </ul>
  </fieldset>
</template>

<style scoped>
.string-list {
  min-width: 0;
}

.string-list__input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.string-list__items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0.75rem 0 0;
  padding: 0;
  list-style: none;
}

.string-list__item {
  display: inline-flex;
  min-height: 2rem;
  align-items: center;
  gap: 0.25rem;
  border: 1px solid #b5d8db;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  padding: 0.25rem 0.3rem 0.25rem 0.75rem;
  font-size: 0.8125rem;
  font-weight: 620;
}

.string-list__remove {
  display: inline-grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-brand-ink);
  padding: 0;
}

.string-list__remove:hover {
  background: rgb(11 102 115 / 12%);
}

.string-list__remove .icon {
  width: 0.875rem;
  height: 0.875rem;
}

@media (max-width: 399px) {
  .string-list__input-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
