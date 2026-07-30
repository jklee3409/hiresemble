import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  answerVersionFixture,
  COVER_LETTER_ANSWER_ID,
  COVER_LETTER_ID,
  COVER_LETTER_JOB_ID,
  COVER_LETTER_QUESTION_ID,
  COVER_LETTER_RUN_ID,
  COVER_LETTER_VERIFICATION_ID,
  coverLetterDetailFixture,
  coverLetterSummaryFixture,
  questionFixture,
  tipTapDocument,
  verificationFixture,
} from '@/features/cover-letters/testFixtures'

import { ApiClientError } from './errors'
import { apiClient } from './http'
import * as coverLetterApi from './coverLetterApi'

describe('P7 cover letter API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uses exact create/generate/verify idempotency headers and typed resource links', async () => {
    const post = vi
      .spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({ status: 201, data: coverLetterDetailFixture() })
      .mockResolvedValueOnce({
        status: 202,
        data: accepted(COVER_LETTER_ID),
      })
      .mockResolvedValueOnce({
        status: 202,
        data: accepted(COVER_LETTER_ID),
      })

    await coverLetterApi.createCoverLetter(
      COVER_LETTER_JOB_ID,
      { title: '지원 자기소개서' },
      'cover-letter-create:key-1',
    )
    await coverLetterApi.generateCoverLetter(
      COVER_LETTER_ID,
      {
        questionIds: [COVER_LETTER_QUESTION_ID],
        preferredEvidenceIds: [],
        qualityMode: 'BALANCED',
        avoidExperienceDuplication: true,
        coverLetterVersion: 3,
      },
      'cover-letter-generate:key-1',
    )
    await coverLetterApi.verifyAnswerVersion(
      COVER_LETTER_ANSWER_ID,
      { qualityMode: 'HIGH_QUALITY' },
      'cover-letter-verify:key-1',
    )

    expect(post).toHaveBeenNthCalledWith(
      1,
      `/jobs/${COVER_LETTER_JOB_ID}/cover-letter`,
      { title: '지원 자기소개서' },
      { headers: { 'Idempotency-Key': 'cover-letter-create:key-1' } },
    )
    expect(post).toHaveBeenNthCalledWith(
      2,
      `/cover-letters/${COVER_LETTER_ID}/generate`,
      expect.objectContaining({ qualityMode: 'BALANCED', coverLetterVersion: 3 }),
      { headers: { 'Idempotency-Key': 'cover-letter-generate:key-1' } },
    )
    expect(post).toHaveBeenNthCalledWith(
      3,
      `/cover-letter-answer-versions/${COVER_LETTER_ANSWER_ID}/verify`,
      { qualityMode: 'HIGH_QUALITY' },
      { headers: { 'Idempotency-Key': 'cover-letter-verify:key-1' } },
    )
  })

  it('maps every synchronous list/detail/question/version/lifecycle operation', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(page([coverLetterSummaryFixture()]))
      .mockResolvedValueOnce(coverLetterDetailFixture())
      .mockResolvedValueOnce(page([answerVersionFixture()]))
      .mockResolvedValueOnce(page([verificationFixture()]))
    const put = vi
      .spyOn(apiClient, 'put')
      .mockResolvedValueOnce(coverLetterDetailFixture({ title: '수정 제목' }))
      .mockResolvedValueOnce(questionFixture({ questionText: '수정 문항' }))
    const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue(coverLetterDetailFixture())
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)
    const rawPost = vi
      .spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({ status: 201, data: questionFixture() })
      .mockResolvedValueOnce({ status: 201, data: answerVersionFixture() })
      .mockResolvedValueOnce({
        status: 201,
        data: answerVersionFixture({
          id: '00000000-0000-4000-8000-000000000200',
          sourceType: 'RESTORED',
          restoredFromVersionId: COVER_LETTER_ANSWER_ID,
        }),
      })
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce(coverLetterDetailFixture({ status: 'FINALIZED' }))
      .mockResolvedValueOnce(
        coverLetterDetailFixture({
          status: 'ARCHIVED',
          canEdit: false,
          canArchive: false,
          archivedAt: '2026-07-30T01:00:00Z',
        }),
      )
      .mockResolvedValueOnce(coverLetterDetailFixture({ status: 'DRAFT' }))

    await coverLetterApi.listCoverLetters({ status: 'DRAFT', sort: 'updatedAt,desc' })
    await coverLetterApi.getCoverLetter(COVER_LETTER_ID)
    await coverLetterApi.updateCoverLetter(COVER_LETTER_ID, {
      title: '수정 제목',
      version: 3,
    })
    await coverLetterApi.createCoverLetterQuestion(COVER_LETTER_ID, {
      questionOrder: 1,
      questionText: '문항',
      maxLength: 1000,
      memo: null,
      coverLetterVersion: 3,
    })
    await coverLetterApi.updateCoverLetterQuestion(COVER_LETTER_ID, COVER_LETTER_QUESTION_ID, {
      questionOrder: 1,
      questionText: '수정 문항',
      maxLength: 1000,
      memo: null,
      version: 2,
    })
    await coverLetterApi.deleteCoverLetterQuestion(COVER_LETTER_ID, COVER_LETTER_QUESTION_ID, 2)
    await coverLetterApi.reorderCoverLetterQuestions(COVER_LETTER_ID, {
      questionIds: [COVER_LETTER_QUESTION_ID],
      version: 3,
    })
    await coverLetterApi.listAnswerVersions(COVER_LETTER_QUESTION_ID)
    await coverLetterApi.saveAnswerVersion(COVER_LETTER_QUESTION_ID, {
      contentJson: tipTapDocument('사용자 수정'),
      parentVersionId: COVER_LETTER_ANSWER_ID,
    })
    await coverLetterApi.restoreAnswerVersion(COVER_LETTER_QUESTION_ID, COVER_LETTER_ANSWER_ID, {
      expectedCurrentVersionId: COVER_LETTER_ANSWER_ID,
    })
    await coverLetterApi.listAnswerVerifications(COVER_LETTER_ANSWER_ID)
    await coverLetterApi.finalizeCoverLetter(COVER_LETTER_ID, {
      version: 3,
      acknowledgedWarningVerificationIds: [COVER_LETTER_VERIFICATION_ID],
    })
    await coverLetterApi.archiveCoverLetter(COVER_LETTER_ID, { version: 4 })
    await coverLetterApi.unarchiveCoverLetter(COVER_LETTER_ID, { version: 5 })

    expect(get).toHaveBeenNthCalledWith(1, '/cover-letters', {
      params: { status: 'DRAFT', sort: 'updatedAt,desc' },
    })
    expect(put).toHaveBeenNthCalledWith(1, `/cover-letters/${COVER_LETTER_ID}`, {
      title: '수정 제목',
      version: 3,
    })
    expect(remove).toHaveBeenCalledWith(
      `/cover-letters/${COVER_LETTER_ID}/questions/${COVER_LETTER_QUESTION_ID}`,
      { params: { version: 2 } },
    )
    expect(patch).toHaveBeenCalledWith(`/cover-letters/${COVER_LETTER_ID}/questions/order`, {
      questionIds: [COVER_LETTER_QUESTION_ID],
      version: 3,
    })
    expect(rawPost).toHaveBeenNthCalledWith(
      2,
      `/cover-letter-questions/${COVER_LETTER_QUESTION_ID}/versions`,
      {
        contentJson: tipTapDocument('사용자 수정'),
        parentVersionId: COVER_LETTER_ANSWER_ID,
      },
    )
    expect(post).toHaveBeenNthCalledWith(1, `/cover-letters/${COVER_LETTER_ID}/finalize`, {
      version: 3,
      acknowledgedWarningVerificationIds: [COVER_LETTER_VERIFICATION_ID],
    })
  })

  it('never accepts client-controlled answer source or createdBy fields', async () => {
    const post = vi.spyOn(apiClient.client, 'post').mockResolvedValue({
      status: 201,
      data: answerVersionFixture(),
    })
    await coverLetterApi.saveAnswerVersion(COVER_LETTER_QUESTION_ID, {
      contentJson: tipTapDocument('명시 저장'),
      parentVersionId: COVER_LETTER_ANSWER_ID,
    })
    const request = post.mock.calls[0]?.[1]
    expect(request).not.toHaveProperty('sourceType')
    expect(request).not.toHaveProperty('createdBy')
  })

  it('rejects incorrect statuses, malformed DTOs and mismatched generation resources', async () => {
    vi.spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({ status: 200, data: coverLetterDetailFixture() })
      .mockResolvedValueOnce({
        status: 202,
        data: accepted('00000000-0000-4000-8000-000000000999'),
      })
    await expect(
      coverLetterApi.createCoverLetter(
        COVER_LETTER_JOB_ID,
        { title: 'x' },
        'cover-letter-create:key-1',
      ),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' } satisfies Partial<ApiClientError>)
    await expect(
      coverLetterApi.generateCoverLetter(
        COVER_LETTER_ID,
        {
          questionIds: [COVER_LETTER_QUESTION_ID],
          preferredEvidenceIds: [],
          qualityMode: 'ECONOMY',
          avoidExperienceDuplication: true,
          coverLetterVersion: 1,
        },
        'cover-letter-generate:key-1',
      ),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' })

    vi.spyOn(apiClient, 'get').mockResolvedValue({ items: [{ id: 'bad' }] })
    await expect(coverLetterApi.listCoverLetters()).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
  })
})

function accepted(resourceId: string) {
  return {
    agentRunId: COVER_LETTER_RUN_ID,
    status: 'QUEUED',
    resourceType: 'COVER_LETTER',
    resourceId,
    replayed: false,
  }
}

function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
  }
}
