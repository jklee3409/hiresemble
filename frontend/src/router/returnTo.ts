const AUTH_REQUIRED_PATHS = new Set([
  '/dashboard',
  '/onboarding',
  '/profile',
  '/profile/basic',
  '/profile/education',
  '/profile/certifications',
  '/profile/languages',
  '/profile/awards',
  '/profile/careers',
  '/profile/evidence',
  '/documents',
  '/agent-runs',
])
const ENCODED_UNSAFE_CHARACTER = /%(?:0a|0d|5c)/i
const AGENT_RUN_DETAIL_PATH =
  /^\/agent-runs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const DOCUMENT_DETAIL_PATH =
  /^\/documents\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function safeReturnTo(value: unknown, origin = currentOrigin()): string | null {
  if (typeof value !== 'string' || value.length === 0) {
    return null
  }

  if (
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    hasControlCharacter(value) ||
    ENCODED_UNSAFE_CHARACTER.test(value)
  ) {
    return null
  }

  try {
    const target = new URL(value, origin)
    const rawPath = value.split(/[?#]/, 1)[0]
    if (
      target.origin !== origin ||
      rawPath !== target.pathname ||
      !isAuthRequiredPath(target.pathname)
    ) {
      return null
    }

    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return null
  }
}

function isAuthRequiredPath(path: string): boolean {
  return (
    AUTH_REQUIRED_PATHS.has(path) ||
    AGENT_RUN_DETAIL_PATH.test(path) ||
    DOCUMENT_DETAIL_PATH.test(path)
  )
}

function currentOrigin(): string {
  return typeof window === 'undefined' ? 'http://localhost' : window.location.origin
}

function hasControlCharacter(value: string): boolean {
  return Array.from(value).some((character) => {
    const codePoint = character.codePointAt(0)
    return codePoint !== undefined && (codePoint <= 0x1f || codePoint === 0x7f)
  })
}
