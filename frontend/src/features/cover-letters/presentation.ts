import type {
  CoverLetterStatus,
  CoverLetterVersionSource,
  IssueSeverity,
  VerificationIssueCode,
  VerificationStatus,
} from '@/shared/api/coverLetterContracts'
import type { EvidenceRefDto } from '@/shared/api/jobContracts'

export const COVER_LETTER_STATUS_LABELS: Record<CoverLetterStatus, string> = {
  DRAFT: '작성 중',
  FINALIZED: '작성 완료',
  ARCHIVED: '보관됨',
}

export const VERIFICATION_STATUS_LABELS: Record<VerificationStatus, string> = {
  PENDING: '검토 중',
  PASSED: '문제없음',
  WARNING: '확인 필요',
  FAILED: '수정 필요',
}

export const ANSWER_SOURCE_LABELS: Record<CoverLetterVersionSource, string> = {
  AI_GENERATED: 'AI 초안',
  USER_EDITED: '내가 쓴 글',
  AI_REVISED: 'AI 수정안',
  RESTORED: '되돌린 내용',
}

export const ISSUE_CODE_LABELS: Record<VerificationIssueCode, string> = {
  UNVERIFIED_CLAIM: '근거를 찾지 못한 내용',
  CONTRADICTION: '경험과 다른 내용',
  REQUIREMENT_MISSING: '문항이 요구한 내용 빠짐',
  LENGTH_VIOLATION: '글자 수 초과',
  SOURCE_DELETED: '원본 자료 삭제됨',
  OTHER: '한 번 더 확인',
}

export const ISSUE_SEVERITY_LABELS: Record<IssueSeverity, string> = {
  WARNING: '확인 권장',
  ERROR: '수정 필요',
}

export function coverLetterJobLabel(job: {
  companyName: string | null
  positionName: string | null
  title: string | null
}): string {
  return [job.companyName, job.positionName ?? job.title].filter(Boolean).join(' · ') || '등록 공고'
}

export function evidenceCurrentState(evidence: EvidenceRefDto): {
  label: string
  excludedFromNewContext: boolean
} {
  if (evidence.sourceDeleted || evidence.verificationStatus === 'SOURCE_DELETED') {
    return { label: '원본 삭제됨', excludedFromNewContext: true }
  }
  if (evidence.verificationStatus === 'REJECTED') {
    return { label: '지금은 사용 안 함', excludedFromNewContext: true }
  }
  if (evidence.verificationStatus === 'PENDING') {
    return { label: '아직 확인 전', excludedFromNewContext: true }
  }
  return { label: '지금도 확인된 경험', excludedFromNewContext: false }
}

export function formatCoverLetterInstant(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
