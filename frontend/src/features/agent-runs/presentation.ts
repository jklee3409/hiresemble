import type {
  AgentRunStatus,
  AiQualityMode,
  ModelTier,
  WorkflowType,
} from '@/shared/api/agentRunContracts'
import { featureFlags } from '@/app/featureFlags'

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
  GITHUB_INGESTION: 'GitHub 경험 확인',
  RESUME_GENERATION: 'AI 이력서 초안 만들기',
  PORTFOLIO_GENERATION: 'AI 포트폴리오 초안 만들기',
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
  ECONOMY: '빠른 처리',
  BALANCED: '균형 처리',
  HIGH_QUALITY: '꼼꼼한 처리',
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

const STEP_LABELS: Record<string, string> = {
  LOAD_DOCUMENT_SOURCE: '업로드 자료 확인',
  EXTRACT_OR_ACCEPT_TEXT: '문서 내용 확인',
  MASK_TEXT: '개인정보 보호 처리',
  CHUNK_TEXT: '내용 단위 정리',
  EMBED_CHUNKS: '관련 경험을 찾기 위한 준비',
  EXTRACT_EVIDENCE_CANDIDATES: '주요 경험과 소재 정리',
  APPLY_EVIDENCE_CANDIDATES: '검토할 소재 구성',
  FINALIZE_DOCUMENT: '분석 결과 저장',
  FETCH_JOB_PAGE: '공고 페이지 불러오기',
  INSPECT_JOB_PAGE: '공고 내용 확인',
  FETCH_JOB_IMAGES: '공고 이미지 준비',
  EXTRACT_JOB_IMAGE_TEXT: '공고 이미지 읽기',
  COMPOSE_JOB_SOURCE_TEXT: '읽은 내용 정리',
  EXTRACT_JOB_FIELDS: '채용 공고 내용 확인',
  MERGE_USER_OVERRIDES: '입력 내용 반영',
  VALIDATE_JOB_EXTRACTION: '채용 정보 확인',
  APPLY_JOB_EXTRACTION: '결과 저장',
  SANITIZE_PAGE_TEXT: '공고 내용 안전하게 정리',
  BUILD_JOB_SNAPSHOT: '공고 분석 준비',
  EXTRACT_REQUIREMENTS: '지원 요건 정리',
  ASSESS_ELIGIBILITY: '지원 가능 여부 확인',
  RETRIEVE_VERIFIED_EVIDENCE: '관련 경험 찾기',
  MATCH_EVIDENCE: '공고와 경험 비교',
  SCORE_FIT: '직무 적합도 계산',
  VALIDATE_ANALYSIS: '분석 결과 확인',
  PERSIST_ANALYSIS: '결과 저장',
  BUILD_GENERATION_CONTEXT: '초안 작성 정보 준비',
  PLAN_QUESTIONS: '문항별 작성 방향 정하기',
  ANALYZE_QUESTION: '문항 의도 파악',
  RETRIEVE_EVIDENCE: '쓸 경험 찾기',
  ALLOCATE_EXPERIENCES: '문항별 경험 배치',
  DRAFT_ANSWER: '자기소개서 초안 작성',
  REVIEW_ANSWER: '채용 담당자 관점 검토·수정',
  WRITE_ANSWER: '답변 완성과 근거 연결',
  FACT_CHECK_ANSWER: '사실과 근거 확인',
  APPLY_ANSWER_VERSION: '작성한 초안 저장',
  LOAD_ANSWER_VERSION: '검토할 답변 확인',
  BUILD_PROVENANCE_CONTEXT: '근거 자료 준비',
  CHECK_FACTS: '사실과 근거 확인',
  CHECK_REQUIREMENTS_AND_LENGTH: '요구사항과 글자 수 확인',
  AGGREGATE_VERIFICATION: '검토 결과 정리',
  PERSIST_VERIFICATION: '검토 결과 저장',
  VALIDATE_PREREQUISITES: '면접 준비 상태 확인',
  BUILD_PUBLIC_SEARCH_PLAN: '찾아볼 정보 정하기',
  SEARCH_OFFICIAL_SOURCES: '공식 자료 찾기',
  SEARCH_INTERVIEW_SOURCES: '면접 정보 찾기',
  DEDUPE_CLASSIFY_SOURCES: '찾은 자료 정리',
  ASSESS_SOURCE_COVERAGE: '자료가 충분한지 확인',
  BUILD_QUESTION_CONTEXT: '예상 질문 준비',
  GENERATE_QUESTIONS: '예상 질문 만들기',
  VALIDATE_QUESTION_PROVENANCE: '질문의 근거 확인',
  PERSIST_RESEARCH_AND_QUESTION_SET: '면접 준비 결과 저장',
  BUILD_FEEDBACK_CONTEXT: '답변 검토 정보 준비',
  ANALYZE_ANSWER: '면접 답변 분석',
  VALIDATE_FEEDBACK: '피드백 내용 확인',
  PERSIST_FEEDBACK: '피드백 저장',
  LOAD_SESSION_SNAPSHOT: '모의 면접 기록 확인',
  ANALYZE_TURNS: '질문과 답변 분석',
  SYNTHESIZE_SESSION_FEEDBACK: '종합 피드백 만들기',
  VALIDATE_GITHUB_SOURCE: 'GitHub 주소와 공개 상태 확인',
  DISCOVER_REPOSITORIES: '공개 저장소 목록 확인',
  WAIT_FOR_REPOSITORY_SELECTION: '분석할 저장소 선택 기다리기',
  CAPTURE_REPOSITORY_SNAPSHOTS: '선택한 저장소 기록 만들기',
  SANITIZE_AND_SELECT_SOURCE_UNITS: '안전하게 확인할 내용 정리',
  EXTRACT_GITHUB_CANDIDATES: '경험 후보 찾기',
  VALIDATE_GITHUB_CANDIDATES: '경험 후보와 출처 확인',
  EMBED_GITHUB_CANDIDATES: '비슷한 경험 비교 준비',
  APPLY_CANONICAL_EXPERIENCES: '경험 보관함에 반영',
  FINALIZE_GITHUB_SOURCE: 'GitHub 분석 결과 저장',
  LOAD_RESUME_REQUEST: '이력서 생성 요청 확인',
  BUILD_VERIFIED_CAREER_CONTEXT: '승인된 경력 근거 준비',
  PLAN_RESUME: '이력서 구성 계획',
  DRAFT_RESUME_CONTENT: '이력서 내용 작성',
  FACT_CHECK_RESUME_CONTENT: '이력서 근거 확인',
  RENDER_DOCX: 'Word 파일 생성',
  VALIDATE_DOCX: 'Word 파일 안전성 확인',
  PERSIST_RESUME_VERSION: '이력서 버전 저장',
  LOAD_PORTFOLIO_REQUEST: '포트폴리오 생성 요청 확인',
  PLAN_PORTFOLIO_STORY: '포트폴리오 흐름 계획',
  DRAFT_PORTFOLIO_SLIDES: '포트폴리오 슬라이드 작성',
  FACT_CHECK_PORTFOLIO_CONTENT: '포트폴리오 근거 확인',
  RENDER_PPTX: 'PowerPoint 파일 생성',
  VALIDATE_PPTX: 'PowerPoint 파일 안전성 확인',
  PERSIST_PORTFOLIO_VERSION: '포트폴리오 버전 저장',
}

