<script setup lang="ts">
import type { AssistTab } from '@/features/cover-letters/editorFlow'
import {
  ISSUE_CODE_LABELS,
  ISSUE_SEVERITY_LABELS,
  VERIFICATION_STATUS_LABELS,
  evidenceCurrentState,
} from '@/features/cover-letters/presentation'
import type { VerificationDto } from '@/shared/api/coverLetterContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

/*
 * 작성 도움. 공고가 요구하는 역량·보완점과 AI 검토 결과를 tab으로 번갈아 본다.
 * 답변에 쓸 소재 고르기는 편집기 아래 별도 영역에서 처리한다.
 */

withDefaults(
  defineProps<{
    tab: AssistTab
    requirements: readonly { category: string; text: string }[]
    gaps: readonly string[]
    analysisOutdated?: boolean
    jobId?: string
    verifications: readonly VerificationDto[]
    verificationsLoading?: boolean
    hasAnswer?: boolean
    reviewedVersionLabel?: string
    readOnly?: boolean
    canApplySuggestion?: boolean
  }>(),
  {
    analysisOutdated: false,
    jobId: '',
    verificationsLoading: false,
    hasAnswer: false,
    reviewedVersionLabel: '',
    readOnly: false,
    canApplySuggestion: false,
  },
)
const emit = defineEmits<{
  'update:tab': [value: AssistTab]
  'apply-suggestion': [suggestion: string]
  verify: []
}>()

function tone(status: VerificationDto['status']) {
  return ({ PENDING: 'neutral', PASSED: 'success', WARNING: 'warning', FAILED: 'danger' } as const)[
    status
  ]
}
</script>

<template>
  <section class="assist" aria-label="작성 도움">
    <div class="assist__tabs" role="tablist" aria-label="작성 도움 보기">
      <button
        type="button"
        role="tab"
        class="assist__tab"
        :class="{ 'assist__tab--active': tab === 'JOB' }"
        :aria-selected="tab === 'JOB'"
        data-testid="assist-tab-job"
        @click="emit('update:tab', 'JOB')"
      >
        공고 요구사항
      </button>
      <button
        type="button"
        role="tab"
        class="assist__tab"
        :class="{ 'assist__tab--active': tab === 'REVIEW' }"
        :aria-selected="tab === 'REVIEW'"
        data-testid="assist-tab-review"
        @click="emit('update:tab', 'REVIEW')"
      >
        AI 검토 결과
      </button>
    </div>

    <div v-if="tab === 'JOB'" class="assist__body">
      <section class="assist__block">
        <h3>공고가 원하는 것</h3>
        <p v-if="analysisOutdated" class="assist__note assist__note--warn">
          공고나 내 정보가 바뀐 뒤로 다시 분석하지 않았어요. 지금 내용도 참고할 수 있어요.
        </p>
        <ul v-if="requirements.length" class="assist__list">
          <li
            v-for="requirement in requirements"
            :key="`${requirement.category}-${requirement.text}`"
          >
            <AppIcon name="check" />
            <span>{{ requirement.text }}</span>
          </li>
        </ul>
        <p v-else class="assist__note">
          공고 분석 결과가 아직 없어요. 공고가 요구하는 내용을 직접 확인하고 작성해 주세요.
        </p>
        <RouterLink
          v-if="jobId"
          :to="{ name: 'job-analysis', params: { jobId } }"
          class="text-link"
        >
          공고 분석 전체 보기
        </RouterLink>
      </section>

      <section v-if="gaps.length" class="assist__block">
        <h3>보완하면 좋은 점</h3>
        <ul class="assist__list assist__list--gap">
          <li v-for="gap in gaps" :key="gap">
            <AppIcon name="lift" />
            <span>{{ gap }}</span>
          </li>
        </ul>
      </section>
    </div>

    <div v-else class="assist__body">
      <p v-if="!hasAnswer" class="assist__note">답변을 저장하면 AI 검토를 받을 수 있어요.</p>
      <p v-else-if="verificationsLoading" class="assist__note">검토 결과를 불러오는 중이에요…</p>
      <template v-else-if="verifications.length === 0">
        <p class="assist__note">
          이 답변은 아직 검토받지 않았어요. 근거가 없는 문장과 빠진 요구사항을 짚어 드려요.
        </p>
        <button
          v-if="!readOnly"
          type="button"
          class="button button--secondary"
          data-testid="assist-verify"
          @click="emit('verify')"
        >
          AI 검토 받기
        </button>
      </template>
      <template v-else>
        <p v-if="reviewedVersionLabel" class="assist__note">{{ reviewedVersionLabel }}</p>
        <article
          v-for="verification in verifications"
          :key="verification.id"
          class="verification-card"
        >
          <header>
            <StatusBadge
              :label="VERIFICATION_STATUS_LABELS[verification.status]"
              :tone="tone(verification.status)"
            />
            <RouterLink
              v-if="verification.agentRunId"
              :to="{ name: 'agent-run-detail', params: { agentRunId: verification.agentRunId } }"
              class="text-link"
            >
              검토 과정 보기
            </RouterLink>
          </header>

          <ul v-if="verification.issues.length" class="verification-issues">
            <li v-for="(issue, index) in verification.issues" :key="`${issue.code}-${index}`">
              <strong>
                {{ ISSUE_CODE_LABELS[issue.code] }} · {{ ISSUE_SEVERITY_LABELS[issue.severity] }}
              </strong>
              <blockquote v-if="issue.relatedText">{{ issue.relatedText }}</blockquote>
              <p>{{ issue.message }}</p>
              <ul v-if="issue.evidenceRefs.length" class="historical-evidence">
                <li v-for="reference in issue.evidenceRefs" :key="reference.id">
                  <span>{{ reference.title }}</span>
                  <small>{{ evidenceCurrentState(reference).label }}</small>
                  <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                    새 초안·검토에서는 쓰지 않아요
                  </small>
                </li>
              </ul>
            </li>
          </ul>
          <p v-else class="assist__note">고칠 곳을 찾지 못했어요.</p>

          <div v-if="verification.suggestions.length" class="verification-suggestions">
            <h4>이렇게 고쳐 보면 어떨까요</h4>
            <div v-for="suggestion in verification.suggestions" :key="suggestion">
              <p>{{ suggestion }}</p>
              <button
                v-if="canApplySuggestion"
                type="button"
                class="button button--secondary button--compact"
                @click="emit('apply-suggestion', suggestion)"
              >
                편집기에 넣기
              </button>
            </div>
            <small>넣기만 해서는 저장되지 않아요. 다듬은 뒤 답변 저장을 눌러 주세요.</small>
          </div>

          <ul v-if="verification.evidenceRefs.length" class="historical-evidence">
            <li v-for="reference in verification.evidenceRefs" :key="reference.id">
              <span>{{ reference.title }}</span>
              <small>{{ evidenceCurrentState(reference).label }}</small>
              <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                새 초안·검토에서는 쓰지 않아요
              </small>
            </li>
          </ul>
        </article>
      </template>
    </div>
  </section>
