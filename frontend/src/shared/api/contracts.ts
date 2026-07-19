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

export type ProfileCompletionItem =
  'LEGAL_NAME' | 'DESIRED_ROLE' | 'DESIRED_INDUSTRY' | 'DESIRED_LOCATION' | 'PRIMARY_EDUCATION'

export type EducationStatus =
  'ENROLLED' | 'LEAVE_OF_ABSENCE' | 'EXPECTED_GRADUATION' | 'GRADUATED' | 'WITHDRAWN'

export type EvidenceSourceType =
  | 'EDUCATION'
  | 'CERTIFICATION'
  | 'LANGUAGE_SCORE'
  | 'AWARD'
  | 'CAREER'
  | 'DOCUMENT_CHUNK'
  | 'MANUAL'

export type EvidenceVerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'SOURCE_DELETED'

export type EvidenceMetadataValue = string | number | boolean | null

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ProfileDto {
  legalName: string | null
  introduction: string | null
  desiredRoles: string[]
  desiredIndustries: string[]
  desiredLocations: string[]
  expectedGraduationDate: string | null
  profileCompleted: boolean
  missingCompletionItems: ProfileCompletionItem[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProfileWrite {
  legalName: string | null
  introduction: string | null
  desiredRoles: string[]
  desiredIndustries: string[]
  desiredLocations: string[]
  expectedGraduationDate: string | null
  version: number
}

interface VersionedProfileResource {
  id: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface EducationDto extends VersionedProfileResource {
  schoolName: string
  major: string | null
  degree: string | null
  educationStatus: EducationStatus
  admissionDate: string | null
  graduationDate: string | null
  gpa: number | null
  gpaScale: number | null
  isPrimary: boolean
  description: string | null
}

export interface EducationCreateRequest {
  schoolName: string
  major: string | null
  degree: string | null
  educationStatus: EducationStatus
  admissionDate: string | null
  graduationDate: string | null
  gpa: number | null
  gpaScale: number | null
  isPrimary: boolean
  description: string | null
}

export interface EducationUpdateRequest extends EducationCreateRequest {
  version: number
}

interface DocumentLinkableRequest {
  evidenceDocumentId: string | null
}

export interface CertificationDto extends VersionedProfileResource, DocumentLinkableRequest {
  name: string
  issuer: string | null
  credentialNumber: string | null
  acquiredDate: string | null
  expiresAt: string | null
  description: string | null
}

export interface CertificationCreateRequest extends DocumentLinkableRequest {
  name: string
  issuer: string | null
  credentialNumber: string | null
  acquiredDate: string | null
  expiresAt: string | null
  description: string | null
}

export interface CertificationUpdateRequest extends CertificationCreateRequest {
  version: number
}

export interface LanguageScoreDto extends VersionedProfileResource, DocumentLinkableRequest {
  testName: string
  score: string
  grade: string | null
  testedAt: string | null
  expiresAt: string | null
}

export interface LanguageScoreCreateRequest extends DocumentLinkableRequest {
  testName: string
  score: string
  grade: string | null
  testedAt: string | null
  expiresAt: string | null
}

export interface LanguageScoreUpdateRequest extends LanguageScoreCreateRequest {
  version: number
}

export interface AwardDto extends VersionedProfileResource, DocumentLinkableRequest {
  name: string
  organizer: string | null
  awardedAt: string | null
  description: string | null
}

export interface AwardCreateRequest extends DocumentLinkableRequest {
  name: string
  organizer: string | null
  awardedAt: string | null
  description: string | null
}

export interface AwardUpdateRequest extends AwardCreateRequest {
  version: number
}

export interface CareerDto extends VersionedProfileResource {
  organization: string
  position: string | null
  employmentType: string | null
  startedAt: string | null
  endedAt: string | null
  isCurrent: boolean
  responsibilities: string | null
  achievements: string | null
}

export interface CareerCreateRequest {
  organization: string
  position: string | null
  employmentType: string | null
  startedAt: string | null
  endedAt: string | null
  isCurrent: boolean
  responsibilities: string | null
  achievements: string | null
}

export interface CareerUpdateRequest extends CareerCreateRequest {
  version: number
}

export interface EvidenceDto extends VersionedProfileResource {
  sourceType: EvidenceSourceType
  sourceEntityId: string | null
  documentId: string | null
  sourceDeletedAt: string | null
  evidenceCategory: string
  title: string
  content: string
  metadata: Record<string, EvidenceMetadataValue>
  confidence: number | null
  verificationStatus: EvidenceVerificationStatus
  verifiedAt: string | null
}

export interface EvidenceUpdateRequest {
  title: string
  content: string
  metadata: Record<string, EvidenceMetadataValue>
  version: number
}

export interface EvidenceVerificationRequest {
  status: Extract<EvidenceVerificationStatus, 'VERIFIED' | 'REJECTED'>
  version: number
}

export type StructuredProfileDto =
  EducationDto | CertificationDto | LanguageScoreDto | AwardDto | CareerDto
