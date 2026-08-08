import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as careerArtifactApi from '@/shared/api/careerArtifactApi'
import type { ExperienceItemDto } from '@/shared/api/contracts'
import { ApiClientError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'

import CareerArtifactGenerationForm from './CareerArtifactGenerationForm.vue'

vi.mock('@/shared/api/careerArtifactApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/api/careerArtifactApi')>()
  return {
    ...original,
    getCareerArtifactReadiness: vi.fn(),
    listCareerArtifactAiModels: vi.fn(),
    createCareerArtifact: vi.fn(),
    generateCareerArtifactVersion: vi.fn(),
  }
})

vi.mock('@/shared/api/profileApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/api/profileApi')>()
  return { ...original, listExperiences: vi.fn() }
})

describe('CareerArtifactGenerationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(profileApi.listExperiences).mockResolvedValue(page([experience()]))
    vi.mocked(careerArtifactApi.getCareerArtifactReadiness).mockResolvedValue(readiness())
    vi.mocked(careerArtifactApi.listCareerArtifactAiModels).mockResolvedValue([model()])
    vi.mocked(careerArtifactApi.createCareerArtifact).mockResolvedValue(accepted())
    vi.mocked(careerArtifactApi.generateCareerArtifactVersion).mockResolvedValue(accepted())
  })

  it('does not create a Run before final confirmation and submits one selected experience with exact model', async () => {
    const wrapper = await mountForm()
    await reachFinalStep(wrapper)
    expect(careerArtifactApi.createCareerArtifact).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('확인된 강점 경험이 아직 없어요')

    await wrapper.get('#artifact-title').setValue('  백엔드 이력서  ')
    const contactToggle = wrapper
      .findAll('label')
      .find((label) => label.text().includes('파일에 연락처와 링크 표시'))
    await contactToggle?.find('input').setValue(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(careerArtifactApi.createCareerArtifact).toHaveBeenCalledOnce()
    const [request, key] = vi.mocked(careerArtifactApi.createCareerArtifact).mock.calls[0] ?? []
    expect(request).toMatchObject({
      artifactType: 'RESUME',
      title: '백엔드 이력서',
      experienceItemIds: [experienceId],
      model: 'server-model-exact',
      templateKey: 'resume-ats-v1',
      renderProfile: {
        displayName: '테스터',
        email: null,
        phone: null,
        links: [],
        includeContact: false,
      },
    })
    expect(key).toMatch(/^career-artifact-create:/)
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    expect(sessionStorage.length).toBe(0)
  })

  it('blocks progress when the server model catalog is empty', async () => {
    vi.mocked(careerArtifactApi.listCareerArtifactAiModels).mockResolvedValue([])
    const wrapper = await mountForm()
    await clickButton(wrapper, '다음')
    await flushPromises()
    await wrapper.get('.artifact-experience-list input[type="checkbox"]').setValue(true)
    await clickButton(wrapper, '다음')
    await flushPromises()
    expect(wrapper.text()).toContain('선택할 수 있는 AI 모델이 없어요')
    await clickButton(wrapper, '다음')
    expect(wrapper.text()).toContain('현재 사용할 수 있는 AI 모델을 선택해 주세요')
    expect(careerArtifactApi.createCareerArtifact).not.toHaveBeenCalled()
  })

  it('does not automatically retry a 409 and preserves the draft for user confirmation', async () => {
    vi.mocked(careerArtifactApi.createCareerArtifact).mockRejectedValue(
      new ApiClientError({
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: 'conflict',
      }),
    )
    const wrapper = await mountForm()
    await reachFinalStep(wrapper)
    await wrapper.get('#artifact-title').setValue('충돌 이력서')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(careerArtifactApi.createCareerArtifact).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('최신 내용을 확인한 뒤 다시 선택')
    expect(sessionStorage.getItem('1/user-1/career-artifact/new/generation/0')).not.toBeNull()
  })

  it('clears the session draft on explicit cancellation', async () => {
    const wrapper = await mountForm()
    await wrapper.get('#artifact-step-1')
    expect(sessionStorage.getItem('1/user-1/career-artifact/new/generation/0')).not.toBeNull()
    await clickButton(wrapper, '취소')
    expect(sessionStorage.getItem('1/user-1/career-artifact/new/generation/0')).toBeNull()
    expect(wrapper.emitted('cancelled')).toHaveLength(1)
  })

  it('connects validation errors to invalid experience and contact controls', async () => {
    const wrapper = await mountForm()
    await clickButton(wrapper, '다음')
    await clickButton(wrapper, '다음')
    const experienceInput = wrapper.get('.artifact-experience-list input[type="checkbox"]')
    expect(experienceInput.attributes('aria-describedby')).toBe('artifact-experience-error')
    expect(wrapper.get('#artifact-experience-error').attributes('role')).toBe('alert')

    await experienceInput.setValue(true)
    await clickButton(wrapper, '다음')
    await wrapper.get('input[name="model"]').setValue('server-model-exact')
    await clickButton(wrapper, '다음')
    await wrapper.get('#artifact-title').setValue('접근성 이력서')
    await wrapper.get('#artifact-email').setValue('invalid-email')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('#artifact-email').attributes('aria-describedby')).toBe(
      'artifact-render-contact-error',
    )
    expect(wrapper.get('#artifact-render-contact-error').attributes('role')).toBe('alert')
  })
})

