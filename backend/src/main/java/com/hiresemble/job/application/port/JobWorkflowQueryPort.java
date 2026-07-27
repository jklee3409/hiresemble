package com.hiresemble.job.application.port;

import com.hiresemble.job.domain.JobRecords.WorkflowSnapshot;
import java.util.UUID;

public interface JobWorkflowQueryPort {

    WorkflowSnapshot snapshot(UUID userId, UUID jobId);
}
