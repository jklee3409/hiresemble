export interface CsrfDto {
  headerName: string
  parameterName: string
  token: string
}

export interface CurrentUserDto {
  id: string
  email: string
  displayName: string
}

export interface AuthSessionDto {
  user: CurrentUserDto
  csrf: CsrfDto
}

export interface SignupRequest {
  email: string
  password: string
  displayName: string
  termsAgreed: boolean
  aiConsent: boolean
}

export interface LoginRequest {
  email: string
  password: string
}

export interface FieldErrorDto {
  field: string
  reason: string
}

export interface ErrorResponseDto {
  timestamp: string
  status: number
  code: string
  message: string
  fieldErrors: FieldErrorDto[]
  requestId: string
}
