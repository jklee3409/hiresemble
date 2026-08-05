<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import CoverLetterConflictPanel from '@/features/cover-letters/CoverLetterConflictPanel.vue'
import type {
  CoverLetterConflict,
  CoverLetterConflictKind,
} from '@/features/cover-letters/conflict'
import CoverLetterRunMonitor from '@/features/cover-letters/CoverLetterRunMonitor.vue'
import CoverLetterTipTapEditor from '@/features/cover-letters/CoverLetterTipTapEditor.vue'
import {
  type CoverLetterDraftCandidate,
  findCoverLetterDraft,
  removeCoverLetterDrafts,
  removeCoverLetterQuestionDrafts,
  saveCoverLetterDraft,
} from '@/features/cover-letters/drafts'
import {
  EMPTY_TIPTAP_DOCUMENT,
  canonicalizeEditorContent,
  sameTipTapContent,
} from '@/features/cover-letters/editorContent'
import {
  ANSWER_SOURCE_LABELS,
  COVER_LETTER_STATUS_LABELS,
  ISSUE_CODE_LABELS,
  ISSUE_SEVERITY_LABELS,
  VERIFICATION_STATUS_LABELS,
  coverLetterJobLabel,
  evidenceCurrentState,
  formatCoverLetterInstant,
} from '@/features/cover-letters/presentation'
import {
  invalidateCoverLetterQueries,
  useAnswerVersionListQuery,
  useArchiveCoverLetterMutation,
  useCoverLetterDetailQuery,
  useCreateQuestionMutation,
  useDeleteQuestionMutation,
  useFinalizeCoverLetterMutation,
  useGenerateCoverLetterMutation,
  useLatestCoverLetterRunQuery,
  useReorderQuestionsMutation,
  useRestoreAnswerVersionMutation,
  useSaveAnswerVersionMutation,
  useUnarchiveCoverLetterMutation,
  useUpdateCoverLetterMutation,
  useUpdateQuestionMutation,
  useVerificationListQuery,
  useVerifyAnswerVersionMutation,
} from '@/features/cover-letters/queries'
import { useJobDetailQuery, useLatestJobAnalysisQuery } from '@/features/jobs/queries'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import type { AgentRunDetailDto, AiQualityMode } from '@/shared/api/agentRunContracts'
import type { EvidenceDto } from '@/shared/api/contracts'
import type {
  CoverLetterAnswerVersionDto,
  CoverLetterDetailDto,
  CoverLetterQuestionDto,
  TipTapDocumentDto,
  VerificationDto,
} from '@/shared/api/coverLetterContracts'
import { normalizeApiError, type ApiClientError } from '@/shared/api/errors'
import { listEvidence } from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

type EditorExpose = { insertSuggestion(suggestion: string): void }
type ConflictRetry = () => Promise<void>
type ConflictCancel = () => void

interface ConflictResolution {
  localSnapshot: string
  serverSnapshot: (latest: CoverLetterDetailDto | null) => string
  retry: (latest: CoverLetterDetailDto) => Promise<void>
  cancel: (latest: CoverLetterDetailDto | null) => void
}

interface TitleMutationSnapshot {
  readonly title: string
  readonly baseVersion: number
}

interface QuestionCreateSnapshot {
  readonly questionOrder: number
  readonly questionText: string
  readonly maxLength: number | null
  readonly memo: string | null
  readonly baseCoverLetterVersion: number
}

interface QuestionEditSnapshot extends QuestionCreateSnapshot {
  readonly questionId: string
  readonly baseQuestionVersion: number
}

interface QuestionDeleteSnapshot {
  readonly questionId: string
  readonly questionText: string
  readonly baseQuestionVersion: number
}

interface QuestionOrderEntrySnapshot {
  readonly id: string
  readonly questionOrder: number
  readonly questionText: string
}

interface QuestionOrderSnapshot {
  readonly questionIds: readonly string[]
  readonly questions: readonly QuestionOrderEntrySnapshot[]
  readonly baseCoverLetterVersion: number
}

interface AnswerSaveSnapshot {
  readonly questionId: string
  readonly contentJson: TipTapDocumentDto
  readonly plainText: string
  readonly characterCount: number
  readonly baseVersionId: string | null
}

interface AnswerRestoreSnapshot {
  readonly questionId: string
  readonly versionId: string
  readonly versionNo: number
  readonly plainText: string
  readonly baseVersionId: string | null
}

interface GenerationSnapshot {
  readonly questionIds: readonly string[]
  readonly preferredEvidenceIds: readonly string[]
  readonly qualityMode: AiQualityMode
  readonly avoidExperienceDuplication: boolean
  readonly baseCoverLetterVersion: number
}

interface VerificationSnapshot {
  readonly questionId: string
  readonly versionId: string
  readonly qualityMode: AiQualityMode
}

interface FinalizeSnapshot {
  readonly acknowledgedWarningVerificationIds: readonly string[]
  readonly baseCoverLetterVersion: number
}

interface LifecycleSnapshot {
  readonly action: string
  readonly baseCoverLetterVersion: number
}

type StatusTone = 'neutral' | 'brand' | 'info' | 'success' | 'warning' | 'danger'
type JourneyState = 'done' | 'active' | 'pending'
type CoachActionKind =
  'ADD_QUESTION' | 'SAVE_ANSWER' | 'GENERATE' | 'VERIFY' | 'FINALIZE' | 'UNARCHIVE' | 'NONE'

interface JourneyStep {
  readonly key: string
  readonly order: number
  readonly label: string
  readonly hint: string
  readonly state: JourneyState
}

interface CoachAction {
  readonly title: string
  readonly description: string
  readonly actionLabel: string
  readonly kind: CoachActionKind
  readonly disabled: boolean
  readonly note: string
}

const route = useRoute()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const coverLetterId = computed(() => String(route.params.coverLetterId ?? ''))
const coverLetter = useCoverLetterDetailQuery(userId, coverLetterId)
const jobId = computed(() => coverLetter.data.value?.job.id ?? '')
const job = useJobDetailQuery(userId, jobId)
const analysis = useLatestJobAnalysisQuery(userId, jobId)
const evidence = useQuery({
  queryKey: computed(() => [
    ...profileQueryKeys.evidenceRoot(userId.value),
    { verificationStatus: 'VERIFIED', page: 0, size: 100, sort: 'updatedAt,desc' },
  ]),
  queryFn: () =>
    listEvidence({
      verificationStatus: 'VERIFIED',
      page: 0,
      size: 100,
      sort: 'updatedAt,desc',
    }),
  enabled: computed(() => userId.value !== ''),
})

const updateCoverMutation = useUpdateCoverLetterMutation(userId)
const createQuestionMutation = useCreateQuestionMutation(userId)
const updateQuestionMutation = useUpdateQuestionMutation(userId)
const deleteQuestionMutation = useDeleteQuestionMutation(userId)
const reorderMutation = useReorderQuestionsMutation(userId)
const generateMutation = useGenerateCoverLetterMutation(userId)
const saveVersionMutation = useSaveAnswerVersionMutation(userId)
const restoreMutation = useRestoreAnswerVersionMutation(userId)
const verifyMutation = useVerifyAnswerVersionMutation(userId)
const finalizeMutation = useFinalizeCoverLetterMutation(userId)
const archiveMutation = useArchiveCoverLetterMutation(userId)
const unarchiveMutation = useUnarchiveCoverLetterMutation(userId)
const latestRun = useLatestCoverLetterRunQuery(userId, coverLetterId)

const selectedQuestionId = ref('')
const titleDraft = ref('')
const renamingTitle = ref(false)
const newQuestionInput = ref<HTMLTextAreaElement | null>(null)
const questionTextDraft = ref('')
const questionMaxLengthDraft = ref<string | number>('')
const questionMemoDraft = ref('')
const addingQuestion = ref(false)
const newQuestionText = ref('')
const newQuestionMaxLength = ref<string | number>('')
const newQuestionMemo = ref('')
const deleteConfirmationId = ref('')
const editorContent = ref<TipTapDocumentDto>(structuredClone(EMPTY_TIPTAP_DOCUMENT))
const editorCharacterCount = ref(0)
const editorDirty = ref(false)
const editorRef = ref<EditorExpose | null>(null)
const draftCandidate = ref<CoverLetterDraftCandidate | null>(null)
const draftNotice = ref('')
const selectedVersionId = ref('')
const selectedEvidenceIds = ref(new Set<string>())
const generationQuestionIds = ref(new Set<string>())
const qualityMode = ref<AiQualityMode>('BALANCED')
const avoidExperienceDuplication = ref(true)
const acceptedRunId = ref('')
const warningAcknowledgements = ref(new Set<string>())
const actionError = ref('')
const statusMessage = ref('')
const conflict = ref<CoverLetterConflict | null>(null)
const conflictRetry = ref<ConflictRetry | null>(null)
const conflictCancel = ref<ConflictCancel | null>(null)
const conflictReapplying = ref(false)

const activeQuestions = computed(() =>
  [...(coverLetter.data.value?.questions ?? [])]
    .filter((question) => question.deletedAt === null)
    .sort((left, right) => left.questionOrder - right.questionOrder),
)
const selectedQuestion = computed(
  () => activeQuestions.value.find((question) => question.id === selectedQuestionId.value) ?? null,
)
const readOnly = computed(() => coverLetter.data.value?.status === 'ARCHIVED')
const versionFilters = computed(() => ({
  page: 0,
  size: 100,
  sort: 'versionNo,desc' as const,
}))
const versions = useAnswerVersionListQuery(userId, selectedQuestionId, versionFilters)
const selectedVersion = computed(
  () =>
    versions.data.value?.items.find((version) => version.id === selectedVersionId.value) ??
    selectedQuestion.value?.currentAnswer ??
    null,
)
const verificationVersionId = computed(() => selectedVersion.value?.id ?? '')
const verifications = useVerificationListQuery(
  userId,
  verificationVersionId,
  computed(() => ({ page: 0, size: 100, sort: 'createdAt,desc' as const })),
)
const currentRunId = computed(() => acceptedRunId.value || latestRun.data.value?.items[0]?.id || '')
const coverLetterRunActive = computed(() =>
  ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(latestRun.data.value?.items[0]?.status ?? ''),
)
const aiActionUnavailable = computed(
  () =>
    generateMutation.isPending.value ||
    verifyMutation.isPending.value ||
    acceptedRunId.value !== '' ||
    coverLetterRunActive.value ||
    latestRun.isLoading.value ||
    latestRun.isError.value,
)
const questionLabels = computed<Record<string, string>>(() =>
  Object.fromEntries(
    activeQuestions.value.map((question) => [
      question.id,
      `${question.questionOrder}. ${question.questionText}`,
    ]),
  ),
)

const jobLabel = computed(() =>
  coverLetter.data.value ? coverLetterJobLabel(coverLetter.data.value.job) : '등록 공고',
)
const questionCount = computed(() => activeQuestions.value.length)
const answeredQuestions = computed(() =>
  activeQuestions.value.filter((question) => question.currentAnswer !== null),
)
const unansweredQuestions = computed(() =>
  activeQuestions.value.filter((question) => question.currentAnswer === null),
)
const refinedCount = computed(
  () =>
    activeQuestions.value.filter((question) =>
      ['USER_EDITED', 'RESTORED', 'AI_REVISED'].includes(question.currentAnswer?.sourceType ?? ''),
    ).length,
)
const reviewedCount = computed(
  () =>
    activeQuestions.value.filter(
      (question) =>
        question.latestVerification !== null && question.latestVerification.status !== 'PENDING',
    ).length,
)
const answeredPercent = computed(() =>
  questionCount.value === 0
    ? 0
    : Math.round((answeredQuestions.value.length / questionCount.value) * 100),
)
const pendingVerificationQuestion = computed(
  () =>
    activeQuestions.value.find(
      (question) =>
        question.currentAnswer !== null &&
        (question.latestVerification === null ||
          question.latestVerification.status === 'FAILED' ||
          question.latestVerification.answerVersionId !== question.currentAnswer.id),
    ) ?? null,
)

const requirementHighlights = computed(() => {
  const detail = analysis.data.value
  if (!detail) return []
  return [...(detail.requiredQualifications ?? []), ...(detail.responsibilities ?? [])].slice(0, 6)
})
const analysisStrengths = computed(() => analysis.data.value?.strengths ?? [])
const analysisGaps = computed(() => analysis.data.value?.gaps ?? [])
const recommendedEvidenceIds = computed(
  () => new Set((analysis.data.value?.matchedEvidenceRefs ?? []).map((item) => item.id)),
)
const evidenceItems = computed(() =>
  [...(evidence.data.value?.items ?? [])].sort(
    (left, right) =>
      (recommendedEvidenceIds.value.has(left.id) ? 0 : 1) -
      (recommendedEvidenceIds.value.has(right.id) ? 0 : 1),
  ),
)
const unselectedRecommendedIds = computed(() =>
  evidenceItems.value
    .filter(
      (item) =>
        recommendedEvidenceIds.value.has(item.id) && !selectedEvidenceIds.value.has(item.id),
    )
    .map((item) => item.id),
)

const answerLengthPercent = computed(() => {
  const limit = selectedQuestion.value?.maxLength ?? null
  if (limit === null || limit === 0) return null
  return Math.min(100, Math.round((editorCharacterCount.value / limit) * 100))
})

