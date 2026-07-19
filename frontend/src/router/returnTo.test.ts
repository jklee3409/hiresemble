import { describe, expect, it } from 'vitest'

import { safeReturnTo } from './returnTo'

describe('safeReturnTo', () => {
  it.each([
    ['/dashboard', '/dashboard'],
    ['/onboarding?step=welcome#intro', '/onboarding?step=welcome#intro'],
  ])('accepts registered auth-required paths: %s', (candidate, expected) => {
    expect(safeReturnTo(candidate, 'https://hiresemble.example')).toBe(expected)
  })

  it.each([
    'https://evil.example/dashboard',
    '//evil.example/dashboard',
    '/\\evil.example/dashboard',
    '/dashboard\\next',
    '/dashboard\r\nX-Test: injected',
    '/dashboard?value=%0Aunsafe',
    '/login',
    '/signup',
    '/not-found',
    '/profile/basic',
    '%2Fdashboard',
    '/%2e%2e/dashboard',
  ])('rejects unsafe, public, or unregistered input: %s', (candidate) => {
    expect(safeReturnTo(candidate, 'https://hiresemble.example')).toBeNull()
  })
})
