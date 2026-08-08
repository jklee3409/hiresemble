import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiClientError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import GitHubSourcePage from './GitHubSourcePage.vue'

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  list: vi.fn(),
  detail: vi.fn(),
  repositories: vi.fn(),
  select: vi.fn(),
  refresh: vi.fn(),
  remove: vi.fn(),
  confirm: vi.fn(),
  toast: vi.fn(),
}))

vi.mock('@/shared/api/githubSourceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/githubSourceApi')>()),
  createGitHubSource: mocks.create,
  listGitHubSources: mocks.list,
  getGitHubSource: mocks.detail,
  listGitHubRepositories: mocks.repositories,
  selectGitHubRepositories: mocks.select,
  refreshGitHubSource: mocks.refresh,
  deleteGitHubSource: mocks.remove,
  createGitHubIdempotencyKey: (action: string) => `${action}-test-key`,
}))

vi.mock('@/shared/ui/notifications', () => ({
  useNotifications: () => ({
    confirm: mocks.confirm,
    toast: mocks.toast,
  }),
}))

describe('GitHubSourcePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.list.mockResolvedValue(page([]))
    mocks.repositories.mockResolvedValue(page([]))
    mocks.confirm.mockResolvedValue(true)
    mocks.remove.mockResolvedValue(undefined)
  })

  it('requires a backend-compatible URL and participation confirmation before registration', async () => {
    const wrapper = await mountPage()
    await wrapper.get('form.github-register').trigger('submit')
    expect(wrapper.text()).toContain('직접 참여한 공개 source인지 확인해 주세요.')
    expect(mocks.create).not.toHaveBeenCalled()

    await wrapper.get('input[type="url"]').setValue('https://github.com/openai/repo/issues')
    await wrapper.get('#github-participation').setValue(true)
    await wrapper.get('form.github-register').trigger('submit')
    expect(wrapper.text()).toContain('github.com/계정/저장소 형식')
    expect(mocks.create).not.toHaveBeenCalled()

    mocks.create.mockResolvedValue(accepted())
    mocks.detail.mockResolvedValue(detail(source({ status: 'DISCOVERING' }), false))
    await wrapper.get('input[type="url"]').setValue('https://www.github.com/openai/hiresemble.git')
    await wrapper.get('form.github-register').trigger('submit')
    await flushPromises()
    expect(mocks.create).toHaveBeenCalledWith(
      {
        url: 'https://www.github.com/openai/hiresemble.git',
        participationConfirmed: true,
      },
      'create-test-key',
    )
    expect(wrapper.text()).toContain('GitHub 연결을 등록했어요')
  })

  it('searches and paginates on the server and enforces a 1–10 repository selection', async () => {
    const account = source({ status: 'WAITING_USER' })
    const repositories = Array.from({ length: 11 }, (_, index) => repository(index + 10))
    mocks.list.mockResolvedValue(page([account]))
    mocks.detail.mockResolvedValue(detail(account))
    mocks.repositories.mockResolvedValue({
      ...page(repositories),
      totalElements: 30,
      totalPages: 2,
    })
    mocks.select.mockResolvedValue(accepted())
    const wrapper = await mountPage(`/profile/github?source=${uuid(1)}`)

    expect(wrapper.text()).toContain('분석할 저장소 1~10개')
    expect(wrapper.get('.repository-selector__actions button').attributes('disabled')).toBeDefined()
    await wrapper.get('input[placeholder="저장소 이름 또는 설명"]').setValue('sdk')
    await wrapper.get('form[role="search"]').trigger('submit')
    await flushPromises()
    expect(mocks.repositories).toHaveBeenCalledWith(
      uuid(1),
      expect.objectContaining({ query: 'sdk', page: 0, sort: 'pushedAt,desc' }),
    )
    await wrapper.findAll('nav[aria-label="GitHub 저장소 페이지"] button')[1]?.trigger('click')
    await flushPromises()
    expect(mocks.repositories).toHaveBeenCalledWith(
      uuid(1),
      expect.objectContaining({ query: 'sdk', page: 1 }),
    )

    const choices = wrapper.findAll('.repository-choice input')
    for (const choice of choices.slice(0, 10)) await choice.setValue(true)
    expect(wrapper.text()).toContain('10개 선택')
    expect(choices[10]?.attributes('disabled')).toBeDefined()
    await choices[0]?.setValue(false)
    await wrapper.get('.repository-selector__actions button').trigger('click')
    await flushPromises()
    expect(mocks.select).toHaveBeenCalledWith(
      uuid(1),
      expect.objectContaining({ version: account.version }),
      'selection-test-key',
    )
    const submittedIds = mocks.select.mock.calls[0]?.[1].repositoryIds as string[]
    expect(new Set(submittedIds).size).toBe(9)
  })

  it('preserves a conflicted selection, reconciles missing repositories, and never auto-retries', async () => {
    const account = source({ status: 'WAITING_USER' })
    const existing = repository(10)
    const removed = repository(11)
    mocks.list.mockResolvedValue(page([account]))
    mocks.detail.mockResolvedValue(detail(account))
    mocks.repositories.mockImplementation(async (_sourceId: string, params: { query?: string }) => {
      if (params.query === existing.repositoryName) return page([existing])
      if (params.query === removed.repositoryName) return page([])
      return page([existing, removed])
    })
    mocks.select.mockRejectedValue(
      new ApiClientError({
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: 'conflict',
      }),
    )
    const wrapper = await mountPage(`/profile/github?source=${uuid(1)}`)
    const choices = wrapper.findAll('.repository-choice input')
    await choices[0]?.setValue(true)
    await choices[1]?.setValue(true)
    await wrapper.get('.repository-selector__actions button').trigger('click')
    await flushPromises()

    expect(mocks.select).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('시도한 선택 2개를 보존했습니다')
    expect(wrapper.text()).toContain(`owner/repository-11`)
    expect(wrapper.text()).toContain('남아 있는 선택을 검토한 뒤')
  })

  it('shows lifecycle states, result counts, truncated/incomplete warnings, and refresh outcomes', async () => {
    const ready = source({
      sourceKind: 'REPOSITORY',
      accountType: null,
      repositoryName: 'hiresemble',
      canonicalUrl: 'https://github.com/openai/hiresemble',
      status: 'READY',
      selectedRepositoryCount: 1,
      repositoryDiscoveryTruncated: true,
      snapshotIncomplete: true,
      newExperienceCount: 2,
      corroboratedExperienceCount: 3,
      reviewRequiredCount: 1,
      rejectedCandidateCount: 4,
    })
    mocks.list.mockResolvedValue(page([ready]))
    mocks.detail.mockResolvedValue(detail(ready, false))
    mocks.refresh
      .mockResolvedValueOnce({ changed: false, source: detail(ready, false), run: null })
      .mockResolvedValueOnce({
        changed: true,
        source: detail({ ...ready, status: 'QUEUED', version: 3 }, false),
        run: accepted(),
      })
    const wrapper = await mountPage(`/profile/github?source=${uuid(1)}`)

    expect(wrapper.text()).toContain('새 경험2')
    expect(wrapper.text()).toContain('기존 경험 보강3')
    expect(wrapper.text()).toContain('검토 필요1')
    expect(wrapper.text()).toContain('제외된 후보4')
    expect(wrapper.text()).toContain('일부 목록만 표시')
    expect(wrapper.text()).toContain('결과가 부분적일 수 있어요')

    await buttonByText(wrapper, '새로고침').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('새로운 변경이 없어 기존 분석 결과를 유지합니다')
    await buttonByText(wrapper, '새로고침').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('GitHub 분석을 다시 시작했어요')
  })

  it('explains rate limiting, failed retry guidance, and delete confirmation/cancel/success', async () => {
    const failed = source({ status: 'FAILED' })
    mocks.list.mockResolvedValue(page([failed]))
    mocks.create.mockRejectedValue(
      new ApiClientError({
        status: 429,
        code: 'GITHUB_RATE_LIMITED',
        message: 'raw',
        retryAfterSeconds: 60,
      }),
    )
    const wrapper = await mountPage()
    await wrapper.get('input[type="url"]').setValue('https://github.com/openai')
    await wrapper.get('#github-participation').setValue(true)
    await wrapper.get('form.github-register').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('GitHub 요청 한도에 도달했어요')
    expect(wrapper.text()).toContain('이후 다시 시도해 주세요')
    expect(wrapper.text()).toContain('AI 작업 상세에서 재시도 가능 여부 확인')

    mocks.confirm.mockResolvedValueOnce(false).mockResolvedValueOnce(true)
    await buttonByText(wrapper, '삭제').trigger('click')
    expect(mocks.remove).not.toHaveBeenCalled()
    await buttonByText(wrapper, '삭제').trigger('click')
    await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        message: expect.stringContaining('이미 검토하고 승인한 경험은 경험 보관함에 유지'),
      }),
    )
    expect(mocks.remove).toHaveBeenCalledWith(failed.id, failed.version)
  })
})

