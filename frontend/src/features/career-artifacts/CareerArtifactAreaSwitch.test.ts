import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'

import CareerArtifactAreaSwitch from './CareerArtifactAreaSwitch.vue'

describe('CareerArtifactAreaSwitch', () => {
  afterEach(() => {
    featureFlags.careerArtifactEnabled = false
  })

  it('separates uploaded documents from generated artifacts and marks the current area', async () => {
    featureFlags.careerArtifactEnabled = true
    const router = testRouter()
    await router.push('/career-artifacts')
    await router.isReady()
    const wrapper = mount(CareerArtifactAreaSwitch, { global: { plugins: [router] } })
    expect(wrapper.findAll('a')).toHaveLength(2)
    expect(wrapper.get('a[href="/documents"]').text()).toBe('업로드한 자료')
    expect(wrapper.get('a[href="/career-artifacts"]').attributes('aria-current')).toBe('page')
  })

  it('renders no switch while Gate 4 is disabled', async () => {
    featureFlags.careerArtifactEnabled = false
    const router = testRouter()
    await router.push('/documents')
    await router.isReady()
    const wrapper = mount(CareerArtifactAreaSwitch, { global: { plugins: [router] } })
    expect(wrapper.html()).toBe('<!--v-if-->')
  })
})

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/documents', component: { template: '<p>documents</p>' } },
      { path: '/career-artifacts', component: { template: '<p>artifacts</p>' } },
    ],
  })
}
