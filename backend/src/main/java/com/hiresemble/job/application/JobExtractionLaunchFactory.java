package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.job.domain.JobDescriptionSource;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobAiCostProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Creates the canonical, safe Job extraction input snapshot for new and retry Runs. */
@Component
final class JobExtractionLaunchFactory {

    private static final String INPUT_POLICY_VERSION = "job-extraction-input-policy-v3";

    private final ObjectMapper objectMapper;
    private final JobAiCostProperties aiCost;

    JobExtractionLaunchFactory(ObjectMapper objectMapper, JobAiCostProperties aiCost) {
        this.objectMapper = objectMapper;
        this.aiCost = aiCost;
    }

    WorkflowLaunchCommand command(
            UUID requestedRunId, JobRecord job, AiQualityMode qualityMode) {
        String canonicalUrlHash = sha256(job.canonicalUrl());
        String overridesHash = overridesHash(job);
        var input = objectMapper.createObjectNode()
                .put("jobId", job.id().toString())
                .put("jobVersion", job.version())
                .put("canonicalUrlHash", canonicalUrlHash)
                .put("userOverridesHash", overridesHash)
                .put("inputPolicyVersion", INPUT_POLICY_VERSION);
        String canonicalInputHash = sha256(String.join(
                "|",
                INPUT_POLICY_VERSION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                job.userId().toString(),
                job.id().toString(),
                Long.toString(job.version()),
                job.canonicalUrl(),
                overridesHash,
                qualityMode.name()));
        return new WorkflowLaunchCommand(
                requestedRunId,
                job.userId(),
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                canonicalInputHash,
                input,
                qualityMode,
                aiCost.estimatedCostUsd(),
                aiCost.priceVersion(),
                new ResourceReference("JOB", job.id(), job.positionName()));
    }

    private String overridesHash(JobRecord job) {
        return sha256(String.join(
                "|",
                job.companyUserOverride() ? nullSafe(job.companyName()) : "-",
                job.titleUserOverride() ? nullSafe(job.title()) : "-",
                job.positionUserOverride() ? nullSafe(job.positionName()) : "-",
                job.descriptionSource() == JobDescriptionSource.USER_ENTERED
                        ? hashOrMissing(job.descriptionText()) : "-",
                job.deadlineUserOverride() && job.deadlineAt() != null
                        ? job.deadlineAt().toString() : "-"));
    }

    private String hashOrMissing(String value) {
        return value == null ? "-" : sha256(value);
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
