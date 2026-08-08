import { describe, expect, it } from 'vitest'

import { safeReturnTo } from './returnTo'

describe('safeReturnTo', () => {
  it.each([
    ['/dashboard', '/dashboard'],
    ['/onboarding?step=welcome#intro', '/onboarding?step=welcome#intro'],
    ['/profile/basic', '/profile/basic'],
    ['/profile/education?page=1', '/profile/education?page=1'],
    ['/profile/experiences?selected=experience-id', '/profile/experiences?selected=experience-id'],
    ['/documents?parseStatus=PARSED', '/documents?parseStatus=PARSED'],
    ['/jobs?status=CLOSED', '/jobs?status=CLOSED'],
    ['/jobs/new', '/jobs/new'],
    ['/cover-letters?status=DRAFT', '/cover-letters?status=DRAFT'],
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

  it('allows the GitHub route only while the Gate 2 build flag is enabled', () => {
    expect(
      safeReturnTo(
        '/profile/github?source=10000000-0000-4000-8000-000000000001',
        'https://hiresemble.example',
      ),
    ).toBeNull()
    expect(
      safeReturnTo(
        '/profile/github?source=10000000-0000-4000-8000-000000000001',
        'https://hiresemble.example',
        { githubSourceEnabled: true },
      ),
    ).toBe('/profile/github?source=10000000-0000-4000-8000-000000000001')
  })

  it('allows only canonical Career Artifact routes while the independent Gate 4 flag is enabled', () => {
    const id = '10000000-0000-4000-8000-000000000001'
    const enabled = { githubSourceEnabled: false, careerArtifactEnabled: true }
    expect(safeReturnTo('/career-artifacts', 'https://hiresemble.example', enabled)).toBe(
      '/career-artifacts',
    )
    expect(
      safeReturnTo(
        '/career-artifacts/new?type=RESUME&step=3',
        'https://hiresemble.example',
        enabled,
      ),
    ).toBe('/career-artifacts/new?type=RESUME&step=3')
    expect(safeReturnTo(`/career-artifacts/${id}`, 'https://hiresemble.example', enabled)).toBe(
      `/career-artifacts/${id}`,
    )
    expect(safeReturnTo('/career-artifacts', 'https://hiresemble.example', {})).toBeNull()
    expect(
      safeReturnTo(
        '/career-artifacts/new?step=3&type=RESUME',
        'https://hiresemble.example',
        enabled,
      ),
    ).toBeNull()
    expect(
      safeReturnTo(
        '/career-artifacts/new?type=RESUME&email=private',
        'https://hiresemble.example',
        enabled,
      ),
    ).toBeNull()
    expect(
      safeReturnTo('/career-artifacts/not-a-uuid', 'https://hiresemble.example', enabled),
    ).toBeNull()
  })

  it('accepts only UUID document detail routes', () => {
    expect(
      safeReturnTo('/documents/10000000-0000-4000-8000-000000000001', 'https://hiresemble.example'),
    ).toBe('/documents/10000000-0000-4000-8000-000000000001')
    expect(safeReturnTo('/documents/not-a-uuid', 'https://hiresemble.example')).toBeNull()
  })

  it('accepts only the implemented Job base, overview, analysis and cover-letter UUID routes', () => {
    const id = '10000000-0000-4000-8000-000000000001'
    expect(safeReturnTo(`/jobs/${id}`, 'https://hiresemble.example')).toBe(`/jobs/${id}`)
    expect(safeReturnTo(`/jobs/${id}/overview?run=${id}`, 'https://hiresemble.example')).toBe(
      `/jobs/${id}/overview?run=${id}`,
    )
    expect(safeReturnTo(`/jobs/${id}/analysis`, 'https://hiresemble.example')).toBe(
      `/jobs/${id}/analysis`,
    )
    expect(safeReturnTo(`/jobs/${id}/cover-letter`, 'https://hiresemble.example')).toBe(
      `/jobs/${id}/cover-letter`,
    )
    expect(safeReturnTo('/jobs/not-a-uuid/overview', 'https://hiresemble.example')).toBeNull()
  })

  it('accepts only UUID cover-letter editor routes', () => {
    const id = '10000000-0000-4000-8000-000000000001'
    expect(safeReturnTo(`/cover-letters/${id}/edit`, 'https://hiresemble.example')).toBe(
      `/cover-letters/${id}/edit`,
    )
    expect(safeReturnTo('/cover-letters/not-a-uuid/edit', 'https://hiresemble.example')).toBeNull()
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
