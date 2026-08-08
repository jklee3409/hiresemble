import { describe, expect, it } from 'vitest'

import { ApiClientError } from '@/shared/api/errors'

import {
  GITHUB_STATUS_LABELS,
  gitHubErrorMessage,
  parsePublicGitHubUrl,
  safeGitHubRepositoryUrl,
} from './presentation'

describe('GitHub presentation boundaries', () => {
  it('maps every source lifecycle state to a safe Korean label', () => {
    expect(GITHUB_STATUS_LABELS).toEqual({
      DISCOVERING: '저장소 확인 중',
      WAITING_USER: '저장소 선택 필요',
      QUEUED: '작업 대기',
      RUNNING: '분석 중',
      READY: '완료',
      PARTIAL: '일부만 확인',
      FAILED: '확인 실패',
    })
  })

  it('matches the backend public GitHub URL normalization contract', () => {
    expect(parsePublicGitHubUrl('https://www.github.com/OpenAI')).toEqual({
      canonicalUrl: 'https://github.com/OpenAI',
      kind: 'ACCOUNT',
      ownerLogin: 'OpenAI',
      repositoryName: null,
    })
    expect(parsePublicGitHubUrl('https://github.com/OpenAI/openai-java.git')).toMatchObject({
      canonicalUrl: 'https://github.com/OpenAI/openai-java',
      kind: 'REPOSITORY',
    })
  })

  it.each([
    'http://github.com/openai',
    'https://user@github.com/openai',
    'https://github.com:443/openai',
    'https://github.com/openai?tab=repositories',
    'https://github.com/openai#readme',
    'https://github.com/openai%2Frepo',
    'https://github.com/openai/repo/issues',
    ' https://github.com/openai',
    'https://github.com/openai/',
  ])('rejects an unsafe or non-source URL: %s', (value) => {
    expect(parsePublicGitHubUrl(value)).toBeNull()
  })

  it('renders only canonical public repository links', () => {
    expect(safeGitHubRepositoryUrl('https://github.com/openai/sdk')).toBe(
      'https://github.com/openai/sdk',
    )
    expect(safeGitHubRepositoryUrl('https://github.com/openai')).toBeNull()
    expect(safeGitHubRepositoryUrl('https://evil.example/openai/sdk')).toBeNull()
  })

  it('uses bounded Retry-After metadata without exposing the raw header', () => {
    const error = new ApiClientError({
      status: 429,
      code: 'GITHUB_RATE_LIMITED',
      message: 'raw message',
      retryAfterSeconds: 120,
    })
    const message = gitHubErrorMessage(error, new Date('2026-08-08T01:00:00Z'))
    expect(message).toContain('오전 10:02')
    expect(message).not.toContain('120')
    expect(message).not.toContain('raw message')
  })
})
