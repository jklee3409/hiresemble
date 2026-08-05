import type { ApiClientError } from '@/shared/api/errors'

export type CoverLetterConflictKind =
  'TITLE' | 'QUESTION' | 'ORDER' | 'ANSWER' | 'LIFECYCLE' | 'ACTIVE_EXISTS'

export interface CoverLetterConflict {
  kind: CoverLetterConflictKind
  errorCode: string
  serverSnapshot: string
  localDraft: string
}

export function conflictHeading(conflict: CoverLetterConflict): string {
  switch (conflict.kind) {
    case 'TITLE':
      return '제목이 다른 곳에서 변경됐어요.'
    case 'QUESTION':
      return '문항이 다른 곳에서 변경됐어요.'
    case 'ORDER':
      return '문항 순서가 달라졌어요.'
    case 'ANSWER':
      return '저장된 답변이 그 사이에 바뀌었어요.'
    case 'ACTIVE_EXISTS':
      return '이 공고에 작성 중인 자기소개서가 있어요.'
    case 'LIFECYCLE':
      return '자기소개서 상태가 변경됐어요.'
  }
}

export function conflictKindFor(
  operation: Exclude<CoverLetterConflictKind, 'ACTIVE_EXISTS'>,
  error: ApiClientError,
): CoverLetterConflictKind {
  return error.code === 'ACTIVE_COVER_LETTER_EXISTS' ? 'ACTIVE_EXISTS' : operation
}