const journeySteps = computed<JourneyStep[]>(() => {
  const status = coverLetter.data.value?.status ?? 'DRAFT'
  const total = questionCount.value
  const answered = answeredQuestions.value.length
  const raw = [
    {
      key: 'questions',
      label: '문항 등록',
      hint: total === 0 ? '아직 없어요' : `${total}개 등록`,
      done: total > 0,
    },
    {
      key: 'materials',
      label: '강점·소재 고르기',
      hint:
        selectedEvidenceIds.value.size > 0
          ? `${selectedEvidenceIds.value.size}개 선택`
          : answered > 0
            ? '승인된 경험 전체 사용'
            : '선택 전이에요',
      done: total > 0 && (selectedEvidenceIds.value.size > 0 || answered > 0),
    },
    {
      key: 'draft',
      label: 'AI 초안 받기',
      hint: total === 0 ? '문항 등록 후' : `${answered}/${total} 작성`,
      done: total > 0 && answered === total,
    },
    {
      key: 'refine',
      label: '내 문장으로 다듬기',
      hint: refinedCount.value > 0 ? `${refinedCount.value}개 수정` : '수정 전이에요',
      done: total > 0 && refinedCount.value >= total,
    },
    {
      key: 'finalize',
      label: '검증하고 최종화',
      hint:
        status === 'FINALIZED'
          ? '최종화 완료'
          : total === 0
            ? '검증 전'
            : `검증 ${reviewedCount.value}/${total}`,
      done: status === 'FINALIZED',
    },
  ]
  // 앞 단계를 건너뛴 채 뒤 단계만 완료로 보이지 않도록 순서대로 누적 판정한다.
  let blocked = false
  const resolved = raw.map((step) => {
    const done = !blocked && step.done
    if (!done) blocked = true
    return { ...step, done }
  })
  const activeIndex = resolved.findIndex((step) => !step.done)
  return resolved.map((step, index) => ({
    key: step.key,
    order: index + 1,
    label: step.label,
    hint: step.hint,
    state: step.done ? 'done' : index === activeIndex ? 'active' : 'pending',
  }))
})

const coachAction = computed<CoachAction>(() => {
  const detail = coverLetter.data.value
  if (!detail) {
    return coach('자기소개서를 준비하고 있어요.', '잠시만 기다려 주세요.', '', 'NONE')
  }
  if (detail.status === 'ARCHIVED') {
    return coach(
      '보관된 자기소개서를 함께 보고 있어요.',
      '문항, 답변 버전과 검증 기록은 그대로 남아 있어요. 다시 쓰려면 DRAFT로 되돌려 주세요.',
      detail.canUnarchive ? 'DRAFT로 되돌리기' : '',
      detail.canUnarchive ? 'UNARCHIVE' : 'NONE',
      { disabled: unarchiveMutation.isPending.value },
    )
  }
  if (questionCount.value === 0) {
    return coach(
      '먼저 지원서에 답할 문항을 알려 주세요.',
      '공고에서 요구하는 문항을 그대로 옮겨 적으면, 제가 공고 분석과 승인된 경험을 연결해 초안을 만들어 드릴게요.',
      '첫 문항 등록하기',
      'ADD_QUESTION',
    )
  }
  if (editorDirty.value) {
    return coach(
      '지금 쓰고 있는 답변이 아직 서버에 저장되지 않았어요.',
      editorOverLimit.value
        ? '최대 글자 수를 넘었어요. 분량을 줄이면 새 버전으로 저장할 수 있어요.'
        : '새 버전으로 저장하면 이전 버전은 그대로 보존되고, 언제든 되돌릴 수 있어요.',
      '새 버전으로 저장',
      'SAVE_ANSWER',
      { disabled: editorOverLimit.value || saveVersionMutation.isPending.value },
    )
  }
  if (unansweredQuestions.value.length > 0) {
    return coach(
      `아직 답변이 없는 문항이 ${unansweredQuestions.value.length}개 있어요.`,
      selectedEvidenceIds.value.size > 0
        ? `선택한 경험 ${selectedEvidenceIds.value.size}개를 근거로 초안을 만들어 볼게요. 초안은 그대로 제출하지 말고 꼭 직접 다듬어 주세요.`
        : '오른쪽에서 쓸 경험을 고르면 근거가 분명한 초안을 만들 수 있어요. 고르지 않아도 승인된 경험 전체를 참고해요.',
      '남은 문항 초안 만들기',
      'GENERATE',
      { disabled: aiActionUnavailable.value, note: aiRunNote() },
    )
  }
  if (pendingVerificationQuestion.value) {
    return coach(
      '초안이 모두 준비됐어요. 근거를 확인해 볼까요?',
      `${pendingVerificationQuestion.value.questionOrder}번 문항의 현재 버전이 아직 검증되지 않았어요. 검증하면 근거 없는 문장과 요구 누락을 함께 확인해 드려요.`,
      '이 문항 검증하기',
      'VERIFY',
      { disabled: aiActionUnavailable.value, note: aiRunNote() },
    )
  }
  if (detail.status === 'FINALIZED') {
    return coach(
      '최종본이 준비됐어요.',
      '문항이나 답변을 다시 수정하면 작성 중 상태로 돌아가고, 바뀐 답변은 다시 검증해야 해요.',
      '',
      'NONE',
    )
  }
  if (finalizeBlockers.value.length > 0) {
    return coach(
      '최종화까지 확인할 것이 조금 남았어요.',
      finalizeBlockers.value[0] ?? '아래 최종화 확인 목록을 살펴봐 주세요.',
      '',
      'NONE',
    )
  }
  return coach(
    '모든 문항의 답변과 검증이 준비됐어요.',
    '최종화하면 이 자기소개서를 제출 기준본으로 표시해요. 공고의 제출 상태는 따로 변경해야 해요.',
    '자기소개서 최종화',
    'FINALIZE',
    { disabled: !canFinalizeNow.value || finalizeMutation.isPending.value },
  )
})

function coach(
  title: string,
  description: string,
  actionLabel: string,
  kind: CoachActionKind,
  options: { disabled?: boolean; note?: string } = {},
): CoachAction {
  return {
    title,
    description,
    actionLabel,
    kind,
    disabled: options.disabled ?? false,
    note: options.note ?? '',
  }
}

function aiRunNote(): string {
  if (latestRun.isError.value)
    return 'AI 작업 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.'
  return aiActionUnavailable.value ? '진행 중인 AI 작업이 끝나면 이어서 요청할 수 있어요.' : ''
}

async function runCoachAction(): Promise<void> {
  const action = coachAction.value
  if (action.disabled || action.kind === 'NONE') return
  if (action.kind === 'ADD_QUESTION') {
    addingQuestion.value = true
    await nextTick()
    newQuestionInput.value?.focus()
    return
  }
  if (action.kind === 'SAVE_ANSWER') {
    await saveAnswer()
    return
  }
  if (action.kind === 'GENERATE') {
    generationQuestionIds.value = new Set(unansweredQuestions.value.map((question) => question.id))
    await generateAnswers()
    return
  }
  if (action.kind === 'VERIFY') {
    const target = pendingVerificationQuestion.value
    if (!target) return
    selectedQuestionId.value = target.id
    await nextTick()
    await verifyCurrentAnswer()
    return
  }
  if (action.kind === 'FINALIZE') {
    await finalizeCover()
    return
  }
  await unarchiveCover()
}

function selectRecommendedEvidence(): void {
  if (readOnly.value) return
  const next = new Set(selectedEvidenceIds.value)
  unselectedRecommendedIds.value.forEach((id) => next.add(id))
  selectedEvidenceIds.value = next
}

function clearSelectedEvidence(): void {
  if (readOnly.value) return
  selectedEvidenceIds.value = new Set<string>()
}

function startRenaming(): void {
  titleDraft.value = coverLetter.data.value?.title ?? ''
  renamingTitle.value = true
}

async function submitTitleRename(): Promise<void> {
  await saveTitle()
  if (conflict.value === null && actionError.value === '') renamingTitle.value = false
}

function cancelRenaming(): void {
  titleDraft.value = coverLetter.data.value?.title ?? ''
  renamingTitle.value = false
}

function questionStatus(question: CoverLetterQuestionDto): { label: string; tone: StatusTone } {
  const verification = question.latestVerification
  if (verification !== null && question.currentAnswer !== null) {
    if (verification.answerVersionId === question.currentAnswer.id) {
      return {
        label: VERIFICATION_STATUS_LABELS[verification.status],
        tone: verificationTone(verification.status),
      }
    }
  }
  const answer = question.currentAnswer
  if (!answer) return { label: '답변 대기', tone: 'neutral' }
  return {
    label: ANSWER_SOURCE_LABELS[answer.sourceType],
    tone: answer.sourceType === 'AI_GENERATED' ? 'brand' : 'info',
  }
}

function questionLengthLabel(question: CoverLetterQuestionDto): string {
  const count = question.currentAnswer?.characterCount ?? 0
  return question.maxLength === null ? `${count}자` : `${count} / ${question.maxLength}자`
}

function evidenceSnippet(item: EvidenceDto): string {
  const content = (item.content ?? '').replace(/\s+/g, ' ').trim()
  return content.length > 90 ? `${content.slice(0, 90)}…` : content
}

function applyLengthPreset(value: number): void {
  newQuestionMaxLength.value = String(value)
}

watch(
  () => coverLetter.data.value,
  (detail) => {
    if (!detail) return
    if (titleDraft.value === '' || titleDraft.value === detail.title) {
      titleDraft.value = detail.title
    }
    if (
      selectedQuestionId.value === '' ||
      !detail.questions.some(
        (question) => question.id === selectedQuestionId.value && question.deletedAt === null,
      )
    ) {
      selectedQuestionId.value =
        detail.questions
          .filter((question) => question.deletedAt === null)
          .sort((left, right) => left.questionOrder - right.questionOrder)[0]?.id ?? ''
    }
    const available = new Set(
      detail.questions
        .filter((question) => question.deletedAt === null)
        .map((question) => question.id),
    )
    const nextSelected = new Set([...generationQuestionIds.value].filter((id) => available.has(id)))
    if (nextSelected.size === 0) {
      available.forEach((id) => nextSelected.add(id))
    }
    generationQuestionIds.value = nextSelected
  },
  { immediate: true },
)

watch(
  [selectedQuestionId, () => selectedQuestion.value?.version],
  () => {
    const question = selectedQuestion.value
    if (!question) {
      editorContent.value = structuredClone(EMPTY_TIPTAP_DOCUMENT)
      editorCharacterCount.value = 0
      editorDirty.value = false
      draftCandidate.value = null
      return
    }
    questionTextDraft.value = question.questionText
    questionMaxLengthDraft.value = question.maxLength === null ? '' : String(question.maxLength)
    questionMemoDraft.value = question.memo ?? ''
    editorContent.value =
      question.currentAnswer?.contentJson ?? structuredClone(EMPTY_TIPTAP_DOCUMENT)
    editorCharacterCount.value = question.currentAnswer?.characterCount ?? 0
    editorDirty.value = false
    selectedVersionId.value = question.currentAnswer?.id ?? ''
    draftCandidate.value = findCoverLetterDraft({
      userId: userId.value,
      coverLetterId: coverLetterId.value,
      questionId: question.id,
      currentBaseVersionId: question.currentAnswer?.id ?? null,
    })
    draftNotice.value = draftCandidate.value
      ? draftCandidate.value.baseMatches
        ? '이 브라우저 세션에 저장하지 않은 답변이 있어요.'
        : '브라우저 임시 저장의 기준 버전이 서버의 현재 버전과 달라요.'
      : ''
  },
  { immediate: true },
)

watch(
  () => versions.data.value?.items,
  (items) => {
    if (!items || items.length === 0) return
    if (!items.some((version) => version.id === selectedVersionId.value)) {
      selectedVersionId.value = selectedQuestion.value?.currentAnswer?.id ?? items[0]?.id ?? ''
    }
  },
)

