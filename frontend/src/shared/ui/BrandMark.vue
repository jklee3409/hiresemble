<script setup lang="ts">
import hiresembleLogoUrl from './hiresemble-logo.png'

withDefaults(
  defineProps<{
    compact?: boolean
    inverse?: boolean
    showName?: boolean
  }>(),
  {
    compact: false,
    inverse: false,
    showName: true,
  },
)
</script>

<template>
  <span
    class="brand-lockup"
    :class="{
      'brand-lockup--compact': compact,
      'brand-lockup--inverse': inverse,
    }"
    data-testid="brand-mark"
    aria-hidden="true"
  >
    <img class="brand-symbol" :src="hiresembleLogoUrl" alt="" draggable="false" />
    <span v-if="showName" class="brand-lockup__name">Hiresemble</span>
  </span>
</template>

<style scoped>
.brand-lockup {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 0.7rem;
  color: var(--color-ink);
}

.brand-symbol {
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  object-fit: contain;
  user-select: none;
}

.brand-lockup__name {
  overflow: hidden;
  font-size: 1.125rem;
  font-weight: 820;
  letter-spacing: -0.045em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-lockup--compact {
  gap: 0.5rem;
}

.brand-lockup--compact .brand-symbol {
  width: 2rem;
  height: 2rem;
}

.brand-lockup--compact .brand-lockup__name {
  font-size: 1rem;
}

.brand-lockup--inverse {
  color: #ffffff;
}
</style>
