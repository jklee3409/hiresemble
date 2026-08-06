<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'

import CoverLetterAssistPanel from '@/features/cover-letters/CoverLetterAssistPanel.vue'
import CoverLetterCompletionPanel from '@/features/cover-letters/CoverLetterCompletionPanel.vue'
import CoverLetterConflictPanel from '@/features/cover-letters/CoverLetterConflictPanel.vue'
import type {
  CoverLetterConflict,
  CoverLetterConflictKind,
} from '@/features/cover-letters/conflict'
import CoverLetterGenerationPanel from '@/features/cover-letters/CoverLetterGenerationPanel.vue'
import CoverLetterMaterialPicker from '@/features/cover-letters/CoverLetterMaterialPicker.vue'
import CoverLetterQuestionRail from '@/features/cover-letters/CoverLetterQuestionRail.vue'
import CoverLetterRunMonitor from '@/features/cover-letters/CoverLetterRunMonitor.vue'
import CoverLetterSheet from '@/features/cover-letters/CoverLetterSheet.vue'
import CoverLetterTipTapEditor from '@/features/cover-letters/CoverLetterTipTapEditor.vue'
import CoverLetterVersionPanel from '@/features/cover-letters/CoverLetterVersionPanel.vue'
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
  completionItems,
  freshVerification,
  questionWorkStatus,
  resolvePrimaryAction,
  type AssistTab,
  type CompletionItem,
  type WarningAcknowledgement,
} from '@/features/cover-letters/editorFlow'
import {
  ANSWER_SOURCE_LABELS,
  COVER_LETTER_STATUS_LABELS,
  coverLetterJobLabel,
} from '@/features/cover-letters/presentation'
import {
  invalidateCoverLetterQueries,
  useAnswerVersionListQuery,
  useArchiveCoverLetterMutation,
  useCoverLetterDetailQuery,
  useCoverLetterAiModelsQuery,
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
import type { AgentRunDetailDto } from '@/shared/api/agentRunContracts'
import type {
  CoverLetterAnswerVersionDto,
  CoverLetterDetailDto,
  CoverLetterQuestionDto,
  TipTapDocumentDto,
} from '@/shared/api/coverLetterContracts'
import { normalizeApiError, type ApiClientError } from '@/shared/api/errors'
import { listEvidence } from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { useNotifications } from '@/shared/ui/notifications'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

type EditorExpose = { insertSuggestion(suggestion: string): void }
type ConflictRetry = () => Promise<void>
type ConflictCancel = () => void
type SheetKind =
  | ''
  | 'QUESTIONS'
  | 'ASSIST'
  | 'ADD_QUESTION'
  | 'EDIT_QUESTION'
  | 'GENERATE'
  | 'VERSIONS'
  | 'COMPLETION'

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
  readonly model: string
  readonly avoidExperienceDuplication: boolean
  readonly baseCoverLetterVersion: number
}

interface VerificationSnapshot {
  readonly questionId: string
  readonly versionId: string
  readonly model: string
}

interface FinalizeSnapshot {
  readonly acknowledgedWarningVerificationIds: readonly string[]
  readonly baseCoverLetterVersion: number
}

interface LifecycleSnapshot {
  readonly action: string
  readonly baseCoverLetterVersion: number
}

const route = useRoute()
const cache = useQueryClient()
const authStore = useAuthStore()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const coverLetterId = computed(() => String(route.params.coverLetterId ?? ''))
const coverLetter = useCoverLetterDetailQuery(userId, coverLetterId)
const aiModels = useCoverLetterAiModelsQuery(userId)
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
const titleInput = ref<HTMLInputElement | null>(null)
const newQuestionInput = ref<HTMLTextAreaElement | null>(null)
const questionTextDraft = ref('')
const questionMaxLengthDraft = ref<string | number>('')
const questionMemoDraft = ref('')
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
const selectedModel = ref('')
const avoidExperienceDuplication = ref(true)
const acceptedRunId = ref('')
const acceptedRunKind = ref<'GENERATE' | 'VERIFY' | ''>('')
const warningAcknowledgements = ref(new Set<string>())
const actionError = ref('')
const conflict = ref<CoverLetterConflict | null>(null)
const conflictRetry = ref<ConflictRetry | null>(null)
const conflictCancel = ref<ConflictCancel | null>(null)
const conflictReapplying = ref(false)
const assistTab = ref<AssistTab>('JOB')
const materialOpen = ref(false)
const materialAnchor = ref<HTMLElement | null>(null)
const activeSheet = ref<SheetKind>('')
const assistCollapsed = ref(false)

