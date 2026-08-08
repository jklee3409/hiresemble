import type { ApiClientError } from '@/shared/api/errors'
import type { GitHubSourceStatus } from '@/shared/api/githubSourceContracts'

export const GITHUB_STATUS_LABELS: Record<GitHubSourceStatus, string> = {
  DISCOVERING: '저장소 확인 중',
  WAITING_USER: '저장소 선택 필요',
  QUEUED: '작업 대기',
  RUNNING: '분석 중',
  READY: '완료',
  PARTIAL: '일부만 확인',
  FAILED: '확인 실패',
}

export function gitHubStatusTone(
  status: GitHubSourceStatus,
): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'READY') return 'success'
  if (status === 'PARTIAL' || status === 'WAITING_USER') return 'warning'
  if (status === 'RUNNING' || status === 'DISCOVERING') return 'info'
  if (status === 'FAILED') return 'danger'
  return 'neutral'
}

export interface ParsedPublicGitHubUrl {
  canonicalUrl: string
  kind: 'ACCOUNT' | 'REPOSITORY'
  ownerLogin: string
  repositoryName: string | null
}

const OWNER_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$/
const REPOSITORY_PATTERN = /^[A-Za-z0-9_.-]{1,100}$/

export function parsePublicGitHubUrl(value: string): ParsedPublicGitHubUrl | null {
  if (
    value.length === 0 ||
    value.length > 500 ||
    value.trim() !== value ||
    value.includes('%') ||
    Array.from(value).some((character) => {
      const point = character.codePointAt(0)
      return point !== undefined && (point <= 0x1f || point === 0x7f)
    })
  ) {
    return null
  }

  try {
    const url = new URL(value)
    const authority = value.slice('https://'.length).split('/', 1)[0] ?? ''
    const hostname = url.hostname.toLowerCase()
    if (
      url.protocol !== 'https:' ||
      !['github.com', 'www.github.com'].includes(hostname) ||
      url.username !== '' ||
      url.password !== '' ||
      authority.includes(':') ||
      url.port !== '' ||
      url.search !== '' ||
      url.hash !== '' ||
      url.pathname.length < 2 ||
      url.pathname.endsWith('/') ||
      url.pathname.includes('//')
    ) {
      return null
    }
    const segments = url.pathname.slice(1).split('/')
    if (segments.length < 1 || segments.length > 2 || !OWNER_PATTERN.test(segments[0] ?? '')) {
      return null
    }
    let repositoryName = segments[1] ?? null
    if (repositoryName?.endsWith('.git')) repositoryName = repositoryName.slice(0, -4)
    if (
      repositoryName !== null &&
      (!REPOSITORY_PATTERN.test(repositoryName) || ['.', '..'].includes(repositoryName))
    ) {
      return null
    }
    const ownerLogin = segments[0] ?? ''
    return {
      canonicalUrl: `https://github.com/${ownerLogin}${repositoryName ? `/${repositoryName}` : ''}`,
      kind: repositoryName === null ? 'ACCOUNT' : 'REPOSITORY',
      ownerLogin,
      repositoryName,
    }
  } catch {
    return null
  }
}

export function safeGitHubRepositoryUrl(value: string | null): string | null {
  if (value === null) return null
  const parsed = parsePublicGitHubUrl(value)
  return parsed?.kind === 'REPOSITORY' ? parsed.canonicalUrl : null
}

export function gitHubErrorMessage(error: ApiClientError, now = new Date()): string {
  if (error.code === 'GITHUB_SOURCE_ALREADY_EXISTS') return '이미 등록한 GitHub 주소예요.'
  if (error.code === 'GITHUB_SOURCE_NOT_ACCESSIBLE') {
    return '공개 상태인지 확인해 주세요. 비공개 계정이나 저장소는 연결할 수 없어요.'
  }
  if (error.code === 'GITHUB_SOURCE_LIMIT_EXCEEDED') {
    return '한 번에 확인할 수 있는 GitHub 범위를 넘었어요. 다른 공개 저장소를 선택해 주세요.'
  }
  if (error.code === 'EXTERNAL_SERVICE_UNAVAILABLE') {
    return 'GitHub 연결이 일시적으로 불안정해요. 잠시 후 다시 시도해 주세요.'
  }
  if (error.code === 'GITHUB_RATE_LIMITED') {
    if (error.retryAfterSeconds !== null) {
      const retryAt = new Date(now.getTime() + error.retryAfterSeconds * 1_000)
      return `GitHub 요청 한도에 도달했어요. ${retryAt.toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
      })} 이후 다시 시도해 주세요.`
    }
    return 'GitHub 요청 한도에 도달했어요. 잠시 후 다시 시도해 주세요.'
  }
  if (error.code === 'RESOURCE_VERSION_CONFLICT') {
    return '다른 곳에서 GitHub 연결 상태가 바뀌었어요. 최신 내용을 확인해 주세요.'
  }
  return error.message
}

export function formatGitHubInstant(value: string | null): string {
  if (value === null) return '아직 없음'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '확인할 수 없음' : date.toLocaleString('ko-KR')
}
