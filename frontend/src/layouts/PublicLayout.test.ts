import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'

import PublicLayout from './PublicLayout.vue'

const LoginForm = {
  template: '<form aria-label="로그인 폼"><label>이메일<input type="email"></label></form>',
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
    expect(wrapper.get('aside').attributes('aria-label')).toBe('Hiresemble 서비스 안내')
    expect(wrapper.text()).toContain('프로필, 증빙 문서, 채용 공고와 비동기 작업 기록')
    expect(wrapper.text()).toContain('가입 시 개인정보 처리와 AI 처리 동의를 직접 확인합니다.')
    expect(wrapper.text()).not.toContain('이용자 수')
    expect(wrapper.text()).not.toContain('기업 고객')
    wrapper.unmount()
  })
})
