<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue'

import AppIcon from '@/shared/ui/AppIcon.vue'

const props = defineProps<{
  id: string
  label: string
  modelValue: string[]
  error?: string
  placeholder?: string
  help?: string
  presets?: readonly string[]
  suggestions?: readonly string[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const input = ref('')
const localError = ref('')
const suggestionsVisible = ref(false)
const presetsExpanded = ref(false)
const textInput = useTemplateRef<HTMLInputElement>('textInput')
const suggestionButtons = useTemplateRef<HTMLButtonElement[]>('suggestionButton')

const availablePresets = computed(() => (props.presets ?? []).filter((option) => !hasValue(option)))
const visiblePresets = computed(() =>
  presetsExpanded.value ? availablePresets.value : availablePresets.value.slice(0, 4),
)
const matchingSuggestions = computed(() => {
  const query = input.value.trim().toLocaleLowerCase()
  if (!suggestionsVisible.value || query.length === 0) return []

  return (props.suggestions ?? [])
    .filter((option) => option.toLocaleLowerCase().includes(query) && !hasValue(option))
    .slice(0, 6)
})

function add(): void {
  addValue(input.value)
}

function addValue(rawValue: string): void {
  const value = rawValue.trim()
  localError.value = ''
  if (value.length === 0) return
  if (value.length > 100) {
    localError.value = '항목은 100자 이하로 입력해 주세요.'
    return
  }
  if (props.modelValue.length >= 10) {
    localError.value = '항목은 최대 10개까지 입력해 주세요.'
    return
  }
  if (
    props.modelValue.some((item) => item.trim().toLocaleLowerCase() === value.toLocaleLowerCase())
  ) {
    localError.value = '이미 추가한 항목이에요.'
    return
  }
  emit('update:modelValue', [...props.modelValue, value])
  input.value = ''
  suggestionsVisible.value = false
}

function remove(index: number): void {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, itemIndex) => itemIndex !== index),
  )
}

function hasValue(value: string): boolean {
  return props.modelValue.some(
    (item) => item.trim().toLocaleLowerCase() === value.trim().toLocaleLowerCase(),
  )
}

function showSuggestions(): void {
  suggestionsVisible.value = true
}

async function focusFirstSuggestion(): Promise<void> {
  if (matchingSuggestions.value.length === 0) return
  await nextTick()
  suggestionButtons.value?.[0]?.focus()
}

function moveSuggestionFocus(index: number, direction: 1 | -1): void {
  const buttons = suggestionButtons.value ?? []
  const nextIndex = index + direction
  if (nextIndex < 0) {
    textInput.value?.focus()
    return
  }
  buttons[nextIndex >= buttons.length ? 0 : nextIndex]?.focus()
}

function onEntryFocusOut(event: FocusEvent): void {
  const nextTarget = event.relatedTarget
  if (nextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(nextTarget))
    return
  suggestionsVisible.value = false
}

function returnToInput(): void {
  suggestionsVisible.value = false
  textInput.value?.focus()
}
</script>