export interface AgentRunFailureCopy {
  title: string
  description: string
}

/*
 * Backend가 내려 주는 safe error는 `AI 결과의 의미 제약을 확인하지 못했습니다.`처럼
 * 내부 검증 단계를 그대로 옮긴 문장이라 사용자가 무엇을 해야 할지 알 수 없다.
 * 화면에는 원문 대신 이 표를 통과한 문구만 보여 주고, 원문은 어디에도 노출하지 않는다.
 */
export function agentRunFailureCopy(
  error: { code?: string | null; message?: string | null } | null | undefined,
  options: { retryable?: boolean; status?: AgentRunStatus } = {},
): AgentRunFailureCopy {
  const code = error?.code?.toUpperCase() ?? ''
  const message = error?.message ?? ''
  const retryHint = options.retryable === false ? '' : ' 잠시 후 다시 시도해 주세요.'

  if (options.status === 'CANCELLED') {
    return {
      title: '작업을 취소했어요',
      description: '요청하신 대로 진행을 멈췄어요. 지금까지 저장한 내용은 그대로 있어요.',
    }
  }
  if (code === 'INSUFFICIENT_JOB_DATA' || code.includes('INSUFFICIENT')) {
    return {
      title: '내용이 부족해 마무리하지 못했어요',
      description: '참고할 내용이 충분하지 않았어요. 자료를 더 채운 뒤 다시 시도해 주세요.',
    }
  }
  if (
    code.startsWith('AI_SO_') ||
    code.includes('STRUCTURED_OUTPUT') ||
    code.includes('OUTPUT_INVALID') ||
    message.includes('의미 제약') ||
    message.includes('결과 형식')
  ) {
    return {
      title: 'AI가 만든 내용을 정리하지 못했어요',
      description: `AI 답변이 화면에 보여 줄 수 있는 형태로 정리되지 않았어요. 저장해 둔 내용은 그대로 있어요.${retryHint}`,
    }
  }
  if (code.includes('TIMEOUT')) {
    return {
      title: 'AI 응답이 너무 오래 걸렸어요',
      description: `작업은 안전하게 멈췄고 저장해 둔 내용은 그대로 있어요.${retryHint}`,
    }
  }
  if (code.includes('RATE_LIMIT') || code.includes('QUOTA') || code.includes('BUDGET')) {
    return {
      title: '지금은 요청이 몰려 있어요',
      description: `사용할 수 있는 양을 넘어서 작업을 멈췄어요. 저장해 둔 내용은 그대로 있어요.${retryHint}`,
    }
  }
  if (
    code.includes('PROVIDER') ||
    code.includes('TEMPORARY') ||
    code.includes('NETWORK') ||
    code.includes('UNAVAILABLE')
  ) {
    return {
      title: 'AI 연결이 원활하지 않아요',
      description: `일시적인 연결 문제로 작업을 마치지 못했어요. 저장해 둔 내용은 그대로 있어요.${retryHint}`,
    }
  }
  return {
    title: '작업을 마치지 못했어요',
    description:
      options.retryable === false
        ? '지금은 이 작업을 진행할 수 없어요. 저장해 둔 내용은 그대로 있어요.'
        : '문제가 생겨 작업을 안전하게 멈췄어요. 저장해 둔 내용은 그대로 있어요. 잠시 후 다시 시도해 주세요.',
  }
}

