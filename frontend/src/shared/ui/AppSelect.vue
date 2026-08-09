<script lang="ts">
export interface AppSelectOption<T extends string = string> {
  value: T
  label: string
  disabled?: boolean
  description?: string
}

let selectSequence = 0

function nextSelectId(): number {
  selectSequence += 1
  return selectSequence
}
</script>

<script setup lang="ts" generic="T extends string">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

/*
 * 서비스 공통 선택 control.
 *
 * 브라우저 기본 `<select>`의 option 목록은 OS가 그리기 때문에 제품 design token을
 * 적용할 수 없다. 그래서 trigger + listbox를 직접 그리고 WAI-ARIA combobox 패턴의
 * `aria-activedescendant`로 키보드 조작을 유지한다. DOM focus는 항상 trigger에 남는다.
 */

const props = withDefaults(
  defineProps<{
    modelValue: T
    options: readonly AppSelectOption<T>[]
    id?: string
    placeholder?: string
    disabled?: boolean
    compact?: boolean
    invalid?: boolean
    ariaLabel?: string
    ariaLabelledby?: string
    ariaDescribedby?: string
  }>(),
  {
    id: undefined,
    placeholder: '선택해 주세요',
    disabled: false,
    compact: false,
    invalid: false,
    ariaLabel: undefined,
    ariaLabelledby: undefined,
    ariaDescribedby: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: T]
  change: [value: T]
}>()

const uid = `app-select-${nextSelectId()}`
const triggerId = computed(() => props.id ?? `${uid}-trigger`)
const listboxId = `${uid}-listbox`

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const listbox = ref<HTMLElement | null>(null)
const open = ref(false)
const activeIndex = ref(-1)
const dropUp = ref(false)

const selectedIndex = computed(() =>
  props.options.findIndex((option) => option.value === props.modelValue),
)
const selectedOption = computed(() =>
  selectedIndex.value >= 0 ? props.options[selectedIndex.value] : undefined,
)
const displayLabel = computed(() => selectedOption.value?.label ?? props.placeholder)
const activeDescendant = computed(() =>
  open.value && activeIndex.value >= 0 ? optionId(activeIndex.value) : undefined,
)

function optionId(index: number): string {
  return `${uid}-option-${index}`
}

function isSelectable(index: number): boolean {
  const option = props.options[index]
  return option !== undefined && option.disabled !== true
}

function firstSelectable(from: number, step: 1 | -1): number {
  for (let index = from; index >= 0 && index < props.options.length; index += step) {
    if (isSelectable(index)) return index
  }
  return -1
}

function updateDropDirection(): void {
  const element = trigger.value
  if (!element || typeof element.getBoundingClientRect !== 'function') return
  const rect = element.getBoundingClientRect()
  const viewportHeight = window.innerHeight || 0
  if (viewportHeight === 0) return
  // 아래 공간이 패널 최대 높이보다 좁고 위 공간이 더 넓을 때만 위로 편다.
  dropUp.value = viewportHeight - rect.bottom < 260 && rect.top > viewportHeight - rect.bottom
}

async function openList(preferred?: number): Promise<void> {
  if (props.disabled || open.value) return
  updateDropDirection()
  open.value = true
  const fallback = selectedIndex.value >= 0 ? selectedIndex.value : firstSelectable(0, 1)
  activeIndex.value = preferred !== undefined && isSelectable(preferred) ? preferred : fallback
  await nextTick()
  scrollActiveIntoView()
}

function closeList(refocus = true): void {
  if (!open.value) return
  open.value = false
  activeIndex.value = -1
  if (refocus) trigger.value?.focus()
}

function scrollActiveIntoView(): void {
  const element = listbox.value?.querySelector<HTMLElement>('[data-active="true"]')
  element?.scrollIntoView?.({ block: 'nearest' })
}

function commit(index: number): void {
  const option = props.options[index]
  if (!option || option.disabled === true) return
  closeList()
  if (option.value === props.modelValue) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
}

