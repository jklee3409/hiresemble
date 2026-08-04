<script lang="ts">
export const LANDING_DEMO_SCENE_DURATION_MS = 2400

export const LANDING_DEMO_SCENES = [
  {
    eyebrow: '경험 준비',
    title: '확인한 경험을 준비했어요',
    description: '등록한 이력서에서 정리한 경험을 직접 확인하고 다음 준비의 근거로 남겨요.',
    status: '확인 완료',
    icon: 'documents',
  },
  {
    eyebrow: '공고 등록',
    title: '관심 공고를 읽고 있어요',
    description: '등록한 URL이나 본문을 확인한 뒤 분석에 필요한 채용 정보를 정리해요.',
    status: '내용 확인 중',
    icon: 'jobs',
  },
  {
    eyebrow: '자동 분석 진행',
    title: '공고와 내 경험을 비교해요',
    description: '주요 업무와 지원 조건을 정리하고 확인한 경험에서 연결할 근거를 찾아요.',
    status: '분석 중',
    icon: 'runs',
  },
  {
    eyebrow: '분석 결과',
    title: '다음 준비의 방향을 확인해요',
    description: '잘 맞는 경험과 보완하면 좋은 부분을 나눠 보고 적합도의 근거를 살펴봐요.',
    status: '결과 확인',
    icon: 'profile',
  },
  {
    eyebrow: '다음 준비',
    title: '자기소개서와 면접 준비로 이어가요',
    description: '공고 분석과 확인한 경험을 바탕으로 다음에 준비할 내용을 선택해요.',
    status: '이어가기',
    icon: 'cover-letter',
  },
] as const
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const demoRoot = ref<HTMLElement | null>(null)
const activeSceneIndex = ref(0)
const isInViewport = ref(false)
const isDocumentVisible = ref(true)
const prefersReducedMotion = ref(false)
const isMounted = ref(false)

let sceneTimer: number | undefined
let observer: IntersectionObserver | undefined
let motionQuery: MediaQueryList | undefined

const activeScene = computed(() => LANDING_DEMO_SCENES[activeSceneIndex.value])
const shouldAutoPlay = computed(
  () =>
    isMounted.value && isInViewport.value && isDocumentVisible.value && !prefersReducedMotion.value,
)

function clearSceneTimer(): void {
  if (sceneTimer === undefined) return
  window.clearTimeout(sceneTimer)
  sceneTimer = undefined
}

function scheduleNextScene(): void {
  clearSceneTimer()
  if (!shouldAutoPlay.value) return

  sceneTimer = window.setTimeout(() => {
    activeSceneIndex.value = (activeSceneIndex.value + 1) % LANDING_DEMO_SCENES.length
    scheduleNextScene()
  }, LANDING_DEMO_SCENE_DURATION_MS)
}

function handleVisibilityChange(): void {
  isDocumentVisible.value = document.visibilityState === 'visible'
}

function handleMotionPreferenceChange(event: MediaQueryListEvent): void {
  prefersReducedMotion.value = event.matches
  if (event.matches) activeSceneIndex.value = 0
}

watch(shouldAutoPlay, (canPlay) => {
  if (canPlay) scheduleNextScene()
  else clearSceneTimer()
})

onMounted(() => {
  isMounted.value = true
  isDocumentVisible.value = document.visibilityState === 'visible'
  document.addEventListener('visibilitychange', handleVisibilityChange)

  motionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionQuery?.matches ?? false
  motionQuery?.addEventListener('change', handleMotionPreferenceChange)

  if (prefersReducedMotion.value) {
    isInViewport.value = true
    return
  }

  if (!('IntersectionObserver' in window)) {
    isInViewport.value = true
    return
  }

  try {
    observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        isInViewport.value = Boolean(entry?.isIntersecting && entry.intersectionRatio >= 0.4)
      },
      { threshold: [0, 0.4, 0.7] },
    )
    if (demoRoot.value) observer.observe(demoRoot.value)
  } catch {
    isInViewport.value = true
  }
})

