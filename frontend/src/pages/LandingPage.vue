<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import { PRODUCT_JOURNEY_STEPS } from '@/shared/ui/productJourney'

import LandingProductDemo from './LandingProductDemo.vue'

const landingRoot = ref<HTMLElement | null>(null)
const motionReady = ref(false)
const heroRevealed = ref(false)

let revealObserver: IntersectionObserver | undefined
let firstFrame: number | undefined
let secondFrame: number | undefined

function revealEverySection(): void {
  landingRoot.value
    ?.querySelectorAll<HTMLElement>('[data-reveal-section]')
    .forEach((section) => section.classList.add('is-revealed'))
}

onMounted(() => {
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  if (reducedMotion || !('IntersectionObserver' in window)) {
    heroRevealed.value = true
    revealEverySection()
    return
  }

  motionReady.value = true
  firstFrame = window.requestAnimationFrame(() => {
    secondFrame = window.requestAnimationFrame(() => {
      heroRevealed.value = true
    })
  })

  try {
    revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return
          entry.target.classList.add('is-revealed')
          revealObserver?.unobserve(entry.target)
        })
      },
      { threshold: 0.08, rootMargin: '0px 0px -6% 0px' },
    )

    landingRoot.value
      ?.querySelectorAll<HTMLElement>('[data-reveal-section]')
      .forEach((section) => revealObserver?.observe(section))
  } catch {
    revealEverySection()
  }
})

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  if (firstFrame !== undefined) window.cancelAnimationFrame(firstFrame)
  if (secondFrame !== undefined) window.cancelAnimationFrame(secondFrame)
})

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
  <div ref="landingRoot" class="landing-page" :class="{ 'motion-ready': motionReady }">
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
        <div class="landing-hero__orbit" aria-hidden="true">
          <span class="landing-hero__orbit-line" />
          <span class="landing-hero__orbit-chip landing-hero__orbit-chip--one">경험 확인</span>
          <span class="landing-hero__orbit-chip landing-hero__orbit-chip--two">공고 분석</span>
          <span class="landing-hero__orbit-chip landing-hero__orbit-chip--three">다음 준비</span>
        </div>
        <div class="landing-shell landing-hero__inner" :class="{ 'is-revealed': heroRevealed }">
          <div class="landing-hero__heading">
            <p class="landing-eyebrow" data-hero-reveal style="--reveal-order: 0">
              내 경험을, 다음 기회로
            </p>
            <h1 id="landing-heading">
              <span data-hero-reveal style="--reveal-order: 1">흩어진 취업 준비를,</span>
              <span data-hero-reveal style="--reveal-order: 2">하나의 흐름으로.</span>
            </h1>
          </div>

          <div class="landing-hero__body">
            <div class="landing-hero__copy">
              <p class="landing-hero__description" data-hero-reveal style="--reveal-order: 3">
                이력서와 포트폴리오에서 경험을 정리하고, 관심 공고 분석부터 자기소개서와 면접
                준비까지 이어가세요.
              </p>
              <div class="landing-hero__actions" data-hero-reveal style="--reveal-order: 4">
                <RouterLink class="button button--primary landing-button" to="/signup">
                  시작하기
                  <AppIcon name="arrow-right" />
                </RouterLink>
                <RouterLink class="button button--secondary landing-button" to="/login">
                  로그인
                </RouterLink>
              </div>
              <dl class="landing-hero__signals" data-hero-reveal style="--reveal-order: 5">
                <div>
                  <dt><AppIcon name="check" /></dt>
                  <dd>확인한 경험 중심</dd>
                </div>
                <div>
                  <dt><AppIcon name="sparkle" /></dt>
                  <dd>공고 맞춤 분석</dd>
                </div>
                <div>
                  <dt><AppIcon name="arrow-right" /></dt>
                  <dd>다음 준비 연결</dd>
                </div>
              </dl>
              <p class="landing-hero__note" data-hero-reveal style="--reveal-order: 6">
                AI가 지원을 대신하지 않아요. 확인한 경험을 바탕으로 다음 준비를 이어갈 수 있게
                도와요.
              </p>
            </div>

            <LandingProductDemo data-hero-reveal style="--reveal-order: 7" />
          </div>
        </div>
      </section>

      <section class="landing-flow-band" aria-label="Hiresemble 지원 준비 흐름">
        <div class="landing-flow-band__track">
          <ul>
            <li v-for="step in PRODUCT_JOURNEY_STEPS" :key="`flow-${step.number}`">
              <AppIcon :name="step.icon" />
              <span>{{ step.title }}</span>
              <i aria-hidden="true">·</i>
            </li>
          </ul>
          <ul aria-hidden="true">
            <li v-for="step in PRODUCT_JOURNEY_STEPS" :key="`flow-copy-${step.number}`">
              <AppIcon :name="step.icon" />
              <span>{{ step.title }}</span>
              <i>·</i>
            </li>
          </ul>
        </div>
      </section>

      <section
        id="service-intro"
        class="landing-section landing-problem"
        aria-labelledby="problem-heading"
        data-reveal-section
      >
        <div class="landing-shell">
          <div class="landing-section__heading" data-reveal-item style="--reveal-order: 0">
            <p class="landing-eyebrow">서비스 소개</p>
            <h2 id="problem-heading">
              준비가 한곳에 쌓이지 않으면<br />지원할 때마다 다시 정리해야 해요.
            </h2>
            <p>
              Hiresemble은 흩어진 정보를 한 번에 없애기보다, 확인한 경험을 다음 준비에 이어 쓰는
              흐름을 만들어요.
            </p>
          </div>
          <div class="problem-list">
            <article
              v-for="(problem, index) in problems"
              :key="problem.title"
              data-reveal-item
              :style="{ '--reveal-order': index + 1 }"
            >
              <AppIcon :name="problem.icon" />
              <h3>{{ problem.title }}</h3>
              <p>{{ problem.description }}</p>
            </article>
          </div>
          <p class="landing-conclusion" data-reveal-item style="--reveal-order: 4">
            한 번 정리한 경험을 확인하고,<br />지원하는 공고에 맞춰 다음 준비로 이어갑니다.
          </p>
        </div>
      </section>

      <section
        id="journey"
        class="landing-section landing-journey"
        aria-labelledby="journey-heading"
        data-reveal-section
      >
        <div class="landing-shell">
          <div
            class="landing-section__heading landing-section__heading--wide"
            data-reveal-item
            style="--reveal-order: 0"
          >
            <div>
              <p class="landing-eyebrow">이용 흐름</p>
              <h2 id="journey-heading">지원 정보부터 면접 준비까지,<br />다섯 단계로 이어져요.</h2>
            </div>
            <p>
              모든 단계를 한 번에 끝낼 필요는 없어요. 지금 준비된 정보부터 시작하고 필요한 순간에
              다음 단계로 넘어가세요.
            </p>
          </div>
          <ol class="journey-list">
            <li
              v-for="step in PRODUCT_JOURNEY_STEPS"
              :key="step.number"
              data-reveal-item
              :style="{ '--reveal-order': step.number }"
            >
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

      <section
        class="landing-section landing-values"
        aria-labelledby="values-heading"
        data-reveal-section
      >
        <div class="landing-shell">
          <div class="landing-section__heading" data-reveal-item style="--reveal-order: 0">
            <p class="landing-eyebrow">Hiresemble이 잇는 것</p>
            <h2 id="values-heading">쌓아 온 경험이,<br />다음 지원의 준비로 이어져요.</h2>
          </div>
          <div class="value-grid">
            <article
              v-for="(value, index) in values"
              :key="value.title"
              data-reveal-item
              :style="{ '--reveal-order': index + 1 }"
            >
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
        data-reveal-section
      >
        <div class="landing-shell ai-principles__grid">
          <div data-reveal-item style="--reveal-order: 0">
            <p class="landing-eyebrow">AI 활용 원칙</p>
            <h2 id="ai-heading">AI가 찾아낸 경험도,<br />사용자가 확인한 뒤에 활용해요.</h2>
            <p class="ai-principles__description">
              적합도는 합격 확률이 아니에요. 등록한 정보와 공고 요구사항이 얼마나 일치하는지 준비
              방향을 살펴보는 기준이에요.
            </p>
          </div>
          <ul>
            <li
              v-for="(principle, index) in aiPrinciples"
              :key="principle"
              data-reveal-item
              :style="{ '--reveal-order': index + 1 }"
            >
              <span><AppIcon name="check" /></span>
              {{ principle }}
            </li>
          </ul>
        </div>
      </section>

      <section class="landing-final-cta" aria-labelledby="final-cta-heading" data-reveal-section>
        <div class="landing-shell landing-final-cta__inner">
          <div data-reveal-item style="--reveal-order: 0">
            <p class="landing-eyebrow">다음 지원을 준비할 때</p>
            <h2 id="final-cta-heading">흩어진 지원 준비를<br />이제 한곳에서 이어가세요.</h2>
          </div>
          <div class="landing-final-cta__actions" data-reveal-item style="--reveal-order: 1">
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

