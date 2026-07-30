<script setup lang="ts">
import { conflictHeading, type CoverLetterConflict } from '@/features/cover-letters/conflict'

defineProps<{ conflict: CoverLetterConflict; reapplying?: boolean }>()
defineEmits<{ reapply: []; cancel: [] }>()
</script>

<template>
  <section class="cover-conflict" role="alertdialog" aria-labelledby="cover-conflict-title">
    <div>
      <p class="page-eyebrow">409 버전 충돌</p>
      <h3 id="cover-conflict-title">{{ conflictHeading(conflict) }}</h3>
      <p>최신 서버 내용과 저장하지 않은 내용을 비교한 뒤 직접 선택해 주세요.</p>
    </div>
    <div class="cover-conflict__comparison">
      <article>
        <h4>최신 서버 내용</h4>
        <pre>{{ conflict.serverSnapshot }}</pre>
      </article>
      <article>
        <h4>내 미저장 내용</h4>
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
        {{ reapplying ? '재적용 중…' : '최신 버전에 재적용' }}
      </button>
      <button type="button" class="button button--secondary" @click="$emit('cancel')">취소</button>
    </div>
  </section>
</template>

<style scoped>
.cover-conflict {
  display: grid;
  gap: var(--space-4);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  padding: var(--space-5);
}

.cover-conflict h3 {
  margin-top: var(--space-1);
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
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-4);
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
