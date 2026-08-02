<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { useActiveAgentRunsQuery } from '@/features/agent-runs/queries'
import InterviewRunMonitor from './InterviewRunMonitor.vue'
import {
  INTERVIEW_QUESTION_TYPE_LABELS,
  RESEARCH_SOURCE_TYPE_LABELS,
  formatInterviewInstant,
  sourceTypeTone,
} from './presentation'
import {
  useInterviewAnswerVersionListQuery,
  useInterviewFeedbackListQuery,
  useRequestInterviewFeedbackMutation,
  useSaveInterviewAnswerMutation,
} from './queries'
import { getInterviewQuestion } from '@/shared/api/interviewApi'
import { normalizeApiError } from '@/shared/api/errors'
import type {
  InterviewAnswerVersionDto,
  InterviewQuestionDto,
} from '@/shared/api/interviewContracts'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const props = defineProps<{
  userId: string
  questionSetId: string
  question: InterviewQuestionDto
}>()
const emit = defineEmits<{ changed: [] }>()

interface AnswerConflict {
  submittedSnapshot: string
  submittedParentVersionId: string | null
  serverAnswer: InterviewAnswerVersionDto | null
}

const questionId = computed(() => props.question.id)
const draft = ref('')
const serverCurrent = ref<InterviewAnswerVersionDto | null>(null)
const conflict = ref<AnswerConflict | null>(null)
const actionError = ref('')
const statusMessage = ref('')
const selectedFeedbackVersionId = ref('')
const feedbackQuality = ref<'ECONOMY' | 'BALANCED' | 'HIGH_QUALITY'>('BALANCED')
const feedbackRunId = ref('')
const feedbackRunVersionId = ref('')
const feedbackRunActive = ref(false)

watch(
  questionId,
  () => {
    serverCurrent.value = props.question.currentAnswer
    draft.value = props.question.currentAnswer?.content ?? ''
    selectedFeedbackVersionId.value = props.question.currentAnswer?.id ?? ''
    conflict.value = null
    actionError.value = ''
    statusMessage.value = ''
    feedbackRunId.value = ''
    feedbackRunVersionId.value = ''
    feedbackRunActive.value = false
  },
  { immediate: true },
)

watch(
  () => props.question.currentAnswer,
  (answer) => {
    if (answer?.id === serverCurrent.value?.id) return
    serverCurrent.value = answer
    if (draft.value === '' || draft.value === conflict.value?.serverAnswer?.content) {
      draft.value = answer?.content ?? ''
    }
  },
)

const answerVersions = useInterviewAnswerVersionListQuery(
  computed(() => props.userId),
  questionId,
  computed(() => ({ page: 0, size: 100, sort: 'versionNo,desc' as const })),
)
const feedbacks = useInterviewFeedbackListQuery(
  computed(() => props.userId),
  selectedFeedbackVersionId,
  computed(() => ({ page: 0, size: 100, sort: 'createdAt,desc' as const })),
)
const saveMutation = useSaveInterviewAnswerMutation(computed(() => props.userId))
const feedbackMutation = useRequestInterviewFeedbackMutation(computed(() => props.userId))
const activeRuns = useActiveAgentRunsQuery(computed(() => props.userId))

watch(
  () => answerVersions.data.value,
  (page) => {
    if (selectedFeedbackVersionId.value !== '' || !page?.items[0]) return
    selectedFeedbackVersionId.value = page.items[0].id
  },
  { immediate: true },
)

const trimmedDraftLength = computed(() => draft.value.trim().length)
const canSave = computed(
  () =>
    trimmedDraftLength.value > 0 && draft.value.length <= 20_000 && !saveMutation.isPending.value,
)
const selectedFeedbackVersion = computed(
  () =>
    answerVersions.data.value?.items.find((item) => item.id === selectedFeedbackVersionId.value) ??
    (serverCurrent.value?.id === selectedFeedbackVersionId.value ? serverCurrent.value : null),
)
const restoredFeedbackRun = computed(
  () =>
    activeRuns.data.value?.items.find(
      (run) =>
        run.workflowType === 'INTERVIEW_ANSWER_FEEDBACK' &&
        run.resourceType === 'INTERVIEW_ANSWER_VERSION' &&
        run.resourceId === selectedFeedbackVersionId.value,
    ) ?? null,
)
const feedbackRunIsActive = computed(
  () => feedbackRunActive.value || restoredFeedbackRun.value !== null,
)
const monitoredFeedbackRunId = computed(
  () => feedbackRunId.value || restoredFeedbackRun.value?.id || '',
)
const monitoredFeedbackVersionId = computed(
  () => feedbackRunVersionId.value || restoredFeedbackRun.value?.resourceId || '',
)

