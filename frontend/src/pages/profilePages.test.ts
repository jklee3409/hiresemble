import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import ProfileBasicPage from '@/pages/ProfileBasicPage.vue'
import ProfileActivitiesPage from '@/pages/ProfileActivitiesPage.vue'
import ProfileEvidencePage from '@/pages/ProfileEvidencePage.vue'
import StructuredProfilePage from '@/pages/StructuredProfilePage.vue'
import type {
  EducationDto,
  EvidenceDto,
  PageResponse,
  ProfileDto,
  ProfileEligibilityDto,
} from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import * as documentApi from '@/shared/api/documentApi'
import * as profileApi from '@/shared/api/profileApi'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api/profileApi', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  getProfileEligibility: vi.fn(),
  updateProfileEligibility: vi.fn(),
  listEducations: vi.fn(),
  createEducation: vi.fn(),
  updateEducation: vi.fn(),
  deleteEducation: vi.fn(),
  listCertifications: vi.fn(),
  createCertification: vi.fn(),
  updateCertification: vi.fn(),
  deleteCertification: vi.fn(),
  listLanguageScores: vi.fn(),
  createLanguageScore: vi.fn(),
  updateLanguageScore: vi.fn(),
  deleteLanguageScore: vi.fn(),
  listAwards: vi.fn(),
  createAward: vi.fn(),
  updateAward: vi.fn(),
  deleteAward: vi.fn(),
  listCareers: vi.fn(),
  createCareer: vi.fn(),
  updateCareer: vi.fn(),
  deleteCareer: vi.fn(),
  listActivities: vi.fn(),
  getActivity: vi.fn(),
  createActivity: vi.fn(),
  updateActivity: vi.fn(),
  deleteActivity: vi.fn(),
  listEvidence: vi.fn(),
  updateEvidence: vi.fn(),
  verifyEvidence: vi.fn(),
  verifyEvidenceBatch: vi.fn(),
}))

vi.mock('@/shared/api/documentApi', () => ({
  listDocuments: vi.fn(),
}))