async function mountForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = mount(CareerArtifactGenerationForm, {
    props: {
      userId: 'user-1',
      displayName: '테스터',
      email: 'tester@example.com',
      artifactType: 'RESUME',
    },
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

async function reachFinalStep(wrapper: VueWrapper): Promise<void> {
  await clickButton(wrapper, '다음')
  await flushPromises()
  await wrapper.get('.artifact-experience-list input[type="checkbox"]').setValue(true)
  await clickButton(wrapper, '다음')
  await flushPromises()
  await wrapper.get('input[name="model"]').setValue('server-model-exact')
  await clickButton(wrapper, '다음')
  await flushPromises()
}

async function clickButton(wrapper: VueWrapper, text: string): Promise<void> {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) throw new Error(`button not found: ${text}`)
  await button.trigger('click')
}

const now = '2026-08-08T00:00:00Z'
const experienceId = '00000000-0000-4000-8000-000000000010'

function experience(): ExperienceItemDto {
  return {
    id: experienceId,
    evidenceCategory: 'PROJECT',
    title: '성능 개선',
    content: '검증된 API 성능 개선 경험',
    verificationStatus: 'VERIFIED' as const,
    matchKind: 'NEW' as const,
    matchedExperienceItemId: null,
    matchSimilarity: null,
    reviewRequired: false,
    sourceCount: 2,
    documentSourceCount: 1,
    githubRepositorySourceCount: 1,
    primaryDocumentName: 'resume.pdf',
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

function model() {
  return {
    id: 'server-model-exact',
    displayName: '서버 모델',
    description: '서버가 제공한 모델',
    recommended: true,
  }
}

function readiness() {
  return {
    hasUploadedResume: false,
    hasUploadedPortfolio: false,
    hasGeneratedResume: false,
    hasGeneratedPortfolio: false,
    verifiedExperienceCount: 1,
    verifiedGitHubExperienceCount: 1,
    verifiedStrengthCount: 0,
    canGenerateResume: true,
    canGeneratePortfolio: true,
    warnings: [],
  }
}

function accepted() {
  return {
    agentRunId: '00000000-0000-4000-8000-000000000020',
    status: 'QUEUED' as const,
    resourceType: 'CAREER_ARTIFACT',
    resourceId: '00000000-0000-4000-8000-000000000021',
    replayed: false,
  }
}

function page<T>(items: T[]) {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}
