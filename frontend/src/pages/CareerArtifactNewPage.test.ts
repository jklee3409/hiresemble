import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

import CareerArtifactNewPage from './CareerArtifactNewPage.vue'

describe('CareerArtifactNewPage', () => {
  it('canonicalizes type and step while keeping private draft values out of the URL', async () => {
    const { wrapper, router } = await mountNew(
      '/career-artifacts/new?type=resume&step=99&email=private@example.com',
    )
    expect(router.currentRoute.value.fullPath).toBe('/career-artifacts/new')
    expect(wrapper.findComponent({ name: 'CareerArtifactGenerationForm' }).props()).toMatchObject({
      artifactType: null,
      initialStep: 1,
    })
  })

  it('replaces the wizard with the accepted artifact resource route', async () => {
    const { wrapper, router } = await mountNew('/career-artifacts/new?type=RESUME')
    wrapper.findComponent({ name: 'CareerArtifactGenerationForm' }).vm.$emit('submitted', {
      agentRunId: '00000000-0000-4000-8000-000000000002',
      status: 'QUEUED',
      resourceType: 'CAREER_ARTIFACT',
      resourceId: artifactId,
      replayed: true,
    })
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe(`/career-artifacts/${artifactId}`)
  })
})

async function mountNew(path: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).$patch({
    status: 'authenticated',
    currentUser: { id: 'user-1', email: 'tester@example.com', displayName: '테스터' },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/career-artifacts',
        name: 'career-artifacts',
        component: { template: '<p>list</p>' },
      },
      {
        path: '/career-artifacts/new',
        name: 'career-artifact-new',
        component: CareerArtifactNewPage,
      },
      {
        path: '/career-artifacts/:careerArtifactId',
        name: 'career-artifact-detail',
        component: { template: '<p>detail</p>' },
      },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CareerArtifactNewPage, {
    global: {
      plugins: [pinia, router],
      stubs: {
        CareerArtifactGenerationForm: {
          name: 'CareerArtifactGenerationForm',
          props: ['artifactType', 'initialStep', 'userId', 'displayName', 'email'],
          template: '<button type="button">wizard</button>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

const artifactId = '00000000-0000-4000-8000-000000000001'
