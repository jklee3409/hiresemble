import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PaginationNav from './PaginationNav.vue'
import StatePanel from './StatePanel.vue'
import StatusBadge from './StatusBadge.vue'

describe('shared UI state components', () => {
  it('renders a semantic status as text instead of relying on color alone', () => {
    const wrapper = mount(StatusBadge, {
      props: { prefix: '근거', label: '추출 실패', tone: 'danger' },
    })

    expect(wrapper.text()).toBe('근거 · 추출 실패')
    expect(wrapper.classes()).toContain('status-badge--danger')
  })

  it('announces loading and error states with suitable live semantics', () => {
    const loading = mount(StatePanel, {
      props: { kind: 'loading', title: '문서를 불러오는 중…' },
    })
    expect(loading.attributes('role')).toBe('status')
    expect(loading.attributes('aria-live')).toBe('polite')
    expect(loading.get('.skeleton-stack').attributes('aria-hidden')).toBe('true')

    const error = mount(StatePanel, {
      props: {
        kind: 'error',
        title: '문서를 불러오지 못했습니다.',
        description: '연결 상태를 확인해 주세요.',
      },
      slots: {
        actions: '<button type="button">다시 시도</button>',
      },
    })
    expect(error.attributes('role')).toBe('alert')
    expect(error.text()).toContain('연결 상태를 확인해 주세요.')
    expect(error.get('button').text()).toBe('다시 시도')
  })

  it('keeps pagination controls named and bounded', async () => {
    const wrapper = mount(PaginationNav, {
      props: { page: 0, totalPages: 3, label: '문서 페이지' },
    })

    expect(wrapper.attributes('aria-label')).toBe('문서 페이지')
    expect(wrapper.get('button:first-of-type').attributes('disabled')).toBeDefined()
    await wrapper.get('button:last-of-type').trigger('click')
    expect(wrapper.emitted('change')).toEqual([[1]])
  })
})
