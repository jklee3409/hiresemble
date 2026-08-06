import { describe, expect, it } from 'vitest'

import {
  answerVersionFixture,
  questionFixture,
  uuid,
  verificationFixture,
} from '@/features/cover-letters/testFixtures'
import type { CoverLetterQuestionDto } from '@/shared/api/coverLetterContracts'

import {
  completionItems,
  freshVerification,
  questionWorkState,
  resolvePrimaryAction,
  type FlowContext,
} from './editorFlow'

const SECOND_QUESTION_ID = uuid(210)

describe('questionWorkState', () => {
  it('reports the answer, save and review stage of a question', () => {
    expect(questionWorkState(questionFixture({ currentAnswer: null }))).toBe('EMPTY')
    expect(questionWorkState(questionFixture(), { dirty: true })).toBe('UNSAVED')
    expect(questionWorkState(questionFixture({ latestVerification: null }))).toBe('REVIEW_NEEDED')
    expect(
      questionWorkState(
        questionFixture({ latestVerification: verificationFixture({ status: 'PENDING' }) }),
      ),
    ).toBe('REVIEW_RUNNING')
    expect(
      questionWorkState(
        questionFixture({ latestVerification: verificationFixture({ status: 'FAILED' }) }),
      ),
    ).toBe('REVIEW_FAILED')
    expect(questionWorkState(questionFixture())).toBe('REVIEW_WARNING')
    expect(
      questionWorkState(
        questionFixture({ latestVerification: verificationFixture({ status: 'PASSED' }) }),
      ),
    ).toBe('READY')
  })

  it('treats a verification of an older answer version as not reviewed', () => {
    const question = questionFixture({
      currentAnswer: answerVersionFixture({ id: uuid(211), versionNo: 4 }),
      latestVerification: verificationFixture({ status: 'PASSED' }),
    })

    expect(freshVerification(question)).toBeNull()
    expect(questionWorkState(question)).toBe('REVIEW_NEEDED')
  })
})

describe('completionItems', () => {
  it('lists every backend finalize condition with the question to move to', () => {
    const items = completionItems({
      questions: [
        questionFixture({ currentAnswer: null, latestVerification: null }),
        questionFixture({
          id: SECOND_QUESTION_ID,
          questionOrder: 2,
          maxLength: 5,
          latestVerification: null,
        }),
      ],
      status: 'DRAFT',
      dirtyQuestionId: SECOND_QUESTION_ID,
      acknowledgedWarningIds: new Set(),
    })

    expect(items.map((item) => item.action)).toEqual(['WRITE', 'SAVE', 'SHORTEN', 'REVIEW'])
    expect(items[0]?.message).toContain('1번 문항의 답변을 아직 쓰지 않았어요')
    expect(items[1]?.questionId).toBe(SECOND_QUESTION_ID)
  })

  it('keeps a warning question blocked until the user acknowledges it', () => {
    const question = questionFixture()
    const verification = question.latestVerification!

    expect(
      completionItems({
        questions: [question],
        status: 'DRAFT',
        dirtyQuestionId: '',
        acknowledgedWarningIds: new Set(),
      }).map((item) => item.action),
    ).toEqual(['ACKNOWLEDGE'])
    expect(
      completionItems({
        questions: [question],
        status: 'DRAFT',
        dirtyQuestionId: '',
        acknowledgedWarningIds: new Set([verification.id]),
      }),
    ).toEqual([])
  })

  it('does not ask for anything once the cover letter is finalized', () => {
    expect(
      completionItems({
        questions: [questionFixture({ currentAnswer: null })],
        status: 'FINALIZED',
        dirtyQuestionId: '',
        acknowledgedWarningIds: new Set(),
      }),
    ).toEqual([])
  })
})

