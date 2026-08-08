package com.hiresemble.ai.workflow.careerartifact;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.careerartifact.application.CareerArtifactApplicationService;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;

public final class CareerArtifactGenerationFailureHandler implements WorkflowFailureHandler {

    private final CareerArtifactWorkflowPort workflowPort;
    private final CareerArtifactApplicationService artifacts;

    public CareerArtifactGenerationFailureHandler(
            CareerArtifactWorkflowPort workflowPort,
            CareerArtifactApplicationService artifacts) {
        this.workflowPort = workflowPort;
        this.artifacts = artifacts;
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return (run.workflowType() == WorkflowType.RESUME_GENERATION
                        || run.workflowType() == WorkflowType.PORTFOLIO_GENERATION)
                && CareerArtifactTypes.RESOURCE_TYPE.equals(run.resourceType())
                && run.resourceId() != null;
    }

    @Override
    public void onFailure(AgentRunSnapshot run, AiExecutionException failure) {
        workflowPort.discard(run.id());
        artifacts.compensate(
                run.userId(), run.id(), run.resourceType(), run.resourceId());
    }
}