const titleDirty = computed(
  () => coverLetter.data.value !== undefined && titleDraft.value !== coverLetter.data.value.title,
)
const selectedServerContent = computed(
  () => selectedQuestion.value?.currentAnswer?.contentJson ?? EMPTY_TIPTAP_DOCUMENT,
)
const selectedServerCount = computed(
  () => selectedQuestion.value?.currentAnswer?.characterCount ?? 0,
)
const editorOverLimit = computed(
  () =>
    selectedQuestion.value?.maxLength !== null &&
    selectedQuestion.value?.maxLength !== undefined &&
    editorCharacterCount.value > selectedQuestion.value.maxLength,
)
const warningVerificationIds = computed(() =>
  activeQuestions.value
    .map((question) => question.latestVerification)
    .filter(
      (verification): verification is VerificationDto =>
        verification !== null && verification.status === 'WARNING',
    )
    .map((verification) => verification.id),
)
const finalizeBlockers = computed(() => {
  const detail = coverLetter.data.value
  if (!detail) return ['자기소개서 정보를 확인하는 중이에요.']
  if (detail.status === 'ARCHIVED') return ['보관된 자기소개서는 최종화할 수 없어요.']
  if (detail.status === 'FINALIZED') return []
  const blockers: string[] = []
  if (activeQuestions.value.length === 0) blockers.push('문항을 하나 이상 등록해 주세요.')
  for (const question of activeQuestions.value) {
    if (!question.currentAnswer) {
      blockers.push(`${question.questionOrder}번 문항의 답변을 저장해 주세요.`)
      continue
    }
    if (question.maxLength !== null && question.currentAnswer.characterCount > question.maxLength) {
      blockers.push(`${question.questionOrder}번 문항의 최대 글자 수를 맞춰 주세요.`)
    }
    const verification = question.latestVerification
    if (!verification) blockers.push(`${question.questionOrder}번 문항을 검증해 주세요.`)
    else if (verification.status === 'PENDING') {
      blockers.push(`${question.questionOrder}번 문항 검증이 진행 중이에요.`)
    } else if (verification.status === 'FAILED') {
      blockers.push(`${question.questionOrder}번 문항을 수정하고 다시 검증해 주세요.`)
    } else if (
      verification.status === 'WARNING' &&
      !warningAcknowledgements.value.has(verification.id)
    ) {
      blockers.push(`${question.questionOrder}번 문항의 경고를 확인해 주세요.`)
    }
  }
  return blockers
})
const canFinalizeNow = computed(
  () =>
    coverLetter.data.value?.status === 'DRAFT' &&
    coverLetter.data.value.canFinalize &&
    finalizeBlockers.value.length === 0,
)

function onEditorUpdate(content: TipTapDocumentDto, characterCount: number): void {
  editorContent.value = content
  editorCharacterCount.value = characterCount
  editorDirty.value = !sameTipTapContent(content, selectedServerContent.value)
  const question = selectedQuestion.value
  if (!question || readOnly.value) return
  if (!editorDirty.value) {
    removeCoverLetterQuestionDrafts({
      userId: userId.value,
      coverLetterId: coverLetterId.value,
      questionId: question.id,
    })
    return
  }
  saveCoverLetterDraft({
    userId: userId.value,
    coverLetterId: coverLetterId.value,
    questionId: question.id,
    baseVersionId: question.currentAnswer?.id ?? null,
    contentJson: content,
  })
  draftNotice.value = '이 브라우저 세션에 임시 저장했어요. 서버 버전은 아직 만들지 않았어요.'
}

function applyDraftCandidate(): void {
  if (!draftCandidate.value) return
  const canonical = canonicalizeEditorContent(draftCandidate.value.contentJson)
  editorContent.value = canonical.document
  editorCharacterCount.value = canonical.characterCount
  editorDirty.value = !sameTipTapContent(canonical.document, selectedServerContent.value)
  draftNotice.value = draftCandidate.value.baseMatches
    ? '임시 저장 내용을 편집기에 적용했어요. 버전 저장 전까지 서버에는 반영되지 않아요.'
    : '기준 버전이 다른 임시 저장 내용을 재적용했어요. 서버 저장 전에 내용을 확인하세요.'
  draftCandidate.value = null
}

function discardDraftCandidate(): void {
  const question = selectedQuestion.value
  if (!question) return
  removeCoverLetterQuestionDrafts({
    userId: userId.value,
    coverLetterId: coverLetterId.value,
    questionId: question.id,
  })
  draftCandidate.value = null
  draftNotice.value = '브라우저 임시 저장을 폐기하고 서버 버전을 유지했어요.'
}

async function saveTitle(): Promise<void> {
  const detail = coverLetter.data.value
  const title = titleDraft.value.trim()
  if (!detail || readOnly.value || title.length === 0) return
  const snapshot: TitleMutationSnapshot = Object.freeze({
    title,
    baseVersion: detail.version,
  })
  await submitTitle(snapshot, detail.version)
}

async function submitTitle(
  snapshot: TitleMutationSnapshot,
  expectedVersion: number,
): Promise<void> {
  clearMessages()
  try {
    const result = await updateCoverMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      request: {
        title: snapshot.title,
        version: expectedVersion,
      },
    })
    titleDraft.value = result.title
    statusMessage.value = '제목을 저장했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('TITLE', error, {
      localSnapshot: `제목: ${snapshot.title}\n기준 자기소개서 version: ${snapshot.baseVersion}`,
      serverSnapshot: (latest) => formatCoverSnapshot(latest),
      retry: async (latest) => submitTitle(snapshot, latest.version),
      cancel: (latest) => {
        if (latest) titleDraft.value = latest.title
      },
    })
  }
}

async function addQuestion(): Promise<void> {
  const detail = coverLetter.data.value
  const questionText = newQuestionText.value.trim()
  if (!detail || readOnly.value || questionText.length === 0) return
  const snapshot: QuestionCreateSnapshot = Object.freeze({
    questionOrder: activeQuestions.value.length + 1,
    questionText,
    maxLength: parseOptionalInteger(newQuestionMaxLength.value),
    memo: newQuestionMemo.value.trim() || null,
    baseCoverLetterVersion: detail.version,
  })
  await submitQuestionCreate(snapshot, detail.version)
}

async function submitQuestionCreate(
  snapshot: QuestionCreateSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    const question = await createQuestionMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      request: {
        questionOrder: snapshot.questionOrder,
        questionText: snapshot.questionText,
        maxLength: snapshot.maxLength,
        memo: snapshot.memo,
        coverLetterVersion: expectedCoverLetterVersion,
      },
    })
    await coverLetter.refetch()
    selectedQuestionId.value = question.id
    newQuestionText.value = ''
    newQuestionMaxLength.value = ''
    newQuestionMemo.value = ''
    addingQuestion.value = false
    statusMessage.value = '문항을 추가했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('QUESTION', error, {
      localSnapshot: formatQuestionCreateSnapshot(snapshot),
      serverSnapshot: (latest) =>
        latest
          ? `현재 문항 목록\n${formatQuestionOrderSnapshot(latest.questions)}`
          : '최신 서버 문항을 찾지 못했어요.',
      retry: async (latest) => submitQuestionCreate(snapshot, latest.version),
      cancel: () => {
        newQuestionText.value = ''
        newQuestionMaxLength.value = ''
        newQuestionMemo.value = ''
        addingQuestion.value = false
      },
    })
  }
}

async function updateQuestion(): Promise<void> {
  const detail = coverLetter.data.value
  const question = selectedQuestion.value
  const questionText = questionTextDraft.value.trim()
  if (!detail || !question || readOnly.value || questionText.length === 0) return
  const snapshot: QuestionEditSnapshot = Object.freeze({
    questionId: question.id,
    questionOrder: question.questionOrder,
    questionText,
    maxLength: parseOptionalInteger(questionMaxLengthDraft.value),
    memo: questionMemoDraft.value.trim() || null,
    baseCoverLetterVersion: detail.version,
    baseQuestionVersion: question.version,
  })
  await submitQuestionUpdate(snapshot, question.version)
}

async function submitQuestionUpdate(
  snapshot: QuestionEditSnapshot,
  expectedQuestionVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await updateQuestionMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
      request: {
        questionOrder: snapshot.questionOrder,
        questionText: snapshot.questionText,
        maxLength: snapshot.maxLength,
        memo: snapshot.memo,
        version: expectedQuestionVersion,
      },
    })
    await coverLetter.refetch()
    statusMessage.value = '문항 정보를 저장했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('QUESTION', error, {
      localSnapshot: formatQuestionEditSnapshot(snapshot),
      serverSnapshot: (latest) =>
        formatQuestionSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async (latest) => {
        const latestQuestion = findActiveQuestion(latest, snapshot.questionId)
        if (!latestQuestion) {
          actionError.value = '최신 서버에서 이 문항을 찾지 못했어요.'
          return
        }
        await submitQuestionUpdate(snapshot, latestQuestion.version)
      },
      cancel: (latest) => syncQuestionFieldsFromServer(latest, snapshot.questionId),
    })
  }
}

async function removeQuestion(question: CoverLetterQuestionDto): Promise<void> {
  const detail = coverLetter.data.value
  if (!detail || readOnly.value) return
  const snapshot: QuestionDeleteSnapshot = Object.freeze({
    questionId: question.id,
    questionText: question.questionText,
    baseQuestionVersion: question.version,
  })
  await submitQuestionDelete(snapshot, question.version)
}

async function submitQuestionDelete(
  snapshot: QuestionDeleteSnapshot,
  expectedQuestionVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await deleteQuestionMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
      version: expectedQuestionVersion,
    })
    removeCoverLetterQuestionDrafts({
      userId: userId.value,
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
    })
    deleteConfirmationId.value = ''
    await coverLetter.refetch()
    statusMessage.value = '문항을 삭제했어요. 과거 버전과 검증 기록은 보존됩니다.'
    clearConflictState()
  } catch (error) {
    await handleConflict('QUESTION', error, {
      localSnapshot: `삭제할 문항\nID: ${snapshot.questionId}\n내용: ${snapshot.questionText}\n기준 문항 version: ${snapshot.baseQuestionVersion}`,
      serverSnapshot: (latest) =>
        formatQuestionSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async (latest) => {
        const latestQuestion = findActiveQuestion(latest, snapshot.questionId)
        if (!latestQuestion) {
          statusMessage.value = '문항이 이미 서버에서 삭제됐어요.'
          clearConflictState()
          return
        }
        await submitQuestionDelete(snapshot, latestQuestion.version)
      },
      cancel: (latest) => {
        deleteConfirmationId.value = ''
        syncQuestionFieldsFromServer(latest, snapshot.questionId)
      },
    })
  }
}

