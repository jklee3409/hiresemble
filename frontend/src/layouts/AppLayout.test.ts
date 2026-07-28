import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'

import AppLayout from './AppLayout.vue'
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

  it('marks the current primary navigation item without exposing future menus', async () => {
    const { wrapper } = await mountLayout('/profile/basic')

    const activeDesktopLink = wrapper.get('.sidebar-nav__link[aria-current="page"]')
    expect(activeDesktopLink.text()).toBe('내 지원 정보')
    expect(wrapper.get('.workspace-title h1').text()).toBe('내 지원 정보')
    expect(wrapper.findAll('.sidebar-nav__link').map((link) => link.text())).toEqual([
      '오늘의 준비',
      '내 지원 정보',
      '이력서·자료',
      '관심 공고',
      '분석 기록',
    ])
    expect(wrapper.text()).not.toContain('자기소개서')
    expect(wrapper.text()).not.toContain('면접 준비')
    wrapper.unmount()
  })

  it('opens and closes the mobile navigation with accessible state and focus return', async () => {
    const { wrapper } = await mountLayout('/dashboard')
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-label="주요 메뉴 열기"]')

    trigger.element.focus()
    await trigger.trigger('click')
    await nextTick()

    expect(trigger.attributes('aria-expanded')).toBe('true')
    const drawer = document.body.querySelector<HTMLElement>('#mobile-navigation')
    expect(drawer).not.toBeNull()
    expect(drawer?.getAttribute('role')).toBe('dialog')
    expect(drawer?.getAttribute('aria-modal')).toBe('true')
    expect(document.body.style.overflow).toBe('hidden')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()

    expect(document.body.querySelector('#mobile-navigation')).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
    expect(document.body.style.overflow).toBe('')
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
          { path: 'agent-runs', component: DashboardPage },
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
  return { wrapper, router }
}
