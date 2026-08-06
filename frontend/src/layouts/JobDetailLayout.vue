<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import {
  JOB_STATUS_LABELS,
  formatJobInstant,
  jobCompanyLabel,
  jobDisplayTitle,
} from '@/features/jobs/presentation'
import { useJobDetailQuery } from '@/features/jobs/queries'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const activeTab = computed(() => {
  if (route.name === 'job-analysis') return 'analysis'
  if (route.name === 'job-cover-letter') return 'cover-letter'
  if (route.name === 'job-interview') return 'interview'
  return 'overview'
})
const analysisLabel = computed(() => {
  const value = job.data.value
  if (!value) return ''
  if (value.latestAnalysis) return value.analysisOutdated ? '분석 업데이트 필요' : '분석 완료'
  return {
    WAITING_FOR_CONTENT: '본문 확인 필요',
    NOT_REQUESTED: '분석 준비',
    PENDING: '자동 분석 준비 중',
    LAUNCHED: '자동 분석 중',
    BLOCKED: '분석 확인 필요',
    SUPERSEDED: '최신 내용 확인 중',
  }[value.automaticAnalysis.state]
})
</script>

<template>
  <section class="job-detail-shell">
    <RouterLink class="job-detail-back" :to="{ name: 'jobs' }">
      <AppIcon name="arrow-left" />
      공고 목록
    </RouterLink>
    <header v-if="job.data.value" class="job-resource-header">
      <div class="job-resource-header__main">
        <p>{{ jobCompanyLabel(job.data.value.companyName) }}</p>
        <h1 class="job-resource-title" :title="jobDisplayTitle(job.data.value)">
          {{ jobDisplayTitle(job.data.value) }}
        </h1>
        <div class="job-resource-header__meta">
          <span v-if="job.data.value.positionName">{{ job.data.value.positionName }}</span>
          <span v-if="job.data.value.location">{{ job.data.value.location }}</span>
          <span v-if="job.data.value.employmentType">{{ job.data.value.employmentType }}</span>
          <span v-if="job.data.value.deadlineAt"
            >마감 {{ formatJobInstant(job.data.value.deadlineAt) }}</span
          >
        </div>
      </div>
      <div class="job-resource-header__aside">
        <div class="job-resource-header__badges">
          <StatusBadge :label="JOB_STATUS_LABELS[job.data.value.status]" tone="brand" />
          <StatusBadge
            :label="analysisLabel"
            :tone="job.data.value.latestAnalysis ? 'success' : 'info'"
          />
        </div>
        <a
          :href="job.data.value.sourceUrl"
          class="job-resource-header__source"
          target="_blank"
          rel="noopener noreferrer"
        >
          원본 공고 보기
          <AppIcon name="arrow-right" />
        </a>
        <!-- 공고 정보 화면의 편집·삭제 동작이 Teleport로 들어오는 자리. 비어 있으면 자리를 차지하지 않는다. -->
        <div id="job-detail-actions" class="job-resource-header__actions" />
      </div>
    </header>
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
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'interview' }"
        :to="{ name: 'job-interview', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'interview' ? 'page' : undefined"
      >
        면접 준비
      </RouterLink>
    </nav>
    <div class="job-detail-body"><RouterView /></div>
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

.job-resource-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-7);
  padding: var(--space-5) 0 var(--space-6);
}

.job-resource-header__main {
  min-width: 0;
  flex: 1 1 auto;
}

.job-resource-header__main > p {
  color: var(--color-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.job-resource-header h1 {
  display: -webkit-box;
  width: 100%;
  max-width: 58rem;
  margin-top: var(--space-2);
  overflow: hidden;
  /* 분석 화면의 적합도 히어로 숫자와 경쟁하지 않도록 30px를 상한으로 둔다. */
  font-size: clamp(1.4rem, 2.8vw, 1.875rem);
  font-weight: 790;
  letter-spacing: -0.035em;
  line-height: 1.17;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.job-resource-header__meta,
.job-resource-header__badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.job-resource-header__meta {
  margin-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.job-resource-header__meta span + span::before {
  margin-right: var(--space-2);
  color: var(--color-border-strong);
  content: '·';
}

.job-resource-header__aside {
  display: grid;
  flex: 0 0 auto;
  justify-items: end;
  gap: var(--space-3);
}

.job-resource-header__source {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: none;
}

.job-resource-header__source:hover {
  color: var(--color-brand-strong);
}

.job-resource-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.job-resource-header__actions:empty {
  display: none;
}

.job-detail-back:hover {
  color: var(--color-brand);
}

.job-detail-tabs {
  position: sticky;
  z-index: 20;
  top: var(--global-header-height);
  display: flex;
  gap: 0.25rem;
  margin-inline: calc(var(--space-2) * -1);
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-canvas) 94%, transparent);
  padding-inline: var(--space-2);
  scrollbar-width: none;
  backdrop-filter: blur(14px);
}

.job-detail-tab {
  min-height: 3.25rem;
  flex: 0 0 auto;
  border-bottom: 2px solid transparent;
  color: var(--color-text-secondary);
  padding: 0.875rem 1rem;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}

.job-detail-tab:hover {
  color: var(--color-brand-strong);
}

/* 선택 상태는 채움면이 아니라 밑줄과 색으로만 표시해 탭이 버튼처럼 보이지 않게 한다. */
.job-detail-tab--active {
  border-bottom-color: var(--color-brand);
  color: var(--color-brand);
  font-weight: 780;
}

.job-detail-tab--active:hover,
.job-detail-tab--active:focus-visible {
  border-bottom-color: var(--color-brand);
  color: var(--color-brand);
}

.job-detail-body {
  min-width: 0;
  padding-top: var(--layout-tabs-body-gap);
}

@media (max-width: 48rem) {
  .job-resource-header {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-2);
    padding-block: var(--space-3) var(--space-4);
  }

  .job-resource-header__aside {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
  }

  .job-resource-header__main,
  .job-resource-header h1 {
    width: 100%;
    max-width: 100%;
  }

  .job-resource-header h1 {
    font-size: clamp(1.35rem, 6.2vw, 1.65rem);
    line-height: 1.28;
  }

  .job-resource-header__meta {
    margin-top: var(--space-2);
    font-size: 0.8125rem;
  }

  .job-resource-header__source {
    flex: 0 0 auto;
    font-size: 0.8125rem;
  }

  .job-detail-tabs {
    top: var(--global-header-height);
    margin-inline: calc(var(--space-4) * -1);
    padding-inline: var(--space-4);
  }

  .job-detail-tab {
    padding-inline: var(--space-3);
  }

  .job-detail-body {
    padding-top: var(--space-4);
  }
}

@media (max-width: 35rem) {
  .job-resource-header__meta {
    display: none;
  }

  .job-detail-tabs {
    margin-inline: calc(var(--space-3) * -1);
    padding-inline: var(--space-3);
  }

  .job-detail-tab:last-child {
    font-size: 0;
  }

  .job-detail-tab:last-child::after {
    content: '면접';
    font-size: 0.875rem;
  }
}
</style>
