import type { AgentRunStatus } from '@/shared/api/agentRunContracts'
import type {
  CareerArtifactGenerationStatus,
  CareerArtifactLifecycle,
  CareerArtifactProfileSection,
  CareerArtifactType,
  PortfolioSlideType,
  PortfolioVisualType,
} from '@/shared/api/careerArtifactContracts'
import type { ApiClientError } from '@/shared/api/errors'

export const ARTIFACT_TYPE_LABELS: Record<CareerArtifactType, string> = {
  RESUME: '이력서',
  PORTFOLIO: '포트폴리오',
}

export const ARTIFACT_FILE_LABELS: Record<CareerArtifactType, string> = {
  RESUME: 'Word(.docx)',
  PORTFOLIO: 'PowerPoint(.pptx)',
}

export const ARTIFACT_LIFECYCLE_LABELS: Record<CareerArtifactLifecycle, string> = {
  ACTIVE: '사용 중',
  ARCHIVED: '보관됨',
}

export const ARTIFACT_GENERATION_LABELS: Record<CareerArtifactGenerationStatus, string> = {
  NOT_STARTED: '생성 전',
  QUEUED: '생성 대기',
  RUNNING: '생성 중',
  SUCCEEDED: '생성 완료',
  FAILED: '생성 실패',
  CANCELLED: '생성 취소',
  INTERRUPTED: '생성 중단',
}

export const PROFILE_SECTION_LABELS: Record<CareerArtifactProfileSection, string> = {
  PROFILE: '기본 프로필',
  EDUCATIONS: '학력',
  CERTIFICATIONS: '자격증',
  LANGUAGE_SCORES: '어학 성적',
  AWARDS: '수상',
  CAREERS: '경력',
  ACTIVITIES: '대외활동',
}

export const PORTFOLIO_SLIDE_LABELS: Record<PortfolioSlideType, string> = {
  COVER: '표지',
  PROFILE_SUMMARY: '프로필 요약',
  STRENGTH_OVERVIEW: '강점 요약',
  PROJECT_CASE_STUDY: '프로젝트 사례',
  TECHNICAL_DECISION: '기술적 의사결정',
  IMPACT_AND_LEARNING: '성과와 배움',
  CLOSING: '마무리',
}

export const PORTFOLIO_VISUAL_LABELS: Record<PortfolioVisualType, string> = {
  NONE: '시각 요소 없음',
  PROCESS: '진행 과정 구조',
  ARCHITECTURE: '구조도',
  TIMELINE: '시간 흐름',
  IMPACT_METRICS: '성과 지표',
}

export function formatCareerArtifactInstant(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '확인할 수 없음' : date.toLocaleString('ko-KR')
}

export function careerArtifactErrorMessage(error: ApiClientError): string {
  switch (error.code) {
    case 'RESOURCE_VERSION_CONFLICT':
      return '다른 화면에서 상태가 바뀌었어요. 최신 내용을 확인한 뒤 다시 선택해 주세요.'
    case 'AI_MODEL_NOT_SUPPORTED':
      return '선택한 AI 모델을 더 이상 사용할 수 없어요. 모델을 다시 선택해 주세요.'
    case 'INSUFFICIENT_VERIFIED_EXPERIENCE':
      return '사용할 수 있는 확인된 경험이 부족해요. 경험 보관함을 확인해 주세요.'
    case 'CAREER_ARTIFACT_GENERATION_IN_PROGRESS':
      return '이미 파일 생성 작업이 진행 중이에요. 현재 작업이 끝난 뒤 다시 시도해 주세요.'
    case 'CAREER_ARTIFACT_ARCHIVED':
      return '보관한 자료는 새로 생성할 수 없어요. 다시 사용으로 바꾼 뒤 시도해 주세요.'
    case 'INVALID_SERVER_RESPONSE':
      return '생성 자료 정보를 안전하게 확인하지 못했어요. 잠시 후 다시 시도해 주세요.'
    default:
      return error.message
  }
}

export function isAgentRunTerminal(status: AgentRunStatus): boolean {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(status)
}

export function hasCareerArtifactQualityWarning(categories: readonly string[]): boolean {
  const projectOrCareer = categories.filter((category) =>
    ['PROJECT', 'CAREER', '프로젝트', '경력'].includes(category.trim().toUpperCase()),
  ).length
  const strength = categories.some((category) =>
    ['STRENGTH', '역량', '강점'].includes(category.trim().toUpperCase()),
  )
  return projectOrCareer < 2 || !strength
}
