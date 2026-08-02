import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LandingPage from './LandingPage.vue'

async function mountLandingPage() {
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
  return mount(LandingPage, { global: { plugins: [router] } })
}

describe('LandingPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('presents the public product journey without protected or placeholder links', async () => {
    const wrapper = await mountLandingPage()
    const hrefs = wrapper.findAll('a').map((link) => link.attributes('href'))

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toContain('흩어진 취업 준비를,')
    expect(wrapper.findAll('h1 > span').map((line) => line.text())).toEqual([
      '흩어진 취업 준비를,',
      '하나의 흐름으로.',
    ])
    expect(
      Array.from(wrapper.get('.landing-hero__inner').element.children).map(
        (element) => element.className,
      ),
    ).toEqual(['landing-hero__heading', 'landing-hero__body'])
    expect(wrapper.text()).toContain('이력서와 포트폴리오에서 경험을 정리하고')
    expect(wrapper.findAll('main, header, footer')).toHaveLength(3)
    expect(wrapper.get('nav[aria-label="서비스 소개 탐색"]')).toBeTruthy()
    expect(wrapper.findAll('.journey-list > li')).toHaveLength(5)
    expect(wrapper.text()).toContain('내 정보와 경험 정리')
    expect(wrapper.text()).toContain('면접 질문과 피드백')
    expect(wrapper.text()).toContain('AI 활용 원칙')
    expect(wrapper.text()).toContain('적합도는 합격 확률이 아니에요.')
    expect(wrapper.text()).toContain('준비가 한곳에 쌓이지 않으면지원할 때마다 다시 정리해야 해요.')
    expect(wrapper.text()).toContain('지원 정보부터 면접 준비까지,다섯 단계로 이어져요.')
    expect(wrapper.text()).toContain('쌓아 온 경험이,다음 지원의 준비로 이어져요.')
    expect(wrapper.text()).toContain('AI가 찾아낸 경험도,사용자가 확인한 뒤에 활용해요.')
    expect(wrapper.text()).toContain('이력서에서 경험을 확인하고')
    expect(wrapper.text()).toContain('자동 분석한 뒤')
    expect(wrapper.text()).toContain('자기소개서와 면접 질문 준비까지 이어가는 흐름')
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
    wrapper.unmount()
  })

  it('keeps every section visible when reveal observation cannot initialize', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    )
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor() {
          throw new Error('observer unavailable')
        }
      },
    )

    const wrapper = await mountLandingPage()
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('[data-reveal-section]')).not.toHaveLength(0)
    expect(
      wrapper.findAll('[data-reveal-section]').every((section) => section.classes('is-revealed')),
    ).toBe(true)
    wrapper.unmount()
  })

  it('does not apply hidden reveal state when reduced motion is requested', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockReturnValue({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    )

    const wrapper = await mountLandingPage()
    await wrapper.vm.$nextTick()

    expect(wrapper.classes('motion-ready')).toBe(false)
    expect(wrapper.get('.landing-hero__inner').classes('is-revealed')).toBe(true)
    expect(wrapper.get('.landing-demo').attributes('data-scene-index')).toBe('0')
    wrapper.unmount()
  })
})
