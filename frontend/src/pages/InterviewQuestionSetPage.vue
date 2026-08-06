<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import InterviewQuestionCard from '@/features/interviews/InterviewQuestionCard.vue'
import InterviewRunMonitor from '@/features/interviews/InterviewRunMonitor.vue'
import {
  INTERVIEW_QUESTION_TYPE_LABELS,
  RESEARCH_QUALITY_LABELS,
  RESEARCH_SOURCE_TYPE_LABELS,
  RESEARCH_STATUS_LABELS,
  RESEARCH_TOPIC_LABELS,
  SOURCE_COVERAGE_LABELS,
  coverageTone,
  formatInterviewInstant,
  questionSetJobLabel,
  researchStatusTone,
  sourceTypeTone,
} from '@/features/interviews/presentation'
import {
  useQuestionSetDetailQuery,
  useResearchSourceListQuery,
  useRetryResearchMutation,
} from '@/features/interviews/queries'
import { normalizeApiError } from '@/shared/api/errors'
import {
  INTERVIEW_QUESTION_TYPES,
  RESEARCH_SOURCE_TYPES,
  RESEARCH_TOPICS,
  type InterviewQuestionType,
  type ResearchSourceType,
  type ResearchTopic,
} from '@/shared/api/interviewContracts'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const questionSetId = computed(() => String(route.params.questionSetId ?? ''))
const detail = useQuestionSetDetailQuery(userId, questionSetId)
const researchRunId = computed(() => detail.data.value?.researchRunId ?? '')

const sourceTopic = ref<ResearchTopic | ''>('')
const sourceType = ref<ResearchSourceType | ''>('')
const sourcePage = ref(0)
const questionType = ref<InterviewQuestionType | ''>('')
const retryError = ref('')
const sources = useResearchSourceListQuery(
  userId,
  researchRunId,
  computed(() => ({
    topic: sourceTopic.value || undefined,
    sourceType: sourceType.value || undefined,
    page: sourcePage.value,
    size: 20,
    sort: 'providerRank,asc' as const,
  })),
)
const retryMutation = useRetryResearchMutation(userId)

const filteredQuestions = computed(() => {
  const questions = detail.data.value?.questions ?? []
  return questionType.value === ''
    ? questions
    : questions.filter((question) => question.questionType === questionType.value)
})
const researchIsActive = computed(() =>
  ['QUEUED', 'RUNNING'].includes(detail.data.value?.research.status ?? ''),
)
const hasCoverageWarning = computed(() =>
  ['LIMITED', 'NONE'].includes(detail.data.value?.research.sourceCoverage ?? ''),
)

function changeSourceFilter(): void {
  sourcePage.value = 0
}

async function retryResearch(): Promise<void> {
  const research = detail.data.value?.research
  if (!research || retryMutation.isPending.value) return
  retryError.value = ''
  try {
    const accepted = await retryMutation.mutateAsync({
      researchRunId: research.id,
      request: {},
    })
    await router.push({
      name: 'interview-question-set',
      params: { questionSetId: accepted.questionSetId },
    })
  } catch (error) {
    retryError.value = normalizeApiError(error).message
  }
}

async function refreshDetail(): Promise<void> {
  await Promise.all([detail.refetch(), sources.refetch()])
}
</script>

