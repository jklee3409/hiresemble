<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
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
import type {
  CoverLetterAnswerVersionDto,
  CoverLetterDetailDto,
  CoverLetterQuestionDto,
  TipTapDocumentDto,
  VerificationDto,
} from '@/shared/api/coverLetterContracts'
import { normalizeApiError, type ApiClientError } from '@/shared/api/errors'
import { listEvidence } from '@/shared/api/profileApi'
import PageHeader from '@/shared/ui/PageHeader.vue'
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
  <section class="cover-editor app-page" aria-labelledby="cover-editor-heading">
    <PageHeader
      heading-id="cover-editor-heading"
      :title="coverLetter.data.value?.title ?? '자기소개서 편집'"
      :description="
        readOnly
          ? '보관된 자기소개서의 문항, 버전과 검증 기록을 읽기 전용으로 확인합니다.'
          : '문항별 답변을 명시적으로 저장하고 근거 검증과 버전 이력을 관리하세요.'
      "
      variant="editor"
    >
      <template #actions>
        <StatusBadge
          v-if="coverLetter.data.value"
          :label="COVER_LETTER_STATUS_LABELS[coverLetter.data.value.status]"
          :tone="
            coverLetter.data.value.status === 'FINALIZED'
              ? 'success'
              : coverLetter.data.value.status === 'ARCHIVED'
                ? 'neutral'
                : 'brand'
          "
        />
      </template>
    </PageHeader>

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
      <section v-if="readOnly" class="cover-editor__archived" role="status">
        <strong>ARCHIVED · 읽기 전용</strong>
        <span> 제목, 문항, 답변 저장·복원, AI 생성·검증과 최종화는 사용할 수 없습니다. </span>
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

      <section class="cover-editor__title">
        <label class="field">
          <span class="field__label">자기소개서 제목</span>
          <input v-model="titleDraft" class="control" maxlength="300" :readonly="readOnly" />
        </label>
        <button
          v-if="!readOnly"
          type="button"
          class="button button--secondary"
          :disabled="!titleDirty || updateCoverMutation.isPending.value"
          @click="saveTitle()"
        >
          {{ updateCoverMutation.isPending.value ? '저장 중…' : '제목 저장' }}
        </button>
        <button
          v-if="coverLetter.data.value.canArchive"
          type="button"
          class="button button--ghost"
          :disabled="archiveMutation.isPending.value"
          @click="archiveCover()"
        >
          보관
        </button>
      </section>

      <p v-if="statusMessage" class="cover-editor__notice" role="status">
        {{ statusMessage }}
      </p>
      <p v-if="actionError" class="cover-editor__error" role="alert">
        {{ actionError }}
      </p>
      <CoverLetterConflictPanel
        v-if="conflict"
        :conflict="conflict"
        :reapplying="conflictReapplying"
        @reapply="reapplyConflict"
        @cancel="cancelConflict"
      />

      <CoverLetterRunMonitor
        v-if="currentRunId"
        :key="currentRunId"
        :user-id="userId"
        :cover-letter-id="coverLetterId"
        :agent-run-id="currentRunId"
        :question-labels="questionLabels"
        @terminal="handleRunTerminal"
      />

      <div class="cover-editor__workspace" data-testid="cover-letter-editor">
        <aside class="cover-editor__navigator" aria-label="자기소개서 문항">
          <div class="cover-editor__section-heading">
            <div>
              <p class="page-eyebrow">문항 Navigator</p>
              <h2>문항 {{ activeQuestions.length }}개</h2>
            </div>
            <button
              v-if="!readOnly && activeQuestions.length < 20"
              type="button"
              class="button button--secondary button--compact"
              @click="addingQuestion = !addingQuestion"
            >
              문항 추가
            </button>
          </div>

          <form
            v-if="addingQuestion && !readOnly"
            class="question-add"
            @submit.prevent="addQuestion()"
          >
            <label class="field">
              <span class="field__label">문항 내용</span>
              <textarea
                v-model="newQuestionText"
                class="control"
                maxlength="2000"
                rows="4"
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
              />
            </label>
            <label class="field">
              <span class="field__label">메모</span>
              <textarea v-model="newQuestionMemo" class="control" maxlength="2000" rows="2" />
            </label>
            <button
              type="submit"
              class="button button--primary"
              :disabled="createQuestionMutation.isPending.value"
            >
              추가
            </button>
          </form>

          <StatePanel
            v-if="activeQuestions.length === 0"
            kind="empty"
            title="등록된 문항이 없어요."
            description="문항을 추가하면 답변 작성과 AI 생성을 시작할 수 있어요."
          />
          <ol v-else class="question-list">
            <li v-for="(question, index) in activeQuestions" :key="question.id">
              <button
                type="button"
                class="question-list__select"
                :class="{ 'question-list__select--active': selectedQuestionId === question.id }"
                :aria-current="selectedQuestionId === question.id ? 'step' : undefined"
                @click="selectedQuestionId = question.id"
              >
                <span>{{ question.questionOrder }}</span>
                <span>
                  <strong>{{ question.questionText }}</strong>
                  <small>
                    {{
                      question.currentAnswer
                        ? `${question.currentAnswer.characterCount}자 · ${ANSWER_SOURCE_LABELS[question.currentAnswer.sourceType]}`
                        : '답변 미작성'
                    }}
                  </small>
                </span>
              </button>
              <div v-if="!readOnly" class="question-list__order" aria-label="문항 순서 변경">
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
                  :disabled="
                    index === activeQuestions.length - 1 || reorderMutation.isPending.value
                  "
                  :aria-label="`${question.questionOrder}번 문항 아래로 이동`"
                  @click="moveQuestion(question.id, 1)"
                >
                  ↓
                </button>
              </div>
            </li>
          </ol>

          <fieldset v-if="activeQuestions.length > 0 && !readOnly" class="generation-questions">
            <legend>AI 생성 대상 문항</legend>
            <label v-for="question in activeQuestions" :key="`generate-${question.id}`">
              <input
                type="checkbox"
                :checked="generationQuestionIds.has(question.id)"
                @change="toggleGenerationQuestion(question.id)"
              />
              {{ question.questionOrder }}번
            </label>
          </fieldset>
        </aside>

        <main class="cover-editor__answer">
          <template v-if="selectedQuestion">
            <section class="question-meta">
              <div class="cover-editor__section-heading">
                <div>
                  <p class="page-eyebrow">선택 문항</p>
                  <h2>{{ selectedQuestion.questionOrder }}번 문항</h2>
                </div>
                <StatusBadge
                  v-if="selectedQuestion.latestVerification"
                  :label="VERIFICATION_STATUS_LABELS[selectedQuestion.latestVerification.status]"
                  :tone="verificationTone(selectedQuestion.latestVerification.status)"
                />
              </div>
              <div class="question-meta__form">
                <label class="field question-meta__text">
                  <span class="field__label">문항 내용</span>
                  <textarea
                    v-model="questionTextDraft"
                    class="control"
                    rows="3"
                    maxlength="2000"
                    :readonly="readOnly"
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
                    :readonly="readOnly"
                  />
                </label>
                <label class="field">
                  <span class="field__label">메모</span>
                  <textarea
                    v-model="questionMemoDraft"
                    class="control"
                    rows="2"
                    maxlength="2000"
                    :readonly="readOnly"
                  />
                </label>
              </div>
              <div v-if="!readOnly" class="question-meta__actions">
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
            </section>

            <section v-if="draftCandidate || draftNotice" class="draft-recovery" role="status">
              <div>
                <strong>브라우저 임시 저장</strong>
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
              <div>
                <span v-if="editorDirty">브라우저 임시 저장됨 · 서버 미저장</span>
                <span v-else>현재 서버 버전과 동일</span>
                <span v-if="editorOverLimit" class="answer-actions__error">
                  최대 글자 수를 초과했어요.
                </span>
              </div>
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
            v-else
            kind="empty"
            title="문항을 선택하거나 추가해 주세요."
            description="답변 편집기는 선택한 문항의 현재 서버 버전을 기준으로 열립니다."
          />
        </main>

        <aside class="cover-editor__rail" aria-label="공고 요구사항, 근거와 검증">
          <section class="rail-section">
            <div class="cover-editor__section-heading">
              <div>
                <p class="page-eyebrow">공고 Context</p>
                <h2>주요 요구사항</h2>
              </div>
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
            <p v-if="analysis.isLoading.value">요구사항을 불러오는 중…</p>
            <ul v-else-if="analysis.data.value" class="rail-list">
              <li
                v-for="requirement in [
                  ...analysis.data.value.requiredQualifications,
                  ...analysis.data.value.responsibilities,
                ].slice(0, 8)"
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
            <div class="cover-editor__section-heading">
              <div>
                <p class="section-kicker">확인한 경험만</p>
                <h2>관련 경험 선택</h2>
              </div>
              <span>{{ selectedEvidenceIds.size }}개</span>
            </div>
            <p v-if="evidence.isLoading.value">확인한 경험을 불러오는 중…</p>
            <p v-else-if="evidence.isError.value" class="rail-section__warning">
              경험 정보를 불러오지 못했어요.
            </p>
            <ul v-else class="evidence-options">
              <li v-for="item in evidence.data.value?.items ?? []" :key="item.id">
                <label>
                  <input
                    type="checkbox"
                    :checked="selectedEvidenceIds.has(item.id)"
                    :disabled="readOnly"
                    @change="toggleEvidence(item.id)"
                  />
                  <span>
                    <strong>{{ item.title }}</strong>
                    <small>{{ item.evidenceCategory }}</small>
                  </span>
                </label>
              </li>
            </ul>
          </section>

          <section v-if="!readOnly" class="rail-section generation-command">
            <p class="section-kicker">초안 설정</p>
            <label class="field">
              <span class="field__label">작성 방식</span>
              <select v-model="qualityMode" class="control control--compact">
                <option value="ECONOMY">빠르게 초안 만들기</option>
                <option value="BALANCED">균형 있게 작성</option>
                <option value="HIGH_QUALITY">내용을 더 꼼꼼히 작성</option>
              </select>
            </label>
            <label class="check-field">
              <input v-model="avoidExperienceDuplication" type="checkbox" />
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
          </section>

          <section class="rail-section">
            <div class="cover-editor__section-heading">
              <div>
                <p class="page-eyebrow">immutable verification</p>
                <h2>검증 결과</h2>
              </div>
              <span v-if="selectedVersion">v{{ selectedVersion.versionNo }}</span>
            </div>
            <p v-if="verifications.isLoading.value">검증 이력을 불러오는 중…</p>
            <p v-else-if="verifications.data.value?.items.length === 0" class="rail-section__empty">
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
                <h3>수정 제안</h3>
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
        <div class="cover-editor__section-heading">
          <div>
            <p class="page-eyebrow">버전 이력</p>
            <h2 id="version-history-title">답변 비교·복원</h2>
          </div>
          <span>{{ versions.data.value?.totalElements ?? 0 }}개</span>
        </div>
        <p v-if="versions.isLoading.value">버전 이력을 불러오는 중…</p>
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
              <strong>v{{ version.versionNo }}</strong>
              <span>{{ ANSWER_SOURCE_LABELS[version.sourceType] }}</span>
              <small>{{ formatCoverLetterInstant(version.createdAt) }}</small>
              <small v-if="version.isCurrent">현재 버전</small>
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

      <section class="finalization">
        <div>
          <p class="page-eyebrow">최종화</p>
          <h2>답변과 fresh 검증 확인</h2>
          <p>
            자기소개서 최종화는 공고를 SUBMITTED로 바꾸지 않습니다. 공고 제출 상태는 별도로
            변경하세요.
          </p>
        </div>
        <ul v-if="finalizeBlockers.length" class="finalization__blockers">
          <li v-for="blocker in finalizeBlockers" :key="blocker">{{ blocker }}</li>
        </ul>
        <fieldset
          v-if="warningVerificationIds.length > 0 && coverLetter.data.value.status === 'DRAFT'"
          class="finalization__warnings"
        >
          <legend>WARNING 검증 확인</legend>
          <label v-for="id in warningVerificationIds" :key="id">
            <input
              type="checkbox"
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
.cover-editor {
  min-width: 0;
  overflow-x: clip;
}