async function saveAnswer(
  content = draft.value,
  parentVersionId = serverCurrent.value?.id ?? null,
): Promise<void> {
  if (content.trim().length === 0 || content.length > 20_000 || saveMutation.isPending.value) {
    return
  }
  const immutableSnapshot = content
  actionError.value = ''
  statusMessage.value = ''
  try {
    const saved = await saveMutation.mutateAsync({
      questionSetId: props.questionSetId,
      questionId: props.question.id,
      request: { content: immutableSnapshot, parentVersionId },
    })
    serverCurrent.value = saved
    draft.value = saved.content
    selectedFeedbackVersionId.value = saved.id
    conflict.value = null
    statusMessage.value = `답변 버전 ${saved.versionNo}을 저장했어요.`
    await answerVersions.refetch()
    emit('changed')
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (apiError.status === 409 && apiError.code === 'RESOURCE_VERSION_CONFLICT') {
      try {
        const latest = await getInterviewQuestion(props.question.id)
        serverCurrent.value = latest.currentAnswer
        conflict.value = {
          submittedSnapshot: immutableSnapshot,
          submittedParentVersionId: parentVersionId,
          serverAnswer: latest.currentAnswer,
        }
      } catch (refreshError) {
        actionError.value = normalizeApiError(refreshError).message
      }
      return
    }
    actionError.value = apiError.message
  }
}

async function reapplyConflict(): Promise<void> {
  const currentConflict = conflict.value
  if (currentConflict === null) return
  await saveAnswer(currentConflict.submittedSnapshot, currentConflict.serverAnswer?.id ?? null)
}

function cancelConflict(): void {
  const currentConflict = conflict.value
  if (currentConflict === null) return
  draft.value = currentConflict.serverAnswer?.content ?? ''
  serverCurrent.value = currentConflict.serverAnswer
  selectedFeedbackVersionId.value = currentConflict.serverAnswer?.id ?? ''
  conflict.value = null
  statusMessage.value = '서버의 최신 답변으로 동기화했어요.'
}