async function mountPage(path = '/profile/github') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.status = 'authenticated'
  authStore.currentUser = {
    id: 'user-1',
    email: 'user@example.com',
    displayName: 'User',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profile/github', component: { template: '<div />' } },
      { path: '/agent-runs/:agentRunId', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = mount(GitHubSourcePage, {
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient }]],
      stubs: {
        ProfileTabs: { template: '<nav aria-label="프로필 메뉴" />' },
        GitHubRunMonitor: { template: '<div data-testid="github-run-monitor" />' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().trim() === text)
  if (button === undefined) throw new Error(`버튼을 찾지 못했습니다: ${text}`)
  return button
}

const now = '2026-08-08T00:00:00Z'

function source(overrides: Record<string, unknown> = {}) {
  return {
    id: uuid(1),
    sourceKind: 'ACCOUNT',
    accountType: 'USER',
    canonicalUrl: 'https://github.com/openai',
    ownerLogin: 'openai',
    repositoryName: null,
    status: 'DISCOVERING',
    discoveredRepositoryCount: 30,
    selectedRepositoryCount: 0,
    repositoryDiscoveryTruncated: false,
    newExperienceCount: 0,
    corroboratedExperienceCount: 0,
    reviewRequiredCount: 0,
    rejectedCandidateCount: 0,
    snapshotIncomplete: false,
    latestAgentRunId: uuid(2),
    lastSuccessfulSyncAt: null,
    version: 2,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  }
}

function detail(value = source({ status: 'WAITING_USER' }), withAction = true) {
  return {
    source: value,
    requiredUserAction: withAction
      ? {
          type: 'SELECT_GITHUB_REPOSITORIES',
          resource: {
            resourceType: 'GITHUB_SOURCE',
            resourceId: value.id,
            displayLabel: value.canonicalUrl,
          },
          route: '/profile/github',
          message: '저장소를 선택해 주세요.',
        }
      : null,
  }
}

function repository(value: number) {
  return {
    id: uuid(value),
    ownerLogin: 'owner',
    repositoryName: `repository-${value}`,
    canonicalUrl: `https://github.com/owner/repository-${value}`,
    description: `저장소 ${value}`,
    defaultBranch: 'main',
    fork: value % 2 === 0,
    archived: value % 3 === 0,
    selected: false,
    pushedAt: now,
  }
}

function accepted() {
  return {
    agentRunId: uuid(2),
    status: 'QUEUED',
    resourceType: 'GITHUB_SOURCE',
    resourceId: uuid(1),
    replayed: false,
  }
}

function page(items: unknown[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
