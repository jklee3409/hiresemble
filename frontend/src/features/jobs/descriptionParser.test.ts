import { describe, expect, it } from 'vitest'

import { parseJobDescription, parseJobDescriptionInline } from './descriptionParser'

describe('parseJobDescription', () => {
  it('renders known headings, paragraphs, and consecutive list types deterministically', () => {
    expect(
      parseJobDescription(`모집 개요
백엔드 서비스를 함께 만들어요.

주요 업무
- Spring API 개발
• 장애 대응

전형 절차:
1. 서류 검토
2) 인터뷰`),
    ).toEqual([
      { type: 'heading', text: '모집 개요' },
      { type: 'paragraph', text: '백엔드 서비스를 함께 만들어요.' },
      { type: 'heading', text: '주요 업무' },
      { type: 'unordered-list', items: ['Spring API 개발', '장애 대응'] },
      { type: 'heading', text: '전형 절차' },
      { type: 'ordered-list', items: ['서류 검토', '인터뷰'] },
    ])
  })

  it('falls back to ordinary paragraphs without inventing headings', () => {
    expect(parseJobDescription('구분이 없는 긴 문장입니다.\n다음 줄도 같은 문단입니다.')).toEqual([
      { type: 'paragraph', text: '구분이 없는 긴 문장입니다. 다음 줄도 같은 문단입니다.' },
    ])
  })

  it('keeps links as safe presentation tokens instead of HTML', () => {
    expect(parseJobDescriptionInline('지원: https://example.com/jobs/1).')).toEqual([
      { type: 'text', value: '지원: ' },
      { type: 'link', value: 'https://example.com/jobs/1', href: 'https://example.com/jobs/1' },
      { type: 'text', value: ').' },
    ])
    expect(parseJobDescriptionInline('<script>alert(1)</script>')).toEqual([
      { type: 'text', value: '<script>alert(1)</script>' },
    ])
  })
})
