package com.hiresemble.agentrun.application;

import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentRunRetryService implements AgentRunRetryPort {

    private static final String ROUTE_SCOPE = "/api/v1/agent-runs/{agentRunId}/retry";
    private final IdempotencyService idempotencyService;
    private final AgentRunRetryTransaction retryTransaction;

    public AgentRunRetryService(
            IdempotencyService idempotencyService,
            AgentRunRetryTransaction retryTransaction) {
        this.idempotencyService = idempotencyService;
        this.retryTransaction = retryTransaction;
    }

    @Override
    public WorkflowLaunchResult retry(
            UUID userId, UUID predecessorRunId, String idempotencyKey) {
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", ROUTE_SCOPE, predecessorRunId, idempotencyKey);
        IdempotentResponse<WorkflowLaunchResult> result = idempotencyService.execute(
                scope,
                "{}",
                WorkflowLaunchResult.class,
                () -> {
                    WorkflowLaunchResult created = retryTransaction.retry(userId, predecessorRunId);
                    return new OriginalResponse<>(
                            202,
                            created,
                            created.resourceType(),
                            created.resourceId(),
                            created.agentRunId());
                });
        WorkflowLaunchResult body = result.body();
        return new WorkflowLaunchResult(
                body.agentRunId(), body.status(), body.resourceType(), body.resourceId(), result.replayed());
    }
}