async function moveQuestion(questionId: string, direction: -1 | 1): Promise<void> {
  const detail = coverLetter.data.value
  if (!detail || readOnly.value) return
  const questionEntries = activeQuestions.value.map((question): QuestionOrderEntrySnapshot =>
    Object.freeze({
      id: question.id,
      questionOrder: question.questionOrder,
      questionText: question.questionText,
    }),
  )
  const ids = questionEntries.map((question) => question.id)
  const index = ids.indexOf(questionId)
  const target = index + direction
  if (index < 0 || target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
  const snapshot: QuestionOrderSnapshot = Object.freeze({
    questionIds: Object.freeze([...ids]),
    questions: Object.freeze(questionEntries),
    baseCoverLetterVersion: detail.version,
  })
  await submitQuestionOrder(snapshot, detail.version)
}

async function submitQuestionOrder(
  snapshot: QuestionOrderSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await reorderMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      request: {
        questionIds: [...snapshot.questionIds],
        version: expectedCoverLetterVersion,
      },
    })
    statusMessage.value = '문항 순서를 저장했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('ORDER', error, {
      localSnapshot: `내가 적용한 순서\n${formatSavedQuestionOrderSnapshot(snapshot)}\n기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
      serverSnapshot: (latest) =>
        latest
          ? `최신 서버 순서\n${formatQuestionOrderSnapshot(latest.questions)}\n자기소개서 version: ${latest.version}`
          : '최신 서버 문항 순서를 찾지 못했어요.',
      retry: async (latest) => submitQuestionOrder(snapshot, latest.version),
      cancel: () => undefined,
    })
  }
}

async function saveAnswer(): Promise<void> {
  const detail = coverLetter.data.value
  const question = selectedQuestion.value
  if (!detail || !question || readOnly.value || editorOverLimit.value) return
  const canonical = canonicalizeEditorContent(editorContent.value)
  const snapshot: AnswerSaveSnapshot = Object.freeze({
    questionId: question.id,
    contentJson: structuredClone(canonical.document),
    plainText: canonical.plainText,
    characterCount: canonical.characterCount,
    baseVersionId: question.currentAnswer?.id ?? null,
  })
  await submitAnswerSave(snapshot, snapshot.baseVersionId)
}

async function submitAnswerSave(
  snapshot: AnswerSaveSnapshot,
  expectedParentVersionId: string | null,
): Promise<void> {
  clearMessages()
  try {
    const answer = await saveVersionMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
      request: {
        contentJson: structuredClone(snapshot.contentJson),
        parentVersionId: expectedParentVersionId,
      },
    })
    removeCoverLetterQuestionDrafts({
      userId: userId.value,
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
    })
    await coverLetter.refetch()
    selectedVersionId.value = answer.id
    editorContent.value = structuredClone(snapshot.contentJson)
    editorCharacterCount.value = snapshot.characterCount
    editorDirty.value = false
    draftCandidate.value = null
    draftNotice.value = ''
    statusMessage.value = `버전 ${answer.versionNo}을 저장했어요. 검증은 별도로 실행해 주세요.`
    clearConflictState()
  } catch (error) {
    await handleConflict('ANSWER', error, {
      localSnapshot: formatAnswerSaveSnapshot(snapshot),
      serverSnapshot: (latest) =>
        formatAnswerSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async (latest) => {
        const latestQuestion = findActiveQuestion(latest, snapshot.questionId)
        if (!latestQuestion) {
          actionError.value = '최신 서버에서 이 문항을 찾지 못했어요.'
          return
        }
        await submitAnswerSave(snapshot, latestQuestion.currentAnswer?.id ?? null)
      },
      cancel: (latest) => syncAnswerFromServer(latest, snapshot.questionId),
    })
  }
}

async function restoreVersion(version: CoverLetterAnswerVersionDto): Promise<void> {
  const detail = coverLetter.data.value
  const question = selectedQuestion.value
  if (!detail || !question || readOnly.value) return
  const snapshot: AnswerRestoreSnapshot = Object.freeze({
    questionId: question.id,
    versionId: version.id,
    versionNo: version.versionNo,
    plainText: version.plainText,
    baseVersionId: question.currentAnswer?.id ?? null,
  })
  await submitAnswerRestore(snapshot, snapshot.baseVersionId)
}

async function submitAnswerRestore(
  snapshot: AnswerRestoreSnapshot,
  expectedCurrentVersionId: string | null,
): Promise<void> {
  clearMessages()
  try {
    const answer = await restoreMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
      versionId: snapshot.versionId,
      request: { expectedCurrentVersionId },
    })
    removeCoverLetterQuestionDrafts({
      userId: userId.value,
      coverLetterId: coverLetterId.value,
      questionId: snapshot.questionId,
    })
    await coverLetter.refetch()
    selectedVersionId.value = answer.id
    statusMessage.value = `버전 ${snapshot.versionNo}을 새 RESTORED 버전으로 복원했어요.`
    clearConflictState()
  } catch (error) {
    await handleConflict('ANSWER', error, {
      localSnapshot: formatAnswerRestoreSnapshot(snapshot),
      serverSnapshot: (latest) =>
        formatAnswerSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async (latest) => {
        const latestQuestion = findActiveQuestion(latest, snapshot.questionId)
        if (!latestQuestion) {
          actionError.value = '최신 서버에서 이 문항을 찾지 못했어요.'
          return
        }
        await submitAnswerRestore(snapshot, latestQuestion.currentAnswer?.id ?? null)
      },
      cancel: (latest) => syncAnswerFromServer(latest, snapshot.questionId),
    })
  }
}

async function generateAnswers(): Promise<void> {
  const detail = coverLetter.data.value
  if (
    !detail ||
    readOnly.value ||
    generationQuestionIds.value.size === 0 ||
    aiActionUnavailable.value
  )
    return
  const snapshot: GenerationSnapshot = Object.freeze({
    questionIds: Object.freeze([...generationQuestionIds.value]),
    preferredEvidenceIds: Object.freeze([...selectedEvidenceIds.value]),
    qualityMode: qualityMode.value,
    avoidExperienceDuplication: avoidExperienceDuplication.value,
    baseCoverLetterVersion: detail.version,
  })
  await submitGeneration(snapshot, detail.version)
}

async function submitGeneration(
  snapshot: GenerationSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    const accepted = await generateMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      request: {
        questionIds: [...snapshot.questionIds],
        preferredEvidenceIds: [...snapshot.preferredEvidenceIds],
        qualityMode: snapshot.qualityMode,
        avoidExperienceDuplication: snapshot.avoidExperienceDuplication,
        coverLetterVersion: expectedCoverLetterVersion,
      },
    })
    acceptedRunId.value = accepted.agentRunId
    statusMessage.value = 'AI 초안 생성을 접수했어요. 편집기는 계속 사용할 수 있어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('LIFECYCLE', error, {
      localSnapshot: formatGenerationSnapshot(snapshot),
      serverSnapshot: (latest) => formatCoverSnapshot(latest),
      retry: async (latest) => submitGeneration(snapshot, latest.version),
      cancel: () => undefined,
    })
  }
}

async function verifyCurrentAnswer(): Promise<void> {
  const detail = coverLetter.data.value
  const question = selectedQuestion.value
  const answer = question?.currentAnswer
  if (!detail || !question || !answer || readOnly.value || aiActionUnavailable.value) return
  const snapshot: VerificationSnapshot = Object.freeze({
    questionId: question.id,
    versionId: answer.id,
    qualityMode: qualityMode.value,
  })
  await submitVerification(snapshot)
}

async function submitVerification(snapshot: VerificationSnapshot): Promise<void> {
  clearMessages()
  try {
    const accepted = await verifyMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      versionId: snapshot.versionId,
      request: { qualityMode: snapshot.qualityMode },
    })
    acceptedRunId.value = accepted.agentRunId
    statusMessage.value = '현재 immutable 답변 버전의 검증을 접수했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('ANSWER', error, {
      localSnapshot: `검증할 답변 ID: ${snapshot.versionId}\n작성 방식: ${snapshot.qualityMode}`,
      serverSnapshot: (latest) =>
        formatAnswerSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async () => submitVerification(snapshot),
      cancel: (latest) => syncAnswerFromServer(latest, snapshot.questionId),
    })
  }
}

function applySuggestion(suggestion: string): void {
  if (readOnly.value) return
  editorRef.value?.insertSuggestion(suggestion)
  statusMessage.value = '제안을 편집기에 적용했어요. 확인 후 새 버전으로 저장해 주세요.'
}

function toggleEvidence(id: string): void {
  const next = new Set(selectedEvidenceIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedEvidenceIds.value = next
}

function toggleGenerationQuestion(id: string): void {
  const next = new Set(generationQuestionIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  generationQuestionIds.value = next
}

function toggleWarningAcknowledgement(id: string): void {
  const next = new Set(warningAcknowledgements.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  warningAcknowledgements.value = next
}

async function finalizeCover(): Promise<void> {
  const detail = coverLetter.data.value
  if (!detail || !canFinalizeNow.value) return
  const snapshot: FinalizeSnapshot = Object.freeze({
    acknowledgedWarningVerificationIds: Object.freeze(
      warningVerificationIds.value.filter((id) => warningAcknowledgements.value.has(id)),
    ),
    baseCoverLetterVersion: detail.version,
  })
  await submitFinalize(snapshot, detail.version)
}

async function submitFinalize(
  snapshot: FinalizeSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await finalizeMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      request: {
        version: expectedCoverLetterVersion,
        acknowledgedWarningVerificationIds: [...snapshot.acknowledgedWarningVerificationIds],
      },
    })
    statusMessage.value =
      '자기소개서를 최종화했어요. 공고의 제출 상태는 별도 사용자 행동으로 유지됩니다.'
    clearConflictState()
  } catch (error) {
    await handleConflict('LIFECYCLE', error, {
      localSnapshot: formatFinalizeSnapshot(snapshot),
      serverSnapshot: (latest) => formatCoverSnapshot(latest),
      retry: async (latest) => submitFinalize(snapshot, latest.version),
      cancel: () => undefined,
    })
  }
}

async function archiveCover(): Promise<void> {
  const detail = coverLetter.data.value
  if (!detail || !detail.canArchive) return
  const snapshot: LifecycleSnapshot = Object.freeze({
    action: '보관',
    baseCoverLetterVersion: detail.version,
  })
  await submitArchive(snapshot, detail.version)
}

async function submitArchive(
  snapshot: LifecycleSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await archiveMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      version: expectedCoverLetterVersion,
    })
    removeCoverLetterDrafts({ userId: userId.value, coverLetterId: coverLetterId.value })
    statusMessage.value = '자기소개서를 보관했어요. 이제 읽기 전용입니다.'
    clearConflictState()
  } catch (error) {
    await handleConflict('LIFECYCLE', error, {
      localSnapshot: `${snapshot.action} 요청\n기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
      serverSnapshot: (latest) => formatCoverSnapshot(latest),
      retry: async (latest) => submitArchive(snapshot, latest.version),
      cancel: () => undefined,
    })
  }
}

async function unarchiveCover(): Promise<void> {
  const detail = coverLetter.data.value
  if (!detail || !detail.canUnarchive) return
  const snapshot: LifecycleSnapshot = Object.freeze({
    action: 'DRAFT 복구',
    baseCoverLetterVersion: detail.version,
  })
  await submitUnarchive(snapshot, detail.version)
}

