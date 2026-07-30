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
  FINALIZED: '최종화',
  ARCHIVED: '보관됨',
}

export const VERIFICATION_STATUS_LABELS: Record<VerificationStatus, string> = {
  PENDING: '검증 중',
  PASSED: '통과',
  WARNING: '확인 필요',
  FAILED: '검증 실패',
}

export const ANSWER_SOURCE_LABELS: Record<CoverLetterVersionSource, string> = {
  AI_GENERATED: 'AI 초안',
  USER_EDITED: '사용자 저장',
  AI_REVISED: 'AI 수정안',
  RESTORED: '과거 버전 복원',
}

export const ISSUE_CODE_LABELS: Record<VerificationIssueCode, string> = {
  UNVERIFIED_CLAIM: '근거 확인 필요',
  CONTRADICTION: '근거와 불일치',
  REQUIREMENT_MISSING: '문항 요구 누락',
  LENGTH_VIOLATION: '글자 수 초과',
  SOURCE_DELETED: '원본 삭제',
  OTHER: '추가 확인',
}

export const ISSUE_SEVERITY_LABELS: Record<IssueSeverity, string> = {
  WARNING: '경고',
  ERROR: '오류',
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
    return { label: '현재 승인 거절됨', excludedFromNewContext: true }
  }
  if (evidence.verificationStatus === 'PENDING') {
    return { label: '현재 검토 대기', excludedFromNewContext: true }
  }
  return { label: '현재 승인됨', excludedFromNewContext: false }
}

export function formatCoverLetterInstant(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
