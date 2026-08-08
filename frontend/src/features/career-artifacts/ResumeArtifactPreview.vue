<script setup lang="ts">
import type { ResumeArtifactPreviewDto } from '@/shared/api/careerArtifactContracts'

defineProps<{ preview: ResumeArtifactPreviewDto }>()
</script>

<template>
  <article class="resume-preview" aria-label="현재 이력서 미리보기">
    <header>
      <p class="section-kicker">구조화 미리보기</p>
      <h2 v-if="preview.headline">{{ preview.headline }}</h2>
      <p v-if="preview.summary" class="resume-preview__summary">{{ preview.summary }}</p>
    </header>

    <section v-for="section in preview.sections" :key="`${section.type}:${section.title}`">
      <h3>{{ section.title }}</h3>
      <article v-for="(item, index) in section.items" :key="index" class="resume-preview__item">
        <h4 v-if="item.heading">{{ item.heading }}</h4>
        <p v-if="item.subheading || item.period" class="resume-preview__meta">
          <span v-if="item.subheading">{{ item.subheading }}</span>
          <span v-if="item.period">{{ item.period }}</span>
        </p>
        <ul v-if="item.bullets.length > 0">
          <li v-for="bullet in item.bullets" :key="bullet">{{ bullet }}</li>
        </ul>
        <details v-if="item.evidenceRefs.length > 0">
          <summary>근거 {{ item.evidenceRefs.length }}개</summary>
          <ul>
            <li v-for="evidence in item.evidenceRefs" :key="evidence.evidenceId">
              {{ evidence.title }}
            </li>
          </ul>
        </details>
      </article>
    </section>

    <aside v-if="preview.warnings.length > 0" class="alert alert--warning" aria-label="검토 안내">
      <strong>직접 확인해 주세요</strong>
      <ul>
        <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
      </ul>
    </aside>
  </article>
</template>

<style scoped>
.resume-preview {
  display: grid;
  max-width: 54rem;
  gap: var(--space-6);
  padding: clamp(1.25rem, 4vw, 3rem);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-ink);
}

.resume-preview h2,
.resume-preview h3,
.resume-preview h4,
.resume-preview p {
  margin: 0;
}

.resume-preview h3 {
  padding-bottom: var(--space-2);
  border-bottom: 2px solid var(--color-ink);
  font-size: 1rem;
}

.resume-preview__summary {
  margin-top: var(--space-3) !important;
  line-height: 1.7;
}

.resume-preview__item {
  margin-top: var(--space-4);
}

.resume-preview__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-1) !important;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.resume-preview li {
  line-height: 1.65;
}

.resume-preview details {
  margin-top: var(--space-2);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}
</style>
