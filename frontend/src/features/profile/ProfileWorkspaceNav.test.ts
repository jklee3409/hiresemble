import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import ProfileTabs from './ProfileTabs.vue'

describe('Career Profile Workspace navigation', () => {
  it('keeps every deep link in a vertical outline and exposes one mobile selector', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        '/profile/basic',
        '/profile/education',
        '/profile/careers',
        '/profile/certifications',
        '/profile/languages',
        '/profile/awards',
        '/profile/activities',
      ].map((path) => ({ path, component: { template: '<div />' } })),
    })
    await router.push('/profile/careers')
    await router.isReady()

    const wrapper = mount(ProfileTabs, {
      global: { plugins: [router] },
    })

    expect(wrapper.find('.profile-tabs').exists()).toBe(false)
    expect(wrapper.findAll('.profile-outline__link').map((link) => link.text())).toEqual([
      '기본 정보',
      '학력',
      '경력현재',
      '자격증',
      '어학',
      '수상',
      '대외활동',
    ])
    expect(wrapper.find('small').exists()).toBe(false)
    expect(wrapper.get('.profile-outline__link[aria-current="page"]').text()).toContain('경력')
    expect(
      (wrapper.get('select[aria-label="프로필 항목 선택"]').element as HTMLSelectElement).value,
    ).toBe('/profile/careers')

    await wrapper.get('select[aria-label="프로필 항목 선택"]').setValue('/profile/languages')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/profile/languages')
  })
})
