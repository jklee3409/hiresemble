import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from './errors'
import * as api from './githubAppConnectionApi'
import { apiClient } from './http'

describe('GitHub App connection API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps capability, start, list, refresh, and confirmed uninstall exactly', async () => {
    const signal = new AbortController().signal
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(capability())
      .mockResolvedValueOnce({ items: [connection()] })
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({
        installationUrl: installationUrl(),
        expiresAt: now,
      })
      .mockResolvedValueOnce(connection({ version: 2 }))
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(
      connection({
        status: 'DISCONNECTING',
        version: 3,
      }),
    )

    await api.getGitHubAppCapability(signal)
    await api.startGitHubAppConnection()
    await api.listGitHubAppConnections(signal)
    await api.refreshGitHubAppConnection(uuid(1), { version: 1 })
    await api.disconnectGitHubAppConnection(uuid(1), 2)

    expect(get).toHaveBeenNthCalledWith(1, '/github-app-connections/capability', { signal })
    expect(get).toHaveBeenNthCalledWith(2, '/github-app-connections', { signal })
    expect(post).toHaveBeenNthCalledWith(1, '/github-app-connections/installation-requests')
    expect(post).toHaveBeenNthCalledWith(2, `/github-app-connections/${uuid(1)}/refresh`, {
      version: 1,
    })
    expect(remove).toHaveBeenCalledWith(`/github-app-connections/${uuid(1)}`, {
      params: { version: 2, uninstallConfirmed: true },
    })
  })

  it('rejects unknown response fields and never exposes provider credentials', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      ...capability(),
      clientSecret: 'must-not-be-accepted',
    })
    await expect(api.getGitHubAppCapability()).rejects.toBeInstanceOf(ApiClientError)
  })
})

const now = '2026-08-09T00:00:00Z'

function capability() {
  return {
    enabled: true,
    configured: true,
    requiredPermissions: ['metadata:read', 'contents:read'],
  }
}

function connection(overrides: Record<string, unknown> = {}) {
  return {
    id: uuid(1),
    targetAccountLogin: 'acme',
    targetAccountType: 'ORGANIZATION',
    repositorySelection: 'SELECTED',
    status: 'ACTIVE',
    manageUrl: 'https://github.com/settings/installations/91',
    version: 1,
    connectedAt: now,
    verifiedAt: now,
    lastCheckedAt: now,
    disconnectedAt: null,
    ...overrides,
  }
}

function installationUrl(): string {
  return `https://github.com/apps/hiresemble-test/installations/new?state=${'a'.repeat(43)}`
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
