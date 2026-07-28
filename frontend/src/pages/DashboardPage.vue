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
    step: '01',
    title: '내 경험 정리하기',
    description: '기본 정보와 학력, 경력을 한 번 정리해 여러 지원에 활용해요.',
    action: '내 프로필 열기',
  },
  {
    to: '/documents',
    icon: 'documents',
    step: '02',
    title: '이력서·자료 모으기',
    description: '이력서나 포트폴리오를 등록하고 경력 정보가 정리되는 과정을 확인해요.',
    action: '자료 등록하기',
  },
  {
    to: '/jobs/new',
    icon: 'jobs',
    step: '03',
    title: '관심 공고 담기',
    description: '지원할 공고 링크를 붙여 넣고 필요한 내용을 한곳에서 살펴봐요.',
    action: '공고 등록하기',
  },
  {
    to: '/agent-runs',
    icon: 'runs',
    step: '04',
    title: '진행 중인 분석 확인하기',
    description: '자료와 공고를 정리하는 작업이 어디까지 진행됐는지 확인해요.',
    action: '분석 기록 보기',
  },
] as const
</script>

<template>
  <section class="dashboard app-page" aria-labelledby="dashboard-heading">
    <div class="dashboard-welcome">
      <PageHeader
        heading-id="dashboard-heading"
        title="오늘의 지원 준비를 이어가세요."
        :description="`${displayName}님, 지금 필요한 단계부터 가볍게 시작해 보세요.`"
        eyebrow="나의 지원 준비"
      />
      <div class="dashboard-signal" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>

    <section class="dashboard-actions" aria-labelledby="quick-actions-heading">
      <header class="dashboard-actions__header">
        <div>
          <p class="section-kicker">4가지 준비</p>
          <h3 id="quick-actions-heading" class="section-title">다음 준비를 골라 보세요.</h3>
          <p class="section-description">순서대로 진행하지 않아도 괜찮아요.</p>
        </div>
      </header>
      <ol class="dashboard-action-list">
        <RouterLink
          v-for="action in quickActions"
          :key="action.to"
          v-slot="{ href, navigate }"
          custom
          :to="action.to"
        >
          <li class="dashboard-action-item">
            <a class="dashboard-action" :href="href" @click="navigate">
              <span class="dashboard-action__step">{{ action.step }}</span>
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
            </a>
          </li>
        </RouterLink>
      </ol>
    </section>

    <aside class="dashboard-note" aria-label="작업 공간 안내">
      <AppIcon name="check" />
      <div>
        <strong>등록한 경험이 모든 준비의 출발점이에요.</strong>
        <p>아직 자료가 없어도 괜찮아요. 지금 가지고 있는 정보부터 하나씩 채워 나갈 수 있어요.</p>
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
  min-height: 15rem;
  border: 1px solid #b9c8f2;
  border-radius: var(--radius-lg);
  background: #eef2ff;
  padding: clamp(1.5rem, 4vw, 2.5rem);
}

.dashboard-welcome :deep(.page-title) {
  max-width: 38rem;
  font-size: clamp(2rem, 4vw, 3.75rem);
  font-weight: 820;
  line-height: 1.1;
  letter-spacing: -0.055em;
}

.dashboard-signal {
  position: absolute;
  right: clamp(1.5rem, 5vw, 4rem);
  bottom: 2rem;
  display: flex;
  align-items: flex-end;
  gap: 0.45rem;
}

.dashboard-signal span {
  width: 0.55rem;
  border-radius: 999px;
  background: var(--color-brand);
}

.dashboard-signal span:nth-child(1) {
  height: 2rem;
}

.dashboard-signal span:nth-child(2) {
  height: 3.5rem;
  background: var(--color-cyan);
}

.dashboard-signal span:nth-child(3) {
  height: 5rem;
  background: var(--color-accent);
}

.dashboard-actions {
  display: grid;
  grid-template-columns: minmax(11rem, 0.32fr) minmax(0, 1fr);
  gap: clamp(1.5rem, 4vw, 3.5rem);
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
  padding-block: clamp(1.5rem, 3vw, 2.5rem);
}

.dashboard-actions__header {
  padding: 0.5rem 0;
}

.dashboard-action-list {
  position: relative;
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dashboard-action-item {
  position: relative;
}

.dashboard-action-item:not(:last-child)::before {
  position: absolute;
  top: 3.25rem;
  bottom: -1rem;
  left: 1rem;
  width: 1px;
  background: var(--color-brand-border);
  content: '';
}

.dashboard-action {
  display: grid;
  min-width: 0;
  grid-template-columns: 2rem 2.75rem minmax(0, 1fr) auto;
  gap: 1rem;
  align-items: center;
  color: var(--color-ink);
  padding: 1rem 0;
  text-decoration: none;
  transition:
    color var(--motion-fast),
    transform var(--motion-fast);
}

.dashboard-action:hover {
  color: var(--color-brand);
  transform: translateX(0.25rem);
}

.dashboard-action__step {
  position: relative;
  z-index: 1;
  display: inline-grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border: 1px solid var(--color-brand-border);
  border-radius: 50%;
  background: var(--color-canvas);
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-weight: 780;
  font-variant-numeric: tabular-nums;
}

.dashboard-action__icon {
  display: inline-grid;
  width: 2.75rem;
  height: 2.75rem;
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
  font-size: 1rem;
  font-weight: 740;
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
  white-space: nowrap;
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
  .dashboard-welcome {
    min-height: 13rem;
  }

  .dashboard-signal {
    right: 1.25rem;
    bottom: 1.25rem;
    opacity: 0.55;
  }

  .dashboard-actions {
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }

  .dashboard-action {
    grid-template-columns: 2rem 2.5rem minmax(0, 1fr);
    gap: 0.75rem;
  }

  .dashboard-action__cta {
    grid-column: 3;
  }

  .dashboard-action-item:not(:last-child)::before {
    bottom: -1.25rem;
  }
}
</style>
