import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import GitHubAppConnectionsCard from './GitHubAppConnectionsCard.vue'

const mocks = vi.hoisted(() => ({
  capability: vi.fn(),
  list: vi.fn(),
  start: vi.fn(),
  refresh: vi.fn(),
  disconnect: vi.fn(),
  createSource: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/shared/api/githubAppConnectionApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/githubAppConnectionApi')>()),
  getGitHubAppCapability: mocks.capability,
  listGitHubAppConnections: mocks.list,
  startGitHubAppConnection: mocks.start,
  refreshGitHubAppConnection: mocks.refresh,
  disconnectGitHubAppConnection: mocks.disconnect,
}))

vi.mock('@/shared/api/githubSourceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/githubSourceApi')>()),
  createGitHubSource: mocks.createSource,
  createGitHubIdempotencyKey: () => 'private-source-key',
}))

vi.mock('@/shared/ui/notifications', () => ({
  useNotifications: () => ({
    confirm: mocks.confirm,
    toast: vi.fn(),
  }),
}))

describe('GitHubAppConnectionsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.capability.mockResolvedValue({
      enabled: true,
      configured: true,
      requiredPermissions: ['metadata:read', 'contents:read'],
    })
    mocks.list.mockResolvedValue({ items: [connection()] })
    mocks.start.mockResolvedValue({
      installationUrl: `https://github.com/apps/hiresemble/installations/new?state=${'a'.repeat(43)}`,
      expiresAt: now,
    })
    mocks.refresh.mockResolvedValue(connection({ version: 2 }))
    mocks.disconnect.mockResolvedValue(connection({ status: 'DISCONNECTING', version: 2 }))
    mocks.createSource.mockResolvedValue({
      agentRunId: uuid(2),
      status: 'QUEUED',
      resourceType: 'GITHUB_SOURCE',
      resourceId: uuid(3),
      replayed: false,
    })
    mocks.confirm.mockResolvedValue(true)
  })

  it('cleans callback secrets, renders status, refreshes, and requires uninstall confirmation', async () => {
    const { wrapper, router } = await mountCard(
      `/integrations?githubAppResult=connected&state=secret&code=oauth-code`,
    )

    expect(wrapper.text()).toContain('GitHub App 연결을 확인했어요.')
    expect(wrapper.text()).toContain('acme')
    expect(wrapper.text()).toContain('연결됨')
    expect(router.currentRoute.value.query).toEqual({})
    expect(wrapper.html()).not.toContain('oauth-code')

    await button(wrapper, '권한 다시 확인').trigger('click')
    await flushPromises()
    expect(mocks.refresh).toHaveBeenCalledWith(uuid(1), { version: 1 })

    await button(wrapper, '연결 해제').trigger('click')
    await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith(
      expect.objectContaining({ message: expect.stringContaining('GitHub에서 이 앱을 지우고') }),
    )
    expect(mocks.disconnect).toHaveBeenCalledWith(uuid(1), 1)
  })

  it('validates the external URL and creates a private source using the ACTIVE connection', async () => {
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    const { wrapper } = await mountCard('/integrations')

    await button(wrapper, 'GitHub App 연결').trigger('click')
    await flushPromises()
    expect(mocks.start).toHaveBeenCalledOnce()
    expect(anchorClick).toHaveBeenCalledOnce()

    const form = wrapper.get('form.private-source-form')
    await form.get('input[type="url"]').setValue('https://github.com/acme/private-platform')
    await form.get('input[type="checkbox"]').setValue(true)
    await form.trigger('submit')
    await flushPromises()

    expect(mocks.createSource).toHaveBeenCalledWith(
      {
        url: 'https://github.com/acme/private-platform',
        participationConfirmed: true,
        accessMode: 'GITHUB_APP',
        connectionId: uuid(1),
      },
      'private-source-key',
    )
    expect(wrapper.emitted('sourceCreated')).toEqual([[uuid(3)]])
  })

  it('fails closed when capability is unavailable', async () => {
    mocks.capability.mockResolvedValue({
      enabled: false,
      configured: false,
      requiredPermissions: ['metadata:read', 'contents:read'],
    })
    const { wrapper } = await mountCard('/integrations')
    expect(wrapper.text()).toContain('private 저장소 연결은 아직 쓸 수 없어요.')
    expect(wrapper.text()).not.toContain('연결 준비 중')
  })
})

async function mountCard(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/integrations', component: { template: '<div />' } }],
  })
  await router.push(path)
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = mount(GitHubAppConnectionsCard, {
    props: { userId: 'user-1' },
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: ReturnType<typeof mount>, text: string) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text().trim() === text)
  if (found === undefined) throw new Error(`button not found: ${text}`)
  return found
}

const now = '2026-08-09T00:00:00Z'

function connection(overrides: Record<string, unknown> = {}) {
  return {
    id: uuid(1),
    targetAccountLogin: 'acme',
    targetAccountType: 'ORGANIZATION',
    repositorySelection: 'SELECTED',
    status: 'ACTIVE',
    manageUrl: 'https://github.com/settings/installations/91',
    version: 1,
    connectedAt: now,
    verifiedAt: now,
    lastCheckedAt: now,
    disconnectedAt: null,
    ...overrides,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
