<script setup lang="ts">
import { computed } from 'vue'

import type {
  CoverLetterAiModelDto,
  CoverLetterQuestionDto,
} from '@/shared/api/coverLetterContracts'

/*
 * AI 초안 설정. 기본 화면에 늘 펼쳐 두지 않고 초안 만들기·다시 쓰기를 누를 때만 연다.
 * 실행 전에 지금 답변이 어떻게 되는지, 설정이 어떤 문항에 적용되는지 먼저 알려 준다.
 */

const props = withDefaults(
  defineProps<{
    questions: readonly CoverLetterQuestionDto[]
    targetQuestionIds: ReadonlySet<string>
    models: readonly CoverLetterAiModelDto[]
    selectedModel: string
    modelsLoading?: boolean
    modelsError?: boolean
    avoidExperienceDuplication: boolean
    selectedEvidenceCount: number
    verifiedEvidenceCount: number
    editorDirty?: boolean
    dirtyQuestionOrder?: number
  }>(),
  { editorDirty: false, dirtyQuestionOrder: 0, modelsLoading: false, modelsError: false },
)
const emit = defineEmits<{
  'toggle-question': [questionId: string]
  'update:selectedModel': [value: string]
  'update:avoidExperienceDuplication': [value: boolean]
}>()

const rewriteTargets = computed(() =>
  props.questions.filter(
    (question) => props.targetQuestionIds.has(question.id) && question.currentAnswer !== null,
  ),
)
const evidenceSummary = computed(() => {
  if (props.verifiedEvidenceCount === 0) return '확인해 둔 경험이 아직 없어 공고 내용만 참고해요.'
  if (props.selectedEvidenceCount === 0) {
    return `확인해 둔 경험 ${props.verifiedEvidenceCount}개 중에서 알맞은 것을 골라 써요.`
  }
  return `작성 도움에서 고른 경험 ${props.selectedEvidenceCount}개를 먼저 참고해요.`
})
</script>

<template>
  <div class="generation-panel">
    <section v-if="editorDirty" class="generation-panel__warn" role="status">
      <strong>{{ dirtyQuestionOrder }}번 문항에 저장하지 않은 내용이 있어요.</strong>
      <p>
        먼저 답변을 저장하면 지금 쓰던 내용도 버전 기록에 남아요. 저장하지 않고 진행해도 쓰던 내용은
        이 브라우저에 남아 있다가 다시 불러올 수 있어요.
      </p>
    </section>

    <section class="generation-panel__block">
      <h3>초안을 받을 문항</h3>
      <ul class="generation-questions">
        <li v-for="question in questions" :key="`generate-${question.id}`">
          <label>
            <input
              type="checkbox"
              class="checkbox-control"
              :checked="targetQuestionIds.has(question.id)"
              @change="emit('toggle-question', question.id)"
            />
            <span>
              <strong>{{ question.questionOrder }}번</strong>
              {{ question.questionText }}
              <small v-if="question.currentAnswer">지금 답변 있음 · 새 버전으로 저장돼요</small>
              <small v-else>아직 답변 전</small>
            </span>
          </label>
        </li>
      </ul>
    </section>

    <section class="generation-panel__block">
      <h3>어떻게 써 드릴까요</h3>
      <label class="generation-panel__field">
        <span>AI 모델</span>
        <select
          :value="selectedModel"
          :disabled="modelsLoading || modelsError || models.length === 0"
          data-testid="cover-letter-model-select"
          @change="emit('update:selectedModel', ($event.target as HTMLSelectElement).value)"
        >
          <option v-if="modelsLoading" value="">모델 목록을 불러오는 중...</option>
          <option v-else-if="modelsError" value="">모델 목록을 불러오지 못했습니다</option>
          <option v-for="model in models" :key="model.id" :value="model.id">
            {{ model.displayName }} ({{ model.id }}){{ model.recommended ? ' · 추천' : '' }}
          </option>
        </select>
        <small v-if="models.find((model) => model.id === selectedModel)">
          {{ models.find((model) => model.id === selectedModel)?.description }}
        </small>
      </label>
      <label class="generation-panel__option">
        <input
          type="checkbox"
          class="checkbox-control"
          :checked="avoidExperienceDuplication"
          @change="
            emit('update:avoidExperienceDuplication', ($event.target as HTMLInputElement).checked)
          "
        />
        문항마다 다른 경험을 쓰기
      </label>
    </section>

    <section class="generation-panel__block">
      <h3>이렇게 진행돼요</h3>
      <ul class="generation-panel__notes">
        <li>{{ evidenceSummary }}</li>
        <li v-if="rewriteTargets.length">
          지금 답변은 지워지지 않아요. AI가 쓴 글이 새 버전으로 저장되고 화면에는 새 답변이 보여요.
          이전 답변은 버전 기록에서 언제든 되돌릴 수 있어요.
        </li>
        <li>초안을 쓰는 동안에는 답변 편집을 잠시 멈춰 둬요. 이 화면을 닫아도 계속 진행돼요.</li>
        <li>초안은 그대로 내지 말고 꼭 직접 다듬어 주세요.</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.generation-panel {
  display: grid;
  gap: var(--space-6);
}

.generation-panel__warn {
  display: grid;
  gap: var(--space-1);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.generation-panel__block {
  display: grid;
  gap: var(--space-3);
}

.generation-panel__block h3 {
  color: var(--color-ink-title);
  font-size: var(--font-size-sm);
  font-weight: 780;
}

.generation-questions {
  display: grid;
  gap: var(--space-1);
}

.generation-questions label,
.generation-panel__option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  font-size: var(--font-size-sm);
  line-height: 1.55;
}

.generation-questions label:hover,
.generation-panel__option:hover {
  background: var(--color-fill);
}

.generation-questions strong {
  margin-right: var(--space-1);
  font-weight: 750;
}

.generation-questions small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.generation-panel__field {
  display: grid;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.generation-panel__field select {
  width: 100%;
}

.generation-panel__field small {
  color: var(--color-text-muted);
}

.generation-panel__notes {
  display: grid;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  padding-left: var(--space-5);
  list-style: disc;
}
</style>
