import { z } from 'zod'

export const AGENT_RUN_STATUSES = [
  'QUEUED',
  'RUNNING',
  'WAITING_USER',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'INTERRUPTED',
] as const

export const AGENT_STEP_STATUSES = [
  'PENDING',
  'RUNNING',
  'WAITING_USER',
  'SUCCEEDED',
  'FAILED',
  'SKIPPED',
  'REUSED',
  'CANCELLED',
  'INTERRUPTED',
] as const

export const AI_QUALITY_MODES = ['ECONOMY', 'BALANCED', 'HIGH_QUALITY'] as const
export const MODEL_TIERS = ['LOW_COST', 'BALANCED', 'HIGH_QUALITY'] as const
export const WORKFLOW_TYPES = [
  'DOCUMENT_INGESTION',
  'JOB_POSTING_EXTRACTION',
  'JOB_ANALYSIS',
  'COVER_LETTER_GENERATION',
  'COVER_LETTER_VERIFICATION',
  'INTERVIEW_PREPARATION',
  'INTERVIEW_ANSWER_FEEDBACK',
  'MOCK_INTERVIEW_FEEDBACK',
] as const

export const REQUIRED_USER_ACTION_TYPES = [
  'PROVIDE_DOCUMENT_TEXT',
  'PROVIDE_JOB_TEXT',
  'ENABLE_HIGH_QUALITY',
  'INCREASE_BUDGET',
] as const

export type AgentRunStatus = (typeof AGENT_RUN_STATUSES)[number]
export type AgentStepStatus = (typeof AGENT_STEP_STATUSES)[number]
export type AiQualityMode = (typeof AI_QUALITY_MODES)[number]
export type ModelTier = (typeof MODEL_TIERS)[number]
export type WorkflowType = (typeof WORKFLOW_TYPES)[number]
export type RequiredUserActionType = (typeof REQUIRED_USER_ACTION_TYPES)[number]

const instantSchema = z.iso.datetime({ offset: true })
const nullableInstantSchema = instantSchema.nullable()
const uuidSchema = z.uuid()
const nullableUuidSchema = uuidSchema.nullable()
const moneySchema = z.number().nonnegative()
const stateVersionSchema = z.number().int().nonnegative()

export const safeErrorSchema = z.object({
  code: z.string().min(1).max(100),
  message: z.string().min(1).max(500),
})

export const resourceRefSchema = z.object({
  resourceType: z.string().min(1).max(50),
  resourceId: uuidSchema,
  displayLabel: z.string().max(200).nullable(),
})

export const requiredUserActionSchema = z.object({
  type: z.enum(REQUIRED_USER_ACTION_TYPES),
  resource: resourceRefSchema.nullable(),
  route: z.string().min(1).max(500).nullable(),
  message: z.string().min(1).max(500),
})

export const partialResultSchema = z.object({
  succeededScopeKeys: z.array(z.string().min(1).max(100)).max(100),
  failedScopeKeys: z.array(z.string().min(1).max(100)).max(100),
  resultRefs: z.array(resourceRefSchema).max(200),
})

export const agentStepSchema = z.object({
  id: uuidSchema,
  stepKey: z.string().min(1).max(100),
  scopeKey: z.string().max(100).nullable(),
  stepOrder: z.number().int().min(1),
  status: z.enum(AGENT_STEP_STATUSES),
  attempt: z.number().int().min(1).max(3),
  maxAttempts: z.number().int().min(1).max(3),
  startedAt: nullableInstantSchema,
  completedAt: nullableInstantSchema,
  safeError: safeErrorSchema.nullable(),
})

const agentRunSummaryFields = {
  id: uuidSchema,
  workflowType: z.enum(WORKFLOW_TYPES),
  resourceType: z.string().min(1).max(50).nullable(),
  resourceId: nullableUuidSchema,
  status: z.enum(AGENT_RUN_STATUSES),
  currentStep: z.string().min(1).max(100).nullable(),
  progressPercent: z.number().int().min(0).max(100),
  requestedQualityMode: z.enum(AI_QUALITY_MODES).nullable(),
  highestModelTierUsed: z.enum(MODEL_TIERS).nullable(),
  estimatedCostUsd: moneySchema,
  reservedCostUsd: moneySchema,
  actualCostUsd: moneySchema,
  retryable: z.boolean(),
  cancellable: z.boolean(),
  requiredUserAction: requiredUserActionSchema.nullable(),
  stateVersion: stateVersionSchema,
  queuedAt: instantSchema,
  updatedAt: instantSchema,
} as const

