import {
  accountDeletionAcceptedSchema,
  accountDeletionRequestSchema,
  passwordChangeRequestSchema,
  type AccountDeletionAcceptedDto,
  type AccountDeletionRequest,
  type PasswordChangeRequest,
} from './accountContracts'
import { ApiClientError } from './errors'
import { apiClient } from './http'

export async function changeAccountPassword(request: PasswordChangeRequest): Promise<void> {
  const validated = passwordChangeRequestSchema.parse(request)
  await apiClient.ensureCsrf()
  await apiClient.patch<void>('/account/password', validated)
}

export async function deleteAccount(
  request: AccountDeletionRequest,
): Promise<AccountDeletionAcceptedDto> {
  const validated = accountDeletionRequestSchema.parse(request)
  await apiClient.ensureCsrf()
  const value = await apiClient.delete<unknown>('/account', { data: validated })
  const parsed = accountDeletionAcceptedSchema.safeParse(value)
  if (parsed.success) return parsed.data
  throw new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: '계정 삭제 접수 정보를 확인하지 못했어요.',
  })
}