async function requestFeedback(): Promise<void> {
  if (
    selectedFeedbackVersionId.value === '' ||
    feedbackMutation.isPending.value ||
    feedbackRunIsActive.value ||
    activeRuns.isLoading.value ||
    activeRuns.isError.value
  ) {
    return
  }
  actionError.value = ''
  statusMessage.value = ''
  try {
    const accepted = await feedbackMutation.mutateAsync({
      questionSetId: props.questionSetId,
      answerVersionId: selectedFeedbackVersionId.value,
      request: { qualityMode: feedbackQuality.value },
    })
    feedbackRunId.value = accepted.agentRunId
    feedbackRunVersionId.value = selectedFeedbackVersionId.value
    feedbackRunActive.value = true
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function refreshFeedbackAfterRun(): Promise<void> {
  feedbackRunActive.value = false
  feedbackRunId.value = ''
  feedbackRunVersionId.value = ''
  await Promise.all([feedbacks.refetch(), activeRuns.refetch()])
  emit('changed')
}
</script>

<template>
  <article class="interview-question" :data-testid="`interview-question-${question.id}`">
    <header class="interview-question__header">
      <div>
        <p class="page-eyebrow">질문 {{ question.questionOrder }}</p>
        <h3>{{ question.questionText }}</h3>
      </div>
      <div class="interview-question__badges">
        <StatusBadge :label="INTERVIEW_QUESTION_TYPE_LABELS[question.questionType]" tone="brand" />
        <StatusBadge
          :label="question.sourceBased ? '조사 출처 기반' : '일반·개인 맥락 기반'"
          :tone="question.sourceBased ? 'info' : 'neutral'"
        />
      </div>
    </header>

    <section v-if="question.intent" class="interview-question__note">
      <h4>질문 의도</h4>
      <p>{{ question.intent }}</p>
    </section>

    <div class="interview-question__guidance">
      <section v-if="question.evaluationPoints.length > 0">
        <h4>평가 포인트</h4>
        <ul>
          <li v-for="point in question.evaluationPoints" :key="point">{{ point }}</li>
        </ul>
      </section>
      <section v-if="question.answerGuide">
        <h4>답변 가이드</h4>
        <p>{{ question.answerGuide }}</p>
      </section>
    </div>

    <section
      v-if="
        question.relatedEvidenceRefs.length > 0 ||
        question.sourceRefs.length > 0 ||
        question.followUpQuestions.length > 0
      "
      class="interview-question__provenance"
    >
      <div v-if="question.relatedEvidenceRefs.length > 0">
        <h4>연결된 내 근거</h4>
        <ul class="interview-question__chips">
          <li v-for="evidence in question.relatedEvidenceRefs" :key="evidence.id">
            {{ evidence.title }} · 대외활동 근거
          </li>
        </ul>
      </div>
      <div v-if="question.sourceRefs.length > 0">
        <h4>연결된 조사 출처</h4>
        <ul class="interview-question__source-links">
          <li v-for="source in question.sourceRefs" :key="source.id">
            <a :href="source.sourceUrl" target="_blank" rel="noopener noreferrer">
              {{ source.title ?? source.sourceUrl }}
            </a>
            <StatusBadge
              :label="RESEARCH_SOURCE_TYPE_LABELS[source.sourceType]"
              :tone="sourceTypeTone(source.sourceType)"
            />
          </li>
        </ul>
      </div>
      <div v-if="question.followUpQuestions.length > 0">
        <h4>이어질 수 있는 질문</h4>
        <ul>
          <li v-for="followUp in question.followUpQuestions" :key="followUp">
            {{ followUp }}
          </li>
        </ul>
      </div>
    </section>

    <section class="interview-question__answer" :aria-labelledby="`answer-${question.id}`">
      <header>
        <div>
          <p class="page-eyebrow">내 답변</p>
          <h4 :id="`answer-${question.id}`">답변 버전 저장</h4>
        </div>
        <span>{{ draft.length.toLocaleString('ko-KR') }} / 20,000자</span>
      </header>
      <label class="field">
        <span class="sr-only">면접 답변</span>
        <textarea
          v-model="draft"
          class="control interview-question__textarea"
          maxlength="20000"
          rows="8"
          placeholder="질문에 대한 답변을 작성하세요. 저장할 때마다 변경 불가능한 새 버전이 만들어져요."
        />
      </label>
      <p v-if="statusMessage" class="alert alert--success" role="status">{{ statusMessage }}</p>
      <p v-if="actionError" class="alert alert--danger" role="alert">{{ actionError }}</p>
      <button
        type="button"
        class="button button--primary"
        :disabled="!canSave"
        :data-testid="`save-interview-answer-${question.id}`"
        @click="saveAnswer()"
      >
        {{ saveMutation.isPending.value ? '저장 중…' : '새 답변 버전 저장' }}
      </button>
    </section>

    <section
      v-if="conflict"
      class="answer-conflict"
      role="alert"
      :aria-labelledby="`answer-conflict-${question.id}`"
      :data-testid="`answer-conflict-${question.id}`"
    >
      <header>
        <div>
          <p class="page-eyebrow">동시 수정 감지</p>
          <h4 :id="`answer-conflict-${question.id}`">서버에 더 최신 답변이 있어요.</h4>
        </div>
      </header>
      <p>
        자동으로 덮어쓰지 않았어요. 처음 제출한 내 답변과 서버의 최신 답변을 비교한 뒤 선택하세요.
      </p>
      <div class="answer-conflict__comparison">
        <section>
          <h5>내가 제출한 답변</h5>
          <pre>{{ conflict.submittedSnapshot }}</pre>
          <small>
            제출 당시 기준:
            {{ conflict.submittedParentVersionId ? '이전 저장 버전' : '최초 버전' }}
          </small>
        </section>
        <section>
          <h5>서버의 최신 답변</h5>
          <pre>{{ conflict.serverAnswer?.content ?? '저장된 답변 없음' }}</pre>
          <small>
            {{
              conflict.serverAnswer
                ? `버전 ${conflict.serverAnswer.versionNo}`
                : '아직 저장된 버전 없음'
            }}
          </small>
        </section>
      </div>
      <div class="answer-conflict__actions">
        <button
          type="button"
          class="button button--primary"
          :disabled="saveMutation.isPending.value"
          :data-testid="`reapply-interview-answer-${question.id}`"
          @click="reapplyConflict"
        >
          내 답변을 최신 버전 위에 재적용
        </button>
        <button
          type="button"
          class="button button--secondary"
          :disabled="saveMutation.isPending.value"
          @click="cancelConflict"
        >
          취소하고 서버 답변 사용
        </button>
      </div>
    </section>

    <section class="interview-question__history" :aria-labelledby="`history-${question.id}`">
      <header>
        <div>
          <p class="page-eyebrow">버전과 피드백</p>
          <h4 :id="`history-${question.id}`">저장 이력</h4>
        </div>
      </header>
      <p v-if="answerVersions.isLoading.value">답변 버전을 불러오는 중…</p>
      <p v-else-if="answerVersions.isError.value" class="alert alert--danger">
        답변 버전 이력을 불러오지 못했어요.
      </p>
      <p v-else-if="answerVersions.data.value?.items.length === 0" class="empty-copy">
        답변을 저장하면 버전별 피드백을 요청할 수 있어요.
      </p>
      <div v-else class="answer-history">
        <label
          v-for="version in answerVersions.data.value?.items"
          :key="version.id"
          class="answer-history__item"
          :class="{
            'answer-history__item--selected': selectedFeedbackVersionId === version.id,
          }"
        >
          <input
            v-model="selectedFeedbackVersionId"
            type="radio"
            :name="`feedback-version-${question.id}`"
            :value="version.id"
          />
          <span>
            <strong>버전 {{ version.versionNo }}</strong>
            <small>
              {{ version.isCurrent ? '현재 답변 · ' : '' }}
              {{ formatInterviewInstant(version.createdAt) }}
            </small>
            <span>{{ version.content }}</span>
          </span>
        </label>
      </div>

      <div v-if="selectedFeedbackVersion" class="feedback-request">
        <div>
          <p>
            피드백 대상:
            <strong>답변 버전 {{ selectedFeedbackVersion.versionNo }}</strong>
          </p>
          <small>새 답변을 저장해도 이 버전의 피드백은 이곳에 그대로 남아요.</small>
        </div>
        <label class="field">
          <span class="field__label">피드백 품질</span>
          <select v-model="feedbackQuality" class="control control--compact">
            <option value="ECONOMY">경제적</option>
            <option value="BALANCED">균형</option>
            <option value="HIGH_QUALITY">고품질 · 설정과 예산 허용 시</option>
          </select>
        </label>
        <button
          type="button"
          class="button button--secondary"
          :disabled="
            feedbackMutation.isPending.value ||
            feedbackRunIsActive ||
            activeRuns.isLoading.value ||
            activeRuns.isError.value
          "
          :data-testid="`request-interview-feedback-${question.id}`"
          @click="requestFeedback"
        >
          {{ feedbackMutation.isPending.value ? '접수 중…' : '이 버전에 AI 피드백 요청' }}
        </button>
      </div>

      <InterviewRunMonitor
        v-if="monitoredFeedbackRunId && monitoredFeedbackVersionId"
        :user-id="userId"
        :agent-run-id="monitoredFeedbackRunId"
        workflow-type="INTERVIEW_ANSWER_FEEDBACK"
        resource-type="INTERVIEW_ANSWER_VERSION"
        :resource-id="monitoredFeedbackVersionId"
        @terminal="refreshFeedbackAfterRun"
        @unavailable="refreshFeedbackAfterRun"
      />

      <div v-if="selectedFeedbackVersionId" class="feedback-history">
        <p v-if="feedbacks.isLoading.value">성공한 피드백을 불러오는 중…</p>
        <p v-else-if="feedbacks.isError.value" class="alert alert--danger">
          피드백 이력을 불러오지 못했어요.
        </p>
        <p v-else-if="feedbacks.data.value?.items.length === 0" class="empty-copy">
          이 답변 버전에 성공한 피드백이 아직 없어요. 실패·취소된 요청은 이력에 포함되지 않습니다.
        </p>
        <template v-else>
          <article
            v-for="feedback in feedbacks.data.value?.items"
            :key="feedback.id"
            class="feedback-card"
            :data-testid="`interview-feedback-${feedback.id}`"
          >
            <header>
              <strong>답변 버전 {{ selectedFeedbackVersion?.versionNo }} 피드백</strong>
              <span>{{ formatInterviewInstant(feedback.createdAt) }}</span>
            </header>
            <dl class="feedback-card__scores">
              <div v-for="score in feedback.scores" :key="score.criterion">
                <dt>{{ score.criterion }}</dt>
                <dd>{{ score.score }}점</dd>
                <p v-if="score.explanation">{{ score.explanation }}</p>
              </div>
            </dl>
            <div class="feedback-card__columns">
              <section v-if="feedback.strengths.length > 0">
                <h5>강점</h5>
                <ul>
                  <li v-for="item in feedback.strengths" :key="item">{{ item }}</li>
                </ul>
              </section>
              <section v-if="feedback.weaknesses.length > 0">
                <h5>보완점</h5>
                <ul>
                  <li v-for="item in feedback.weaknesses" :key="item">{{ item }}</li>
                </ul>
              </section>
              <section v-if="feedback.suggestions.length > 0">
                <h5>개선 제안</h5>
                <ul>
                  <li v-for="item in feedback.suggestions" :key="item">{{ item }}</li>
                </ul>
              </section>
            </div>
            <section v-if="feedback.revisedExample" class="feedback-card__example">
              <h5>개선 예시</h5>
              <p>{{ feedback.revisedExample }}</p>
            </section>
          </article>
        </template>
      </div>
    </section>
  </article>
</template>

<style scoped>
.interview-question {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-5);
  box-shadow: var(--shadow-sm);
}

