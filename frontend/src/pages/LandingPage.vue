<script setup lang="ts">
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import { PRODUCT_JOURNEY_STEPS } from '@/shared/ui/productJourney'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const problems = [
  {
    icon: 'documents' as const,
    title: '경험을 매번 다시 찾는 준비',
    description: '이력서에 적은 경험을 공고와 자기소개서마다 처음부터 다시 꺼내 정리하게 돼요.',
  },
  {
    icon: 'jobs' as const,
    title: '서로 흩어진 지원 과정',
    description: '공고 분석, 자기소개서와 면접 준비가 따로 남아 다음 행동을 놓치기 쉬워요.',
  },
  {
    icon: 'runs' as const,
    title: '근거를 알기 어려운 AI 결과',
    description: '어떤 경험을 바탕으로 만든 결과인지 확인하기 어려우면 그대로 믿기 부담스러워요.',
  },
]

const values = [
  {
    icon: 'profile' as const,
    title: '확인한 경험을 중심으로 준비',
    description:
      '문서에서 정리된 경험을 직접 확인하고 관리해요. 확인한 내용은 이후 공고 분석과 자기소개서, 면접 준비의 근거가 돼요.',
  },
  {
    icon: 'jobs' as const,
    title: '공고 등록부터 분석까지 자연스럽게 연결',
    description:
      '공고 URL이나 본문을 등록하면 내용을 확인한 뒤 공고 요구사항과 등록한 정보를 비교하는 분석이 이어져요.',
  },
  {
    icon: 'cover-letter' as const,
    title: '자기소개서와 면접 준비까지 이어서 관리',
    description:
      '공고 분석과 확인한 경험을 바탕으로 자기소개서를 준비하고, 예상 질문과 답변 피드백까지 이어갈 수 있어요.',
  },
]

const aiPrinciples = [
  'AI는 사용자가 요청한 취업 준비 과정에서 활용해요.',
  '문서에서 정리된 경험은 사용자가 확인하고 관리해요.',
  '확인하지 않은 경험을 확정된 경력처럼 후속 준비에 사용하지 않아요.',
  'AI 결과는 사용자가 검토하고 직접 수정할 수 있어요.',
]
</script>

