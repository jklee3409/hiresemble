package com.hiresemble.agentrun.application;

public interface WorkflowLauncher {
    WorkflowLaunchResult launch(WorkflowLaunchCommand command);
}
