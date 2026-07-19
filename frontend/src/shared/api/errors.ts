import axios from 'axios'

import type { ErrorResponseDto, FieldErrorDto } from './contracts'

export class ApiClientError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: readonly FieldErrorDto[]
  readonly requestId: string | null
  readonly serverResponse: ErrorResponseDto | null

  constructor(options: {
    message: string
    status: number
    code: string
    fieldErrors?: readonly FieldErrorDto[]
    requestId?: string | null
    serverResponse?: ErrorResponseDto | null
  }) {
    super(options.message)
    this.name = 'ApiClientError'
    this.status = options.status
    this.code = options.code
    this.fieldErrors = [...(options.fieldErrors ?? [])]
    this.requestId = options.requestId ?? null
    this.serverResponse = options.serverResponse ?? null
  }

  static fromServer(response: ErrorResponseDto): ApiClientError {
    return new ApiClientError({
      message: response.message,
      status: response.status,
      code: response.code,
      fieldErrors: response.fieldErrors,
      requestId: response.requestId,
      serverResponse: response,
    })
  }
}

export function isApiClientError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError
}

export function normalizeApiError(error: unknown): ApiClientError {
  if (isApiClientError(error)) {
    return error
  }

  if (axios.isAxiosError(error) && isErrorResponseDto(error.response?.data)) {
    return ApiClientError.fromServer(error.response.data)
  }

  return new ApiClientError({
    message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
    status: 0,
    code: 'NETWORK_ERROR',
  })
}

export function fieldErrorsToRecord(fieldErrors: readonly FieldErrorDto[]): Record<string, string> {
  const result: Record<string, string> = {}

  for (const fieldError of fieldErrors) {
    if (result[fieldError.field] === undefined) {
      result[fieldError.field] = validationReasonMessage(fieldError.reason)
    }
  }

  return result
}

export function authErrorMessage(error: ApiClientError): string {
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      return '이메일 또는 비밀번호를 확인해 주세요.'
    case 'EMAIL_ALREADY_REGISTERED':
      return '이미 가입된 이메일입니다.'
    case 'CSRF_INVALID':
      return '보안 확인 정보가 만료되었습니다. 다시 시도해 주세요.'
    case 'ACCESS_DENIED':
      return '이 작업을 수행할 권한이 없습니다.'
    case 'AUTHENTICATION_REQUIRED':
      return '로그인이 필요합니다.'
    case 'NETWORK_ERROR':
      return error.message
    default:
      return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }
}

function validationReasonMessage(reason: string): string {
  switch (reason) {
    case 'REQUIRED':
      return '필수 입력입니다.'
    case 'INVALID_LENGTH':
      return '입력 길이를 확인해 주세요.'
    case 'INVALID_FORMAT':
      return '입력 형식을 확인해 주세요.'
    case 'MUST_BE_TRUE':
      return '동의가 필요합니다.'
    default:
      return '입력값을 확인해 주세요.'
  }
}

function isErrorResponseDto(value: unknown): value is ErrorResponseDto {
  if (!isRecord(value)) {
    return false
  }

  return (
    typeof value.timestamp === 'string' &&
    typeof value.status === 'number' &&
    typeof value.code === 'string' &&
    typeof value.message === 'string' &&
    Array.isArray(value.fieldErrors) &&
    value.fieldErrors.every(isFieldErrorDto) &&
    typeof value.requestId === 'string'
  )
}

function isFieldErrorDto(value: unknown): value is FieldErrorDto {
  return isRecord(value) && typeof value.field === 'string' && typeof value.reason === 'string'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