onBeforeUnmount(() => {
  isMounted.value = false
  clearSceneTimer()
  observer?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  motionQuery?.removeEventListener('change', handleMotionPreferenceChange)
})
</script>

<template>
  <section
    ref="demoRoot"
    class="landing-demo"
    aria-labelledby="landing-demo-title"
    aria-describedby="landing-demo-description"
    :data-scene-index="activeSceneIndex"
    :data-playback-state="shouldAutoPlay ? 'playing' : 'paused'"
  >
    <h2 id="landing-demo-title" class="sr-only">Hiresemble 지원 준비 자동 데모</h2>
    <p id="landing-demo-description" class="sr-only">
      이력서에서 경험을 확인하고, 관심 공고를 등록해 자동 분석한 뒤, 결과를 바탕으로 자기소개서와
      면접 질문 준비까지 이어가는 흐름입니다.
    </p>

    <div class="landing-demo__chrome" aria-hidden="true">
      <div class="landing-demo__topbar">
        <span class="landing-demo__brand"><BrandMark compact :show-name="false" />Hiresemble</span>
        <span class="landing-demo__location">지원 준비</span>
        <span class="landing-demo__window"><i /><i /><i /></span>
      </div>

      <div class="landing-demo__canvas">
        <div class="landing-demo__glow landing-demo__glow--one" />
        <div class="landing-demo__glow landing-demo__glow--two" />

        <Transition name="demo-scene" mode="out-in">
          <article :key="activeSceneIndex" class="landing-demo__scene">
            <div class="landing-demo__scene-heading">
              <span class="landing-demo__scene-icon"><AppIcon :name="activeScene.icon" /></span>
              <div>
                <p>{{ activeScene.eyebrow }}</p>
                <h3>{{ activeScene.title }}</h3>
              </div>
              <StatusBadge :label="activeScene.status" tone="brand" />
            </div>
            <p class="landing-demo__scene-description">{{ activeScene.description }}</p>

            <div v-if="activeSceneIndex === 0" class="demo-experience">
              <div class="demo-document">
                <AppIcon name="documents" />
                <span><small>등록한 자료</small><strong>지원용 이력서</strong></span>
                <StatusBadge label="등록 완료" tone="success" />
              </div>
              <div class="demo-experience__items">
                <span><i />프로젝트 문제 해결</span>
                <span><i />협업과 역할</span>
                <span><i />기술 경험</span>
              </div>
            </div>

            <div v-else-if="activeSceneIndex === 1" class="demo-browser">
              <div class="demo-browser__bar"><i /><i /><i /><span>채용 공고 URL</span></div>
              <div class="demo-browser__content">
                <span class="demo-browser__logo"><AppIcon name="jobs" /></span>
                <div><small>관심 공고</small><strong>백엔드 개발자</strong></div>
                <span class="demo-browser__reading"><i />공고 내용을 읽고 있어요</span>
              </div>
            </div>

            <div v-else-if="activeSceneIndex === 2" class="demo-analysis">
              <div class="demo-analysis__progress"><i /></div>
              <ol>
                <li class="is-complete"><span>1</span><b>공고 본문 확인</b><em>완료</em></li>
                <li class="is-active">
                  <span>2</span><b>주요 업무와 지원 조건 정리</b><em>진행 중</em>
                </li>
                <li><span>3</span><b>내 경험과 비교</b><em>다음 단계</em></li>
              </ol>
            </div>

            <div v-else-if="activeSceneIndex === 3" class="demo-result">
              <div>
                <span class="demo-result__icon is-match"><AppIcon name="check" /></span>
                <small>잘 맞는 경험</small>
                <strong>문제를 나눠 해결한 프로젝트 경험</strong>
              </div>
              <div>
                <span class="demo-result__icon"><AppIcon name="profile" /></span>
                <small>보완하면 좋은 부분</small>
                <strong>협업 과정에서 맡은 역할을 더 구체적으로</strong>
              </div>
              <p>적합도는 합격 확률이 아닌 준비 방향 안내예요.</p>
            </div>

            <div v-else class="demo-next">
              <div>
                <span><AppIcon name="cover-letter" /></span>
                <small>자기소개서</small>
                <strong>공고에 맞춘 준비 가능</strong>
              </div>
              <div>
                <span><AppIcon name="interview" /></span>
                <small>면접 준비</small>
                <strong>예상 질문 준비로 이어가기</strong>
              </div>
            </div>
          </article>
        </Transition>
      </div>
    </div>

    <ol class="landing-demo__progress" aria-hidden="true">
      <li
        v-for="(_, index) in LANDING_DEMO_SCENES"
        :key="index"
        :class="{ 'is-active': index === activeSceneIndex }"
      >
        <span>{{ index + 1 }}</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.landing-demo {
  min-width: 0;
}