async function submitUnarchive(
  snapshot: LifecycleSnapshot,
  expectedCoverLetterVersion: number,
): Promise<void> {
  clearMessages()
  try {
    await unarchiveMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      version: expectedCoverLetterVersion,
    })
    statusMessage.value = '자기소개서를 DRAFT로 복구했어요.'
    clearConflictState()
  } catch (error) {
    await handleConflict('LIFECYCLE', error, {
      localSnapshot: `${snapshot.action} 요청\n기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
      serverSnapshot: (latest) => formatCoverSnapshot(latest),
      retry: async (latest) => submitUnarchive(snapshot, latest.version),
      cancel: () => undefined,
    })
  }
}

async function handleRunTerminal(run: AgentRunDetailDto): Promise<void> {
  acceptedRunId.value = ''
  await invalidateCoverLetterQueries(cache, userId.value, coverLetterId.value)
  await latestRun.refetch()
  await coverLetter.refetch()
  if (selectedQuestionId.value) await versions.refetch()
  if (verificationVersionId.value) await verifications.refetch()
  if (run.partialResult?.failedScopeKeys.length) {
    generationQuestionIds.value = new Set(run.partialResult.failedScopeKeys)
    statusMessage.value =
      '일부 문항만 완료됐어요. 성공 버전은 보존됐고 실패 문항만 선택해 두었어요.'
  }
}

async function handleConflict(
  kind: CoverLetterConflictKind,
  error: unknown,
  resolution: ConflictResolution,
): Promise<void> {
  const apiError = normalizeApiError(error)
  if (apiError.status !== 409) {
    actionError.value = coverLetterActionMessage(apiError)
    return
  }
  await coverLetter.refetch()
  const latest = coverLetter.data.value ?? null
  conflict.value = {
    kind: apiError.code === 'ACTIVE_COVER_LETTER_EXISTS' ? 'ACTIVE_EXISTS' : kind,
    errorCode: apiError.code,
    serverSnapshot: resolution.serverSnapshot(latest),
    localDraft: resolution.localSnapshot,
  }
  conflictRetry.value = latest ? () => resolution.retry(latest) : null
  conflictCancel.value = () => resolution.cancel(latest)
}

async function reapplyConflict(): Promise<void> {
  const retry = conflictRetry.value
  if (!retry || conflictReapplying.value) return
  conflictReapplying.value = true
  actionError.value = ''
  try {
    await retry()
  } finally {
    conflictReapplying.value = false
  }
}

function cancelConflict(): void {
  conflictCancel.value?.()
  clearConflictState()
}

function clearMessages(): void {
  actionError.value = ''
  statusMessage.value = ''
}

function clearConflictState(): void {
  conflict.value = null
  conflictRetry.value = null
  conflictCancel.value = null
}

function findActiveQuestion(
  detail: CoverLetterDetailDto | null,
  questionId: string,
): CoverLetterQuestionDto | null {
  return (
    detail?.questions.find(
      (question) => question.id === questionId && question.deletedAt === null,
    ) ?? null
  )
}

function formatCoverSnapshot(detail: CoverLetterDetailDto | null): string {
  if (!detail) return '최신 서버 상태를 찾지 못했어요.'
  return [
    `제목: ${detail.title}`,
    `상태: ${COVER_LETTER_STATUS_LABELS[detail.status]}`,
    `자기소개서 version: ${detail.version}`,
  ].join('\n')
}

function formatQuestionCreateSnapshot(snapshot: QuestionCreateSnapshot): string {
  return [
    '새 문항',
    `순서: ${snapshot.questionOrder}`,
    `내용: ${snapshot.questionText}`,
    `최대 글자 수: ${snapshot.maxLength ?? '없음'}`,
    `메모: ${snapshot.memo ?? '없음'}`,
    `기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
  ].join('\n')
}

function formatQuestionEditSnapshot(snapshot: QuestionEditSnapshot): string {
  return [
    `문항 ID: ${snapshot.questionId}`,
    `순서: ${snapshot.questionOrder}`,
    `내용: ${snapshot.questionText}`,
    `최대 글자 수: ${snapshot.maxLength ?? '없음'}`,
    `메모: ${snapshot.memo ?? '없음'}`,
    `기준 문항 version: ${snapshot.baseQuestionVersion}`,
  ].join('\n')
}

function formatQuestionSnapshot(question: CoverLetterQuestionDto | null): string {
  if (!question) return '최신 서버에서 이 문항을 찾지 못했어요. 삭제됐을 수 있어요.'
  return [
    `문항 ID: ${question.id}`,
    `순서: ${question.questionOrder}`,
    `내용: ${question.questionText}`,
    `최대 글자 수: ${question.maxLength ?? '없음'}`,
    `메모: ${question.memo ?? '없음'}`,
    `문항 version: ${question.version}`,
  ].join('\n')
}

function formatQuestionOrderSnapshot(questions: readonly CoverLetterQuestionDto[]): string {
  const ordered = [...questions]
    .filter((question) => question.deletedAt === null)
    .sort((left, right) => left.questionOrder - right.questionOrder)
  if (ordered.length === 0) return '활성 문항 없음'
  return ordered
    .map((question) => `${question.questionOrder}. ${question.questionText}\n   ID: ${question.id}`)
    .join('\n')
}

function formatSavedQuestionOrderSnapshot(snapshot: QuestionOrderSnapshot): string {
  const byId = new Map(snapshot.questions.map((question) => [question.id, question]))
  return snapshot.questionIds
    .map((id, index) => {
      const question = byId.get(id)
      return `${index + 1}. ${question?.questionText ?? '알 수 없는 문항'}\n   ID: ${id}`
    })
    .join('\n')
}

function formatAnswerSnapshot(question: CoverLetterQuestionDto | null): string {
  if (!question) return '최신 서버에서 이 문항을 찾지 못했어요. 삭제됐을 수 있어요.'
  const answer = question.currentAnswer
  if (!answer) {
    return [`문항 ID: ${question.id}`, `문항: ${question.questionText}`, '현재 답변: 없음'].join(
      '\n',
    )
  }
  return [
    `문항 ID: ${question.id}`,
    `문항: ${question.questionText}`,
    `현재 답변 ID: ${answer.id}`,
    `답변 version: ${answer.versionNo}`,
    `내용:\n${answer.plainText}`,
  ].join('\n')
}

function formatAnswerSaveSnapshot(snapshot: AnswerSaveSnapshot): string {
  return [
    `문항 ID: ${snapshot.questionId}`,
    `기준 답변 ID: ${snapshot.baseVersionId ?? '없음'}`,
    `글자 수: ${snapshot.characterCount}`,
    `저장할 내용:\n${snapshot.plainText}`,
  ].join('\n')
}

function formatAnswerRestoreSnapshot(snapshot: AnswerRestoreSnapshot): string {
  return [
    `문항 ID: ${snapshot.questionId}`,
    `기준 답변 ID: ${snapshot.baseVersionId ?? '없음'}`,
    `복원할 답변 ID: ${snapshot.versionId}`,
    `복원할 version: ${snapshot.versionNo}`,
    `복원할 내용:\n${snapshot.plainText}`,
  ].join('\n')
}

function formatGenerationSnapshot(snapshot: GenerationSnapshot): string {
  return [
    '선택 문항 AI 초안 생성',
    `문항 IDs: ${snapshot.questionIds.join(', ')}`,
    `선호 근거 IDs: ${snapshot.preferredEvidenceIds.join(', ') || '없음'}`,
    `작성 방식: ${snapshot.qualityMode}`,
    `경험 중복 최소화: ${snapshot.avoidExperienceDuplication ? '예' : '아니요'}`,
    `기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
  ].join('\n')
}

function formatFinalizeSnapshot(snapshot: FinalizeSnapshot): string {
  return [
    '최종화 요청',
    `확인한 WARNING 검증 IDs: ${snapshot.acknowledgedWarningVerificationIds.join(', ') || '없음'}`,
    `기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
  ].join('\n')
}

function syncQuestionFieldsFromServer(
  detail: CoverLetterDetailDto | null,
  questionId: string,
): void {
  if (!detail) return
  const question = findActiveQuestion(detail, questionId)
  if (!question) return
  selectedQuestionId.value = question.id
  questionTextDraft.value = question.questionText
  questionMaxLengthDraft.value = question.maxLength === null ? '' : String(question.maxLength)
  questionMemoDraft.value = question.memo ?? ''
}

function syncAnswerFromServer(detail: CoverLetterDetailDto | null, questionId: string): void {
  if (!detail) return
  const question = findActiveQuestion(detail, questionId)
  if (!question) return
  const answer = question.currentAnswer
  const canonical = canonicalizeEditorContent(answer?.contentJson ?? EMPTY_TIPTAP_DOCUMENT)
  selectedQuestionId.value = question.id
  editorContent.value = canonical.document
  editorCharacterCount.value = answer?.characterCount ?? 0
  editorDirty.value = false
  selectedVersionId.value = answer?.id ?? ''
  removeCoverLetterQuestionDrafts({
    userId: userId.value,
    coverLetterId: coverLetterId.value,
    questionId,
  })
  draftCandidate.value = null
  draftNotice.value = ''
}

function parseOptionalInteger(value: string | number): number | null {
  if (typeof value === 'string' && value.trim() === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function coverLetterActionMessage(error: ApiClientError): string {
  if (error.code === 'COVER_LETTER_ARCHIVED') {
    return '보관된 자기소개서는 읽기 전용이에요.'
  }
  if (error.code === 'COVER_LETTER_NOT_FINALIZABLE') {
    return '최신 답변과 검증 상태를 다시 확인해 주세요.'
  }
  if (error.code === 'QUALITY_MODE_NOT_SUPPORTED') {
    return '현재 AI 설정에서 선택한 작성 방식을 사용할 수 없어요.'
  }
  return error.message
}

function verificationTone(
  status: VerificationDto['status'],
): 'neutral' | 'success' | 'warning' | 'danger' {
  return ({ PENDING: 'neutral', PASSED: 'success', WARNING: 'warning', FAILED: 'danger' } as const)[
    status
  ]
}
</script>

<template>
  <section class="cover-editor app-page" aria-label="자기소개서 편집">
    <StatePanel
      v-if="coverLetter.isLoading.value"
      kind="loading"
      title="자기소개서를 불러오는 중…"
      description="문항, 현재 답변과 검증 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="coverLetter.isError.value"
      kind="error"
      :title="
        normalizeApiError(coverLetter.error.value).status === 404
          ? '자기소개서를 찾을 수 없어요.'
          : '자기소개서를 불러오지 못했어요.'
      "
      :description="normalizeApiError(coverLetter.error.value).message"
    >
      <template #actions>
        <RouterLink class="button button--secondary" :to="{ name: 'cover-letters' }">
          자기소개서 목록
        </RouterLink>
      </template>
    </StatePanel>

    <template v-else-if="coverLetter.data.value">
      <header class="cover-header">
        <RouterLink class="back-link" :to="{ name: 'cover-letters' }">
          <AppIcon name="arrow-left" />
          자기소개서 목록
        </RouterLink>

        <div class="cover-header__top">
          <div class="cover-header__identity">
            <p class="cover-header__job">
              <AppIcon name="jobs" />
              <span>{{ jobLabel }}</span>
            </p>
            <div class="cover-header__title-row">
              <h1 id="cover-editor-heading" class="cover-header__title">
                {{ coverLetter.data.value.title }}
              </h1>
              <StatusBadge
                :label="COVER_LETTER_STATUS_LABELS[coverLetter.data.value.status]"
                :tone="
                  coverLetter.data.value.status === 'FINALIZED'
                    ? 'success'
                    : coverLetter.data.value.status === 'ARCHIVED'
                      ? 'neutral'
                      : 'brand'
                "
              />
            </div>
          </div>
          <div class="cover-header__actions">
            <button
              v-if="!readOnly && !renamingTitle"
              type="button"
              class="button button--ghost button--compact"
              @click="startRenaming()"
            >
              제목 수정
            </button>
            <RouterLink
              v-if="jobId"
              class="button button--ghost button--compact"
              :to="{ name: 'job-analysis', params: { jobId } }"
            >
              공고 분석 보기
            </RouterLink>
            <button
              v-if="coverLetter.data.value.canArchive"
              type="button"
              class="button button--ghost button--compact"
              :disabled="archiveMutation.isPending.value"
              @click="archiveCover()"
            >
              보관
            </button>
          </div>
        </div>

        <form
          v-if="renamingTitle && !readOnly"
          class="cover-header__rename"
          @submit.prevent="submitTitleRename()"
        >
          <label class="field">
            <span class="field__label">자기소개서 제목</span>
            <input v-model="titleDraft" class="control" maxlength="300" />
          </label>
          <button
            type="submit"
            class="button button--primary"
            :disabled="!titleDirty || updateCoverMutation.isPending.value"
          >
            {{ updateCoverMutation.isPending.value ? '저장 중…' : '제목 저장' }}
          </button>
          <button type="button" class="button button--secondary" @click="cancelRenaming()">
            제목 수정 취소
          </button>
        </form>

        <div class="cover-header__progress">
          <div
            class="cover-progress"
            role="img"
            :aria-label="`문항 ${questionCount}개 중 ${answeredQuestions.length}개 답변 작성`"
          >
            <span class="cover-progress__fill" :style="{ width: `${answeredPercent}%` }" />
          </div>
          <dl class="cover-header__facts">
            <div>
              <dt>답변</dt>
              <dd>{{ answeredQuestions.length }} / {{ questionCount }}</dd>
            </div>
            <div>
              <dt>검증</dt>
              <dd>{{ reviewedCount }} / {{ questionCount }}</dd>
            </div>
            <div>
              <dt>마지막 수정</dt>
              <dd>{{ formatCoverLetterInstant(coverLetter.data.value.updatedAt) }}</dd>
            </div>
          </dl>
        </div>
      </header>

      <section v-if="readOnly" class="cover-editor__archived alert alert--warning" role="status">
        <div>
          <strong>ARCHIVED · 읽기 전용</strong>
          <p>제목, 문항, 답변 저장·복원, AI 생성·검증과 최종화는 사용할 수 없습니다.</p>
        </div>
        <button
          v-if="coverLetter.data.value.canUnarchive"
          type="button"
          class="button button--secondary"
          :disabled="unarchiveMutation.isPending.value"
          @click="unarchiveCover()"
        >
          DRAFT로 복구
        </button>
      </section>

      <section class="coach" aria-labelledby="coach-heading">
        <div class="coach__body">
          <span class="coach__avatar" aria-hidden="true"><AppIcon name="sparkle" /></span>
          <div class="coach__copy">
            <p class="coach__eyebrow">AI 자기소개서 코치</p>
            <h2 id="coach-heading">{{ coachAction.title }}</h2>
            <p class="coach__description">{{ coachAction.description }}</p>
          </div>
          <div v-if="coachAction.actionLabel" class="coach__action">
            <button
              type="button"
              class="button button--primary"
              :disabled="coachAction.disabled"
              data-testid="coach-primary-action"
              @click="runCoachAction()"
            >
              {{ coachAction.actionLabel }}
              <AppIcon name="arrow-right" />
            </button>
            <p v-if="coachAction.note" class="coach__note">{{ coachAction.note }}</p>
          </div>
        </div>
        <ol class="coach__steps" aria-label="자기소개서 작성 단계">
          <li v-for="step in journeySteps" :key="step.key" :data-state="step.state">
            <span class="coach__marker" aria-hidden="true">
              {{ step.state === 'done' ? '✓' : step.order }}
            </span>
            <span class="coach__step">
              <strong>{{ step.label }}</strong>
              <small>{{ step.hint }}</small>
            </span>
          </li>
        </ol>
      </section>

      <p v-if="statusMessage" class="alert alert--success cover-editor__message" role="status">
        {{ statusMessage }}
      </p>
      <p v-if="actionError" class="alert alert--danger cover-editor__message" role="alert">
        {{ actionError }}
      </p>
      <CoverLetterConflictPanel
        v-if="conflict"
        class="cover-editor__message"
        :conflict="conflict"
        :reapplying="conflictReapplying"
        @reapply="reapplyConflict"
        @cancel="cancelConflict"
      />

      <CoverLetterRunMonitor
        v-if="currentRunId"
        :key="currentRunId"
        class="cover-editor__message"
        :user-id="userId"
        :cover-letter-id="coverLetterId"
        :agent-run-id="currentRunId"
        :question-labels="questionLabels"
        @terminal="handleRunTerminal"
      />

      <div class="cover-editor__workspace" data-testid="cover-letter-editor">
        <aside class="cover-editor__navigator" aria-label="자기소개서 문항">
          <div class="panel-heading">
            <div>
              <h2>문항 {{ questionCount }}개</h2>
              <p>답변 {{ answeredQuestions.length }}개 작성됨</p>
            </div>
            <button
              v-if="!readOnly && questionCount < 20"
              type="button"
              class="button button--secondary button--compact"
              :aria-expanded="addingQuestion"
              @click="addingQuestion = !addingQuestion"
            >
              문항 추가
            </button>
          </div>

          <p v-if="questionCount === 0" class="question-list__empty">
            아직 등록한 문항이 없어요. 문항을 추가하면 답변 작성과 AI 초안을 시작할 수 있어요.
          </p>
          <ol v-else class="question-list">
            <li v-for="(question, index) in activeQuestions" :key="question.id">
              <button
                type="button"
                class="question-list__select"
                :class="{ 'question-list__select--active': selectedQuestionId === question.id }"
                :aria-current="selectedQuestionId === question.id ? 'step' : undefined"
                @click="selectedQuestionId = question.id"
              >
                <span class="question-list__order">{{ question.questionOrder }}</span>
                <span class="question-list__body">
                  <strong>{{ question.questionText }}</strong>
                  <span class="question-list__meta">
                    <StatusBadge
                      :label="questionStatus(question).label"
                      :tone="questionStatus(question).tone"
                    />
                    <small>{{ questionLengthLabel(question) }}</small>
                  </span>
                </span>
              </button>
              <div v-if="!readOnly" class="question-list__move" aria-label="문항 순서 변경">
                <button
                  type="button"
                  :disabled="index === 0 || reorderMutation.isPending.value"
                  :aria-label="`${question.questionOrder}번 문항 위로 이동`"
                  @click="moveQuestion(question.id, -1)"
                >
                  ↑
                </button>
                <button
                  type="button"
                  :disabled="index === questionCount - 1 || reorderMutation.isPending.value"
                  :aria-label="`${question.questionOrder}번 문항 아래로 이동`"
                  @click="moveQuestion(question.id, 1)"
                >
                  ↓
                </button>
              </div>
            </li>
          </ol>
        </aside>

        <main class="cover-editor__answer">
          <section v-if="addingQuestion && !readOnly" class="question-add-panel">
            <div class="panel-heading">
              <div>
                <h2><AppIcon name="plus" />새 문항 등록</h2>
                <p>
                  공고에 적힌 문항을 그대로 옮겨 적으면 AI 코치가 같은 기준으로 초안을 만들어요.
                </p>
              </div>
              <button
                type="button"
                class="button button--ghost button--compact"
                @click="addingQuestion = false"
              >
                닫기
              </button>
            </div>
            <form class="question-add" @submit.prevent="addQuestion()">
              <label class="field question-add__text">
                <span class="field__label">문항 내용</span>
                <textarea
                  ref="newQuestionInput"
                  v-model="newQuestionText"
                  class="control"
                  maxlength="2000"
                  rows="3"
                  placeholder="예) 지원 직무를 선택한 이유와 준비 과정을 설명해 주세요."
                  required
                />
              </label>
              <label class="field">
                <span class="field__label">최대 글자 수</span>
                <input
                  v-model="newQuestionMaxLength"
                  class="control control--compact"
                  type="number"
                  min="1"
                  max="10000"
                  placeholder="예: 700"
                />
                <span class="field__help">비워 두면 글자 수 제한 없이 작성해요.</span>
              </label>
              <div class="question-add__presets">
                <button
                  v-for="preset in [500, 700, 1000, 1500]"
                  :key="preset"
                  type="button"
                  class="chip"
                  @click="applyLengthPreset(preset)"
                >
                  {{ preset }}자
                </button>
              </div>
              <label class="field question-add__text">
                <span class="field__label">메모</span>
                <textarea
                  v-model="newQuestionMemo"
                  class="control"
                  maxlength="2000"
                  rows="2"
                  placeholder="강조하고 싶은 방향이나 기억할 점을 적어 두세요. AI 초안에도 함께 참고해요."
                />
              </label>
              <button
                type="submit"
                class="button button--primary question-add__submit"
                :disabled="createQuestionMutation.isPending.value"
              >
                추가
              </button>
            </form>
          </section>

          <template v-if="selectedQuestion">
            <section class="answer-brief">
              <div class="answer-brief__top">
                <div>
                  <p class="answer-brief__eyebrow">{{ selectedQuestion.questionOrder }}번 문항</p>
                  <h2 class="answer-brief__question">{{ selectedQuestion.questionText }}</h2>
                </div>
                <StatusBadge
                  :label="questionStatus(selectedQuestion).label"
                  :tone="questionStatus(selectedQuestion).tone"
                />
              </div>

              <p v-if="selectedQuestion.memo" class="answer-brief__memo">
                <AppIcon name="pen" />
                <span>{{ selectedQuestion.memo }}</span>
              </p>

              <div class="answer-brief__length">
                <div class="answer-brief__length-copy">
                  <strong>
                    {{ editorCharacterCount }}자
                    <span v-if="selectedQuestion.maxLength">
                      / {{ selectedQuestion.maxLength }}자
                    </span>
                  </strong>
                  <small v-if="editorOverLimit" class="answer-brief__over">
                    최대 글자 수를 {{ editorCharacterCount - (selectedQuestion.maxLength ?? 0) }}자
                    넘었어요.
                  </small>
                  <small v-else-if="selectedQuestion.maxLength">
                    {{ Math.max(0, selectedQuestion.maxLength - editorCharacterCount) }}자 더 쓸 수
                    있어요.
                  </small>
                  <small v-else>이 문항에는 글자 수 제한이 없어요.</small>
                </div>
                <div
                  v-if="answerLengthPercent !== null"
                  class="answer-brief__meter"
                  aria-hidden="true"
                >
                  <span
                    :class="{ 'answer-brief__meter--over': editorOverLimit }"
                    :style="{ width: `${answerLengthPercent}%` }"
                  />
                </div>
              </div>

              <details v-if="!readOnly" class="question-meta question-settings">
                <summary>문항 내용·글자 수·메모 수정</summary>
                <div class="question-meta__form">
                  <label class="field question-meta__text">
                    <span class="field__label">문항 내용</span>
                    <textarea
                      v-model="questionTextDraft"
                      class="control"
                      rows="3"
                      maxlength="2000"
                    />
                  </label>
                  <label class="field">
                    <span class="field__label">최대 글자 수</span>
                    <input
                      v-model="questionMaxLengthDraft"
                      class="control control--compact"
                      type="number"
                      min="1"
                      max="10000"
                    />
                  </label>
                  <label class="field">
                    <span class="field__label">메모</span>
                    <textarea
                      v-model="questionMemoDraft"
                      class="control"
                      rows="2"
                      maxlength="2000"
                    />
                  </label>
                </div>
                <div class="question-meta__actions">
                  <button
                    type="button"
                    class="button button--secondary"
                    :disabled="updateQuestionMutation.isPending.value"
                    @click="updateQuestion()"
                  >
                    문항 저장
                  </button>
                  <button
                    v-if="deleteConfirmationId !== selectedQuestion.id"
                    type="button"
                    class="button button--ghost"
                    @click="deleteConfirmationId = selectedQuestion.id"
                  >
                    문항 삭제
                  </button>
                  <template v-else>
                    <button
                      type="button"
                      class="button button--danger"
                      :disabled="deleteQuestionMutation.isPending.value"
                      @click="removeQuestion(selectedQuestion)"
                    >
                      삭제 확인
                    </button>
                    <button
                      type="button"
                      class="button button--secondary"
                      @click="deleteConfirmationId = ''"
                    >
                      취소
                    </button>
                  </template>
                </div>
                <p class="question-settings__note">
                  문항을 삭제해도 과거 답변 버전과 검증 기록은 보존돼요.
                </p>
              </details>
              <div v-else class="question-settings question-settings--readonly">
                <p>보관된 자기소개서라 문항 정보를 수정할 수 없어요.</p>
              </div>
            </section>

            <section
              v-if="draftCandidate || draftNotice"
              class="draft-recovery alert alert--warning"
              role="status"
            >
              <div class="draft-recovery__lead">
                <strong>이 브라우저에만 있는 임시 저장</strong>
                <p>{{ draftNotice }}</p>
              </div>
              <div v-if="draftCandidate" class="draft-recovery__comparison">
                <article>
                  <h3>현재 서버 버전</h3>
                  <pre>{{ selectedQuestion.currentAnswer?.plainText ?? '(저장된 답변 없음)' }}</pre>
                </article>
                <article>
                  <h3>임시 저장</h3>
                  <pre>{{ canonicalizeEditorContent(draftCandidate.contentJson).plainText }}</pre>
                </article>
              </div>
              <div v-if="draftCandidate" class="draft-recovery__actions">
                <button type="button" class="button button--primary" @click="applyDraftCandidate">
                  임시 저장 재적용
                </button>
                <button
                  type="button"
                  class="button button--secondary"
                  @click="discardDraftCandidate"
                >
                  서버 버전 유지
                </button>
              </div>
            </section>

            <CoverLetterTipTapEditor
              ref="editorRef"
              :content="editorContent"
              :readonly="readOnly"
              :max-length="selectedQuestion.maxLength"
              :server-character-count="editorDirty ? null : selectedServerCount"
              @update="onEditorUpdate"
            />

            <div class="answer-actions">
              <p class="answer-actions__state" :data-dirty="editorDirty">
                <AppIcon :name="editorDirty ? 'clock' : 'check'" />
                <span v-if="editorDirty">브라우저 임시 저장됨 · 서버 미저장</span>
                <span v-else-if="selectedQuestion.currentAnswer">
                  서버 v{{ selectedQuestion.currentAnswer.versionNo }}와 동일 ·
                  {{ ANSWER_SOURCE_LABELS[selectedQuestion.currentAnswer.sourceType] }}
                </span>
                <span v-else>아직 저장된 답변이 없어요.</span>
              </p>
              <button
                v-if="!readOnly"
                type="button"
                class="button button--primary"
                :disabled="!editorDirty || editorOverLimit || saveVersionMutation.isPending.value"
                data-testid="save-answer-version"
                @click="saveAnswer()"
              >
                {{ saveVersionMutation.isPending.value ? '저장 중…' : '새 버전 저장' }}
              </button>
            </div>
          </template>
          <StatePanel
            v-else-if="!addingQuestion"
            kind="empty"
            title="문항을 선택하거나 추가해 주세요."
            description="답변 편집기는 선택한 문항의 현재 서버 버전을 기준으로 열립니다."
          />
        </main>

        <aside class="cover-editor__rail" aria-label="AI 코치 작업 패널">
          <section class="rail-section">
            <div class="rail-section__heading">
              <span class="rail-section__step" aria-hidden="true">1</span>
              <h2><AppIcon name="target" />공고가 원하는 것</h2>
              <RouterLink
                v-if="jobId"
                :to="{ name: 'job-analysis', params: { jobId } }"
                class="text-link"
              >
                분석 보기
              </RouterLink>
            </div>
            <p v-if="job.data.value?.analysisOutdated" class="rail-section__warning">
              공고 분석이 현재 공고·프로필·근거와 달라졌어요. 기존 결과는 참고할 수 있지만 재분석을
              권장합니다.
            </p>
            <p v-if="analysis.isLoading.value" class="rail-section__empty">
              요구사항을 불러오는 중…
            </p>
            <ul v-else-if="requirementHighlights.length" class="rail-list">
              <li
                v-for="requirement in requirementHighlights"
                :key="`${requirement.category}-${requirement.text}`"
              >
                {{ requirement.text }}
              </li>
            </ul>
            <p v-else class="rail-section__empty">
              공고 분석 결과가 없어요. 분석 없이도 작성은 가능하지만 요구사항 연결을 직접 확인해
              주세요.
            </p>
          </section>

          <section class="rail-section">
            <div class="rail-section__heading">
              <span class="rail-section__step" aria-hidden="true">2</span>
              <h2><AppIcon name="spark" />내 강점과 보완점</h2>
            </div>
            <ul v-if="analysisStrengths.length" class="insight-list">
              <li v-for="strength in analysisStrengths.slice(0, 4)" :key="strength">
                <AppIcon name="check" />
                <span>{{ strength }}</span>
              </li>
            </ul>
            <p v-else class="rail-section__empty">
              공고 분석에서 찾은 강점이 아직 없어요. 확인한 경험을 늘리면 근거가 풍부해져요.
            </p>
            <details v-if="analysisGaps.length" class="insight-gaps">
              <summary>보완 포인트 {{ analysisGaps.length }}개</summary>
              <ul class="insight-list insight-list--gap">
                <li v-for="gap in analysisGaps" :key="gap">
                  <AppIcon name="lift" />
                  <span>{{ gap }}</span>
                </li>
              </ul>
            </details>
          </section>

          <section class="rail-section">
            <div class="rail-section__heading">
              <span class="rail-section__step" aria-hidden="true">3</span>
              <h2><AppIcon name="evidence" />쓸 소재 고르기</h2>
              <span class="rail-section__count">{{ selectedEvidenceIds.size }}개 선택</span>
            </div>
            <p class="rail-section__hint">
              승인한 경험만 근거로 사용해요. 고르지 않으면 승인된 경험 전체를 참고합니다.
            </p>
            <div v-if="!readOnly && evidenceItems.length" class="evidence-quick">
              <button
                v-if="unselectedRecommendedIds.length"
                type="button"
                class="chip chip--brand"
                @click="selectRecommendedEvidence()"
              >
                공고와 연결된 {{ unselectedRecommendedIds.length }}개 담기
              </button>
              <button
                v-if="selectedEvidenceIds.size"
                type="button"
                class="chip"
                @click="clearSelectedEvidence()"
              >
                선택 해제
              </button>
            </div>
            <p v-if="evidence.isLoading.value" class="rail-section__empty">
              확인한 경험을 불러오는 중…
            </p>
            <p v-else-if="evidence.isError.value" class="rail-section__warning">
              경험 정보를 불러오지 못했어요.
            </p>
            <ul v-else-if="evidenceItems.length" class="evidence-options">
              <li v-for="item in evidenceItems" :key="item.id">
                <label :class="{ 'evidence-options__item--on': selectedEvidenceIds.has(item.id) }">
                  <input
                    type="checkbox"
                    class="checkbox-control"
                    :checked="selectedEvidenceIds.has(item.id)"
                    :disabled="readOnly"
                    @change="toggleEvidence(item.id)"
                  />
                  <span class="evidence-options__body">
                    <span class="evidence-options__title">
                      <strong>{{ item.title }}</strong>
                      <em v-if="recommendedEvidenceIds.has(item.id)">공고와 연결됨</em>
                    </span>
                    <small>{{ item.evidenceCategory }}</small>
                    <small v-if="evidenceSnippet(item)" class="evidence-options__snippet">
                      {{ evidenceSnippet(item) }}
                    </small>
                  </span>
                </label>
              </li>
            </ul>
            <p v-else class="rail-section__empty">
              승인한 경험이 아직 없어요. 이력서·자료에서 경험을 확인하면 근거로 쓸 수 있어요.
            </p>
          </section>

          <section v-if="!readOnly" class="rail-section generation-command">
            <div class="rail-section__heading">
              <span class="rail-section__step" aria-hidden="true">4</span>
              <h2><AppIcon name="sparkle" />AI에게 초안 맡기기</h2>
            </div>
            <fieldset v-if="questionCount > 0" class="generation-questions">
              <legend>초안을 만들 문항</legend>
              <label v-for="question in activeQuestions" :key="`generate-${question.id}`">
                <input
                  type="checkbox"
                  class="checkbox-control"
                  :checked="generationQuestionIds.has(question.id)"
                  @change="toggleGenerationQuestion(question.id)"
                />
                {{ question.questionOrder }}번
              </label>
            </fieldset>
            <label class="field">
              <span class="field__label">작성 방식</span>
              <select v-model="qualityMode" class="control control--compact">
                <option value="ECONOMY">빠르게 초안 만들기</option>
                <option value="BALANCED">균형 있게 작성</option>
                <option value="HIGH_QUALITY">내용을 더 꼼꼼히 작성</option>
              </select>
            </label>
            <label class="check-field">
              <input
                v-model="avoidExperienceDuplication"
                type="checkbox"
                class="checkbox-control"
              />
              지원서 전체에서 경험 중복 최소화
            </label>
            <button
              type="button"
              class="button button--primary"
              :disabled="generationQuestionIds.size === 0 || aiActionUnavailable"
              data-testid="generate-cover-letter"
              @click="generateAnswers()"
            >
              {{ aiActionUnavailable ? 'AI 작업 진행 중…' : '선택 문항 AI 초안 생성' }}
            </button>
            <button
              type="button"
              class="button button--secondary"
              :disabled="!selectedQuestion?.currentAnswer || aiActionUnavailable"
              data-testid="verify-answer-version"
              @click="verifyCurrentAnswer"
            >
              {{ aiActionUnavailable ? 'AI 작업 진행 중…' : '현재 답변 검증' }}
            </button>
            <p class="rail-section__hint">
              초안은 편집기를 덮어쓰지 않고 새 버전으로 도착해요. 검증은 지금 저장된 버전을 기준으로
              실행됩니다.
            </p>
          </section>

          <section class="rail-section">
            <div class="rail-section__heading">
              <span class="rail-section__step" aria-hidden="true">5</span>
              <h2><AppIcon name="shield" />검증 결과</h2>
              <span v-if="selectedVersion" class="rail-section__count">
                v{{ selectedVersion.versionNo }}
              </span>
            </div>
            <p v-if="!selectedVersion" class="rail-section__empty">
              문항과 답변 버전을 선택하면 검증 결과를 보여 드려요.
            </p>
            <p v-else-if="verifications.isLoading.value" class="rail-section__empty">
              검증 이력을 불러오는 중…
            </p>
            <p
              v-else-if="(verifications.data.value?.items.length ?? 0) === 0"
              class="rail-section__empty"
            >
              선택한 답변 버전의 검증 기록이 없어요.
            </p>
            <article
              v-for="verification in verifications.data.value?.items ?? []"
              :key="verification.id"
              class="verification-card"
            >
              <header>
                <StatusBadge
                  :label="VERIFICATION_STATUS_LABELS[verification.status]"
                  :tone="verificationTone(verification.status)"
                />
                <RouterLink
                  v-if="verification.agentRunId"
                  :to="{
                    name: 'agent-run-detail',
                    params: { agentRunId: verification.agentRunId },
                  }"
                  class="text-link"
                >
                  AI 작업
                </RouterLink>
              </header>
              <ul v-if="verification.issues.length" class="verification-issues">
                <li v-for="(issue, index) in verification.issues" :key="`${issue.code}-${index}`">
                  <strong>
                    {{ ISSUE_CODE_LABELS[issue.code] }} ·
                    {{ ISSUE_SEVERITY_LABELS[issue.severity] }}
                  </strong>
                  <blockquote v-if="issue.relatedText">{{ issue.relatedText }}</blockquote>
                  <p>{{ issue.message }}</p>
                  <ul v-if="issue.evidenceRefs.length" class="historical-evidence">
                    <li v-for="reference in issue.evidenceRefs" :key="reference.id">
                      <span>{{ reference.title }}</span>
                      <small>{{ evidenceCurrentState(reference).label }}</small>
                      <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                        새 생성·검증에서는 제외됨
                      </small>
                    </li>
                  </ul>
                </li>
              </ul>
              <div v-if="verification.suggestions.length" class="verification-suggestions">
                <h3>코치의 수정 제안</h3>
                <div v-for="suggestion in verification.suggestions" :key="suggestion">
                  <p>{{ suggestion }}</p>
                  <button
                    v-if="!readOnly"
                    type="button"
                    class="button button--secondary button--compact"
                    @click="applySuggestion(suggestion)"
                  >
                    편집기에 적용
                  </button>
                </div>
                <small>제안 적용은 서버에 자동 저장되지 않습니다.</small>
              </div>
              <ul v-if="verification.evidenceRefs.length" class="historical-evidence">
                <li v-for="reference in verification.evidenceRefs" :key="reference.id">
                  <span>{{ reference.title }}</span>
                  <small>{{ evidenceCurrentState(reference).label }}</small>
                  <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
                    새 생성·검증에서는 제외됨
                  </small>
                </li>
              </ul>
            </article>
          </section>
        </aside>
      </div>

      <section
        v-if="selectedQuestion"
        class="version-history"
        aria-labelledby="version-history-title"
      >
        <div class="panel-heading">
          <div>
            <h2 id="version-history-title">
              <AppIcon name="history" />{{ selectedQuestion.questionOrder }}번 문항 답변 이력
            </h2>
            <p>저장한 버전은 지워지지 않아요. 언제든 비교하고 되돌릴 수 있어요.</p>
          </div>
          <span class="panel-heading__count">{{ versions.data.value?.totalElements ?? 0 }}개</span>
        </div>
        <p v-if="versions.isLoading.value" class="rail-section__empty">버전 이력을 불러오는 중…</p>
        <div v-else class="version-history__layout">
          <div class="version-history__list" role="listbox" aria-label="답변 버전">
            <button
              v-for="version in versions.data.value?.items ?? []"
              :key="version.id"
              type="button"
              role="option"
              :aria-selected="selectedVersionId === version.id"
              :class="{ 'version-history__item--active': selectedVersionId === version.id }"
              @click="selectedVersionId = version.id"
            >
              <span class="version-history__label">
                <strong>v{{ version.versionNo }}</strong>
                <StatusBadge
                  :label="ANSWER_SOURCE_LABELS[version.sourceType]"
                  :tone="version.sourceType === 'AI_GENERATED' ? 'brand' : 'info'"
                />
              </span>
              <small>{{ formatCoverLetterInstant(version.createdAt) }}</small>
              <small v-if="version.isCurrent" class="version-history__current">현재 버전</small>
            </button>
          </div>
          <div v-if="selectedVersion" class="version-history__comparison">
            <article>
              <h3>현재 서버 답변</h3>
              <pre>{{ selectedQuestion.currentAnswer?.plainText ?? '(답변 없음)' }}</pre>
            </article>
            <article>
              <h3>선택한 v{{ selectedVersion.versionNo }}</h3>
              <pre>{{ selectedVersion.plainText || '(빈 답변)' }}</pre>
            </article>
            <button
              v-if="!readOnly && !selectedVersion.isCurrent"
              type="button"
              class="button button--secondary"
              :disabled="restoreMutation.isPending.value"
              data-testid="restore-answer-version"
              @click="restoreVersion(selectedVersion)"
            >
              {{
                restoreMutation.isPending.value
                  ? '복원 중…'
                  : '선택 버전을 새 RESTORED 버전으로 복원'
              }}
            </button>
          </div>
        </div>
      </section>

      <section class="finalization" aria-labelledby="finalization-title">
        <div class="panel-heading">
          <div>
            <h2 id="finalization-title"><AppIcon name="flag" />최종화 확인</h2>
            <p>
              자기소개서 최종화는 공고를 SUBMITTED로 바꾸지 않습니다. 공고 제출 상태는 별도로
              변경하세요.
            </p>
          </div>
          <StatusBadge
            :label="finalizeBlockers.length === 0 ? '준비됨' : `확인 ${finalizeBlockers.length}건`"
            :tone="finalizeBlockers.length === 0 ? 'success' : 'warning'"
          />
        </div>

        <ul v-if="questionCount > 0" class="finalization__checklist">
          <li v-for="question in activeQuestions" :key="`check-${question.id}`">
            <span class="finalization__order">{{ question.questionOrder }}</span>
            <span class="finalization__question">{{ question.questionText }}</span>
            <StatusBadge
              :label="questionStatus(question).label"
              :tone="questionStatus(question).tone"
            />
          </li>
        </ul>

        <ul v-if="finalizeBlockers.length" class="finalization__blockers">
          <li v-for="blocker in finalizeBlockers" :key="blocker">{{ blocker }}</li>
        </ul>

        <fieldset
          v-if="warningVerificationIds.length > 0 && coverLetter.data.value.status === 'DRAFT'"
          class="finalization__warnings"
        >
          <legend>확인 필요 검증을 읽었는지 확인해 주세요</legend>
          <label v-for="id in warningVerificationIds" :key="id">
            <input
              type="checkbox"
              class="checkbox-control"
              :checked="warningAcknowledgements.has(id)"
              @change="toggleWarningAcknowledgement(id)"
            />
            검증 {{ id.slice(0, 8) }}의 경고를 확인했습니다.
          </label>
        </fieldset>

        <button
          v-if="coverLetter.data.value.status === 'DRAFT'"
          type="button"
          class="button button--primary"
          :disabled="!canFinalizeNow || finalizeMutation.isPending.value"
          data-testid="finalize-cover-letter"
          @click="finalizeCover()"
        >
          {{ finalizeMutation.isPending.value ? '최종화 중…' : '자기소개서 최종화' }}
        </button>
        <p v-else-if="coverLetter.data.value.status === 'FINALIZED'" role="status">
          최종화된 자기소개서입니다. 문항이나 답변을 수정하면 DRAFT로 돌아갑니다.
        </p>
      </section>
    </template>
  </section>
</template>

<style scoped>
/*
 * 자기소개서 작성 화면.
 * 공고 분석 결과 화면과 같은 규칙을 따른다.
 *   - 정보는 카드를 중첩하지 않고 하나의 면 안에서 구분선과 여백으로 나눈다.
 *   - 화면마다 primary CTA는 하나이며, 그 하나는 항상 코치 패널이 소유한다.
 *   - 상태 색은 항상 한글 라벨과 함께 쓰고 색만으로 의미를 전달하지 않는다.
 * 오른쪽 rail의 1~5 번호는 코치 패널의 진행 단계와 같은 순서를 가리킨다.
 */

.cover-editor {
  min-width: 0;
  overflow-x: clip;
}

/* ------------------------------------------------------------ 작업 헤더 */

.cover-header {
  display: grid;
  gap: var(--space-4);
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  box-shadow: var(--shadow-panel);
}

.cover-header .back-link {
  justify-self: start;
}

.cover-header__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.cover-header__identity {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.cover-header__job {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.cover-header__job .icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
}

.cover-header__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.cover-header__title {
  font-size: clamp(1.35rem, 3vw, 1.8rem);
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--color-ink-title);
  overflow-wrap: anywhere;
}

.cover-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.cover-header__rename {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: end;
  gap: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-4);
}

.cover-header__progress {
  display: grid;
  gap: var(--space-3);
}

.cover-progress {
  height: 0.5rem;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--color-fill-strong);
}

