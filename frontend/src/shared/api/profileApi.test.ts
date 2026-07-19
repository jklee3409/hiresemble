import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  AwardCreateRequest,
  CareerCreateRequest,
  CertificationCreateRequest,
  EducationCreateRequest,
  LanguageScoreCreateRequest,
} from './contracts'
import { apiClient } from './http'
import * as profileApi from './profileApi'

describe('P2 profile API contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(apiClient, 'get').mockResolvedValue({})
    vi.spyOn(apiClient, 'post').mockResolvedValue({})
    vi.spyOn(apiClient, 'put').mockResolvedValue({})
    vi.spyOn(apiClient, 'patch').mockResolvedValue({})
    vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)
  })

  it('uses the exact basic profile methods and direct DTO body', async () => {
    const request = {
      legalName: 'User',
      introduction: null,
      desiredRoles: ['Backend'],
      desiredIndustries: ['IT'],
      desiredLocations: ['Seoul'],
      expectedGraduationDate: null,
      version: 2,
    }
    await profileApi.getProfile()
    await profileApi.updateProfile(request)
    expect(apiClient.get).toHaveBeenCalledWith('/profile')
    expect(apiClient.put).toHaveBeenCalledWith('/profile', request)
  })

  it.each([
    {
      name: 'education',
      base: '/profile/educations',
      list: profileApi.listEducations,
      request: education(),
      create: () => profileApi.createEducation(education()),
      update: () => profileApi.updateEducation('resource-id', { ...education(), version: 4 }),
      remove: () => profileApi.deleteEducation('resource-id', 4),
    },
    {
      name: 'certification',
      base: '/profile/certifications',
      list: profileApi.listCertifications,
      request: certification(),
      create: () => profileApi.createCertification(certification()),
      update: () =>
        profileApi.updateCertification('resource-id', { ...certification(), version: 4 }),
      remove: () => profileApi.deleteCertification('resource-id', 4),
    },
    {
      name: 'language score',
      base: '/profile/language-scores',
      list: profileApi.listLanguageScores,
      request: language(),
      create: () => profileApi.createLanguageScore(language()),
      update: () => profileApi.updateLanguageScore('resource-id', { ...language(), version: 4 }),
      remove: () => profileApi.deleteLanguageScore('resource-id', 4),
    },
    {
      name: 'award',
      base: '/profile/awards',
      list: profileApi.listAwards,
      request: award(),
      create: () => profileApi.createAward(award()),
      update: () => profileApi.updateAward('resource-id', { ...award(), version: 4 }),
      remove: () => profileApi.deleteAward('resource-id', 4),
    },
    {
      name: 'career',
      base: '/profile/careers',
      list: profileApi.listCareers,
      request: career(),
      create: () => profileApi.createCareer(career()),
      update: () => profileApi.updateCareer('resource-id', { ...career(), version: 4 }),
      remove: () => profileApi.deleteCareer('resource-id', 4),
    },
  ])('maps $name list/create/update/delete exactly', async (entry) => {
    const params = { page: 1, size: 20, sort: 'createdAt,desc' }
    await entry.list(params)
    await entry.create()
    await entry.update()
    await entry.remove()

    expect(apiClient.get).toHaveBeenCalledWith(entry.base, { params })
    expect(apiClient.post).toHaveBeenCalledWith(entry.base, entry.request)
    expect(apiClient.put).toHaveBeenCalledWith(`${entry.base}/resource-id`, {
      ...entry.request,
      version: 4,
    })
    expect(apiClient.delete).toHaveBeenCalledWith(`${entry.base}/resource-id`, {
      params: { version: 4 },
    })
  })

  it('uses only the three approved evidence operations and omits documentId when not supplied', async () => {
    await profileApi.listEvidence({
      verificationStatus: 'VERIFIED',
      evidenceCategory: 'CAREER',
      page: 0,
      size: 20,
      sort: 'updatedAt,desc',
    })
    await profileApi.updateEvidence('evidence-id', {
      title: 'Title',
      content: 'Content',
      metadata: { direct: true },
      version: 1,
    })
    await profileApi.verifyEvidence('evidence-id', { status: 'REJECTED', version: 2 })

    expect(apiClient.get).toHaveBeenCalledWith('/profile/evidence', {
      params: {
        verificationStatus: 'VERIFIED',
        evidenceCategory: 'CAREER',
        page: 0,
        size: 20,
        sort: 'updatedAt,desc',
      },
    })
    expect(apiClient.put).toHaveBeenCalledWith('/profile/evidence/evidence-id', {
      title: 'Title',
      content: 'Content',
      metadata: { direct: true },
      version: 1,
    })
    expect(apiClient.patch).toHaveBeenCalledWith('/profile/evidence/evidence-id/verification', {
      status: 'REJECTED',
      version: 2,
    })
  })
})

function education(): EducationCreateRequest {
  return {
    schoolName: 'School',
    major: null,
    degree: null,
    educationStatus: 'ENROLLED',
    admissionDate: null,
    graduationDate: null,
    gpa: null,
    gpaScale: null,
    isPrimary: true,
    description: null,
  }
}

function certification(): CertificationCreateRequest {
  return {
    name: 'Cert',
    issuer: null,
    credentialNumber: null,
    acquiredDate: null,
    expiresAt: null,
    description: null,
    evidenceDocumentId: null,
  }
}

function language(): LanguageScoreCreateRequest {
  return {
    testName: 'TOEIC',
    score: '900',
    grade: null,
    testedAt: null,
    expiresAt: null,
    evidenceDocumentId: null,
  }
}

function award(): AwardCreateRequest {
  return {
    name: 'Award',
    organizer: null,
    awardedAt: null,
    description: null,
    evidenceDocumentId: null,
  }
}

function career(): CareerCreateRequest {
  return {
    organization: 'Company',
    position: null,
    employmentType: null,
    startedAt: null,
    endedAt: null,
    isCurrent: true,
    responsibilities: null,
    achievements: null,
  }
}
