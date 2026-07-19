<script setup lang="ts">
import { ref } from 'vue'

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
  <fieldset>
    <legend class="text-sm font-medium">{{ label }}</legend>
    <div class="mt-2 flex gap-2">
      <input
        :id="id"
        v-model="input"
        class="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2"
        type="text"
        maxlength="100"
        :aria-describedby="error || localError ? `${id}-error` : `${id}-help`"
        :aria-invalid="Boolean(error || localError)"
        @keydown.enter.prevent="add"
      />
      <button
        type="button"
        class="rounded-lg border border-indigo-700 px-3 py-2 text-sm font-semibold text-indigo-700 disabled:opacity-50"
        :disabled="modelValue.length >= 10"
        @click="add"
      >
        추가
      </button>
    </div>
    <p :id="`${id}-help`" class="mt-1 text-xs text-slate-500">
      최대 10개 · 현재 {{ modelValue.length }}개
    </p>
    <p v-if="error || localError" :id="`${id}-error`" class="mt-1 text-sm text-red-700">
      {{ error || localError }}
    </p>
    <ul
      v-if="modelValue.length > 0"
      class="mt-2 flex flex-wrap gap-2"
      :aria-label="`${label} 목록`"
    >
      <li
        v-for="(item, index) in modelValue"
        :key="`${item}-${index}`"
        class="flex items-center gap-1 rounded-full bg-indigo-50 px-3 py-1 text-sm text-indigo-900"
      >
        <span>{{ item }}</span>
        <button
          type="button"
          class="rounded px-1 font-bold"
          :aria-label="`${item} 삭제`"
          @click="remove(index)"
        >
          ×
        </button>
      </li>
    </ul>
  </fieldset>
</template>
