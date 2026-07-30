import { beforeEach, describe, expect, it } from 'vitest'

import { tipTapDocument } from './testFixtures'
import {
  COVER_LETTER_DRAFT_TTL_MS,
  coverLetterDraftKey,
  findCoverLetterDraft,
  removeCoverLetterDrafts,
  removeCoverLetterQuestionDrafts,
  saveCoverLetterDraft,
} from './drafts'

describe('P7 cover letter sessionStorage drafts', () => {
  beforeEach(() => window.sessionStorage.clear())

  it('uses schema/user/resource/cover/question/base isolation and stores no extra fields', () => {
    const key = saveCoverLetterDraft({
      userId: 'user-1',
      coverLetterId: 'cover-1',
      questionId: 'question-1',
      baseVersionId: null,
      contentJson: tipTapDocument('draft'),
      savedAt: new Date('2026-07-30T00:00:00Z'),
    })
    expect(key).toBe('1/user-1/COVER_LETTER/cover-1/question-1/none')
    expect(
      coverLetterDraftKey({
        userId: 'user-1',
        coverLetterId: 'cover-1',
        questionId: 'question-1',
        baseVersionId: '00000000-0000-4000-8000-000000000001',
      }),
    ).toContain('/00000000-0000-4000-8000-000000000001')
    expect(Object.keys(JSON.parse(window.sessionStorage.getItem(key!)!))).toEqual([
      'contentJson',
      'baseVersionId',
      'savedAt',
    ])
  })

  it('returns same-user recovery candidates and marks a base mismatch without overwriting', () => {
    const base = '00000000-0000-4000-8000-000000000001'
    saveCoverLetterDraft({
      userId: 'user-1',
      coverLetterId: 'cover-1',
      questionId: 'question-1',
      baseVersionId: base,
      contentJson: tipTapDocument('my draft'),
      savedAt: new Date('2026-07-30T00:00:00Z'),
    })
    expect(
      findCoverLetterDraft({
        userId: 'user-1',
        coverLetterId: 'cover-1',
        questionId: 'question-1',
        currentBaseVersionId: base,
        now: new Date('2026-07-30T01:00:00Z'),
      }),
    ).toMatchObject({ baseMatches: true })
    expect(
      findCoverLetterDraft({
        userId: 'user-1',
        coverLetterId: 'cover-1',
        questionId: 'question-1',
        currentBaseVersionId: '00000000-0000-4000-8000-000000000002',
        now: new Date('2026-07-30T01:00:00Z'),
      }),
    ).toMatchObject({ baseMatches: false })
    expect(
      findCoverLetterDraft({
        userId: 'user-2',
        coverLetterId: 'cover-1',
        questionId: 'question-1',
        currentBaseVersionId: base,
      }),
    ).toBeNull()
  })

  it('removes drafts after 24 hours and supports question/archive purge boundaries', () => {
    const savedAt = new Date('2026-07-30T00:00:00Z')
    for (const questionId of ['question-1', 'question-2']) {
      saveCoverLetterDraft({
        userId: 'user-1',
        coverLetterId: 'cover-1',
        questionId,
        baseVersionId: null,
        contentJson: tipTapDocument(questionId),
        savedAt,
      })
    }
    expect(
      findCoverLetterDraft({
        userId: 'user-1',
        coverLetterId: 'cover-1',
        questionId: 'question-1',
        currentBaseVersionId: null,
        now: new Date(savedAt.getTime() + COVER_LETTER_DRAFT_TTL_MS + 1),
      }),
    ).toBeNull()

    removeCoverLetterQuestionDrafts({
      userId: 'user-1',
      coverLetterId: 'cover-1',
      questionId: 'question-2',
    })
    expect(window.sessionStorage.length).toBe(0)

    saveCoverLetterDraft({
      userId: 'user-1',
      coverLetterId: 'cover-1',
      questionId: 'question-3',
      baseVersionId: null,
      contentJson: tipTapDocument('archive'),
    })
    removeCoverLetterDrafts({ userId: 'user-1', coverLetterId: 'cover-1' })
    expect(window.sessionStorage.length).toBe(0)
  })
})
