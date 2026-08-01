<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    eyebrow?: string
    headingId?: string
    level?: 1 | 2
    variant?: 'default' | 'list' | 'detail' | 'editor' | 'compact'
  }>(),
  {
    description: '',
    eyebrow: '',
    headingId: undefined,
    level: 1,
    variant: 'default',
  },
)
</script>

<template>
  <header class="page-header" :class="`page-header--${variant}`">
    <div class="page-header__body">
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <component :is="level === 1 ? 'h1' : 'h2'" :id="headingId" class="page-title">
        {{ title }}
      </component>
      <p v-if="description" class="page-description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
