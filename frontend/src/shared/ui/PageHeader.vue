<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    eyebrow?: string
    headingId?: string
  }>(),
  {
    description: '',
    eyebrow: '',
    headingId: undefined,
  },
)
</script>

<template>
  <header class="page-header">
    <div class="page-header__body">
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <h2 :id="headingId" class="page-title">{{ title }}</h2>
      <p v-if="description" class="page-description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