.cover-editor__archived,
.cover-editor__title,
.cover-editor__notice,
.cover-editor__error,
.draft-recovery,
.finalization {
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.cover-editor__archived {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  border: 1px solid var(--color-warning-border);
  background: var(--color-warning-soft);
}

.cover-editor__archived span {
  flex: 1;
  color: var(--color-text-secondary);
}

.cover-editor__title {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: end;
  gap: var(--space-3);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.cover-editor__notice,
.cover-editor__error {
  margin-top: var(--space-3);
}

.cover-editor__notice {
  background: var(--color-success-soft);
  color: var(--color-success-strong);
}

.cover-editor__error {
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
}

.cover-editor__workspace {
  display: grid;
  grid-template-columns: minmax(14rem, 0.72fr) minmax(26rem, 1.7fr) minmax(19rem, 0.95fr);
  align-items: start;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.cover-editor__navigator,
.cover-editor__answer,
.cover-editor__rail,
.version-history,
.finalization {
  min-width: 0;
}

.cover-editor__navigator,
.question-meta,
.rail-section,
.version-history,
.finalization {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-4);
}

.cover-editor__navigator,
.cover-editor__rail {
  display: grid;
  gap: var(--space-4);
}

.cover-editor__section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.cover-editor__section-heading h2 {
  margin-top: var(--space-1);
  font-size: 1.05rem;
}

.question-add,
.question-meta__form,
.generation-command {
  display: grid;
  gap: var(--space-3);
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
  grid-template-columns: 1.75rem minmax(0, 1fr);
  gap: var(--space-2);
  width: 100%;
  min-height: 3.25rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-3);
  text-align: left;
}

.question-list__select > span:first-child {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface-subtle);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: 750;
}

