<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    kind?: 'loading' | 'empty' | 'error' | 'info'
    title: string
    description?: string
  }>(),
  {
    kind: 'info',
    description: '',
  },
)
</script>

<template>
  <section
    class="state-panel"
    :class="{
      'state-panel--error': kind === 'error',
      'state-panel--loading': kind === 'loading',
    }"
    :role="kind === 'error' ? 'alert' : kind === 'loading' ? 'status' : undefined"
    :aria-live="kind === 'loading' ? 'polite' : undefined"
  >
    <AppIcon v-if="kind === 'error'" name="alert" />
    <AppIcon v-else-if="kind === 'empty'" name="inbox" />
    <AppIcon v-else-if="kind !== 'loading'" name="clock" />
    <div v-if="kind === 'loading'" class="skeleton-stack" aria-hidden="true">
      <div class="skeleton-line" />
      <div class="skeleton-line" />
      <div class="skeleton-line" />
    </div>
    <h3 class="state-panel__title">{{ title }}</h3>
    <p v-if="description" class="state-panel__description">{{ description }}</p>
    <div v-if="$slots.actions" class="state-panel__actions">
      <slot name="actions" />
    </div>
  </section>
</template>
