import { describe, expect, it } from 'vitest'

import { validateLoginForm, validateSignupForm } from './formValidation'

describe('auth form validation', () => {
  it('matches the signup UTF-8 password byte boundaries', () => {
    expect(signup('가가가').fieldErrors.password).toBeDefined()
    expect(signup('가가가a').fieldErrors.password).toBeUndefined()
    expect(signup('가'.repeat(24)).fieldErrors.password).toBeUndefined()
    expect(signup(`${'가'.repeat(24)}a`).fieldErrors.password).toBeDefined()
  })

  it('matches the login 1..72 UTF-8 byte contract', () => {
    expect(
      validateLoginForm({ email: 'user@example.com', password: '' }).fieldErrors.password,
    ).toBe('비밀번호를 입력해 주세요.')
    expect(
      validateLoginForm({ email: 'user@example.com', password: '가'.repeat(24) }).fieldErrors
        .password,
    ).toBeUndefined()
    expect(
      validateLoginForm({ email: 'user@example.com', password: `${'가'.repeat(24)}a` }).fieldErrors
        .password,
    ).toBeDefined()
  })

  it('requires matching confirmation and both consent fields without adding them to login', () => {
    const result = validateSignupForm({
      email: 'user@example.com',
      password: 'password-123',
      passwordConfirm: 'different-123',
      displayName: 'User',
      termsAgreed: false,
      aiConsent: false,
    })

    expect(result.fieldErrors).toMatchObject({
      passwordConfirm: '입력한 비밀번호가 서로 달라요.',
      termsAgreed: '필수 이용약관에 동의해 주세요.',
      aiConsent: '필수 AI 처리 안내에 동의해 주세요.',
    })
  })

  it('uses natural visible copy while preserving byte-based boundaries internally', () => {
    expect(signup('가가가').fieldErrors.password).toBe('비밀번호를 조금 더 길게 입력해 주세요.')
    expect(signup(`${'가'.repeat(24)}a`).fieldErrors.password).toBe(
      '비밀번호가 너무 깁니다. 조금 짧게 입력해 주세요.',
    )
    expect(validateLoginForm({ email: '', password: 'password' }).fieldErrors.email).toBe(
      '이메일을 입력해 주세요.',
    )
    expect(signup('가가가').fieldErrors.password).not.toContain('바이트')
  })
})

function signup(password: string) {
  return validateSignupForm({
    email: 'user@example.com',
    password,
    passwordConfirm: password,
    displayName: 'User',
    termsAgreed: true,
    aiConsent: true,
  })
}
