import type {
  TipTapDocumentDto,
  TipTapMarkDto,
  TipTapNodeDto,
} from '@/shared/api/coverLetterContracts'

const CONTAINERS = new Set(['paragraph', 'bulletList', 'orderedList', 'listItem'])
const NODE_TYPES = new Set([
  'paragraph',
  'text',
  'hardBreak',
  'bulletList',
  'orderedList',
  'listItem',
])
const MARK_TYPES = new Set(['bold', 'italic'])
const ZERO_WIDTH = /(?:\u200B|\u200C|\u200D|\u2060|\uFEFF)/gu

export const EMPTY_TIPTAP_DOCUMENT: TipTapDocumentDto = {
  type: 'doc',
  content: [{ type: 'paragraph', text: null, marks: [], content: [] }],
}

export interface CanonicalEditorContent {
  document: TipTapDocumentDto
  plainText: string
  characterCount: number
}

export function canonicalizeEditorContent(value: unknown): CanonicalEditorContent {
  if (!isRecord(value) || value.type !== 'doc' || !Array.isArray(value.content)) {
    throw new Error('INVALID_TIPTAP_CONTENT')
  }
  if (value.content.length > 1_000) throw new Error('INVALID_TIPTAP_CONTENT')

  const output: string[] = []
  const content = canonicalizeChildren(value.content, output, true, 0)
  const plainText = output.join('')
  const characterCount = Array.from(plainText).length
  if (characterCount > 20_000) throw new Error('INVALID_TIPTAP_CONTENT')
  return { document: { type: 'doc', content }, plainText, characterCount }
}

export function plainTextToTipTap(value: string): TipTapDocumentDto {
  const normalized = normalizeText(value)
  if (normalized.length === 0) return structuredClone(EMPTY_TIPTAP_DOCUMENT)
  return {
    type: 'doc',
    content: normalized.split('\n').map((line) => ({
      type: 'paragraph',
      text: null,
      marks: [],
      content: line.length === 0 ? [] : [{ type: 'text', text: line, marks: [], content: [] }],
    })),
  }
}

export function sameTipTapContent(left: TipTapDocumentDto, right: TipTapDocumentDto): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

function canonicalizeChildren(
  values: unknown[],
  output: string[],
  blockBoundary: boolean,
  depth: number,
): TipTapNodeDto[] {
  if (values.length > 1_000 || depth > 100) throw new Error('INVALID_TIPTAP_CONTENT')
  return values.map((value, index) => {
    const node = canonicalizeNode(value, output, depth + 1)
    if (
      blockBoundary &&
      index + 1 < values.length &&
      CONTAINERS.has(node.type) &&
      output.at(-1) !== '\n'
    ) {
      output.push('\n')
    }
    return node
  })
}

function canonicalizeNode(value: unknown, output: string[], depth: number): TipTapNodeDto {
  if (!isRecord(value) || typeof value.type !== 'string' || !NODE_TYPES.has(value.type)) {
    throw new Error('INVALID_TIPTAP_CONTENT')
  }
  if (depth > 100) throw new Error('INVALID_TIPTAP_CONTENT')

  if (value.type === 'text') {
    if (
      typeof value.text !== 'string' ||
      value.text.length === 0 ||
      (Array.isArray(value.content) && value.content.length > 0)
    ) {
      throw new Error('INVALID_TIPTAP_CONTENT')
    }
    const marks = canonicalizeMarks(value.marks)
    const text = normalizeText(value.text)
    if (text.length === 0 || Array.from(text).length > 20_000) {
      throw new Error('INVALID_TIPTAP_CONTENT')
    }
    output.push(text)
    return { type: 'text', text, marks, content: [] }
  }

  if (value.type === 'hardBreak') {
    if (
      value.text !== undefined &&
      value.text !== null &&
      (typeof value.text !== 'string' || value.text.length > 0)
    ) {
      throw new Error('INVALID_TIPTAP_CONTENT')
    }
    if (
      (Array.isArray(value.marks) && value.marks.length > 0) ||
      (Array.isArray(value.content) && value.content.length > 0)
    ) {
      throw new Error('INVALID_TIPTAP_CONTENT')
    }
    output.push('\n')
    return { type: 'hardBreak', text: null, marks: [], content: [] }
  }

  if (
    (value.text !== undefined && value.text !== null) ||
    (Array.isArray(value.marks) && value.marks.length > 0)
  ) {
    throw new Error('INVALID_TIPTAP_CONTENT')
  }
  const children = value.content === undefined || value.content === null ? [] : value.content
  if (!Array.isArray(children)) throw new Error('INVALID_TIPTAP_CONTENT')
  if (
    (value.type === 'bulletList' || value.type === 'orderedList') &&
    children.some((child) => !isRecord(child) || child.type !== 'listItem')
  ) {
    throw new Error('INVALID_TIPTAP_CONTENT')
  }
  const boundary = ['bulletList', 'orderedList', 'listItem'].includes(value.type)
  return {
    type: value.type as TipTapNodeDto['type'],
    text: null,
    marks: [],
    content: canonicalizeChildren(children, output, boundary, depth),
  }
}

function canonicalizeMarks(value: unknown): TipTapMarkDto[] {
  if (value === undefined || value === null) return []
  if (!Array.isArray(value) || value.length > 2) throw new Error('INVALID_TIPTAP_CONTENT')
  const types = new Set<string>()
  for (const mark of value) {
    if (!isRecord(mark) || typeof mark.type !== 'string' || !MARK_TYPES.has(mark.type)) {
      throw new Error('INVALID_TIPTAP_CONTENT')
    }
    if (types.has(mark.type)) throw new Error('INVALID_TIPTAP_CONTENT')
    types.add(mark.type)
  }
  return [...types].sort().map((type) => ({ type: type as TipTapMarkDto['type'] }))
}

function normalizeText(value: string): string {
  return value
    .replace(/\r\n?/gu, '\n')
    .replace(/\u00A0/gu, ' ')
    .normalize('NFC')
    .replace(ZERO_WIDTH, '')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