.interview-question__header,
.interview-question__badges,
.interview-question__answer > header,
.interview-question__history > header,
.feedback-card > header,
.feedback-request,
.answer-conflict__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.interview-question h3 {
  margin-top: var(--space-1);
  font-size: 1.15rem;
  line-height: 1.55;
}

.interview-question__badges,
.answer-conflict__actions {
  flex-wrap: wrap;
}

.interview-question__note,
.interview-question__guidance,
.interview-question__provenance,
.interview-question__answer,
.interview-question__history {
  margin-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.interview-question__note p,
.interview-question__guidance p,
.interview-question__provenance,
.feedback-request small,
.empty-copy {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.interview-question__guidance,
.feedback-card__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.interview-question ul,
.feedback-card ul {
  margin-top: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
}

.interview-question__provenance {
  display: grid;
  gap: var(--space-4);
}

.interview-question__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-left: 0 !important;
  list-style: none !important;
}

.interview-question__chips li {
  border-radius: 999px;
  background: var(--color-neutral-soft);
  padding: var(--space-1) var(--space-3);
}

.interview-question__source-links {
  padding-left: 0 !important;
  list-style: none !important;
}

.interview-question__source-links li {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding: var(--space-2) 0;
}

.interview-question__source-links a {
  min-width: 0;
  color: var(--color-brand);
  overflow-wrap: anywhere;
}

.interview-question__textarea {
  min-height: 11rem;
  resize: vertical;
  line-height: 1.65;
}

.interview-question__answer > .button {
  margin-top: var(--space-3);
}

.answer-conflict {
  margin-top: var(--space-4);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  padding: var(--space-4);
}

.answer-conflict > p {
  margin-top: var(--space-2);
  color: var(--color-warning-strong);
}

.answer-conflict__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.answer-conflict__comparison section {
  min-width: 0;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-3);
}