.question-list__select > span:last-child {
  display: grid;
  min-width: 0;
  gap: var(--space-1);
}

.question-list__select strong,
.question-list__select small {
  overflow: hidden;
  text-overflow: ellipsis;
}

.question-list__select small {
  color: var(--color-text-muted);
}

.question-list__select--active {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.question-list__select--active > span:first-child {
  background: var(--color-brand);
  color: white;
}

.question-list__order {
  display: grid;
  gap: var(--space-1);
}

.question-list__order button {
  width: 2rem;
  min-height: 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
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

.cover-editor__answer {
  display: grid;
  gap: var(--space-3);
}

.question-meta__form {
  grid-template-columns: minmax(0, 1.5fr) minmax(8rem, 0.5fr);
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
  border: 1px solid var(--color-warning-border);
  background: var(--color-warning-soft);
}

.draft-recovery p {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
}

.draft-recovery__comparison,
.version-history__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.draft-recovery__comparison article,
.version-history__comparison article {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-3);
}

.draft-recovery pre,
.version-history pre {
  max-height: 14rem;
  margin-top: var(--space-2);
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  color: var(--color-text-secondary);
}

.answer-actions {
  align-items: center;
  justify-content: space-between;
}

.answer-actions > div {
  display: grid;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.answer-actions__error,
.rail-section__warning {
  color: var(--color-warning-strong) !important;
}

.rail-section {
  display: grid;
  gap: var(--space-3);
}

.rail-list,
.verification-issues {
  display: grid;
  gap: var(--space-2);
  padding-left: var(--space-5);
  list-style: disc;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.rail-section__empty {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.evidence-options {
  display: grid;
  gap: var(--space-2);
  max-height: 18rem;
  overflow-y: auto;
}

.evidence-options label {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.evidence-options span {
  display: grid;
  min-width: 0;
}

.evidence-options strong,
.evidence-options small {
  overflow-wrap: anywhere;
}

.evidence-options small {
  color: var(--color-text-muted);
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

.verification-issues blockquote {
  margin-top: var(--space-1);
  border-left: 3px solid var(--color-warning-border);
  padding-left: var(--space-2);
}

.verification-suggestions {
  display: grid;
  gap: var(--space-2);
}

.verification-suggestions > div {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.verification-suggestions .button {
  justify-self: start;
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
}

.historical-evidence small {
  color: var(--color-warning-strong);
}

.version-history,
.finalization {
  margin-top: var(--space-5);
}

.version-history__layout {
  display: grid;
  grid-template-columns: minmax(10rem, 0.35fr) minmax(0, 1.65fr);
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.version-history__list {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.version-history__list button {
  display: grid;
  gap: var(--space-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  padding: var(--space-3);
  text-align: left;
}

.version-history__item--active {
  border-color: var(--color-brand-border) !important;
  background: var(--color-brand-soft) !important;
}

.version-history__comparison {
  margin-top: 0;
}

.version-history__comparison .button {
  grid-column: 1 / -1;
  justify-self: start;
}

.finalization {
  display: grid;
  gap: var(--space-4);
}

.finalization p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.finalization__blockers {
  display: grid;
  gap: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-4) var(--space-6);
  list-style: disc;
}

.finalization .button {
  justify-self: start;
}

@media (max-width: 80rem) {
  .cover-editor__workspace {
    grid-template-columns: minmax(13rem, 0.65fr) minmax(0, 1.35fr);
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

  .cover-editor__rail {
    grid-column: auto;
  }
}

@media (max-width: 48rem) {
  .cover-editor__rail,
  .question-meta__form,
  .draft-recovery__comparison,
  .version-history__layout,
  .version-history__comparison {
    grid-template-columns: 1fr;
  }

  .question-meta__text,
  .version-history__comparison .button {
    grid-column: auto;
  }
}

@media (max-width: 40rem) {
  .cover-editor__archived,
  .cover-editor__title,
  .cover-editor__section-heading,
  .answer-actions {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .cover-editor__archived {
    display: flex;
  }

  .cover-editor__title .button,
  .question-meta__actions .button,
  .answer-actions .button,
  .draft-recovery__actions .button,
  .generation-command .button,
  .finalization .button {
    width: 100%;
  }
}
</style>
