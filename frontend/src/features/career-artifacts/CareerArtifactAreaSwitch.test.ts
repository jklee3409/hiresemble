import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'

import CareerArtifactAreaSwitch from './CareerArtifactAreaSwitch.vue'

describe('CareerArtifactAreaSwitch', () => {
  afterEach(() => {
    featureFlags.careerArtifactEnabled = false
    featureFlags.githubSourceEnabled = false
  })

  it('separates upload, integration, and generated areas and marks the current one', async () => {
    featureFlags.careerArtifactEnabled = true
    featureFlags.githubSourceEnabled = true
    const wrapper = await mountSwitch('/career-artifacts')

    expect(wrapper.findAll('a').map((link) => link.text())).toEqual([
      '자료 업로드',
      '외부 연동',
      'AI로 만든 초안',
    ])
    expect(wrapper.get('a[href="/career-artifacts"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('a[href="/documents"]').attributes('aria-current')).toBeUndefined()
  })

  it('marks the integration area from any of its child paths', async () => {
    featureFlags.careerArtifactEnabled = true
    featureFlags.githubSourceEnabled = true
    const wrapper = await mountSwitch('/integrations')

    expect(wrapper.get('a[href="/integrations"]').attributes('aria-current')).toBe('page')
  })

  it('hides an area whose gate is disabled', async () => {
    featureFlags.careerArtifactEnabled = true
    featureFlags.githubSourceEnabled = false
    const wrapper = await mountSwitch('/documents')

    expect(wrapper.findAll('a').map((link) => link.text())).toEqual([
      '자료 업로드',
      'AI로 만든 초안',
    ])
  })

  it('renders no switch when only the upload area is available', async () => {
    featureFlags.careerArtifactEnabled = false
    featureFlags.githubSourceEnabled = false
    const wrapper = await mountSwitch('/documents')

    expect(wrapper.html()).toBe('<!--v-if-->')
  })
})

async function mountSwitch(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/documents', component: { template: '<p>documents</p>' } },
      { path: '/integrations', component: { template: '<p>integrations</p>' } },
      { path: '/career-artifacts', component: { template: '<p>artifacts</p>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return mount(CareerArtifactAreaSwitch, { global: { plugins: [router] } })
}