<template>
  <div class="landing-page">
    <a class="sr-only-focusable landing-skip-link" href="#landing-content">본문으로 건너뛰기</a>

    <header class="landing-header">
      <div class="landing-shell landing-header__inner">
        <RouterLink class="landing-brand" to="/" aria-label="Hiresemble 서비스 소개">
          <BrandMark compact />
        </RouterLink>
        <nav class="landing-navigation" aria-label="서비스 소개 탐색">
          <a href="#service-intro">서비스 소개</a>
          <a href="#journey">이용 흐름</a>
          <a href="#ai-principles">AI 활용 원칙</a>
        </nav>
        <div class="landing-header__actions">
          <RouterLink class="landing-login" to="/login">로그인</RouterLink>
          <RouterLink class="button button--primary" to="/signup">시작하기</RouterLink>
        </div>
      </div>
    </header>

    <main id="landing-content">
      <section class="landing-hero" aria-labelledby="landing-heading">
        <div class="landing-shell landing-hero__grid">
          <div class="landing-hero__copy">
            <p class="landing-eyebrow">내 경험을, 다음 기회로</p>
            <h1 id="landing-heading">흩어진 취업 준비를,<br />하나의 흐름으로.</h1>
            <p class="landing-hero__description">
              이력서와 포트폴리오에서 경험을 정리하고, 관심 공고 분석부터 자기소개서와 면접 준비까지
              이어가세요.
            </p>
            <div class="landing-hero__actions">
              <RouterLink class="button button--primary landing-button" to="/signup">
                시작하기
                <AppIcon name="arrow-right" />
              </RouterLink>
              <RouterLink class="button button--secondary landing-button" to="/login">
                로그인
              </RouterLink>
            </div>
            <p class="landing-hero__note">
              AI가 지원을 대신하지 않아요. 확인한 경험을 바탕으로 다음 준비를 이어갈 수 있게 도와요.
            </p>
          </div>

          <div class="product-preview" aria-label="Hiresemble에서 지원 준비가 이어지는 화면 예시">
            <div class="product-preview__top">
              <span><BrandMark compact :show-name="false" />지원 준비 흐름</span>
              <StatusBadge label="자동 연결" tone="brand" />
            </div>
            <ol class="product-preview__flow">
              <li class="is-complete">
                <span class="product-preview__marker"><AppIcon name="check" /></span>
                <span><small>경험 준비</small><strong>이력서 등록 완료</strong></span>
                <StatusBadge label="확인 완료" tone="success" />
              </li>
              <li class="is-active">
                <span class="product-preview__marker"><AppIcon name="jobs" /></span>
                <span><small>관심 공고</small><strong>공고 내용을 읽는 중</strong></span>
                <StatusBadge label="진행 중" tone="info" />
              </li>
              <li>
                <span class="product-preview__marker"><AppIcon name="profile" /></span>
                <span><small>공고 분석</small><strong>내 경험과 비교</strong></span>
                <span class="product-preview__waiting">다음 단계</span>
              </li>
              <li>
                <span class="product-preview__marker"><AppIcon name="cover-letter" /></span>
                <span><small>이어지는 준비</small><strong>자기소개서 준비 가능</strong></span>
                <span class="product-preview__waiting">준비 전</span>
              </li>
            </ol>
            <div class="product-preview__footer">
              <span>등록한 정보와 공고 요구사항을 비교해요.</span>
              <strong>결과는 직접 확인하고 수정할 수 있어요.</strong>
            </div>
          </div>
        </div>
      </section>

      <section
        id="service-intro"
        class="landing-section landing-problem"
        aria-labelledby="problem-heading"
      >
        <div class="landing-shell">
          <div class="landing-section__heading">
            <p class="landing-eyebrow">서비스 소개</p>
            <h2 id="problem-heading">
              지원 준비가 이어지지 않으면<br />같은 일을 여러 번 하게 돼요.
            </h2>
            <p>
              Hiresemble은 흩어진 정보를 한 번에 없애기보다, 확인한 경험을 다음 준비에 이어 쓰는
              흐름을 만들어요.
            </p>
          </div>
          <div class="problem-list">
            <article v-for="problem in problems" :key="problem.title">
              <AppIcon :name="problem.icon" />
              <h3>{{ problem.title }}</h3>
              <p>{{ problem.description }}</p>
            </article>
          </div>
          <p class="landing-conclusion">
            한 번 정리한 경험을 확인하고,<br />지원하는 공고에 맞춰 다음 준비로 이어갑니다.
          </p>
        </div>
      </section>

      <section
        id="journey"
        class="landing-section landing-journey"
        aria-labelledby="journey-heading"
      >
        <div class="landing-shell">
          <div class="landing-section__heading landing-section__heading--wide">
            <div>
              <p class="landing-eyebrow">이용 흐름</p>
              <h2 id="journey-heading">내 정보부터 면접 준비까지,<br />다섯 단계로 이어져요.</h2>
            </div>
            <p>
              모든 단계를 한 번에 끝낼 필요는 없어요. 지금 준비된 정보부터 시작하고 필요한 순간에
              다음 단계로 넘어가세요.
            </p>
          </div>
          <ol class="journey-list">
            <li v-for="step in PRODUCT_JOURNEY_STEPS" :key="step.number">
              <article>
                <div class="journey-list__number">{{ String(step.number).padStart(2, '0') }}</div>
                <div class="journey-list__icon"><AppIcon :name="step.icon" /></div>
                <div class="journey-list__copy">
                  <h3>{{ step.title }}</h3>
                  <p>{{ step.description }}</p>
                </div>
                <div class="journey-list__preview" aria-hidden="true">
                  <template v-if="step.number === 1"
                    ><span>기본 정보</span><b>확인한 경험</b></template
                  >
                  <template v-else-if="step.number === 2"
                    ><span>이력서.pdf</span><b>내용 확인</b></template
                  >
                  <template v-else-if="step.number === 3"
                    ><span>공고 본문 확인</span><b>자동 분석</b></template
                  >
                  <template v-else-if="step.number === 4"
                    ><span>공고 요구사항</span><b>초안 준비</b></template
                  >
                  <template v-else><span>예상 질문</span><b>답변 피드백</b></template>
                </div>
              </article>
            </li>
          </ol>
        </div>
      </section>

      <section class="landing-section landing-values" aria-labelledby="values-heading">
        <div class="landing-shell">
          <div class="landing-section__heading">
            <p class="landing-eyebrow">Hiresemble이 잇는 것</p>
            <h2 id="values-heading">기능보다 중요한 건<br />준비의 근거와 흐름이에요.</h2>
          </div>
          <div class="value-grid">
            <article v-for="(value, index) in values" :key="value.title">
              <span class="value-grid__number">0{{ index + 1 }}</span>
              <AppIcon :name="value.icon" />
              <h3>{{ value.title }}</h3>
              <p>{{ value.description }}</p>
            </article>
          </div>
        </div>
      </section>

      <section
        id="ai-principles"
        class="landing-section ai-principles"
        aria-labelledby="ai-heading"
      >
        <div class="landing-shell ai-principles__grid">
          <div>
            <p class="landing-eyebrow">AI 활용 원칙</p>
            <h2 id="ai-heading">AI의 결과보다,<br />사용자가 확인한 경험을 먼저 봅니다.</h2>
            <p class="ai-principles__description">
              적합도는 합격 확률이 아니에요. 등록한 정보와 공고 요구사항이 얼마나 일치하는지 준비
              방향을 살펴보는 기준이에요.
            </p>
          </div>
          <ul>
            <li v-for="principle in aiPrinciples" :key="principle">
              <span><AppIcon name="check" /></span>
              {{ principle }}
            </li>
          </ul>
        </div>
      </section>

      <section class="landing-final-cta" aria-labelledby="final-cta-heading">
        <div class="landing-shell landing-final-cta__inner">
          <div>
            <p class="landing-eyebrow">다음 지원을 준비할 때</p>
            <h2 id="final-cta-heading">흩어진 지원 준비를<br />이제 한곳에서 이어가세요.</h2>
          </div>
          <div class="landing-final-cta__actions">
            <RouterLink class="button button--primary landing-button" to="/signup">
              시작하기
              <AppIcon name="arrow-right" />
            </RouterLink>
            <RouterLink class="landing-account-link" to="/login">이미 계정이 있어요</RouterLink>
          </div>
        </div>
      </section>
    </main>

    <footer class="landing-footer">
      <div class="landing-shell landing-footer__inner">
        <RouterLink class="landing-brand" to="/" aria-label="Hiresemble 서비스 소개">
          <BrandMark compact />
        </RouterLink>
        <nav aria-label="서비스 하단 메뉴">
          <a href="#ai-principles">AI 활용 원칙</a>
          <RouterLink to="/login">로그인</RouterLink>
          <RouterLink to="/signup">회원가입</RouterLink>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.landing-page {
  min-width: 20rem;
  overflow: clip;
  background: var(--color-surface);
  color: var(--color-ink);
}

