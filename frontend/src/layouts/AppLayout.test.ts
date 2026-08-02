import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import AppLayout from './AppLayout.vue'
import appLayoutSource from './AppLayout.vue?raw'
import { useAuthStore } from '@/stores/auth'

const DashboardPage = {
  template: '<section><h2>대시보드 테스트</h2></section>',
}
const ProfilePage = {
  template: '<section><h2>프로필 테스트</h2></section>',
}
const TestApp = {
  template: '<RouterView />',
}

describe('AppLayout', () => {
  afterEach(() => {
    document.body.replaceChildren()
    document.body.style.overflow = ''
  })

  it('marks the current journey in the top navigation without duplicating a sidebar profile', async () => {
    const { wrapper } = await mountLayout('/profile/basic')

    const activeDesktopLink = wrapper.get('.desktop-navigation__link[aria-current="page"]')
    expect(activeDesktopLink.text()).toBe('내 정보')
    expect(wrapper.findAll('.desktop-navigation__link').map((link) => link.text())).toEqual([
      '홈',
      '내 정보',
      '이력서·자료',
      '관심 공고',
      '자기소개서',
      '면접 준비',
    ])
    expect(wrapper.find('.desktop-sidebar').exists()).toBe(false)
    expect(wrapper.find('.user-avatar').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('모의 면접')
    expect(document.activeElement).toBe(wrapper.get('#app-content').element)
    wrapper.unmount()
  })

  it('keeps route focus on the non-interactive workspace and scopes the wider canvas to dashboard', async () => {
    const { wrapper, router } = await mountLayout('/dashboard')
    const workspace = wrapper.get<HTMLElement>('#app-content')

    expect(workspace.attributes('tabindex')).toBe('-1')
    expect(workspace.classes()).toContain('workspace-content--dashboard')
    expect(document.activeElement).toBe(workspace.element)

    await router.push('/documents')
    await flushPromises()

    expect(workspace.classes()).not.toContain('workspace-content--dashboard')
    expect(document.activeElement).toBe(workspace.element)
    expect(getComputedStyle(workspace.element).outlineStyle).toBe('none')
    expect(appLayoutSource).toContain(".workspace-content[tabindex='-1']:focus-visible")
    expect(appLayoutSource).toContain('box-shadow: none;')
    wrapper.unmount()
  })

  it('opens and closes the mobile more sheet with accessible state and focus return', async () => {
    const { wrapper } = await mountLayout('/dashboard')
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-controls="mobile-more-menu"]')

    trigger.element.focus()
    await trigger.trigger('click')
    await nextTick()

    expect(trigger.attributes('aria-expanded')).toBe('true')
    const drawer = document.body.querySelector<HTMLElement>('#mobile-more-menu')
    expect(drawer).not.toBeNull()
    expect(drawer?.getAttribute('role')).toBe('dialog')
    expect(drawer?.getAttribute('aria-modal')).toBe('true')
    expect(document.body.style.overflow).toBe('hidden')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()

    expect(document.body.querySelector('#mobile-more-menu')).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })

  it('keeps interview question-set detail inside the interview navigation context', async () => {
    const questionSetId = '00000000-0000-4000-8000-000000000099'
    const { wrapper } = await mountLayout(`/interview-question-sets/${questionSetId}`)

    expect(wrapper.get('.desktop-navigation__link[aria-current="page"]').text()).toBe('면접 준비')
    expect(wrapper.get('.mobile-bottom-navigation__item[aria-current="page"]').text()).toBe(
      '면접 준비',
    )
    wrapper.unmount()
  })

  it('updates the nickname from the header modal and returns focus to the trigger', async () => {
    const { wrapper, authStore } = await mountLayout('/dashboard')
    const trigger = wrapper.get<HTMLButtonElement>('.account-trigger')
    const updateDisplayName = vi
      .spyOn(authStore, 'updateDisplayName')
      .mockImplementation(async (request) => {
        const updated = { ...authStore.currentUser!, displayName: request.displayName }
        authStore.currentUser = updated
        return updated
      })

    trigger.element.focus()
    await trigger.trigger('click')
    await nextTick()
    const nicknameCommand = document.body.querySelector<HTMLButtonElement>(
      '.account-menu button[role="menuitem"]',
    )
    if (nicknameCommand === null) throw new Error('nickname command is missing')
    nicknameCommand.click()
    await flushPromises()

    const dialog = document.body.querySelector<HTMLElement>('.nickname-modal')
    const input = dialog?.querySelector<HTMLInputElement>('#nickname-modal-input')
    expect(dialog?.getAttribute('role')).toBe('dialog')
    expect(dialog?.getAttribute('aria-modal')).toBe('true')
    expect(document.activeElement).toBe(input)

    if (input === undefined || input === null) throw new Error('nickname input is missing')
    input.value = '  새 닉네임  '
    input.dispatchEvent(new Event('input', { bubbles: true }))
    dialog
      ?.querySelector<HTMLFormElement>('form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(updateDisplayName).toHaveBeenCalledWith({ displayName: '새 닉네임' })
    expect(document.body.querySelector('.nickname-modal')).toBeNull()
    expect(trigger.text()).toContain('새 닉네임')
    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
  })
})

async function mountLayout(path: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.$patch({
    status: 'authenticated',
    currentUser: {
      id: '00000000-0000-4000-8000-000000000001',
      email: 'tester@example.com',
      displayName: '테스터',
    },
  })

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: AppLayout,
        children: [
          {
            path: 'dashboard',
            component: DashboardPage,
            meta: { title: '대시보드' },
          },
          {
            path: 'profile/basic',
            component: ProfilePage,
            meta: { title: '내 지원 정보' },
          },
          { path: 'documents', component: DashboardPage },
          { path: 'jobs', component: DashboardPage },
          { path: 'cover-letters', component: DashboardPage },
          { path: 'interviews', component: DashboardPage },
          { path: 'interview-question-sets/:questionSetId', component: DashboardPage },
          { path: 'agent-runs', component: DashboardPage },
          { path: 'guide', component: DashboardPage },
          { path: 'onboarding', component: ProfilePage },
        ],
      },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(TestApp, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router],
      stubs: {
        AgentRunProgressDrawer: {
          template: '<button type="button">진행 작업 0</button>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router, authStore }
}
