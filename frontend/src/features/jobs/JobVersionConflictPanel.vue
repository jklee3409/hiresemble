<script setup lang="ts">
import { reactive } from 'vue'

const props = defineProps<{
  draft: object
  latest: object
  fields: ReadonlyArray<{ key: string; label: string }>
}>()

const emit = defineEmits<{
  cancel: []
  reapply: [fields: string[]]
}>()

const selected = reactive<Record<string, boolean>>(
  Object.fromEntries(props.fields.map((field) => [field.key, true])),
)

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '비어 있음'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function valueAt(source: object, key: string): unknown {
  return (source as Record<string, unknown>)[key]
}

function reapply(): void {
  emit(
    'reapply',
    props.fields.filter((field) => selected[field.key]).map((field) => field.key),
  )
}
</script>

<template>
  <section
    class="rounded-xl border border-amber-300 bg-amber-50 p-4"
    aria-labelledby="job-version-conflict-title"
    role="alert"
  >
    <h3 id="job-version-conflict-title" class="font-semibold text-amber-950">
      다른 곳에서 공고가 변경되었습니다
    </h3>
    <p class="mt-1 text-sm text-amber-900">
      자동으로 덮어쓰지 않습니다. 내 값을 최신 서버 버전에 재적용할 항목을 선택하세요.
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
            <td class="max-w-xs break-words py-2">{{ display(valueAt(draft, field.key)) }}</td>
            <td class="max-w-xs break-words py-2">{{ display(valueAt(latest, field.key)) }}</td>
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
