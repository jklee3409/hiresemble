<script setup lang="ts">
import { nextTick } from 'vue'

import { questionWorkStatus } from '@/features/cover-letters/editorFlow'
import type { CoverLetterQuestionDto } from '@/shared/api/coverLetterContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 문항 목록. 작업 화면 좌측에서는 번호만 남긴 좁은 strip으로 두고
 * 좁은 화면 sheet에서는 질문 preview까지 함께 보여 준다.
 * 상태는 색 점과 접근 가능한 이름으로 함께 전달한다.
 */

const props = withDefaults(
  defineProps<{
    questions: readonly CoverLetterQuestionDto[]
    selectedQuestionId: string
    dirtyQuestionId?: string
    canAdd?: boolean
    /* 같은 화면에 목록이 둘 이상 있을 수 있어 tab id를 분리한다. */
    idPrefix?: string
    variant?: 'compact' | 'list'
  }>(),
  { dirtyQuestionId: '', canAdd: false, idPrefix: 'question-tab', variant: 'compact' },
)
const emit = defineEmits<{ select: [questionId: string]; add: [] }>()

const PREVIEW_LENGTH = 34

function preview(question: CoverLetterQuestionDto): string {
  const text = question.questionText.replace(/\s+/g, ' ').trim()
  const characters = Array.from(text)
  if (characters.length <= PREVIEW_LENGTH) return text
  return `${characters.slice(0, PREVIEW_LENGTH).join('')}…`
}

function status(question: CoverLetterQuestionDto) {
  return questionWorkStatus(question, { dirty: question.id === props.dirtyQuestionId })
}

function label(question: CoverLetterQuestionDto): string {
  return `${question.questionOrder}번 문항: ${question.questionText} · ${status(question).label}`
}

function onKeydown(event: KeyboardEvent, index: number): void {
  const last = props.questions.length - 1
  const target = { ArrowDown: index + 1, ArrowUp: index - 1, Home: 0, End: last }[event.key]
  if (target === undefined) return
  event.preventDefault()
  const question = props.questions[Math.min(Math.max(target, 0), last)]
  if (!question) return
  emit('select', question.id)
  void nextTick(() => {
    document.getElementById(`${props.idPrefix}-${question.id}`)?.focus()
  })
}
</script>

<template>
  <div class="question-rail" :data-variant="variant">
    <p v-if="variant === 'list'" class="question-rail__title">문항 {{ questions.length }}개</p>

    <p v-if="questions.length === 0 && variant === 'list'" class="question-rail__empty">
      아직 등록한 문항이 없어요.
    </p>
    <div
      v-if="questions.length > 0"
      class="question-rail__list"
      role="tablist"
      aria-label="자기소개서 문항"
      aria-orientation="vertical"
    >
      <button
        v-for="(question, index) in questions"
        :id="`${idPrefix}-${question.id}`"
        :key="question.id"
        type="button"
        role="tab"
        class="question-rail__item"
        :class="{ 'question-rail__item--active': selectedQuestionId === question.id }"
        :aria-label="label(question)"
        :title="variant === 'compact' ? label(question) : undefined"
        :aria-selected="selectedQuestionId === question.id"
        :aria-controls="`question-panel-${question.id}`"
        :tabindex="selectedQuestionId === question.id ? 0 : -1"
        @click="emit('select', question.id)"
        @keydown="onKeydown($event, index)"
      >
        <span class="question-rail__order">{{ question.questionOrder }}</span>
        <span v-if="variant === 'list'" class="question-rail__body">
          <span class="question-rail__text">{{ preview(question) }}</span>
          <span class="question-rail__state">{{ status(question).label }}</span>
        </span>
        <span class="question-rail__dot" :data-tone="status(question).tone" aria-hidden="true" />
      </button>
    </div>

    <button
      v-if="canAdd"
      type="button"
      class="question-rail__add"
      :aria-label="variant === 'compact' ? '문항 추가' : undefined"
      data-testid="add-question"
      @click="emit('add')"
    >
      <AppIcon name="plus" />
      <span v-if="variant === 'list'">문항 추가</span>
    </button>
  </div>
</template>

<style scoped>
.question-rail {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
}

.question-rail__title {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 750;
  letter-spacing: 0.02em;
}

.question-rail__empty {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.question-rail__list {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.question-rail__item {
  position: relative;
  display: grid;
  align-items: center;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-fill);
  color: var(--color-text-secondary);
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
}

.question-rail__item:hover {
  background: var(--color-fill-strong);
}

.question-rail__item--active {
  background: var(--color-brand);
  color: #ffffff;
}

.question-rail__order {
  font-variant-numeric: tabular-nums;
  font-weight: 780;
}

.question-rail__dot {
  position: absolute;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: var(--radius-pill);
  background: var(--color-border-strong);
}

.question-rail__dot[data-tone='success'] {
  background: var(--color-success);
}

.question-rail__dot[data-tone='warning'] {
  background: var(--color-warning);
}

.question-rail__dot[data-tone='danger'] {
  background: var(--color-danger);
}

.question-rail__dot[data-tone='brand'],
.question-rail__dot[data-tone='info'] {
  background: var(--color-brand);
}

.question-rail__add {
  display: grid;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-fill);
  color: var(--color-text-muted);
  place-items: center;
}

.question-rail__add:hover {
  background: var(--color-fill-strong);
  color: var(--color-ink-title);
}

.question-rail__add :deep(.icon) {
  width: 1.1rem;
  height: 1.1rem;
}

/* 번호만 남긴 좁은 strip */
.question-rail[data-variant='compact'] .question-rail__item,
.question-rail[data-variant='compact'] .question-rail__add {
  width: 2.75rem;
  height: 2.75rem;
  justify-items: center;
}

.question-rail[data-variant='compact'] .question-rail__order {
  font-size: var(--font-size-sm);
}

.question-rail[data-variant='compact'] .question-rail__dot {
  top: 0.375rem;
  right: 0.375rem;
}

.question-rail[data-variant='compact'] .question-rail__item--active .question-rail__dot {
  box-shadow: 0 0 0 2px var(--color-brand);
}

/* 질문 preview까지 보여 주는 목록 */
.question-rail[data-variant='list'] .question-rail__item {
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  background: transparent;
  padding: var(--space-3) var(--space-5) var(--space-3) var(--space-3);
  text-align: left;
}

.question-rail[data-variant='list'] .question-rail__item:hover {
  background: var(--color-fill);
}

.question-rail[data-variant='list'] .question-rail__item--active {
  background: var(--color-fill);
  color: var(--color-ink-title);
  box-shadow: inset 2px 0 0 var(--color-brand);
}

.question-rail[data-variant='list'] .question-rail__order {
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: var(--radius-pill);
  background: var(--color-fill-strong);
  font-size: var(--font-size-xs);
}

.question-rail[data-variant='list'] .question-rail__item--active .question-rail__order {
  background: var(--color-brand);
  color: #ffffff;
}

.question-rail[data-variant='list'] .question-rail__body {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.question-rail[data-variant='list'] .question-rail__text {
  font-size: var(--font-size-sm);
  font-weight: 650;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.question-rail[data-variant='list'] .question-rail__state {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.question-rail[data-variant='list'] .question-rail__dot {
  top: 1.1rem;
  right: var(--space-3);
}

.question-rail[data-variant='list'] .question-rail__add {
  grid-template-columns: auto auto;
  gap: var(--space-2);
  justify-content: start;
  min-height: 2.75rem;
  background: transparent;
  padding: 0 var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: 700;
}
</style>
