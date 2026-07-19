const AUTH_REQUIRED_PATHS = new Set(['/dashboard', '/onboarding'])
const ENCODED_UNSAFE_CHARACTER = /%(?:0a|0d|5c)/i

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
      !AUTH_REQUIRED_PATHS.has(target.pathname)
    ) {
      return null
    }

    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return null
  }
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
