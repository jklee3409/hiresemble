import { featureFlags } from '@/app/featureFlags'

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
  '/profile/activities',
  '/profile/experiences',
  '/profile/evidence',
  '/documents',
  '/jobs',
  '/jobs/new',
  '/cover-letters',
  '/agent-runs',
])
const ENCODED_UNSAFE_CHARACTER = /%(?:0a|0d|5c)/i
const AGENT_RUN_DETAIL_PATH =
  /^\/agent-runs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const DOCUMENT_DETAIL_PATH =
  /^\/documents\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const JOB_DETAIL_PATH =
  /^\/jobs\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?:\/(?:overview|analysis|cover-letter))?$/i
const COVER_LETTER_EDIT_PATH =
  /^\/cover-letters\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\/edit$/i

export function safeReturnTo(
  value: unknown,
  origin = currentOrigin(),
  flags: Partial<
    Pick<typeof featureFlags, 'githubSourceEnabled' | 'careerArtifactEnabled'>
  > = featureFlags,
): string | null {
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
      !isAuthRequiredPath(
        target,
        Boolean(flags.githubSourceEnabled),
        Boolean(flags.careerArtifactEnabled),
      )
    ) {
      return null
    }

    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return null
  }
}

function isAuthRequiredPath(
  target: URL,
  githubSourceEnabled: boolean,
  careerArtifactEnabled: boolean,
): boolean {
  const path = target.pathname
  return (
    AUTH_REQUIRED_PATHS.has(path) ||
    (githubSourceEnabled && path === '/profile/github') ||
    AGENT_RUN_DETAIL_PATH.test(path) ||
    DOCUMENT_DETAIL_PATH.test(path) ||
    JOB_DETAIL_PATH.test(path) ||
    COVER_LETTER_EDIT_PATH.test(path) ||
    (careerArtifactEnabled && isCareerArtifactPath(target))
  )
}

function isCareerArtifactPath(target: URL): boolean {
  if (target.pathname === '/career-artifacts') return target.search === ''
  if (target.pathname === '/career-artifacts/new') {
    const allowedKeys = new Set(['type', 'step'])
    if ([...target.searchParams.keys()].some((key) => !allowedKeys.has(key))) return false
    if (
      [...target.searchParams.keys()].some((key) => target.searchParams.getAll(key).length !== 1)
    ) {
      return false
    }
    const type = target.searchParams.get('type')
    const step = target.searchParams.get('step')
    if (type !== null && type !== 'RESUME' && type !== 'PORTFOLIO') return false
    if (step !== null && !['2', '3', '4'].includes(step)) return false
    const canonical = new URLSearchParams()
    if (type !== null) canonical.set('type', type)
    if (step !== null) canonical.set('step', step)
    return target.search === (canonical.size > 0 ? `?${canonical.toString()}` : '')
  }
  return (
    /^\/career-artifacts\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      target.pathname,
    ) && target.search === ''
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