.answer-conflict pre {
  max-height: 16rem;
  margin: var(--space-2) 0;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.answer-conflict__actions {
  justify-content: flex-start;
  margin-top: var(--space-3);
}

.answer-history {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.answer-history__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  cursor: pointer;
}

.answer-history__item--selected {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.answer-history__item > span {
  display: grid;
  min-width: 0;
  gap: var(--space-1);
}

.answer-history__item small {
  color: var(--color-text-muted);
}

.answer-history__item > span > span {
  max-height: 5rem;
  overflow: hidden;
  white-space: pre-wrap;
}

.feedback-request {
  align-items: end;
  margin-top: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.feedback-history {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.feedback-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.feedback-card > header span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.feedback-card__scores {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.feedback-card__scores div {
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.feedback-card__scores dd {
  margin-top: var(--space-1);
  font-weight: 800;
}

.feedback-card__scores p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.feedback-card__columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--space-4);
}

.feedback-card__example {
  margin-top: var(--space-4);
  border-left: 3px solid var(--color-brand);
  background: var(--color-brand-soft);
  padding: var(--space-3);
}

.feedback-card__example p {
  margin-top: var(--space-2);
  white-space: pre-wrap;
}

@media (max-width: 48rem) {
  .interview-question__guidance,
  .feedback-card__columns,
  .answer-conflict__comparison {
    grid-template-columns: 1fr;
  }

  .feedback-request {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 40rem) {
  .interview-question {
    padding: var(--space-4);
  }

  .interview-question__header,
  .interview-question__answer > header,
  .feedback-card > header {
    align-items: stretch;
    flex-direction: column;
  }

  .interview-question__source-links li {
    align-items: flex-start;
    flex-direction: column;
  }

  .interview-question__answer > .button,
  .answer-conflict__actions .button,
  .feedback-request .button {
    width: 100%;
  }
}
</style>
