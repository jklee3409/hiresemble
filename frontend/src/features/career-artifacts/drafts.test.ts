import { describe, expect, it, vi } from 'vitest'

import {
  CAREER_ARTIFACT_DRAFT_TTL_MS,
  clearCareerArtifactDraft,
  clearCareerArtifactDraftsForArtifact,
  createCareerArtifactDraftKey,
  createEmptyCareerArtifactDraft,
  loadCareerArtifactDraft,
  pendingCareerArtifactIdempotencyKey,
  regenerateCareerArtifactDraftKey,
  saveCareerArtifactDraft,
} from './drafts'

describe('Career Artifact session draft', () => {
  it('uses user-scoped six-plus-segment keys for create and regenerate', () => {
    expect(createCareerArtifactDraftKey('user-1').split('/')).toHaveLength(6)
    expect(createCareerArtifactDraftKey('user-1')).toBe('1/user-1/career-artifact/new/generation/0')
    expect(regenerateCareerArtifactDraftKey('user-2', 'artifact-1', 7)).toBe(
      '1/user-2/career-artifact/artifact-1/generation/7',
    )
  })

  it('isolates users, expires after 24 hours, and purges malformed values', () => {
    const storage = memoryStorage()
    const now = 10_000
    const firstKey = createCareerArtifactDraftKey('user-1')
    const secondKey = createCareerArtifactDraftKey('user-2')
    saveCareerArtifactDraft(
      firstKey,
      createEmptyCareerArtifactDraft('첫 사용자', null, 'RESUME', now),
      now,
      storage,
    )
    saveCareerArtifactDraft(
      secondKey,
      createEmptyCareerArtifactDraft('둘째 사용자', null, 'PORTFOLIO', now),
      now,
      storage,
    )
    expect(loadCareerArtifactDraft(firstKey, now, storage)?.renderProfile.displayName).toBe(
      '첫 사용자',
    )
    expect(loadCareerArtifactDraft(secondKey, now, storage)?.renderProfile.displayName).toBe(
      '둘째 사용자',
    )
    expect(
      loadCareerArtifactDraft(firstKey, now + CAREER_ARTIFACT_DRAFT_TTL_MS + 1, storage),
    ).toBeNull()
    expect(storage.getItem(firstKey)).toBeNull()
    storage.setItem(firstKey, '{broken')
    expect(loadCareerArtifactDraft(firstKey, now, storage)).toBeNull()
    storage.setItem(
      firstKey,
      JSON.stringify({
        ...createEmptyCareerArtifactDraft('지원자', null, 'RESUME', now),
        renderProfile: {},
      }),
    )
    expect(loadCareerArtifactDraft(firstKey, now, storage)).toBeNull()
  })

  it('reuses the key for the same pending request and rotates it after input changes', () => {
    const randomUUID = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUUID.mockReturnValueOnce('00000000-0000-4000-8000-000000000001')
    randomUUID.mockReturnValueOnce('00000000-0000-4000-8000-000000000002')
    const draft = createEmptyCareerArtifactDraft('지원자', null, 'RESUME')
    const first = pendingCareerArtifactIdempotencyKey(draft, { title: 'A', ids: ['1'] }, 'create')
    const same = pendingCareerArtifactIdempotencyKey(draft, { ids: ['1'], title: 'A' }, 'create')
    const changed = pendingCareerArtifactIdempotencyKey(draft, { title: 'B', ids: ['1'] }, 'create')
    expect(same).toBe(first)
    expect(changed).not.toBe(first)
    randomUUID.mockRestore()
  })

  it('clears the exact draft after success or explicit cancellation', () => {
    const storage = memoryStorage()
    const key = createCareerArtifactDraftKey('user-1')
    saveCareerArtifactDraft(key, createEmptyCareerArtifactDraft('지원자', null), 1, storage)
    clearCareerArtifactDraft(key, storage)
    expect(storage.getItem(key)).toBeNull()
  })

  it('clears every version draft for a deleted artifact without crossing users or artifacts', () => {
    const storage = memoryStorage()
    const targetV1 = regenerateCareerArtifactDraftKey('user-1', 'artifact-1', 1)
    const targetV2 = regenerateCareerArtifactDraftKey('user-1', 'artifact-1', 2)
    const otherArtifact = regenerateCareerArtifactDraftKey('user-1', 'artifact-2', 1)
    const otherUser = regenerateCareerArtifactDraftKey('user-2', 'artifact-1', 1)
    for (const key of [targetV1, targetV2, otherArtifact, otherUser]) {
      saveCareerArtifactDraft(key, createEmptyCareerArtifactDraft('지원자', null), 1, storage)
    }

    clearCareerArtifactDraftsForArtifact('user-1', 'artifact-1', storage)

    expect(storage.getItem(targetV1)).toBeNull()
    expect(storage.getItem(targetV2)).toBeNull()
    expect(storage.getItem(otherArtifact)).not.toBeNull()
    expect(storage.getItem(otherUser)).not.toBeNull()
  })
})

function memoryStorage() {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => Array.from(values.keys())[index] ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  }
}
