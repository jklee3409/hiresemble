<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useCoverLetterListQuery } from '@/features/cover-letters/queries'
import InterviewRunMonitor from '@/features/interviews/InterviewRunMonitor.vue'
import {
  AGENT_RUN_STATUS_LABELS,
  INTERVIEW_QUESTION_TYPE_LABELS,
  RESEARCH_QUALITY_LABELS,
  SOURCE_COVERAGE_LABELS,
  coverageTone,
  questionSetJobLabel,
} from '@/features/interviews/presentation'
import {
  useCreateInterviewPreparationMutation,
  useQuestionSetListQuery,
} from '@/features/interviews/queries'
import { jobDisplayTitle } from '@/features/jobs/presentation'
import { useJobDetailQuery } from '@/features/jobs/queries'
import { normalizeApiError, type ApiClientError } from '@/shared/api/errors'
import type { AiQualityMode } from '@/shared/api/agentRunContracts'
import {
  PREPARATION_QUESTION_TYPES,
  type PreparationQuestionType,
  type ResearchQuality,
} from '@/shared/api/interviewContracts'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const coverLetters = useCoverLetterListQuery(
  userId,
  computed(() => ({
    jobId: jobId.value,
    page: 0,
    size: 100,
    sort: 'updatedAt,desc' as const,
  })),
)
const questionSets = useQuestionSetListQuery(
  userId,
  computed(() => ({
    jobId: jobId.value,
    page: 0,
    size: 10,
    sort: 'updatedAt,desc' as const,
  })),
)
const prepareMutation = useCreateInterviewPreparationMutation(userId)

const selectedCoverLetterId = ref('')
const researchQuality = ref<ResearchQuality>('BASIC')
const qualityMode = ref<Extract<AiQualityMode, 'ECONOMY' | 'BALANCED'>>('BALANCED')
const selectedQuestionTypes = ref<PreparationQuestionType[]>([
  'COVER_LETTER',
  'TECHNICAL',
  'PROJECT_DEEP_DIVE',
  'BEHAVIORAL',
  'COMPANY_MOTIVATION',
])
const questionCount = ref(10)
const acceptedQuestionSetId = ref('')
const acceptedAgentRunId = ref('')
const actionError = ref<ApiClientError | null>(null)

const usableCoverLetters = computed(
  () =>
    coverLetters.data.value?.items.filter(
      (item) => item.status !== 'ARCHIVED' && item.answeredQuestionCount > 0,
    ) ?? [],
)
const latestQuestionSet = computed(() => questionSets.data.value?.items[0] ?? null)
const monitoredQuestionSetId = computed(
  () => acceptedQuestionSetId.value || latestQuestionSet.value?.id || '',
)
const monitoredAgentRunId = computed(
  () => acceptedAgentRunId.value || latestQuestionSet.value?.agentRun.id || '',
)
const canSubmit = computed(
  () =>
    selectedCoverLetterId.value !== '' &&
    selectedQuestionTypes.value.length > 0 &&
    questionCount.value >= 1 &&
    questionCount.value <= 20 &&
    !prepareMutation.isPending.value,
)
const prerequisiteMessage = computed(() => {
  if (actionError.value === null) return null
  switch (actionError.value.code) {
    case 'JOB_ANALYSIS_NOT_FOUND':
      return {
        title: '먼저 공고 분석을 완료해 주세요.',
        description: '최신 공고 분석이 있어야 면접 질문이 공고 요구사항을 반영할 수 있어요.',
        route: { name: 'job-analysis', params: { jobId: jobId.value } },
        action: '공고 분석으로 이동',
      }
    case 'COVER_LETTER_ARCHIVED':
      return {
        title: '보관된 자기소개서는 사용할 수 없어요.',
        description: '현재 작성 중이거나 최종화한 자기소개서를 선택해 주세요.',
        route: { name: 'job-cover-letter', params: { jobId: jobId.value } },
        action: '자기소개서 확인',
      }
    case 'RESOURCE_STATE_CONFLICT':
      return {
        title: '사용할 수 있는 자기소개서 답변이 필요해요.',
        description: '같은 공고의 자기소개서에 문항과 저장된 답변을 하나 이상 준비해 주세요.',
        route: { name: 'job-cover-letter', params: { jobId: jobId.value } },
        action: '자기소개서 작성',
      }
    default:
      return null
  }
})

function toggleQuestionType(value: PreparationQuestionType): void {
  const selected = selectedQuestionTypes.value.includes(value)
  selectedQuestionTypes.value = selected
    ? selectedQuestionTypes.value.filter((item) => item !== value)
    : [...selectedQuestionTypes.value, value]
}

async function submitPreparation(): Promise<void> {
  if (!canSubmit.value) return
  actionError.value = null
  try {
    const accepted = await prepareMutation.mutateAsync({
      jobId: jobId.value,
      request: {
        coverLetterId: selectedCoverLetterId.value,
        researchQuality: researchQuality.value,
        qualityMode: qualityMode.value,
        questionTypes: [...selectedQuestionTypes.value],
        questionCount: questionCount.value,
      },
    })
    acceptedQuestionSetId.value = accepted.questionSetId
    acceptedAgentRunId.value = accepted.agentRunId
    await questionSets.refetch()
  } catch (error) {
    actionError.value = normalizeApiError(error)
  }
}