<template>
  <fieldset class="string-list">
    <legend class="field-label">{{ label }}</legend>
    <div class="string-list__entry" @focusout="onEntryFocusOut">
      <div class="string-list__input-row">
        <input
          :id="id"
          ref="textInput"
          v-model="input"
          class="control"
          type="text"
          maxlength="100"
          autocomplete="off"
          :role="suggestions?.length ? 'combobox' : undefined"
          :aria-label="label"
          :placeholder="placeholder"
          :aria-autocomplete="suggestions?.length ? 'list' : undefined"
          :aria-controls="suggestions?.length ? `${id}-suggestions` : undefined"
          :aria-expanded="suggestions?.length ? matchingSuggestions.length > 0 : undefined"
          :aria-describedby="error || localError ? `${id}-help ${id}-error` : `${id}-help`"
          :aria-invalid="Boolean(error || localError)"
          @focus="showSuggestions"
          @input="showSuggestions"
          @keydown.enter.prevent="add"
          @keydown.down.prevent="focusFirstSuggestion"
          @keydown.esc="suggestionsVisible = false"
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
      <ul
        v-if="matchingSuggestions.length > 0"
        :id="`${id}-suggestions`"
        class="string-list__suggestions"
        role="listbox"
        :aria-label="`${label} 추천`"
      >
        <li v-for="(option, index) in matchingSuggestions" :key="option" role="none">
          <button
            ref="suggestionButton"
            type="button"
            role="option"
            aria-selected="false"
            @mousedown.prevent
            @click="addValue(option)"
            @keydown.esc.prevent="returnToInput"
            @keydown.down.prevent="moveSuggestionFocus(index, 1)"
            @keydown.up.prevent="moveSuggestionFocus(index, -1)"
          >
            <span>{{ option }}</span>
            <small>선택</small>
          </button>
        </li>
      </ul>
    </div>
    <p :id="`${id}-help`" class="field-help">
      {{ help ?? `직접 입력 후 Enter 또는 추가 버튼으로 등록 · 최대 10개` }} · 현재
      {{ modelValue.length }}개
    </p>
    <div v-if="modelValue.length > 0" class="string-list__selected">
      <span>선택한 항목</span>
      <ul class="string-list__items" :aria-label="`${label} 목록`">
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
    </div>
    <div v-if="availablePresets.length > 0" class="string-list__presets">
      <div class="string-list__presets-heading">
        <span>추천 항목</span>
        <button
          v-if="availablePresets.length > 4"
          type="button"
          class="string-list__preset-toggle"
          :aria-expanded="presetsExpanded"
          @click="presetsExpanded = !presetsExpanded"
        >
          {{ presetsExpanded ? '간단히 보기' : '추천 더 보기' }}
        </button>
      </div>
      <div>
        <button
          v-for="option in visiblePresets"
          :key="option"
          type="button"
          :disabled="modelValue.length >= 10"
          @click="addValue(option)"
        >
          <AppIcon name="plus" />
          {{ option }}
        </button>
      </div>
    </div>
    <p v-if="error || localError" :id="`${id}-error`" class="field-error">
      {{ error || localError }}
    </p>
  </fieldset>
</template>

<style scoped>
.string-list {
  min-width: 0;
}

.string-list__entry {
  position: relative;
}

.string-list__input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.string-list__suggestions {
  position: absolute;
  top: calc(100% + 0.375rem);
  right: 0;
  left: 0;
  z-index: 10;
  display: grid;
  gap: 0.125rem;
  margin: 0;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface-raised);
  box-shadow: var(--shadow-md);
  padding: 0.375rem;
  list-style: none;
}

.string-list__suggestions button {
  display: flex;
  width: 100%;
  min-height: 2.75rem;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-ink);
  padding: 0.625rem 0.75rem;
  text-align: left;
}

.string-list__suggestions button:hover,
.string-list__suggestions button:focus-visible {
  background: var(--color-brand-soft);
}

.string-list__suggestions small {
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-weight: 700;
}

.string-list__presets {
  display: grid;
  gap: 0.5rem;
  margin-top: 1rem;
}

.string-list__presets-heading,
.string-list__selected > span {
  color: var(--color-muted);
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.string-list__presets-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.string-list__preset-toggle {
  min-height: 2rem;
  border: 0;
  background: transparent;
  color: var(--color-brand);
  padding: 0.25rem;
  font-size: 0.75rem;
  font-weight: 700;
}

.string-list__presets > div {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.string-list__presets > div:last-child > button {
  display: inline-flex;
  min-height: 2.5rem;
  align-items: center;
  gap: 0.25rem;
  border: 0;
  border-radius: 999px;
  background: var(--color-fill);
  color: var(--color-ink-soft);
  padding: 0.375rem 0.75rem;
  font-size: 0.75rem;
  font-weight: 650;
}

.string-list__presets > div:last-child > button:hover:not(:disabled) {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
}

.string-list__presets > div:last-child .icon {
  width: 0.75rem;
  height: 0.75rem;
}

.string-list__items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}

.string-list__item {
  display: inline-flex;
  min-height: 2rem;
  align-items: center;
  gap: 0.25rem;
  border: 0;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  padding: 0.25rem 0.3rem 0.25rem 0.75rem;
  font-size: 0.8125rem;
  font-weight: 620;
}

.string-list__remove {
  display: inline-grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-brand-ink);
  padding: 0;
}

.string-list__remove:hover {
  background: var(--hs-blue-100);
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
