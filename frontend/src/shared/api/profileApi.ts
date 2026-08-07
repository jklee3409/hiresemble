import type {
  ActivityCreateRequest,
  ActivityDto,
  ActivityUpdateRequest,
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
  EvidenceVerificationBatchRequest,
  EvidenceVerificationStatus,
  ExperienceItemDetailDto,
  ExperienceItemDto,
  ExperienceItemUpdateRequest,
  ExperienceMatchKind,
  ExperienceMatchResolutionRequest,
  ExperienceVerificationRequest,
  LanguageScoreCreateRequest,
  LanguageScoreDto,
  LanguageScoreUpdateRequest,
  PageResponse,
  ProfileDto,
  ProfileEligibilityDto,
  ProfileEligibilityWrite,
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
  documentId?: string
}

export interface ExperienceListParams extends PageParams {
  verificationStatus?: EvidenceVerificationStatus
  matchKind?: Exclude<ExperienceMatchKind, 'SAME_EXPERIENCE'>
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

export function getProfileEligibility(): Promise<ProfileEligibilityDto> {
  return apiClient.get('/profile/eligibility')
}

export function updateProfileEligibility(
  request: ProfileEligibilityWrite,
): Promise<ProfileEligibilityDto> {
  return apiClient.put('/profile/eligibility', request)
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

export function listActivities(params: PageParams = {}): Promise<PageResponse<ActivityDto>> {
  return apiClient.get('/profile/activities', query(params))
}

export function getActivity(id: string): Promise<ActivityDto> {
  return apiClient.get(`/profile/activities/${id}`)
}

export function createActivity(request: ActivityCreateRequest): Promise<ActivityDto> {
  return apiClient.post('/profile/activities', request)
}

export function updateActivity(id: string, request: ActivityUpdateRequest): Promise<ActivityDto> {
  return apiClient.put(`/profile/activities/${id}`, request)
}

export function deleteActivity(id: string, version: number): Promise<void> {
  return apiClient.delete(`/profile/activities/${id}`, query({ version }))
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

export function verifyEvidenceBatch(
  request: EvidenceVerificationBatchRequest,
): Promise<EvidenceDto[]> {
  return apiClient.patch('/profile/evidence/verification', request)
}

export function listExperiences(
  params: ExperienceListParams = {},
): Promise<PageResponse<ExperienceItemDto>> {
  return apiClient.get('/profile/experiences', query(params))
}

export function getExperience(id: string): Promise<ExperienceItemDetailDto> {
  return apiClient.get(`/profile/experiences/${id}`)
}

export function updateExperience(
  id: string,
  request: ExperienceItemUpdateRequest,
): Promise<ExperienceItemDetailDto> {
  return apiClient.put(`/profile/experiences/${id}`, request)
}

export function verifyExperience(
  id: string,
  request: ExperienceVerificationRequest,
): Promise<ExperienceItemDetailDto> {
  return apiClient.patch(`/profile/experiences/${id}/verification`, request)
}

export function resolveExperienceMatch(
  id: string,
  request: ExperienceMatchResolutionRequest,
): Promise<ExperienceItemDetailDto> {
  return apiClient.patch(`/profile/experiences/${id}/match-resolution`, request)
}
