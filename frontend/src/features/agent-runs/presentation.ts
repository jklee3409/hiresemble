import type {
  AgentRunStatus,
  AiQualityMode,
  ModelTier,
  WorkflowType,
} from '@/shared/api/agentRunContracts'

const AGENT_RUN_DETAIL_PATH =
  /^\/agent-runs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export const WORKFLOW_LABELS: Record<WorkflowType, string> = {
  DOCUMENT_INGESTION: '문서 처리',
  JOB_POSTING_EXTRACTION: '공고 정보 추출',
  JOB_ANALYSIS: '공고 분석',
  COVER_LETTER_GENERATION: '자기소개서 생성',
  COVER_LETTER_VERIFICATION: '자기소개서 검증',
  INTERVIEW_PREPARATION: '면접 준비',
  INTERVIEW_ANSWER_FEEDBACK: '면접 답변 피드백',
  MOCK_INTERVIEW_FEEDBACK: '모의 면접 종합 피드백',
}

export const STATUS_LABELS: Record<AgentRunStatus, string> = {
  QUEUED: '대기 중',
  RUNNING: '실행 중',
  WAITING_USER: '사용자 입력 대기',
  SUCCEEDED: '완료',
  FAILED: '실패',
  CANCELLED: '취소됨',
  INTERRUPTED: '중단됨',
}

export const QUALITY_LABELS: Record<AiQualityMode, string> = {
  ECONOMY: '경제형',
  BALANCED: '균형형',
  HIGH_QUALITY: '고품질',
}

export const MODEL_TIER_LABELS: Record<ModelTier, string> = {
  LOW_COST: '저비용 등급',
  BALANCED: '균형 등급',
  HIGH_QUALITY: '고품질 등급',
}

export function formatInstant(value: string | null): string {
  if (value === null) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR')
}

export function formatCost(value: number): string {
  return `USD ${value.toFixed(6)}`
}

export function formatDuration(value: number | null): string {
  if (value === null) return '—'
  if (value < 1_000) return `${value}ms`
  const seconds = Math.floor(value / 1_000)
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return minutes === 0 ? `${seconds}초` : `${minutes}분 ${remainder}초`
}

export function safeRequiredActionRoute(value: string | null): string | null {
  if (
    value === null ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    Array.from(value).some((character) => {
      const point = character.codePointAt(0)
      return point !== undefined && (point <= 0x1f || point === 0x7f)
    })
  ) {
    return null
  }

  try {
    const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
    const target = new URL(value, origin)
    if (target.origin !== origin) return null
    const allowed =
      ['/onboarding', '/profile/basic', '/profile/evidence', '/agent-runs'].includes(
        target.pathname,
      ) || AGENT_RUN_DETAIL_PATH.test(target.pathname)
    return allowed ? `${target.pathname}${target.search}${target.hash}` : null
  } catch {
    return null
  }
}
