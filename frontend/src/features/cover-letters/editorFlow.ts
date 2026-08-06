import type {
  CoverLetterQuestionDto,
  CoverLetterStatus,
  VerificationDto,
} from '@/shared/api/coverLetterContracts'

/*
 * 자기소개서 작성 화면의 상태 판정을 한곳에 모은다.
 * 화면은 이 결과만 읽어 문항 상태, 남은 조건, 강조할 단일 행동을 표시한다.
 * Backend 최종화 조건(모든 문항 답변 저장 · 글자 수 · 최신 답변 기준 검토 통과 ·
 * 확인 필요 항목 동의)과 같은 규칙을 사용한다.
 */

export type QuestionWorkState =
  | 'EMPTY'
  | 'UNSAVED'
  | 'REVIEW_NEEDED'
  | 'REVIEW_RUNNING'
  | 'REVIEW_FAILED'
  | 'REVIEW_WARNING'
  | 'READY'

export type StatusTone = 'neutral' | 'brand' | 'info' | 'success' | 'warning' | 'danger'

export type CompletionAction = 'WRITE' | 'SAVE' | 'SHORTEN' | 'REVIEW' | 'WAIT' | 'ACKNOWLEDGE'

export type PrimaryActionKind =
  | 'NONE'
  | 'ADD_QUESTION'
  | 'SAVE_ANSWER'
  | 'GENERATE'
  | 'REGENERATE'
  | 'VERIFY'
  | 'GO_TO_QUESTION'
  | 'OPEN_COMPLETION'
  | 'FINALIZE'
  | 'UNARCHIVE'

export interface QuestionWorkStatus {
  readonly state: QuestionWorkState
  readonly label: string
  readonly tone: StatusTone
}

export interface CompletionItem {
  readonly questionId: string
  readonly questionOrder: number
  readonly action: CompletionAction
  readonly message: string
}

/** 확인 필요 검토를 읽었는지 표시할 때 어떤 문항의 검토인지 함께 보여 준다. */
export interface WarningAcknowledgement {
  readonly verificationId: string
  readonly questionId: string
  readonly questionOrder: number
}

/** 작성 도움 패널이 한 번에 하나만 보여 주는 보조 정보 묶음. */
export type AssistTab = 'JOB' | 'MATERIAL' | 'REVIEW'

export interface PrimaryAction {
  readonly kind: PrimaryActionKind
  readonly label: string
  readonly hint: string
  readonly disabled: boolean
  readonly targetQuestionId: string
}

export interface FlowContext {
  readonly status: CoverLetterStatus
  readonly canUnarchive: boolean
  readonly canFinalize: boolean
  readonly questions: readonly CoverLetterQuestionDto[]
  readonly selectedQuestionId: string
  readonly editorDirty: boolean
  readonly editorOverLimit: boolean
  readonly generationInProgress: boolean
  readonly aiBusy: boolean
  readonly aiUnavailableReason: string
  readonly savePending: boolean
  readonly finalizePending: boolean
  readonly unarchivePending: boolean
  readonly completion: readonly CompletionItem[]
}

const QUESTION_STATE_LABELS: Record<QuestionWorkState, string> = {
  EMPTY: '답변 전',
  UNSAVED: '저장 안 됨',
  REVIEW_NEEDED: '검토 전',
  REVIEW_RUNNING: '검토 중',
  REVIEW_FAILED: '수정 필요',
  REVIEW_WARNING: '확인 필요',
  READY: '작성 완료',
}

const QUESTION_STATE_TONES: Record<QuestionWorkState, StatusTone> = {
  EMPTY: 'neutral',
  UNSAVED: 'warning',
  REVIEW_NEEDED: 'info',
  REVIEW_RUNNING: 'brand',
  REVIEW_FAILED: 'danger',
  REVIEW_WARNING: 'warning',
  READY: 'success',
}

/** 답변이 저장된 뒤 다시 고쳐 저장하면 과거 검토는 최신 답변의 검토가 아니다. */
export function freshVerification(question: CoverLetterQuestionDto): VerificationDto | null {
  const answer = question.currentAnswer
  const verification = question.latestVerification
  if (answer === null || verification === null) return null
  return verification.answerVersionId === answer.id ? verification : null
}

export function questionWorkState(
  question: CoverLetterQuestionDto,
  options: { dirty?: boolean } = {},
): QuestionWorkState {
  if (question.currentAnswer === null) return 'EMPTY'
  if (options.dirty === true) return 'UNSAVED'
  const verification = freshVerification(question)
  if (verification === null) return 'REVIEW_NEEDED'
  if (verification.status === 'PENDING') return 'REVIEW_RUNNING'
  if (verification.status === 'FAILED') return 'REVIEW_FAILED'
  if (verification.status === 'WARNING') return 'REVIEW_WARNING'
  return 'READY'
}

export function questionWorkStatus(
  question: CoverLetterQuestionDto,
  options: { dirty?: boolean } = {},
): QuestionWorkStatus {
  const state = questionWorkState(question, options)
  return { state, label: QUESTION_STATE_LABELS[state], tone: QUESTION_STATE_TONES[state] }
}

/**
 * 작성 완료까지 남은 조건을 문항 단위로 만든다.
 * 화면 최하단까지 내려가지 않아도 확인할 수 있도록 문항 이동에 필요한 ID를 함께 담는다.
 */
