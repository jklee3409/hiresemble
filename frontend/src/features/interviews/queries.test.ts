import { describe, expect, it } from 'vitest'

import { interviewQueryKeys } from './queries'

describe('P8 interview query ownership', () => {
  it('separates every question-set, research, answer and feedback cache by user', () => {
    expect(interviewQueryKeys.questionSet('user-a', 'set-1')).not.toEqual(
      interviewQueryKeys.questionSet('user-b', 'set-1'),
    )
    expect(interviewQueryKeys.researchSources('user-a', 'research-1', {})).toEqual([
      'user',
      'user-a',
      'interviews',
      'research',
      'research-1',
      'sources',
      {},
    ])
    expect(interviewQueryKeys.answerVersions('user-a', 'question-1', {})).toEqual([
      'user',
      'user-a',
      'interviews',
      'question',
      'question-1',
      'answerVersions',
      {},
    ])
    expect(interviewQueryKeys.feedbacks('user-a', 'answer-1', {})).toEqual([
      'user',
      'user-a',
      'interviews',
      'answerVersion',
      'answer-1',
      'feedbacks',
      {},
    ])
  })

  it('keeps list filters and resource IDs in the cache identity', () => {
    expect(
      interviewQueryKeys.questionSetList('user-a', {
        sourceCoverage: 'LIMITED',
        page: 0,
      }),
    ).not.toEqual(
      interviewQueryKeys.questionSetList('user-a', {
        sourceCoverage: 'SUFFICIENT',
        page: 0,
      }),
    )
    expect(interviewQueryKeys.feedbacksRoot('user-a', 'answer-1')).not.toEqual(
      interviewQueryKeys.feedbacksRoot('user-a', 'answer-2'),
    )
  })
})
