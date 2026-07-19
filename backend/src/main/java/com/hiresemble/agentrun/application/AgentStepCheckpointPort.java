package com.hiresemble.agentrun.application;

public interface AgentStepCheckpointPort {
    AgentStepSnapshot start(StepStartCommand command);
    AgentStepSnapshot reuse(StepStartCommand command, ReusableStepSnapshot reusableStep);
    AgentStepSnapshot checkpoint(StepCheckpointCommand command);
}
