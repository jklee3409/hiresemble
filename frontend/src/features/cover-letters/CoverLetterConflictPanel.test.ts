import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CoverLetterConflictPanel from './CoverLetterConflictPanel.vue'

describe('CoverLetterConflictPanel', () => {
  it('keeps the latest server snapshot and local draft visible until the user chooses', async () => {
    const wrapper = mount(CoverLetterConflictPanel, {
      props: {
        conflict: {
          kind: 'ANSWER',
          errorCode: 'VERSION_CONFLICT',
          serverSnapshot: '서버의 최신 답변',
          localDraft: '내가 저장하지 않은 답변',
        },
      },
    })

    expect(wrapper.attributes('role')).toBe('alertdialog')
    expect(wrapper.text()).toContain('현재 답변 버전이 달라졌어요.')
    expect(wrapper.text()).toContain('서버의 최신 답변')
    expect(wrapper.text()).toContain('내가 저장하지 않은 답변')

    await wrapper.get('button.button--primary').trigger('click')
    expect(wrapper.emitted('reapply')).toHaveLength(1)
    await wrapper.get('button.button--secondary').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('prevents duplicate reapply while a conflict retry is pending', () => {
    const wrapper = mount(CoverLetterConflictPanel, {
      props: {
        conflict: {
          kind: 'ACTIVE_EXISTS',
          errorCode: 'ACTIVE_COVER_LETTER_EXISTS',
          serverSnapshot: '이미 활성 자기소개서가 있습니다.',
          localDraft: '새 자기소개서 만들기',
        },
        reapplying: true,
      },
    })

    expect(wrapper.get('button.button--primary').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button.button--primary').text()).toBe('재적용 중…')
  })
})
