import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LandingPage from './LandingPage.vue'

describe('LandingPage', () => {
  it('presents the public product journey without protected or placeholder links', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: LandingPage },
        { path: '/login', component: { template: '<div />' } },
        { path: '/signup', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(LandingPage, { global: { plugins: [router] } })
    const hrefs = wrapper.findAll('a').map((link) => link.attributes('href'))

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toContain('흩어진 취업 준비를,')
    expect(wrapper.text()).toContain('이력서와 포트폴리오에서 경험을 정리하고')
    expect(wrapper.findAll('main, header, footer')).toHaveLength(3)
    expect(wrapper.get('nav[aria-label="서비스 소개 탐색"]')).toBeTruthy()
    expect(wrapper.findAll('.journey-list > li')).toHaveLength(5)
    expect(wrapper.text()).toContain('내 정보와 경험 정리')
    expect(wrapper.text()).toContain('면접 질문과 피드백')
    expect(wrapper.text()).toContain('AI 활용 원칙')
    expect(wrapper.text()).toContain('적합도는 합격 확률이 아니에요.')
    expect(wrapper.text()).toContain('이력서 등록 완료')
    expect(wrapper.text()).toContain('공고 내용을 읽는 중')
    expect(wrapper.text()).toContain('자기소개서 준비 가능')
    expect(hrefs.filter((href) => href === '/signup').length).toBeGreaterThanOrEqual(2)
    expect(hrefs.filter((href) => href === '/login').length).toBeGreaterThanOrEqual(2)
    expect(hrefs).toEqual(expect.arrayContaining(['#service-intro', '#journey', '#ai-principles']))
    expect(hrefs.some((href) => href === '#')).toBe(false)
    expect(
      hrefs.some((href) =>
        ['/profile/basic', '/documents', '/jobs', '/jobs/new', '/guide'].includes(href ?? ''),
      ),
    ).toBe(false)
    expect(wrapper.text()).not.toMatch(/무료|평생|사용자 수|고객 후기|별점|합격률/)
  })
})
