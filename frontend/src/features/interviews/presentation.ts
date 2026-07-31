import type { AgentRunStatus } from '@/shared/api/agentRunContracts'
import type {
  InterviewQuestionType,
  ResearchQuality,
  ResearchRunStatus,
  ResearchSourceType,
  ResearchTopic,
  SourceCoverage,
} from '@/shared/api/interviewContracts'

export const RESEARCH_QUALITY_LABELS = {
  BASIC: '기본 조사',
  ADVANCED: '심화 조사',
} as const satisfies Record<ResearchQuality, string>

export const SOURCE_COVERAGE_LABELS = {
  SUFFICIENT: '출처 충분',
  LIMITED: '출처 제한적',
  NONE: '확인된 출처 없음',
} as const satisfies Record<SourceCoverage, string>

export const RESEARCH_STATUS_LABELS = {
  QUEUED: '접수됨',
  RUNNING: '조사 중',
  SUCCEEDED: '조사 완료',
  FAILED: '조사 실패',
  CANCELLED: '취소됨',
} as const satisfies Record<ResearchRunStatus, string>

export const RESEARCH_TOPIC_LABELS = {
  COMPANY: '회사 정보',
  INTERVIEW_PROCESS: '채용·면접 과정',
  ROLE_TECHNICAL: '유사 직무 면접',
} as const satisfies Record<ResearchTopic, string>

export const RESEARCH_SOURCE_TYPE_LABELS = {
  OFFICIAL: '공식 출처',
  TECH_BLOG: '기술 블로그',
  NEWS: '뉴스',
  INTERVIEW_REVIEW: '면접 후기',
  COMMUNITY: '커뮤니티',
  OTHER: '기타',
} as const satisfies Record<ResearchSourceType, string>

export const INTERVIEW_QUESTION_TYPE_LABELS = {
  COVER_LETTER: '자기소개서',
  RESUME: '이력서',
  PORTFOLIO: '포트폴리오',
  TECHNICAL: '기술',
  PROJECT_DEEP_DIVE: '프로젝트 심층',
  BEHAVIORAL: '경험·행동',
  COMPANY_MOTIVATION: '회사·지원 동기',
  FOLLOW_UP: '후속 질문',
} as const satisfies Record<InterviewQuestionType, string>

export const AGENT_RUN_STATUS_LABELS = {
  QUEUED: '접수됨',
  RUNNING: '진행 중',
  WAITING_USER: '확인 필요',
  SUCCEEDED: '완료',
  FAILED: '실패',
  CANCELLED: '취소',
  INTERRUPTED: '중단',
} as const satisfies Record<AgentRunStatus, string>

export function coverageTone(coverage: SourceCoverage | null): 'neutral' | 'success' | 'warning' {
  if (coverage === 'SUFFICIENT') return 'success'
  if (coverage === 'LIMITED' || coverage === 'NONE') return 'warning'
  return 'neutral'
}

export function researchStatusTone(
  status: ResearchRunStatus,
): 'neutral' | 'info' | 'success' | 'danger' {
  if (status === 'RUNNING') return 'info'
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'neutral'
}

export function sourceTypeTone(
  sourceType: ResearchSourceType,
): 'neutral' | 'brand' | 'info' | 'warning' {
  if (sourceType === 'OFFICIAL') return 'brand'
  if (sourceType === 'TECH_BLOG' || sourceType === 'NEWS') return 'info'
  if (sourceType === 'INTERVIEW_REVIEW' || sourceType === 'COMMUNITY') return 'warning'
  return 'neutral'
}

export function formatInterviewInstant(value: string | null): string {
  if (value === null) return '정보 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function questionSetJobLabel(job: {
  companyName: string | null
  positionName: string | null
  title: string | null
}): string {
  return [job.companyName, job.positionName ?? job.title].filter(Boolean).join(' · ') || '지원 공고'
}