function moveActive(step: 1 | -1): void {
  const from =
    activeIndex.value < 0 ? (step === 1 ? 0 : props.options.length - 1) : activeIndex.value + step
  const next = firstSelectable(Math.min(Math.max(from, 0), props.options.length - 1), step)
  if (next >= 0) {
    activeIndex.value = next
    void nextTick(scrollActiveIntoView)
  }
}

let typeahead = ''
let typeaheadTimer: ReturnType<typeof setTimeout> | undefined

function runTypeahead(character: string): void {
  typeahead += character.toLowerCase()
  if (typeaheadTimer) clearTimeout(typeaheadTimer)
  typeaheadTimer = setTimeout(() => {
    typeahead = ''
  }, 700)

  const start = activeIndex.value >= 0 ? activeIndex.value : selectedIndex.value
  const total = props.options.length
  for (let offset = 1; offset <= total; offset += 1) {
    const index = (Math.max(start, 0) + offset) % total
    const option = props.options[index]
    if (option && option.disabled !== true && option.label.toLowerCase().startsWith(typeahead)) {
      if (open.value) {
        activeIndex.value = index
        void nextTick(scrollActiveIntoView)
      } else {
        commit(index)
      }
      return
    }
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (props.disabled) return

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (open.value) moveActive(1)
      else void openList()
      return
    case 'ArrowUp':
      event.preventDefault()
      if (open.value) moveActive(-1)
      else void openList(firstSelectable(props.options.length - 1, -1))
      return
    case 'Home':
      if (!open.value) return
      event.preventDefault()
      activeIndex.value = firstSelectable(0, 1)
      void nextTick(scrollActiveIntoView)
      return
    case 'End':
      if (!open.value) return
      event.preventDefault()
      activeIndex.value = firstSelectable(props.options.length - 1, -1)
      void nextTick(scrollActiveIntoView)
      return
    case 'Enter':
    case ' ':
      event.preventDefault()
      if (open.value) commit(activeIndex.value)
      else void openList()
      return
    case 'Escape':
      if (!open.value) return
      event.preventDefault()
      closeList()
      return
    case 'Tab':
      closeList(false)
      return
    default:
      if (event.key.length === 1 && !event.altKey && !event.ctrlKey && !event.metaKey) {
        runTypeahead(event.key)
      }
  }
}

function onDocumentPointerDown(event: Event): void {
  const target = event.target as Node | null
  if (target && root.value?.contains(target)) return
  closeList(false)
}

watch(open, (isOpen) => {
  if (typeof document === 'undefined') return
  if (isOpen) {
    document.addEventListener('pointerdown', onDocumentPointerDown, true)
    window.addEventListener('resize', updateDropDirection)
    window.addEventListener('scroll', updateDropDirection, true)
  } else {
    document.removeEventListener('pointerdown', onDocumentPointerDown, true)
    window.removeEventListener('resize', updateDropDirection)
    window.removeEventListener('scroll', updateDropDirection, true)
  }
})

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) closeList(false)
  },
)

onBeforeUnmount(() => {
  if (typeaheadTimer !== undefined) clearTimeout(typeaheadTimer)
  if (typeof document === 'undefined') return
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  window.removeEventListener('resize', updateDropDirection)
  window.removeEventListener('scroll', updateDropDirection, true)
})
</script>

