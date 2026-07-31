import { describe, expect, it } from 'vitest'

import {
  answerFixture,
  feedbackFixture,
  questionFixture,
  questionSetDetailFixture,
  researchFixture,
  sourceFixture,
} from '@/features/interviews/testFixtures'

import {
  interviewAnswerVersionSchema,
  interviewFeedbackSchema,
  interviewQuestionSchema,
  questionSetDetailSchema,
  researchRunSchema,
  researchSourceSchema,
} from './interviewContracts'

describe('P8 interview public contracts', () => {
  it('parses research, source, question, answer, feedback and question-set DTOs', () => {
    expect(researchRunSchema.parse(researchFixture()).sourceCoverage).toBe('SUFFICIENT')
    expect(researchSourceSchema.parse(sourceFixture()).sourceType).toBe('OFFICIAL')
    expect(interviewQuestionSchema.parse(questionFixture()).sourceBased).toBe(true)
    expect(interviewAnswerVersionSchema.parse(answerFixture()).sourceType).toBe('USER_EDITED')
    expect(interviewFeedbackSchema.parse(feedbackFixture()).scores[0]?.score).toBe(82)
    expect(questionSetDetailSchema.parse(questionSetDetailFixture()).questions).toHaveLength(1)
  })

  it('accepts every canonical source, coverage and question type but no private value', () => {
    for (const coverage of ['SUFFICIENT', 'LIMITED', 'NONE'] as const) {
      expect(
        researchRunSchema.safeParse(researchFixture({ sourceCoverage: coverage })).success,
      ).toBe(true)
    }
    for (const sourceType of [
      'OFFICIAL',
      'TECH_BLOG',
      'NEWS',
      'INTERVIEW_REVIEW',
      'COMMUNITY',
      'OTHER',
    ] as const) {
      expect(researchSourceSchema.safeParse(sourceFixture({ sourceType })).success).toBe(true)
    }
    expect(
      researchSourceSchema.safeParse({
        ...sourceFixture(),
        sourceType: 'PROVIDER_INTERNAL',
      }).success,
    ).toBe(false)
    expect(
      interviewQuestionSchema.safeParse(questionFixture({ questionType: 'FOLLOW_UP' })).success,
    ).toBe(true)
  })

  it('enforces feedback, answer and generated-question upper bounds', () => {
    expect(
      interviewAnswerVersionSchema.safeParse(answerFixture({ content: 'x'.repeat(20_000) }))
        .success,
    ).toBe(true)
    expect(
      interviewAnswerVersionSchema.safeParse(answerFixture({ content: 'x'.repeat(20_001) }))
        .success,
    ).toBe(false)
    expect(
      interviewFeedbackSchema.safeParse(
        feedbackFixture({
          scores: Array.from({ length: 20 }, (_, index) => ({
            criterion: `기준 ${index}`,
            score: 100,
            explanation: 'x'.repeat(1_000),
          })),
          revisedExample: 'x'.repeat(10_000),
        }),
      ).success,
    ).toBe(true)
    expect(
      interviewFeedbackSchema.safeParse(
        feedbackFixture({ strengths: Array.from({ length: 21 }, () => '강점') }),
      ).success,
    ).toBe(false)
    expect(
      interviewQuestionSchema.safeParse(
        questionFixture({
          followUpQuestions: Array.from({ length: 11 }, () => '후속 질문'),
        }),
      ).success,
    ).toBe(false)
  })

  it('does not accept a model/provider field in place of the public DTO shape', () => {
    const malformed = {
      ...feedbackFixture(),
      scores: [{ criterion: '질문 적합성', score: 101, explanation: null }],
    }
    expect(interviewFeedbackSchema.safeParse(malformed).success).toBe(false)
    expect(researchSourceSchema.safeParse({ providerRank: 1 }).success).toBe(false)
  })
})
