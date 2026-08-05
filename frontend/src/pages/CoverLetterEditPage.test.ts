import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  COVER_LETTER_ANSWER_ID,
  COVER_LETTER_EVIDENCE_ID,
  COVER_LETTER_ID,
  COVER_LETTER_JOB_ID,
  COVER_LETTER_QUESTION_ID,
  COVER_LETTER_RUN_ID,
  COVER_LETTER_VERIFICATION_ID,
  answerVersionFixture,
  coverLetterDetailFixture,
  questionFixture,
  tipTapDocument,
  uuid,
  verificationFixture,
} from '@/features/cover-letters/testFixtures'
import { ApiClientError } from '@/shared/api/errors'

import CoverLetterEditPage from './CoverLetterEditPage.vue'

const mocks = vi.hoisted(() => {
  const query = () => ({
    data: { value: undefined as unknown },
    error: { value: null as unknown },
    isLoading: { value: false },
    isError: { value: false },
    refetch: vi.fn(async () => undefined),
  })
  const mutation = () => ({
    isPending: { value: false },
    mutateAsync: vi.fn(),
  })
  return {
    detail: query(),
    versions: query(),
    verifications: query(),
    latestRun: query(),
    job: query(),
    analysis: query(),
    updateCover: mutation(),
    createQuestion: mutation(),
    updateQuestion: mutation(),
    deleteQuestion: mutation(),
    reorder: mutation(),
    generate: mutation(),
    saveVersion: mutation(),
    restore: mutation(),
    verify: mutation(),
    finalize: mutation(),
    archive: mutation(),
    unarchive: mutation(),
    invalidate: vi.fn(async () => undefined),
    listEvidence: vi.fn(),
    insertSuggestion: vi.fn(),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: 'user-1' } }),
}))

vi.mock('@/features/jobs/queries', () => ({
  useJobDetailQuery: () => mocks.job,
  useLatestJobAnalysisQuery: () => mocks.analysis,
}))

vi.mock('@/features/cover-letters/queries', () => ({
  invalidateCoverLetterQueries: mocks.invalidate,
  useAnswerVersionListQuery: () => mocks.versions,
  useArchiveCoverLetterMutation: () => mocks.archive,
  useCoverLetterDetailQuery: () => mocks.detail,
  useCreateQuestionMutation: () => mocks.createQuestion,
  useDeleteQuestionMutation: () => mocks.deleteQuestion,
  useFinalizeCoverLetterMutation: () => mocks.finalize,
  useGenerateCoverLetterMutation: () => mocks.generate,
  useLatestCoverLetterRunQuery: () => mocks.latestRun,
  useReorderQuestionsMutation: () => mocks.reorder,
  useRestoreAnswerVersionMutation: () => mocks.restore,
  useSaveAnswerVersionMutation: () => mocks.saveVersion,
  useUnarchiveCoverLetterMutation: () => mocks.unarchive,
  useUpdateCoverLetterMutation: () => mocks.updateCover,
  useUpdateQuestionMutation: () => mocks.updateQuestion,
  useVerificationListQuery: () => mocks.verifications,
  useVerifyAnswerVersionMutation: () => mocks.verify,
}))

vi.mock('@/shared/api/profileApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/api/profileApi')>()
  return { ...actual, listEvidence: mocks.listEvidence }
})

const EditorStub = defineComponent({
  name: 'CoverLetterTipTapEditor',
  props: {
    readonly: Boolean,
    content: {
      type: Object,
      default: () => ({}),
    },
  },
  emits: ['update'],
  setup(props, { emit, expose }) {
    expose({ insertSuggestion: mocks.insertSuggestion })
    return () =>
      h(
        'button',
        {
          type: 'button',
          disabled: props.readonly,
          'data-testid': 'fake-cover-editor',
          onClick: () => emit('update', tipTapDocument('사용자가 수정한 답변 😀'), 13),
        },
        props.readonly ? '읽기 전용 편집기' : '답변 편집',
      )
  },
})

