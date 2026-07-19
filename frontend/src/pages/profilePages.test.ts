import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import ProfileBasicPage from '@/pages/ProfileBasicPage.vue'
import ProfileEvidencePage from '@/pages/ProfileEvidencePage.vue'
import StructuredProfilePage from '@/pages/StructuredProfilePage.vue'
import type { EducationDto, EvidenceDto, PageResponse, ProfileDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import * as documentApi from '@/shared/api/documentApi'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api/profileApi', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
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
  listEvidence: vi.fn(),
  updateEvidence: vi.fn(),
  verifyEvidence: vi.fn(),
}))

vi.mock('@/shared/api/documentApi', () => ({
  listDocuments: vi.fn(),
}))

describe('P2 profile pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(documentApi.listDocuments).mockResolvedValue(pageOf([]))
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

    expect(wrapper.text()).toContain('20% 완료')
    expect(wrapper.text()).toContain('보완 권장')
    expect(wrapper.text()).toContain('대표 학력')
    await wrapper.get('#profile-legalName').setValue('Updated User')
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
    expect(wrapper.text()).toContain('프로필을 저장했습니다.')
    expect(wrapper.text()).toContain('100% 완료')
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

    expect(wrapper.text()).toContain('다른 곳에서 최신 내용이 저장되었습니다')
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
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('입력 길이를 확인해 주세요.')
    expect(wrapper.get('#profile-legalName').attributes('aria-invalid')).toBe('true')
  })

  it('supports education list/create/update/delete and primary selection', async () => {
    const item = education()
    vi.mocked(profileApi.listEducations).mockResolvedValue(pageOf([item]))
    vi.mocked(profileApi.createEducation).mockResolvedValue({ ...item, id: 'new-id' })
    vi.mocked(profileApi.updateEducation).mockResolvedValue({
      ...item,
      isPrimary: true,
      version: 2,
    })
    vi.mocked(profileApi.deleteEducation).mockResolvedValue()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = await mountPage(StructuredProfilePage, { kind: 'education' })

    expect(wrapper.text()).toContain('School')
    await wrapper.get('button').trigger('click')
    await wrapper.get('#education-schoolName').setValue('New School')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.createEducation).toHaveBeenCalledWith(
      expect.objectContaining({ schoolName: 'New School', isPrimary: false }),
    )

    const primaryButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '대표로 설정')
    await primaryButton?.trigger('click')
    await flushPromises()
    expect(profileApi.updateEducation).toHaveBeenCalledWith(
      item.id,
      expect.objectContaining({ isPrimary: true, version: item.version }),
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
    await deleteButton?.trigger('click')
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
    const deleted = {
      ...evidence(),
      id: 'evidence-deleted',
      verificationStatus: 'SOURCE_DELETED' as const,
      sourceDeletedAt: '2026-07-19T00:00:00Z',
    }
    vi.mocked(profileApi.listEvidence).mockResolvedValue(pageOf([active, deleted]))
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

    expect(wrapper.text()).toContain('직접 입력 근거와 문서에서 추출된 근거')
    await wrapper.get('#evidence-document-filter').setValue('00000000-0000-4000-8000-000000000101')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(profileApi.listEvidence).toHaveBeenLastCalledWith(
      expect.objectContaining({ documentId: '00000000-0000-4000-8000-000000000101' }),
    )
    expect(wrapper.text()).toContain('원본이 삭제되어 읽기 전용입니다.')
    const deletedCard = wrapper
      .findAll('li')
      .find((card) => card.text().includes('evidence-deleted'))
    expect(deletedCard).toBeUndefined()
    const disabledActions = wrapper
      .findAll('button[disabled]')
      .filter((button) => ['수정', '승인', '거절'].includes(button.text()))
    expect(disabledActions.length).toBeGreaterThanOrEqual(3)

    await wrapper.get('select').setValue('REJECTED')
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

function education(): EducationDto {
  return {
    id: 'education-id',
    schoolName: 'School',
    major: 'Computer Science',
    degree: null,
    educationStatus: 'ENROLLED',
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
    sourceType: 'EDUCATION',
    sourceEntityId: 'education-id',
    documentId: null,
    sourceDeletedAt: null,
    evidenceCategory: 'EDUCATION',
    title: 'School evidence',
    content: 'Education content',
    metadata: { primary: true },
    confidence: null,
    verificationStatus: 'VERIFIED',
    verifiedAt: '2026-07-19T00:00:00Z',
    version: 1,
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  }
}

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}
