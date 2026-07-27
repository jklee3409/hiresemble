import { describe, expect, it } from 'vitest'

import { isJobVersionConflict, reapplyJobDraft } from './conflict'
import { jobDetailFixture } from './testFixtures'

describe('P5 Job version conflict recovery', () => {
  it('starts from the current server snapshot and reapplies selected draft fields with new version', () => {
    const latest = jobDetailFixture({
      companyName: 'Latest Company',
      title: 'Latest title',
      version: 8,
    })
    expect(
      reapplyJobDraft(
        latest,
        {
          companyName: 'Draft Company',
          title: 'Draft title',
          positionName: 'Draft position',
          descriptionText: 'Draft body',
          deadlineAt: null,
          version: 7,
        },
        ['title', 'descriptionText'],
      ),
    ).toEqual({
      companyName: 'Latest Company',
      title: 'Draft title',
      positionName: latest.positionName,
      descriptionText: 'Draft body',
      deadlineAt: latest.deadlineAt,
      version: 8,
    })
  })

  it('recognizes only the canonical optimistic-lock conflict', () => {
    expect(isJobVersionConflict({ status: 409, code: 'RESOURCE_VERSION_CONFLICT' })).toBe(true)
    expect(isJobVersionConflict({ status: 409, code: 'RESOURCE_STATE_CONFLICT' })).toBe(false)
  })
})