.cover-progress__fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  transition: width var(--motion-base);
}

.cover-header__facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3) var(--space-6);
}

.cover-header__facts div {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.cover-header__facts dt {
  color: var(--color-text-muted);
}

.cover-header__facts dd {
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

/* ---------------------------------------------------------- 보관 안내 */

.cover-editor__archived {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.cover-editor__archived strong {
  font-weight: 750;
}

.cover-editor__archived p {
  margin-top: var(--space-1);
}

/* ---------------------------------------------------------- 코치 패널 */

.coach {
  position: relative;
  overflow: hidden;
  margin-top: var(--space-4);
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  box-shadow: var(--shadow-panel);
}

.coach::before {
  content: '';
  position: absolute;
  inset: -60% -15% auto auto;
  width: 26rem;
  height: 26rem;
  background: radial-gradient(
    closest-side,
    rgb(49 87 255 / 13%),
    rgb(116 138 255 / 5%) 55%,
    transparent 72%
  );
  pointer-events: none;
}

.coach__body {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
}

.coach__avatar {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border-radius: var(--radius-pill);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.coach__avatar .icon {
  width: 1.5rem;
  height: 1.5rem;
}

.coach__eyebrow {
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 750;
  letter-spacing: 0.02em;
}

.coach__copy h2 {
  margin-top: var(--space-1);
  max-width: 34rem;
  font-size: clamp(1.15rem, 2.4vw, 1.45rem);
  font-weight: 800;
  line-height: 1.4;
  letter-spacing: -0.015em;
  color: var(--color-ink-title);
}

.coach__description {
  margin-top: var(--space-2);
  max-width: 44rem;
  color: var(--color-text-secondary);
}

.coach__action {
  display: grid;
  justify-items: end;
  gap: var(--space-2);
}

.coach__action .button .icon {
  width: 1.1rem;
  height: 1.1rem;
}

.coach__note {
  max-width: 18rem;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  text-align: right;
}

.coach__steps {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
}

.coach__steps li {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  min-width: 0;
}

.coach__marker {
  display: grid;
  width: 1.375rem;
  height: 1.375rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 0.6875rem;
  font-weight: 800;
}

.coach__step {
  display: grid;
  gap: 0.125rem;
  min-width: 0;
}

.coach__step strong {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-text-secondary);
}

.coach__step small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.coach__steps li[data-state='done'] .coach__marker {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: white;
}

.coach__steps li[data-state='active'] .coach__marker {
  position: relative;
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.coach__steps li[data-state='active'] .coach__marker::after {
  content: '';
  position: absolute;
  inset: -1px;
  border: 2px solid var(--color-brand);
  border-radius: 50%;
  animation: coach-pulse 1.8s ease-out infinite;
}

.coach__steps li[data-state='active'] .coach__step strong {
  color: var(--color-brand-strong);
}

@keyframes coach-pulse {
  from {
    transform: scale(1);
    opacity: 0.85;
  }

  to {
    transform: scale(1.7);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .coach__steps li[data-state='active'] .coach__marker::after {
    animation: none;
  }

  .cover-progress__fill {
    transition: none;
  }
}

/* -------------------------------------------------------- 메시지 영역 */

.cover-editor__message {
  margin-top: var(--space-4);
}

/* ------------------------------------------------------------- 작업대 */

.cover-editor__workspace {
  display: grid;
  grid-template-columns: minmax(13rem, 0.62fr) minmax(24rem, 1.55fr) minmax(18rem, 0.85fr);
  align-items: start;
  gap: var(--space-4);
  margin-top: var(--space-6);
}

.cover-editor__navigator,
.cover-editor__answer,
.cover-editor__rail,
.version-history,
.finalization {
  min-width: 0;
}

.cover-editor__navigator,
.cover-editor__rail {
  display: grid;
  gap: var(--space-4);
}

/*
 * 문항 목록만 sticky로 고정한다.
 * 오른쪽 코치 rail은 5개 section이 길어 내부 scroll을 만들지 않고 페이지 흐름을 따른다.
 */
.cover-editor__navigator {
  position: sticky;
  top: calc(var(--global-header-height) + var(--space-4));
  max-height: calc(100dvh - var(--global-header-height) - var(--space-8));
  overflow-y: auto;
  overscroll-behavior: contain;
}

.cover-editor__navigator,
.answer-brief,
.question-add-panel,
.rail-section,
.version-history,
.finalization {
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-5);
  box-shadow: var(--shadow-panel);
}

.panel-heading,
.rail-section__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
}

.panel-heading h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 1.05rem;
  font-weight: 780;
  color: var(--color-ink-title);
}

.panel-heading h2 .icon {
  width: 1.15rem;
  height: 1.15rem;
  flex: 0 0 auto;
  color: var(--color-brand);
}

.panel-heading p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.panel-heading__count {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

/* ------------------------------------------------------- 문항 목록 */

.question-add-panel {
  display: grid;
  gap: var(--space-4);
  border: 1px solid var(--color-brand-border);
}

.question-add {
  display: grid;
  grid-template-columns: minmax(10rem, 0.5fr) minmax(0, 1fr);
  align-items: end;
  gap: var(--space-3) var(--space-4);
}

.question-add__text,
.question-add__submit {
  grid-column: 1 / -1;
}

.question-add__submit {
  justify-self: start;
}

.question-list__empty {
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-4);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.question-add__presets,
.evidence-quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: 0.25rem 0.7rem;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.chip:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.chip--brand {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.question-list {
  display: grid;
  gap: var(--space-2);
}

.question-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-1);
}

.question-list__select {
  display: grid;
  grid-template-columns: 1.5rem minmax(0, 1fr);
  gap: var(--space-2);
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3);
  text-align: left;
  transition:
    border-color var(--motion-fast),
    background var(--motion-fast);
}

.question-list__select:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.question-list__order {
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-fill);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.question-list__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.question-list__body strong {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  font-size: var(--font-size-sm);
  font-weight: 700;
  line-height: 1.45;
}

.question-list__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.question-list__meta small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.question-list__select--active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.question-list__select--active .question-list__order {
  background: var(--color-brand);
  color: white;
}

.question-list__move {
  display: grid;
  gap: var(--space-1);
}

.question-list__move button {
  width: 2rem;
  min-height: 1.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
}

.question-list__move button:hover:not(:disabled) {
  border-color: var(--color-brand-border);
  color: var(--color-brand-strong);
}

.question-list__move button:disabled {
  opacity: 0.45;
}

/* ---------------------------------------------------------- 답변 작업 */

.cover-editor__answer {
  display: grid;
  gap: var(--space-4);
}

.answer-brief {
  display: grid;
  gap: var(--space-4);
}

.answer-brief__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.answer-brief__eyebrow {
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-weight: 750;
}

.answer-brief__question {
  margin-top: var(--space-1);
  max-width: 44rem;
  font-size: 1.15rem;
  font-weight: 780;
  line-height: 1.5;
  letter-spacing: -0.01em;
  color: var(--color-ink-title);
  overflow-wrap: anywhere;
}

.answer-brief__memo {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3) var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.answer-brief__memo .icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  margin-top: 0.15rem;
  color: var(--color-text-muted);
}