const activeQuestions = computed(() =>
  [...(coverLetter.data.value?.questions ?? [])]
    .filter((question) => question.deletedAt === null)
    .sort((left, right) => left.questionOrder - right.questionOrder),
)
const selectedQuestion = computed(
  () => activeQuestions.value.find((question) => question.id === selectedQuestionId.value) ?? null,
)
const readOnly = computed(() => coverLetter.data.value?.status === 'ARCHIVED')
const versionFilters = computed(() => ({ page: 0, size: 100, sort: 'versionNo,desc' as const }))
const versions = useAnswerVersionListQuery(userId, selectedQuestionId, versionFilters)
const selectedVersion = computed(
  () =>
    versions.data.value?.items.find((version) => version.id === selectedVersionId.value) ??
    selectedQuestion.value?.currentAnswer ??
    null,
)
const verificationFilters = computed(() => ({
  page: 0,
  size: 100,
  sort: 'createdAt,desc' as const,
}))
const verificationVersionId = computed(() => selectedQuestion.value?.currentAnswer?.id ?? '')
const verifications = useVerificationListQuery(userId, verificationVersionId, verificationFilters)
/* 버전 기록에서 고른 과거 저장본의 검토 결과는 따로 조회한다. */
const historicalVerificationVersionId = computed(() =>
  selectedVersionId.value === verificationVersionId.value ? '' : selectedVersionId.value,
)
const historicalVerifications = useVerificationListQuery(
  userId,
  historicalVerificationVersionId,
  verificationFilters,
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
/*
 * AI가 초안을 쓰는 동안에는 답변 편집을 잠근다.
 * 사용자가 편집한 내용과 도착한 초안이 같은 문항에서 서로를 덮어쓰는 상황을 만들지 않는다.
 */
const generationInProgress = computed(() => {
  if (generateMutation.isPending.value || acceptedRunKind.value === 'GENERATE') return true
  const run = latestRun.data.value?.items[0]
  if (!run) return false
  return (
    run.workflowType === 'COVER_LETTER_GENERATION' &&
    ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(run.status)
  )
})
const answerLocked = computed(() => readOnly.value || generationInProgress.value)
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
const selectedQuestionIndex = computed(() =>
  activeQuestions.value.findIndex((question) => question.id === selectedQuestionId.value),
)
const questionCount = computed(() => activeQuestions.value.length)
const answeredCount = computed(
  () => activeQuestions.value.filter((question) => question.currentAnswer !== null).length,
)
const reviewedCount = computed(
  () =>
    activeQuestions.value.filter((question) => {
      const verification = freshVerification(question)
      return verification !== null && ['PASSED', 'WARNING'].includes(verification.status)
    }).length,
)
const dirtyQuestionId = computed(() => (editorDirty.value ? selectedQuestionId.value : ''))

/* 편집 중이 아닐 때는 편집기 footer와 같은 서버 글자 수를 보여 준다. */
const selectedServerContent = computed(
  () => selectedQuestion.value?.currentAnswer?.contentJson ?? EMPTY_TIPTAP_DOCUMENT,
)
const selectedServerCount = computed(
  () => selectedQuestion.value?.currentAnswer?.characterCount ?? 0,
)
const displayedCharacterCount = computed(() =>
  editorDirty.value ? editorCharacterCount.value : selectedServerCount.value,
)
const editorOverLimit = computed(
  () =>
    selectedQuestion.value?.maxLength !== null &&
    selectedQuestion.value?.maxLength !== undefined &&
    displayedCharacterCount.value > selectedQuestion.value.maxLength,
)
const titleDirty = computed(
  () => coverLetter.data.value !== undefined && titleDraft.value !== coverLetter.data.value.title,
)
const saveStateLabel = computed(() => {
  if (editorDirty.value) return '저장 안 됨 · 이 브라우저에만 있어요'
  const answer = selectedQuestion.value?.currentAnswer
  if (!answer) return '아직 저장한 답변이 없어요'
  return `저장됨 · v${answer.versionNo} ${ANSWER_SOURCE_LABELS[answer.sourceType]}`
})
const warningAcknowledgementTargets = computed<WarningAcknowledgement[]>(() =>
  activeQuestions.value.flatMap((question) => {
    const verification = freshVerification(question)
    if (verification === null || verification.status !== 'WARNING') return []
    return [
      {
        verificationId: verification.id,
        questionId: question.id,
        questionOrder: question.questionOrder,
      },
    ]
  }),
)
const completion = computed<CompletionItem[]>(() =>
  completionItems({
    questions: activeQuestions.value,
    status: coverLetter.data.value?.status ?? 'DRAFT',
    dirtyQuestionId: dirtyQuestionId.value,
    acknowledgedWarningIds: warningAcknowledgements.value,
  }),
)
const canFinalizeNow = computed(
  () =>
    coverLetter.data.value?.status === 'DRAFT' &&
    coverLetter.data.value.canFinalize &&
    completion.value.length === 0,
)
const aiUnavailableReason = computed(() => {
  if (latestRun.isError.value)
    return 'AI 진행 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.'
  return aiActionUnavailable.value ? '진행 중인 AI 작업이 끝나면 이어서 요청할 수 있어요.' : ''
})
const primaryAction = computed(() =>
  resolvePrimaryAction({
    status: coverLetter.data.value?.status ?? 'DRAFT',
    canUnarchive: coverLetter.data.value?.canUnarchive ?? false,
    canFinalize: canFinalizeNow.value,
    questions: activeQuestions.value,
    selectedQuestionId: selectedQuestionId.value,
    editorDirty: editorDirty.value,
    editorOverLimit: editorOverLimit.value,
    generationInProgress: generationInProgress.value,
    aiBusy: aiActionUnavailable.value,
    aiUnavailableReason: aiUnavailableReason.value,
    savePending: saveVersionMutation.isPending.value,
    finalizePending: finalizeMutation.isPending.value,
    unarchivePending: unarchiveMutation.isPending.value,
    completion: completion.value,
  }),
)
const selectedQuestionStatus = computed(() =>
  selectedQuestion.value === null
    ? null
    : questionWorkStatus(selectedQuestion.value, { dirty: editorDirty.value }),
)
const canRegenerate = computed(
  () =>
    !readOnly.value &&
    selectedQuestion.value !== null &&
    selectedQuestion.value.currentAnswer !== null &&
    primaryAction.value.kind !== 'GENERATE',
)

const requirementHighlights = computed(() => {
  const detail = analysis.data.value
  if (!detail) return []
  return [...(detail.requiredQualifications ?? []), ...(detail.responsibilities ?? [])].slice(0, 6)
})
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
const usedEvidence = computed(() => {
  const question = selectedQuestion.value
  if (question === null) return []
  const verification = freshVerification(question)
  if (verification === null) return []
  const seen = new Set<string>()
  return [
    ...verification.evidenceRefs,
    ...verification.issues.flatMap((issue) => issue.evidenceRefs),
  ].filter((reference) => (seen.has(reference.id) ? false : seen.add(reference.id)))
})

watch(
  () => aiModels.data.value,
  (models) => {
    if (!models || models.length === 0) return
    if (!models.some((model) => model.id === selectedModel.value)) {
      selectedModel.value = models.find((model) => model.recommended)?.id ?? models[0]?.id ?? ''
    }
  },
  { immediate: true },
)

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
  },
  { immediate: true },
)

watch(
  [
    selectedQuestionId,
    () => selectedQuestion.value?.version,
    () => selectedQuestion.value?.currentAnswer?.id,
  ],
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
        ? '저장하지 않은 답변이 남아 있어요.'
        : '저장하지 않은 답변이 있는데, 그 사이에 답변이 새로 저장됐어요.'
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

/* 문항 목록 · 작성 도움 sheet는 좁은 화면 전용이므로 문항을 고르면 닫는다. */
watch(selectedQuestionId, () => {
  if (activeSheet.value === 'QUESTIONS') activeSheet.value = ''
})

watch(selectedQuestionId, () => (materialOpen.value = false))

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('mousedown', onDocumentPointerDown)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('mousedown', onDocumentPointerDown)
})

onBeforeRouteLeave(async () => {
  if (!editorDirty.value) return true
  return notifications.confirm({
    title: '저장하지 않은 답변이 있어요',
    message:
      '지금 나가면 쓰던 내용은 이 브라우저에만 남아요. 이 문항으로 돌아오면 다시 불러올 수 있어요.',
    confirmLabel: '저장하지 않고 나가기',
    cancelLabel: '여기 남기',
    tone: 'primary',
  })
})

function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (!editorDirty.value) return
  event.preventDefault()
}

async function selectQuestion(questionId: string): Promise<void> {
  if (questionId === selectedQuestionId.value) return
  if (editorDirty.value) {
    const leave = await notifications.confirm({
      title: '저장하지 않은 답변이 있어요',
      message:
        '다른 문항으로 이동하면 쓰던 내용은 이 브라우저에만 남아요. 이 문항으로 돌아오면 다시 불러올 수 있어요.',
      confirmLabel: '저장하지 않고 이동',
      cancelLabel: '여기 남기',
      tone: 'primary',
    })
    if (!leave) return
  }
  selectedQuestionId.value = questionId
}

