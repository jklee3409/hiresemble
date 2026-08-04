import type {
  EvidenceRefDto,
  Eligibility,
  FitCriterionCategory,
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
  EVIDENCE_CHANGED: '확인한 경험이 변경됨',
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

export interface JobAnalysisFailureCopy {
  title: string
  description: string
}

export function jobAnalysisFailureCopy(
  code: string | null | undefined,
  message: string | null | undefined,
  status?: string,
): JobAnalysisFailureCopy {
  const normalizedCode = code?.toUpperCase() ?? ''
  const normalizedMessage = message ?? ''

  if (status === 'CANCELLED') {
    return {
      title: '분석이 취소됐어요.',
      description:
        '사용자 요청으로 진행 중이던 분석을 멈췄어요. 공고와 등록한 지원 정보는 그대로 보존되어 있습니다.',
    }
  }
  if (normalizedCode === 'INSUFFICIENT_JOB_DATA') {
    return {
      title: '공고에서 분석 기준을 충분히 찾지 못했어요.',
      description:
        '주요 업무나 지원 조건이 포함되도록 공고 본문을 보완한 뒤 다시 시도해 주세요. 저장한 공고 정보는 그대로 유지됩니다.',
    }
  }
  if (
    normalizedCode.startsWith('AI_SO_') ||
    normalizedCode.includes('STRUCTURED_OUTPUT') ||
    normalizedCode.includes('OUTPUT_INVALID') ||
    normalizedMessage.includes('의미 제약') ||
    normalizedMessage.includes('결과 형식')
  ) {
    return {
      title: '분석 결과를 안정적으로 정리하지 못했어요.',
      description:
        'AI가 공고 내용을 화면에 보여 줄 수 있는 일관된 결과로 정리하지 못했습니다. 공고와 등록한 지원 정보는 그대로 보존되어 있으니 잠시 후 다시 시도해 주세요.',
    }
  }
  if (normalizedCode.includes('TIMEOUT')) {
    return {
      title: 'AI 응답이 예상보다 오래 걸렸어요.',
      description:
        '분석 요청은 안전하게 종료됐고 저장한 공고 정보는 그대로입니다. 잠시 후 같은 입력으로 다시 시도해 주세요.',
    }
  }
  if (
    normalizedCode.includes('PROVIDER') ||
    normalizedCode.includes('TEMPORARY') ||
    normalizedCode.includes('NETWORK') ||
    normalizedCode.includes('RATE_LIMIT')
  ) {
    return {
      title: 'AI 서비스 연결이 원활하지 않아요.',
      description:
        '현재 일시적인 연결 문제로 분석을 마치지 못했습니다. 공고와 지원 정보는 보존되어 있으니 잠시 후 다시 시도해 주세요.',
    }
  }
  return {
    title: '공고 분석을 완료하지 못했어요.',
    description:
      '진행 중 문제가 발생해 분석을 안전하게 종료했습니다. 저장한 공고와 지원 정보는 그대로이며, 아래 버튼으로 공고 분석을 다시 실행할 수 있어요.',
  }
}

export function formatAnalysisInstant(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR')
}

export function formatFitScore(value: number | null): string {
  return value === null ? '산정하지 못함' : `${value.toFixed(2)}점`
}

export function formatRequirementSourceLocation(value: string | null): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null

  const normalized = trimmed.toLowerCase()
  if (
    normalized.startsWith('$') ||
    normalized.includes('untrustedjobposting') ||
    normalized.includes('descriptiontext')
  ) {
    return '공고 본문'
  }
  return trimmed
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
  return '확인한 경험'
}
