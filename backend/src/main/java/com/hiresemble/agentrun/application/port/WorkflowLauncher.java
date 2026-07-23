package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;

public interface WorkflowLauncher {
    WorkflowLaunchResult launch(WorkflowLaunchCommand command);
}
