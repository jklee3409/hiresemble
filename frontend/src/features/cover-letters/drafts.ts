import { z } from 'zod'

import { tipTapDocumentSchema, type TipTapDocumentDto } from '@/shared/api/coverLetterContracts'

export const COVER_LETTER_DRAFT_SCHEMA_VERSION = '1'
export const COVER_LETTER_DRAFT_TTL_MS = 24 * 60 * 60 * 1_000
export const COVER_LETTER_DRAFT_RESOURCE_TYPE = 'COVER_LETTER'
const NO_BASE_VERSION = 'none'

export interface CoverLetterDraft {
  contentJson: TipTapDocumentDto
  baseVersionId: string | null
  savedAt: string
}

export interface CoverLetterDraftCandidate extends CoverLetterDraft {
  key: string
  baseMatches: boolean
}

type DraftStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem' | 'key' | 'length'>

const draftSchema = z.object({
  contentJson: tipTapDocumentSchema,
  baseVersionId: z.uuid().nullable(),
  savedAt: z.iso.datetime({ offset: true }),
})

export function coverLetterDraftKey(input: {
  userId: string
  coverLetterId: string
  questionId: string
  baseVersionId: string | null
}): string {
  return [
    COVER_LETTER_DRAFT_SCHEMA_VERSION,
    input.userId,
    COVER_LETTER_DRAFT_RESOURCE_TYPE,
    input.coverLetterId,
    input.questionId,
    input.baseVersionId ?? NO_BASE_VERSION,
  ].join('/')
}

export function saveCoverLetterDraft(
  input: {
    userId: string
    coverLetterId: string
    questionId: string
    baseVersionId: string | null
    contentJson: TipTapDocumentDto
    savedAt?: Date
  },
  storage = browserSessionStorage(),
): string | null {
  if (storage === null) return null
  const key = coverLetterDraftKey(input)
  const value: CoverLetterDraft = {
    contentJson: input.contentJson,
    baseVersionId: input.baseVersionId,
    savedAt: (input.savedAt ?? new Date()).toISOString(),
  }
  storage.setItem(key, JSON.stringify(value))
  return key
}

export function findCoverLetterDraft(
  input: {
    userId: string
    coverLetterId: string
    questionId: string
    currentBaseVersionId: string | null
    now?: Date
  },
  storage = browserSessionStorage(),
): CoverLetterDraftCandidate | null {
  if (storage === null) return null
  const prefix = [
    COVER_LETTER_DRAFT_SCHEMA_VERSION,
    input.userId,
    COVER_LETTER_DRAFT_RESOURCE_TYPE,
    input.coverLetterId,
    input.questionId,
  ].join('/')
  const now = (input.now ?? new Date()).getTime()
  const candidates: CoverLetterDraftCandidate[] = []

  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index)
    if (key === null || !key.startsWith(`${prefix}/`)) continue
    const value = parseDraft(storage.getItem(key))
    if (value === null || now - Date.parse(value.savedAt) > COVER_LETTER_DRAFT_TTL_MS) {
      storage.removeItem(key)
      continue
    }
    candidates.push({
      ...value,
      key,
      baseMatches: value.baseVersionId === input.currentBaseVersionId,
    })
  }

  return (
    candidates.sort((left, right) => Date.parse(right.savedAt) - Date.parse(left.savedAt))[0] ??
    null
  )
}

export function removeCoverLetterQuestionDrafts(
  input: { userId: string; coverLetterId: string; questionId: string },
  storage = browserSessionStorage(),
): void {
  removeMatching(
    [
      COVER_LETTER_DRAFT_SCHEMA_VERSION,
      input.userId,
      COVER_LETTER_DRAFT_RESOURCE_TYPE,
      input.coverLetterId,
      input.questionId,
    ].join('/'),
    storage,
  )
}

export function removeCoverLetterDrafts(
  input: { userId: string; coverLetterId: string },
  storage = browserSessionStorage(),
): void {
  removeMatching(
    [
      COVER_LETTER_DRAFT_SCHEMA_VERSION,
      input.userId,
      COVER_LETTER_DRAFT_RESOURCE_TYPE,
      input.coverLetterId,
    ].join('/'),
    storage,
  )
}

function removeMatching(prefix: string, storage: DraftStorage | null): void {
  if (storage === null) return
  const keys: string[] = []
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index)
    if (key !== null && key.startsWith(`${prefix}/`)) keys.push(key)
  }
  keys.forEach((key) => storage.removeItem(key))
}

function parseDraft(value: string | null): CoverLetterDraft | null {
  if (value === null) return null
  try {
    const result = draftSchema.safeParse(JSON.parse(value) as unknown)
    return result.success ? result.data : null
  } catch {
    return null
  }
}

function browserSessionStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.sessionStorage
}
