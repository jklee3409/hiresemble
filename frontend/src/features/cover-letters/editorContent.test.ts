import { describe, expect, it } from 'vitest'

import { canonicalizeEditorContent, plainTextToTipTap, sameTipTapContent } from './editorContent'

describe('P7 TipTap canonical content', () => {
  it('mirrors server newline, NFC, NBSP, zero-width and Unicode code-point counting', () => {
    const result = canonicalizeEditorContent({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: `e\u0301\u00a0경험\u200b😀`,
              marks: [{ type: 'italic' }, { type: 'bold' }],
            },
          ],
        },
        {
          type: 'paragraph',
          content: [{ type: 'text', text: '둘째\r\n줄' }],
        },
      ],
    })

    expect(result.plainText).toBe('é 경험😀\n둘째\n줄')
    expect(result.characterCount).toBe(Array.from('é 경험😀\n둘째\n줄').length)
    expect(result.document.content[0]?.content[0]?.marks).toEqual([
      { type: 'bold' },
      { type: 'italic' },
    ])
  })

  it('preserves hard breaks and list item boundaries', () => {
    const result = canonicalizeEditorContent({
      type: 'doc',
      content: [
        {
          type: 'bulletList',
          content: [
            {
              type: 'listItem',
              content: [
                {
                  type: 'paragraph',
                  content: [
                    { type: 'text', text: '하나' },
                    { type: 'hardBreak' },
                    { type: 'text', text: '계속' },
                  ],
                },
              ],
            },
            {
              type: 'listItem',
              content: [
                {
                  type: 'paragraph',
                  content: [{ type: 'text', text: '둘' }],
                },
              ],
            },
          ],
        },
      ],
    })
    expect(result.plainText).toBe('하나\n계속\n둘')
  })

  it('rejects raw HTML, link marks, duplicate marks and malformed list children', () => {
    for (const value of [
      { type: 'doc', content: [{ type: 'html', text: '<script />' }] },
      {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            content: [{ type: 'text', text: 'x', marks: [{ type: 'link' }] }],
          },
        ],
      },
      {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            content: [
              {
                type: 'text',
                text: 'x',
                marks: [{ type: 'bold' }, { type: 'bold' }],
              },
            ],
          },
        ],
      },
      {
        type: 'doc',
        content: [
          {
            type: 'bulletList',
            content: [{ type: 'paragraph', content: [] }],
          },
        ],
      },
    ]) {
      expect(() => canonicalizeEditorContent(value)).toThrow('INVALID_TIPTAP_CONTENT')
    }
  })

  it('converts plain text without adding unsupported nodes and compares canonical JSON', () => {
    const document = plainTextToTipTap('첫 줄\n둘째 줄')
    expect(document.content.map((node) => node.type)).toEqual(['paragraph', 'paragraph'])
    expect(sameTipTapContent(document, structuredClone(document))).toBe(true)
  })
})
