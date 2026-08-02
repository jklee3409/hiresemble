import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import LandingProductDemo, {
  LANDING_DEMO_SCENES,
  LANDING_DEMO_SCENE_DURATION_MS,
} from './LandingProductDemo.vue'

let intersectionCallback: IntersectionObserverCallback | undefined

class IntersectionObserverStub implements IntersectionObserver {
  readonly root = null
  readonly rootMargin = '0px'
  readonly thresholds = [0, 0.4, 0.7]
  disconnect = vi.fn()
  observe = vi.fn()
  takeRecords = vi.fn(() => [])
  unobserve = vi.fn()

  constructor(callback: IntersectionObserverCallback) {
    intersectionCallback = callback
  }
}

function installMotionPreference(matches: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({
      matches,
      media: '(prefers-reduced-motion: reduce)',
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    } satisfies MediaQueryList),
  )
}

function enterViewport(isIntersecting = true, intersectionRatio = 0.7): void {
  intersectionCallback?.(
    [
      {
        isIntersecting,
        intersectionRatio,
      } as IntersectionObserverEntry,
    ],
    {} as IntersectionObserver,
  )
}

async function sceneIndex(wrapper: VueWrapper): Promise<number> {
  await nextTick()
  return Number(wrapper.attributes('data-scene-index'))
}

describe('LandingProductDemo', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    intersectionCallback = undefined
    installMotionPreference(false)
    vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('uses only the implemented Hiresemble preparation flow and avoids live announcements', () => {
    expect(LANDING_DEMO_SCENES.map((scene) => scene.eyebrow)).toEqual([
      '경험 준비',
      '공고 등록',
      '자동 분석 진행',
      '분석 결과',
      '다음 준비',
    ])
    expect(LANDING_DEMO_SCENES.map((scene) => scene.description).join(' ')).not.toMatch(
      /합격 확률|모의 면접|성공률/,
    )

    const wrapper = mount(LandingProductDemo)

    expect(wrapper.find('[aria-live]').exists()).toBe(false)
    expect(wrapper.get('#landing-demo-description').text()).toContain(
      '자기소개서와 면접 질문 준비까지 이어가는 흐름',
    )
    wrapper.unmount()
  })

  it('advances automatically and loops from the last scene to the first', async () => {
    const wrapper = mount(LandingProductDemo)
    enterViewport()

    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS)
    expect(await sceneIndex(wrapper)).toBe(1)

    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS * 4)
    expect(await sceneIndex(wrapper)).toBe(0)
    wrapper.unmount()
  })

  it('stops offscreen and resumes without mapping scroll position to a scene', async () => {
    const wrapper = mount(LandingProductDemo)
    enterViewport()
    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS)
    expect(await sceneIndex(wrapper)).toBe(1)

    enterViewport(false, 0)
    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS * 2)
    expect(await sceneIndex(wrapper)).toBe(1)

    enterViewport()
    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS)
    expect(await sceneIndex(wrapper)).toBe(2)
    wrapper.unmount()
  })

  it('stops in a hidden document and resumes when the page is visible', async () => {
    let visibility: DocumentVisibilityState = 'visible'
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
    const wrapper = mount(LandingProductDemo)
    enterViewport()

    visibility = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS * 2)
    expect(await sceneIndex(wrapper)).toBe(0)

    visibility = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS)
    expect(await sceneIndex(wrapper)).toBe(1)
    wrapper.unmount()
  })

  it('falls back to autoplay when Intersection Observer is unavailable', async () => {
    Reflect.deleteProperty(window, 'IntersectionObserver')
    Reflect.deleteProperty(globalThis, 'IntersectionObserver')
    const wrapper = mount(LandingProductDemo)

    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS)
    expect(await sceneIndex(wrapper)).toBe(1)
    wrapper.unmount()
  })

  it('keeps a representative static scene when reduced motion is requested', async () => {
    installMotionPreference(true)
    const wrapper = mount(LandingProductDemo)

    await vi.advanceTimersByTimeAsync(LANDING_DEMO_SCENE_DURATION_MS * 3)
    expect(await sceneIndex(wrapper)).toBe(0)
    expect(wrapper.attributes('data-playback-state')).toBe('paused')
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
  })

  it('cleans the pending scene timer on unmount', async () => {
    const wrapper = mount(LandingProductDemo)
    enterViewport()
    await nextTick()
    expect(vi.getTimerCount()).toBe(1)

    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
