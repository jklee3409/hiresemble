import { describe, expect, it } from 'vitest'

import {
  canonicalCareerArtifactListQuery,
  canonicalCareerArtifactNewQuery,
  parseCareerArtifactListFilters,
  parseCareerArtifactNewQuery,
} from './filters'

describe('Career Artifact URL filters', () => {
  it('canonicalizes malformed list filters to ACTIVE page zero defaults', () => {
    const parsed = parseCareerArtifactListFilters({
      artifactType: 'CV',
      lifecycleStatus: 'DELETED',
      page: '-1',
      size: '1000',
      sort: 'title,asc',
      private: 'value',
    })
    expect(parsed).toEqual({
      lifecycleStatus: 'ACTIVE',
      page: 0,
      size: 20,
      sort: 'updatedAt,desc',
    })
    expect(canonicalCareerArtifactListQuery(parsed)).toEqual({})
  })

  it('preserves supported server filters and omits defaults', () => {
    expect(
      canonicalCareerArtifactListQuery(
        parseCareerArtifactListFilters({
          artifactType: 'PORTFOLIO',
          lifecycleStatus: 'ARCHIVED',
          page: '2',
          size: '50',
          sort: 'createdAt,desc',
        }),
      ),
    ).toEqual({
      artifactType: 'PORTFOLIO',
      lifecycleStatus: 'ARCHIVED',
      page: '2',
      size: '50',
      sort: 'createdAt,desc',
    })
  })

  it('keeps only non-sensitive type and step navigation state for the wizard', () => {
    expect(
      parseCareerArtifactNewQuery({
        type: 'RESUME',
        step: '4',
        email: 'private@example.com',
        experienceItemIds: 'private-id',
      }),
    ).toEqual({ type: 'RESUME', step: 4 })
    expect(canonicalCareerArtifactNewQuery('RESUME', 4)).toEqual({ type: 'RESUME', step: '4' })
    expect(parseCareerArtifactNewQuery({ type: 'resume', step: '09' })).toEqual({
      type: null,
      step: 1,
    })
  })
})
