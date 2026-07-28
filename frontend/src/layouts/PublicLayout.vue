<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

import BrandMark from '@/shared/ui/BrandMark.vue'
</script>

<template>
  <main class="public-shell" data-testid="public-layout">
    <section class="auth-panel" aria-label="인증" data-testid="public-auth-panel">
      <RouterLink class="auth-brand auth-brand--mobile" to="/" aria-label="Hiresemble 시작 화면">
        <BrandMark />
      </RouterLink>
      <div class="auth-form">
        <RouterView />
      </div>
      <p class="auth-privacy-note">
        계정 정보와 등록한 자료는 취업 준비 기능을 제공하는 데 사용해요. 회원가입할 때 개인정보와 AI
        처리 동의를 직접 확인할 수 있어요.
      </p>
    </section>

    <section class="brand-canvas" aria-label="Hiresemble 서비스 안내">
      <RouterLink class="auth-brand" to="/" aria-label="Hiresemble 시작 화면">
        <BrandMark inverse />
      </RouterLink>

      <div class="brand-orbit" aria-hidden="true">
        <span class="brand-orbit__ring brand-orbit__ring--outer" />
        <span class="brand-orbit__ring brand-orbit__ring--inner" />
        <span class="brand-orbit__line brand-orbit__line--one" />
        <span class="brand-orbit__line brand-orbit__line--two" />
        <span class="brand-orbit__node brand-orbit__node--one" />
        <span class="brand-orbit__node brand-orbit__node--two" />
        <span class="brand-orbit__node brand-orbit__node--three" />
        <span class="brand-orbit__node brand-orbit__node--four" />
      </div>

      <div class="auth-context">
        <p class="auth-context__eyebrow">내 경력을, 다음 기회로</p>
        <h2 class="auth-context__title">
          흩어진 경험을 모아
          <span>나답게 지원해요.</span>
        </h2>
        <p class="auth-context__description">
          프로필부터 이력서, 관심 공고까지 한곳에 정리하고 필요한 준비를 이어갈 수 있어요.
        </p>
        <ol class="auth-context__steps">
          <li>
            <span>01</span>
            <strong>경험을 정리해요</strong>
          </li>
          <li>
            <span>02</span>
            <strong>공고를 모아 봐요</strong>
          </li>
          <li>
            <span>03</span>
            <strong>준비 과정을 확인해요</strong>
          </li>
        </ol>
      </div>

      <p class="auth-context__footnote">
        AI는 사용자가 요청한 준비 과정에서만 활용해요. 중요한 선택과 경력 정보의 주인은 사용자예요.
      </p>
    </section>
  </main>
</template>

<style scoped>
.public-shell {
  position: relative;
  display: grid;
  min-height: 100dvh;
  grid-template-columns: minmax(0, 56rem) minmax(27rem, 35rem);
  justify-content: center;
  gap: clamp(2rem, 4vw, 4rem);
  overflow: hidden;
  background: #11182d;
  padding: clamp(1.5rem, 4vw, 4rem);
}

.brand-canvas {
  position: relative;
  display: flex;
  grid-column: 1;
  grid-row: 1;
  min-width: 0;
  min-height: calc(100dvh - clamp(3rem, 8vw, 8rem));
  flex-direction: column;
  color: white;
  isolation: isolate;
}

.auth-brand {
  position: relative;
  z-index: 2;
  width: max-content;
  text-decoration: none;
}

.auth-brand--mobile {
  display: none;
}

.auth-context {
  position: relative;
  z-index: 2;
  max-width: 47rem;
  margin-block: auto;
  padding-block: clamp(4rem, 11vh, 8rem);
}

.auth-context__eyebrow {
  margin: 0 0 1rem;
  color: var(--hs-blue-300);
  font-size: 0.8125rem;
  font-weight: 780;
  letter-spacing: 0.04em;
}

.auth-context__title {
  max-width: 43rem;
  margin: 0;
  color: white;
  font-size: clamp(3rem, 5vw, 5.25rem);
  font-weight: 830;
  line-height: 0.99;
  letter-spacing: -0.075em;
  word-break: keep-all;
}

.auth-context__title span {
  display: block;
  margin-top: 0.15em;
  color: var(--hs-blue-300);
}

.auth-context__description {
  max-width: 36rem;
  margin: 1.75rem 0 0;
  color: #c8d2ed;
  font-size: clamp(0.9375rem, 1.4vw, 1.125rem);
  line-height: 1.75;
  word-break: keep-all;
}

.auth-context__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 2.5rem 0 0;
  padding: 0;
  list-style: none;
}

.auth-context__steps li {
  position: relative;
  display: grid;
  min-width: 10rem;
  gap: 0.25rem;
  border-top: 1px solid rgb(255 255 255 / 28%);
  padding: 0.875rem 1.5rem 0 0;
}

.auth-context__steps li:not(:last-child)::after {
  position: absolute;
  top: -0.25rem;
  right: 0.9rem;
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: var(--hs-blue-400);
  content: '';
}

