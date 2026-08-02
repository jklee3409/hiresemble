import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as agentRunApi from '@/shared/api/agentRunApi'
import * as dashboardApi from '@/shared/api/dashboardApi'
import type { CareerGuidePostDto, DashboardDto } from '@/shared/api/dashboardContracts'
import * as documentApi from '@/shared/api/documentApi'
import * as jobApi from '@/shared/api/jobApi'
import { useAuthStore } from '@/stores/auth'

import DashboardPage from './DashboardPage.vue'

vi.mock('@/shared/api/dashboardApi', () => ({ getDashboard: vi.fn(), listCareerGuides: vi.fn() }))
vi.mock('@/shared/api/documentApi', () => ({ listDocuments: vi.fn() }))
vi.mock('@/shared/api/jobApi', () => ({ listJobs: vi.fn() }))
vi.mock('@/shared/api/agentRunApi', () => ({ listAgentRuns: vi.fn() }))

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-02T03:00:00Z'))
    mockSources()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.replaceChildren()
    document.body.style.overflow = ''
  })

  it('shows a natural heading, career facts and action-oriented summary labels', async () => {
    const wrapper = await mountDashboard()

    expect(wrapper.get('h1').text()).toBe('이종규님의 지원 준비 현황')
    expect(wrapper.get('.dashboard-title__name').text()).toBe('이종규')
    expect(wrapper.text()).toContain('마감 일정과 다음 할 일을 한눈에 확인하세요.')
    expect(wrapper.find('.career-card__person svg').exists()).toBe(true)
    expect(wrapper.find('.career-card__monogram').exists()).toBe(false)
    expect(wrapper.get('.career-card').text()).toContain('백엔드 개발자')
    expect(wrapper.get('.career-card').text()).toContain('서울')
    expect(wrapper.get('.career-card').text()).toContain('한국대학교 · 컴퓨터공학 · 학사 · 졸업')
    expect(wrapper.get('.career-card').text()).toContain('지원 정보 준비도')
    expect(wrapper.get('.start-checklist').text()).toContain('2 / 3 완료')
    expect(wrapper.get('.summary-grid').text()).toContain('준비 중인 공고')
    expect(wrapper.get('.summary-grid').text()).toContain('지원 완료')
    expect(wrapper.get('.summary-grid').text()).toContain('AI가 확인 중')
    expect(wrapper.get('.summary-grid').text()).toContain('등록한 이력서·자료')
    expect(wrapper.find('.summary-section .section-kicker').exists()).toBe(false)
    expect(wrapper.get('.summary-section > h2').classes()).toContain('sr-only')
    expect(wrapper.text()).not.toContain('오늘로 이동')
    expect(wrapper.text()).not.toContain('모든 날짜와 시각은 Asia/Seoul 기준으로 표시합니다.')

    const shortcuts = wrapper.get('aside[aria-label="대시보드 바로가기"]')
    expect(shortcuts.findAll('a').map((link) => [link.text(), link.attributes('href')])).toEqual([
      ['지원 현황', '#dashboard-overview'],
      ['마감 캘린더', '#dashboard-deadlines'],
      ['최근 활동', '#dashboard-activity'],
      ['취업 준비 가이드', '#dashboard-guides'],
    ])
    wrapper.unmount()
  })

  it('selects a calendar date and shows the exact deadline details in Seoul time', async () => {
    const wrapper = await mountDashboard()
    const dateButton = wrapper.get<HTMLButtonElement>('button[aria-label^="2026-08-15,"]')
    const sundayButton = wrapper.get<HTMLButtonElement>('button[aria-label^="2026-08-02,"]')

    expect(dateButton.text()).toContain('2건')
    expect(dateButton.classes()).toContain('calendar-day--saturday')
    expect(sundayButton.classes()).toContain('calendar-day--sunday')
    await dateButton.trigger('click')

    const detail = wrapper.get('.deadline-detail--desktop')
    expect(detail.text()).toContain('2026-08-15')
    expect(detail.text()).toContain('2건')
    expect(detail.text()).toContain('플랫폼 엔지니어')
    expect(detail.text()).toContain('하이어셈블랩')
    expect(detail.text()).toContain('지원 완료')
    expect(detail.findAll('a[href^="/jobs/"]')).toHaveLength(2)

    await wrapper.get<HTMLButtonElement>('button[aria-label="다음 달"]').trigger('click')
    await flushPromises()
    expect(dashboardApi.getDashboard).toHaveBeenCalledWith('2026-09')
    wrapper.unmount()
  })

  it('opens a server guide in an accessible modal and restores trigger focus on Escape', async () => {
    const wrapper = await mountDashboard()
    const trigger = wrapper.get<HTMLButtonElement>('.guide-card')
    trigger.element.focus()

    await trigger.trigger('click')
    await flushPromises()

    const dialog = document.body.querySelector<HTMLElement>('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog?.getAttribute('aria-modal')).toBe('true')
    expect(dialog?.textContent).toContain('공고 분석 전에 확인할 항목')
    expect(dialog?.textContent).toContain('필수 역량을 먼저 표시하세요.')
    expect(dialog?.querySelector('.guide-modal__hero')).not.toBeNull()
    expect(dialog?.querySelectorAll('.guide-modal__content p')).toHaveLength(2)
    expect(dialog?.textContent).toContain('콘텐츠 v2')
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.activeElement?.getAttribute('aria-label')).toBe('가이드 닫기')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })

  it('does not turn dashboard and guide failures into zero or empty success states', async () => {
    vi.mocked(dashboardApi.getDashboard).mockRejectedValue(new Error('dashboard unavailable'))
    vi.mocked(dashboardApi.listCareerGuides).mockRejectedValue(new Error('guides unavailable'))

    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('지원 준비 요약과 마감 일정을 불러오지 못했어요.')
    expect(wrapper.get('.summary-grid').text()).toContain('—')
    expect(wrapper.get('.start-checklist').text()).toContain('다시 확인')
    expect(wrapper.text()).toContain('이 달의 마감 일정을 확인하지 못했어요.')
    expect(wrapper.text()).toContain('취업 준비 가이드를 불러오지 못했어요.')
    expect(wrapper.text()).not.toContain('현재 게시된 취업 준비 가이드가 없어요.')
    wrapper.unmount()
  })

  it('distinguishes a successful month with no deadlines and no published guides', async () => {
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue(dashboard({ deadlineDays: [] }))
    vi.mocked(dashboardApi.listCareerGuides).mockResolvedValue([])

    const wrapper = await mountDashboard()

    expect(wrapper.get('.deadline-section__summary').text()).toContain('0건')
    expect(wrapper.get('.deadline-detail--desktop').text()).toContain(
      '이날 마감되는 공고가 없어요.',
    )
    expect(wrapper.text()).toContain('현재 게시된 취업 준비 가이드가 없어요.')
    wrapper.unmount()
  })
})

