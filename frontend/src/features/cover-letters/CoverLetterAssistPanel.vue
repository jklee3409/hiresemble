<script setup lang="ts">
import { computed } from 'vue'

import type { AssistTab } from '@/features/cover-letters/editorFlow'
import {
  ISSUE_CODE_LABELS,
  ISSUE_SEVERITY_LABELS,
  VERIFICATION_STATUS_LABELS,
  evidenceCurrentState,
} from '@/features/cover-letters/presentation'
import type { EvidenceDto } from '@/shared/api/contracts'
import type { VerificationDto } from '@/shared/api/coverLetterContracts'
import type { EvidenceRefDto } from '@/shared/api/jobContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

/*
 * 작성 도움. 공고 요구사항, 이 답변에 실제로 쓰인 경험, 아직 쓰지 않은 경험,
 * 보완할 내용과 AI 검토 결과를 한 영역에서 tab으로 번갈아 본다.
 * 세 종류를 동시에 펼치지 않아 편집기 옆 공간을 과하게 쓰지 않는다.
 */

const props = withDefaults(
  defineProps<{
    tab: AssistTab
    requirements: readonly { category: string; text: string }[]
    gaps: readonly string[]
    analysisOutdated?: boolean
    jobId?: string
    usedEvidence: readonly EvidenceRefDto[]
    evidenceItems: readonly EvidenceDto[]
    recommendedEvidenceIds: ReadonlySet<string>
    selectedEvidenceIds: ReadonlySet<string>
    evidenceLoading?: boolean
    evidenceError?: boolean
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
    evidenceLoading: false,
    evidenceError: false,
    verificationsLoading: false,
    hasAnswer: false,
    reviewedVersionLabel: '',
    readOnly: false,
    canApplySuggestion: false,
  },
)
const emit = defineEmits<{
  'update:tab': [value: AssistTab]
  'toggle-evidence': [evidenceId: string]
  'clear-evidence': []
  'apply-suggestion': [suggestion: string]
  verify: []
}>()

const usedEvidenceIds = computed(() => new Set(props.usedEvidence.map((item) => item.id)))
const unusedEvidence = computed(() =>
  props.evidenceItems.filter((item) => !usedEvidenceIds.value.has(item.id)),
)
const selectedCount = computed(
  () => unusedEvidence.value.filter((item) => props.selectedEvidenceIds.has(item.id)).length,
)
function snippet(item: EvidenceDto): string {
  const content = (item.content ?? '').replace(/\s+/g, ' ').trim()
  return content.length > 80 ? `${content.slice(0, 80)}…` : content
}

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
        :class="{ 'assist__tab--active': tab === 'MATERIAL' }"
        :aria-selected="tab === 'MATERIAL'"
        data-testid="assist-tab-material"
        @click="emit('update:tab', 'MATERIAL')"
      >
        쓸 소재
      </button>
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

    <div v-if="tab === 'MATERIAL'" class="assist__body">
      <section class="assist__block">
        <div class="assist__lead">
          <p>
            여기서 고른 소재를 다음 <strong>AI 초안</strong>에 먼저 써요. 고르지 않으면 확인해 둔
            경험 전체에서 알맞은 것을 골라 써요.
          </p>
          <p v-if="selectedCount > 0" class="assist__lead-state">
            <strong>{{ selectedCount }}개 선택함</strong>
            <button
              v-if="!readOnly"
              type="button"
              class="button button--ghost button--compact"
              @click="emit('clear-evidence')"
            >
              모두 해제
            </button>
          </p>
        </div>

        <p v-if="evidenceLoading" class="assist__note">확인해 둔 경험을 불러오는 중이에요…</p>
        <p v-else-if="evidenceError" class="assist__note assist__note--warn">
          경험 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
        <template v-else-if="unusedEvidence.length">
          <ul class="assist__evidence">
            <li v-for="item in unusedEvidence" :key="item.id">
              <label :class="{ 'assist__evidence--on': selectedEvidenceIds.has(item.id) }">
                <input
                  type="checkbox"
                  class="checkbox-control"
                  :checked="selectedEvidenceIds.has(item.id)"
                  :disabled="readOnly"
                  @change="emit('toggle-evidence', item.id)"
                />
                <span>
                  <strong>{{ item.title }}</strong>
                  <em v-if="recommendedEvidenceIds.has(item.id)">이 공고와 잘 맞아요</em>
                  <small v-if="snippet(item)">{{ snippet(item) }}</small>
                </span>
              </label>
            </li>
          </ul>
        </template>
        <p v-else-if="evidenceItems.length" class="assist__note">
          확인해 둔 경험을 이 답변에 모두 썼어요.
        </p>
        <p v-else class="assist__note">
          아직 확인해 둔 경험이 없어요. 이력서·자료를 올리고 경험을 확인하면 소재로 쓸 수 있어요.
        </p>
        <RouterLink v-if="evidenceItems.length === 0" :to="{ name: 'documents' }" class="text-link">
          이력서·자료 올리러 가기
        </RouterLink>
      </section>

      <section class="assist__block">
        <h3>이 답변에 이미 쓴 소재</h3>
        <ul v-if="usedEvidence.length" class="assist__list assist__list--used">
          <li v-for="reference in usedEvidence" :key="reference.id">
            <AppIcon name="evidence" />
            <span>
              {{ reference.title }}
              <small>{{ evidenceCurrentState(reference).label }}</small>
              <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                새 초안·검토에서는 쓰지 않아요
              </small>
            </span>
          </li>
        </ul>
        <p v-else class="assist__note">
          아직 이 답변에 연결된 소재가 없어요. AI 검토를 받으면 어떤 경험이 근거가 됐는지 알려
          드려요.
        </p>
      </section>
    </div>

    <div v-else-if="tab === 'JOB'" class="assist__body">
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

.assist__body {
  display: grid;
  align-content: start;
  gap: var(--space-6);
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