.landing-hero__inner {
  position: relative;
}

.landing-hero__heading {
  max-width: 72rem;
}

.landing-hero__body {
  display: grid;
  grid-template-columns: minmax(17rem, 0.72fr) minmax(32rem, 1.28fr);
  align-items: center;
  gap: clamp(3rem, 6vw, 5.5rem);
  margin-top: clamp(2.75rem, 5vw, 4.5rem);
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
  font-size: clamp(2.8rem, 5.44vw, 5rem);
  font-weight: 840;
  line-height: 0.99;
}

.landing-hero h1 span {
  display: block;
}

.landing-hero__description {
  max-width: 31rem;
  margin: 0;
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

.landing-page.motion-ready .landing-hero__inner:not(.is-revealed) [data-hero-reveal],
.landing-page.motion-ready [data-reveal-section]:not(.is-revealed) [data-reveal-item] {
  opacity: 0;
  transform: translateY(22px);
}

.landing-page.motion-ready .landing-hero__inner.is-revealed [data-hero-reveal],
.landing-page.motion-ready [data-reveal-section].is-revealed [data-reveal-item] {
  animation: landing-reveal-up 650ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--reveal-order, 0) * 80ms);
}

@keyframes landing-reveal-up {
  from {
    opacity: 0;
    transform: translateY(22px);
  }

  to {
    opacity: 1;
    transform: none;
  }
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
  .landing-hero__body {
    grid-template-columns: minmax(0, 1fr);
    gap: clamp(2.5rem, 6vw, 4rem);
  }

  .landing-hero__copy {
    max-width: 38rem;
  }

  .landing-hero :deep(.landing-demo) {
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

  .landing-hero {
    padding-block: clamp(3.25rem, 10vw, 5rem);
  }

  .landing-hero h1 {
    font-size: clamp(2.2rem, 8.4vw, 3.4rem);
    line-height: 1.02;
  }

  .landing-hero__body {
    margin-top: var(--space-8);
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
    font-size: clamp(1.88rem, 9.6vw, 2.4rem);
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

  .landing-page.motion-ready .landing-hero__inner [data-hero-reveal],
  .landing-page.motion-ready [data-reveal-section] [data-reveal-item] {
    opacity: 1;
    animation: none;
    transform: none;
  }
}

/* Reference-inspired kinetic polish, constrained to the Hiresemble Blue system. */
.landing-hero {
  isolation: isolate;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 14%, rgb(164 179 255 / 48%), transparent 25rem),
    radial-gradient(circle at 10% 88%, rgb(197 231 255 / 44%), transparent 24rem),
    linear-gradient(160deg, #f7f8ff 0%, var(--hs-blue-50) 45%, #ffffff 88%);
}

.landing-hero::before {
  z-index: 0;
  background-image:
    linear-gradient(rgb(49 87 255 / 5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(49 87 255 / 4%) 1px, transparent 1px);
  background-size: 4rem 4rem;
  mask-image: linear-gradient(to bottom, black, transparent 88%);
}

.landing-hero::after {
  position: absolute;
  z-index: 0;
  right: -9rem;
  bottom: -15rem;
  width: 38rem;
  height: 38rem;
  border: 1px solid rgb(49 87 255 / 9%);
  border-radius: 50%;
  box-shadow:
    0 0 0 5rem rgb(49 87 255 / 3%),
    0 0 0 10rem rgb(49 87 255 / 2%);
  content: '';
}

.landing-hero__orbit {
  position: absolute;
  z-index: 0;
  top: 6rem;
  right: max(-5rem, calc((100vw - 73.75rem) / 2 - 8rem));
  width: 29rem;
  height: 29rem;
  opacity: 0.7;
  pointer-events: none;
}

.landing-hero__inner {
  z-index: 1;
}

.landing-hero__orbit-line {
  position: absolute;
  inset: 2rem;
  border: 1px dashed rgb(49 87 255 / 20%);
  border-radius: 50%;
  animation: landing-orbit-spin 28s linear infinite;
}

.landing-hero__orbit-chip {
  position: absolute;
  display: inline-flex;
  align-items: center;
  border: 1px solid rgb(202 211 255 / 86%);
  border-radius: 999px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 10px 30px rgb(32 57 189 / 10%);
  padding: 0.45rem 0.75rem;
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 760;
  backdrop-filter: blur(10px);
  animation: landing-orbit-float 5s ease-in-out infinite;
}

.landing-hero__orbit-chip--one {
  top: 1rem;
  left: 9rem;
}

.landing-hero__orbit-chip--two {
  top: 13rem;
  right: 0;
  animation-delay: -1.8s;
}

.landing-hero__orbit-chip--three {
  bottom: 1rem;
  left: 3rem;
  animation-delay: -3.4s;
}

@keyframes landing-orbit-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes landing-orbit-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-10px);
  }
}