.landing-demo__chrome {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--hs-blue-200);
  border-radius: clamp(1rem, 2vw, var(--radius-lg));
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 30px 80px rgb(32 57 189 / 16%);
  transform: perspective(70rem) rotateX(0.8deg) rotateY(-1deg);
  transform-origin: center;
  transition:
    box-shadow 300ms ease,
    transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}

.landing-demo__chrome:hover {
  box-shadow: 0 38px 90px rgb(32 57 189 / 21%);
  transform: perspective(70rem) translateY(-4px) rotateX(0) rotateY(0);
}

.landing-demo__topbar {
  display: grid;
  min-height: 3.25rem;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  border-bottom: 1px solid rgb(193 204 242 / 72%);
  padding-inline: var(--space-4);
  color: var(--color-muted-strong);
  font-size: var(--font-size-xs);
  font-weight: 720;
}

.landing-demo__brand {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-ink-soft);
}

.landing-demo__location {
  border-radius: 999px;
  background: var(--hs-blue-50);
  padding: 0.3rem 0.65rem;
  color: var(--color-brand-strong);
}

.landing-demo__window {
  display: flex;
  justify-content: flex-end;
  gap: 0.3rem;
}

.landing-demo__window i,
.demo-browser__bar > i {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: var(--hs-blue-200);
}

.landing-demo__canvas {
  position: relative;
  display: grid;
  min-height: clamp(25rem, 37vw, 30rem);
  place-items: center;
  overflow: hidden;
  background:
    linear-gradient(rgb(255 255 255 / 48%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 48%) 1px, transparent 1px),
    linear-gradient(145deg, #f4f6ff 0%, #edf5ff 52%, #f8f2ff 100%);
  background-size:
    3rem 3rem,
    3rem 3rem,
    auto;
  padding: clamp(var(--space-5), 4vw, var(--space-7));
}

.landing-demo__glow {
  position: absolute;
  width: 16rem;
  height: 16rem;
  border-radius: 50%;
  filter: blur(34px);
  opacity: 0.36;
  pointer-events: none;
}

.landing-demo__glow--one {
  top: -6rem;
  right: -4rem;
  background: #bcc8ff;
  animation: demo-glow-drift-one 7s ease-in-out infinite;
}

.landing-demo__glow--two {
  bottom: -8rem;
  left: -2rem;
  background: #c5e7ff;
  animation: demo-glow-drift-two 8s ease-in-out infinite;
}

@keyframes demo-glow-drift-one {
  50% {
    opacity: 0.52;
    transform: translate(-1rem, 1.5rem) scale(1.08);
  }
}

@keyframes demo-glow-drift-two {
  50% {
    opacity: 0.5;
    transform: translate(1.5rem, -1rem) scale(1.1);
  }
}

.landing-demo__scene {
  position: relative;
  width: min(100%, 31rem);
  border: 1px solid rgb(184 198 239 / 82%);
  border-radius: var(--radius-lg);
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 20px 50px rgb(64 83 166 / 15%);
  padding: clamp(var(--space-5), 4vw, var(--space-7));
}

.landing-demo__scene-heading {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-3);
}

