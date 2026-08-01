import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import GuidePage from './GuidePage.vue'

const EmptyPage = { template: '<div />' }

describe('GuidePage', () => {
  it('explains the complete product journey with reusable, labelled product previews', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/guide', name: 'guide', component: GuidePage },
        { path: '/profile/basic', name: 'profile-basic', component: EmptyPage },
        { path: '/documents', name: 'documents', component: EmptyPage },
        { path: '/jobs/new', name: 'job-new', component: EmptyPage },
        { path: '/cover-letters', name: 'cover-letters', component: EmptyPage },
        { path: '/interviews', name: 'interviews', component: EmptyPage },
      ],
    })
    await router.push('/guide')
    await router.isReady()

    const wrapper = mount(GuidePage, { global: { plugins: [router] } })

    expect(wrapper.get('h1').text()).toBe('Hiresemble 이용 가이드')
    expect(wrapper.findAll('.guide-steps > li')).toHaveLength(5)
    expect(wrapper.findAll('.guide-preview')).toHaveLength(5)
    expect(
      wrapper.findAll('.guide-preview').every((preview) => preview.attributes('aria-label')),
    ).toBe(true)
    expect(wrapper.get('nav[aria-label="Hiresemble 이용 순서"] a').attributes('href')).toBe(
      '#guide-step-1',
    )
    expect(wrapper.get('a[href="/jobs/new"]').text()).toContain('공고부터 추가하기')
  })
})
