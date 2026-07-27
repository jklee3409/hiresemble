<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const displayName = computed(() => authStore.currentUser?.displayName ?? '사용자')

const quickActions = [
  {
    to: '/profile/basic',
    icon: 'profile',
    title: '프로필 관리',
    description: '기본 정보와 학력·경력 등 지원에 사용할 정보를 정리합니다.',
    action: '프로필 열기',
  },
  {
    to: '/documents',
    icon: 'documents',
    title: '문서 업로드',
    description: '이력서나 포트폴리오를 등록하고 처리 상태와 추출 근거를 확인합니다.',
    action: '문서 관리',
  },
  {
    to: '/jobs/new',
    icon: 'jobs',
    title: '공고 등록',
    description: '채용 공고 URL을 등록하거나 필요한 경우 본문을 직접 입력합니다.',
    action: '공고 등록',
  },
  {
    to: '/agent-runs',
    icon: 'runs',
    title: '작업 기록 확인',
    description: '비동기 작업의 진행률, 필요한 사용자 조치와 완료 결과를 확인합니다.',
    action: '기록 보기',
  },
] as const
</script>

<template>
  <section class="dashboard app-page" aria-labelledby="dashboard-heading">
    <div class="dashboard-welcome">
      <PageHeader
        heading-id="dashboard-heading"
        :title="`${displayName}님의 지원 준비 공간`"
        description="현재 사용할 수 있는 작업부터 시작하세요. 등록한 정보와 처리 상태는 각 업무 화면에서 확인할 수 있습니다."
        eyebrow="Workspace"
      />
      <div class="dashboard-welcome__line" aria-hidden="true" />
    </div>

    <section class="dashboard-actions" aria-labelledby="quick-actions-heading">
      <header class="dashboard-actions__header">
        <div>
          <h3 id="quick-actions-heading" class="section-title">빠른 작업</h3>
          <p class="section-description">준비하려는 항목을 선택해 바로 이동합니다.</p>
        </div>
      </header>
      <div class="dashboard-action-list">
        <RouterLink
          v-for="action in quickActions"
          :key="action.to"
          class="dashboard-action"
          :to="action.to"
        >
          <span class="dashboard-action__icon">
            <AppIcon :name="action.icon" />
          </span>
          <span class="dashboard-action__body">
            <strong>{{ action.title }}</strong>
            <small>{{ action.description }}</small>
          </span>
          <span class="dashboard-action__cta">
            {{ action.action }}
            <AppIcon name="arrow-right" />
          </span>
        </RouterLink>
      </div>
    </section>

    <aside class="dashboard-note" aria-label="작업 공간 안내">
      <AppIcon name="check" />
      <div>
        <strong>사용자 데이터가 준비의 중심입니다.</strong>
        <p>
          등록한 프로필, 문서와 공고를 기준으로 작업이 진행됩니다. 아직 데이터가 없어도 필요한
          항목부터 차례로 시작할 수 있습니다.
        </p>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: 1.5rem;
}

.dashboard-welcome {
  position: relative;
  overflow: hidden;
  border: 1px solid #bfd2d7;
  border-radius: var(--radius-lg);
  background: #edf5f5;
  padding: clamp(1.5rem, 4vw, 2.5rem);
}

.dashboard-welcome__line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 0.25rem;
  background: var(--color-brand);
}

.dashboard-actions {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.dashboard-actions__header {
  border-bottom: 1px solid var(--color-border);
  padding: 1.125rem 1.25rem;
}

.dashboard-action-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-action {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.75rem 1rem;
  align-items: start;
  color: var(--color-ink);
  padding: 1.25rem;
  text-decoration: none;
}

.dashboard-action:nth-child(even) {
  border-left: 1px solid var(--color-border);
}

.dashboard-action:nth-child(n + 3) {
  border-top: 1px solid var(--color-border);
}

.dashboard-action:hover {
  background: var(--color-surface-subtle);
}

.dashboard-action__icon {
  display: inline-grid;
  width: 2.5rem;
  height: 2.5rem;
  grid-row: 1 / 3;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.dashboard-action__body {
  min-width: 0;
}

.dashboard-action__body strong,
.dashboard-action__body small {
  display: block;
}

.dashboard-action__body strong {
  font-size: 0.9375rem;
  font-weight: 710;
}

.dashboard-action__body small {
  margin-top: 0.25rem;
  color: var(--color-muted);
  font-size: 0.8125rem;
  line-height: 1.55;
}

.dashboard-action__cta {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--color-brand);
  font-size: 0.75rem;
  font-weight: 700;
}

.dashboard-action__cta .icon {
  width: 0.875rem;
  height: 0.875rem;
}

.dashboard-note {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  border-left: 3px solid var(--color-border-strong);
  color: var(--color-muted-strong);
  padding: 0.5rem 0 0.5rem 1rem;
}

.dashboard-note > .icon {
  margin-top: 0.15rem;
  color: var(--color-success);
}

.dashboard-note strong {
  color: var(--color-ink-soft);
  font-size: 0.875rem;
}

.dashboard-note p {
  max-width: 48rem;
  margin: 0.25rem 0 0;
  font-size: 0.8125rem;
  line-height: 1.6;
}

@media (max-width: 699px) {
  .dashboard-action-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-action,
  .dashboard-action:nth-child(even),
  .dashboard-action:nth-child(n + 3) {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .dashboard-action:first-child {
    border-top: 0;
  }
}
</style>
