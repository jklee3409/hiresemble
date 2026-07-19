package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.application.AgentRunPage;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentStepSnapshot;
import com.hiresemble.agentrun.application.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.PartialResult;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.ResourceReference;
import com.hiresemble.agentrun.domain.SafeError;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentRunApiMapper {

    public AgentRunPageDto page(AgentRunPage page) {
        return new AgentRunPageDto(
                page.items().stream().map(this::summary).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public AgentRunSummaryDto summary(AgentRunSnapshot run) {
        return new AgentRunSummaryDto(
                run.id(), run.workflowType(), run.resourceType(), run.resourceId(), run.status(),
                run.currentStep(), run.progressPercent(), run.requestedQualityMode(),
                run.highestModelTierUsed(), run.estimatedCostUsd(), run.reservedCostUsd(),
                run.actualCostUsd(), run.retryable(), run.cancellable(),
                action(run.requiredUserAction()), run.stateVersion(), run.queuedAt(), run.updatedAt());
    }

    public AgentRunDetailDto detail(AgentRunSnapshot run) {
        return new AgentRunDetailDto(
                run.id(), run.workflowType(), run.resourceType(), run.resourceId(), run.status(),
                run.currentStep(), run.progressPercent(), run.requestedQualityMode(),
                run.highestModelTierUsed(), run.estimatedCostUsd(), run.reservedCostUsd(),
                run.actualCostUsd(), run.retryable(), run.cancellable(),
                action(run.requiredUserAction()), run.stateVersion(), run.queuedAt(), run.updatedAt(),
                run.retryOfRunId(), run.rootRunId(), run.runAttemptNo(), duration(run),
                run.startedAt(), run.completedAt(), error(run.safeError()),
                partial(run.partialResult()), run.steps().stream().map(this::step).toList());
    }

    public RunAcceptedDto accepted(WorkflowLaunchResult result) {
        return new RunAcceptedDto(
                result.agentRunId(), result.status(), result.resourceType(),
                result.resourceId(), result.replayed());
    }

    public AgentStepDto step(AgentStepSnapshot step) {
        return new AgentStepDto(
                step.id(), step.stepKey(), step.scopeKey(), step.stepOrder(), step.status(),
                step.attempt(), step.maxAttempts(), step.startedAt(), step.completedAt(),
                error(step.safeError()));
    }

    public RequiredUserActionDto action(RequiredUserAction action) {
        return action == null ? null : new RequiredUserActionDto(
                action.type(), resource(action.resource()), action.route(), action.message());
    }

    public SafeErrorDto error(SafeError error) {
        return error == null ? null : new SafeErrorDto(error.code(), error.message());
    }

    private PartialResultDto partial(PartialResult partial) {
        return partial == null ? null : new PartialResultDto(
                partial.succeededScopeKeys(), partial.failedScopeKeys(),
                partial.resultRefs().stream().map(this::resource).toList());
    }

    private ResourceRefDto resource(ResourceReference reference) {
        return reference == null ? null : new ResourceRefDto(
                reference.resourceType(), reference.resourceId(), reference.displayLabel());
    }

    private Long duration(AgentRunSnapshot run) {
        if (run.startedAt() == null) {
            return null;
        }
        Instant end = run.completedAt() == null ? run.updatedAt() : run.completedAt();
        return Math.max(0, Duration.between(run.startedAt(), end).toMillis());
    }
}
