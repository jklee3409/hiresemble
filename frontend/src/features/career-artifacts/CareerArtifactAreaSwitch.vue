<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'

/*
 * `이력서·자료` 영역의 자료 출처 전환.
 *
 * 외부 연동은 지금 GitHub 저장소만 지원하지만 tab 이름은 provider에 묶지 않는다.
 * 개발 직군이 아닌 사용자에게 `GitHub`만 보이면 이 영역 전체가 자신과 무관해 보이고,
 * 나중에 다른 출처가 늘어도 tab 구조를 다시 바꾸지 않아도 된다.
 */

const route = useRoute()

const areas = computed(() => [
  { to: '/documents', label: '자료 업로드', prefix: '/documents', enabled: true },
  {
    to: '/integrations',
    label: '외부 연동',
    prefix: '/integrations',
    enabled: featureFlags.githubSourceEnabled,
  },
  {
    to: '/career-artifacts',
    label: 'AI로 만든 초안',
    prefix: '/career-artifacts',
    enabled: featureFlags.careerArtifactEnabled,
  },
])

const visibleAreas = computed(() => areas.value.filter((area) => area.enabled))
</script>

<template>
  <nav v-if="visibleAreas.length > 1" class="artifact-area-switch" aria-label="자료 종류">
    <RouterLink
      v-for="area in visibleAreas"
      :key="area.to"
      :to="area.to"
      class="artifact-area-switch__item"
      :aria-current="route.path.startsWith(area.prefix) ? 'page' : undefined"
    >
      {{ area.label }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.artifact-area-switch {
  display: inline-flex;
  max-width: 100%;
  gap: 0.25rem;
  padding: 0.3125rem;
  overflow-x: auto;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  scrollbar-width: none;
}

.artifact-area-switch::-webkit-scrollbar {
  display: none;
}

.artifact-area-switch__item {
  display: inline-flex;
  min-height: 2.375rem;
  flex: 0 0 auto;
  align-items: center;
  padding: 0.4375rem 0.9375rem;
  border-radius: var(--radius-pill);
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast),
    box-shadow var(--motion-fast);
}

.artifact-area-switch__item:hover {
  color: var(--color-ink);
}

.artifact-area-switch__item[aria-current='page'] {
  background: var(--color-surface);
  color: var(--color-brand-ink);
  box-shadow: var(--shadow-xs);
  font-weight: 750;
}
</style>
