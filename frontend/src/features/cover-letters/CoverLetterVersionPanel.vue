<script setup lang="ts">
import {
  ANSWER_SOURCE_LABELS,
  VERIFICATION_STATUS_LABELS,
  VERIFICATION_STATUS_TONES,
  evidenceCurrentState,
  formatCoverLetterInstant,
} from '@/features/cover-letters/presentation'
import type {
  CoverLetterAnswerVersionDto,
  VerificationDto,
} from '@/shared/api/coverLetterContracts'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

/*
 * 버전 기록. 기본 작성 화면을 차지하지 않고 필요할 때만 연다.
 * 편집 중인 내용, 지금 답변, 과거 저장본을 각각 다른 개념으로 구분해 보여 준다.
 */

withDefaults(
  defineProps<{
    versions: readonly CoverLetterAnswerVersionDto[]
    selectedVersionId: string
    currentAnswer: CoverLetterAnswerVersionDto | null
    loading?: boolean
    editorDirty?: boolean
    readOnly?: boolean
    restorePending?: boolean
    selectedVersion: CoverLetterAnswerVersionDto | null
    /* 고른 저장본이 그때 어떤 검토를 받았는지 함께 보여 준다. */
    verifications?: readonly VerificationDto[]
  }>(),
  {
    loading: false,
    editorDirty: false,
    readOnly: false,
    restorePending: false,
    verifications: () => [],
  },
)
const emit = defineEmits<{ select: [versionId: string]; restore: [] }>()

function tone(status: VerificationDto['status']) {
  return VERIFICATION_STATUS_TONES[status]
}
</script>

<template>
  <div class="version-panel">
    <p class="version-panel__lead">
      저장한 답변은 지워지지 않아요. 되돌리기를 눌러도 과거 답변을 고치는 것이 아니라 그 내용으로 새
      답변이 하나 더 저장돼요.
    </p>

    <p v-if="editorDirty" class="version-panel__draft" role="status">
      지금 편집기에 있는 내용은 아직 저장 전이라 기록에 없어요. 남기려면 먼저 답변을 저장해 주세요.
    </p>

    <p v-if="loading" class="version-panel__empty">저장 기록을 불러오는 중이에요…</p>
    <p v-else-if="versions.length === 0" class="version-panel__empty">아직 저장한 답변이 없어요.</p>
    <div v-else class="version-panel__layout">
      <div class="version-panel__list" role="listbox" aria-label="답변 버전">
        <button
          v-for="version in versions"
          :key="version.id"
          type="button"
          role="option"
          :aria-selected="selectedVersionId === version.id"
          :class="{ 'version-panel__item--active': selectedVersionId === version.id }"
          @click="emit('select', version.id)"
        >
          <span class="version-panel__label">
            <strong>v{{ version.versionNo }}</strong>
            <StatusBadge
              :label="ANSWER_SOURCE_LABELS[version.sourceType]"
              :tone="version.sourceType === 'AI_GENERATED' ? 'brand' : 'info'"
            />
          </span>
          <small>{{ formatCoverLetterInstant(version.createdAt) }}</small>
          <small v-if="version.isCurrent" class="version-panel__current">지금 답변</small>
          <small v-if="version.restoredFromVersionId">
            v{{
              versions.find((item) => item.id === version.restoredFromVersionId)?.versionNo ?? '?'
            }}에서 되돌린 내용
          </small>
        </button>
      </div>

      <div v-if="selectedVersion" class="version-panel__comparison">
        <article>
          <h3>지금 답변</h3>
          <pre>{{ currentAnswer?.plainText ?? '(저장된 답변 없음)' }}</pre>
        </article>
        <article>
          <h3>
            v{{ selectedVersion.versionNo }} ·
            {{ ANSWER_SOURCE_LABELS[selectedVersion.sourceType] }}
          </h3>
          <pre>{{ selectedVersion.plainText || '(빈 답변)' }}</pre>
          <div v-if="verifications.length" class="version-panel__review">
            <p v-for="verification in verifications" :key="verification.id">
              <StatusBadge
                :label="VERIFICATION_STATUS_LABELS[verification.status]"
                :tone="tone(verification.status)"
              />
              <span>이 저장본을 검토한 결과예요.</span>
            </p>
            <ul class="historical-evidence">
              <li
                v-for="reference in verifications.flatMap((item) => [
                  ...item.evidenceRefs,
                  ...item.issues.flatMap((issue) => issue.evidenceRefs),
                ])"
                :key="reference.id"
              >
                <span>{{ reference.title }}</span>
                <small>{{ evidenceCurrentState(reference).label }}</small>
                <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                  새 초안·검토에서는 쓰지 않아요
                </small>
              </li>
            </ul>
          </div>
        </article>
        <button
          v-if="!readOnly && !selectedVersion.isCurrent"
          type="button"
          class="button button--secondary"
          :disabled="restorePending"
          data-testid="restore-answer-version"
          @click="emit('restore')"
        >
          {{ restorePending ? '되돌리는 중…' : `v${selectedVersion.versionNo} 내용으로 되돌리기` }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.version-panel {
  display: grid;
  gap: var(--space-4);
}

.version-panel__lead,
.version-panel__empty {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.version-panel__draft {
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-3);
  font-size: var(--font-size-sm);
}

.version-panel__layout {
  display: grid;
  gap: var(--space-4);
}

.version-panel__list {
  display: grid;
  gap: var(--space-1);
}

.version-panel__list button {
  display: grid;
  gap: var(--space-1);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
  text-align: left;
}

.version-panel__list button:hover {
  background: var(--color-fill);
}

.version-panel__item--active {
  box-shadow: inset 0 0 0 1px var(--color-brand-border);
}

.version-panel__label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  font-variant-numeric: tabular-nums;
}

.version-panel__list small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.version-panel__current {
  color: var(--color-brand-strong);
  font-weight: 700;
}

.version-panel__comparison {
  display: grid;
  gap: var(--space-3);
  justify-items: start;
}

.version-panel__comparison article {
  min-width: 0;
  width: 100%;
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-4);
}

.version-panel__comparison h3 {
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.version-panel__review {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
  font-size: var(--font-size-sm);
}

.version-panel__review p {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
}

.version-panel__review .historical-evidence {
  display: grid;
  gap: var(--space-1);
}

.version-panel__review .historical-evidence li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
}

.version-panel__review small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.version-panel__comparison pre {
  max-height: 14rem;
  margin-top: var(--space-2);
  overflow: auto;
  color: var(--color-text-secondary);
  font: inherit;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