describe('P2 profile pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(documentApi.listDocuments).mockResolvedValue(pageOf([]))
    vi.mocked(profileApi.getProfileEligibility).mockResolvedValue(eligibility())
    vi.mocked(profileApi.updateProfileEligibility).mockResolvedValue(eligibility())
  })

  it('fetches and saves the basic profile, showing server completion and missing items without blocking', async () => {
    const initial = profile()
    const saved = {
      ...initial,
      legalName: 'Updated User',
      profileCompleted: true,
      missingCompletionItems: [],
      version: 2,
    }
    vi.mocked(profileApi.getProfile).mockResolvedValue(initial)
    vi.mocked(profileApi.updateProfile).mockResolvedValue(saved)
    const wrapper = await mountPage(ProfileBasicPage)

    expect(wrapper.text()).toContain('20%')
    expect(wrapper.text()).toContain('필수 항목 4개')
    expect(wrapper.text()).toContain('최종 학력')
    expect(
      wrapper.get('form').element.compareDocumentPosition(wrapper.get('.profile-savebar').element) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    expect(wrapper.get('.profile-savebar button').text()).toBe('변경 사항 저장')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('#profile-legalName').setValue('Updated User')
    expect(wrapper.text()).toContain('저장되지 않은 변경 사항')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(profileApi.updateProfile).toHaveBeenCalledWith({
      legalName: 'Updated User',
      introduction: initial.introduction,
      desiredRoles: initial.desiredRoles,
      desiredIndustries: initial.desiredIndustries,
      desiredLocations: initial.desiredLocations,
      expectedGraduationDate: null,
      version: 1,
    })
    expect(wrapper.text()).toContain('저장 완료')
    expect(wrapper.text()).toContain('100%')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('saves self-reported application eligibility information separately', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(profile())
    vi.mocked(profileApi.getProfileEligibility).mockResolvedValue(eligibility())
    vi.mocked(profileApi.updateProfileEligibility).mockResolvedValue({
      ...eligibility(),
      workAvailableDate: '2026-08-01',
      militaryStatus: 'COMPLETED',
      overseasTravelEligibility: 'ELIGIBLE',
      employmentDisqualificationStatus: 'NONE_DECLARED',
      version: 1,
    })
    const wrapper = await mountPage(ProfileBasicPage)

    expect(wrapper.text()).toContain('지원 자격 확인 정보')
    expect(wrapper.text()).toContain('사용자 입력 기준')
    await wrapper.get('#profile-workAvailableDate').setValue('2026-08-01')
    await wrapper.get('#profile-militaryStatus').setValue('COMPLETED')
    await wrapper.get('#profile-overseasTravelEligibility').setValue('ELIGIBLE')
    await wrapper.get('#profile-employmentDisqualificationStatus').setValue('NONE_DECLARED')
    await wrapper.get('form.profile-eligibility').trigger('submit')
    await flushPromises()

    expect(profileApi.updateProfileEligibility).toHaveBeenCalledWith({
      workAvailableDate: '2026-08-01',
      militaryStatus: 'COMPLETED',
      overseasTravelEligibility: 'ELIGIBLE',
      employmentDisqualificationStatus: 'NONE_DECLARED',
      version: 0,
    })
    expect(wrapper.text()).toContain('지원 자격 확인 정보를 저장했습니다.')
  })

  it('keeps nickname editing out of the basic profile form', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(profile())
    const wrapper = await mountPage(ProfileBasicPage)

    expect(wrapper.find('#profile-displayName').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('헤더와 지원 홈에 표시되는 이름')
  })

  it('registers user-entered activities separately and makes material use explicit', async () => {
    vi.mocked(profileApi.listActivities).mockResolvedValue(pageOf([]))
    vi.mocked(profileApi.createActivity).mockResolvedValue({
      id: '00000000-0000-4000-8000-000000000801',
      title: '교내 IT 동아리 운영진',
      activityType: 'CLUB',
      organizer: 'OO대학교',
      startedAt: '2025-03-01',
      endedAt: null,
      ongoing: true,
      role: null,
      description: '정기 세미나를 기획하고 운영했습니다.',
      achievements: null,
      relatedUrl: null,
      useAsMaterial: true,
      version: 0,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    })
    const wrapper = await mountPage(ProfileActivitiesPage)

    expect(wrapper.text()).toContain('문서 분석 결과와 별도로 관리해요.')
    expect(wrapper.text()).toContain('아직 등록한 대외활동이 없어요.')
    await wrapper.get('button').trigger('click')
    const controls = wrapper.findAll('input, textarea')
    await controls
      .find((control) => control.attributes('placeholder')?.includes('IT 동아리'))
      ?.setValue('교내 IT 동아리 운영진')
    await wrapper.get('form select').setValue('CLUB')
    await controls
      .find((control) => control.attributes('placeholder')?.includes('총학생회'))
      ?.setValue('OO대학교')
    await controls
      .find((control) => control.attributes('placeholder')?.includes('무엇을 목표'))
      ?.setValue('정기 세미나를 기획하고 운영했습니다.')
    const materialToggle = wrapper.findAll('input[type="checkbox"]').at(-1)
    await materialToggle?.setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(profileApi.createActivity).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '교내 IT 동아리 운영진',
        organizer: 'OO대학교',
        useAsMaterial: true,
      }),
    )
  })

  it('moves to education only after the basic profile save succeeds', async () => {
    const initial = profile()
    vi.mocked(profileApi.getProfile).mockResolvedValue(initial)
    vi.mocked(profileApi.updateProfile).mockResolvedValue({ ...initial, version: 2 })
    const wrapper = await mountPage(ProfileBasicPage)

    await wrapper.get('#profile-introduction').setValue('학력으로 이동하기 전 저장할 소개')
    const continueButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('저장 후 다음: 학력'))
    await continueButton?.trigger('click')
    await flushPromises()

    expect(profileApi.updateProfile).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.$router.currentRoute.value.path).toBe('/profile/education')
  })

  it('maps server field errors and offers latest-vs-draft field reapplication on 409', async () => {
    const initial = profile()
    const latest = { ...initial, introduction: 'Server latest', version: 2 }
    vi.mocked(profileApi.getProfile).mockResolvedValueOnce(initial).mockResolvedValueOnce(latest)
    vi.mocked(profileApi.updateProfile).mockRejectedValueOnce(
      ApiClientError.fromServer({
        timestamp: '2026-07-19T00:00:00Z',
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: '최신 내용을 확인한 뒤 다시 적용해 주세요.',
        fieldErrors: [{ field: 'version', reason: 'STALE' }],
        requestId: '00000000-0000-0000-0000-000000000010',
      }),
    )
    const wrapper = await mountPage(ProfileBasicPage)
    await wrapper.get('#profile-introduction').setValue('My unsaved text')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('다른 곳에서 최신 내용이 저장됐어요')
    expect(wrapper.text()).toContain('My unsaved text')
    expect(wrapper.text()).toContain('Server latest')
    await wrapper.get('button[type="button"]').trigger('click')
    expect(wrapper.get<HTMLInputElement>('#profile-introduction').element.value).toBe(
      'My unsaved text',
    )
  })

  it('connects backend fieldErrors to the matching basic profile field', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue(profile())
    vi.mocked(profileApi.updateProfile).mockRejectedValueOnce(
      ApiClientError.fromServer({
        timestamp: '2026-07-19T00:00:00Z',
        status: 400,
        code: 'VALIDATION_ERROR',
        message: '입력값을 확인해 주세요.',
        fieldErrors: [{ field: 'legalName', reason: 'INVALID_LENGTH' }],
        requestId: '00000000-0000-0000-0000-000000000011',
      }),
    )
    const wrapper = await mountPage(ProfileBasicPage)
    await wrapper.get('#profile-legalName').setValue('Changed name')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('입력 길이를 확인해 주세요.')
    expect(wrapper.get('#profile-legalName').attributes('aria-invalid')).toBe('true')
  })

  it('supports education list/create/update/delete and shows the server-derived final education', async () => {
    const item = education()
    vi.mocked(profileApi.listEducations).mockResolvedValue(pageOf([item]))
    vi.mocked(profileApi.createEducation).mockResolvedValue({ ...item, id: 'new-id' })
    vi.mocked(profileApi.updateEducation).mockResolvedValue({
      ...item,
      isPrimary: true,
      version: 2,
    })
    vi.mocked(profileApi.deleteEducation).mockResolvedValue()
    const wrapper = await mountPage(StructuredProfilePage, { kind: 'education' })

    expect(wrapper.text()).toContain('School')
    expect(wrapper.text()).toContain('졸업 예정')
    expect(wrapper.text()).not.toContain('EXPECTED_GRADUATION')
    expect(wrapper.text()).not.toContain('대표로 설정')
    await wrapper.get('button').trigger('click')
    await wrapper.get('#education-schoolName').setValue('New School')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.createEducation).toHaveBeenCalledWith(
      expect.objectContaining({ schoolName: 'New School', educationLevel: 'BACHELOR' }),
    )

    const editButton = wrapper.findAll('button').find((button) => button.text() === '수정')
    await editButton?.trigger('click')
    await wrapper.get('#education-schoolName').setValue('Edited School')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.updateEducation).toHaveBeenCalledWith(
      item.id,
      expect.objectContaining({ schoolName: 'Edited School', version: item.version }),
    )

    const deleteButton = wrapper.findAll('button').find((button) => button.text() === '삭제')
    const deletion = deleteButton?.trigger('click')
    useNotifications().resolveConfirmation(true)
    await deletion
    await flushPromises()
    expect(profileApi.deleteEducation).toHaveBeenCalledWith(item.id, item.version)
  })

  it('links certification, language, and award forms to an active owner document', async () => {
    const documentId = '00000000-0000-4000-8000-000000000101'
    vi.mocked(documentApi.listDocuments).mockResolvedValue(
      pageOf([
        {
          id: documentId,
          documentType: 'CERTIFICATE',
          originalFilename: 'certificate.pdf',
          displayName: '자격 증빙.pdf',
          mimeType: 'application/pdf',
          fileSizeBytes: 100,
          parseStatus: 'PARSED',
          evidenceExtractionStatus: 'SUCCEEDED',
          manualTextProvided: false,
          safeError: null,
          latestAgentRunId: null,
          version: 1,
          uploadedAt: '2026-07-19T00:00:00Z',
          updatedAt: '2026-07-19T00:00:00Z',
        },
      ]),
    )
    vi.mocked(profileApi.listCertifications).mockResolvedValue(pageOf([]))
    vi.mocked(profileApi.createCertification).mockResolvedValue({
      id: 'certification-id',
      name: '정보처리기사',
      issuer: null,
      credentialNumber: null,
      acquiredDate: null,
      expiresAt: null,
      description: null,
      evidenceDocumentId: documentId,
      version: 0,
      createdAt: '2026-07-19T00:00:00Z',
      updatedAt: '2026-07-19T00:00:00Z',
    })
    const wrapper = await mountPage(StructuredProfilePage, { kind: 'certification' })
    await wrapper.get('button').trigger('click')
    await wrapper.get('#certification-name').setValue('정보처리기사')
    await wrapper.get('#certification-evidenceDocumentId').setValue(documentId)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.createCertification).toHaveBeenCalledWith(
      expect.objectContaining({ name: '정보처리기사', evidenceDocumentId: documentId }),
    )
  })

  it('filters by document, edits, verifies, and renders SOURCE_DELETED evidence read-only', async () => {
    const active = evidence()
    const direct = {
      ...evidence(),
      id: 'evidence-direct',
      sourceType: 'CAREER' as const,
      sourceEntityId: '00000000-0000-4000-8000-000000000103',
      documentId: null,
      confidence: null,
      verificationStatus: 'VERIFIED' as const,
      verifiedAt: '2026-07-19T00:00:00Z',
    }
    const deleted = {
      ...evidence(),
      id: 'evidence-deleted',
      sourceEntityId: null,
      documentId: null,
      confidence: null,
      verificationStatus: 'SOURCE_DELETED' as const,
      sourceDeletedAt: '2026-07-19T00:00:00Z',
    }
    const educationEvidence = {
      ...evidence(),
      id: 'evidence-education',
      sourceType: 'EDUCATION' as const,
      sourceEntityId: null,
      documentId: null,
      evidenceCategory: 'EDUCATION',
      verificationStatus: 'SOURCE_DELETED' as const,
      sourceDeletedAt: '2026-07-19T00:00:00Z',
    }
    vi.mocked(profileApi.listEvidence).mockResolvedValue(
      pageOf([active, direct, deleted, educationEvidence]),
    )
    vi.mocked(profileApi.updateEvidence).mockResolvedValue({
      ...active,
      title: 'Edited',
      version: 2,
    })
    vi.mocked(profileApi.verifyEvidence).mockResolvedValue({
      ...active,
      verificationStatus: 'REJECTED',
      version: 2,
    })
    vi.mocked(documentApi.listDocuments).mockResolvedValue(
      pageOf([
        {
          id: '00000000-0000-4000-8000-000000000101',
          documentType: 'RESUME',
          originalFilename: 'resume.txt',
          displayName: '이력서.txt',
          mimeType: 'text/plain',
          fileSizeBytes: 100,
          parseStatus: 'PARSED',
          evidenceExtractionStatus: 'SUCCEEDED',
          manualTextProvided: false,
          safeError: null,
          latestAgentRunId: null,
          version: 1,
          uploadedAt: '2026-07-19T00:00:00Z',
          updatedAt: '2026-07-19T00:00:00Z',
        },
      ]),
    )
    const wrapper = await mountPage(ProfileEvidencePage)

    expect(wrapper.get('.evidence-page__guidance').text()).toContain(
      '승인공고 분석과 자기소개서 작성에 사용해요.',
    )
    expect(wrapper.get('.evidence-page__guidance').text()).toContain(
      '거절AI 기능에서 해당 정보를 사용하지 않아요.',
    )
    expect(wrapper.get('.evidence-page__guidance').text()).not.toContain('신뢰도')
    expect(wrapper.find('[data-testid="evidence-card-evidence-education"]').exists()).toBe(false)
    await wrapper.get('#evidence-document-filter').setValue('00000000-0000-4000-8000-000000000101')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.listEvidence).toHaveBeenLastCalledWith(
      expect.objectContaining({ documentId: '00000000-0000-4000-8000-000000000101' }),
    )
    expect(wrapper.text()).toContain('원본이 삭제되어 읽기 전용이에요.')
    const deletedCard = wrapper.get('[data-testid="evidence-card-evidence-deleted"]')
    expect(deletedCard.text()).toContain('원본 삭제됨')
    expect(deletedCard.text()).toContain('수정·승인·거절할 수 없어요.')
    expect(
      deletedCard
        .findAll('button')
        .filter((button) => ['수정', '승인', '거절'].includes(button.text())),
    ).toHaveLength(0)
    const directCard = wrapper.get('[data-testid="evidence-card-evidence-direct"]')
    expect(directCard.text()).toContain('직접 입력')
    expect(
      directCard.findAll('button').filter((button) => ['승인', '거절'].includes(button.text())),
    ).toHaveLength(0)

    await wrapper.get('.evidence-filters select').setValue('REJECTED')
    await flushPromises()
    expect(profileApi.listEvidence).toHaveBeenLastCalledWith(
      expect.objectContaining({ verificationStatus: 'REJECTED' }),
    )

    const editButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '수정' && button.attributes('disabled') === undefined)
    await editButton?.trigger('click')
    await wrapper.get('#evidence-title').setValue('Edited')
    await wrapper.get('form[novalidate]').trigger('submit')
    await flushPromises()
    expect(profileApi.updateEvidence).toHaveBeenCalledWith(
      active.id,
      expect.objectContaining({ title: 'Edited', version: active.version }),
    )

    const rejectButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '거절' && button.attributes('disabled') === undefined)
    await rejectButton?.trigger('click')
    await flushPromises()
    expect(profileApi.verifyEvidence).toHaveBeenCalledWith(active.id, {
      status: 'REJECTED',
      version: active.version,
    })
  })
})

