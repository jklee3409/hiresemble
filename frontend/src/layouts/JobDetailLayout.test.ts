import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import JobDetailLayout from './JobDetailLayout.vue'

const TestApp = { template: '<RouterView />' }
const Child = { template: '<section>child</section>' }
const JOB_ID = '50000000-0000-4000-8000-000000000001'

describe('JobDetailLayout', () => {
  it('shows the P7 tabs with route-derived aria-current without exposing P8', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/jobs/:jobId',
          component: JobDetailLayout,
          children: [
            { path: 'overview', name: 'job-overview', component: Child },
            { path: 'analysis', name: 'job-analysis', component: Child },
            { path: 'cover-letter', name: 'job-cover-letter', component: Child },
          ],
        },
        { path: '/jobs', name: 'jobs', component: Child },
      ],
    })
    await router.push(`/jobs/${JOB_ID}/overview`)
    await router.isReady()
    const wrapper = mount(TestApp, { global: { plugins: [router] } })

    const tabs = wrapper.get('nav[aria-label="공고 상세 탭"]')
    expect(tabs.findAll('a').map((link) => link.text())).toEqual([
      '공고 정보',
      '공고 분석',
      '자기소개서',
    ])
    expect(tabs.get('a[aria-current="page"]').text()).toBe('공고 정보')
    expect(wrapper.text()).not.toContain('면접 준비')

    await router.push(`/jobs/${JOB_ID}/analysis`)
    await flushPromises()
    expect(tabs.get('a[aria-current="page"]').text()).toBe('공고 분석')
    expect(tabs.get('a[aria-current="page"]').classes()).toContain('job-detail-tab--active')

    await router.push(`/jobs/${JOB_ID}/cover-letter`)
    await flushPromises()
    expect(tabs.get('a[aria-current="page"]').text()).toBe('자기소개서')
  })
})