async function refreshAfterRun(): Promise<void> {
  await Promise.all([job.refetch(), questionSets.refetch()])
}

async function refreshPage(): Promise<void> {
  await Promise.all([job.refetch(), coverLetters.refetch(), questionSets.refetch()])
}
</script>

<template>
  <section class="job-interview app-page" aria-labelledby="job-interview-heading">
    <PageHeader
      heading-id="job-interview-heading"
      title="면접 준비"
      :description="
        job.data.value
          ? `${jobDisplayTitle(job.data.value)}의 공개 정보를 조사하고 예상 질문을 준비하세요.`
          : '공고와 자기소개서, 확인한 경험을 바탕으로 예상 질문을 준비하세요.'
      "
      :level="2"
      variant="compact"
    />

    <StatePanel
      v-if="job.isLoading.value || coverLetters.isLoading.value || questionSets.isLoading.value"
      kind="loading"
      title="면접 준비 상태를 확인하는 중…"
      description="공고 분석과 사용할 수 있는 자기소개서를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="job.isError.value || coverLetters.isError.value || questionSets.isError.value"
      kind="error"
      title="면접 준비 정보를 불러오지 못했어요."
      :description="
        normalizeApiError(job.error.value ?? coverLetters.error.value ?? questionSets.error.value)
          .message
      "
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="refreshPage">
          다시 불러오기
        </button>
      </template>
    </StatePanel>

    <template v-else-if="job.data.value">
      <section class="job-interview__readiness section-surface" aria-labelledby="readiness-heading">
        <header>
          <div>
            <p class="page-eyebrow">준비 조건</p>
            <h2 id="readiness-heading">현재 준비 상태</h2>
          </div>
          <StatusBadge
            :label="
              job.data.value.latestAnalysis && usableCoverLetters.length > 0
                ? '접수 가능'
                : '준비 필요'
            "
            :tone="
              job.data.value.latestAnalysis && usableCoverLetters.length > 0 ? 'success' : 'warning'
            "
          />
        </header>
        <dl>
          <div>
            <dt>공고 분석</dt>
            <dd>{{ job.data.value.latestAnalysis ? '완료' : '필요' }}</dd>
          </div>
          <div>
            <dt>답변이 있는 자기소개서</dt>
            <dd>{{ usableCoverLetters.length }}개</dd>
          </div>
          <div>
            <dt>생성된 질문 세트</dt>
            <dd>{{ job.data.value.interviewPreparationCount }}개</dd>
          </div>
        </dl>
        <p class="job-interview__profile-note">
          프로필 완성 여부와 관계없이 접수할 수 있어요. 현재 입력한 최종 학력과 검증된 대외활동
          근거만 질문 맥락에 사용됩니다.
        </p>
      </section>

      <section class="job-interview__form section-surface" aria-labelledby="preparation-heading">
        <header>
          <div>
            <p class="page-eyebrow">새 질문 세트</p>
            <h2 id="preparation-heading">조사와 예상 질문 설정</h2>
          </div>
        </header>

        <div v-if="prerequisiteMessage" class="alert alert--warning" role="alert">
          <strong>{{ prerequisiteMessage.title }}</strong>
          <p>{{ prerequisiteMessage.description }}</p>
          <RouterLink class="button button--secondary" :to="prerequisiteMessage.route">
            {{ prerequisiteMessage.action }}
          </RouterLink>
        </div>
        <p v-else-if="actionError" class="alert alert--danger" role="alert">
          {{ actionError.message }}
        </p>

        <form class="job-interview__form-grid" @submit.prevent="submitPreparation">
          <label class="field job-interview__wide">
            <span class="field__label">사용할 자기소개서</span>
            <select v-model="selectedCoverLetterId" class="control" required>
              <option value="" disabled>답변이 저장된 자기소개서를 선택하세요</option>
              <option
                v-for="coverLetter in usableCoverLetters"
                :key="coverLetter.id"
                :value="coverLetter.id"
              >
                {{ coverLetter.title }} · 답변 {{ coverLetter.answeredQuestionCount }}개
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">조사 범위</span>
            <select v-model="researchQuality" class="control">
              <option value="BASIC">{{ RESEARCH_QUALITY_LABELS.BASIC }} · 검색 최대 2개</option>
              <option value="ADVANCED">
                {{ RESEARCH_QUALITY_LABELS.ADVANCED }} · 검색 최대 4개
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">답변 생성 품질</span>
            <select v-model="qualityMode" class="control">
              <option value="ECONOMY">경제적</option>
              <option value="BALANCED">균형</option>
            </select>
          </label>

          <fieldset class="job-interview__types job-interview__wide">
            <legend class="field__label">질문 유형</legend>
            <div>
              <label v-for="type in PREPARATION_QUESTION_TYPES" :key="type">
                <input
                  type="checkbox"
                  :checked="selectedQuestionTypes.includes(type)"
                  @change="toggleQuestionType(type)"
                />
                <span>{{ INTERVIEW_QUESTION_TYPE_LABELS[type] }}</span>
              </label>
            </div>
            <small>하나 이상 선택하세요. AI가 답변을 바탕으로 후속 질문을 추가할 수 있어요.</small>
          </fieldset>

          <label class="field">
            <span class="field__label">질문 수</span>
            <input
              v-model.number="questionCount"
              class="control"
              type="number"
              min="1"
              max="20"
              inputmode="numeric"
            />
          </label>

          <div class="job-interview__submit">
            <button
              type="submit"
              class="button button--primary"
              :disabled="!canSubmit"
              data-testid="submit-interview-preparation"
            >
              {{ prepareMutation.isPending.value ? '접수 중…' : '면접 준비 시작' }}
            </button>
            <RouterLink
              v-if="usableCoverLetters.length === 0"
              class="button button--secondary"
              :to="{ name: 'job-cover-letter', params: { jobId } }"
            >
              자기소개서 준비
            </RouterLink>
          </div>
        </form>
      </section>

      <InterviewRunMonitor
        v-if="monitoredQuestionSetId && monitoredAgentRunId"
        :user-id="userId"
        :agent-run-id="monitoredAgentRunId"
        workflow-type="INTERVIEW_PREPARATION"
        resource-type="QUESTION_SET"
        :resource-id="monitoredQuestionSetId"
        @terminal="refreshAfterRun"
        @unavailable="refreshAfterRun"
      />

      <section
        v-if="latestQuestionSet"
        class="job-interview__latest section-surface"
        aria-labelledby="latest-question-set-heading"
      >
        <header>
          <div>
            <p class="page-eyebrow">{{ questionSetJobLabel(latestQuestionSet.job) }}</p>
            <h2 id="latest-question-set-heading">{{ latestQuestionSet.title }}</h2>
          </div>
          <div class="job-interview__badges">
            <StatusBadge
              :label="AGENT_RUN_STATUS_LABELS[latestQuestionSet.agentRun.status]"
              :tone="
                latestQuestionSet.agentRun.status === 'SUCCEEDED'
                  ? 'success'
                  : latestQuestionSet.agentRun.status === 'FAILED'
                    ? 'danger'
                    : latestQuestionSet.agentRun.status === 'RUNNING'
                      ? 'info'
                      : latestQuestionSet.agentRun.status === 'WAITING_USER' ||
                          latestQuestionSet.agentRun.status === 'INTERRUPTED'
                        ? 'warning'
                        : 'neutral'
              "
            />
            <StatusBadge
              v-if="latestQuestionSet.sourceCoverage"
              :label="SOURCE_COVERAGE_LABELS[latestQuestionSet.sourceCoverage]"
              :tone="coverageTone(latestQuestionSet.sourceCoverage)"
            />
          </div>
        </header>
        <p>
          질문 {{ latestQuestionSet.questionCount }}개 · 조사 출처 coverage와 근거 연결을 상세에서
          확인할 수 있어요.
        </p>
        <RouterLink
          class="button button--primary"
          :to="{
            name: 'interview-question-set',
            params: { questionSetId: latestQuestionSet.id },
          }"
        >
          질문 세트 열기
        </RouterLink>
      </section>
    </template>
  </section>
