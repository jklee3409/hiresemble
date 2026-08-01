package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.job.domain.JobCommands.CreateJob;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobCreationService {

    public static final String WORKFLOW_VERSION =
            CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION;
    private final JobStore store;
    private final WorkflowLauncher workflowLauncher;
    private final JobExtractionLaunchFactory launchFactory;
    private final Clock clock;

    public JobCreationService(
            JobStore store,
            WorkflowLauncher workflowLauncher,
            JobExtractionLaunchFactory launchFactory,
            Clock clock) {
        this.store = store;
        this.workflowLauncher = workflowLauncher;
        this.launchFactory = launchFactory;
        this.clock = clock;
    }

    @Transactional
    public CreatedJob create(CreateJob command) {
        Instant now = clock.instant();
        JobRecord job = store.create(command, now);
        if (command.descriptionText() != null) {
            return new CreatedJob(job, null);
        }
        WorkflowLaunchResult run = workflowLauncher.launch(
                launchFactory.command(null, job, AiQualityMode.ECONOMY));
        store.attachLatestRun(command.userId(), job.id(), run.agentRunId(), now);
        return new CreatedJob(
                store.findActive(command.userId(), job.id()).orElseThrow(),
                run);
    }

    public record CreatedJob(JobRecord job, WorkflowLaunchResult run) {}
}