</template>

<style scoped>
.assist {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.assist__tabs {
  display: flex;
  gap: var(--space-1);
  border-bottom: 1px solid var(--color-border);
}

.assist__tab {
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  padding: var(--space-3) var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.assist__tab--active {
  border-bottom-color: var(--color-brand);
  color: var(--color-ink-title);
}

/* 편집 영역과 같은 높이 안에서만 스크롤한다. */
.assist__body {
  display: grid;
  align-content: start;
  gap: var(--space-6);
  min-height: 0;
  overflow-y: auto;
  padding-top: var(--space-4);
}

.assist__block {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.assist__block h3 {
  color: var(--color-ink-title);
  font-size: var(--font-size-sm);
  font-weight: 780;
}

.assist__note {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.assist__note--warn {
  color: var(--color-warning-strong);
}

.assist__lead {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.assist__lead strong {
  color: var(--color-ink-title);
  font-weight: 750;
}

.assist__lead-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.assist__list {
  display: grid;
  gap: var(--space-2);
}

.assist__list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.55;
}

.assist__list :deep(.icon) {
  width: 1rem;
  height: 1rem;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.assist__list--gap :deep(.icon) {
  color: var(--color-warning);
}

.assist__list--used small {
  display: block;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.assist__evidence {
  display: grid;
  gap: var(--space-1);
}

.assist__evidence label {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  font-size: var(--font-size-sm);
}

.assist__evidence label:hover {
  background: var(--color-fill);
}

.assist__evidence strong {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.assist__evidence em {
  margin-left: var(--space-2);
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-style: normal;
  font-weight: 700;
}

.assist__evidence small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.verification-card {
  display: grid;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.verification-card header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.verification-issues {
  display: grid;
  gap: var(--space-3);
}

.verification-issues > li {
  display: grid;
  gap: var(--space-1);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
  font-size: var(--font-size-sm);
}

.verification-issues strong {
  font-weight: 750;
}

.verification-issues blockquote {
  border-left: 2px solid var(--color-border-strong);
  color: var(--color-text-secondary);
  padding-left: var(--space-3);
}

.verification-suggestions {
  display: grid;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.verification-suggestions h4 {
  font-weight: 750;
}

.verification-suggestions > div {
  display: grid;
  gap: var(--space-2);
  justify-items: start;
}

.verification-suggestions small,
.historical-evidence small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.historical-evidence {
  display: grid;
  gap: var(--space-1);
  font-size: var(--font-size-sm);
}

.historical-evidence li {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: baseline;
}
</style>
