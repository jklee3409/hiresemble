import type {
  AgentRunStatus,
  AiQualityMode,
  ModelTier,
  WorkflowType,
} from '@/shared/api/agentRunContracts'

const AGENT_RUN_DETAIL_PATH =
  /^\/agent-runs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const DOCUMENT_DETAIL_PATH =
  /^\/documents\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const JOB_DETAIL_PATH =
  /^\/jobs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\/(?:overview|analysis)$/i

export const WORKFLOW_LABELS: Record<WorkflowType, string> = {
  DOCUMENT_INGESTION: '이력서·자료 정리',
  JOB_POSTING_EXTRACTION: '공고 불러오기',
  JOB_ANALYSIS: '공고 분석',
  COVER_LETTER_GENERATION: '자기소개서 초안 만들기',
  COVER_LETTER_VERIFICATION: '자기소개서 내용 확인',
  INTERVIEW_PREPARATION: '면접 준비 자료 만들기',
  INTERVIEW_ANSWER_FEEDBACK: '면접 답변 돌아보기',
  MOCK_INTERVIEW_FEEDBACK: '모의 면접 돌아보기',
}

export const STATUS_LABELS: Record<AgentRunStatus, string> = {
  QUEUED: '대기 중',
  RUNNING: '진행 중',
  WAITING_USER: '정보 입력 필요',
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
  LOW_COST: '빠른 처리',
  BALANCED: '균형 처리',
  HIGH_QUALITY: '정밀 처리',
}

export function formatRunProgressLabel(status: AgentRunStatus): string {
  return {
    QUEUED: '시작을 준비하고 있어요',
    RUNNING: '작업을 진행하고 있어요',
    WAITING_USER: '추가 입력을 기다리고 있어요',
    SUCCEEDED: '작업을 마쳤어요',
    FAILED: '작업을 마치지 못했어요',
    CANCELLED: '작업을 취소했어요',
    INTERRUPTED: '작업이 잠시 멈췄어요',
  }[status]
}

export function formatStepName(stepOrder: number): string {
  return `${stepOrder}단계`
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
      ) ||
      AGENT_RUN_DETAIL_PATH.test(target.pathname) ||
      DOCUMENT_DETAIL_PATH.test(target.pathname) ||
      JOB_DETAIL_PATH.test(target.pathname)
    return allowed ? `${target.pathname}${target.search}${target.hash}` : null
  } catch {
    return null
  }
}
