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
  QUEUED: '불러오기 대기',
  EXTRACTING: '불러오는 중',
  EXTRACTED: '불러오기 완료',
  MANUAL_INPUT_PROVIDED: '직접 입력 완료',
  NEEDS_MANUAL_INPUT: '본문 입력 필요',
  FAILED: '불러오지 못함',
}

export const DEADLINE_SOURCE_LABELS: Record<DeadlineSource, string> = {
  USER_ENTERED: '사용자 입력',
  AUTO_EXTRACTED: '공고에서 불러옴',
  UNKNOWN: '출처 미확인',
}

export const DESCRIPTION_SOURCE_LABELS: Record<JobDescriptionSource, string> = {
  USER_ENTERED: '사용자 입력',
  AUTO_EXTRACTED: '공고에서 불러옴',
}

export const CLOSED_REASON_LABELS: Record<ClosedReason, string> = {
  DEADLINE_PASSED: '마감일 경과',
  USER_CLOSED: '사용자 마감',
  URL_INACTIVE: '공고 링크 비활성',
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
    return '공고 내용을 불러오지 못했어요. 본문과 마감일을 직접 입력해 주세요.'
  }
  if (status === 'FAILED') {
    return '공고를 불러오는 중 문제가 생겼어요. 다시 불러오거나 본문을 직접 입력할 수 있어요.'
  }
  if (status === 'MANUAL_INPUT_PROVIDED') {
    return '직접 입력한 공고 본문을 사용하고 있어요.'
  }
  if (status === 'QUEUED' || status === 'EXTRACTING') {
    return '공고 링크에서 본문과 기본 정보를 불러오고 있어요.'
  }
  return '공고 링크에서 불러온 내용을 확인할 수 있어요.'
}
