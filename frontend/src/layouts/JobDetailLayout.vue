<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'

const route = useRoute()
</script>

<template>
  <section class="job-detail-shell">
    <RouterLink class="job-detail-back" :to="{ name: 'jobs' }">
      <AppIcon name="arrow-left" />
      공고 목록
    </RouterLink>
    <nav class="job-detail-tabs" aria-label="공고 상세 탭">
      <RouterLink
        class="job-detail-tab"
        :to="{ name: 'job-overview', params: { jobId: route.params.jobId } }"
        aria-current="page"
      >
        개요
      </RouterLink>
    </nav>
    <RouterView />
  </section>
</template>

<style scoped>
.job-detail-shell {
  min-width: 0;
}

.job-detail-back {
  display: inline-flex;
  min-height: 2.25rem;
  align-items: center;
  gap: 0.375rem;
  color: var(--color-muted-strong);
  font-size: 0.8125rem;
  font-weight: 680;
  text-decoration: none;
}

.job-detail-back:hover {
  color: var(--color-brand);
}

.job-detail-tabs {
  display: flex;
  gap: 0.25rem;
  margin-top: 1rem;
  border-bottom: 1px solid var(--color-border);
}

.job-detail-tab {
  min-height: 2.75rem;
  border-bottom: 2px solid var(--color-brand);
  color: var(--color-brand);
  padding: 0.625rem 0.875rem;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}
</style>