.auth-context__steps span {
  color: #7e91c4;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
}

.auth-context__steps strong {
  color: #e7ecfa;
  font-size: 0.8125rem;
  font-weight: 650;
}

.auth-context__footnote {
  position: relative;
  z-index: 2;
  margin: 0;
  color: #8897bc;
  font-size: 0.75rem;
  line-height: 1.65;
}

.brand-orbit {
  position: absolute;
  top: 50%;
  left: 58%;
  z-index: 1;
  width: min(34vw, 30rem);
  aspect-ratio: 1;
  opacity: 0.32;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.brand-orbit__ring,
.brand-orbit__line,
.brand-orbit__node {
  position: absolute;
}

.brand-orbit__ring {
  border: 1px solid #5571bd;
  border-radius: 50%;
}

.brand-orbit__ring--outer {
  inset: 0;
  animation: orbit-turn 28s linear infinite;
}

.brand-orbit__ring--inner {
  inset: 24%;
  border-style: dashed;
  animation: orbit-turn 22s linear infinite reverse;
}

.brand-orbit__line {
  top: 50%;
  left: 10%;
  width: 80%;
  height: 1px;
  background: #4965ad;
  transform-origin: center;
}

.brand-orbit__line--one {
  transform: rotate(24deg);
}

.brand-orbit__line--two {
  transform: rotate(-38deg);
}

.brand-orbit__node {
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid #11182d;
  border-radius: 50%;
  background: var(--hs-blue-300);
  box-shadow: 0 0 0 1px var(--hs-blue-300);
  animation: node-drift 20s ease-in-out infinite;
}

.brand-orbit__node--one {
  top: 8%;
  left: 48%;
}

.brand-orbit__node--two {
  top: 48%;
  right: 4%;
  width: 1rem;
  height: 1rem;
  background: var(--hs-blue-400);
  box-shadow: 0 0 0 1px var(--hs-blue-400);
  animation-delay: -5s;
}

.brand-orbit__node--three {
  bottom: 12%;
  left: 25%;
  animation-delay: -10s;
}

.brand-orbit__node--four {
  top: 36%;
  left: 21%;
  width: 0.5rem;
  height: 0.5rem;
  background: white;
  box-shadow: 0 0 0 1px white;
  animation-delay: -14s;
}

.auth-panel {
  position: relative;
  z-index: 3;
  align-self: center;
  grid-column: 2;
  grid-row: 1;
  width: 100%;
  max-height: calc(100dvh - 3rem);
  overflow-y: auto;
  border: 1px solid rgb(255 255 255 / 55%);
  border-radius: 1rem;
  background: var(--color-surface);
  box-shadow: 0 28px 70px rgb(2 7 26 / 36%);
  padding: clamp(2rem, 4vw, 3.75rem);
}

.auth-form {
  width: 100%;
  max-width: 27rem;
  margin-inline: auto;
}

.auth-privacy-note {
  width: 100%;
  max-width: 27rem;
  margin: 2rem auto 0;
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  padding-top: 1rem;
  font-size: 0.75rem;
  line-height: 1.65;
}

@keyframes orbit-turn {
  to {
    transform: rotate(360deg);
  }
}

@keyframes node-drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0.5rem, -0.75rem, 0);
  }
}

@media (max-width: 1099px) {
  .public-shell {
    grid-template-columns: minmax(0, 1fr) minmax(25rem, 31rem);
    gap: 2rem;
  }

  .auth-context__title {
    font-size: clamp(3rem, 5.8vw, 4.75rem);
  }

  .auth-context__steps {
    display: grid;
    max-width: 24rem;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .auth-context__steps li {
    min-width: 0;
    padding-right: 0.75rem;
  }
}

@media (max-width: 767px) {
  .public-shell {
    display: flex;
    min-height: 100dvh;
    flex-direction: column;
    background: var(--color-canvas);
    padding: 0;
  }

  .auth-panel {
    max-height: none;
    min-height: auto;
    align-self: stretch;
    border: 0;
    border-radius: 0;
    box-shadow: none;
    padding: 1.25rem clamp(1.25rem, 7vw, 2.5rem) 2.5rem;
  }

  .auth-brand--mobile {
    display: block;
    margin-bottom: 2.5rem;
  }

  .brand-canvas {
    min-height: 24rem;
    background: #11182d;
    padding: 2.5rem clamp(1.25rem, 7vw, 2.5rem);
  }

  .brand-canvas > .auth-brand,
  .auth-context__steps {
    display: none;
  }

  .auth-context {
    margin: auto 0;
    padding: 1rem 0;
  }

  .auth-context__title {
    max-width: 22rem;
    font-size: clamp(2.25rem, 12vw, 3.5rem);
  }

  .auth-context__description {
    margin-top: 1.25rem;
    font-size: 0.875rem;
  }

  .auth-context__footnote {
    margin-top: 2rem;
  }

  .brand-orbit {
    top: 56%;
    left: 75%;
    width: 23rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-orbit__ring,
  .brand-orbit__node {
    animation: none;
  }
}
</style>