.landing-demo__scene-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand);
  color: white;
}

.landing-demo__scene-icon .icon {
  width: 1.15rem;
}

.landing-demo__scene-heading p,
.landing-demo__scene-heading h3,
.landing-demo__scene-description {
  margin: 0;
}

.landing-demo__scene-heading p {
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 760;
}

.landing-demo__scene-heading h3 {
  margin-top: 0.15rem;
  font-size: clamp(1rem, 2vw, 1.2rem);
  line-height: 1.4;
  word-break: keep-all;
}

.landing-demo__scene-description {
  margin-top: var(--space-4);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  line-height: 1.65;
  word-break: keep-all;
}

.demo-document,
.demo-browser,
.demo-analysis,
.demo-result,
.demo-next {
  margin-top: var(--space-5);
}

.demo-document {
  display: grid;
  grid-template-columns: 2.25rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.demo-document > .icon {
  width: 1.25rem;
  color: var(--color-brand);
}

.demo-document small,
.demo-document strong {
  display: block;
}

.demo-document small,
.demo-result small,
.demo-next small {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.demo-document strong,
.demo-result strong,
.demo-next strong {
  margin-top: 0.15rem;
  font-size: var(--font-size-sm);
}

.demo-experience__items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.demo-experience__items span {
  min-width: 0;
  border-radius: var(--radius-sm);
  background: var(--hs-blue-50);
  padding: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--font-size-xs);
  font-weight: 680;
}

.demo-experience__items i {
  display: block;
  width: 1.25rem;
  height: 0.25rem;
  margin-bottom: var(--space-2);
  border-radius: 999px;
  background: var(--hs-blue-400);
}

.demo-browser {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
}

.demo-browser__bar {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-canvas);
  padding: var(--space-2) var(--space-3);
}

.demo-browser__bar span {
  min-width: 0;
  flex: 1;
  margin-left: var(--space-2);
  overflow: hidden;
  border-radius: 999px;
  background: white;
  color: var(--color-muted);
  padding: 0.3rem var(--space-3);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.demo-browser__content {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-5);
}

.demo-browser__logo {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--hs-blue-50);
  color: var(--color-brand);
}

.demo-browser__content small,
.demo-browser__content strong {
  display: block;
}

.demo-browser__content small {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.demo-browser__reading {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 720;
}

.demo-browser__reading i {
  display: block;
  width: 42%;
  height: 0.35rem;
  overflow: hidden;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-brand) 70%, var(--hs-blue-100) 70%);
}

.demo-analysis__progress {
  height: 0.35rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--hs-blue-100);
}

.demo-analysis__progress i {
  display: block;
  width: 66%;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
}

.demo-analysis ol {
  display: grid;
  gap: var(--space-2);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.demo-analysis li {
  display: grid;
  grid-template-columns: 1.75rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
}

.demo-analysis li > span {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-neutral-soft);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  font-weight: 760;
}

.demo-analysis li b {
  font-size: var(--font-size-xs);
}

.demo-analysis li em {
  color: var(--color-muted);
  font-size: 0.68rem;
  font-style: normal;
}

.demo-analysis li.is-complete,
.demo-analysis li.is-active {
  border-color: var(--hs-blue-200);
  background: var(--hs-blue-50);
}

.demo-analysis li.is-complete > span,
.demo-analysis li.is-active > span {
  background: var(--color-brand);
  color: white;
}

.demo-analysis li.is-active em {
  color: var(--color-brand-strong);
  font-weight: 720;
}

.demo-result {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.demo-result > div {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-4);
}

.demo-result__icon {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  margin-bottom: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-neutral-soft);
  color: var(--color-muted-strong);
}