async function mountPage(
  component: Parameters<typeof mount>[0],
  props: Record<string, unknown> = {},
): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.status = 'authenticated'
  authStore.currentUser = {
    id: 'user-1',
    email: 'user-1@example.com',
    displayName: 'User One',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/dashboard', component: { template: '<div />' } },
      {
        path: '/profile/education',
        name: 'profile-education',
        component: { template: '<div />' },
      },
      { path: '/profile/:pathMatch(.*)*', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(component, {
    props,
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function profile(): ProfileDto {
  return {
    legalName: 'User',
    introduction: 'Intro',
    desiredRoles: ['Backend'],
    desiredIndustries: [],
    desiredLocations: [],
    expectedGraduationDate: null,
    profileCompleted: false,
    missingCompletionItems: [
      'DESIRED_INDUSTRY',
      'DESIRED_LOCATION',
      'PRIMARY_EDUCATION',
      'LEGAL_NAME',
    ],
    version: 1,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function eligibility(): ProfileEligibilityDto {
  return {
    id: 'eligibility-id',
    workAvailableDate: null,
    militaryStatus: 'UNSPECIFIED',
    overseasTravelEligibility: 'UNSPECIFIED',
    employmentDisqualificationStatus: 'UNSPECIFIED',
    version: 0,
    createdAt: '2026-08-02T00:00:00Z',
    updatedAt: '2026-08-02T00:00:00Z',
  }
}

function education(): EducationDto {
  return {
    id: 'education-id',
    schoolName: 'School',
    major: 'Computer Science',
    degree: null,
    educationLevel: 'BACHELOR',
    educationStatus: 'EXPECTED_GRADUATION',
    admissionDate: null,
    graduationDate: null,
    gpa: null,
    gpaScale: null,
    isPrimary: false,
    description: null,
    version: 1,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function evidence(): EvidenceDto {
  return {
    id: 'evidence-active',
    sourceType: 'DOCUMENT_CHUNK',
    sourceEntityId: '00000000-0000-4000-8000-000000000102',
    documentId: '00000000-0000-4000-8000-000000000101',
    sourceDeletedAt: null,
    evidenceCategory: 'ACTIVITY',
    title: 'Project activity',
    content: 'Activity content',
    metadata: { role: 'lead' },
    confidence: 0.84,
    verificationStatus: 'PENDING',
    verifiedAt: null,
    version: 1,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}
