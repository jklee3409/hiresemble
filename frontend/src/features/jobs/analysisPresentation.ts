import type {
  EvidenceRefDto,
  Eligibility,
  FitCriterionCategory,
  JobAnalysisQualityMode,
  MatchLevel,
  OutdatedReason,
} from '@/shared/api/jobContracts'

export const ELIGIBILITY_LABELS: Record<Eligibility, string> = {
  ELIGIBLE: '지원 가능',
  CONDITIONAL: '조건 확인 후 지원 가능',
  INELIGIBLE: '필수 조건 미충족',
  UNKNOWN: '판단 정보 부족',
}

export const OUTDATED_REASON_LABELS: Record<OutdatedReason, string> = {
  JOB_CONTENT_CHANGED: '공고 내용이 변경됨',
  PROFILE_CHANGED: '프로필 정보가 변경됨',
  EVIDENCE_CHANGED: '승인된 경험 정보가 변경됨',
}

export const FIT_CRITERION_CATEGORY_LABELS: Record<FitCriterionCategory, string> = {
  REQUIRED_QUALIFICATION: '필수 지원 자격',
  CORE_RESPONSIBILITY_OR_SKILL: '핵심 업무·기술',
  PREFERRED_QUALIFICATION: '우대 사항',
  RELATED_EXPERIENCE_OR_DOMAIN: '관련 경험·도메인',
  EDUCATION_CERTIFICATION_LANGUAGE: '학력·자격·어학',
}

export const MATCH_LEVEL_LABELS: Record<MatchLevel, string> = {
  MATCHED: '일치',
  PARTIAL: '일부 일치',
  MISSING: '확인되지 않음',
  UNKNOWN: '판단 정보 부족',
}

export const JOB_ANALYSIS_QUALITY_LABELS: Record<JobAnalysisQualityMode, string> = {
  ECONOMY: '경제형',
  BALANCED: '균형형',
}

export function formatAnalysisInstant(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR')
}

export function formatFitScore(value: number | null): string {
  return value === null ? '산정하지 못함' : `${value.toFixed(2)}점`
}

export function isCurrentlyVerifiedEvidence(evidence: EvidenceRefDto): boolean {
  return evidence.verificationStatus === 'VERIFIED' && !evidence.sourceDeleted
}

export function evidenceCurrentStateLabel(evidence: EvidenceRefDto): string {
  if (evidence.sourceDeleted || evidence.verificationStatus === 'SOURCE_DELETED') {
    return '현재 상태: 원본 삭제됨 · 재분석 근거에서 제외'
  }
  if (evidence.verificationStatus === 'REJECTED') {
    return '현재 상태: 승인 거절됨 · 재분석 근거에서 제외'
  }
  if (evidence.verificationStatus === 'PENDING') {
    return '현재 상태: 검토 대기 · 재분석 근거에서 제외'
  }
  return '승인된 경험 정보'
}