.demo-result__icon.is-match {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.demo-result__icon .icon {
  width: 1rem;
}

.demo-result strong {
  display: block;
  line-height: 1.5;
  word-break: keep-all;
}

.demo-result > p {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  text-align: center;
}

.demo-next {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.demo-next > div {
  border: 1px solid var(--hs-blue-200);
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, white, var(--hs-blue-50));
  padding: var(--space-4);
}

.demo-next > div > span {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  margin-bottom: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-brand);
  color: white;
}

.demo-next .icon {
  width: 1.1rem;
}

.demo-next small,
.demo-next strong {
  display: block;
}

.demo-next strong {
  line-height: 1.5;
  word-break: keep-all;
}

.landing-demo__progress {
  display: flex;
  width: min(42%, 12rem);
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.landing-demo__progress li {
  flex: 1;
}

.landing-demo__progress li span {
  position: relative;
  display: block;
  height: 0.25rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--hs-blue-100);
  color: transparent;
}

.landing-demo__progress li.is-active span {
  background: var(--hs-blue-100);
}

.landing-demo__progress li.is-active span::after {
  position: absolute;
  inset: 0;
  background: var(--color-brand);
  content: '';
  transform-origin: left;
  animation: demo-progress-fill 2400ms linear both;
}

@keyframes demo-progress-fill {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

.demo-scene-enter-active {
  transition:
    opacity 620ms ease-out,
    filter 620ms ease-out,
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.demo-scene-leave-active {
  transition:
    opacity 300ms ease-in,
    filter 300ms ease-in,
    transform 300ms ease-in;
}

.demo-scene-enter-from {
  opacity: 0;
  filter: blur(6px);
  transform: translateY(8px) scale(0.985);
}

.demo-scene-leave-to {
  opacity: 0;
  filter: blur(5px);
  transform: translateY(-3px) scale(0.99);
}

@media (max-width: 40rem) {
  .landing-demo__chrome,
  .landing-demo__chrome:hover {
    transform: none;
  }

  .landing-demo__canvas {
    min-height: 28rem;
    padding: var(--space-4);
  }

  .landing-demo__scene {
    padding: var(--space-5);
  }

  .landing-demo__scene-heading {
    grid-template-columns: 2.25rem minmax(0, 1fr);
  }

  .landing-demo__scene-heading > .status-badge {
    grid-column: 2;
    justify-self: start;
  }

  .landing-demo__scene-icon {
    width: 2.25rem;
    height: 2.25rem;
  }

  .demo-experience__items,
  .demo-result,
  .demo-next {
    grid-template-columns: minmax(0, 1fr);
  }

  .demo-result > p {
    grid-column: 1;
  }

  .demo-analysis li {
    grid-template-columns: 1.75rem minmax(0, 1fr);
  }

  .demo-analysis li em {
    grid-column: 2;
  }

  .demo-scene-enter-from {
    filter: blur(3px);
    transform: translateY(4px) scale(0.992);
  }

  .demo-scene-leave-to {
    filter: blur(3px);
    transform: scale(0.995);
  }
}

@media (max-width: 22rem) {
  .landing-demo__topbar {
    grid-template-columns: 1fr auto;
  }

  .landing-demo__location {
    display: none;
  }

  .landing-demo__canvas {
    min-height: 30rem;
    padding: var(--space-3);
  }

  .landing-demo__scene {
    padding: var(--space-4);
  }

  .demo-document {
    grid-template-columns: 2rem minmax(0, 1fr);
  }

  .demo-document > .status-badge {
    grid-column: 2;
    justify-self: start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .demo-scene-enter-active,
  .demo-scene-leave-active {
    transition: none;
  }

  .landing-demo__glow {
    filter: blur(20px);
    animation: none;
  }

  .landing-demo__chrome,
  .landing-demo__chrome:hover {
    transform: none;
    transition: none;
  }

  .landing-demo__progress li.is-active span::after {
    animation: none;
  }
}
</style>
