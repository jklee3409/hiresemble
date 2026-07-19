<script setup lang="ts">
import { reactive } from 'vue'

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
  <section
    class="rounded-xl border border-amber-300 bg-amber-50 p-4"
    aria-labelledby="version-conflict-title"
    role="alert"
  >
    <h3 id="version-conflict-title" class="font-semibold text-amber-950">
      다른 곳에서 최신 내용이 저장되었습니다
    </h3>
    <p class="mt-1 text-sm text-amber-900">
      자동으로 덮어쓰지 않습니다. 유지할 내 입력을 선택해 최신값에 재적용한 뒤 다시 저장해 주세요.
    </p>

    <div class="mt-4 overflow-x-auto">
      <table class="w-full min-w-[36rem] text-left text-sm">
        <thead>
          <tr class="border-b border-amber-300">
            <th class="py-2">재적용</th>
            <th class="py-2">항목</th>
            <th class="py-2">내 미저장 값</th>
            <th class="py-2">최신 서버 값</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="field in fields" :key="field.key" class="border-b border-amber-200">
            <td class="py-2">
              <input
                v-model="selected[field.key]"
                type="checkbox"
                :aria-label="`${field.label} 내 값 재적용`"
              />
            </td>
            <th class="py-2 font-medium">{{ field.label }}</th>
            <td class="max-w-xs break-words py-2">{{ display(draft[field.key]) }}</td>
            <td class="max-w-xs break-words py-2">{{ display(latest[field.key]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="mt-4 flex flex-wrap gap-2">
      <button
        type="button"
        class="rounded-lg bg-amber-800 px-3 py-2 text-sm font-semibold text-white"
        @click="reapply"
      >
        선택 항목 재적용
      </button>
      <button
        type="button"
        class="rounded-lg border border-amber-700 px-3 py-2 text-sm font-semibold text-amber-950"
        @click="emit('cancel')"
      >
        취소하고 최신값 사용
      </button>
    </div>
  </section>
</template>
