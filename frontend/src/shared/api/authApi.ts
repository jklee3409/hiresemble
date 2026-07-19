import type {
  AuthSessionDto,
  CsrfDto,
  CurrentUserDto,
  LoginRequest,
  SignupRequest,
} from './contracts'
import { apiClient } from './http'

export async function initializeCsrf(): Promise<CsrfDto> {
  return apiClient.ensureCsrf()
}

export async function getCurrentUser(): Promise<CurrentUserDto> {
  return apiClient.get<CurrentUserDto>('/auth/me')
}

export async function signup(request: SignupRequest): Promise<AuthSessionDto> {
  await apiClient.ensureCsrf()
  const session = await apiClient.post<AuthSessionDto>('/auth/signup', request)
  apiClient.setCsrfToken(session.csrf)
  return session
}

export async function login(request: LoginRequest): Promise<AuthSessionDto> {
  await apiClient.ensureCsrf()
  const session = await apiClient.post<AuthSessionDto>('/auth/login', request)
  apiClient.setCsrfToken(session.csrf)
  return session
}

export async function logout(): Promise<void> {
  await apiClient.ensureCsrf()
  await apiClient.post<void>('/auth/logout')
}