<template>
  <div
    ref="root"
    class="app-select"
    :class="{
      'app-select--open': open,
      'app-select--compact': compact,
      'app-select--up': dropUp,
    }"
  >
    <button
      :id="triggerId"
      ref="trigger"
      type="button"
      class="app-select__trigger"
      role="combobox"
      :disabled="disabled"
      :aria-expanded="open"
      :aria-controls="listboxId"
      aria-haspopup="listbox"
      :aria-activedescendant="activeDescendant"
      :aria-label="ariaLabel"
      :aria-labelledby="ariaLabelledby"
      :aria-describedby="ariaDescribedby"
      :aria-invalid="invalid ? 'true' : undefined"
      @click="open ? closeList() : openList()"
      @keydown="onKeydown"
    >
      <span
        class="app-select__value"
        :class="{ 'app-select__value--placeholder': !selectedOption }"
        >{{ displayLabel }}</span
      >
      <svg class="app-select__caret" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
        <path
          d="m4 6 4 4 4-4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <div v-if="open" :id="listboxId" ref="listbox" class="app-select__panel" role="listbox">
      <div
        v-for="(option, index) in options"
        :id="optionId(index)"
        :key="option.value"
        class="app-select__option"
        role="option"
        :aria-selected="option.value === modelValue"
        :aria-disabled="option.disabled ? 'true' : undefined"
        :data-active="index === activeIndex ? 'true' : 'false'"
        @click="commit(index)"
        @mousemove="isSelectable(index) ? (activeIndex = index) : undefined"
      >
        <span class="app-select__option-body">
          <span class="app-select__option-label">{{ option.label }}</span>
          <span v-if="option.description" class="app-select__option-description">{{
            option.description
          }}</span>
        </span>
        <svg
          v-if="option.value === modelValue"
          class="app-select__check"
          viewBox="0 0 16 16"
          aria-hidden="true"
          focusable="false"
        >
          <path
            d="m3.5 8.5 3 3 6-6.5"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-select {
  position: relative;
  min-width: 0;
}

.app-select__trigger {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 2.75rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: var(--color-fill);
  color: var(--color-ink);
  padding: 0.625rem 0.875rem;
  font-size: var(--font-size-md);
  font-weight: 620;
  line-height: 1.35;
  text-align: left;
  transition:
    border-color var(--motion-fast),
    background-color var(--motion-fast),
    box-shadow var(--motion-fast);
}

.app-select--compact .app-select__trigger {
  padding: 0.5rem 0.75rem;
  font-size: var(--font-size-sm);
}

.app-select__trigger:hover:not(:disabled) {
  background: var(--color-fill-strong);
}

.app-select__trigger:disabled {
  background: var(--color-neutral-soft);
  color: var(--color-muted);
  opacity: 0.75;
}

.app-select--open .app-select__trigger,
.app-select__trigger:focus-visible {
  border-color: var(--color-focus);
  outline: none;
  background: var(--color-surface);
  box-shadow: var(--focus-ring);
}

.app-select__trigger[aria-invalid='true'] {
  border-color: var(--color-danger);
}

.app-select__value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__value--placeholder {
  color: #98a2b3;
  font-weight: 550;
}

.app-select__caret {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  color: var(--color-muted-strong);
  transition: transform var(--motion-fast);
}

.app-select--open .app-select__caret {
  transform: rotate(180deg);
  color: var(--color-brand);
}

/*
 * 떠 있는 목록. `.menu-panel`과 같은 표면 언어를 쓰되 trigger 폭에 맞춘다.
 */
.app-select__panel {
  position: absolute;
  z-index: 60;
  top: calc(100% + 0.375rem);
  right: 0;
  left: 0;
  display: grid;
  max-height: 16.5rem;
  gap: 0.125rem;
  overflow-y: auto;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 0.375rem;
  animation: app-select-in var(--motion-fast) var(--ease-emphasized) both;
}

.app-select--up .app-select__panel {
  top: auto;
  bottom: calc(100% + 0.375rem);
}

.app-select__option {
  display: flex;
  min-height: 2.5rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.625rem;
  border-radius: var(--radius-md);
  color: var(--color-ink-soft);
  padding: 0.5rem 0.75rem;
  font-size: var(--font-size-sm);
  font-weight: 650;
  cursor: pointer;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
}

.app-select__option[data-active='true'] {
  background: var(--color-fill);
  color: var(--color-ink);
}

.app-select__option[aria-selected='true'] {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  font-weight: 750;
}

.app-select__option[aria-disabled='true'] {
  color: var(--color-muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.app-select__option-body {
  display: grid;
  min-width: 0;
  gap: 0.125rem;
}

.app-select__option-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__option-description {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  font-weight: 550;
  line-height: 1.45;
}

.app-select__check {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  color: var(--color-brand);
}

@keyframes app-select-in {
  from {
    opacity: 0;
    transform: translateY(-0.25rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.app-select--up .app-select__panel {
  animation-name: app-select-in-up;
}

@keyframes app-select-in-up {
  from {
    opacity: 0;
    transform: translateY(0.25rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