.landing-shell {
  width: min(100% - 3rem, 73.75rem);
  margin-inline: auto;
}

.landing-skip-link {
  position: fixed;
  top: var(--space-3);
  left: var(--space-3);
  z-index: 100;
  border-radius: var(--radius-control);
  background: var(--color-ink);
  color: white;
  padding: 0.625rem 0.875rem;
  font-weight: 700;
}

.landing-header {
  position: sticky;
  top: 0;
  z-index: 50;
  border-bottom: 1px solid rgb(203 209 219 / 65%);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(14px);
}

.landing-header__inner {
  display: grid;
  min-height: 4.5rem;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: clamp(1rem, 3vw, 2.5rem);
}

.landing-brand {
  display: inline-flex;
  width: max-content;
  border-radius: var(--radius-control);
  text-decoration: none;
}

.landing-navigation {
  display: flex;
  justify-content: center;
  gap: clamp(0.25rem, 1vw, 1rem);
}

.landing-navigation a,
.landing-login,
.landing-footer nav a,
.landing-account-link {
  border-radius: var(--radius-control);
  color: var(--color-muted-strong);
  padding: 0.65rem 0.75rem;
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: none;
}

.landing-navigation a:hover,
.landing-login:hover,
.landing-footer nav a:hover,
.landing-account-link:hover {
  color: var(--color-brand-strong);
}

.landing-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.landing-hero {
  position: relative;
  background:
    radial-gradient(circle at 82% 18%, rgb(202 211 255 / 70%), transparent 30rem),
    linear-gradient(180deg, var(--hs-blue-50), white 86%);
  padding-block: clamp(4rem, 9vw, 7.5rem);
}

.landing-hero::before {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgb(49 87 255 / 5%) 1px, transparent 1px);
  background-size: 100% 4rem;
  content: '';
  pointer-events: none;
}

.landing-hero__grid {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(28rem, 1.12fr);
  align-items: center;
  gap: clamp(3rem, 7vw, 7rem);
}

.landing-eyebrow {
  margin: 0 0 var(--space-4);
  color: var(--color-brand);
  font-size: var(--font-size-sm);
  font-weight: 780;
  letter-spacing: 0.04em;
}

.landing-hero h1,
.landing-section__heading h2,
.ai-principles h2,
.landing-final-cta h2 {
  margin: 0;
  letter-spacing: -0.055em;
  word-break: keep-all;
}

.landing-hero h1 {
  font-size: clamp(2.8rem, 5vw, 4.75rem);
  font-weight: 840;
  line-height: 1.08;
}

