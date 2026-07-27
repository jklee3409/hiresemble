<script setup lang="ts">
type StatusTone = 'neutral' | 'brand' | 'info' | 'success' | 'warning' | 'danger'

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
