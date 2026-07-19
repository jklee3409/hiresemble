import type {
  AwardCreateRequest,
  AwardDto,
  AwardUpdateRequest,
  CareerCreateRequest,
  CareerDto,
  CareerUpdateRequest,
  CertificationCreateRequest,
  CertificationDto,
  CertificationUpdateRequest,
  EducationCreateRequest,
  EducationDto,
  EducationUpdateRequest,
  EvidenceDto,
  EvidenceUpdateRequest,
  EvidenceVerificationRequest,
  EvidenceVerificationStatus,
  LanguageScoreCreateRequest,
  LanguageScoreDto,
  LanguageScoreUpdateRequest,
  PageResponse,
  ProfileDto,
  ProfileWrite,
} from './contracts'
import { apiClient } from './http'

export interface PageParams {
  page?: number
  size?: number
  sort?: string
}

export interface EvidenceListParams extends PageParams {
  verificationStatus?: EvidenceVerificationStatus
  evidenceCategory?: string
}

function query<T extends object>(params: T): { params: T } {
  return { params }
}

export function getProfile(): Promise<ProfileDto> {
  return apiClient.get('/profile')
}

export function updateProfile(request: ProfileWrite): Promise<ProfileDto> {
  return apiClient.put('/profile', request)
}

export function listEducations(params: PageParams = {}): Promise<PageResponse<EducationDto>> {
  return apiClient.get('/profile/educations', query(params))
}

export function createEducation(request: EducationCreateRequest): Promise<EducationDto> {
  return apiClient.post('/profile/educations', request)
}

export function updateEducation(
  id: string,
  request: EducationUpdateRequest,
): Promise<EducationDto> {
  return apiClient.put(`/profile/educations/${id}`, request)
}

export function deleteEducation(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/educations/${id}`, query({ version }))
}

export function listCertifications(
  params: PageParams = {},
): Promise<PageResponse<CertificationDto>> {
  return apiClient.get('/profile/certifications', query(params))
}

export function createCertification(
  request: CertificationCreateRequest,
): Promise<CertificationDto> {
  return apiClient.post('/profile/certifications', request)
}

export function updateCertification(
  id: string,
  request: CertificationUpdateRequest,
): Promise<CertificationDto> {
  return apiClient.put(`/profile/certifications/${id}`, request)
}

export function deleteCertification(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/certifications/${id}`, query({ version }))
}

export function listLanguageScores(
  params: PageParams = {},
): Promise<PageResponse<LanguageScoreDto>> {
  return apiClient.get('/profile/language-scores', query(params))
}

export function createLanguageScore(
  request: LanguageScoreCreateRequest,
): Promise<LanguageScoreDto> {
  return apiClient.post('/profile/language-scores', request)
}

export function updateLanguageScore(
  id: string,
  request: LanguageScoreUpdateRequest,
): Promise<LanguageScoreDto> {
  return apiClient.put(`/profile/language-scores/${id}`, request)
}

export function deleteLanguageScore(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/language-scores/${id}`, query({ version }))
}

export function listAwards(params: PageParams = {}): Promise<PageResponse<AwardDto>> {
  return apiClient.get('/profile/awards', query(params))
}

export function createAward(request: AwardCreateRequest): Promise<AwardDto> {
  return apiClient.post('/profile/awards', request)
}

export function updateAward(id: string, request: AwardUpdateRequest): Promise<AwardDto> {
  return apiClient.put(`/profile/awards/${id}`, request)
}

export function deleteAward(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/awards/${id}`, query({ version }))
}

export function listCareers(params: PageParams = {}): Promise<PageResponse<CareerDto>> {
  return apiClient.get('/profile/careers', query(params))
}

export function createCareer(request: CareerCreateRequest): Promise<CareerDto> {
  return apiClient.post('/profile/careers', request)
}

export function updateCareer(id: string, request: CareerUpdateRequest): Promise<CareerDto> {
  return apiClient.put(`/profile/careers/${id}`, request)
}

export function deleteCareer(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/careers/${id}`, query({ version }))
}

export function listEvidence(params: EvidenceListParams = {}): Promise<PageResponse<EvidenceDto>> {
  return apiClient.get('/profile/evidence', query(params))
}

export function updateEvidence(id: string, request: EvidenceUpdateRequest): Promise<EvidenceDto> {
  return apiClient.put(`/profile/evidence/${id}`, request)
}

export function verifyEvidence(
  id: string,
  request: EvidenceVerificationRequest,
): Promise<EvidenceDto> {
  return apiClient.patch(`/profile/evidence/${id}/verification`, request)
}
