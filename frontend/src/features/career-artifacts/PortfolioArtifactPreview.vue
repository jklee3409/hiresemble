<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from 'vue'

import type { PortfolioArtifactPreviewDto } from '@/shared/api/careerArtifactContracts'

import { PORTFOLIO_SLIDE_LABELS, PORTFOLIO_VISUAL_LABELS } from './presentation'

const props = defineProps<{ preview: PortfolioArtifactPreviewDto }>()
const selectedIndex = ref(0)
const tabs = ref<HTMLButtonElement[]>([])

watch(
  () => props.preview.slides.length,
  (length) => {
    if (selectedIndex.value >= length) selectedIndex.value = 0
  },
)

const selectedSlide = computed(() => props.preview.slides[selectedIndex.value] ?? null)

async function selectSlide(index: number, focus = false): Promise<void> {
  selectedIndex.value = index
  if (focus) {
    await nextTick()
    tabs.value[index]?.focus()
  }
}

function onTabKeydown(event: KeyboardEvent, index: number): void {
  let next: number
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown')
    next = (index + 1) % props.preview.slides.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    next = (index - 1 + props.preview.slides.length) % props.preview.slides.length
  } else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = props.preview.slides.length - 1
  else return
  event.preventDefault()
  void selectSlide(next, true)
}

function setTabRef(element: Element | ComponentPublicInstance | null, index: number): void {
  if (element instanceof HTMLButtonElement) tabs.value[index] = element
}
</script>

<template>
  <section class="portfolio-preview" aria-label="현재 포트폴리오 미리보기">
    <header>
      <p class="section-kicker">구조화 미리보기</p>
      <h2>포트폴리오 슬라이드 {{ preview.slides.length }}장</h2>
    </header>

    <div class="portfolio-preview__tabs" role="tablist" aria-label="슬라이드 선택">
      <button
        v-for="(slide, index) in preview.slides"
        :id="`portfolio-slide-tab-${slide.slideNo}`"
        :key="slide.slideNo"
        :ref="(element) => setTabRef(element, index)"
        type="button"
        role="tab"
        :tabindex="selectedIndex === index ? 0 : -1"
        :aria-selected="selectedIndex === index"
        :aria-controls="`portfolio-slide-panel-${slide.slideNo}`"
        @click="selectSlide(index)"
        @keydown="onTabKeydown($event, index)"
      >
        <span>{{ slide.slideNo }}</span>
        <strong>{{ PORTFOLIO_SLIDE_LABELS[slide.slideType] }}</strong>
        <small>{{ slide.title }}</small>
      </button>
    </div>

    <article
      v-if="selectedSlide"
      :id="`portfolio-slide-panel-${selectedSlide.slideNo}`"
      class="portfolio-preview__detail"
      role="tabpanel"
      :aria-labelledby="`portfolio-slide-tab-${selectedSlide.slideNo}`"
    >
      <p>{{ selectedSlide.slideNo }} · {{ PORTFOLIO_SLIDE_LABELS[selectedSlide.slideType] }}</p>
      <h3>{{ selectedSlide.title }}</h3>
      <p v-if="selectedSlide.subtitle">{{ selectedSlide.subtitle }}</p>
      <dl>
        <div>
          <dt>구성 방식</dt>
          <dd>{{ PORTFOLIO_VISUAL_LABELS[selectedSlide.visualType] }}</dd>
        </div>
      </dl>
      <ul v-if="selectedSlide.items.length > 0">
        <li v-for="item in selectedSlide.items" :key="item">{{ item }}</li>
      </ul>
      <details v-if="selectedSlide.evidenceRefs.length > 0">
        <summary>근거 {{ selectedSlide.evidenceRefs.length }}개</summary>
        <ul>
          <li v-for="evidence in selectedSlide.evidenceRefs" :key="evidence.evidenceId">
            {{ evidence.title }}
          </li>
        </ul>
      </details>
    </article>

    <aside v-if="preview.warnings.length > 0" class="alert alert--warning" aria-label="검토 안내">
      <strong>직접 확인해 주세요</strong>
      <ul>
        <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
      </ul>
    </aside>
  </section>
</template>

<style scoped>
.portfolio-preview {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
}

.portfolio-preview h2,
.portfolio-preview h3,
.portfolio-preview p {
  margin: 0;
}

.portfolio-preview__tabs {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: var(--space-3);
}

.portfolio-preview__tabs button {
  display: grid;
  min-height: 8rem;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-ink);
  background: var(--color-surface);
  text-align: left;
}

.portfolio-preview__tabs button[aria-selected='true'] {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 18%, transparent);
}

.portfolio-preview__tabs span,
.portfolio-preview__tabs small,
.portfolio-preview__detail > p {
  color: var(--color-muted);
}

.portfolio-preview__detail {
  min-height: 19rem;
  padding: clamp(1.25rem, 4vw, 2.5rem);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.portfolio-preview__detail h3 {
  margin-top: var(--space-2);
  font-size: clamp(1.35rem, 4vw, 2rem);
}

.portfolio-preview__detail dl,
.portfolio-preview__detail ul,
.portfolio-preview__detail details {
  margin-top: var(--space-4);
}

@media (max-width: 32rem) {
  .portfolio-preview__tabs {
    grid-template-columns: 1fr;
  }

  .portfolio-preview__tabs button {
    min-height: 4.5rem;
  }
}
</style>
