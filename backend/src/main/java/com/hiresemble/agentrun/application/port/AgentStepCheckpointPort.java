package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.StepCheckpointCommand;
import com.hiresemble.agentrun.application.command.StepStartCommand;
import com.hiresemble.agentrun.application.model.AgentStepSnapshot;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;

public interface AgentStepCheckpointPort {
    AgentStepSnapshot start(StepStartCommand command);
    AgentStepSnapshot reuse(StepStartCommand command, ReusableStepSnapshot reusableStep);
    AgentStepSnapshot checkpoint(StepCheckpointCommand command);
}
