<script setup lang="ts">
import type { CompletionItem, WarningAcknowledgement } from '@/features/cover-letters/editorFlow'

/*
 * 작성 완료 점검. 페이지 최하단이 아니라 상단 상태 영역에서 바로 연다.
 * 부족한 조건마다 해당 문항으로 이동할 수 있게 해 무엇을 해야 하는지 바로 알 수 있게 한다.
 */

const ACTION_LABELS: Record<CompletionItem['action'], string> = {
  WRITE: '답변 쓰기',
  SAVE: '저장하러 가기',
  SHORTEN: '줄이러 가기',
  REVIEW: '검토하러 가기',
  WAIT: '검토 상태 보기',
  ACKNOWLEDGE: '확인 사항 보기',
}

withDefaults(
  defineProps<{
    items: readonly CompletionItem[]
    warnings: readonly WarningAcknowledgement[]
    acknowledged: ReadonlySet<string>
    finalized?: boolean
    readOnly?: boolean
    canFinalize?: boolean
    finalizePending?: boolean
  }>(),
  {
    finalized: false,
    readOnly: false,
    canFinalize: false,
    finalizePending: false,
  },
)
const emit = defineEmits<{
  'focus-question': [questionId: string]
  acknowledge: [verificationId: string]
}>()
</script>

<template>
  <div class="completion">
    <p v-if="finalized" class="completion__lead" role="status">
      작성을 마친 자기소개서예요. 문항이나 답변을 고치면 다시 작성 중으로 돌아가고, 고친 답변은 한
      번 더 검토받아야 해요.
    </p>
    <p v-else-if="readOnly" class="completion__lead">
      보관된 자기소개서는 작성 완료로 표시할 수 없어요.
    </p>
    <template v-else>
      <p class="completion__lead">
        모든 문항의 답변을 저장하고 AI 검토까지 마쳐야 작성 완료로 표시할 수 있어요. 확인 필요가
        나온 문항은 내용을 읽고 확인 표시를 해 주세요.
      </p>

      <p v-if="items.length === 0" class="completion__ready" role="status">
        확인할 것이 남아 있지 않아요. 이대로 작성 완료로 표시할 수 있어요.
      </p>
      <ul v-else class="completion__list" data-testid="completion-blockers">
        <li v-for="item in items" :key="`${item.questionId}-${item.action}`">
          <span>{{ item.message }}</span>
          <button
            type="button"
            class="button button--ghost button--compact"
            @click="emit('focus-question', item.questionId)"
          >
            {{ ACTION_LABELS[item.action] }}
          </button>
        </li>
      </ul>

      <fieldset v-if="warnings.length" class="completion__warnings finalization__warnings">
        <legend>확인 필요 항목을 읽었는지 표시해 주세요</legend>
        <label v-for="warning in warnings" :key="warning.verificationId">
          <input
            type="checkbox"
            class="checkbox-control"
            :checked="acknowledged.has(warning.verificationId)"
            @change="emit('acknowledge', warning.verificationId)"
          />
          {{ warning.questionOrder }}번 문항의 확인 사항을 읽었어요.
        </label>
      </fieldset>

      <p class="completion__note">
        작성 완료로 표시해도 공고의 지원 상태는 바뀌지 않아요. 공고 화면에서 따로 바꿔 주세요.
      </p>
    </template>
  </div>
</template>

<style scoped>
.completion {
  display: grid;
  gap: var(--space-4);
}

.completion__lead,
.completion__note {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.completion__ready {
  border-radius: var(--radius-md);
  background: var(--color-success-soft);
  color: var(--color-success-strong);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
}

.completion__list {
  display: grid;
  gap: var(--space-1);
}

.completion__list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-2) var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
}

.completion__warnings {
  display: grid;
  gap: var(--space-1);
}

.completion__warnings legend {
  color: var(--color-ink-title);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.completion__warnings label {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}
</style>
