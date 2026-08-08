<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'

const route = useRoute()
const currentArea = computed(() =>
  route.path.startsWith('/career-artifacts') ? 'generated' : 'uploaded',
)
</script>

<template>
  <nav
    v-if="featureFlags.careerArtifactEnabled"
    class="artifact-area-switch"
    aria-label="자료 종류"
  >
    <RouterLink
      to="/documents"
      :aria-current="currentArea === 'uploaded' ? 'page' : undefined"
      :class="{ 'artifact-area-switch__item--current': currentArea === 'uploaded' }"
    >
      업로드한 자료
    </RouterLink>
    <RouterLink
      to="/career-artifacts"
      :aria-current="currentArea === 'generated' ? 'page' : undefined"
      :class="{ 'artifact-area-switch__item--current': currentArea === 'generated' }"
    >
      AI로 만든 초안
    </RouterLink>
  </nav>
</template>

<style scoped>
.artifact-area-switch {
  display: inline-flex;
  max-width: 100%;
  gap: 0.25rem;
  padding: 0.25rem;
  overflow-x: auto;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
}

.artifact-area-switch a {
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  padding: 0.55rem 0.9rem;
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  font-weight: 750;
  white-space: nowrap;
}

.artifact-area-switch .artifact-area-switch__item--current {
  color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}
</style>
