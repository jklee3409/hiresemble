<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'

const route = useRoute()
const activeTab = computed(() => {
  if (route.name === 'job-analysis') return 'analysis'
  if (route.name === 'job-cover-letter') return 'cover-letter'
  return 'overview'
})
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
        :class="{ 'job-detail-tab--active': activeTab === 'overview' }"
        :to="{ name: 'job-overview', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'overview' ? 'page' : undefined"
      >
        공고 정보
      </RouterLink>
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'analysis' }"
        :to="{ name: 'job-analysis', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'analysis' ? 'page' : undefined"
      >
        공고 분석
      </RouterLink>
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'cover-letter' }"
        :to="{ name: 'job-cover-letter', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'cover-letter' ? 'page' : undefined"
      >
        자기소개서
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
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
}

.job-detail-tab {
  min-height: 2.75rem;
  flex: 0 0 auto;
  border-bottom: 2px solid transparent;
  color: var(--color-text-secondary);
  padding: 0.625rem 0.875rem;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}

.job-detail-tab:hover {
  color: var(--color-brand);
}

.job-detail-tab--active {
  border-bottom-color: var(--color-brand);
  color: var(--color-brand);
}
</style>