<template>
  <section class="question-set-page app-page" aria-labelledby="question-set-heading">
    <RouterLink class="question-set-page__back" :to="{ name: 'interviews' }">
      예상 질문 세트 목록
    </RouterLink>

    <StatePanel
      v-if="detail.isLoading.value"
      kind="loading"
      title="질문 세트를 불러오는 중…"
      description="조사 출처와 질문별 근거를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="detail.isError.value"
      kind="error"
      :title="
        normalizeApiError(detail.error.value).status === 404
          ? '질문 세트를 찾을 수 없어요.'
          : '질문 세트를 불러오지 못했어요.'
      "
      :description="normalizeApiError(detail.error.value).message"
    >
      <template #actions>
        <RouterLink class="button button--secondary" :to="{ name: 'interviews' }">
          목록으로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>

    <template v-else-if="detail.data.value">
      <PageHeader
        heading-id="question-set-heading"
        :title="detail.data.value.title"
        :description="`${questionSetJobLabel(detail.data.value.job)} · ${detail.data.value.coverLetter.title}`"
        variant="detail"
      />

      <section class="research-summary section-surface" aria-labelledby="research-summary-heading">
        <header>
          <div>
            <p class="page-eyebrow">공개 정보 조사</p>
            <h2 id="research-summary-heading">조사 결과</h2>
          </div>
          <div class="research-summary__badges">
            <StatusBadge
              :label="RESEARCH_QUALITY_LABELS[detail.data.value.research.researchQuality]"
              tone="brand"
            />
            <StatusBadge
              :label="RESEARCH_STATUS_LABELS[detail.data.value.research.status]"
              :tone="researchStatusTone(detail.data.value.research.status)"
            />
            <StatusBadge
              v-if="detail.data.value.research.sourceCoverage"
              :label="SOURCE_COVERAGE_LABELS[detail.data.value.research.sourceCoverage]"
              :tone="coverageTone(detail.data.value.research.sourceCoverage)"
            />
          </div>
        </header>

        <p
          v-if="hasCoverageWarning"
          class="research-summary__coverage-warning"
          role="status"
          data-testid="source-coverage-warning"
        >
          {{
            detail.data.value.research.sourceCoverage === 'NONE'
              ? '확인 가능한 공개 출처가 없어 일반 질문과 개인 맥락 중심으로 구성했어요.'
              : '공개 출처가 제한적이에요. 후기·커뮤니티 정보는 참고로만 보고 단정적인 사실로 사용하지 마세요.'
          }}
        </p>
        <p v-if="detail.data.value.research.safeError" class="alert alert--danger" role="alert">
          {{ detail.data.value.research.safeError.message }}
        </p>
        <p v-if="retryError" class="alert alert--danger" role="alert">{{ retryError }}</p>

        <p v-if="detail.data.value.research.summary" class="research-summary__copy">
          {{ detail.data.value.research.summary }}
        </p>
        <p v-else-if="researchIsActive" class="research-summary__copy">
          회사·채용 과정·유사 직무 면접 정보를 조사하고 있어요.
        </p>
        <p v-else class="research-summary__copy">저장된 조사 요약이 없어요.</p>

        <div
          v-if="detail.data.value.research.missingCoverageTopics.length > 0"
          class="research-summary__missing"
        >
          <strong>추가 확인이 필요한 주제</strong>
          <ul>
            <li v-for="topic in detail.data.value.research.missingCoverageTopics" :key="topic">
              {{ topic }}
            </li>
          </ul>
        </div>

        <div class="research-summary__meta">
          <span>조사 시작 {{ formatInterviewInstant(detail.data.value.research.startedAt) }}</span>
          <span
            >조사 완료 {{ formatInterviewInstant(detail.data.value.research.completedAt) }}</span
          >
        </div>
        <button
          v-if="detail.data.value.research.retryable"
          type="button"
          class="button button--secondary"
          :disabled="retryMutation.isPending.value"
          data-testid="retry-research"
          @click="retryResearch"
        >
          {{ retryMutation.isPending.value ? '재시도 접수 중…' : '새 조사와 질문 세트로 재시도' }}
        </button>
      </section>

      <InterviewRunMonitor
        :user-id="userId"
        :agent-run-id="detail.data.value.agentRun.id"
        workflow-type="INTERVIEW_PREPARATION"
        resource-type="QUESTION_SET"
        :resource-id="detail.data.value.id"
        @terminal="refreshDetail"
        @unavailable="refreshDetail"
      />

      <section class="research-sources section-surface" aria-labelledby="sources-heading">
        <header>
          <div>
            <p class="page-eyebrow">출처와 신뢰 수준</p>
            <h2 id="sources-heading">조사 출처</h2>
          </div>
        </header>
        <div class="research-sources__filters filter-toolbar">
          <label class="field">
            <span class="field__label">조사 주제</span>
            <select
              v-model="sourceTopic"
              class="control control--compact"
              @change="changeSourceFilter"
            >
              <option value="">전체</option>
              <option v-for="topic in RESEARCH_TOPICS" :key="topic" :value="topic">
                {{ RESEARCH_TOPIC_LABELS[topic] }}
              </option>
            </select>
          </label>
          <label class="field">
            <span class="field__label">출처 유형</span>
            <select
              v-model="sourceType"
              class="control control--compact"
              @change="changeSourceFilter"
            >
              <option value="">전체</option>
              <option v-for="type in RESEARCH_SOURCE_TYPES" :key="type" :value="type">
                {{ RESEARCH_SOURCE_TYPE_LABELS[type] }}
              </option>
            </select>
          </label>
        </div>

        <p v-if="sources.isLoading.value">출처를 불러오는 중…</p>
        <p v-else-if="sources.isError.value" class="alert alert--danger">
          {{ normalizeApiError(sources.error.value).message }}
        </p>
        <p v-else-if="sources.data.value?.items.length === 0" class="empty-copy">
          이 조건에서 확인된 출처가 없어요.
        </p>
        <div v-else class="research-sources__items">
          <article
            v-for="source in sources.data.value?.items"
            :key="source.id"
            class="source-card"
            :data-testid="`research-source-${source.sourceType}`"
          >
            <header>
              <div>
                <p class="page-eyebrow">{{ RESEARCH_TOPIC_LABELS[source.topic] }}</p>
                <h3>{{ source.title ?? '제목 없는 공개 출처' }}</h3>
              </div>
              <StatusBadge
                :label="RESEARCH_SOURCE_TYPE_LABELS[source.sourceType]"
                :tone="sourceTypeTone(source.sourceType)"
              />
            </header>
            <p v-if="source.snippet" class="source-card__snippet">{{ source.snippet }}</p>
            <p class="source-card__notice">{{ source.reliabilityNotice }}</p>
            <dl>
              <div>
                <dt>발행일</dt>
                <dd>{{ formatInterviewInstant(source.publishedAt) }}</dd>
              </div>
              <div>
                <dt>조회일</dt>
                <dd>{{ formatInterviewInstant(source.retrievedAt) }}</dd>
              </div>
            </dl>
            <a
              class="button button--secondary"
              :href="source.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              새 탭에서 원문 확인
            </a>
          </article>
        </div>
        <PaginationNav
          v-if="sources.data.value && sources.data.value.totalPages > 1"
          :page="sources.data.value.page"
          :total-pages="sources.data.value.totalPages"
          label="조사 출처 페이지"
          @change="sourcePage = $event"
        />
      </section>

      <section class="question-set-page__questions" aria-labelledby="questions-heading">
        <header class="question-set-page__questions-header">
          <div>
            <p class="page-eyebrow">예상 질문과 답변</p>
            <h2 id="questions-heading">질문 {{ detail.data.value.questionCount }}개</h2>
          </div>
          <label class="field">
            <span class="field__label">질문 유형</span>
            <select v-model="questionType" class="control control--compact">
              <option value="">전체</option>
              <option v-for="type in INTERVIEW_QUESTION_TYPES" :key="type" :value="type">
                {{ INTERVIEW_QUESTION_TYPE_LABELS[type] }}
              </option>
            </select>
          </label>
        </header>

        <StatePanel
          v-if="detail.data.value.questions.length === 0 && researchIsActive"
          kind="loading"
          title="예상 질문을 준비하는 중…"
          description="조사와 근거 검증이 끝나면 질문이 이곳에 표시돼요."
        />
        <StatePanel
          v-else-if="detail.data.value.questions.length === 0"
          kind="empty"
          title="이 질문 세트에는 생성된 질문이 없어요."
          description="취소되었거나 실패한 조사는 기존 결과를 덮어쓰지 않아요. 가능한 경우 새 lineage로 재시도하세요."
        />
        <StatePanel
          v-else-if="filteredQuestions.length === 0"
          kind="empty"
          title="선택한 유형의 질문이 없어요."
          description="질문 유형 필터를 바꾸어 보세요."
        />
        <div v-else class="question-set-page__question-list">
          <InterviewQuestionCard
            v-for="question in filteredQuestions"
            :key="question.id"
            :user-id="userId"
            :question-set-id="detail.data.value.id"
            :question="question"
            @changed="detail.refetch()"
          />
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.question-set-page {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
}

