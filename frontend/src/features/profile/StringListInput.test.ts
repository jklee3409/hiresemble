import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import StringListInput from './StringListInput.vue'
import {
  DESIRED_LOCATION_PRESETS,
  DESIRED_ROLE_PRESETS,
  DESIRED_ROLE_SUGGESTIONS,
} from './preferenceOptions'

describe('StringListInput suggestions and presets', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows role suggestions containing the input and supports keyboard selection', async () => {
    const wrapper = mount(StringListInput, {
      attachTo: document.body,
      props: {
        id: 'desired-role',
        label: '희망 직무',
        modelValue: [],
        presets: DESIRED_ROLE_PRESETS,
        suggestions: DESIRED_ROLE_SUGGESTIONS,
      },
    })

    const input = wrapper.get('input')
    await input.setValue('프론트')

    const suggestions = wrapper.get('[aria-label="희망 직무 추천"]')
    expect(suggestions.text()).toContain('프론트엔드 개발자')
    expect(suggestions.text()).not.toContain('백엔드 개발자')

    await input.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement?.textContent).toContain('프론트엔드 개발자')
    await wrapper.get('[aria-label="희망 직무 추천"] button').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['프론트엔드 개발자']])
    wrapper.unmount()
  })

  it('moves through every matching suggestion with arrow keys and returns to the input', async () => {
    const wrapper = mount(StringListInput, {
      attachTo: document.body,
      props: {
        id: 'desired-role-navigation',
        label: '희망 직무',
        modelValue: [],
        suggestions: DESIRED_ROLE_SUGGESTIONS,
      },
    })

    const input = wrapper.get('input')
    await input.setValue('개발')
    await input.trigger('keydown', { key: 'ArrowDown' })
    const options = wrapper.findAll('[role="option"]')
    expect(options.length).toBeGreaterThan(1)
    expect(document.activeElement).toBe(options[0]?.element)

    await options[0]?.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(options[1]?.element)
    await options[1]?.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(options[0]?.element)
    await options[0]?.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(input.element)

    wrapper.unmount()
  })

  it('adds a Korean region preset while preserving direct free-text entry', async () => {
    const wrapper = mount(StringListInput, {
      props: {
        id: 'desired-location',
        label: '희망 지역',
        modelValue: [],
        presets: DESIRED_LOCATION_PRESETS,
      },
    })

    const preset = wrapper
      .findAll('.string-list__presets button')
      .find((button) => button.text().includes('서울'))
    await preset?.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['서울']])

    await wrapper.setProps({ modelValue: ['서울'] })
    await wrapper.get('input').setValue('판교')
    await wrapper.get('input').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['서울', '판교']])
  })

  it('shows only a concise preset set until the user expands it', async () => {
    const wrapper = mount(StringListInput, {
      props: {
        id: 'desired-role-presets',
        label: '희망 직무',
        modelValue: [],
        presets: DESIRED_ROLE_PRESETS,
        suggestions: DESIRED_ROLE_SUGGESTIONS,
      },
    })

    expect(wrapper.findAll('.string-list__presets > div:last-child > button')).toHaveLength(4)
    const toggle = wrapper.get('.string-list__preset-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
  })

  it('prevents case-insensitive duplicates and enforces the ten-item maximum', async () => {
    const values = Array.from({ length: 10 }, (_, index) => `항목 ${index + 1}`)
    const wrapper = mount(StringListInput, {
      props: {
        id: 'desired-role-limit',
        label: '희망 직무',
        modelValue: ['Frontend'],
      },
    })

    await wrapper.get('input').setValue('frontend')
    await wrapper.get('input').trigger('keydown', { key: 'Enter' })
    expect(wrapper.text()).toContain('이미 추가한 항목이에요.')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await wrapper.setProps({ modelValue: values })
    expect(wrapper.get('.string-list__input-row button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('현재 10개')
  })
})
