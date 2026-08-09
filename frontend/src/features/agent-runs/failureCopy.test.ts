import { describe, expect, it } from 'vitest'

import { agentRunFailureCopy } from './presentation'

/*
 * 이 표가 화면과 backend safe error 사이의 유일한 경계다.
 * 여기서 원문이 새면 사용자는 내부 검증 단계 이름을 그대로 읽게 된다.
 */
describe('agentRunFailureCopy', () => {
  it('never reuses the persisted server sentence', () => {
    const serverMessage = 'AI 결과의 의미 제약을 확인하지 못했습니다.'
    const copy = agentRunFailureCopy({ code: 'AI_SO_JAVA_RECORD_INVALID', message: serverMessage })

    expect(copy.title).toBe('AI가 만든 내용을 정리하지 못했어요')
    expect(`${copy.title}${copy.description}`).not.toContain('의미 제약')
    expect(`${copy.title}${copy.description}`).not.toContain('AI_SO_')
  })

  it('classifies a structured output failure by message when the code is missing', () => {
    const copy = agentRunFailureCopy({
      code: '',
      message: 'AI 결과의 의미 제약을 확인하지 못했습니다.',
    })

    expect(copy.title).toBe('AI가 만든 내용을 정리하지 못했어요')
  })

  it.each([
    ['AI_PROVIDER_DISABLED', 'AI 연결이 원활하지 않아요'],
    ['AI_CALL_TIMEOUT', 'AI 응답이 너무 오래 걸렸어요'],
    ['AI_RATE_LIMITED', '지금은 요청이 몰려 있어요'],
    ['INSUFFICIENT_JOB_DATA', '내용이 부족해 마무리하지 못했어요'],
  ])('maps %s to a specific title', (code, title) => {
    expect(agentRunFailureCopy({ code, message: '내부 사유' }).title).toBe(title)
  })

  it('prefers the cancellation copy over the error code', () => {
    const copy = agentRunFailureCopy(
      { code: 'AI_SO_JAVA_RECORD_INVALID', message: 'AI 결과의 의미 제약을 확인하지 못했습니다.' },
      { status: 'CANCELLED' },
    )

    expect(copy.title).toBe('작업을 취소했어요')
  })

  it('drops the retry hint when the run cannot be retried', () => {
    const retryable = agentRunFailureCopy({ code: 'AI_CALL_TIMEOUT' }, { retryable: true })
    const terminal = agentRunFailureCopy({ code: 'AI_CALL_TIMEOUT' }, { retryable: false })

    expect(retryable.description).toContain('다시 시도해 주세요')
    expect(terminal.description).not.toContain('다시 시도해 주세요')
  })

  it('falls back to a safe sentence for an unknown code', () => {
    const copy = agentRunFailureCopy({ code: 'SOMETHING_NEW', message: 'stack trace like text' })

    expect(copy.title).toBe('작업을 마치지 못했어요')
    expect(copy.description).not.toContain('stack trace')
  })

  it('handles a missing error object', () => {
    expect(agentRunFailureCopy(null).title).toBe('작업을 마치지 못했어요')
    expect(agentRunFailureCopy(undefined).description.length).toBeGreaterThan(0)
  })
})
