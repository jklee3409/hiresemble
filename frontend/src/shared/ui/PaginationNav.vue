<script setup lang="ts">
defineProps<{
  page: number
  totalPages: number
  label: string
}>()

defineEmits<{
  change: [page: number]
}>()
</script>

<template>
  <nav class="pagination" :aria-label="label">
    <button
      type="button"
      class="button button--secondary button--compact"
      :disabled="page === 0"
      @click="$emit('change', page - 1)"
    >
      이전
    </button>
    <span class="pagination__summary">{{ page + 1 }} / {{ totalPages }} 페이지</span>
    <button
      type="button"
      class="button button--secondary button--compact"
      :disabled="page + 1 >= totalPages"
      @click="$emit('change', page + 1)"
    >
      다음
    </button>
  </nav>
</template>