describe('resolvePrimaryAction', () => {
  it('asks for the first question before anything else', () => {
    expect(resolvePrimaryAction(context({ questions: [] })).kind).toBe('ADD_QUESTION')
  })

  it('prefers saving the edited answer over any AI action', () => {
    const action = resolvePrimaryAction(context({ editorDirty: true }))

    expect(action.kind).toBe('SAVE_ANSWER')
    expect(action.label).toBe('답변 저장')
    expect(action.disabled).toBe(false)
  })

  it('blocks saving while the answer is over the length limit', () => {
    expect(
      resolvePrimaryAction(context({ editorDirty: true, editorOverLimit: true })).disabled,
    ).toBe(true)
  })

  it('offers the first draft when the selected question has no answer', () => {
    const question = questionFixture({ currentAnswer: null, latestVerification: null })
    const action = resolvePrimaryAction(
      context({ questions: [question], selectedQuestionId: question.id }),
    )

    expect(action.kind).toBe('GENERATE')
    expect(action.label).toBe('AI 초안 만들기')
  })

  it('asks for the review that the backend requires before finalizing', () => {
    const question = questionFixture({ latestVerification: null })
    const action = resolvePrimaryAction(
      context({ questions: [question], selectedQuestionId: question.id }),
    )

    expect(action.kind).toBe('VERIFY')
    expect(action.label).toBe('AI 검토 받기')
    expect(action.hint).toContain('작성 완료')
  })

  it('disables AI actions while another AI run is in progress', () => {
    const question = questionFixture({ latestVerification: null })
    const action = resolvePrimaryAction(
      context({
        questions: [question],
        selectedQuestionId: question.id,
        aiBusy: true,
        aiUnavailableReason: '진행 중인 AI 작업이 끝나면 이어서 요청할 수 있어요.',
      }),
    )

    expect(action.disabled).toBe(true)
    expect(action.hint).toContain('진행 중인 AI 작업')
  })

  it('points at the remaining completion condition of the current question', () => {
    const question = questionFixture()
    const completion = completionItems({
      questions: [question],
      status: 'DRAFT',
      dirtyQuestionId: '',
      acknowledgedWarningIds: new Set(),
    })
    const action = resolvePrimaryAction(
      context({ questions: [question], selectedQuestionId: question.id, completion }),
    )

    expect(action.kind).toBe('OPEN_COMPLETION')
    expect(action.hint).toContain('확인 사항을 읽어 주세요')
  })

  it('moves to the next unfinished question when the current one is done', () => {
    const first = questionFixture({ latestVerification: verificationFixture({ status: 'PASSED' }) })
    const second = questionFixture({
      id: SECOND_QUESTION_ID,
      questionOrder: 2,
      currentAnswer: null,
      latestVerification: null,
    })
    const completion = completionItems({
      questions: [first, second],
      status: 'DRAFT',
      dirtyQuestionId: '',
      acknowledgedWarningIds: new Set(),
    })
    const action = resolvePrimaryAction(
      context({ questions: [first, second], selectedQuestionId: first.id, completion }),
    )

    expect(action.kind).toBe('GO_TO_QUESTION')
    expect(action.targetQuestionId).toBe(SECOND_QUESTION_ID)
  })

  it('finishes with a single completion action when nothing is left', () => {
    const question = questionFixture({
      latestVerification: verificationFixture({ status: 'PASSED' }),
    })
    const action = resolvePrimaryAction(
      context({ questions: [question], selectedQuestionId: question.id, canFinalize: true }),
    )

    expect(action.kind).toBe('FINALIZE')
    expect(action.label).toBe('작성 완료')
    expect(action.disabled).toBe(false)
  })

  it('hides every action while AI writes a draft and offers recovery when archived', () => {
    expect(resolvePrimaryAction(context({ generationInProgress: true })).kind).toBe('NONE')
    expect(resolvePrimaryAction(context({ status: 'ARCHIVED', canUnarchive: true })).kind).toBe(
      'UNARCHIVE',
    )
    expect(resolvePrimaryAction(context({ status: 'ARCHIVED' })).kind).toBe('NONE')
  })
})

function context(overrides: Partial<FlowContext> = {}): FlowContext {
  const questions: readonly CoverLetterQuestionDto[] = overrides.questions ?? [questionFixture()]
  return {
    status: 'DRAFT',
    canUnarchive: false,
    canFinalize: false,
    questions,
    selectedQuestionId: questions[0]?.id ?? '',
    editorDirty: false,
    editorOverLimit: false,
    generationInProgress: false,
    aiBusy: false,
    aiUnavailableReason: '',
    savePending: false,
    finalizePending: false,
    unarchivePending: false,
    completion: [],
    ...overrides,
  }
}
