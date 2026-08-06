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
import { useNotifications } from '@/shared/ui/notifications'

import CoverLetterEditPage from './CoverLetterEditPage.vue'

const UNUSED_EVIDENCE_ID = uuid(170)
const RECOMMENDED_MODEL = 'gpt-5.6-terra'
const HIGH_CAPABILITY_MODEL = 'gpt-5.6-sol'

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
    aiModels: query(),
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
  useCoverLetterAiModelsQuery: () => mocks.aiModels,
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
    useNotifications().state.toasts.splice(0)
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
    mocks.aiModels.data.value = [
      {
        id: HIGH_CAPABILITY_MODEL,
        displayName: 'GPT-5.6 Sol',
        description: '가장 꼼꼼하게 씁니다.',
        recommended: false,
      },
      {
        id: RECOMMENDED_MODEL,
        displayName: 'GPT-5.6 Terra',
        description: '속도와 완성도가 균형 잡혀 있습니다.',
        recommended: true,
      },
    ]
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
      mocks.aiModels,
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
        {
          id: UNUSED_EVIDENCE_ID,
          title: '아직 쓰지 않은 협업 경험',
          evidenceCategory: 'PROJECT',
          content: '다른 팀과 배포 절차를 정리한 경험입니다.',
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

  it('puts the question and the answer editor first without stacking status cards', async () => {
    const wrapper = await mountPage()
    const question = questionFixture()

    const workspace = wrapper.get('[data-testid="cover-letter-editor"]')
    const editor = wrapper.get('[data-testid="fake-cover-editor"]')
    expect(workspace.element.contains(editor.element)).toBe(true)
    expect(wrapper.get('.answer-brief__order').text()).toBe('1')
    expect(wrapper.get('.answer-brief__question').text()).toContain(question.questionText)

    const rail = wrapper.get('[role="tablist"][aria-label="자기소개서 문항"]')
    expect(rail.attributes('aria-orientation')).toBe('vertical')
    const tab = rail.get('[role="tab"]')
    expect(tab.attributes('aria-label')).toContain(question.questionText)
    expect(tab.attributes('aria-selected')).toBe('true')

    // 생성 설정과 버전 기록은 기본 화면을 차지하지 않는다.
    expect(wrapper.find('.generation-panel').exists()).toBe(false)
    expect(wrapper.find('.version-panel').exists()).toBe(false)
    expect(wrapper.find('.quality-options').exists()).toBe(false)
  })

  it('highlights exactly one primary action for the current state', async () => {
    const wrapper = await mountPage()
    const primaries = () => wrapper.findAll('button.button--primary')

    expect(primaries()).toHaveLength(1)
    expect(wrapper.get('[data-testid="primary-action"]').text()).toBe('남은 확인 사항 보기')

    // 답변을 고치면 강조는 편집기 옆 저장 button 하나로 옮겨 간다.
    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await flushPromises()
    expect(primaries()).toHaveLength(1)
    expect(wrapper.find('[data-testid="primary-action"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="save-answer-version"]').classes()).toContain(
      'button--primary',
    )
  })

  it('keeps browser drafts separate and saves versions only on request', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('저장 안 됨')
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
    expect(toastMessages()).toContainEqual(expect.stringContaining('버전 3을 저장했어요.'))
  })

  it('asks before leaving a question with unsaved content', async () => {
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
    await wrapper.get('[data-testid="fake-cover-editor"]').trigger('click')
    await flushPromises()

    const move = selectQuestionTab(wrapper, '두 번째 문항')
    await flushPromises()
    expect(useNotifications().state.confirmation?.title).toBe('저장하지 않은 답변이 있어요')
    useNotifications().resolveConfirmation(false)
    await move
    await flushPromises()
    expect(wrapper.get('.answer-brief__question').text()).toContain(questionFixture().questionText)

    const second = selectQuestionTab(wrapper, '두 번째 문항')
    await flushPromises()
    useNotifications().resolveConfirmation(true)
    await second
    await flushPromises()
    expect(wrapper.get('.answer-brief__question').text()).toContain('두 번째 문항')
  })

  it('confirms AI settings and the non-destructive rewrite before generating', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="open-generation"]').text()).toBe('AI로 다시 쓰기')
    await wrapper.get('[data-testid="open-generation"]').trigger('click')
    await flushPromises()

    const panel = wrapper.get('.generation-panel')
    expect(panel.text()).toContain('지금 답변은 지워지지 않아요')
    expect(panel.text()).toContain('새 버전으로 저장돼요')
    expect(mocks.generate.mutateAsync).not.toHaveBeenCalled()

    // 서버가 허용한 모델 목록에서 고르고, 기본값은 추천 모델이다.
    const modelSelect = () =>
      wrapper.get<HTMLSelectElement>('[data-testid="cover-letter-model-select"]')
    expect(modelSelect().element.value).toBe(RECOMMENDED_MODEL)

    const availableEvidence = wrapper.findAll('.assist__evidence label')
    expect(availableEvidence).toHaveLength(1)
    expect(availableEvidence[0]?.text()).toContain('아직 쓰지 않은 협업 경험')
    await availableEvidence[0]?.get('input[type="checkbox"]').setValue(true)
    await modelSelect().setValue(HIGH_CAPABILITY_MODEL)
    await flushPromises()
    expect(modelSelect().element.value).toBe(HIGH_CAPABILITY_MODEL)
    await wrapper.get('[data-testid="generate-cover-letter"]').trigger('click')
    await flushPromises()

    expect(mocks.generate.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        questionIds: [COVER_LETTER_QUESTION_ID],
        preferredEvidenceIds: [UNUSED_EVIDENCE_ID],
        model: HIGH_CAPABILITY_MODEL,
        avoidExperienceDuplication: true,
        coverLetterVersion: 3,
      },
    })
    expect(wrapper.find('.generation-panel').exists()).toBe(false)
  })

  it('submits answer verification when no other AI command is active', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="verify-answer-version"]').trigger('click')
    await flushPromises()

    expect(mocks.verify.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      versionId: COVER_LETTER_ANSWER_ID,
      request: { model: RECOMMENDED_MODEL },
    })
  })

  it('restores an active cover-letter run and disables both AI commands', async () => {
    mocks.latestRun.data.value = page([{ id: COVER_LETTER_RUN_ID, status: 'RUNNING' }])
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="run-monitor"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="open-generation"]').attributes('disabled')).toBeDefined()
    expect(
      wrapper.get('[data-testid="verify-answer-version"]').attributes('disabled'),
    ).toBeDefined()
    await wrapper.get('[data-testid="open-generation"]').trigger('click')
    await flushPromises()
    expect(mocks.generate.mutateAsync).not.toHaveBeenCalled()
  })

  it('submits a valid new question from the browser form', async () => {
    mocks.detail.data.value = coverLetterDetailFixture({ questions: [] })
    const wrapper = await mountPage()

    await findButton(wrapper, '문항 추가').trigger('click')
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

  it('shows what is left before completion and finalizes after acknowledgement', async () => {
    const wrapper = await mountPage()

    const completionTrigger = wrapper.get('[data-testid="open-completion"]')
    expect(completionTrigger.text()).toContain('완료까지 1가지 남았어요')
    await completionTrigger.trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="completion-blockers"]').text()).toContain(
      '1번 문항의 확인 사항을 읽어 주세요.',
    )
    const finalizeButton = () =>
      wrapper.get<HTMLButtonElement>('[data-testid="finalize-cover-letter"]')
    expect(finalizeButton().attributes('disabled')).toBeDefined()

    await wrapper.get('.finalization__warnings input').setValue(true)
    await flushPromises()
    expect(wrapper.find('[data-testid="completion-blockers"]').exists()).toBe(false)
    expect(finalizeButton().attributes('disabled')).toBeUndefined()
    await finalizeButton().trigger('click')
    await flushPromises()

    expect(mocks.finalize.mutateAsync).toHaveBeenCalledWith({
      coverLetterId: COVER_LETTER_ID,
      request: {
        version: 3,
        acknowledgedWarningVerificationIds: [COVER_LETTER_VERIFICATION_ID],
      },
    })
    expect(toastMessages()).toContainEqual(
      expect.stringContaining('공고의 지원 상태는 공고 화면에서 따로 바꿔 주세요'),
    )
  })

  it('restores a past version from the version history panel', async () => {
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="open-versions"]').trigger('click')
    await flushPromises()
    const panel = wrapper.get('.version-panel')
    expect(panel.text()).toContain('그 내용으로 새 답변이 하나 더 저장돼요')

    const oldVersion = panel.findAll('button').find((button) => button.text().includes('v1'))
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
  })

  it('separates the material picker, the job requirements and the review result', async () => {
    const wrapper = await mountPage()
    const assist = wrapper.get('.assist')

    // 기본 tab은 소재 고르기다. 공고 요구사항과 섞이지 않는다.
    expect(assist.text()).toContain('AI 초안')
    expect(assist.text()).toContain('이 답변에 이미 쓴 소재')
    expect(assist.text()).toContain('프로젝트 성과')
    expect(assist.text()).not.toContain('Vue 경험')
    const available = wrapper.findAll('.assist__evidence label')
    expect(available).toHaveLength(1)
    expect(available[0]?.text()).toContain('아직 쓰지 않은 협업 경험')

    await wrapper.get('[data-testid="assist-tab-job"]').trigger('click')
    await flushPromises()
    expect(assist.text()).toContain('Vue 경험')
    expect(assist.text()).toContain('대규모 트래픽 경험을 보강하면 좋아요.')
    expect(wrapper.findAll('.assist__evidence label')).toHaveLength(0)

    await wrapper.get('[data-testid="assist-tab-review"]').trigger('click')
    await flushPromises()
    expect(assist.text()).toContain('원본이 삭제된 근거')
    expect(assist.text()).toContain('새 초안·검토에서는 쓰지 않아요')
    expect(assist.text()).toContain('지금은 사용 안 함')
    expect(assist.text()).toContain('확인 필요')
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

    await selectQuestionTab(wrapper, '두 번째 문항')
    await openQuestionForm(wrapper)
    const questionForm = wrapper.get('.question-meta__form')
    await questionForm.get('textarea').setValue('수정한 두 번째 문항')
    await findButton(wrapper, '문항 저장').trigger('click')
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

    await wrapper.get('[aria-label="2번 문항 앞으로 이동"]').trigger('click')
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
    await findButton(wrapper, '문항 삭제').trigger('click')
    await findButton(wrapper, '삭제 확인').trigger('click')
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

    await openQuestionForm(wrapper)
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

    await openQuestionForm(wrapper)
    await wrapper.get('.question-meta__text textarea').setValue('refetch 뒤 덮어쓴 값')
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

    await openQuestionForm(wrapper)
    await wrapper.get('.question-meta__text textarea').setValue('취소할 내 문항')
    await findButton(wrapper, '문항 저장').trigger('click')
    await flushPromises()
    await openQuestionForm(wrapper)
    await wrapper.get('.question-meta__text textarea').setValue('충돌 뒤 다시 쓴 값')
    await wrapper.get('.cover-conflict button.button--secondary').trigger('click')
    await flushPromises()

    expect(mocks.updateQuestion.mutateAsync).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.cover-conflict').exists()).toBe(false)
    await openQuestionForm(wrapper)
    const questionForm = wrapper.get('.question-meta__form')
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

    await selectQuestionTab(wrapper, '두 번째 로컬 문항')
    await openQuestionForm(wrapper)
    await wrapper.get('[aria-label="2번 문항 앞으로 이동"]').trigger('click')
    await flushPromises()

    const panel = wrapper.get('.cover-conflict')
    expect(panel.text()).toContain('지금 저장된 순서')
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

  it('keeps the saved answer when an AI run fails', async () => {
    mocks.latestRun.data.value = page([{ id: COVER_LETTER_RUN_ID, status: 'RUNNING' }])
    const wrapper = await mountPage()

    wrapper
      .findComponent({ name: 'CoverLetterRunMonitorStub' })
      .vm.$emit('terminal', { status: 'FAILED', partialResult: null })
    await flushPromises()

    expect(toastMessages()).toContainEqual(
      expect.stringContaining('저장한 답변은 그대로 남아 있어요'),
    )
    expect(wrapper.findComponent(EditorStub).props('content')).toEqual(
      answerVersionFixture({ versionNo: 2 }).contentJson,
    )
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

    expect(wrapper.text()).toContain('보관된 자기소개서예요 · 읽기 전용')
    expect(wrapper.find('[data-testid="save-answer-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="open-generation"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="verify-answer-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="open-question-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="finalize-cover-letter"]').exists()).toBe(false)

    await wrapper.get('[data-testid="primary-action"]').trigger('click')
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
      { path: '/documents', name: 'documents', component: { template: '<div />' } },
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
        teleport: true,
        CoverLetterTipTapEditor: EditorStub,
        CoverLetterRunMonitor: {
          name: 'CoverLetterRunMonitorStub',
          template: '<div data-testid="run-monitor" />',
        },
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

function toastMessages(): string[] {
  return useNotifications().state.toasts.map((toast) => toast.message)
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

async function selectQuestionTab(wrapper: VueWrapper, text: string): Promise<void> {
  const tab = wrapper
    .findAll('[role="tab"]')
    .find((button) => button.attributes('aria-label')?.includes(text))
  if (!tab) throw new Error(`Question tab not found: ${text}`)
  await tab.trigger('click')
  await flushPromises()
}

async function openQuestionForm(wrapper: VueWrapper): Promise<void> {
  if (wrapper.find('.question-meta__form').exists()) return
  await wrapper.get('[data-testid="open-question-form"]').trigger('click')
  await flushPromises()
}

function findButton(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper
    .findAll<HTMLButtonElement>('button')
    .find((candidate) => candidate.text() === text)
  if (!button) throw new Error(`Button not found: ${text}`)
  return button
}