export function completionItems(input: {
  questions: readonly CoverLetterQuestionDto[]
  status: CoverLetterStatus
  dirtyQuestionId: string
  acknowledgedWarningIds: ReadonlySet<string>
}): CompletionItem[] {
  if (input.status !== 'DRAFT') return []
  const items: CompletionItem[] = []
  for (const question of input.questions) {
    const order = question.questionOrder
    const answer = question.currentAnswer
    if (answer === null) {
      items.push(item(question, 'WRITE', `${order}번 문항의 답변을 아직 쓰지 않았어요.`))
      continue
    }
    if (question.id === input.dirtyQuestionId) {
      items.push(item(question, 'SAVE', `${order}번 문항에 저장하지 않은 내용이 있어요.`))
    }
    if (question.maxLength !== null && answer.characterCount > question.maxLength) {
      items.push(
        item(
          question,
          'SHORTEN',
          `${order}번 문항이 ${answer.characterCount - question.maxLength}자 넘었어요.`,
        ),
      )
    }
    const verification = freshVerification(question)
    if (verification === null) {
      items.push(item(question, 'REVIEW', `${order}번 문항은 아직 AI 검토를 받지 않았어요.`))
      continue
    }
    if (verification.status === 'PENDING') {
      items.push(item(question, 'WAIT', `${order}번 문항을 검토하는 중이에요.`))
      continue
    }
    if (verification.status === 'FAILED') {
      items.push(item(question, 'REVIEW', `${order}번 문항을 고친 뒤 다시 검토받아야 해요.`))
      continue
    }
    if (verification.status === 'WARNING' && !input.acknowledgedWarningIds.has(verification.id)) {
      items.push(item(question, 'ACKNOWLEDGE', `${order}번 문항의 확인 사항을 읽어 주세요.`))
    }
  }
  return items
}

export function resolvePrimaryAction(context: FlowContext): PrimaryAction {
  const selected = context.questions.find((question) => question.id === context.selectedQuestionId)

  if (context.status === 'ARCHIVED') {
    return context.canUnarchive
      ? action('UNARCHIVE', '보관 해제하고 이어 쓰기', '보관을 풀면 다시 고칠 수 있어요.', {
          disabled: context.unarchivePending,
        })
      : action('NONE', '', '보관된 자기소개서라 읽기만 할 수 있어요.')
  }
  if (context.generationInProgress) {
    return action('NONE', '', 'AI가 초안을 쓰고 있어요. 끝나면 새 답변으로 보여 드릴게요.')
  }
  if (context.questions.length === 0) {
    return action('ADD_QUESTION', '첫 문항 추가하기', '공고에 적힌 문항을 그대로 옮겨 적어 주세요.')
  }
  if (context.editorDirty) {
    return action(
      'SAVE_ANSWER',
      '답변 저장',
      context.editorOverLimit
        ? '글자 수를 줄이면 저장할 수 있어요.'
        : '저장해도 이전 답변은 버전 기록에 남아요.',
      {
        disabled: context.editorOverLimit || context.savePending,
      },
    )
  }
  if (selected !== undefined && selected.currentAnswer === null) {
    return action(
      'GENERATE',
      'AI 초안 만들기',
      context.aiUnavailableReason || '고른 경험을 근거로 이 문항의 초안을 써 드려요.',
      {
        disabled: context.aiBusy,
        targetQuestionId: selected.id,
      },
    )
  }
  if (selected !== undefined && questionWorkState(selected) === 'REVIEW_NEEDED') {
    return action(
      'VERIFY',
      'AI 검토 받기',
      context.aiUnavailableReason || '작성 완료를 하려면 저장한 답변마다 검토가 필요해요.',
      {
        disabled: context.aiBusy,
        targetQuestionId: selected.id,
      },
    )
  }
  if (selected !== undefined && questionWorkState(selected) === 'REVIEW_FAILED') {
    return action(
      'VERIFY',
      '다시 검토 받기',
      context.aiUnavailableReason || '고친 답변을 한 번 더 확인해 볼게요.',
      {
        disabled: context.aiBusy,
        targetQuestionId: selected.id,
      },
    )
  }

  const next = context.completion.find(
    (entry) => entry.questionId !== context.selectedQuestionId && entry.action !== 'WAIT',
  )
  if (next !== undefined) {
    return action('GO_TO_QUESTION', `${next.questionOrder}번 문항 이어 쓰기`, next.message, {
      targetQuestionId: next.questionId,
    })
  }
  if (context.status === 'FINALIZED') {
    return action('NONE', '', '작성을 마친 자기소개서예요. 고치면 다시 작성 중으로 돌아가요.')
  }
  const pending = context.completion.find((entry) => entry.action !== 'WAIT')
  if (pending !== undefined) {
    return action('OPEN_COMPLETION', '남은 확인 사항 보기', pending.message, {
      targetQuestionId: pending.questionId,
    })
  }
  if (context.completion.length > 0) {
    const waiting = context.completion[0]
    return action('NONE', '', waiting?.message ?? '조금만 더 확인하면 작성을 마칠 수 있어요.')
  }
  return action('FINALIZE', '작성 완료', '이대로 제출본으로 표시할게요.', {
    disabled: !context.canFinalize || context.finalizePending,
  })
}

function item(
  question: CoverLetterQuestionDto,
  action: CompletionAction,
  message: string,
): CompletionItem {
  return {
    questionId: question.id,
    questionOrder: question.questionOrder,
    action,
    message,
  }
}

function action(
  kind: PrimaryActionKind,
  label: string,
  hint: string,
  options: { disabled?: boolean; targetQuestionId?: string } = {},
): PrimaryAction {
  return {
    kind,
    label,
    hint,
    disabled: options.disabled ?? false,
    targetQuestionId: options.targetQuestionId ?? '',
  }
}
