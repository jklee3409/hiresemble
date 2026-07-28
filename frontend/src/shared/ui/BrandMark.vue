<script setup lang="ts">
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
    <svg class="brand-symbol" viewBox="0 0 44 44" fill="none">
      <circle class="brand-symbol__orbit" cx="22" cy="22" r="18.25" />
      <path class="brand-symbol__line" d="M13 11v22M31 11v22M13 22h18" />
      <path class="brand-symbol__signal" d="m13 22 9-7 9 7" />
      <circle class="brand-symbol__node brand-symbol__node--accent" cx="13" cy="11" r="3" />
      <circle class="brand-symbol__node" cx="13" cy="33" r="3" />
      <circle class="brand-symbol__node" cx="31" cy="11" r="3" />
      <circle class="brand-symbol__node brand-symbol__node--accent" cx="31" cy="33" r="3" />
      <circle class="brand-symbol__core" cx="22" cy="15" r="2.5" />
    </svg>
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
  overflow: visible;
}

.brand-symbol__orbit {
  stroke: var(--color-brand-border);
  stroke-dasharray: 3 4;
  stroke-width: 1.25;
}

.brand-symbol__line {
  stroke: var(--color-brand);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.brand-symbol__signal {
  stroke: var(--hs-blue-400);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
}

.brand-symbol__node {
  fill: var(--color-brand);
}

.brand-symbol__node--accent {
  fill: var(--hs-blue-500);
  stroke: var(--color-ink);
  stroke-width: 1;
}

.brand-symbol__core {
  fill: var(--hs-blue-400);
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

.brand-lockup--inverse .brand-symbol__orbit {
  stroke: rgb(255 255 255 / 42%);
}

.brand-lockup--inverse .brand-symbol__line {
  stroke: #ffffff;
}

.brand-lockup--inverse .brand-symbol__signal,
.brand-lockup--inverse .brand-symbol__core {
  stroke: var(--hs-blue-300);
  fill: var(--hs-blue-300);
}

.brand-lockup--inverse .brand-symbol__node {
  fill: #ffffff;
}

.brand-lockup--inverse .brand-symbol__node--accent {
  fill: var(--hs-blue-400);
  stroke: var(--color-ink);
}
</style>
