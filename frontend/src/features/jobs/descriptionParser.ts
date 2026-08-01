export type JobDescriptionNode =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'unordered-list'; items: string[] }
  | { type: 'ordered-list'; items: string[] }

export type JobDescriptionInline =
  { type: 'text'; value: string } | { type: 'link'; value: string; href: string }

const KNOWN_HEADINGS = new Set([
  '모집 개요',
  '포지션 소개',
  '주요 업무',
  '담당 업무',
  '업무 내용',
  '지원 자격',
  '자격 요건',
  '필수 조건',
  '우대 사항',
  '전형 절차',
  '채용 절차',
  '지원 방법',
  '근무 조건',
  '근무 환경',
  '복지 및 혜택',
  '혜택 및 복지',
  '접수 기간',
])

const UNORDERED_ITEM = /^\s*[-•·▪◦]\s+(.+)$/u
const ORDERED_ITEM = /^\s*\d{1,2}[.)]\s+(.+)$/u
const URL = /https?:\/\/[^\s<>]+/giu

export function parseJobDescription(source: string): JobDescriptionNode[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const nodes: JobDescriptionNode[] = []
  let paragraph: string[] = []
  let listType: 'unordered-list' | 'ordered-list' | null = null
  let listItems: string[] = []

  const flushParagraph = () => {
    const text = paragraph.join(' ').replace(/\s+/g, ' ').trim()
    if (text) nodes.push({ type: 'paragraph', text })
    paragraph = []
  }
  const flushList = () => {
    if (listType && listItems.length) nodes.push({ type: listType, items: listItems })
    listType = null
    listItems = []
  }

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      flushParagraph()
      flushList()
      continue
    }
    if (isHeading(line)) {
      flushParagraph()
      flushList()
      nodes.push({ type: 'heading', text: line.replace(/\s*[:：]\s*$/u, '') })
      continue
    }
    const unordered = rawLine.match(UNORDERED_ITEM)
    const ordered = rawLine.match(ORDERED_ITEM)
    const nextType = unordered ? 'unordered-list' : ordered ? 'ordered-list' : null
    if (nextType) {
      flushParagraph()
      if (listType && listType !== nextType) flushList()
      listType = nextType
      listItems.push((unordered?.[1] ?? ordered?.[1] ?? '').trim())
      continue
    }
    flushList()
    paragraph.push(line)
  }
  flushParagraph()
  flushList()
  return nodes
}

export function parseJobDescriptionInline(text: string): JobDescriptionInline[] {
  const result: JobDescriptionInline[] = []
  let cursor = 0
  for (const match of text.matchAll(URL)) {
    const index = match.index ?? 0
    if (index > cursor) result.push({ type: 'text', value: text.slice(cursor, index) })
    const raw = match[0]
    const href = raw.replace(/[),.;!?]+$/u, '')
    result.push({ type: 'link', value: href, href })
    const trailing = raw.slice(href.length)
    if (trailing) result.push({ type: 'text', value: trailing })
    cursor = index + raw.length
  }
  if (cursor < text.length) result.push({ type: 'text', value: text.slice(cursor) })
  return result.length ? result : [{ type: 'text', value: text }]
}

function isHeading(line: string): boolean {
  const normalized = line.replace(/\s*[:：]\s*$/u, '').trim()
  if (KNOWN_HEADINGS.has(normalized)) return true
  return (
    line.length <= 32 &&
    /[:：]$/u.test(line) &&
    !/[.!?。！？]/u.test(normalized) &&
    !/^https?:\/\//iu.test(normalized)
  )
}