.landing-hero h1 span:last-child {
  display: inline-block;
  position: relative;
  color: var(--color-brand-strong);
}

.landing-hero h1 span:last-child::after {
  position: absolute;
  z-index: -1;
  right: -0.05em;
  bottom: 0.04em;
  left: -0.05em;
  height: 0.16em;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--hs-blue-200), rgb(116 138 255 / 30%));
  content: '';
  transform: rotate(-1deg);
}

.landing-hero__signals {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: var(--space-5) 0 0;
}

.landing-hero__signals > div {
  display: inline-flex;
  min-height: 2.25rem;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid rgb(202 211 255 / 88%);
  border-radius: 999px;
  background: rgb(255 255 255 / 76%);
  padding: 0.4rem 0.7rem;
  box-shadow: var(--shadow-xs);
}

.landing-hero__signals dt {
  display: grid;
  color: var(--color-brand);
}

.landing-hero__signals dt .icon {
  width: 0.9rem;
  height: 0.9rem;
}

.landing-hero__signals dd {
  color: var(--color-ink-soft);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.landing-flow-band {
  overflow: hidden;
  border-block: 1px solid rgb(255 255 255 / 11%);
  background: #11182d;
  color: white;
}

.landing-flow-band__track {
  display: flex;
  width: max-content;
  gap: var(--space-8);
  padding-block: var(--space-4);
  animation: landing-flow-marquee 34s linear infinite;
}

.landing-flow-band ul {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-8);
  margin: 0;
  padding: 0;
  list-style: none;
}