.question-set-page__back {
  justify-self: start;
  color: var(--color-brand);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.section-surface {
  min-width: 0;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  padding: var(--space-6);
  box-shadow: var(--shadow-panel);
}

.section-surface > header,
.research-summary__badges,
.question-set-page__questions-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.section-surface h2,
.question-set-page__questions h2 {
  margin-top: var(--space-1);
}

.research-summary {
  display: grid;
  gap: var(--space-4);
}

.research-summary__badges {
  flex-wrap: wrap;
}

.research-summary__coverage-warning {
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: inset 0 0 0 1px var(--color-warning-border);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-3);
  line-height: 1.65;
}

.research-summary__copy,
.research-summary__missing,
.research-summary__meta,
.empty-copy {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.research-summary__missing ul {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.research-summary__missing li {
  border-radius: 999px;
  background: var(--color-neutral-soft);
  padding: var(--space-1) var(--space-3);
}

.research-summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.research-summary > .button {
  justify-self: start;
}

.research-sources__filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(10rem, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.research-sources__items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.source-card {
  display: grid;
  min-width: 0;
  gap: var(--space-3);
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4);
}

.source-card > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.source-card h3 {
  margin-top: var(--space-1);
  font-size: 1rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.source-card__snippet,
.source-card__notice {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.source-card__notice {
  border-left: 3px solid var(--color-warning-border);
  background: var(--color-warning-soft);
  padding: var(--space-2) var(--space-3);
}

.source-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.source-card dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.source-card dd {
  margin-top: var(--space-1);
  font-size: var(--font-size-sm);
}

.source-card .button {
  justify-self: start;
}

.question-set-page__questions-header {
  align-items: end;
}

.question-set-page__question-list {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-4);
}

@media (max-width: 56rem) {
  .research-sources__items {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 40rem) {
  .section-surface {
    padding: var(--space-4);
  }

  .section-surface > header,
  .question-set-page__questions-header,
  .source-card > header {
    align-items: stretch;
    flex-direction: column;
  }

  .research-sources__filters,
  .source-card dl {
    grid-template-columns: 1fr;
  }

  .research-summary > .button,
  .source-card .button {
    width: 100%;
  }
}
</style>