async function runPrimaryAction(): Promise<void> {
  const action = primaryAction.value
  if (action.disabled || action.kind === 'NONE') return
  if (action.kind === 'ADD_QUESTION') {
    await openAddQuestion()
    return
  }
  if (action.kind === 'SAVE_ANSWER') {
    await saveAnswer()
    return
  }
  if (action.kind === 'GENERATE' || action.kind === 'REGENERATE') {
    openGenerationSheet()
    return
  }
  if (action.kind === 'VERIFY') {
    await verifyCurrentAnswer()
    return
  }
  if (action.kind === 'GO_TO_QUESTION') {
    await focusQuestion(action.targetQuestionId)
    return
  }
  if (action.kind === 'OPEN_COMPLETION' || action.kind === 'FINALIZE') {
    activeSheet.value = 'COMPLETION'
    return
  }
  if (action.kind === 'UNARCHIVE') await unarchiveCover()
}

async function focusQuestion(questionId: string): Promise<void> {
  activeSheet.value = ''
  await selectQuestion(questionId)
  await nextTick()
  document.querySelector<HTMLElement>(`[data-question-tab="${questionId}"]`)?.focus()
}

async function openAddQuestion(): Promise<void> {
  activeSheet.value = 'ADD_QUESTION'
  await nextTick()
  newQuestionInput.value?.focus()
}

function openGenerationSheet(): void {
  const question = selectedQuestion.value
  generationQuestionIds.value = new Set(question ? [question.id] : [])
  activeSheet.value = 'GENERATE'
}

function closeSheet(): void {
  activeSheet.value = ''
  deleteConfirmationId.value = ''
}

async function startRenaming(): Promise<void> {
  titleDraft.value = coverLetter.data.value?.title ?? ''
  renamingTitle.value = true
  await nextTick()
  titleInput.value?.focus()
}

async function startRenamingFromSheet(): Promise<void> {
  closeSheet()
  await startRenaming()
}

async function submitTitleRename(): Promise<void> {
  await saveTitle()
  if (conflict.value === null && actionError.value === '') renamingTitle.value = false
}

function cancelRenaming(): void {
  titleDraft.value = coverLetter.data.value?.title ?? ''
  renamingTitle.value = false
}

function applyLengthPreset(value: number): void {
  newQuestionMaxLength.value = String(value)
}

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
    draftNotice.value = ''
    return
  }
  saveCoverLetterDraft({
    userId: userId.value,
    coverLetterId: coverLetterId.value,
    questionId: question.id,
    baseVersionId: question.currentAnswer?.id ?? null,
    contentJson: content,
  })
  draftNotice.value = '아직 저장하지 않았어요. 답변 저장을 눌러야 이 내용이 남아요.'
}

function applyDraftCandidate(): void {
  if (!draftCandidate.value) return
  const canonical = canonicalizeEditorContent(draftCandidate.value.contentJson)
  editorContent.value = canonical.document
  editorCharacterCount.value = canonical.characterCount
  editorDirty.value = !sameTipTapContent(canonical.document, selectedServerContent.value)
  draftNotice.value = draftCandidate.value.baseMatches
    ? '쓰던 내용을 다시 불러왔어요. 답변 저장을 눌러야 남아요.'
    : '그 사이에 답변이 바뀌었어요. 불러온 내용을 확인한 뒤 저장해 주세요.'
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
  draftNotice.value = ''
  notifications.toast('쓰던 내용을 버리고 저장된 답변을 그대로 두었어요.', 'info')
}

