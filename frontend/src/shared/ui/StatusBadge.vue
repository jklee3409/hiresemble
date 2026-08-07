<script setup lang="ts">
/* notice는 고쳐야 하는 오류는 아니지만 한 번 봐 두면 좋은 상태에 쓴다. */
type StatusTone = 'neutral' | 'brand' | 'info' | 'success' | 'notice' | 'warning' | 'danger'

withDefaults(
  defineProps<{
    label: string
    prefix?: string
    tone?: StatusTone
  }>(),
  {
    prefix: '',
    tone: 'neutral',
  },
)
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span v-if="prefix">{{ prefix }} · </span>{{ label }}
  </span>
</template>