.landing-hero__description {
  max-width: 38rem;
  margin: var(--space-6) 0 0;
  color: var(--color-text-secondary);
  font-size: clamp(1rem, 1.6vw, 1.125rem);
  line-height: 1.85;
  word-break: keep-all;
}

.landing-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-7);
}

.landing-button {
  min-height: 3rem;
  padding-inline: 1.25rem;
}

.landing-hero__note {
  max-width: 34rem;
  margin: var(--space-5) 0 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.product-preview {
  overflow: hidden;
  border: 1px solid var(--hs-blue-200);
  border-radius: var(--radius-lg);
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 30px 80px rgb(32 57 189 / 16%);
}

.product-preview__top,
.product-preview__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
}

.product-preview__top {
  border-bottom: 1px solid var(--color-border);
}

.product-preview__top > span {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.product-preview__flow {
  margin: 0;
  padding: var(--space-3) var(--space-5);
  list-style: none;
}

.product-preview__flow li {
  position: relative;
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-4);
}

.product-preview__flow li + li {
  border-top: 1px solid var(--color-border);
}

.product-preview__marker {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-neutral-soft);
  color: var(--color-muted-strong);
}

.product-preview__marker .icon {
  width: 1.1rem;
}

.product-preview__flow .is-complete .product-preview__marker {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.product-preview__flow .is-active {
  margin-inline: calc(var(--space-3) * -1);
  border: 1px solid var(--hs-blue-200);
  border-radius: var(--radius-md);
  background: var(--hs-blue-50);
  padding-inline: var(--space-3);
}

.product-preview__flow .is-active + li {
  border-top: 0;
}

.product-preview__flow .is-active .product-preview__marker {
  background: var(--color-brand);
  color: white;
}

.product-preview__flow small,
.product-preview__flow strong {
  display: block;
}

.product-preview__flow small {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.product-preview__flow strong {
  margin-top: 0.1rem;
  font-size: var(--font-size-sm);
}

.product-preview__waiting {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  font-weight: 650;
}

.product-preview__footer {
  align-items: flex-start;
  flex-direction: column;
  gap: var(--space-1);
  background: #11182d;
  color: #c8d2ed;
  font-size: var(--font-size-xs);
}

.product-preview__footer strong {
  color: white;
  font-size: var(--font-size-sm);
}

.landing-section {
  padding-block: clamp(5rem, 10vw, 8rem);
  scroll-margin-top: 4.75rem;
}

.landing-section__heading {
  max-width: 47rem;
}

.landing-section__heading h2,
.ai-principles h2,
.landing-final-cta h2 {
  font-size: clamp(2rem, 3.6vw, 3.25rem);
  font-weight: 810;
  line-height: 1.2;
}

.landing-section__heading > p:last-child,
.landing-section__heading--wide > p {
  margin: var(--space-5) 0 0;
  color: var(--color-muted);
  line-height: 1.8;
  word-break: keep-all;
}

.problem-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: clamp(3rem, 7vw, 5rem);
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.problem-list article {
  padding: var(--space-7) clamp(var(--space-5), 3vw, var(--space-8));
}

.problem-list article + article {
  border-left: 1px solid var(--color-border);
}

.problem-list .icon,
.value-grid .icon {
  width: 1.5rem;
  height: 1.5rem;
  color: var(--color-brand);
}

.problem-list h3,
.value-grid h3,
.journey-list h3 {
  margin: var(--space-4) 0 0;
  font-size: var(--font-size-lg);
  line-height: 1.45;
  word-break: keep-all;
}

.problem-list p,
.value-grid p,
.journey-list p {
  margin: var(--space-3) 0 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  line-height: 1.75;
  word-break: keep-all;
}

.landing-conclusion {
  margin: clamp(3rem, 7vw, 5rem) 0 0;
  color: var(--color-ink-soft);
  font-size: clamp(1.35rem, 2.5vw, 2rem);
  font-weight: 730;
  line-height: 1.55;
  text-align: center;
  word-break: keep-all;
}

.landing-journey {
  background: var(--color-canvas);
}

.landing-section__heading--wide {
  display: grid;
  max-width: none;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 0.65fr);
  align-items: end;
  gap: var(--space-8);
}

.landing-section__heading--wide > p {
  margin: 0;
}

.journey-list {
  margin: clamp(3rem, 7vw, 5rem) 0 0;
  padding: 0;
  list-style: none;
}

.journey-list > li {
  border-top: 1px solid var(--color-border-strong);
}

.journey-list > li:last-child {
  border-bottom: 1px solid var(--color-border-strong);
}

.journey-list article {
  display: grid;
  grid-template-columns: 3.5rem 3rem minmax(0, 1fr) minmax(14rem, 0.72fr);
  align-items: center;
  gap: clamp(var(--space-4), 3vw, var(--space-7));
  padding-block: var(--space-6);
}

.journey-list__number {
  color: var(--color-brand);
  font-size: var(--font-size-xs);
  font-weight: 780;
  font-variant-numeric: tabular-nums;
}

.journey-list__icon {
  display: grid;
  width: 3rem;
  height: 3rem;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-brand);
  box-shadow: var(--shadow-xs);
}

