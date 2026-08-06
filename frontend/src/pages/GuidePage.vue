<script setup lang="ts">
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { PRODUCT_JOURNEY_STEPS } from '@/shared/ui/productJourney'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const guideActions = [
  {
    route: { name: 'profile-basic' },
    action: '내 정보 채우기',
  },
  {
    route: { name: 'documents' },
    action: '자료 등록하기',
  },
  {
    route: { name: 'job-new' },
    action: '공고 추가하기',
  },
  {
    route: { name: 'cover-letters' },
    action: '자기소개서 보기',
  },
  {
    route: { name: 'interviews' },
    action: '면접 준비 보기',
  },
] as const

const steps = PRODUCT_JOURNEY_STEPS.map((step, index) => ({
  ...step,
  ...guideActions[index],
}))
</script>

<template>
  <section class="guide-page app-page" aria-labelledby="guide-heading">
    <PageHeader
      heading-id="guide-heading"
      title="Hiresemble 이용 가이드"
      description="처음부터 전부 채울 필요는 없어요. 지금 필요한 단계부터 시작하고 언제든 다시 돌아오세요."
      variant="list"
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="{ name: 'job-new' }">
          공고부터 추가하기
        </RouterLink>
      </template>
    </PageHeader>

    <nav class="guide-flow" aria-label="Hiresemble 이용 순서">
      <a v-for="step in steps" :key="step.number" :href="`#guide-step-${step.number}`">
        <span>{{ step.number }}</span
        >{{ step.title }}
      </a>
    </nav>

    <ol class="guide-steps">
      <li v-for="step in steps" :id="`guide-step-${step.number}`" :key="step.number">
        <article class="guide-step">
          <div class="guide-step__copy">
            <span class="guide-step__number">{{ step.number }}</span>
            <AppIcon :name="step.icon" />
            <div>
              <h2>{{ step.title }}</h2>
              <p>{{ step.description }}</p>
              <RouterLink class="guide-step__link" :to="step.route">
                {{ step.action }}
                <AppIcon name="arrow-right" />
              </RouterLink>
            </div>
          </div>

          <div class="guide-preview" :aria-label="`${step.title} 화면 예시`">
            <template v-if="step.number === 1">
              <div class="guide-preview__header">
                <strong>내 지원 정보</strong><span>60% 완료</span>
              </div>
              <div class="guide-preview__fields"><i /><i /><i /></div>
              <StatusBadge label="확인한 경험 4개" tone="success" />
            </template>
            <template v-else-if="step.number === 2">
              <div class="guide-preview__header">
                <strong>이력서·자료</strong><span>최근 등록순</span>
              </div>
              <div class="guide-preview__row">
                <AppIcon name="documents" /><span>백엔드_이력서.pdf</span
                ><StatusBadge label="확인 완료" tone="success" />
              </div>
              <div class="guide-preview__row">
                <AppIcon name="documents" /><span>포트폴리오.pdf</span
                ><StatusBadge label="내용 읽는 중" tone="info" />
              </div>
            </template>
            <template v-else-if="step.number === 3">
              <div class="guide-preview__header">
                <strong>백엔드 개발자</strong><span>Hiresemble Demo</span>
              </div>
              <div class="guide-preview__journey">
                <span class="is-done">✓ 본문 확인</span><span class="is-active">2 조건 정리</span
                ><span>3 경험 비교</span>
              </div>
              <StatusBadge label="자동 분석 중" tone="info" />
            </template>
            <template v-else-if="step.number === 4">
              <div class="guide-preview__header"><strong>지원 동기</strong><span>저장됨</span></div>
              <div class="guide-preview__editor"><i /><i /><i /><i /></div>
              <div class="guide-preview__actions">
                <span>확인한 경험 2개</span><b>초안 만들기</b>
              </div>
            </template>
            <template v-else>
              <div class="guide-preview__header"><strong>예상 질문</strong><span>8개</span></div>
              <div class="guide-preview__question">
                1. 최근 프로젝트에서 가장 어려웠던 문제는 무엇인가요?
              </div>
              <div class="guide-preview__question">
                2. 이 공고의 핵심 업무에 어떻게 기여할 수 있나요?
              </div>
            </template>
          </div>
        </article>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.guide-flow {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--layout-heading-content-gap);
  overflow-x: auto;
  padding-bottom: var(--space-2);
}

.guide-flow a {
  display: inline-flex;
  min-height: 2.75rem;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  border: 0;
  border-radius: 999px;
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: none;
}

.guide-flow a span,
.guide-step__number {
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand);
  color: white;
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.guide-steps {
  display: grid;
  gap: var(--layout-section-gap);
  margin-top: var(--space-8);
}

.guide-step {
  display: grid;
  grid-template-columns: minmax(15rem, 0.8fr) minmax(0, 1.2fr);
  align-items: center;
  gap: clamp(var(--space-6), 7vw, var(--space-10));
  scroll-margin-top: calc(var(--global-header-height) + var(--space-6));
}

.guide-step__copy {
  display: grid;
  grid-template-columns: auto auto 1fr;
  align-items: start;
  gap: var(--space-3);
}

.guide-step__copy > .icon {
  width: 1.5rem;
  height: 1.5rem;
  margin-top: 0.15rem;
  color: var(--color-brand-strong);
}

.guide-step__copy h2 {
  font-size: 1.35rem;
  font-weight: 780;
}

.guide-step__copy p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.guide-step__link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-4);
  color: var(--color-brand-strong);
  font-weight: 720;
  text-decoration: none;
}

.guide-preview {
  min-height: 14rem;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: clamp(var(--space-5), 4vw, var(--space-7));
}

.guide-preview__header,
.guide-preview__row,
.guide-preview__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.guide-preview__header {
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.guide-preview__header span,
.guide-preview__actions span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.guide-preview__fields,
.guide-preview__editor {
  display: grid;
  gap: var(--space-3);
  margin-block: var(--space-5);
}

.guide-preview__fields i,
.guide-preview__editor i {
  height: 0.75rem;
  border-radius: 999px;
  background: var(--color-canvas-strong);
}

.guide-preview__fields i:nth-child(2),
.guide-preview__editor i:nth-child(2) {
  width: 76%;
}

.guide-preview__fields i:nth-child(3),
.guide-preview__editor i:nth-child(3) {
  width: 88%;
}

.guide-preview__row {
  margin-top: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.guide-preview__row > span {
  flex: 1;
  font-size: var(--font-size-sm);
}

.guide-preview__journey {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
  margin-block: var(--space-6);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.guide-preview__journey span {
  border-top: 3px solid var(--color-border-strong);
  padding-top: var(--space-2);
}

.guide-preview__journey .is-done,
.guide-preview__journey .is-active {
  border-color: var(--color-brand);
  color: var(--color-brand-strong);
  font-weight: 700;
}

.guide-preview__editor {
  padding: var(--space-4);
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.guide-preview__actions b {
  border-radius: var(--radius-control);
  background: var(--color-brand);
  color: white;
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
}

.guide-preview__question {
  margin-top: var(--space-3);
  border-left: 3px solid var(--color-brand-border);
  background: var(--color-surface-subtle);
  padding: var(--space-3) var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 48rem) {
  .guide-step {
    grid-template-columns: 1fr;
    gap: var(--space-5);
  }
}
</style>
