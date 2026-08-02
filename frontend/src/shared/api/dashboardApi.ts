import { ApiClientError } from './errors'
import { apiClient } from './http'
import {
  careerGuidePostsSchema,
  dashboardSchema,
  type CareerGuidePostDto,
  type DashboardDto,
} from './dashboardContracts'

export async function getDashboard(month: string): Promise<DashboardDto> {
  const value = await apiClient.get<unknown>('/dashboard', { params: { month } })
  const result = dashboardSchema.safeParse(value)
  if (result.success) return result.data
  throw invalidServerResponse('지원 준비 현황')
}

export async function listCareerGuides(): Promise<CareerGuidePostDto[]> {
  const value = await apiClient.get<unknown>('/career-guides')
  const result = careerGuidePostsSchema.safeParse(value)
  if (result.success) return result.data
  throw invalidServerResponse('취업 준비 가이드')
}

function invalidServerResponse(target: string): ApiClientError {
  return new ApiClientError({
    status: 0,
    code: 'INVALID_SERVER_RESPONSE',
    message: `${target}을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.`,
  })
}
