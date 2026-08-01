package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.job.domain.JobCommands.CreateJob;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobAiCostProperties;
import com.hiresemble.job.infrastructure.JobStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobCreationService {

    public static final String WORKFLOW_VERSION = "job-posting-extraction-v2";
    private final JobStore store;
    private final WorkflowLauncher workflowLauncher;
    private final ObjectMapper objectMapper;
    private final JobAiCostProperties aiCost;
    private final Clock clock;

    public JobCreationService(
            JobStore store,
            WorkflowLauncher workflowLauncher,
            ObjectMapper objectMapper,
            JobAiCostProperties aiCost,
            Clock clock) {
        this.store = store;
        this.workflowLauncher = workflowLauncher;
        this.objectMapper = objectMapper;
        this.aiCost = aiCost;
        this.clock = clock;
    }

    @Transactional
    public CreatedJob create(CreateJob command) {
        Instant now = clock.instant();
        JobRecord job = store.create(command, now);
        if (command.descriptionText() != null) {
            return new CreatedJob(job, null);
        }
        var input = objectMapper.createObjectNode()
                .put("jobId", job.id().toString())
                .put("jobVersion", job.version());
        String canonicalInputHash = sha256(String.join(
                "|",
                command.userId().toString(),
                job.id().toString(),
                Long.toString(job.version()),
                job.canonicalUrl()));
        WorkflowLaunchResult run = workflowLauncher.launch(new WorkflowLaunchCommand(
                command.userId(),
                WorkflowType.JOB_POSTING_EXTRACTION,
                WORKFLOW_VERSION,
                canonicalInputHash,
                input,
                AiQualityMode.ECONOMY,
                aiCost.estimatedCostUsd(),
                aiCost.priceVersion(),
                new ResourceReference("JOB", job.id(), job.positionName())));
        store.attachLatestRun(command.userId(), job.id(), run.agentRunId(), now);
        return new CreatedJob(
                store.findActive(command.userId(), job.id()).orElseThrow(),
                run);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CreatedJob(JobRecord job, WorkflowLaunchResult run) {}
}