</template>

<style scoped>
.job-interview {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
}

.section-surface {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-6);
  box-shadow: var(--shadow-sm);
}

.section-surface > header,
.job-interview__badges,
.job-interview__submit {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.section-surface h2 {
  margin-top: var(--space-1);
}

.job-interview__readiness dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.job-interview__readiness dl div {
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.job-interview__readiness dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.job-interview__readiness dd {
  margin-top: var(--space-1);
  font-weight: 750;
}

.job-interview__profile-note,
.job-interview__latest > p,
.job-interview__types small {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.job-interview__profile-note {
  margin-top: var(--space-4);
}

.job-interview__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.job-interview__wide,
.job-interview__submit {
  grid-column: 1 / -1;
}

.job-interview__types {
  min-width: 0;
  border: 0;
  padding: 0;
}

.job-interview__types > div {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.job-interview__types label {
  display: flex;
  min-height: 2.75rem;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
}

.job-interview__types small {
  display: block;
  margin-top: var(--space-2);
}

.job-interview__submit {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.job-interview__latest {
  display: grid;
  gap: var(--space-4);
}

.job-interview__badges {
  flex-wrap: wrap;
}

.job-interview__latest .button {
  justify-self: start;
}

.alert p {
  margin: var(--space-2) 0;
}

@media (max-width: 48rem) {
  .job-interview__readiness dl,
  .job-interview__types > div {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 40rem) {
  .section-surface {
    padding: var(--space-4);
  }

  .section-surface > header,
  .job-interview__form-grid {
    grid-template-columns: 1fr;
  }

  .section-surface > header {
    align-items: stretch;
    flex-direction: column;
  }

  .job-interview__form-grid {
    display: grid;
  }

  .job-interview__wide,
  .job-interview__submit {
    grid-column: auto;
  }

  .job-interview__submit .button,
  .job-interview__latest .button {
    width: 100%;
  }
}
</style>
