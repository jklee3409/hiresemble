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
    ).toBe('비밀번호는 UTF-8 기준 1~72바이트여야 합니다.')
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
      passwordConfirm: '비밀번호가 일치하지 않습니다.',
      termsAgreed: '이용약관 동의가 필요합니다.',
      aiConsent: 'AI 처리 동의가 필요합니다.',
    })
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
