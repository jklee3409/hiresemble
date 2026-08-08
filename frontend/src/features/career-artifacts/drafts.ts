import { z } from 'zod'

import { CAREER_ARTIFACT_PROFILE_SECTIONS } from '@/shared/api/careerArtifactContracts'
import type {
  CareerArtifactProfileSection,
  CareerArtifactRenderProfile,
  CareerArtifactType,
} from '@/shared/api/careerArtifactContracts'
import { createCareerArtifactIdempotencyKey } from '@/shared/api/careerArtifactApi'

export const CAREER_ARTIFACT_DRAFT_TTL_MS = 24 * 60 * 60 * 1_000

export interface CareerArtifactGenerationDraft {
  artifactType: CareerArtifactType | null
  title: string
  experienceItemIds: string[]
  model: string
  includeProfileSections: CareerArtifactProfileSection[]
  renderProfile: CareerArtifactRenderProfile
  step: number
  savedAt: number
  pendingRequest: {
    signature: string
    idempotencyKey: string
  } | null
}

const careerArtifactDraftSchema = z
  .object({
    artifactType: z.enum(['RESUME', 'PORTFOLIO']).nullable(),
    title: z.string().max(120),
    experienceItemIds: z
      .array(z.string().uuid())
      .max(20)
      .refine((ids) => new Set(ids).size === ids.length),
    model: z.string(),
    includeProfileSections: z
      .array(z.enum(CAREER_ARTIFACT_PROFILE_SECTIONS))
      .max(CAREER_ARTIFACT_PROFILE_SECTIONS.length)
      .refine((sections) => new Set(sections).size === sections.length),
    renderProfile: z
      .object({
        displayName: z.string().max(100),
        email: z.string().max(320).nullable(),
        phone: z.string().max(30).nullable(),
        links: z
          .array(
            z
              .object({
                label: z.string().max(50),
                url: z.string().max(500),
              })
              .strict(),
          )
          .max(5),
        includeContact: z.boolean(),
      })
      .strict(),
    step: z.number().int().min(1).max(4),
    savedAt: z.number().finite().nonnegative(),
    pendingRequest: z
      .object({
        signature: z.string().min(1),
        idempotencyKey: z.string().min(1),
      })
      .strict()
      .nullable(),
  })
  .strict()

export function createCareerArtifactDraftKey(userId: string): string {
  return `1/${userId}/career-artifact/new/generation/0`
}

export function regenerateCareerArtifactDraftKey(
  userId: string,
  artifactId: string,
  artifactVersion: number,
): string {
  return `1/${userId}/career-artifact/${artifactId}/generation/${artifactVersion}`
}

export function createEmptyCareerArtifactDraft(
  displayName: string,
  email: string | null,
  artifactType: CareerArtifactType | null = null,
  now = Date.now(),
): CareerArtifactGenerationDraft {
  return {
    artifactType,
    title: '',
    experienceItemIds: [],
    model: '',
    includeProfileSections: [
      'PROFILE',
      'EDUCATIONS',
      'CERTIFICATIONS',
      'LANGUAGE_SCORES',
      'AWARDS',
      'CAREERS',
      'ACTIVITIES',
    ],
    renderProfile: {
      displayName,
      email,
      phone: null,
      links: [],
      includeContact: true,
    },
    step: 1,
    savedAt: now,
    pendingRequest: null,
  }
}

export function loadCareerArtifactDraft(
  key: string,
  now = Date.now(),
  storage: Pick<Storage, 'getItem' | 'removeItem'> | null = browserSessionStorage(),
): CareerArtifactGenerationDraft | null {
  if (storage === null) return null
  const raw = storage.getItem(key)
  if (raw === null) return null
  try {
    const parsed = careerArtifactDraftSchema.safeParse(JSON.parse(raw) as unknown)
    if (!parsed.success || now - parsed.data.savedAt > CAREER_ARTIFACT_DRAFT_TTL_MS) {
      storage.removeItem(key)
      return null
    }
    return parsed.data
  } catch {
    storage.removeItem(key)
    return null
  }
}

export function saveCareerArtifactDraft(
  key: string,
  draft: CareerArtifactGenerationDraft,
  now = Date.now(),
  storage: Pick<Storage, 'setItem'> | null = browserSessionStorage(),
): void {
  storage?.setItem(key, JSON.stringify({ ...draft, savedAt: now }))
}

export function clearCareerArtifactDraft(
  key: string,
  storage: Pick<Storage, 'removeItem'> | null = browserSessionStorage(),
): void {
  storage?.removeItem(key)
}

export function clearCareerArtifactDraftsForArtifact(
  userId: string,
  artifactId: string,
  storage: Pick<Storage, 'key' | 'length' | 'removeItem'> | null = browserSessionStorage(),
): void {
  if (storage === null) return
  const keys: string[] = []
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index)
    if (key === null) continue
    const segments = key.split('/')
    if (
      segments.length === 6 &&
      segments[0] === '1' &&
      segments[1] === userId &&
      segments[2] === 'career-artifact' &&
      segments[3] === artifactId &&
      segments[4] === 'generation'
    ) {
      keys.push(key)
    }
  }
  for (const key of keys) storage.removeItem(key)
}

export function pendingCareerArtifactIdempotencyKey(
  draft: CareerArtifactGenerationDraft,
  request: unknown,
  action: 'create' | 'regenerate',
): string {
  const signature = stableRequestSignature(request)
  if (draft.pendingRequest?.signature === signature) return draft.pendingRequest.idempotencyKey
  const idempotencyKey = createCareerArtifactIdempotencyKey(action)
  draft.pendingRequest = { signature, idempotencyKey }
  return idempotencyKey
}

export function stableRequestSignature(request: unknown): string {
  return JSON.stringify(sortValue(request))
}

function sortValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortValue)
  if (typeof value !== 'object' || value === null) return value
  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entry]) => [key, sortValue(entry)]),
  )
}

function browserSessionStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.sessionStorage
}
