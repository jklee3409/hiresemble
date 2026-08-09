import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { featureFlags } from '@/app/featureFlags'
import { appSelectLabel, selectAppOption } from '@/shared/ui/appSelectTesting'
import ProfileTabs from './ProfileTabs.vue'

const PROFILE_PATHS = [
  '/profile/basic',
  '/profile/education',
  '/profile/careers',
  '/profile/certifications',
  '/profile/languages',
  '/profile/awards',
  '/profile/activities',
  '/profile/experiences',
]

describe('Career Profile Workspace navigation', () => {
  it('keeps every deep link in a vertical outline and exposes one mobile selector', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: PROFILE_PATHS.map((path) => ({ path, component: { template: '<div />' } })).concat({
        path: '/integrations',
        component: { template: '<div />' },
      }),
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
      '경험 보관함',
    ])
    expect(wrapper.find('small').exists()).toBe(false)
    expect(wrapper.get('.profile-outline__link[aria-current="page"]').text()).toContain('경력')
    expect(appSelectLabel(wrapper, '프로필 항목 선택')).toContain('경력')

    await selectAppOption(wrapper, '프로필 항목 선택', '어학')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/profile/languages')
  })

  it('never offers the GitHub integration here even when Gate 2 is enabled', async () => {
    featureFlags.githubSourceEnabled = true
    const router = createRouter({
      history: createMemoryHistory(),
      routes: PROFILE_PATHS.map((path) => ({ path, component: { template: '<div />' } })).concat({
        path: '/integrations',
        component: { template: '<div />' },
      }),
    })
    await router.push('/profile/basic')
    await router.isReady()
    const wrapper = mount(ProfileTabs, { global: { plugins: [router] } })

    expect(wrapper.findAll('.profile-outline__link').map((link) => link.text())).not.toContain(
      'GitHub',
    )
    expect(wrapper.html()).not.toContain('/integrations')
    featureFlags.githubSourceEnabled = false
  })
})
