import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  ActivityCreateRequest,
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

  it('uses the additive owner-scoped eligibility endpoint', async () => {
    const request = {
      workAvailableDate: '2026-08-01',
      militaryStatus: 'COMPLETED' as const,
      overseasTravelEligibility: 'ELIGIBLE' as const,
      employmentDisqualificationStatus: 'NONE_DECLARED' as const,
      version: 1,
    }
    await profileApi.getProfileEligibility()
    await profileApi.updateProfileEligibility(request)
    expect(apiClient.get).toHaveBeenCalledWith('/profile/eligibility')
    expect(apiClient.put).toHaveBeenCalledWith('/profile/eligibility', request)
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
    {
      name: 'activity',
      base: '/profile/activities',
      list: profileApi.listActivities,
      request: activity(),
      create: () => profileApi.createActivity(activity()),
      update: () => profileApi.updateActivity('resource-id', { ...activity(), version: 4 }),
      remove: () => profileApi.deleteActivity('resource-id', 4),
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

  it('uses the evidence operations and activates documentId filter', async () => {
    await profileApi.listEvidence({
      verificationStatus: 'VERIFIED',
      evidenceCategory: 'CAREER',
      documentId: 'document-id',
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
    await profileApi.verifyEvidenceBatch({
      items: [{ id: 'evidence-id', version: 2 }],
      status: 'VERIFIED',
    })

    expect(apiClient.get).toHaveBeenCalledWith('/profile/evidence', {
      params: {
        verificationStatus: 'VERIFIED',
        evidenceCategory: 'CAREER',
        documentId: 'document-id',
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
    expect(apiClient.patch).toHaveBeenCalledWith('/profile/evidence/verification', {
      items: [{ id: 'evidence-id', version: 2 }],
      status: 'VERIFIED',
    })
  })

  it('uses the canonical experience library operations exactly', async () => {
    const response = experienceDetail()
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({
        items: [response.item],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      })
      .mockResolvedValueOnce(response)
    vi.mocked(apiClient.put).mockResolvedValueOnce(response)
    vi.mocked(apiClient.patch).mockResolvedValueOnce(response).mockResolvedValueOnce(response)

    await profileApi.listExperiences({
      verificationStatus: 'PENDING',
      matchKind: 'CONFLICT',
      page: 1,
      size: 10,
      sort: 'updatedAt,desc',
    })
    await profileApi.getExperience('experience-id')
    await profileApi.updateExperience('experience-id', {
      title: '주문 처리 개선',
      content: '처리 시간을 35% 줄였습니다.',
      version: 3,
    })
    await profileApi.verifyExperience('experience-id', { status: 'VERIFIED', version: 4 })
    await profileApi.resolveExperienceMatch('experience-id', {
      resolution: 'MERGE_WITH_TARGET',
      targetExperienceItemId: 'target-id',
      version: 5,
    })

    expect(apiClient.get).toHaveBeenCalledWith('/profile/experiences', {
      params: {
        verificationStatus: 'PENDING',
        matchKind: 'CONFLICT',
        page: 1,
        size: 10,
        sort: 'updatedAt,desc',
      },
    })
    expect(apiClient.get).toHaveBeenCalledWith('/profile/experiences/experience-id')
    expect(apiClient.put).toHaveBeenCalledWith('/profile/experiences/experience-id', {
      title: '주문 처리 개선',
      content: '처리 시간을 35% 줄였습니다.',
      version: 3,
    })
    expect(apiClient.patch).toHaveBeenCalledWith(
      '/profile/experiences/experience-id/verification',
      { status: 'VERIFIED', version: 4 },
    )
    expect(apiClient.patch).toHaveBeenCalledWith(
      '/profile/experiences/experience-id/match-resolution',
      {
        resolution: 'MERGE_WITH_TARGET',
        targetExperienceItemId: 'target-id',
        version: 5,
      },
    )
  })
})

function experienceDetail() {
  const now = '2026-08-08T00:00:00Z'
  return {
    item: {
      id: '00000000-0000-4000-8000-000000000001',
      evidenceCategory: 'PROJECT',
      title: '주문 처리 개선',
      content: '처리 시간을 35% 줄였습니다.',
      verificationStatus: 'PENDING',
      matchKind: 'NEW',
      matchedExperienceItemId: null,
      matchSimilarity: null,
      reviewRequired: false,
      sourceCount: 1,
      documentSourceCount: 1,
      githubRepositorySourceCount: 0,
      primaryDocumentName: 'resume.pdf',
      version: 3,
      createdAt: now,
      updatedAt: now,
    },
    sources: [
      {
        evidenceId: '00000000-0000-4000-8000-000000000002',
        sourceType: 'DOCUMENT_CHUNK',
        documentId: '00000000-0000-4000-8000-000000000003',
        verificationStatus: 'PENDING',
        relationKind: 'PRIMARY_SOURCE',
        similarity: null,
        githubSourceId: null,
        githubRepositoryId: null,
        repositoryName: null,
        repositoryUrl: null,
        commitShaShort: null,
        capturedAt: null,
        sourceExcerpt: null,
        sourceDeletedAt: null,
        createdAt: now,
      },
    ],
  }
}

function education(): EducationCreateRequest {
  return {
    schoolName: 'School',
    major: null,
    degree: null,
    educationLevel: 'BACHELOR',
    educationStatus: 'ENROLLED',
    admissionDate: null,
    graduationDate: null,
    gpa: null,
    gpaScale: null,
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

function activity(): ActivityCreateRequest {
  return {
    title: 'IT 동아리 운영진',
    activityType: 'CLUB',
    organizer: 'OO대학교',
    startedAt: '2025-03-01',
    endedAt: null,
    ongoing: true,
    role: '운영진',
    description: '정기 세미나를 기획하고 운영했습니다.',
    achievements: '참여율을 높였습니다.',
    relatedUrl: null,
    useAsMaterial: true,
  }
}
