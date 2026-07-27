<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'
</script>

<template>
  <main class="public-shell" data-testid="public-layout">
    <div class="public-shell__grid">
      <section class="auth-panel" aria-label="인증" data-testid="public-auth-panel">
        <RouterLink class="auth-brand auth-brand--mobile" to="/" aria-label="Hiresemble 시작 화면">
          <span class="auth-brand__mark" aria-hidden="true">H</span>
          <span>Hiresemble</span>
        </RouterLink>
        <div class="auth-form">
          <RouterView />
        </div>
        <p class="auth-privacy-note">
          계정 정보와 업로드한 자료는 취업 준비 기능 제공을 위해 사용됩니다. 가입 시 개인정보 처리와
          AI 처리 동의를 직접 확인합니다.
        </p>
      </section>

      <aside class="auth-context" aria-label="Hiresemble 서비스 안내">
        <RouterLink class="auth-brand" to="/" aria-label="Hiresemble 시작 화면">
          <span class="auth-brand__mark" aria-hidden="true">H</span>
          <span>Hiresemble</span>
        </RouterLink>
        <div class="auth-context__body">
          <p class="auth-context__eyebrow">Career workspace</p>
          <p class="auth-context__title">지원 준비의 근거와 진행 상황을 한곳에서 관리하세요.</p>
          <p class="auth-context__description">
            프로필, 증빙 문서, 채용 공고와 비동기 작업 기록을 분리된 상태로 확인할 수 있는 개인 취업
            준비 공간입니다.
          </p>
          <ul class="auth-context__list">
            <li>
              <AppIcon name="check" />
              <span>경력 정보와 승인한 근거를 구조적으로 관리합니다.</span>
            </li>
            <li>
              <AppIcon name="check" />
              <span>문서 처리와 공고 추출 상태를 단계별로 확인합니다.</span>
            </li>
            <li>
              <AppIcon name="check" />
              <span>진행 중인 작업과 필요한 사용자 조치를 놓치지 않습니다.</span>
            </li>
          </ul>
        </div>
        <p class="auth-context__footnote">
          AI 처리는 필요한 작업 흐름 안에서만 사용되며, 사용자의 경력 정보와 명시적인 선택이
          중심입니다.
        </p>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.public-shell {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  background: var(--color-canvas);
  padding: clamp(1rem, 3vw, 2.5rem);
}

.public-shell__grid {
  display: grid;
  width: min(100%, 72rem);
  min-height: min(46rem, calc(100dvh - 5rem));
  grid-template-areas: 'context form';
  grid-template-columns: minmax(0, 1fr) minmax(24rem, 0.9fr);
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.auth-panel {
  grid-area: form;
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: clamp(2rem, 5vw, 4.5rem);
}

.auth-form {
  width: 100%;
  max-width: 27rem;
  margin: auto;
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  color: #f3f8f9;
  font-size: 1.125rem;
  font-weight: 760;
  letter-spacing: -0.02em;
  text-decoration: none;
}

.auth-brand--mobile {
  display: none;
  color: var(--color-ink);
}

.auth-brand__mark {
  display: inline-grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border: 1px solid #5f99a2;
  border-radius: var(--radius-md);
  background: #0b6673;
  color: white;
  font-weight: 800;
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

.auth-context {
  grid-area: context;
  display: flex;
  min-width: 0;
  flex-direction: column;
  background: #173840;
  color: #e8f1f3;
  padding: clamp(2rem, 5vw, 4.5rem);
}

.auth-context__body {
  margin-block: auto;
}

.auth-context__eyebrow {
  margin: 0 0 0.75rem;
  color: #86c9ce;
  font-size: 0.75rem;
  font-weight: 760;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.auth-context__title {
  max-width: 31rem;
  margin: 0;
  color: white;
  font-size: clamp(1.75rem, 1.4rem + 1.1vw, 2.5rem);
  font-weight: 730;
  line-height: 1.3;
  letter-spacing: -0.035em;
}

.auth-context__description {
  max-width: 31rem;
  margin: 1.25rem 0 0;
  color: #bed0d4;
  font-size: 0.9375rem;
  line-height: 1.75;
}

.auth-context__list {
  display: grid;
  gap: 0.875rem;
  max-width: 31rem;
  margin: 2rem 0 0;
  padding: 0;
  list-style: none;
}

.auth-context__list li {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  color: #dae7e9;
  font-size: 0.875rem;
  line-height: 1.6;
}

.auth-context__list .icon {
  margin-top: 0.2rem;
  color: #86c9ce;
}

.auth-context__footnote {
  margin: 2rem 0 0;
  border-top: 1px solid #36575f;
  color: #9fb8be;
  padding-top: 1rem;
  font-size: 0.75rem;
  line-height: 1.65;
}

@media (max-width: 767px) {
  .public-shell {
    display: block;
    background: var(--color-canvas);
    padding: 0;
  }

  .public-shell__grid {
    min-height: 100dvh;
    grid-template-areas:
      'form'
      'context';
    grid-template-columns: minmax(0, 1fr);
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .auth-panel {
    min-height: 100dvh;
    justify-content: flex-start;
    padding: 1.5rem clamp(1.25rem, 7vw, 2.5rem) 2.5rem;
  }

  .auth-brand--mobile {
    display: inline-flex;
    margin-bottom: 2.75rem;
  }

  .auth-context {
    padding: 2.25rem clamp(1.25rem, 7vw, 2.5rem);
  }

  .auth-context > .auth-brand {
    display: none;
  }

  .auth-context__title {
    font-size: 1.5rem;
  }
}
</style>