.landing-flow-band li {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  color: #dfe5f5;
  font-size: var(--font-size-sm);
  font-weight: 720;
  white-space: nowrap;
}

.landing-flow-band li .icon {
  width: 1.1rem;
  height: 1.1rem;
  color: var(--hs-blue-300);
}

.landing-flow-band li i {
  margin-left: var(--space-5);
  color: var(--hs-blue-400);
  font-size: 1.2rem;
  font-style: normal;
}

@keyframes landing-flow-marquee {
  to {
    transform: translateX(calc(-50% - var(--space-4)));
  }
}

.problem-list article,
.journey-list article,
.value-grid article {
  transition:
    background var(--motion-base),
    border-color var(--motion-base),
    box-shadow var(--motion-base),
    transform var(--motion-base);
}

.problem-list article:hover {
  background: linear-gradient(145deg, var(--color-brand-soft), white);
  transform: translateY(-4px);
}

.problem-list .icon,
.value-grid > article > .icon {
  box-sizing: content-box;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  padding: var(--space-3);
}

.journey-list > li {
  overflow: hidden;
}

.journey-list > li:hover article {
  padding-inline: var(--space-4);
  background: rgb(255 255 255 / 72%);
  transform: translateX(4px);
}

.journey-list__preview {
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--hs-blue-300);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.value-grid article {
  min-height: 19rem;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--color-brand);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 100% 0%, rgb(202 211 255 / 45%), transparent 9rem),
    var(--color-surface);
  padding: var(--space-6);
  box-shadow: var(--shadow-xs);
}

