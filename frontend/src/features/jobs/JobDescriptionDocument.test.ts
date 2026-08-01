import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import JobDescriptionDocument from './JobDescriptionDocument.vue'

describe('JobDescriptionDocument', () => {
  it('uses semantic headings and lists without interpreting source text as HTML', () => {
    const wrapper = mount(JobDescriptionDocument, {
      props: {
        source: `주요 업무
- Spring API 개발
- <script>alert('unsafe')</script>

지원 방법
https://example.com/jobs/1`,
      },
    })

    expect(wrapper.findAll('h3').map((heading) => heading.text())).toEqual([
      '주요 업무',
      '지원 방법',
    ])
    expect(wrapper.findAll('ul li')).toHaveLength(2)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain("<script>alert('unsafe')</script>")
    expect(wrapper.get('a').attributes()).toMatchObject({
      href: 'https://example.com/jobs/1',
      rel: 'noopener noreferrer',
      target: '_blank',
    })
  })

  it('collapses only very long documents in the page flow and exposes an accessible toggle', async () => {
    const wrapper = mount(JobDescriptionDocument, {
      props: { source: '공고 설명 문장입니다. '.repeat(500) },
    })

    const toggle = wrapper.get('button')
    expect(toggle.text()).toBe('공고 본문 전체 보기')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.job-document__content').classes()).toContain(
      'job-document__content--collapsed',
    )

    await toggle.trigger('click')
    expect(toggle.text()).toBe('본문 접기')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.job-document__content').classes()).not.toContain(
      'job-document__content--collapsed',
    )
  })
})