export const agentRunSummarySchema = z
  .object(agentRunSummaryFields)
  .superRefine((value, context) => validateResourcePair(value, context))

export const agentRunDetailSchema = z
  .object({
    ...agentRunSummaryFields,
    retryOfRunId: nullableUuidSchema,
    rootRunId: uuidSchema,
    runAttemptNo: z.number().int().min(1),
    durationMs: z.number().int().nonnegative().nullable(),
    startedAt: nullableInstantSchema,
    completedAt: nullableInstantSchema,
    safeError: safeErrorSchema.nullable(),
    partialResult: partialResultSchema.nullable(),
    steps: z.array(agentStepSchema).max(200),
  })
  .superRefine((value, context) => validateResourcePair(value, context))

export const agentRunPageSchema = z.object({
  items: z.array(agentRunSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
})

export const runAcceptedSchema = z
  .object({
    agentRunId: uuidSchema,
    status: z.enum(AGENT_RUN_STATUSES),
    resourceType: z.string().min(1).max(50).nullable(),
    resourceId: nullableUuidSchema,
    replayed: z.boolean(),
  })
  .superRefine((value, context) => validateResourcePair(value, context))

const eventBaseFields = {
  agentRunId: uuidSchema,
  stateVersion: stateVersionSchema,
  occurredAt: instantSchema,
} as const

export const snapshotEventSchema = z.object({
  ...eventBaseFields,
  run: agentRunDetailSchema,
})

export const progressEventSchema = z.object({
  ...eventBaseFields,
  status: z.enum(AGENT_RUN_STATUSES),
  currentStep: z.string().min(1).max(100).nullable(),
  progressPercent: z.number().int().min(0).max(100),
  actualCostUsd: moneySchema,
})

export const stepEventSchema = z.object({
  ...eventBaseFields,
  step: agentStepSchema,
})

export const waitingUserEventSchema = z.object({
  ...eventBaseFields,
  requiredUserAction: requiredUserActionSchema,
})

export const heartbeatEventSchema = z.object({
  ...eventBaseFields,
  serverTime: instantSchema,
  status: z.enum(AGENT_RUN_STATUSES),
})

export const terminalEventSchema = z
  .object({
    ...eventBaseFields,
    status: z.enum(AGENT_RUN_STATUSES),
    completedAt: instantSchema,
    actualCostUsd: moneySchema,
    retryable: z.boolean(),
    safeError: safeErrorSchema.nullable(),
    resourceType: z.string().min(1).max(50).nullable(),
    resourceId: nullableUuidSchema,
  })
  .superRefine((value, context) => validateResourcePair(value, context))

export type SafeErrorDto = z.infer<typeof safeErrorSchema>
export type ResourceRefDto = z.infer<typeof resourceRefSchema>
export type RequiredUserActionDto = z.infer<typeof requiredUserActionSchema>
export type PartialResultDto = z.infer<typeof partialResultSchema>
export type AgentStepDto = z.infer<typeof agentStepSchema>
export type AgentRunSummaryDto = z.infer<typeof agentRunSummarySchema>
export type AgentRunDetailDto = z.infer<typeof agentRunDetailSchema>
export type AgentRunPageDto = z.infer<typeof agentRunPageSchema>
export type RunAcceptedDto = z.infer<typeof runAcceptedSchema>
export type SnapshotEventDto = z.infer<typeof snapshotEventSchema>
export type ProgressEventDto = z.infer<typeof progressEventSchema>
export type StepEventDto = z.infer<typeof stepEventSchema>
export type WaitingUserEventDto = z.infer<typeof waitingUserEventSchema>
export type HeartbeatEventDto = z.infer<typeof heartbeatEventSchema>
export type TerminalEventDto = z.infer<typeof terminalEventSchema>

export interface AgentRunSseEventMap {
  snapshot: SnapshotEventDto
  progress: ProgressEventDto
  step: StepEventDto
  waiting_user: WaitingUserEventDto
  heartbeat: HeartbeatEventDto
  terminal: TerminalEventDto
}

function validateResourcePair(
  value: { resourceType: string | null; resourceId: string | null },
  context: z.RefinementCtx,
): void {
  if ((value.resourceType === null) !== (value.resourceId === null)) {
    context.addIssue({
      code: 'custom',
      path: ['resourceId'],
      message: 'resourceType과 resourceId는 함께 제공되어야 합니다.',
    })
  }
}