describe('CoverLetterEditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    const current = answerVersionFixture({ versionNo: 2 })
    mocks.detail.data = ref(
      coverLetterDetailFixture({
        questions: [
          questionFixture({
            currentAnswer: current,
            latestVerification: verificationFixture(),
          }),
        ],
      }),
    )
    mocks.detail.refetch.mockResolvedValue(undefined)
    mocks.versions.data.value = page([
      current,
      answerVersionFixture({
        id: uuid(130),
        versionNo: 1,
        plainText: '이전 답변',
        contentJson: tipTapDocument('이전 답변'),
        characterCount: 5,
        sourceType: 'AI_GENERATED',
        isCurrent: false,
      }),
    ])
    mocks.verifications.data.value = page([
      verificationFixture({
        issues: [
          {
            code: 'UNVERIFIED_CLAIM',
            severity: 'WARNING',
            message: '현재 승인 상태가 바뀐 근거를 확인해 주세요.',
            relatedText: '성과를 높였습니다.',
            evidenceRefs: [
              {
                id: COVER_LETTER_EVIDENCE_ID,
                title: '거절된 과거 근거',
                evidenceCategory: 'PROJECT',
                verificationStatus: 'REJECTED',
                sourceType: 'DOCUMENT_CHUNK',
                sourceDeleted: false,
              },
            ],
          },
        ],
        evidenceRefs: [
          {
            id: uuid(131),
            title: '원본이 삭제된 근거',
            evidenceCategory: 'CAREER',
            verificationStatus: 'SOURCE_DELETED',
            sourceType: 'DOCUMENT_CHUNK',
            sourceDeleted: true,
          },
        ],
      }),
    ])
    mocks.latestRun.data.value = page([])
    mocks.job.data.value = {
      id: COVER_LETTER_JOB_ID,
      title: 'Frontend Engineer',
      positionName: '프론트엔드 개발자',
      companyName: 'Hiresemble',
      analysisOutdated: false,
    }
    mocks.analysis.data.value = {
      requiredQualifications: [{ category: 'REQUIRED_QUALIFICATION', text: 'Vue 경험' }],
      responsibilities: [{ category: 'CORE_RESPONSIBILITY_OR_SKILL', text: '사용자 경험 개선' }],
      strengths: ['컴포넌트 구조를 정리한 경험이 공고와 잘 맞아요.'],
      gaps: ['대규모 트래픽 경험을 보강하면 좋아요.'],
      matchedEvidenceRefs: [{ id: COVER_LETTER_EVIDENCE_ID }],
    }
    for (const state of [
      mocks.detail,
      mocks.versions,
      mocks.verifications,
      mocks.latestRun,
      mocks.job,
      mocks.analysis,
    ]) {
      state.error.value = null
      state.isLoading.value = false
      state.isError.value = false
    }
    mocks.listEvidence.mockResolvedValue(
      page([
        {
          id: COVER_LETTER_EVIDENCE_ID,
          title: '검증된 프로젝트 경험',
          evidenceCategory: 'PROJECT',
          content: '주문 처리 지연을 35% 줄인 개선 경험입니다.',
          verificationStatus: 'VERIFIED',
        },
      ]),
    )
    mocks.updateCover.mutateAsync.mockResolvedValue(mocks.detail.data.value)
    mocks.createQuestion.mutateAsync.mockResolvedValue(questionFixture())
    mocks.updateQuestion.mutateAsync.mockResolvedValue(questionFixture())
    mocks.deleteQuestion.mutateAsync.mockResolvedValue(undefined)
    mocks.reorder.mutateAsync.mockResolvedValue(mocks.detail.data.value)
    mocks.generate.mutateAsync.mockResolvedValue(runAccepted())
    mocks.saveVersion.mutateAsync.mockResolvedValue(
      answerVersionFixture({
        id: uuid(140),
        versionNo: 3,
        parentVersionId: COVER_LETTER_ANSWER_ID,
        plainText: '사용자가 수정한 답변 😀',
        contentJson: tipTapDocument('사용자가 수정한 답변 😀'),
        characterCount: 13,
      }),
    )
    mocks.restore.mutateAsync.mockResolvedValue(
      answerVersionFixture({
        id: uuid(141),
        versionNo: 3,
        parentVersionId: COVER_LETTER_ANSWER_ID,
        restoredFromVersionId: uuid(130),
        sourceType: 'RESTORED',
      }),
    )
    mocks.verify.mutateAsync.mockResolvedValue(runAccepted())
    mocks.finalize.mutateAsync.mockResolvedValue(coverLetterDetailFixture({ status: 'FINALIZED' }))
    mocks.archive.mutateAsync.mockResolvedValue(coverLetterDetailFixture({ status: 'ARCHIVED' }))
    mocks.unarchive.mutateAsync.mockResolvedValue(coverLetterDetailFixture({ status: 'DRAFT' }))
  })

  afterEach(() => {
    window.sessionStorage.clear()
  })

  it('keeps browser drafts separate, explicitly saves versions, and sends bounded AI commands', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="cover-letter-editor"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Vue 경험')
    expect(wrapper.text()).toContain('현재 승인 거절됨')
    expect(wrapper.text()).toContain('원본 삭제됨')
    expect(wrapper.text()).toContain('새 생성·검증에서는 제외됨')

    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('서버 미저장')
    expect(window.sessionStorage.length).toBe(1)

    await wrapper.get('[data-testid="save-answer-version"]').trigger('click')
    await flushPromises()
    expect(mocks.saveVersion.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: COVER_LETTER_QUESTION_ID,
      request: {
        contentJson: tipTapDocument('사용자가 수정한 답변 😀'),
        parentVersionId: COVER_LETTER_ANSWER_ID,
      },
    })
    const saveRequest = mocks.saveVersion.mutateAsync.mock.calls[0]?.[0]?.request
    expect(saveRequest).not.toHaveProperty('sourceType')
    expect(saveRequest).not.toHaveProperty('createdBy')
    expect(window.sessionStorage.length).toBe(0)

    await wrapper.get('.evidence-options input').setValue(true)
    await wrapper.get('select').setValue('HIGH_QUALITY')
    await wrapper.get('[data-testid="generate-cover-letter"]').trigger('click')
    await flushPromises()
    expect(mocks.generate.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        questionIds: [COVER_LETTER_QUESTION_ID],
        preferredEvidenceIds: [COVER_LETTER_EVIDENCE_ID],
        qualityMode: 'HIGH_QUALITY',
        avoidExperienceDuplication: true,
        coverLetterVersion: 3,
      },
    })
    expect(wrapper.text()).toContain('편집기는 계속 사용할 수 있어요.')

    await wrapper.get('[data-testid="verify-answer-version"]').trigger('click')
    await flushPromises()
    expect(mocks.verify.mutateAsync).not.toHaveBeenCalled()
    expect(
      wrapper.get('[data-testid="verify-answer-version"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('submits answer verification when no other AI command is active', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="verify-answer-version"]').trigger('click')
    await flushPromises()

    expect(mocks.verify.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      versionId: COVER_LETTER_ANSWER_ID,
      request: { qualityMode: 'BALANCED' },
    })
  })

  it('restores an active cover-letter run and disables both AI command buttons', async () => {
    mocks.latestRun.data.value = page([{ id: COVER_LETTER_RUN_ID, status: 'RUNNING' }])
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="run-monitor"]').exists()).toBe(true)
    expect(
      wrapper.get('[data-testid="generate-cover-letter"]').attributes('disabled'),
    ).toBeDefined()
    expect(
      wrapper.get('[data-testid="verify-answer-version"]').attributes('disabled'),
    ).toBeDefined()
    await wrapper.get('[data-testid="generate-cover-letter"]').trigger('click')
    expect(mocks.generate.mutateAsync).not.toHaveBeenCalled()
  })

  it('submits a valid new question from the browser form', async () => {
    mocks.detail.data.value = coverLetterDetailFixture({ questions: [] })
    const wrapper = await mountPage()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '문항 추가')
      ?.trigger('click')
    await flushPromises()

    const form = wrapper.get('.question-add')
    await form.get('textarea').setValue('승인된 경험을 바탕으로 지원 동기를 작성해 주세요.')
    await form.get('input[type="number"]').setValue('1000')
    await form.trigger('submit')
    await flushPromises()

    expect(mocks.createQuestion.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        questionOrder: 1,
        questionText: '승인된 경험을 바탕으로 지원 동기를 작성해 주세요.',
        maxLength: 1000,
        memo: null,
        coverLetterVersion: 3,
      },
    })
  })

  it('restores an immutable version and finalizes only after warning acknowledgement', async () => {
    const wrapper = await mountPage()
    const finalize = wrapper.get<HTMLButtonElement>('[data-testid="finalize-cover-letter"]')
    expect(finalize.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('1번 문항의 경고를 확인해 주세요.')

    const oldVersion = wrapper
      .get('[aria-label="답변 버전"]')
      .findAll('button')
      .find((button) => button.text().includes('v1'))
    await oldVersion?.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="restore-answer-version"]').trigger('click')
    await flushPromises()
    expect(mocks.restore.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: COVER_LETTER_QUESTION_ID,
      versionId: uuid(130),
      request: { expectedCurrentVersionId: COVER_LETTER_ANSWER_ID },
    })

    await wrapper.get('.finalization__warnings input').setValue(true)
    await flushPromises()
    expect(finalize.attributes('disabled')).toBeUndefined()
    await finalize.trigger('click')
    await flushPromises()
    expect(mocks.finalize.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        version: 3,
        acknowledgedWarningVerificationIds: [COVER_LETTER_VERIFICATION_ID],
      },
    })
    expect(wrapper.text()).toContain('공고의 제출 상태는 별도 사용자 행동')
  })

  it('updates, reorders and soft-deletes questions through aggregate versions', async () => {
    const secondQuestionId = uuid(150)
    mocks.detail.data.value = coverLetterDetailFixture({
      questions: [
        questionFixture(),
        questionFixture({
          id: secondQuestionId,
          questionOrder: 2,
          questionText: '두 번째 문항',
          currentAnswer: null,
          latestVerification: null,
          version: 1,
        }),
      ],
    })
    const wrapper = await mountPage()

    const secondSelect = wrapper
      .findAll('.question-list__select')
      .find((button) => button.text().includes('두 번째 문항'))
    await secondSelect?.trigger('click')
    await flushPromises()
    const questionForm = wrapper.get('.question-meta__form')
    await questionForm.get('textarea').setValue('수정한 두 번째 문항')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '문항 저장')
      ?.trigger('click')
    await flushPromises()
    expect(mocks.updateQuestion.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: secondQuestionId,
      request: {
        questionOrder: 2,
        questionText: '수정한 두 번째 문항',
        maxLength: 1000,
        memo: null,
        version: 1,
      },
    })

    await wrapper.get('[aria-label="2번 문항 위로 이동"]').trigger('click')
    await flushPromises()
    expect(mocks.reorder.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        questionIds: [secondQuestionId, COVER_LETTER_QUESTION_ID],
        version: 3,
      },
    })

    window.sessionStorage.setItem(
      `1/user-1/COVER_LETTER/${COVER_LETTER_ID}/${secondQuestionId}/none`,
      '{}',
    )
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '문항 삭제')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '삭제 확인')
      ?.trigger('click')
    await flushPromises()
    expect(mocks.deleteQuestion.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: secondQuestionId,
      version: 1,
    })
    expect(window.sessionStorage.length).toBe(0)
  })

  it('compares the latest question fields and reapplies the immutable local question snapshot', async () => {
    const serverQuestion = questionFixture({
      questionText: '서버에서 바뀐 최신 문항',
      maxLength: 777,
      memo: '서버 메모',
      version: 3,
    })
    const latestDetail = coverLetterDetailFixture({
      version: 4,
      questions: [serverQuestion],
    })
    setDetailOnNextRefetch(latestDetail)
    mocks.updateQuestion.mutateAsync
      .mockRejectedValueOnce(versionConflict())
      .mockResolvedValueOnce(serverQuestion)
    const wrapper = await mountPage()

    const questionForm = wrapper.get('.question-meta__form')
    await questionForm.get('.question-meta__text textarea').setValue('내가 저장하려던 문항')
    await questionForm.get('input[type="number"]').setValue('999')
    await questionForm.findAll('textarea')[1]!.setValue('내 메모')
    await findButton(wrapper, '문항 저장').trigger('click')
    await flushPromises()

    const panel = wrapper.get('.cover-conflict')
    expect(panel.text()).toContain('서버에서 바뀐 최신 문항')
    expect(panel.text()).toContain('최대 글자 수: 777')
    expect(panel.text()).toContain('메모: 서버 메모')
    expect(panel.text()).toContain('내가 저장하려던 문항')
    expect(panel.text()).toContain('최대 글자 수: 999')
    expect(mocks.updateQuestion.mutateAsync).toHaveBeenCalledTimes(1)

    await questionForm.get('.question-meta__text textarea').setValue('refetch 뒤 덮어쓴 값')
    await panel.get('button.button--primary').trigger('click')
    await flushPromises()

    expect(mocks.updateQuestion.mutateAsync).toHaveBeenCalledTimes(2)
    expect(mocks.updateQuestion.mutateAsync).toHaveBeenLastCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: COVER_LETTER_QUESTION_ID,
      request: {
        questionOrder: 1,
        questionText: '내가 저장하려던 문항',
        maxLength: 999,
        memo: '내 메모',
        version: 3,
      },
    })
  })

  it('cancels a question conflict without retrying and restores the latest server fields', async () => {
    const serverQuestion = questionFixture({
      questionText: '취소 시 유지할 서버 문항',
      maxLength: 640,
      memo: '서버 기준 메모',
      version: 4,
    })
    setDetailOnNextRefetch(
      coverLetterDetailFixture({
        version: 5,
        questions: [serverQuestion],
      }),
    )
    mocks.updateQuestion.mutateAsync.mockRejectedValueOnce(versionConflict())
    const wrapper = await mountPage()
    const questionForm = wrapper.get('.question-meta__form')

    await questionForm.get('.question-meta__text textarea').setValue('취소할 내 문항')
    await findButton(wrapper, '문항 저장').trigger('click')
    await flushPromises()
    await questionForm.get('.question-meta__text textarea').setValue('충돌 뒤 다시 쓴 값')
    await wrapper.get('.cover-conflict button.button--secondary').trigger('click')
    await flushPromises()

    expect(mocks.updateQuestion.mutateAsync).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.cover-conflict').exists()).toBe(false)
    expect(
      (
        questionForm.get<HTMLTextAreaElement>('.question-meta__text textarea')
          .element as HTMLTextAreaElement
      ).value,
    ).toBe('취소 시 유지할 서버 문항')
    expect(
      (questionForm.get<HTMLInputElement>('input[type="number"]').element as HTMLInputElement)
        .value,
    ).toBe('640')
    expect(
      (questionForm.findAll<HTMLTextAreaElement>('textarea')[1]!.element as HTMLTextAreaElement)
        .value,
    ).toBe('서버 기준 메모')
  })

  it('compares the exact server order and reapplies the saved full question sequence', async () => {
    const secondQuestionId = uuid(150)
    const thirdQuestionId = uuid(151)
    const initialQuestions = [
      questionFixture({ questionText: '첫 번째 로컬 문항' }),
      questionFixture({
        id: secondQuestionId,
        questionOrder: 2,
        questionText: '두 번째 로컬 문항',
        currentAnswer: null,
        latestVerification: null,
        version: 1,
      }),
      questionFixture({
        id: thirdQuestionId,
        questionOrder: 3,
        questionText: '세 번째 로컬 문항',
        currentAnswer: null,
        latestVerification: null,
        version: 1,
      }),
    ]
    mocks.detail.data.value = coverLetterDetailFixture({ questions: initialQuestions })
    const latestDetail = coverLetterDetailFixture({
      version: 4,
      questions: [
        questionFixture({
          id: thirdQuestionId,
          questionOrder: 1,
          questionText: '서버 세 번째 문항',
          currentAnswer: null,
          latestVerification: null,
          version: 2,
        }),
        questionFixture({
          questionOrder: 2,
          questionText: '서버 첫 번째 문항',
          version: 3,
        }),
        questionFixture({
          id: secondQuestionId,
          questionOrder: 3,
          questionText: '서버 두 번째 문항',
          currentAnswer: null,
          latestVerification: null,
          version: 2,
        }),
      ],
    })
    setDetailOnNextRefetch(latestDetail)
    mocks.reorder.mutateAsync
      .mockRejectedValueOnce(versionConflict())
      .mockResolvedValueOnce(latestDetail)
    const wrapper = await mountPage()

    await wrapper.get('[aria-label="2번 문항 위로 이동"]').trigger('click')
    await flushPromises()

    const panel = wrapper.get('.cover-conflict')
    expect(panel.text()).toContain('최신 서버 순서')
    expect(panel.text()).toContain('1. 서버 세 번째 문항')
    expect(panel.text()).toContain('2. 서버 첫 번째 문항')
    expect(panel.text()).toContain('3. 서버 두 번째 문항')
    expect(panel.text()).toContain('1. 두 번째 로컬 문항')
    expect(panel.text()).toContain('2. 첫 번째 로컬 문항')
    expect(panel.text()).toContain('3. 세 번째 로컬 문항')

    await panel.get('button.button--primary').trigger('click')
    await flushPromises()
    expect(mocks.reorder.mutateAsync).toHaveBeenLastCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        questionIds: [secondQuestionId, COVER_LETTER_QUESTION_ID, thirdQuestionId],
        version: 4,
      },
    })
  })

  it('cancels an order conflict without issuing a second reorder', async () => {
    const secondQuestionId = uuid(152)
    mocks.detail.data.value = coverLetterDetailFixture({
      questions: [
        questionFixture({ questionText: '로컬 첫 문항' }),
        questionFixture({
          id: secondQuestionId,
          questionOrder: 2,
          questionText: '로컬 둘째 문항',
          currentAnswer: null,
          latestVerification: null,
          version: 1,
        }),
      ],
    })
    setDetailOnNextRefetch(
      coverLetterDetailFixture({
        version: 4,
        questions: [
          questionFixture({
            id: secondQuestionId,
            questionOrder: 1,
            questionText: '서버 첫 순서 문항',
            currentAnswer: null,
            latestVerification: null,
            version: 2,
          }),
          questionFixture({
            questionOrder: 2,
            questionText: '서버 둘째 순서 문항',
            version: 3,
          }),
        ],
      }),
    )
    mocks.reorder.mutateAsync.mockRejectedValueOnce(versionConflict())
    const wrapper = await mountPage()

    await wrapper.get('[aria-label="2번 문항 위로 이동"]').trigger('click')
    await flushPromises()
    await wrapper.get('.cover-conflict button.button--secondary').trigger('click')
    await flushPromises()

    expect(mocks.reorder.mutateAsync).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.cover-conflict').exists()).toBe(false)
    expect(wrapper.findAll('.question-list__select')[0]!.text()).toContain('서버 첫 순서 문항')
  })

  it('compares the current answer ID, version and content then reapplies the saved editor document', async () => {
    const latestAnswerId = uuid(160)
    const latestAnswer = answerVersionFixture({
      id: latestAnswerId,
      versionNo: 3,
      parentVersionId: COVER_LETTER_ANSWER_ID,
      plainText: '서버의 최신 답변',
      contentJson: tipTapDocument('서버의 최신 답변'),
      characterCount: 9,
    })
    setDetailOnNextRefetch(
      coverLetterDetailFixture({
        version: 4,
        questions: [
          questionFixture({
            version: 3,
            currentAnswer: latestAnswer,
          }),
        ],
      }),
    )
    mocks.saveVersion.mutateAsync.mockRejectedValueOnce(versionConflict()).mockResolvedValueOnce(
      answerVersionFixture({
        id: uuid(161),
        versionNo: 4,
        parentVersionId: latestAnswerId,
        plainText: '사용자가 수정한 답변 😀',
        contentJson: tipTapDocument('사용자가 수정한 답변 😀'),
        characterCount: 13,
      }),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await wrapper.get('[data-testid="save-answer-version"]').trigger('click')
    await flushPromises()

    const panel = wrapper.get('.cover-conflict')
    expect(panel.text()).toContain(`현재 답변 ID: ${latestAnswerId}`)
    expect(panel.text()).toContain('답변 version: 3')
    expect(panel.text()).toContain('서버의 최신 답변')
    expect(panel.text()).toContain('사용자가 수정한 답변 😀')

    wrapper
      .findComponent(EditorStub)
      .vm.$emit('update', tipTapDocument('충돌 뒤 반응형 편집 값'), 12)
    await flushPromises()
    await panel.get('button.button--primary').trigger('click')
    await flushPromises()

    expect(mocks.saveVersion.mutateAsync).toHaveBeenLastCalledWith({
      coverLetterId: COVER_LETTER_ID,
      questionId: COVER_LETTER_QUESTION_ID,
      request: {
        contentJson: tipTapDocument('사용자가 수정한 답변 😀'),
        parentVersionId: latestAnswerId,
      },
    })
  })

  it('cancels an answer conflict, keeps the server answer and does not save automatically', async () => {
    const latestAnswerId = uuid(162)
    const latestAnswer = answerVersionFixture({
      id: latestAnswerId,
      versionNo: 3,
      parentVersionId: COVER_LETTER_ANSWER_ID,
      plainText: '취소 시 유지할 서버 답변',
      contentJson: tipTapDocument('취소 시 유지할 서버 답변'),
      characterCount: 13,
    })
    setDetailOnNextRefetch(
      coverLetterDetailFixture({
        version: 4,
        questions: [questionFixture({ version: 3, currentAnswer: latestAnswer })],
      }),
    )
    mocks.saveVersion.mutateAsync.mockRejectedValueOnce(versionConflict())
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await wrapper.get('[data-testid="save-answer-version"]').trigger('click')
    await flushPromises()
    wrapper
      .findComponent(EditorStub)
      .vm.$emit('update', tipTapDocument('충돌 뒤 다시 쓴 미저장 답변'), 15)
    await flushPromises()
    await wrapper.get('.cover-conflict button.button--secondary').trigger('click')
    await flushPromises()

    expect(mocks.saveVersion.mutateAsync).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.cover-conflict').exists()).toBe(false)
    expect(wrapper.findComponent(EditorStub).props('content')).toEqual(latestAnswer.contentJson)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('makes archived content read-only and exposes only conditional DRAFT recovery', async () => {
    mocks.detail.data.value = coverLetterDetailFixture({
      status: 'ARCHIVED',
      canEdit: false,
      canArchive: false,
      canUnarchive: true,
      canFinalize: false,
      archivedAt: '2026-07-30T01:00:00Z',
    })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('ARCHIVED · 읽기 전용')
    expect(wrapper.find('[data-testid="save-answer-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="generate-cover-letter"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="verify-answer-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="restore-answer-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="finalize-cover-letter"]').exists()).toBe(false)

    const recover = wrapper.findAll('button').find((button) => button.text() === 'DRAFT로 복구')
    await recover?.trigger('click')
    await flushPromises()
    expect(mocks.unarchive.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      version: 3,
    })
  })
})

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/cover-letters/:coverLetterId/edit',
        name: 'cover-letter-edit',
        component: CoverLetterEditPage,
      },
      { path: '/cover-letters', name: 'cover-letters', component: { template: '<div />' } },
      {
        path: '/jobs/:jobId/analysis',
        name: 'job-analysis',
        component: { template: '<div />' },
      },
      {
        path: '/agent-runs/:agentRunId',
        name: 'agent-run-detail',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/cover-letters/${COVER_LETTER_ID}/edit`)
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = mount(CoverLetterEditPage, {
    global: {
      plugins: [router, [VueQueryPlugin, { queryClient }]],
      stubs: {
        CoverLetterTipTapEditor: EditorStub,
        CoverLetterRunMonitor: { template: '<div data-testid="run-monitor" />' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 100,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
  }
}

function runAccepted() {
  return {
    agentRunId: COVER_LETTER_RUN_ID,
    status: 'QUEUED',
    resourceType: 'COVER_LETTER',
    resourceId: COVER_LETTER_ID,
    replayed: false,
  }
}

function setDetailOnNextRefetch(detail: ReturnType<typeof coverLetterDetailFixture>): void {
  mocks.detail.refetch.mockImplementationOnce(async () => {
    mocks.detail.data.value = detail
    return undefined
  })
}

function versionConflict(): ApiClientError {
  return new ApiClientError({
    message: '최신 서버 버전과 충돌했어요.',
    status: 409,
    code: 'OPTIMISTIC_LOCK_CONFLICT',
  })
}

function findButton(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper
    .findAll<HTMLButtonElement>('button')
    .find((candidate) => candidate.text() === text)
  if (!button) throw new Error(`Button not found: ${text}`)
  return button
}