export function formatStepName(stepKey: string): string {
  return STEP_LABELS[stepKey] ?? '작업 진행 내용'
}

export function formatInstant(value: string | null): string {
  if (value === null) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR')
}

export function usagePercent(used: number, limit: number): number | null {
  if (!Number.isFinite(used) || !Number.isFinite(limit) || limit <= 0) return null
  return Math.min(100, Math.max(0, Math.round((used / limit) * 100)))
}

export function formatUsage(used: number, limit: number): string {
  const percent = usagePercent(used, limit)
  return percent === null ? '집계 정보 없음' : `작업 한도의 ${percent}%`
}

export function formatDuration(value: number | null): string {
  if (value === null) return '—'
  if (value < 1_000) return `${value}ms`
  const seconds = Math.floor(value / 1_000)
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return minutes === 0 ? `${seconds}초` : `${minutes}분 ${remainder}초`
}

export function gitHubSourceResourceRoute(
  resourceType: string | null,
  resourceId: string | null,
  githubSourceEnabled = featureFlags.githubSourceEnabled,
): string | null {
  return githubSourceEnabled && resourceType === 'GITHUB_SOURCE' && resourceId !== null
    ? `/integrations?source=${encodeURIComponent(resourceId)}`
    : null
}

export function careerArtifactResourceRoute(
  resourceType: string | null,
  resourceId: string | null,
  careerArtifactEnabled = featureFlags.careerArtifactEnabled,
): string | null {
  return careerArtifactEnabled && resourceType === 'CAREER_ARTIFACT' && resourceId !== null
    ? `/career-artifacts/${encodeURIComponent(resourceId)}`
    : null
}

export function safeRequiredActionRoute(
  value: string | null,
  githubSourceEnabled = featureFlags.githubSourceEnabled,
): string | null {
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
      [
        '/onboarding',
        '/profile/basic',
        '/profile/activities',
        '/profile/evidence',
        '/agent-runs',
        // backend는 `/profile/github`를 그대로 돌려주고 router가 `/integrations`로 보낸다.
        ...(githubSourceEnabled ? ['/profile/github', '/integrations'] : []),
      ].includes(target.pathname) ||
      AGENT_RUN_DETAIL_PATH.test(target.pathname) ||
      DOCUMENT_DETAIL_PATH.test(target.pathname) ||
      JOB_DETAIL_PATH.test(target.pathname)
    return allowed ? `${target.pathname}${target.search}${target.hash}` : null
  } catch {
    return null
  }
}
