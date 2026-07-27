import type {
  ClosedReason,
  DeadlineSource,
  JobDescriptionSource,
  JobExtractionStatus,
  JobStatus,
} from '@/shared/api/jobContracts'

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  IN_PROGRESS: '지원 중',
  SUBMITTED: '서류 제출',
  CLOSED: '마감',
}

export const JOB_EXTRACTION_STATUS_LABELS: Record<JobExtractionStatus, string> = {
  QUEUED: 'URL 추출 대기',
  EXTRACTING: 'URL 추출 중',
  EXTRACTED: 'URL 추출 완료',
  MANUAL_INPUT_PROVIDED: '수동 본문 입력',
  NEEDS_MANUAL_INPUT: '본문 입력 필요',
  FAILED: 'URL 추출 실패',
}

export const DEADLINE_SOURCE_LABELS: Record<DeadlineSource, string> = {
  USER_ENTERED: '사용자 입력',
  AUTO_EXTRACTED: 'URL 자동 추출',
  UNKNOWN: '출처 미확인',
}

export const DESCRIPTION_SOURCE_LABELS: Record<JobDescriptionSource, string> = {
  USER_ENTERED: '사용자 입력',
  AUTO_EXTRACTED: 'URL 자동 추출',
}

export const CLOSED_REASON_LABELS: Record<ClosedReason, string> = {
  DEADLINE_PASSED: '마감일 경과',
  USER_CLOSED: '사용자 마감',
  URL_INACTIVE: '공고 URL 비활성',
}

export function jobDisplayTitle(input: {
  title: string | null
  positionName: string | null
}): string {
  return input.title ?? input.positionName ?? '제목 미입력 공고'
}

export function jobCompanyLabel(companyName: string | null): string {
  return companyName ?? '회사명 미입력'
}

export function formatJobInstant(value: string | null): string {
  return value === null ? '미입력' : new Date(value).toLocaleString('ko-KR')
}

export function jobExtractionGuidance(status: JobExtractionStatus): string {
  if (status === 'NEEDS_MANUAL_INPUT') {
    return 'URL에서 본문을 읽지 못했습니다. 공고 본문과 마감일을 직접 입력해 주세요.'
  }
  if (status === 'FAILED') {
    return '안전한 오류를 확인한 뒤 URL 추출을 재시도하거나 본문을 직접 입력할 수 있습니다.'
  }
  if (status === 'MANUAL_INPUT_PROVIDED') {
    return '사용자가 입력한 본문을 사용하고 있으며 URL 추출 작업은 만들지 않았습니다.'
  }
  if (status === 'QUEUED' || status === 'EXTRACTING') {
    return '공고 URL에서 본문과 기본 정보를 추출하고 있습니다.'
  }
  return '공고 URL에서 추출한 본문을 확인할 수 있습니다.'
}