function mockSources(): void {
  vi.clearAllMocks()
  vi.mocked(dashboardApi.getDashboard).mockImplementation(async (month) =>
    dashboard({ month, deadlineDays: month === '2026-08' ? deadlineDays() : [] }),
  )
  vi.mocked(dashboardApi.listCareerGuides).mockResolvedValue(guides())
  vi.mocked(documentApi.listDocuments).mockResolvedValue(page([]))
  vi.mocked(jobApi.listJobs).mockResolvedValue(page([]))
  vi.mocked(agentRunApi.listAgentRuns).mockResolvedValue(page([]))
}

async function mountDashboard() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: {
      id: '00000000-0000-4000-8000-000000000001',
      email: 'dashboard@example.com',
      displayName: '이종규',
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', component: DashboardPage },
      { path: '/profile/basic', component: { template: '<div />' } },
      { path: '/documents', component: { template: '<div />' } },
      { path: '/documents/:documentId', component: { template: '<div />' } },
      { path: '/jobs', component: { template: '<div />' } },
      { path: '/jobs/new', component: { template: '<div />' } },
      { path: '/jobs/:jobId/overview', component: { template: '<div />' } },
      { path: '/agent-runs', component: { template: '<div />' } },
      { path: '/agent-runs/:agentRunId', component: { template: '<div />' } },
      { path: '/guide', component: { template: '<div />' } },
    ],
  })
  await router.push('/dashboard')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(DashboardPage, {
    attachTo: document.body,
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function dashboard(overrides: Partial<DashboardDto> = {}): DashboardDto {
  return {
    generatedAt: '2026-08-02T03:00:00Z',
    month: '2026-08',
    profile: {
      displayName: '이종규',
      legalName: '이종규',
      desiredRoles: ['백엔드 개발자'],
      desiredLocations: ['서울'],
      completed: false,
      completionPercent: 80,
      missingItems: ['DESIRED_INDUSTRY'],
      primaryEducation: {
        schoolName: '한국대학교',
        major: '컴퓨터공학',
        degree: null,
        educationLevel: 'BACHELOR',
        educationStatus: 'GRADUATED',
      },
    },
    documents: { registeredCount: 2, processingCount: 1, needsActionCount: 0 },
    jobs: { registeredCount: 5, preparingCount: 3, submittedCount: 2 },
    agentRuns: { activeCount: 1 },
    deadlineDays: deadlineDays(),
    ...overrides,
  }
}

function deadlineDays(): DashboardDto['deadlineDays'] {
  return [
    {
      date: '2026-08-02',
      count: 1,
      items: [deadlineJob('00000000-0000-4000-8000-000000000020', '백엔드 개발자')],
    },
    {
      date: '2026-08-15',
      count: 2,
      items: [
        deadlineJob('00000000-0000-4000-8000-000000000021', '플랫폼 엔지니어'),
        deadlineJob('00000000-0000-4000-8000-000000000022', '서버 개발자', 'SUBMITTED'),
      ],
    },
  ]
}

function deadlineJob(
  id: string,
  positionName: string,
  status: 'IN_PROGRESS' | 'SUBMITTED' = 'IN_PROGRESS',
): DashboardDto['deadlineDays'][number]['items'][number] {
  return {
    id,
    companyName: '하이어셈블랩',
    title: positionName,
    positionName,
    status,
    deadlineAt: '2026-08-15T05:30:00Z',
  }
}

function guides(): CareerGuidePostDto[] {
  return [
    {
      id: '00000000-0000-4000-8000-000000000101',
      status: 'PUBLISHED',
      displayOrder: 10,
      category: '공고 분석',
      title: '공고 분석 전에 확인할 항목',
      summary: '회사와 역할, 자격 요건을 나눠 읽어 보세요.',
      body: '공고를 처음 읽을 때는 필수 역량을 먼저 표시하세요. 담당 업무의 동사와 기대 결과를 함께 적으면 역할의 중심이 선명해집니다.\n\n지원 전에는 우대 조건과 근무 조건을 분리해 확인하고, 내 경험에서 연결할 수 있는 근거를 한 줄씩 남겨 보세요.',
      publishedAt: '2026-08-01T00:00:00Z',
      version: 2,
    },
  ]
}

function page<T>(items: T[]) {
  return { items, page: 0, size: 5, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}
