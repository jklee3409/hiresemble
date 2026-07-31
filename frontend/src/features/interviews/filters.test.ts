import { describe, expect, it } from 'vitest'

import { INTERVIEW_COVER_LETTER_ID, INTERVIEW_JOB_ID } from './testFixtures'
import {
  canonicalQuestionSetQuery,
  interviewQuerySignature,
  parseQuestionSetFilters,
  questionSetApiFilters,
} from './filters'

describe('P8 question-set URL filters', () => {
  it('canonicalizes the qs namespace and maps canonical API parameter names', () => {
    const parsed = parseQuestionSetFilters({
      qsJobId: INTERVIEW_JOB_ID,
      qsCoverLetterId: INTERVIEW_COVER_LETTER_ID,
      qsQuery: '  Hiresemble backend  ',
      qsSourceCoverage: 'LIMITED',
      qsResearchStatus: 'SUCCEEDED',
      qsSort: 'createdAt,desc',
      qsPage: '2',
      qsSize: '50',
      page: '99',
      unknown: 'drop',
    })
    expect(parsed).toEqual({
      jobId: INTERVIEW_JOB_ID,
      coverLetterId: INTERVIEW_COVER_LETTER_ID,
      query: 'Hiresemble backend',
      sourceCoverage: 'LIMITED',
      researchStatus: 'SUCCEEDED',
      sort: 'createdAt,desc',
      page: 2,
      size: 50,
    })
    expect(canonicalQuestionSetQuery(parsed)).toEqual({
      qsJobId: INTERVIEW_JOB_ID,
      qsCoverLetterId: INTERVIEW_COVER_LETTER_ID,
      qsQuery: 'Hiresemble backend',
      qsSourceCoverage: 'LIMITED',
      qsResearchStatus: 'SUCCEEDED',
      qsSort: 'createdAt,desc',
      qsPage: '2',
      qsSize: '50',
    })
    expect(questionSetApiFilters(parsed)).not.toHaveProperty('qsSourceCoverage')
    expect(questionSetApiFilters(parsed)).toMatchObject({
      sourceCoverage: 'LIMITED',
      researchStatus: 'SUCCEEDED',
    })
  })

  it('drops malformed namespace values and resets to allowlisted defaults', () => {
    expect(
      parseQuestionSetFilters({
        qsJobId: 'not-a-uuid',
        qsCoverLetterId: ['bad', INTERVIEW_COVER_LETTER_ID],
        qsSourceCoverage: 'FAILED',
        qsResearchStatus: 'WAITING_USER',
        qsSort: 'providerRank,asc',
        qsPage: '-1',
        qsSize: '101',
      }),
    ).toEqual({
      jobId: undefined,
      coverLetterId: undefined,
      query: undefined,
      sourceCoverage: undefined,
      researchStatus: undefined,
      sort: 'updatedAt,desc',
      page: 0,
      size: 20,
    })
  })

  it('uses a stable encoded query signature', () => {
    expect(
      interviewQuerySignature({
        qsSourceCoverage: 'SUFFICIENT',
        qsQuery: 'a b',
      }),
    ).toBe('qsQuery=a%20b&qsSourceCoverage=SUFFICIENT')
  })
})
