import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as accountApi from './accountApi'
import { ApiClientError } from './errors'
import { apiClient } from './http'

describe('account management API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uses CSRF mutations and sends no Idempotency-Key for deletion', async () => {
    const csrf = vi.spyOn(apiClient, 'ensureCsrf').mockResolvedValue({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-fixture',
    })
    const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue(undefined)
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue({
      deletionRequestId: uuid(1),
      purgeBy: '2026-08-10T00:00:00Z',
    })

    await accountApi.changeAccountPassword({
      currentPassword: 'Old-password1!',
      newPassword: 'New-password2!',
    })
    await accountApi.deleteAccount({
      currentPassword: 'New-password2!',
      permanentDeletionConfirmed: true,
    })

    expect(csrf).toHaveBeenCalledTimes(2)
    expect(patch).toHaveBeenCalledWith('/account/password', {
      currentPassword: 'Old-password1!',
      newPassword: 'New-password2!',
    })
    expect(remove).toHaveBeenCalledWith('/account', {
      data: { currentPassword: 'New-password2!', permanentDeletionConfirmed: true },
    })
    expect(JSON.stringify(remove.mock.calls)).not.toContain('Idempotency-Key')
  })

  it('strictly rejects a malformed deletion receipt', async () => {
    vi.spyOn(apiClient, 'ensureCsrf').mockResolvedValue({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-fixture',
    })
    vi.spyOn(apiClient, 'delete').mockResolvedValue({
      deletionRequestId: uuid(1),
      purgeBy: '2026-08-10T00:00:00Z',
      email: 'must-not-be-returned@example.com',
    })

    await expect(
      accountApi.deleteAccount({
        currentPassword: 'Old-password1!',
        permanentDeletionConfirmed: true,
      }),
    ).rejects.toBeInstanceOf(ApiClientError)
  })
})

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