.answer-brief__length {
  display: grid;
  gap: var(--space-2);
}

.answer-brief__length-copy {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.answer-brief__length-copy strong {
  font-variant-numeric: tabular-nums;
  font-weight: 780;
}

.answer-brief__length-copy small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.answer-brief__over {
  color: var(--color-danger-strong) !important;
  font-weight: 700;
}

.answer-brief__meter {
  height: 0.375rem;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--color-fill-strong);
}

.answer-brief__meter span {
  display: block;
  height: 100%;
  background: var(--color-brand);
}

.answer-brief__meter--over {
  background: var(--color-danger) !important;
}

.question-settings {
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.question-settings > summary {
  width: fit-content;
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.question-settings--readonly p {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.question-settings__note {
  margin-top: var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.question-meta__form {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(8rem, 0.5fr);
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.question-meta__text {
  grid-column: 1 / -1;
}

.question-meta__actions,
.answer-actions,
.draft-recovery__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.draft-recovery {
  display: grid;
  gap: var(--space-3);
}

.draft-recovery__lead strong {
  font-weight: 750;
}

.draft-recovery__lead p {
  margin-top: var(--space-1);
}

.draft-recovery__comparison,
.version-history__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.draft-recovery__comparison article,
.version-history__comparison article {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-4);
}

.draft-recovery__comparison h3,
.version-history__comparison h3 {
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.draft-recovery pre,
.version-history pre {
  max-height: 14rem;
  margin-top: var(--space-2);
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.answer-actions {
  align-items: center;
  justify-content: space-between;
  margin-top: 0;
}

.answer-actions__state {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.answer-actions__state .icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  color: var(--color-success);
}

.answer-actions__state[data-dirty='true'] {
  color: var(--color-warning-strong);
}

.answer-actions__state[data-dirty='true'] .icon {
  color: var(--color-warning);
}

/* -------------------------------------------------------- 코치 rail */

.rail-section {
  display: grid;
  gap: var(--space-3);
}

.rail-section__heading h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-md);
  font-weight: 760;
  color: var(--color-ink-title);
}

.rail-section__heading h2 .icon {
  width: 1.1rem;
  height: 1.1rem;
  flex: 0 0 auto;
  color: var(--color-brand);
}

.rail-section__step {
  display: grid;
  width: 1.375rem;
  height: 1.375rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: 0.6875rem;
  font-weight: 800;
}

.rail-section__heading h2 {
  flex: 1;
  min-width: 0;
}

.rail-section__count {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.rail-section__hint,
.rail-section__empty {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.rail-section__warning {
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

.rail-list {
  display: grid;
  gap: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.insight-list {
  display: grid;
  gap: var(--space-2);
}

.insight-list li {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.55;
}

.insight-list .icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  margin-top: 0.15rem;
  color: var(--color-success);
}

.insight-list--gap .icon {
  color: var(--color-warning);
}

.insight-gaps > summary {
  width: fit-content;
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.insight-gaps > ul {
  margin-top: var(--space-2);
}

.evidence-options {
  display: grid;
  gap: var(--space-2);
  max-height: 20rem;
  overflow-y: auto;
}

.evidence-options label {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  cursor: pointer;
}

.evidence-options label:hover {
  border-color: var(--color-brand-border);
}

.evidence-options__item--on {
  border-color: var(--color-brand) !important;
  background: var(--color-brand-soft);
}

.evidence-options__body {
  display: grid;
  gap: 0.125rem;
  min-width: 0;
}

.evidence-options__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.evidence-options__title strong {
  font-size: var(--font-size-sm);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.evidence-options__title em {
  border-radius: var(--radius-pill);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  padding: 0.05rem 0.45rem;
  font-size: 0.6875rem;
  font-style: normal;
  font-weight: 750;
}

.evidence-options small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  overflow-wrap: anywhere;
}

.evidence-options__snippet {
  line-height: 1.5;
}

.generation-questions,
.finalization__warnings {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  border: 0;
  padding: 0;
}

.generation-questions legend,
.finalization__warnings legend {
  width: 100%;
  margin-bottom: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.generation-questions label,
.finalization__warnings label,
.check-field {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.verification-card {
  display: grid;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
}

.verification-card header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
}

.verification-issues {
  display: grid;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.verification-issues > li {
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.verification-issues blockquote {
  margin-top: var(--space-1);
  border-left: 3px solid var(--color-warning-border);
  padding-left: var(--space-2);
}

.verification-issues p {
  margin-top: var(--space-1);
}

.verification-suggestions {
  display: grid;
  gap: var(--space-2);
}

.verification-suggestions h3 {
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.verification-suggestions > div {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  padding: var(--space-3);
  font-size: var(--font-size-sm);
}

.verification-suggestions .button {
  justify-self: start;
}

.verification-suggestions > small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.historical-evidence {
  display: grid;
  gap: var(--space-2);
}

.historical-evidence li {
  display: grid;
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-2);
  font-size: var(--font-size-sm);
}

.historical-evidence small {
  color: var(--color-warning-strong);
  font-size: var(--font-size-xs);
}

/* --------------------------------------------------------- 버전 이력 */

.version-history,
.finalization {
  margin-top: var(--space-6);
}

.version-history__layout {
  display: grid;
  grid-template-columns: minmax(11rem, 0.35fr) minmax(0, 1.65fr);
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.version-history__list {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  max-height: 22rem;
  overflow-y: auto;
}

.version-history__list button {
  display: grid;
  gap: var(--space-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3);
  text-align: left;
}

.version-history__list button:hover {
  border-color: var(--color-brand-border);
}

.version-history__label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.version-history__list small {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.version-history__current {
  color: var(--color-brand-strong) !important;
  font-weight: 700;
}

.version-history__item--active {
  border-color: var(--color-brand) !important;
  background: var(--color-brand-soft) !important;
}

.version-history__comparison .button {
  grid-column: 1 / -1;
  justify-self: start;
}

/* ------------------------------------------------------------ 최종화 */

.finalization {
  display: grid;
  gap: var(--space-4);
}

.finalization__checklist {
  display: grid;
  gap: var(--space-2);
}

.finalization__checklist li {
  display: grid;
  grid-template-columns: 1.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: var(--space-3) var(--space-4);
}

.finalization__order {
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-fill-strong);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.finalization__question {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: var(--font-size-sm);
}

.finalization__blockers {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-4) var(--space-6);
  list-style: disc;
  font-size: var(--font-size-sm);
}

.finalization .button {
  justify-self: start;
}

/* ------------------------------------------------------------ 반응형 */

@media (max-width: 80rem) {
  .cover-editor__workspace {
    grid-template-columns: minmax(12rem, 0.6fr) minmax(0, 1.4fr);
  }

  .cover-editor__rail {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 64rem) {
  .cover-editor__workspace {
    grid-template-columns: 1fr;
  }

  .cover-editor__navigator {
    position: static;
    max-height: none;
    overflow: visible;
  }

  .cover-editor__rail {
    grid-column: auto;
  }

  .coach__steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 48rem) {
  .cover-editor__rail,
  .question-add,
  .question-meta__form,
  .draft-recovery__comparison,
  .version-history__layout,
  .version-history__comparison,
  .cover-header__rename {
    grid-template-columns: 1fr;
  }

  .coach__body {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .coach__action {
    grid-column: 1 / -1;
    justify-items: stretch;
  }

  .coach__action .button {
    width: 100%;
  }

  .coach__note {
    max-width: none;
    text-align: left;
  }

  .question-meta__text,
  .version-history__comparison .button {
    grid-column: auto;
  }
}

@media (max-width: 40rem) {
  .cover-editor__archived,
  .panel-heading,
  .answer-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .cover-header__actions {
    width: 100%;
  }

  .cover-header__actions > * {
    flex: 1 1 8rem;
  }

  .coach__steps {
    grid-template-columns: 1fr;
  }

  .finalization__checklist li {
    grid-template-columns: 1.5rem minmax(0, 1fr);
  }

  .finalization__checklist li .status-badge {
    grid-column: 2;
  }

  .cover-header__rename .button,
  .question-meta__actions .button,
  .answer-actions .button,
  .draft-recovery__actions .button,
  .generation-command .button,
  .finalization .button {
    width: 100%;
  }
}
</style>
