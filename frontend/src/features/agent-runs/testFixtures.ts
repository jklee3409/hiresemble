import type {
  AgentRunDetailDto,
  AgentRunSummaryDto,
  AgentRunStatus,
} from '@/shared/api/agentRunContracts'

export const RUN_ID = '10000000-0000-4000-8000-000000000001'
export const ROOT_RUN_ID = '10000000-0000-4000-8000-000000000002'
export const STEP_ID = '10000000-0000-4000-8000-000000000003'

export function agentRunDetail(overrides: Partial<AgentRunDetailDto> = {}): AgentRunDetailDto {
  return {
    id: RUN_ID,
    workflowType: 'JOB_ANALYSIS',
    resourceType: null,
    resourceId: null,
    status: 'RUNNING',
    currentStep: 'LOAD_FIXTURE',
    progressPercent: 20,
    requestedQualityMode: 'BALANCED',
    highestModelTierUsed: 'LOW_COST',
    estimatedCostUsd: 0.03,
    reservedCostUsd: 0.03,
    actualCostUsd: 0.01,
    retryable: false,
    cancellable: true,
    requiredUserAction: null,
    stateVersion: 1,
    queuedAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:01Z',
    retryOfRunId: null,
    rootRunId: ROOT_RUN_ID,
    runAttemptNo: 1,
    durationMs: null,
    startedAt: '2026-07-19T00:00:01Z',
    completedAt: null,
    safeError: null,
    partialResult: null,
    steps: [],
    ...overrides,
  }
}

export function agentRunSummary(
  status: AgentRunStatus = 'RUNNING',
  overrides: Partial<AgentRunSummaryDto> = {},
): AgentRunSummaryDto {
  const detail = agentRunDetail({ status, ...overrides })
  return {
    id: detail.id,
    workflowType: detail.workflowType,
    resourceType: detail.resourceType,
    resourceId: detail.resourceId,
    status: detail.status,
    currentStep: detail.currentStep,
    progressPercent: detail.progressPercent,
    requestedQualityMode: detail.requestedQualityMode,
    highestModelTierUsed: detail.highestModelTierUsed,
    estimatedCostUsd: detail.estimatedCostUsd,
    reservedCostUsd: detail.reservedCostUsd,
    actualCostUsd: detail.actualCostUsd,
    retryable: detail.retryable,
    cancellable: detail.cancellable,
    requiredUserAction: detail.requiredUserAction,
    stateVersion: detail.stateVersion,
    queuedAt: detail.queuedAt,
    updatedAt: detail.updatedAt,
  }
}