.journey-list__copy h3,
.journey-list__copy p {
  margin-top: 0;
}

.journey-list__preview {
  display: flex;
  min-height: 3.5rem;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-left: 3px solid var(--hs-blue-200);
  background: var(--color-surface);
  padding: var(--space-3) var(--space-4);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.journey-list__preview b {
  color: var(--color-brand-strong);
}

.value-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(var(--space-6), 4vw, var(--space-9));
  margin-top: clamp(3rem, 7vw, 5rem);
}

.value-grid article {
  position: relative;
  border-top: 3px solid var(--color-brand);
  padding-top: var(--space-6);
}

.value-grid__number {
  position: absolute;
  top: var(--space-5);
  right: 0;
  color: var(--hs-blue-200);
  font-size: 2rem;
  font-weight: 800;
}

.ai-principles {
  background: #11182d;
  color: white;
}

.ai-principles__grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(22rem, 1.1fr);
  align-items: start;
  gap: clamp(3rem, 8vw, 8rem);
}

.ai-principles h2 {
  color: white;
}

.ai-principles__description {
  margin: var(--space-6) 0 0;
  color: #aab7d7;
  line-height: 1.85;
  word-break: keep-all;
}

.ai-principles ul {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-principles li {
  display: grid;
  grid-template-columns: 2.25rem minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
  border-bottom: 1px solid rgb(255 255 255 / 16%);
  padding-block: var(--space-4);
  color: #dfe5f5;
  line-height: 1.65;
}

.ai-principles li:first-child {
  border-top: 1px solid rgb(255 255 255 / 16%);
}

.ai-principles li > span {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 50%;
  background: rgb(116 138 255 / 18%);
  color: var(--hs-blue-300);
}

.landing-final-cta {
  padding-block: clamp(5rem, 10vw, 8rem);
}

.landing-final-cta__inner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-8);
}

.landing-final-cta__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.landing-footer {
  border-top: 1px solid var(--color-border);
  background: var(--color-canvas);
}

.landing-footer__inner {
  display: flex;
  min-height: 6rem;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
}

.landing-footer nav {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-1);
}

@media (max-width: 62rem) {
  .landing-hero__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .landing-hero__copy {
    max-width: 44rem;
  }

  .product-preview {
    width: min(100%, 43rem);
    margin-inline: auto;
  }

  .landing-section__heading--wide,
  .ai-principles__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .landing-section__heading--wide > p {
    max-width: 40rem;
  }
}

@media (max-width: 48rem) {
  .landing-shell {
    width: min(100% - 2rem, 73.75rem);
  }

  .landing-navigation {
    display: none;
  }

  .landing-header__inner {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .landing-header__actions {
    grid-column: 2;
  }

  .problem-list,
  .value-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .problem-list article + article {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .journey-list article {
    grid-template-columns: 2.5rem minmax(0, 1fr);
  }

  .journey-list__icon {
    display: none;
  }

  .journey-list__preview {
    grid-column: 2;
  }

  .landing-final-cta__inner {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 27rem) {
  .landing-header__inner {
    gap: var(--space-2);
  }

  .landing-header :deep(.brand-lockup__name) {
    display: none;
  }

  .landing-header__actions {
    gap: 0;
  }

  .landing-login {
    padding-inline: var(--space-2);
  }

  .landing-header .button {
    padding-inline: var(--space-3);
  }

  .landing-hero h1 {
    font-size: clamp(2.35rem, 12vw, 3rem);
  }

  .landing-hero__actions,
  .landing-final-cta__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .landing-hero__actions .button,
  .landing-final-cta__actions .button {
    width: 100%;
  }

  .product-preview__top,
  .product-preview__flow,
  .product-preview__footer {
    padding-inline: var(--space-4);
  }

  .product-preview__flow li {
    grid-template-columns: 2.5rem minmax(0, 1fr);
  }

  .product-preview__flow li > :last-child {
    grid-column: 2;
    justify-self: start;
  }

  .landing-footer__inner {
    align-items: flex-start;
    flex-direction: column;
    padding-block: var(--space-6);
  }

  .landing-footer nav {
    justify-content: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing-header {
    backdrop-filter: none;
  }
}
</style>