async function saveTitle(): Promise<void> {
  const detail = coverLetter.data.value
  const title = titleDraft.value.trim()
  if (!detail || readOnly.value || title.length === 0) return
  const snapshot: TitleMutationSnapshot = Object.freeze({ title, baseVersion: detail.version })
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
      request: { title: snapshot.title, version: expectedVersion },
    })
    titleDraft.value = result.title
    notifications.toast('제목을 저장했어요.', 'success')
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
    activeSheet.value = ''
    notifications.toast('문항을 추가했어요.', 'success')
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
        activeSheet.value = ''
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
    notifications.toast('문항 정보를 저장했어요.', 'success')
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
    activeSheet.value = ''
    await coverLetter.refetch()
    notifications.toast('문항을 삭제했어요. 지금까지 저장한 답변 기록은 그대로 남아요.', 'success')
    clearConflictState()
  } catch (error) {
    await handleConflict('QUESTION', error, {
      localSnapshot: `삭제할 문항\nID: ${snapshot.questionId}\n내용: ${snapshot.questionText}\n기준 문항 version: ${snapshot.baseQuestionVersion}`,
      serverSnapshot: (latest) =>
        formatQuestionSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async (latest) => {
        const latestQuestion = findActiveQuestion(latest, snapshot.questionId)
        if (!latestQuestion) {
          notifications.toast('이 문항은 이미 삭제되어 있었어요.', 'info')
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
    notifications.toast('문항 순서를 저장했어요.', 'success')
    clearConflictState()
  } catch (error) {
    await handleConflict('ORDER', error, {
      localSnapshot: `내가 바꾼 순서\n${formatSavedQuestionOrderSnapshot(snapshot)}\n기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
      serverSnapshot: (latest) =>
        latest
          ? `지금 저장된 순서\n${formatQuestionOrderSnapshot(latest.questions)}\n자기소개서 version: ${latest.version}`
          : '지금 저장된 문항 순서를 찾지 못했어요.',
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
    notifications.toast(
      `버전 ${answer.versionNo}을 저장했어요. 이 답변을 AI 검토까지 받아야 작성 완료할 수 있어요.`,
      'success',
    )
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
    notifications.toast(
      `버전 ${snapshot.versionNo}의 내용으로 되돌린 답변을 새로 저장했어요. 과거 답변은 그대로 남아 있어요.`,
      'success',
    )
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
    selectedModel.value === '' ||
    aiActionUnavailable.value
  )
    return
  const snapshot: GenerationSnapshot = Object.freeze({
    questionIds: Object.freeze([...generationQuestionIds.value]),
    preferredEvidenceIds: Object.freeze([...selectedEvidenceIds.value]),
    model: selectedModel.value,
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
        model: snapshot.model,
        avoidExperienceDuplication: snapshot.avoidExperienceDuplication,
        coverLetterVersion: expectedCoverLetterVersion,
      },
    })
    acceptedRunId.value = accepted.agentRunId
    acceptedRunKind.value = 'GENERATE'
    activeSheet.value = ''
    notifications.toast(
      'AI가 초안을 쓰기 시작했어요. 다 쓸 때까지 답변 편집은 잠시 멈춰 둘게요.',
      'success',
    )
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
  if (
    !detail ||
    !question ||
    !answer ||
    readOnly.value ||
    aiActionUnavailable.value ||
    selectedModel.value === ''
  )
    return
  const snapshot: VerificationSnapshot = Object.freeze({
    questionId: question.id,
    versionId: answer.id,
    model: selectedModel.value,
  })
  await submitVerification(snapshot)
}

async function submitVerification(snapshot: VerificationSnapshot): Promise<void> {
  clearMessages()
  try {
    const accepted = await verifyMutation.mutateAsync({
      coverLetterId: coverLetterId.value,
      versionId: snapshot.versionId,
      request: { model: snapshot.model },
    })
    acceptedRunId.value = accepted.agentRunId
    acceptedRunKind.value = 'VERIFY'
    assistTab.value = 'REVIEW'
    notifications.toast('지금 저장된 답변을 AI가 살펴보고 있어요.', 'success')
    clearConflictState()
  } catch (error) {
    await handleConflict('ANSWER', error, {
      localSnapshot: `검토할 답변 ID: ${snapshot.versionId}\nAI 모델: ${snapshot.model}`,
      serverSnapshot: (latest) =>
        formatAnswerSnapshot(findActiveQuestion(latest, snapshot.questionId)),
      retry: async () => submitVerification(snapshot),
      cancel: (latest) => syncAnswerFromServer(latest, snapshot.questionId),
    })
  }
}

function applySuggestion(suggestion: string): void {
  if (answerLocked.value) return
  editorRef.value?.insertSuggestion(suggestion)
  notifications.toast('제안을 편집기에 넣었어요. 내용을 다듬은 뒤 저장해 주세요.', 'info')
}

function toggleEvidence(id: string): void {
  if (readOnly.value) return
  const next = new Set(selectedEvidenceIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedEvidenceIds.value = next
}

function clearSelectedEvidence(): void {
  if (readOnly.value) return
  selectedEvidenceIds.value = new Set<string>()
}

/* 소재 고르기는 편집기 아래에서 펼쳐지며 다른 내용을 밀어내지 않는다. */
function toggleMaterialPicker(): void {
  materialOpen.value = !materialOpen.value
}

function closeMaterialPicker(): void {
  materialOpen.value = false
}

function onDocumentPointerDown(event: MouseEvent): void {
  if (!materialOpen.value) return
  const anchor = materialAnchor.value
  if (anchor && event.target instanceof Node && !anchor.contains(event.target)) {
    materialOpen.value = false
  }
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
      warningAcknowledgementTargets.value
        .map((warning) => warning.verificationId)
        .filter((id) => warningAcknowledgements.value.has(id)),
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
    activeSheet.value = ''
    notifications.toast(
      '자기소개서를 작성 완료로 표시했어요. 공고의 지원 상태는 공고 화면에서 따로 바꿔 주세요.',
      'success',
    )
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
    notifications.toast('보관함으로 옮겼어요. 이제 읽기 전용이에요.', 'success')
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
    action: '다시 쓰기',
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
    notifications.toast('다시 쓸 수 있게 되돌렸어요.', 'success')
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
  const wasGeneration = acceptedRunKind.value === 'GENERATE'
  acceptedRunId.value = ''
  acceptedRunKind.value = ''
  await invalidateCoverLetterQueries(cache, userId.value, coverLetterId.value)
  await latestRun.refetch()
  await coverLetter.refetch()
  if (selectedQuestionId.value) await versions.refetch()
  if (verificationVersionId.value) await verifications.refetch()
  if (run.partialResult?.failedScopeKeys.length) {
    generationQuestionIds.value = new Set(run.partialResult.failedScopeKeys)
    notifications.toast(
      '일부 문항만 초안이 나왔어요. 완성된 답변은 그대로 두고 남은 문항만 다시 고를 수 있어요.',
      'error',
    )
    return
  }
  if (run.status === 'FAILED' || run.status === 'CANCELLED' || run.status === 'INTERRUPTED') {
    notifications.toast(
      wasGeneration
        ? '초안 작성을 끝내지 못했어요. 지금까지 저장한 답변은 그대로 남아 있어요.'
        : '검토를 끝내지 못했어요. 저장한 답변은 그대로 남아 있어요.',
      'error',
    )
  }
}

async function handleConflict(
  kind: CoverLetterConflictKind,
  error: unknown,
  resolution: ConflictResolution,
): Promise<void> {
  const apiError = normalizeApiError(error)
  /* 오류 안내와 비교 화면은 보조 sheet 뒤에 가려지면 안 된다. */
  activeSheet.value = ''
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
    `되돌릴 답변 ID: ${snapshot.versionId}`,
    `되돌릴 version: ${snapshot.versionNo}`,
    `되돌릴 내용:\n${snapshot.plainText}`,
  ].join('\n')
}

function formatGenerationSnapshot(snapshot: GenerationSnapshot): string {
  return [
    '선택 문항 AI 초안 생성',
    `문항 IDs: ${snapshot.questionIds.join(', ')}`,
    `선호 근거 IDs: ${snapshot.preferredEvidenceIds.join(', ') || '없음'}`,
    `AI 모델: ${snapshot.model}`,
    `경험 중복 최소화: ${snapshot.avoidExperienceDuplication ? '예' : '아니요'}`,
    `기준 자기소개서 version: ${snapshot.baseCoverLetterVersion}`,
  ].join('\n')
}

function formatFinalizeSnapshot(snapshot: FinalizeSnapshot): string {
  return [
    '작성 완료 요청',
    `확인한 확인 필요 항목 IDs: ${snapshot.acknowledgedWarningVerificationIds.join(', ') || '없음'}`,
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
    return '최신 답변과 검토 상태를 다시 확인해 주세요.'
  }
  if (error.code === 'AI_MODEL_NOT_SUPPORTED') {
    return '현재 사용할 수 없는 AI 모델입니다. 모델 목록을 새로고침한 뒤 다시 골라 주세요.'
  }
  return error.message
}
</script>

<template>
  <section class="cover-editor app-page" aria-label="자기소개서 작성">
    <StatePanel
      v-if="coverLetter.isLoading.value"
      kind="loading"
      title="자기소개서를 불러오는 중…"
      description="문항과 지금까지 쓴 답변을 확인하고 있어요."
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
      <header class="cover-topbar">
        <div class="cover-topbar__identity">
          <RouterLink class="cover-topbar__back" :to="{ name: 'cover-letters' }">
            <AppIcon name="arrow-left" />
            <span>자기소개서 목록</span>
          </RouterLink>
          <form
            v-if="renamingTitle && !readOnly"
            class="cover-topbar__rename"
            @submit.prevent="submitTitleRename()"
          >
            <label class="field">
              <span class="sr-only-focusable">자기소개서 제목</span>
              <input
                ref="titleInput"
                v-model="titleDraft"
                class="control control--compact"
                maxlength="300"
                aria-label="자기소개서 제목"
              />
            </label>
            <button
              type="submit"
              class="button button--secondary button--compact"
              :disabled="!titleDirty || updateCoverMutation.isPending.value"
            >
              {{ updateCoverMutation.isPending.value ? '저장 중…' : '제목 저장' }}
            </button>
            <button
              type="button"
              class="button button--ghost button--compact"
              @click="cancelRenaming()"
            >
              취소
            </button>
          </form>
          <div v-else class="cover-topbar__title-row">
            <h1 class="cover-topbar__title">{{ coverLetter.data.value.title }}</h1>
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
          <p class="cover-topbar__job">
            <span>{{ jobLabel }}</span>
            <span aria-hidden="true">·</span>
            <span>답변 {{ answeredCount }}/{{ questionCount }}</span>
            <span aria-hidden="true">·</span>
            <span>검토 {{ reviewedCount }}/{{ questionCount }}</span>
            <span aria-hidden="true">·</span>
            <span :data-dirty="editorDirty">{{ saveStateLabel }}</span>
          </p>
        </div>

        <div class="cover-topbar__actions">
          <button
            v-if="coverLetter.data.value.status !== 'ARCHIVED'"
            type="button"
            class="cover-topbar__completion"
            :data-ready="completion.length === 0"
            data-testid="open-completion"
            @click="activeSheet = 'COMPLETION'"
          >
            <AppIcon :name="completion.length === 0 ? 'check' : 'flag'" />
            <span>
              {{
                coverLetter.data.value.status === 'FINALIZED'
                  ? '작성 완료됨'
                  : completion.length === 0
                    ? '작성 완료할 수 있어요'
                    : `완료까지 ${completion.length}가지 남았어요`
              }}
            </span>
          </button>
          <button
            v-if="primaryAction.label && primaryAction.kind !== 'SAVE_ANSWER'"
            type="button"
            class="button button--primary"
            :disabled="primaryAction.disabled"
            :title="primaryAction.hint"
            data-testid="primary-action"
            @click="runPrimaryAction()"
          >
            {{ primaryAction.label }}
          </button>
        </div>
        <p v-if="primaryAction.hint" class="cover-topbar__hint">{{ primaryAction.hint }}</p>
      </header>

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
      <section v-if="readOnly" class="alert alert--warning cover-editor__message" role="status">
        <div>
          <strong>보관된 자기소개서예요 · 읽기 전용</strong>
          <p>제목과 문항, 답변 저장, AI 초안·검토와 작성 완료는 사용할 수 없어요.</p>
        </div>
      </section>

      <div class="cover-workspace" data-testid="cover-letter-editor">
        <aside class="cover-workspace__rail">
          <CoverLetterQuestionRail
            :questions="activeQuestions"
            :selected-question-id="selectedQuestionId"
            :dirty-question-id="dirtyQuestionId"
            :can-add="!readOnly && questionCount < 20"
            @select="selectQuestion"
            @add="openAddQuestion()"
          />
        </aside>

        <main class="cover-workspace__main">
          <div class="cover-workspace__mobile-nav">
            <button
              type="button"
              class="button button--secondary button--compact"
              data-testid="open-questions"
              @click="activeSheet = 'QUESTIONS'"
            >
              문항 {{ selectedQuestionIndex + 1 }}/{{ questionCount }}
            </button>
            <button
              type="button"
              class="button button--secondary button--compact"
              data-testid="open-assist"
              @click="activeSheet = 'ASSIST'"
            >
              작성 도움
            </button>
          </div>

          <template v-if="selectedQuestion">
            <div
              :id="`question-panel-${selectedQuestion.id}`"
              class="answer-brief"
              role="tabpanel"
              :aria-labelledby="`question-tab-${selectedQuestion.id}`"
            >
              <div class="answer-brief__head">
                <h2 class="answer-brief__question">
                  <span class="answer-brief__order">{{ selectedQuestion.questionOrder }}</span>
                  <span>{{ selectedQuestion.questionText }}</span>
                </h2>
                <div class="answer-brief__tools">
                  <StatusBadge
                    v-if="selectedQuestionStatus"
                    :label="selectedQuestionStatus.label"
                    :tone="selectedQuestionStatus.tone"
                  />
                  <button
                    v-if="!readOnly"
                    type="button"
                    class="button button--ghost button--compact"
                    data-testid="open-question-form"
                    @click="activeSheet = 'EDIT_QUESTION'"
                  >
                    문항 수정
                  </button>
                </div>
              </div>
              <p v-if="selectedQuestion.memo" class="answer-brief__memo">
                <AppIcon name="pen" />
                <span>{{ selectedQuestion.memo }}</span>
              </p>
            </div>

            <CoverLetterRunMonitor
              v-if="currentRunId"
              :key="currentRunId"
              class="cover-workspace__run"
              :user-id="userId"
              :cover-letter-id="coverLetterId"
              :agent-run-id="currentRunId"
              :question-labels="questionLabels"
              @terminal="handleRunTerminal"
            />

            <p v-if="generationInProgress" class="answer-lock alert alert--info" role="status">
              <AppIcon name="sparkle" />
              <span>
                AI가 초안을 쓰는 동안에는 답변을 고칠 수 없어요. 새 답변이 도착하면 이전 답변도 버전
                기록에 그대로 남아요.
              </span>
            </p>

            <section
              v-if="draftCandidate"
              class="draft-recovery alert alert--warning"
              role="status"
            >
              <div class="draft-recovery__lead">
                <strong>저장하지 않은 내용이 남아 있어요</strong>
                <p>{{ draftNotice }}</p>
              </div>
              <div class="draft-recovery__comparison">
                <article>
                  <h3>저장된 답변</h3>
                  <pre>{{ selectedQuestion.currentAnswer?.plainText ?? '(저장된 답변 없음)' }}</pre>
                </article>
                <article>
                  <h3>내가 쓰던 내용</h3>
                  <pre>{{ canonicalizeEditorContent(draftCandidate.contentJson).plainText }}</pre>
                </article>
              </div>
              <div class="draft-recovery__actions">
                <button type="button" class="button button--secondary" @click="applyDraftCandidate">
                  쓰던 내용으로 이어 쓰기
                </button>
                <button type="button" class="button button--ghost" @click="discardDraftCandidate">
                  저장된 답변 그대로 두기
                </button>
              </div>
            </section>

            <CoverLetterTipTapEditor
              ref="editorRef"
              class="cover-workspace__editor"
              :content="editorContent"
              :readonly="answerLocked"
              :max-length="selectedQuestion.maxLength"
              :server-character-count="editorDirty ? null : selectedServerCount"
              @update="onEditorUpdate"
            />

            <div class="answer-actions">
              <div class="answer-actions__state">
                <p :data-dirty="editorDirty">
                  <AppIcon :name="editorDirty ? 'clock' : 'check'" />
                  <span>{{ saveStateLabel }}</span>
                </p>
                <p class="answer-actions__length" :data-over="editorOverLimit">
                  {{ displayedCharacterCount }}자<template v-if="selectedQuestion.maxLength">
                    / {{ selectedQuestion.maxLength }}자</template
                  >
                  <small v-if="editorOverLimit">
                    {{ displayedCharacterCount - (selectedQuestion.maxLength ?? 0) }}자 넘었어요
                  </small>
                  <small v-else-if="selectedQuestion.maxLength">
                    {{ Math.max(0, selectedQuestion.maxLength - displayedCharacterCount) }}자 더 쓸
                    수 있어요
                  </small>
                  <small v-else>글자 수 제한이 없어요</small>
                </p>
              </div>
              <div class="answer-actions__buttons">
                <button
                  type="button"
                  class="button button--ghost button--compact"
                  data-testid="open-versions"
                  @click="activeSheet = 'VERSIONS'"
                >
                  <AppIcon name="history" />버전 기록
                </button>
                <button
                  v-if="canRegenerate"
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="aiActionUnavailable"
                  data-testid="open-generation"
                  @click="openGenerationSheet()"
                >
                  AI로 다시 쓰기
                </button>
                <button
                  v-if="!readOnly"
                  type="button"
                  class="button"
                  :class="editorDirty ? 'button--primary' : 'button--secondary'"
                  :disabled="
                    !editorDirty ||
                    editorOverLimit ||
                    answerLocked ||
                    saveVersionMutation.isPending.value
                  "
                  data-testid="save-answer-version"
                  @click="saveAnswer()"
                >
                  {{ saveVersionMutation.isPending.value ? '저장 중…' : '답변 저장' }}
                </button>
                <button
                  v-if="!readOnly"
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="!selectedQuestion.currentAnswer || aiActionUnavailable || editorDirty"
                  data-testid="verify-answer-version"
                  @click="verifyCurrentAnswer()"
                >
                  AI 검토 받기
                </button>
              </div>
            </div>

            <div v-if="!readOnly" ref="materialAnchor" class="material-anchor">
              <button
                type="button"
                class="button button--ghost button--compact material-anchor__trigger"
                :aria-expanded="materialOpen"
                aria-controls="cover-letter-material-picker"
                data-testid="open-material-picker"
                @click="toggleMaterialPicker()"
              >
                <AppIcon name="evidence" />
                답변에 사용할 소재
                <span v-if="selectedEvidenceIds.size">{{ selectedEvidenceIds.size }}개 선택</span>
                <AppIcon :name="materialOpen ? 'arrow-left' : 'arrow-right'" />
              </button>
              <div
                v-if="materialOpen"
                id="cover-letter-material-picker"
                class="material-anchor__panel"
                role="group"
                aria-label="답변에 사용할 소재"
                @keydown.esc.stop.prevent="closeMaterialPicker()"
              >
                <CoverLetterMaterialPicker
                  :used-evidence="usedEvidence"
                  :evidence-items="evidenceItems"
                  :recommended-evidence-ids="recommendedEvidenceIds"
                  :selected-evidence-ids="selectedEvidenceIds"
                  :loading="evidence.isLoading.value"
                  :error="evidence.isError.value"
                  :read-only="readOnly"
                  @toggle="toggleEvidence"
                  @clear="clearSelectedEvidence()"
                />
              </div>
            </div>
          </template>

          <StatePanel
            v-else
            kind="empty"
            title="문항을 추가하면 작성을 시작할 수 있어요."
            description="공고에 적힌 문항을 그대로 옮겨 적으면 같은 기준으로 초안을 써 드려요."
          >
            <template #actions>
              <button
                v-if="!readOnly"
                type="button"
                class="button button--primary"
                @click="openAddQuestion()"
              >
                문항 추가
              </button>
            </template>
          </StatePanel>
        </main>

        <aside class="cover-workspace__assist" :data-collapsed="assistCollapsed">
          <div class="cover-workspace__assist-inner">
            <button
              type="button"
              class="cover-workspace__assist-toggle"
              :aria-expanded="!assistCollapsed"
              @click="assistCollapsed = !assistCollapsed"
            >
              {{ assistCollapsed ? '작성 도움 펴기' : '작성 도움 접기' }}
            </button>
            <CoverLetterAssistPanel
              v-if="!assistCollapsed"
              :tab="assistTab"
              :requirements="requirementHighlights"
              :gaps="analysisGaps"
              :analysis-outdated="job.data.value?.analysisOutdated ?? false"
              :job-id="jobId"
              :verifications="verifications.data.value?.items ?? []"
              :verifications-loading="verifications.isLoading.value"
              :has-answer="selectedQuestion?.currentAnswer !== null"
              :reviewed-version-label="
                selectedQuestion?.currentAnswer
                  ? `지금 답변(v${selectedQuestion.currentAnswer.versionNo}) 기준 결과예요.`
                  : ''
              "
              :read-only="readOnly"
              :can-apply-suggestion="!answerLocked"
              @update:tab="assistTab = $event"
              @apply-suggestion="applySuggestion"
              @verify="verifyCurrentAnswer()"
            />
          </div>
        </aside>
      </div>

      <CoverLetterSheet
        :open="activeSheet === 'QUESTIONS'"
        title="문항 고르기"
        description="문항을 고르면 그 문항의 답변을 이어서 쓸 수 있어요."
        @close="closeSheet()"
      >
        <CoverLetterQuestionRail
          :questions="activeQuestions"
          :selected-question-id="selectedQuestionId"
          :dirty-question-id="dirtyQuestionId"
          :can-add="!readOnly && questionCount < 20"
          id-prefix="sheet-question-tab"
          variant="list"
          @select="selectQuestion"
          @add="openAddQuestion()"
        />
      </CoverLetterSheet>

      <CoverLetterSheet
        :open="activeSheet === 'ASSIST'"
        title="작성 도움"
        description="공고 요구사항, 연결된 경험과 AI 검토 결과를 볼 수 있어요."
        @close="closeSheet()"
      >
        <CoverLetterAssistPanel
          :tab="assistTab"
          :requirements="requirementHighlights"
          :gaps="analysisGaps"
          :analysis-outdated="job.data.value?.analysisOutdated ?? false"
          :job-id="jobId"
          :verifications="verifications.data.value?.items ?? []"
          :verifications-loading="verifications.isLoading.value"
          :has-answer="selectedQuestion?.currentAnswer !== null"
          :read-only="readOnly"
          :can-apply-suggestion="!answerLocked"
          @update:tab="assistTab = $event"
          @apply-suggestion="applySuggestion"
          @verify="verifyCurrentAnswer()"
        />
      </CoverLetterSheet>

      <CoverLetterSheet
        :open="activeSheet === 'ADD_QUESTION'"
        title="문항 추가"
        description="공고에 적힌 문항을 그대로 옮겨 적어 주세요."
        placement="center"
        @close="closeSheet()"
      >
        <form id="cover-letter-add-question" class="question-add" @submit.prevent="addQuestion()">
          <label class="field">
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
            <span class="field__help">비워 두면 제한 없이 쓸 수 있어요.</span>
          </label>
          <div class="question-add__presets">
            <button
              v-for="preset in [500, 700, 1000, 1500]"
              :key="preset"
              type="button"
              class="button button--ghost button--compact"
              @click="applyLengthPreset(preset)"
            >
              {{ preset }}자
            </button>
          </div>
          <label class="field">
            <span class="field__label">메모</span>
            <textarea
              v-model="newQuestionMemo"
              class="control"
              maxlength="2000"
              rows="2"
              placeholder="강조하고 싶은 방향이나 기억할 점을 적어 두세요. AI 초안에도 함께 참고해요."
            />
          </label>
        </form>
        <template #footer>
          <button type="button" class="button button--ghost" @click="closeSheet()">취소</button>
          <button
            type="submit"
            form="cover-letter-add-question"
            class="button button--primary"
            :disabled="createQuestionMutation.isPending.value"
          >
            추가
          </button>
        </template>
      </CoverLetterSheet>

      <CoverLetterSheet
        v-if="selectedQuestion"
        :open="activeSheet === 'EDIT_QUESTION'"
        title="문항 수정"
        description="문항을 지워도 지금까지 저장한 답변 기록은 그대로 남아요."
        placement="center"
        @close="closeSheet()"
      >
        <div class="question-meta__form">
          <label class="field question-meta__text">
            <span class="field__label">문항 내용</span>
            <textarea v-model="questionTextDraft" class="control" rows="3" maxlength="2000" />
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
            <textarea v-model="questionMemoDraft" class="control" rows="2" maxlength="2000" />
          </label>
        </div>
        <div v-if="questionCount > 1" class="question-meta__order">
          <span class="field__label">문항 순서</span>
          <div>
            <button
              type="button"
              class="button button--secondary button--compact"
              :disabled="selectedQuestionIndex === 0 || reorderMutation.isPending.value"
              :aria-label="`${selectedQuestion.questionOrder}번 문항 앞으로 이동`"
              @click="moveQuestion(selectedQuestion.id, -1)"
            >
              앞으로
            </button>
            <button
              type="button"
              class="button button--secondary button--compact"
              :disabled="
                selectedQuestionIndex === questionCount - 1 || reorderMutation.isPending.value
              "
              :aria-label="`${selectedQuestion.questionOrder}번 문항 뒤로 이동`"
              @click="moveQuestion(selectedQuestion.id, 1)"
            >
              뒤로
            </button>
          </div>
        </div>
        <template #footer>
          <button
            v-if="deleteConfirmationId !== selectedQuestion.id"
            type="button"
            class="button button--ghost"
            @click="deleteConfirmationId = selectedQuestion.id"
          >
            문항 삭제
          </button>
          <template v-else>
            <button type="button" class="button button--ghost" @click="deleteConfirmationId = ''">
              취소
            </button>
            <button
              type="button"
              class="button button--danger"
              :disabled="deleteQuestionMutation.isPending.value"
              @click="removeQuestion(selectedQuestion)"
            >
              삭제 확인
            </button>
          </template>
          <button
            type="button"
            class="button button--primary"
            :disabled="updateQuestionMutation.isPending.value"
            @click="updateQuestion()"
          >
            문항 저장
          </button>
        </template>
      </CoverLetterSheet>

      <CoverLetterSheet
        :open="activeSheet === 'GENERATE'"
        :title="canRegenerate ? 'AI로 다시 쓰기' : 'AI 초안 만들기'"
        description="설정을 확인하고 시작해 주세요."
        placement="center"
        width="36rem"
        @close="closeSheet()"
      >
        <CoverLetterGenerationPanel
          :questions="activeQuestions"
          :target-question-ids="generationQuestionIds"
          :models="aiModels.data.value ?? []"
          :selected-model="selectedModel"
          :models-loading="aiModels.isLoading.value"
          :models-error="aiModels.isError.value"
          :avoid-experience-duplication="avoidExperienceDuplication"
          :selected-evidence-count="selectedEvidenceIds.size"
          :verified-evidence-count="evidenceItems.length"
          :editor-dirty="editorDirty"
          :dirty-question-order="selectedQuestion?.questionOrder ?? 0"
          @toggle-question="toggleGenerationQuestion"
          @update:selected-model="selectedModel = $event"
          @update:avoid-experience-duplication="avoidExperienceDuplication = $event"
        />
        <template #footer>
          <p class="cover-sheet__summary">
            {{ generationQuestionIds.size }}개 문항 · {{ selectedModel || '모델 선택 필요' }}
          </p>
          <button
            v-if="editorDirty"
            type="button"
            class="button button--secondary"
            :disabled="editorOverLimit || saveVersionMutation.isPending.value"
            @click="saveAnswer()"
          >
            먼저 답변 저장
          </button>
          <button type="button" class="button button--ghost" @click="closeSheet()">취소</button>
          <button
            type="button"
            class="button button--primary"
            :disabled="
              generationQuestionIds.size === 0 ||
              selectedModel === '' ||
              aiModels.isLoading.value ||
              aiModels.isError.value ||
              aiActionUnavailable
            "
            data-testid="generate-cover-letter"
            @click="generateAnswers()"
          >
            {{ canRegenerate ? 'AI로 다시 쓰기' : 'AI 초안 만들기' }}
          </button>
        </template>
      </CoverLetterSheet>

      <CoverLetterSheet
        v-if="selectedQuestion"
        :open="activeSheet === 'VERSIONS'"
        :title="`${selectedQuestion.questionOrder}번 문항 버전 기록`"
        description="저장할 때마다 새 버전이 쌓여요."
        @close="closeSheet()"
      >
        <CoverLetterVersionPanel
          :versions="versions.data.value?.items ?? []"
          :selected-version-id="selectedVersionId"
          :selected-version="selectedVersion"
          :current-answer="selectedQuestion.currentAnswer"
          :loading="versions.isLoading.value"
          :verifications="historicalVerifications.data.value?.items ?? []"
          :editor-dirty="editorDirty"
          :read-only="readOnly"
          :restore-pending="restoreMutation.isPending.value"
          @select="selectedVersionId = $event"
          @restore="selectedVersion && restoreVersion(selectedVersion)"
        />
      </CoverLetterSheet>

      <CoverLetterSheet
        :open="activeSheet === 'COMPLETION'"
        title="작성 완료 점검"
        description="완료까지 남은 조건을 한곳에서 확인해요."
        @close="closeSheet()"
      >
        <CoverLetterCompletionPanel
          :items="completion"
          :warnings="warningAcknowledgementTargets"
          :acknowledged="warningAcknowledgements"
          :finalized="coverLetter.data.value.status === 'FINALIZED'"
          :read-only="readOnly"
          :can-finalize="canFinalizeNow"
          :finalize-pending="finalizeMutation.isPending.value"
          @focus-question="focusQuestion"
          @acknowledge="toggleWarningAcknowledgement"
        />
        <template #footer>
          <button
            v-if="coverLetter.data.value.canUnarchive"
            type="button"
            class="button button--secondary"
            :disabled="unarchiveMutation.isPending.value"
            @click="unarchiveCover()"
          >
            다시 쓰기
          </button>
          <button
            v-if="coverLetter.data.value.canArchive"
            type="button"
            class="button button--ghost"
            :disabled="archiveMutation.isPending.value"
            @click="archiveCover()"
          >
            보관하기
          </button>
          <RouterLink
            v-if="jobId"
            class="button button--ghost"
            :to="{ name: 'job-analysis', params: { jobId } }"
          >
            공고 분석 보기
          </RouterLink>
          <button
            v-if="!readOnly && !renamingTitle"
            type="button"
            class="button button--ghost"
            @click="startRenamingFromSheet()"
          >
            제목 수정
          </button>
          <button
            v-if="coverLetter.data.value.status === 'DRAFT'"
            type="button"
            class="button button--primary"
            :disabled="!canFinalizeNow || finalizeMutation.isPending.value"
            data-testid="finalize-cover-letter"
            @click="finalizeCover()"
          >
            {{ finalizeMutation.isPending.value ? '표시하는 중…' : '작성 완료' }}
          </button>
        </template>
      </CoverLetterSheet>
    </template>
  </section>
</template>

<style scoped>
/*
 * 자기소개서 작성 화면.
 * 화면의 중심은 문항과 답변 편집기다. 보조 정보는 우측 패널과 sheet로 내려 둔다.
 *   상단 고정 영역 → 좌: 문항 목록 / 중: 편집기 / 우: 작성 도움
 * 카드로 감싸는 대신 여백, 구분선과 배경 차이로 영역을 나눈다.
 */

.cover-editor {
  min-width: 0;
  overflow-x: clip;
}

/* ------------------------------------------------------------ 상단 고정 영역 */

/* 본문과 함께 스크롤되어 내려간다. 화면 상단에 계속 붙여 두면 읽는 영역을 좁힌다. */
.cover-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-canvas);
  padding-bottom: var(--space-4);
}

.cover-topbar__identity {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.cover-topbar__back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
  text-decoration: none;
}

.cover-topbar__back:hover {
  color: var(--color-brand-strong);
}

.cover-topbar__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.cover-topbar__title {
  font-size: clamp(1.15rem, 2.4vw, 1.5rem);
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--color-ink-title);
  overflow-wrap: anywhere;
}

.cover-topbar__rename {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.cover-topbar__rename .field {
  flex: 1 1 16rem;
}

.cover-topbar__job {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.cover-topbar__job [data-dirty='true'] {
  color: var(--color-warning-strong);
  font-weight: 700;
}

.cover-topbar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.cover-topbar__completion {
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  gap: var(--space-2);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-fill);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.cover-topbar__completion:hover {
  background: var(--color-fill-strong);
}

.cover-topbar__completion[data-ready='true'] {
  background: var(--color-success-soft);
  color: var(--color-success-strong);
}

.cover-topbar__completion :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.cover-topbar__hint {
  grid-column: 1 / -1;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.cover-editor__message {
  margin-top: var(--space-4);
}

/* ------------------------------------------------------------------ 작업 영역 */

.cover-workspace {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(16rem, 19rem);
  align-items: start;
  gap: clamp(var(--space-3), 1.5vw, var(--space-5));
  margin-top: var(--space-4);
}

.cover-workspace__rail {
  position: sticky;
  top: calc(var(--global-header-height) + var(--space-4));
  min-width: 0;
}

.cover-workspace__main {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.cover-workspace__mobile-nav {
  display: none;
  gap: var(--space-2);
}

/*
 * 작성 도움은 편집 영역과 정확히 같은 높이를 쓴다.
 * 안쪽 내용을 흐름에서 빼 두어 이 열이 grid 행 높이를 늘리지 않게 한다.
 */
.cover-workspace__assist {
  position: relative;
  align-self: stretch;
  min-width: 0;
  min-height: 0;
  border-left: 1px solid var(--color-border);
}

.cover-workspace__assist-inner {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-2);
  min-height: 0;
  overflow: hidden;
  padding-left: var(--space-4);
}

.cover-workspace__assist[data-collapsed='true'] .cover-workspace__assist-inner {
  grid-template-rows: auto;
}

.cover-workspace__assist-toggle {
  justify-self: start;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  padding: var(--space-1) 0;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.cover-workspace__assist-toggle:hover {
  color: var(--color-brand-strong);
}

/* --------------------------------------------------------------- 문항과 편집기 */

.answer-brief {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.answer-brief__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.answer-brief__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

/* 문항은 읽는 정보라 제목 크기를 키우지 않는다. */
.answer-brief__question {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: baseline;
  gap: var(--space-2);
  flex: 1 1 22rem;
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: 700;
  line-height: 1.6;
  color: var(--color-ink-title);
  overflow-wrap: anywhere;
}

.answer-brief__order {
  color: var(--color-brand-strong);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.answer-brief__order::after {
  content: '.';
}

.answer-brief__memo {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.answer-brief__memo :deep(.icon) {
  width: 1rem;
  height: 1rem;
  margin-top: 0.15rem;
}

.answer-lock {
  align-items: center;
}

.cover-workspace__editor {
  min-width: 0;
}

.answer-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.answer-actions__state {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.answer-actions__state p {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.answer-actions__state :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.answer-actions__state p[data-dirty='true'] {
  color: var(--color-warning-strong);
  font-weight: 700;
}

.answer-actions__length {
  font-variant-numeric: tabular-nums;
}

.answer-actions__length[data-over='true'] {
  color: var(--color-danger-strong);
  font-weight: 700;
}

.answer-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.answer-actions__buttons :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

/* ---------------------------------------------------- 답변에 사용할 소재 */

.material-anchor {
  position: relative;
  justify-self: start;
}

.material-anchor__trigger {
  gap: var(--space-2);
}

.material-anchor__trigger :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.material-anchor__trigger span {
  border-radius: var(--radius-pill);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  padding: 0.1rem 0.45rem;
  font-size: var(--font-size-xs);
  font-weight: 750;
}

/*
 * 다른 영역 위로 펼쳐진다. 열려도 아래 내용이 밀려나지 않도록 흐름에서 뺀다.
 */
.material-anchor__panel {
  position: absolute;
  z-index: 30;
  top: calc(100% + var(--space-2));
  left: 0;
  width: min(30rem, calc(100vw - 2rem));
  max-height: 22rem;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-4);
  box-shadow: var(--shadow-md);
  animation: material-panel-enter var(--motion-fast) both;
}

@keyframes material-panel-enter {
  from {
    opacity: 0;
    transform: translateY(-0.25rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .material-anchor__panel {
    animation: none;
  }
}

/* ---------------------------------------------------------- 미저장 내용 복구 */

.draft-recovery {
  display: grid;
  gap: var(--space-3);
}

.draft-recovery__lead strong {
  font-weight: 750;
}

.draft-recovery__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.draft-recovery__comparison article {
  min-width: 0;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3);
}

.draft-recovery__comparison h3 {
  font-size: var(--font-size-xs);
  font-weight: 750;
}

.draft-recovery__comparison pre {
  max-height: 8rem;
  margin-top: var(--space-2);
  overflow: auto;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.draft-recovery__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* ------------------------------------------------------------------ sheet 내부 */

.question-add,
.question-meta__form {
  display: grid;
  gap: var(--space-4);
}

.question-add__presets {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.question-meta__order {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.question-meta__order > div {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.cover-sheet__summary {
  margin-right: auto;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

/* ------------------------------------------------------------------- 반응형 */

@media (max-width: 74rem) {
  .cover-workspace {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .cover-workspace__assist {
    display: none;
  }

  .cover-workspace__mobile-nav {
    display: flex;
    justify-content: flex-end;
  }

  .cover-workspace__mobile-nav [data-testid='open-questions'] {
    display: none;
  }
}

@media (max-width: 56rem) {
  .cover-topbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .cover-topbar__actions {
    justify-content: flex-start;
  }

  .cover-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .cover-workspace__rail {
    display: none;
  }

  .cover-workspace__mobile-nav {
    justify-content: flex-start;
  }

  .cover-workspace__mobile-nav [data-testid='open-questions'] {
    display: inline-flex;
  }

  .draft-recovery__comparison {
    grid-template-columns: minmax(0, 1fr);
  }

  .answer-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .answer-actions__buttons .button {
    flex: 1 1 8rem;
  }
}
</style>