.value-grid article:hover {
  border-color: var(--color-brand-border);
  box-shadow: 0 20px 42px rgb(32 57 189 / 10%);
  transform: translateY(-5px);
}

.value-grid__number {
  top: var(--space-5);
  right: var(--space-5);
  color: rgb(49 87 255 / 18%);
  font-size: 3.75rem;
  line-height: 1;
}

.landing-final-cta {
  background: linear-gradient(180deg, white, var(--color-canvas));
}

.landing-final-cta__inner {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 92% 12%, rgb(164 179 255 / 38%), transparent 18rem),
    linear-gradient(145deg, #17224d, var(--hs-blue-700));
  box-shadow: 0 30px 70px rgb(32 57 189 / 20%);
  padding: clamp(var(--space-7), 6vw, var(--space-10));
  color: white;
}

.landing-final-cta__inner::after {
  position: absolute;
  right: -7rem;
  bottom: -9rem;
  width: 19rem;
  height: 19rem;
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 50%;
  box-shadow:
    0 0 0 3rem rgb(255 255 255 / 4%),
    0 0 0 6rem rgb(255 255 255 / 2%);
  content: '';
  pointer-events: none;
}

.landing-final-cta .landing-eyebrow {
  color: var(--hs-blue-300);
}

.landing-final-cta h2 {
  color: white;
}

.landing-final-cta__actions {
  position: relative;
  z-index: 1;
}

.landing-final-cta .button--primary {
  border-color: white;
  background: white;
  color: var(--color-brand-strong);
}

.landing-final-cta .button--primary:hover {
  background: var(--hs-blue-50);
}

.landing-final-cta .landing-account-link {
  color: rgb(255 255 255 / 78%);
}

.landing-final-cta .landing-account-link:hover {
  color: white;
}

@media (max-width: 62rem) {
  .landing-hero__orbit {
    top: 28rem;
    right: -9rem;
  }

  .value-grid article {
    min-height: 17rem;
  }
}

@media (max-width: 48rem) {
  .landing-hero__orbit {
    display: none;
  }

  .landing-flow-band__track,
  .landing-flow-band ul {
    gap: var(--space-6);
  }

  .landing-flow-band__track {
    animation-duration: 25s;
  }

  .value-grid article {
    min-height: 0;
  }

  .landing-final-cta__inner {
    margin-inline: -0.25rem;
  }
}

@media (max-width: 27rem) {
  .landing-hero__signals {
    display: grid;
    grid-template-columns: 1fr;
  }

  .landing-hero__signals > div {
    width: max-content;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing-hero__orbit-line,
  .landing-hero__orbit-chip,
  .landing-flow-band__track {
    animation: none;
  }

  .problem-list article,
  .journey-list article,
  .value-grid article {
    transition: none;
  }
}
</style>
