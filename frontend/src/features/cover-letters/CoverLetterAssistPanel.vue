<script setup lang="ts">
import { computed } from 'vue'

import type { AssistTab } from '@/features/cover-letters/editorFlow'
import {
  ISSUE_CODE_LABELS,
  ISSUE_SEVERITY_LABELS,
  VERIFICATION_STATUS_LABELS,
  VERIFICATION_STATUS_TONES,
  evidenceCurrentState,
} from '@/features/cover-letters/presentation'
import { FIT_CRITERION_CATEGORY_LABELS } from '@/features/jobs/analysisPresentation'
import type { VerificationDto } from '@/shared/api/coverLetterContracts'
import type { FitCriterionCategory } from '@/shared/api/jobContracts'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

/*
 * 작성 도움. 공고가 요구하는 역량·보완점과 AI 검토 결과를 tab으로 번갈아 본다.
 * 답변에 쓸 소재 고르기는 편집기 아래 별도 영역에서 처리한다.
 */

const props = withDefaults(
  defineProps<{
    tab: AssistTab
    requirements: readonly { category: FitCriterionCategory; text: string }[]
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

/*
 * 검토 결과가 쌓이면 최신 한 건만 펼쳐 두고 나머지는 접는다.
 * 지적 사항은 "수정 필요"를 먼저 보여 훑어보는 순서를 고정한다.
 */
const latestVerification = computed(() => props.verifications[0] ?? null)
const olderVerifications = computed(() => props.verifications.slice(1))

function sortedIssues(verification: VerificationDto) {
  return [...verification.issues].sort(
    (left, right) => severityRank(right.severity) - severityRank(left.severity),
  )
}

function severityRank(severity: VerificationDto['issues'][number]['severity']): number {
  return severity === 'ERROR' ? 1 : 0
}

function issueSummary(verification: VerificationDto): string {
  const errors = verification.issues.filter((issue) => issue.severity === 'ERROR').length
  const warnings = verification.issues.length - errors
  if (verification.issues.length === 0) return '지적 사항 없음'
  if (errors === 0) return `확인 권장 ${warnings}건`
  if (warnings === 0) return `수정 필요 ${errors}건`
  return `수정 필요 ${errors}건 · 확인 권장 ${warnings}건`
}

function tone(status: VerificationDto['status']) {
  return VERIFICATION_STATUS_TONES[status]
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
        <ul v-if="requirements.length" class="assist__cards">
          <li
            v-for="requirement in requirements"
            :key="`${requirement.category}-${requirement.text}`"
          >
            <span class="assist__card-tag">{{
              FIT_CRITERION_CATEGORY_LABELS[requirement.category]
            }}</span>
            <p>{{ requirement.text }}</p>
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
        <ul class="assist__cards">
          <li v-for="gap in gaps" :key="gap">
            <p>{{ gap }}</p>
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
        <article v-if="latestVerification" class="verification-card">
          <header>
            <StatusBadge
              :label="VERIFICATION_STATUS_LABELS[latestVerification.status]"
              :tone="tone(latestVerification.status)"
            />
            <span class="verification-card__count">{{ issueSummary(latestVerification) }}</span>
            <RouterLink
              v-if="latestVerification.agentRunId"
              :to="{
                name: 'agent-run-detail',
                params: { agentRunId: latestVerification.agentRunId },
              }"
              class="text-link"
            >
              검토 과정 보기
            </RouterLink>
          </header>

          <ul v-if="latestVerification.issues.length" class="verification-issues">
            <li
              v-for="(issue, index) in sortedIssues(latestVerification)"
              :key="`${issue.code}-${index}`"
              :data-severity="issue.severity"
            >
              <p class="verification-issues__head">
                <em>{{ ISSUE_SEVERITY_LABELS[issue.severity] }}</em>
                <strong>{{ ISSUE_CODE_LABELS[issue.code] }}</strong>
              </p>
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

          <div v-if="latestVerification.suggestions.length" class="verification-suggestions">
            <h4>이렇게 고쳐 보면 어떨까요</h4>
            <div v-for="suggestion in latestVerification.suggestions" :key="suggestion">
              <p>{{ suggestion }}</p>
              <button
                v-if="canApplySuggestion"
                type="button"
                class="button button--secondary button--compact"
                @click="emit('apply-suggestion', suggestion)"
              >
                답변에 적용
              </button>
            </div>
            <small>적용해도 바로 저장되지 않아요. 다듬은 뒤 답변 저장을 눌러 주세요.</small>
          </div>

          <ul v-if="latestVerification.evidenceRefs.length" class="historical-evidence">
            <li v-for="reference in latestVerification.evidenceRefs" :key="reference.id">
              <span>{{ reference.title }}</span>
              <small>{{ evidenceCurrentState(reference).label }}</small>
              <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                새 초안·검토에서는 쓰지 않아요
              </small>
            </li>
          </ul>
        </article>

        <!-- 지난 검토는 접어 둔다. 최신 결과 하나만 읽으면 되도록 화면을 비운다. -->
        <details v-if="olderVerifications.length" class="verification-archive">
          <summary>지난 검토 {{ olderVerifications.length }}개</summary>
          <article
            v-for="verification in olderVerifications"
            :key="verification.id"
            class="verification-card verification-card--past"
          >
            <header>
              <StatusBadge
                :label="VERIFICATION_STATUS_LABELS[verification.status]"
                :tone="tone(verification.status)"
              />
              <span class="verification-card__count">{{ issueSummary(verification) }}</span>
              <RouterLink
                v-if="verification.agentRunId"
                :to="{ name: 'agent-run-detail', params: { agentRunId: verification.agentRunId } }"
                class="text-link"
              >
                검토 과정 보기
              </RouterLink>
            </header>
            <ul v-if="verification.issues.length" class="verification-issues">
              <li
                v-for="(issue, index) in sortedIssues(verification)"
                :key="`${issue.code}-${index}`"
                :data-severity="issue.severity"
              >
                <p class="verification-issues__head">
                  <em>{{ ISSUE_SEVERITY_LABELS[issue.severity] }}</em>
                  <strong>{{ ISSUE_CODE_LABELS[issue.code] }}</strong>
                </p>
                <p>{{ issue.message }}</p>
              </li>
            </ul>
            <p v-else class="assist__note">고칠 곳을 찾지 못했어요.</p>
          </article>
        </details>
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
  gap: var(--space-4);
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

/*
 * 요구사항 한 줄을 카드 한 장으로 둔다. 앞에 붙은 분류 알약이 무엇에 대한 요구인지 먼저 알리고,
 * 아이콘 없이 채움면과 여백만으로 항목 경계를 만든다.
 */
.assist__cards {
  display: grid;
  gap: var(--space-2);
}

.assist__cards li {
  display: grid;
  gap: var(--space-2);
  justify-items: start;
  border-radius: var(--radius-md);
  background: var(--color-fill);
  padding: var(--space-3) var(--space-4);
}

.assist__cards p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.assist__card-tag {
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-brand-strong);
  padding: 0.125rem 0.5rem;
  font-size: var(--font-size-xs);
  font-weight: 750;
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

/*
 * 검토 결과는 개수가 많아질수록 경계가 흐려진다.
 * 결과 한 건을 하나의 면으로 묶고, 지적 사항은 심각도 색 띠로 훑을 수 있게 한다.
 */
.verification-card {
  display: grid;
  gap: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: var(--space-4);
}

.verification-card header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.verification-card__count {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.verification-card header .text-link {
  margin-left: auto;
  font-size: var(--font-size-xs);
}

.verification-archive {
  min-width: 0;
}

.verification-archive > summary {
  display: flex;
  min-height: 2.5rem;
  align-items: center;
  justify-content: space-between;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-xs);
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.verification-archive > summary::-webkit-details-marker {
  display: none;
}

.verification-archive > summary::after {
  color: var(--color-brand);
  content: '+';
  font-size: var(--font-size-md);
}

.verification-archive[open] > summary::after {
  content: '−';
}

.verification-archive .verification-card {
  margin-top: var(--space-2);
}

.verification-card--past {
  box-shadow: none;
  background: var(--color-fill);
}

.verification-issues {
  display: grid;
  gap: var(--space-2);
}

/*
 * 지적 사항 한 건. 왼쪽 색 띠 없이 옅은 채움면과 심각도 알약만으로 구분한다.
 * 목록이 "수정 필요"를 먼저 정렬하므로 위치도 함께 심각도를 알린다.
 */
.verification-issues > li {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-fill);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.verification-card--past .verification-issues > li {
  background: var(--color-surface);
}

.verification-issues > li[data-severity='ERROR'] {
  background: var(--color-danger-soft);
}

.verification-issues > li[data-severity='WARNING'] {
  background: var(--color-notice-soft);
}

.verification-issues__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
}

.verification-issues__head em {
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: 0.0625rem 0.5rem;
  font-size: var(--font-size-xs);
  font-style: normal;
  font-weight: 750;
}

.verification-issues > li[data-severity='ERROR'] .verification-issues__head em {
  color: var(--color-danger-strong);
}

.verification-issues > li[data-severity='WARNING'] .verification-issues__head em {
  color: var(--color-notice-strong);
}

.verification-issues strong {
  color: var(--color-ink-title);
  font-weight: 750;
}

/* 지적한 문장은 왼쪽 선 대신 안쪽 흰 면으로 원문임을 알린다. */
.verification-issues blockquote {
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-3);
  line-height: 1.6;
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
