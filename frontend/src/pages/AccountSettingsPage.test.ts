import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import * as authApi from '@/shared/api/authApi'
import { useAuthStore } from '@/stores/auth'

import AccountSettingsPage from './AccountSettingsPage.vue'

const accountMocks = vi.hoisted(() => ({
  changePassword: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock('@/shared/api/accountApi', () => ({
  changeAccountPassword: accountMocks.changePassword,
  deleteAccount: accountMocks.deleteAccount,
}))

vi.mock('@/shared/api/authApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/authApi')>()),
  updateDisplayName: vi.fn(),
  logout: vi.fn(),
}))

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    accountMocks.changePassword.mockResolvedValue(undefined)
    accountMocks.deleteAccount.mockResolvedValue({
      deletionRequestId: uuid(9),
      purgeBy: '2026-08-10T00:00:00Z',
    })
    vi.mocked(authApi.updateDisplayName).mockResolvedValue({
      id: 'user-1',
      email: 'user@example.com',
      displayName: '변경한 이름',
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('validates password parity and confirms other sessions are revoked on success', async () => {
    const { wrapper } = await mountPage()
    const form = wrapper.get('section[aria-labelledby="settings-password-heading"] form')
    await form.get('#settings-current-password').setValue('Old-password1!')
    await form.get('#settings-new-password').setValue('New-password2!')
    await form.get('#settings-new-password-confirm').setValue('different')
    await form.trigger('submit')
    expect(wrapper.text()).toContain('새 비밀번호가 서로 달라요.')
    expect(accountMocks.changePassword).not.toHaveBeenCalled()

    await form.get('#settings-new-password-confirm').setValue('New-password2!')
    await form.trigger('submit')
    await flushPromises()
    expect(accountMocks.changePassword).toHaveBeenCalledWith({
      currentPassword: 'Old-password1!',
      newPassword: 'New-password2!',
    })
    expect(wrapper.text()).toContain('다른 모든 세션을 종료했어요.')
  })

  it('requires explicit second confirmation and performs the full client cleanup after 202', async () => {
    const { wrapper, router, authStore } = await mountPage()
    const replace = vi.spyOn(router, 'replace')
    window.sessionStorage.setItem('1/user-1/career-artifact/new/generation/0', '{}')
    const trigger = wrapper
      .findAll('button')
      .find((candidate) => candidate.text().trim() === '회원 탈퇴')!
    await trigger.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(document.activeElement?.id).toBe('delete-current-password')
    await wrapper.get('#delete-current-password').setValue('Old-password1!')
    const deletionForm = wrapper.get('[role="dialog"] form')
    await deletionForm.trigger('submit')
    expect(wrapper.text()).toContain('영구 삭제 확인을 모두 완료해 주세요.')
    expect(accountMocks.deleteAccount).not.toHaveBeenCalled()

    await deletionForm.get('input[type="checkbox"]').setValue(true)
    await deletionForm.trigger('submit')
    await flushPromises()

    expect(accountMocks.deleteAccount).toHaveBeenCalledWith({
      currentPassword: 'Old-password1!',
      permanentDeletionConfirmed: true,
    })
    expect(authStore.status).toBe('anonymous')
    expect(authStore.currentUser).toBeNull()
    expect(window.sessionStorage.length).toBe(0)
    expect(router.currentRoute.value.name).toBe('login')
    expect(replace).toHaveBeenCalledWith({
      name: 'login',
      state: { accountDeletionAccepted: true, purgeBy: '2026-08-10T00:00:00Z' },
    })
    expect(JSON.stringify(replace.mock.calls)).not.toContain(uuid(9))
  })

  it('restores focus when the destructive dialog is cancelled with Escape', async () => {
    const { wrapper } = await mountPage()
    const trigger = wrapper
      .findAll('button')
      .find((candidate) => candidate.text().trim() === '회원 탈퇴')!
    trigger.element.focus()
    await trigger.trigger('click')
    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
  })
})

async function mountPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.status = 'authenticated'
  authStore.currentUser = {
    id: 'user-1',
    email: 'user@example.com',
    displayName: '사용자',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings/account', name: 'settings-account', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push('/settings/account')
  await router.isReady()
  const wrapper = mount(AccountSettingsPage, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient: new QueryClient() }]],
      stubs: { Teleport: true },
    },
  })
  await flushPromises()
  return { wrapper, router, authStore }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
