import { describe, expect, it } from 'vitest'

import { agentRunDetail, RUN_ID } from '@/features/agent-runs/testFixtures'

import {
  AGENT_RUN_STATUSES,
  AGENT_STEP_STATUSES,
  AI_QUALITY_MODES,
  MODEL_TIERS,
  WORKFLOW_TYPES,
  agentRunDetailSchema,
  runAcceptedSchema,
} from './agentRunContracts'

describe('Agent Run public contract parity', () => {
  it('keeps the canonical enum values in backend order', () => {
    expect(AGENT_RUN_STATUSES).toEqual([
      'QUEUED',
      'RUNNING',
      'WAITING_USER',
      'SUCCEEDED',
      'FAILED',
      'CANCELLED',
      'INTERRUPTED',
    ])
    expect(AGENT_STEP_STATUSES).toEqual([
      'PENDING',
      'RUNNING',
      'WAITING_USER',
      'SUCCEEDED',
      'FAILED',
      'SKIPPED',
      'REUSED',
      'CANCELLED',
      'INTERRUPTED',
    ])
    expect(AI_QUALITY_MODES).toEqual(['ECONOMY', 'BALANCED', 'HIGH_QUALITY'])
    expect(MODEL_TIERS).toEqual(['LOW_COST', 'BALANCED', 'HIGH_QUALITY'])
    expect(WORKFLOW_TYPES).toHaveLength(8)
  })

  it('accepts the exact nullable P3 detail projection and strips internal fields', () => {
    const parsed = agentRunDetailSchema.parse({
      ...agentRunDetail(),
      provider: 'must-not-be-public',
      modelId: 'private-model',
      prompt: 'private prompt',
      inputHash: 'private hash',
      claimToken: 'private claim',
    })

    expect(parsed.resourceType).toBeNull()
    expect(parsed.resourceId).toBeNull()
    expect(parsed.requestedQualityMode).toBe('BALANCED')
    expect(parsed).not.toHaveProperty('provider')
    expect(parsed).not.toHaveProperty('modelId')
    expect(parsed).not.toHaveProperty('prompt')
    expect(parsed).not.toHaveProperty('inputHash')
    expect(parsed).not.toHaveProperty('claimToken')
  })

  it('rejects a half-present resource reference and accepts nullable RunAcceptedDto fields', () => {
    expect(() =>
      agentRunDetailSchema.parse({ ...agentRunDetail(), resourceType: 'document' }),
    ).toThrow()
    expect(
      runAcceptedSchema.parse({
        agentRunId: RUN_ID,
        status: 'QUEUED',
        resourceType: null,
        resourceId: null,
        replayed: false,
      }),
    ).toMatchObject({ agentRunId: RUN_ID, resourceType: null, resourceId: null })
  })
})
