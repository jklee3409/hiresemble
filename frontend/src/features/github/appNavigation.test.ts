import { describe, expect, it } from 'vitest'

import { safeGitHubInstallationManageUrl, safeGitHubInstallationUrl } from './appNavigation'

describe('GitHub App external navigation', () => {
  it('allows only fixed GitHub installation and management URL shapes', () => {
    const state = 'a'.repeat(43)
    expect(
      safeGitHubInstallationUrl(
        `https://github.com/apps/hiresemble/installations/new?state=${state}`,
      ),
    ).toContain('https://github.com/apps/hiresemble/installations/new')
    expect(safeGitHubInstallationManageUrl('https://github.com/settings/installations/91')).toBe(
      'https://github.com/settings/installations/91',
    )
  })

  it('rejects host tricks, extra query data, credentials, fragments, and non-https URLs', () => {
    const state = 'a'.repeat(43)
    for (const value of [
      `https://github.com.evil.example/apps/h/installations/new?state=${state}`,
      `https://github.com/apps/h/installations/new?state=${state}&code=secret`,
      `https://github.com/apps/h/installations/new?state=${state}&state=${state}`,
      `https://user@github.com/apps/h/installations/new?state=${state}`,
      `http://github.com/apps/h/installations/new?state=${state}`,
      `https://github.com/apps/h/installations/new?state=${state}#fragment`,
    ]) {
      expect(safeGitHubInstallationUrl(value)).toBeNull()
    }
    expect(
      safeGitHubInstallationManageUrl('https://github.com/settings/installations/91?token=x'),
    ).toBeNull()
  })
})
