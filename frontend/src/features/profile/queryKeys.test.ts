import { describe, expect, it } from 'vitest'

import { profileQueryKeys } from './queryKeys'

describe('profile query keys', () => {
  it('namespaces every profile cache by authenticated user', () => {
    const filters = { page: 0, size: 20, sort: 'createdAt,desc' }
    expect(profileQueryKeys.profile('user-1')).toEqual(['user', 'user-1', 'profile'])
    expect(profileQueryKeys.educations('user-1', filters)).not.toEqual(
      profileQueryKeys.educations('user-2', filters),
    )
    expect(profileQueryKeys.certifications('user-1', filters)[1]).toBe('user-1')
    expect(profileQueryKeys.languageScores('user-1', filters)[1]).toBe('user-1')
    expect(profileQueryKeys.awards('user-1', filters)[1]).toBe('user-1')
    expect(profileQueryKeys.careers('user-1', filters)[1]).toBe('user-1')
    expect(profileQueryKeys.evidence('user-1', filters)[1]).toBe('user-1')
    expect(profileQueryKeys.evidence('user-1', filters)).toEqual([
      'user',
      'user-1',
      'evidence',
      filters,
    ])
  })
})
