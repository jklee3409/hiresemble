import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'

import PublicLayout from './PublicLayout.vue'

const LoginForm = {
  template:
    '<form aria-label="로그인 폼"><h1>로그인</h1><label>이메일<input type="email"></label></form>',
}
const TestApp = {
  template: '<RouterView />',
}

describe('PublicLayout', () => {
  afterEach(() => document.body.replaceChildren())

  it('keeps the authentication form primary and provides concrete privacy and service context', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/',
          component: PublicLayout,
          children: [{ path: 'login', component: LoginForm }],
        },
      ],
    })
    await router.push('/login')
    await router.isReady()

    const wrapper = mount(TestApp, {
      attachTo: document.body,
      global: { plugins: [router] },
    })

    expect(wrapper.get('[data-testid="public-layout"]').element.tagName).toBe('MAIN')
    expect(wrapper.get('[data-testid="public-auth-panel"]').attributes('aria-label')).toBe('인증')
    expect(wrapper.get('form').attributes('aria-label')).toBe('로그인 폼')
    expect(
      wrapper
        .get('[data-testid="public-auth-panel"]')
        .element.compareDocumentPosition(
          wrapper.get('section[aria-label="Hiresemble 서비스 안내"]').element,
        ) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(wrapper.findAll('h1, h2').map((heading) => heading.element.tagName)).toEqual([
      'H1',
      'H2',
    ])
    expect(
      wrapper.get('section[aria-label="Hiresemble 서비스 안내"]').attributes('aria-label'),
    ).toBe('Hiresemble 서비스 안내')
    expect(wrapper.text()).toContain('흩어진 경험을 모아')
    expect(wrapper.text()).toContain('프로필부터 이력서, 관심 공고까지')
    expect(wrapper.text()).toContain('개인정보와 AI 처리 동의를 직접 확인할 수 있어요.')
    expect(wrapper.findAll('[data-testid="brand-mark"]')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('비동기')
    expect(wrapper.text()).not.toContain('Career workspace')
    expect(wrapper.text()).not.toContain('이용자 수')
    expect(wrapper.text()).not.toContain('기업 고객')
    wrapper.unmount()
  })
})
