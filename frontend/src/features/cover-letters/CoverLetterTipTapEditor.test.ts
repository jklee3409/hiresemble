import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import CoverLetterTipTapEditor from './CoverLetterTipTapEditor.vue'
import { tipTapDocument } from './testFixtures'

describe('CoverLetterTipTapEditor', () => {
  it('exposes only the canonical P7 toolbar and emits a canonical draft with Unicode count', async () => {
    const wrapper = mount(CoverLetterTipTapEditor, {
      props: {
        content: tipTapDocument('초안'),
        maxLength: 10,
        serverCharacterCount: null,
      },
    })

    expect(
      wrapper
        .get('[role="toolbar"]')
        .findAll('button')
        .map((button) => button.text()),
    ).toEqual(['굵게', '기울임', '글머리 목록', '번호 목록'])
    expect(wrapper.text()).not.toContain('링크')
    expect(wrapper.text()).not.toContain('이미지')
    expect(wrapper.get('[aria-label="답변 글자 수"]').text()).toBe('2 / 10자')

    const exposed = wrapper.vm as unknown as { insertSuggestion(value: string): void }
    exposed.insertSuggestion('😀')
    await nextTick()

    const update = wrapper.emitted('update')?.at(-1)
    expect(update).toBeDefined()
    expect(update?.[1]).toBe(3)
    expect(wrapper.get('[aria-label="답변 글자 수"]').text()).toBe('3 / 10자')
    wrapper.unmount()
  })

  it('uses the server count when supplied and disables every editing action when archived', () => {
    const wrapper = mount(CoverLetterTipTapEditor, {
      props: {
        content: tipTapDocument('로컬 미리보기'),
        readonly: true,
        maxLength: 5,
        serverCharacterCount: 7,
      },
    })

    expect(wrapper.text()).toContain('읽기 전용')
    expect(wrapper.get('[aria-label="답변 글자 수"]').text()).toBe('7 / 5자')
    expect(wrapper.get('[aria-label="답변 글자 수"]').classes()).toContain(
      'cover-tiptap__count--over',
    )
    expect(
      wrapper
        .get('[role="toolbar"]')
        .findAll('button')
        .every((button) => button.attributes('disabled') !== undefined),
    ).toBe(true)
    wrapper.unmount()
  })
})
