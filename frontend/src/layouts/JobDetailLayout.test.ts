import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { jobDetailFixture } from '@/features/jobs/testFixtures'
import * as jobApi from '@/shared/api/jobApi'
import { useAuthStore } from '@/stores/auth'

import JobDetailLayout from './JobDetailLayout.vue'

vi.mock('@/shared/api/jobApi', () => ({ getJob: vi.fn() }))

const TestApp = { template: '<RouterView />' }
const Child = { template: '<section>child</section>' }
const JOB_ID = '50000000-0000-4000-8000-000000000001'

describe('JobDetailLayout', () => {
  it('shows the P8 tabs with route-derived aria-current without exposing P9', async () => {
    vi.mocked(jobApi.getJob).mockResolvedValue(
      jobDetailFixture({ companyName: '모아테크', title: '백엔드 엔지니어' }),
    )
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore(pinia)
    auth.status = 'authenticated'
    auth.currentUser = { id: 'user-1', email: 'user@example.com', displayName: '테스터' }
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
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
            { path: 'interview', name: 'job-interview', component: Child },
          ],
        },
        { path: '/jobs', name: 'jobs', component: Child },
      ],
    })
    await router.push(`/jobs/${JOB_ID}/overview`)
    await router.isReady()
    const wrapper = mount(TestApp, {
      global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('백엔드 엔지니어')
    expect(wrapper.get('h1').classes()).toContain('job-resource-title')
    expect(wrapper.get('h1').attributes('tabindex')).toBeUndefined()
    expect(wrapper.get('h1').attributes('title')).toBe('백엔드 엔지니어')
    expect(wrapper.find('h1 span').exists()).toBe(false)

    const tabs = wrapper.get('nav[aria-label="공고 상세 탭"]')
    expect(tabs.findAll('a').map((link) => link.text())).toEqual([
      '공고 정보',
      '공고 분석',
      '자기소개서',
      '면접 준비',
    ])
    expect(tabs.get('a[aria-current="page"]').text()).toBe('공고 정보')
    expect(wrapper.get('.job-detail-body').classes()).toContain('job-detail-body')
    expect(wrapper.text()).not.toContain('모의 면접')

    await router.push(`/jobs/${JOB_ID}/analysis`)
    await flushPromises()
    expect(tabs.get('a[aria-current="page"]').text()).toBe('공고 분석')
    expect(tabs.get('a[aria-current="page"]').classes()).toContain('job-detail-tab--active')

    await router.push(`/jobs/${JOB_ID}/cover-letter`)
    await flushPromises()
    expect(tabs.get('a[aria-current="page"]').text()).toBe('자기소개서')

    await router.push(`/jobs/${JOB_ID}/interview`)
    await flushPromises()
    expect(tabs.get('a[aria-current="page"]').text()).toBe('면접 준비')
  })
})
