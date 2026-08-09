import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppSelect, { type AppSelectOption } from './AppSelect.vue'

const options: AppSelectOption[] = [
  { value: 'updatedAt,desc', label: '최근 수정순' },
  { value: 'createdAt,desc', label: '최근 등록순' },
  { value: 'dueAt,asc', label: '마감 임박순', disabled: true },
]

function mountSelect(modelValue = 'updatedAt,desc') {
  return mount(AppSelect, {
    props: { modelValue, options, ariaLabel: '정렬' },
    attachTo: document.body,
  })
}

describe('AppSelect', () => {
  it('shows the selected label and stays closed until activated', () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('[role="combobox"]')

    expect(trigger.text()).toContain('최근 수정순')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })

  it('falls back to the placeholder when no option matches the value', () => {
    const wrapper = mount(AppSelect, {
      props: { modelValue: '', options, placeholder: '전체' },
    })

    expect(wrapper.get('[role="combobox"]').text()).toContain('전체')
  })

  it('emits the chosen value once and closes the list', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[role="combobox"]').trigger('click')

    const items = wrapper.findAll('[role="option"]')
    expect(items).toHaveLength(3)
    await items[1].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['createdAt,desc']])
    expect(wrapper.emitted('change')).toEqual([['createdAt,desc']])
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })

  it('does not emit when the already selected option is chosen again', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[role="combobox"]').trigger('click')
    await wrapper.findAll('[role="option"]')[0].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('ignores disabled options on click and while moving with the keyboard', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await wrapper.findAll('[role="option"]')[2].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['createdAt,desc']])
  })

  it('opens with ArrowDown and exposes the active option to assistive technology', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('[role="combobox"]')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-activedescendant')).toBe(
      wrapper.findAll('[role="option"]')[0].attributes('id'),
    )

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(trigger.attributes('aria-activedescendant')).toBe(
      wrapper.findAll('[role="option"]')[1].attributes('id'),
    )
  })

  it('closes on Escape without changing the value', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('[role="combobox"]')
    await trigger.trigger('click')
    await trigger.trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('selects by typing the first letters while closed', async () => {
    const wrapper = mount(AppSelect, {
      props: {
        modelValue: 'a',
        options: [
          { value: 'a', label: 'Alpha' },
          { value: 'b', label: 'Bravo' },
        ],
      },
    })

    await wrapper.get('[role="combobox"]').trigger('keydown', { key: 'b' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['b']])
  })

  it('marks the current value as selected and never renders a native select', async () => {
    const wrapper = mountSelect('createdAt,desc')
    await wrapper.get('[role="combobox"]').trigger('click')

    const selected = wrapper
      .findAll('[role="option"]')
      .filter((option) => option.attributes('aria-selected') === 'true')
    expect(selected).toHaveLength(1)
    expect(selected[0].text()).toContain('최근 등록순')
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('cannot be opened while disabled', async () => {
    const wrapper = mount(AppSelect, {
      props: { modelValue: 'updatedAt,desc', options, disabled: true },
    })

    await wrapper.get('[role="combobox"]').trigger('click')
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })
})
