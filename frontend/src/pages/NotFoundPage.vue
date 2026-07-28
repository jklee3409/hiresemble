<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const recoveryRoute = computed(() => (authStore.isAuthenticated ? '/dashboard' : '/login'))
const recoveryLabel = computed(() =>
  authStore.isAuthenticated ? '오늘의 준비로 돌아가기' : '로그인으로 이동',
)
</script>

<template>
  <main class="not-found">
    <RouterLink class="not-found__brand" :to="recoveryRoute" aria-label="Hiresemble 시작 화면">
      <BrandMark />
    </RouterLink>
    <section class="not-found__content" aria-labelledby="not-found-heading">
      <p class="section-kicker">404</p>
      <h1 id="not-found-heading">페이지를 찾을 수 없어요.</h1>
      <p>주소가 바뀌었거나 볼 수 없는 페이지예요. 지금 이용할 수 있는 화면으로 돌아가세요.</p>
      <RouterLink class="button button--primary" :to="recoveryRoute">
        {{ recoveryLabel }}
        <AppIcon name="arrow-right" />
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: 100dvh;
  grid-template-rows: auto 1fr;
  padding: clamp(var(--space-5), 5vw, var(--space-10));
  border-left: 0.3rem solid var(--color-brand-strong);
  background: var(--color-canvas);
}

.not-found__brand {
  display: inline-flex;
  width: max-content;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text);
  font-weight: 800;
  letter-spacing: -0.02em;
}

.not-found__content {
  width: min(100%, 36rem);
  align-self: center;
  margin: 0 auto;
  padding: var(--space-8) 0;
}

.not-found__content h1 {
  margin-top: var(--space-2);
  color: var(--color-text);
  font-size: clamp(2rem, 6vw, 3.5rem);
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.12;
}

.not-found__content > p:not(.section-kicker) {
  max-width: 32rem;
  margin: var(--space-4) 0 var(--space-6);
  color: var(--color-text-secondary);
  line-height: 1.75;
}
</style>
