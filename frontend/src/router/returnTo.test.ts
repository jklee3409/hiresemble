import { describe, expect, it } from 'vitest'

import { safeReturnTo } from './returnTo'

describe('safeReturnTo', () => {
  it.each([
    ['/dashboard', '/dashboard'],
    ['/onboarding?step=welcome#intro', '/onboarding?step=welcome#intro'],
    ['/profile/basic', '/profile/basic'],
    ['/profile/education?page=1', '/profile/education?page=1'],
  ])('accepts registered auth-required paths: %s', (candidate, expected) => {
    expect(safeReturnTo(candidate, 'https://hiresemble.example')).toBe(expected)
  })

  it('accepts Agent Run list and UUID detail routes', () => {
    expect(safeReturnTo('/agent-runs', 'https://hiresemble.example')).toBe('/agent-runs')
    expect(
      safeReturnTo(
        '/agent-runs/10000000-0000-4000-8000-000000000001',
        'https://hiresemble.example',
      ),
    ).toBe('/agent-runs/10000000-0000-4000-8000-000000000001')
    expect(safeReturnTo('/agent-runs/not-a-uuid', 'https://hiresemble.example')).toBeNull()
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
    '%2Fdashboard',
    '/%2e%2e/dashboard',
  ])('rejects unsafe, public, or unregistered input: %s', (candidate) => {
    expect(safeReturnTo(candidate, 'https://hiresemble.example')).toBeNull()
  })
})
