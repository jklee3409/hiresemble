import { describe, expect, it } from 'vitest'

import {
  validateDisplayNameForm,
  validateLoginForm,
  validateSignupCredentialField,
  validateSignupForm,
} from './formValidation'

describe('auth form validation', () => {
  it('requires ten characters and a letter, number, and special character within 72 bytes', () => {
    expect(signup('Abcdef1!x').fieldErrors.password).toBeDefined()
    expect(signup('Abcdefg1!x').fieldErrors.password).toBeUndefined()
    expect(signup(`${'가'.repeat(23)}A1!`).fieldErrors.password).toBeUndefined()
    expect(signup(`${'가'.repeat(23)}A1!a`).fieldErrors.password).toBeDefined()
    expect(signup('abcdefghij').fieldErrors.password).toBeDefined()
    expect(signup('abcdefgh1j').fieldErrors.password).toBeDefined()
    expect(signup('12345678!0').fieldErrors.password).toBeDefined()
    expect(signup('abcdefg1\u0000x').fieldErrors.password).toBeDefined()
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

  it.each([
    'user',
    'user@',
    '@example.com',
    'user@example',
    'user name@example.com',
    'user@example..com',
  ])('rejects an invalid email format on signup and login: %s', (email) => {
    expect(signup('password-123', email).fieldErrors.email).toBe('이메일 형식을 확인해 주세요.')
    expect(validateLoginForm({ email, password: 'password-123' }).fieldErrors.email).toBe(
      '이메일 형식을 확인해 주세요.',
    )
  })

  it('trims and accepts a valid email address on both forms', () => {
    expect(signup('password-123', '  user.name+job@example.co.kr  ').data?.email).toBe(
      'user.name+job@example.co.kr',
    )
    expect(
      validateLoginForm({
        email: '  user.name+job@example.co.kr  ',
        password: 'password-123',
      }).data?.email,
    ).toBe('user.name+job@example.co.kr')
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

  it('shows the actual signup policy in validation messages', () => {
    expect(signup('Abcdef1!x').fieldErrors.password).toBe('비밀번호는 10자 이상 입력해주세요.')
    expect(signup(`${'가'.repeat(23)}A1!a`).fieldErrors.password).toBe(
      '비밀번호가 너무 길어요. 조금 짧게 입력해 주세요.',
    )
    expect(signup('abcdefghij').fieldErrors.password).toBe(
      '비밀번호에 문자, 숫자, 특수문자를 각각 1개 이상 포함해 주세요.',
    )
    expect(validateLoginForm({ email: '', password: 'password' }).fieldErrors.email).toBe(
      '이메일을 입력해 주세요.',
    )
  })

  it('validates signup credentials independently for blur feedback', () => {
    const values = {
      email: 'user@invalid',
      password: 'abcdefghij',
      passwordConfirm: 'different',
      displayName: '',
      termsAgreed: false,
      aiConsent: false,
    }

    expect(validateSignupCredentialField('email', values)).toBe('이메일 형식을 확인해 주세요.')
    expect(validateSignupCredentialField('password', values)).toContain('문자, 숫자, 특수문자')
    expect(validateSignupCredentialField('passwordConfirm', values)).toBe(
      '입력한 비밀번호가 서로 달라요.',
    )
  })

  it('shares the trimmed nickname contract with account updates', () => {
    expect(validateDisplayNameForm({ displayName: '  새 닉네임  ' }).data).toEqual({
      displayName: '새 닉네임',
    })
    expect(validateDisplayNameForm({ displayName: '   ' }).fieldErrors.displayName).toBe(
      '닉네임을 입력해 주세요.',
    )
    expect(validateDisplayNameForm({ displayName: 'a'.repeat(101) }).fieldErrors.displayName).toBe(
      '닉네임은 100자 이하로 입력해 주세요.',
    )
    expect(validateDisplayNameForm({ displayName: '잘못된/닉네임' }).fieldErrors.displayName).toBe(
      '닉네임에 줄바꿈이나 /, \\를 사용할 수 없어요.',
    )
  })
})

function signup(password: string, email = 'user@example.com') {
  return validateSignupForm({
    email,
    password,
    passwordConfirm: password,
    displayName: 'User',
    termsAgreed: true,
    aiConsent: true,
  })
}
