<script setup lang="ts">
import { conflictHeading, type CoverLetterConflict } from '@/features/cover-letters/conflict'

defineProps<{ conflict: CoverLetterConflict; reapplying?: boolean }>()
defineEmits<{ reapply: []; cancel: [] }>()
</script>

<template>
  <section class="cover-conflict" role="alertdialog" aria-labelledby="cover-conflict-title">
    <div>
      <p class="page-eyebrow">다른 곳에서 먼저 저장됐어요</p>
      <h3 id="cover-conflict-title">{{ conflictHeading(conflict) }}</h3>
      <p>지금 저장된 내용과 내가 쓰던 내용을 나란히 두었어요. 어느 쪽을 남길지 직접 골라 주세요.</p>
    </div>
    <div class="cover-conflict__comparison">
      <article>
        <h4>지금 저장된 내용</h4>
        <pre>{{ conflict.serverSnapshot }}</pre>
      </article>
      <article>
        <h4>내가 쓰던 내용</h4>
        <pre>{{ conflict.localDraft }}</pre>
      </article>
    </div>
    <div class="cover-conflict__actions">
      <button
        type="button"
        class="button button--primary"
        :disabled="reapplying"
        @click="$emit('reapply')"
      >
        {{ reapplying ? '저장하는 중…' : '내가 쓰던 내용으로 저장' }}
      </button>
      <button type="button" class="button button--secondary" @click="$emit('cancel')">
        저장된 내용 그대로 두기
      </button>
    </div>
  </section>
</template>

<style scoped>
.cover-conflict {
  display: grid;
  gap: var(--space-4);
  border: 0;
  border-radius: var(--radius-xl);
  box-shadow: inset 0 0 0 1px var(--color-warning-border);
  background: var(--color-warning-soft);
  padding: clamp(var(--space-4), 2.5vw, var(--space-6));
}

.cover-conflict h3 {
  margin-top: var(--space-1);
  font-size: var(--font-size-lg);
  font-weight: 780;
}

.cover-conflict p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.cover-conflict__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.cover-conflict__comparison article {
  min-width: 0;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-4);
}

.cover-conflict__comparison h4 {
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.cover-conflict__comparison pre {
  max-height: 12rem;
  margin-top: var(--space-2);
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  color: var(--color-text-secondary);
}

.cover-conflict__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 40rem) {
  .cover-conflict__comparison {
    grid-template-columns: 1fr;
  }

  .cover-conflict__actions .button {
    width: 100%;
  }
}
</style>
