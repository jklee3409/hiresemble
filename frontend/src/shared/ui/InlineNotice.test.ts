import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import InlineNotice from './InlineNotice.vue'

describe('InlineNotice', () => {
  it('shows the title and description without filling the whole surface with the status colour', () => {
    const wrapper = mount(InlineNotice, {
      props: { title: '작업을 마치지 못했어요', description: '잠시 후 다시 시도해 주세요.' },
    })

    expect(wrapper.text()).toContain('작업을 마치지 못했어요')
    expect(wrapper.text()).toContain('잠시 후 다시 시도해 주세요.')
    // `.alert`는 면 전체를 상태색으로 채운다. 이 component는 그 표면을 쓰지 않는다.
    expect(wrapper.get('.inline-notice').classes()).not.toContain('alert')
  })

  it('carries the tone on the icon only', () => {
    const wrapper = mount(InlineNotice, { props: { title: '확인이 필요해요', tone: 'danger' } })

    expect(wrapper.get('.inline-notice').classes()).toContain('inline-notice--danger')
    expect(wrapper.find('.inline-notice__icon').exists()).toBe(true)
  })

  it('omits the description paragraph when there is nothing to add', () => {
    const wrapper = mount(InlineNotice, { props: { title: '제목만' } })

    expect(wrapper.find('.inline-notice__description').exists()).toBe(false)
  })

  it('announces urgent notices to assistive technology only when asked', () => {
    expect(
      mount(InlineNotice, { props: { title: '기본' } })
        .get('.inline-notice')
        .attributes('role'),
    ).toBe('status')
    expect(
      mount(InlineNotice, { props: { title: '오류', role: 'alert' } })
        .get('.inline-notice')
        .attributes('role'),
    ).toBe('alert')
  })

  it('renders action slot content beside the message', () => {
    const wrapper = mount(InlineNotice, {
      props: { title: '다시 시도할 수 있어요' },
      slots: { actions: '<button type="button">다시 시도</button>' },
    })

    expect(wrapper.get('.inline-notice__actions').text()).toBe('다시 시도')
  })
})
