package com.hiresemble.agentrun.application.service;

import com.hiresemble.agentrun.application.model.AgentRunPage;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunHistoryDeletionPort;
import com.hiresemble.agentrun.application.port.AgentRunHistoryDeletionContributor;
import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.query.AgentRunListCriteria;
import com.hiresemble.agentrun.application.query.AgentRunSort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.util.List;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunApplicationService {

    private final AgentRunQueryPort queryPort;
    private final AgentRunHistoryDeletionPort historyDeletionPort;
    private final List<AgentRunResourceOwnerResolver> resourceResolvers;
    private final List<AgentRunHistoryDeletionContributor> deletionContributors;

    @Autowired
    public AgentRunApplicationService(
            AgentRunQueryPort queryPort,
            AgentRunHistoryDeletionPort historyDeletionPort,
            List<AgentRunResourceOwnerResolver> resourceResolvers,
            List<AgentRunHistoryDeletionContributor> deletionContributors) {
        this.queryPort = queryPort;
        this.historyDeletionPort = historyDeletionPort;
        this.resourceResolvers = List.copyOf(resourceResolvers);
        this.deletionContributors = List.copyOf(deletionContributors);
    }

    public AgentRunApplicationService(
            AgentRunQueryPort queryPort,
            AgentRunHistoryDeletionPort historyDeletionPort,
            List<AgentRunResourceOwnerResolver> resourceResolvers) {
        this(queryPort, historyDeletionPort, resourceResolvers, List.of());
    }

    public AgentRunPage list(
            UUID userId,
            List<WorkflowType> workflowTypes,
            List<AgentRunStatus> statuses,
            String resourceType,
            UUID resourceId,
            Boolean retryable,
            int page,
            int size,
            String sort) {
        if (page < 0 || size < 1 || size > 100 || (resourceType == null) != (resourceId == null)
                || (resourceType != null && (resourceType.isBlank() || resourceType.length() > 50))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AgentRunSort parsedSort = switch (sort) {
            case "queuedAt,desc" -> AgentRunSort.QUEUED_AT_DESC;
            case "updatedAt,desc" -> AgentRunSort.UPDATED_AT_DESC;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
        if (resourceType != null) {
            AgentRunResourceOwnerResolver resolver = resourceResolvers.stream()
                    .filter(candidate -> candidate.supports(resourceType))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            resolver.requireActiveOwner(userId, resourceType, resourceId);
        }
        return queryPort.findPage(new AgentRunListCriteria(
                userId,
                workflowTypes == null ? List.of() : workflowTypes,
                statuses == null ? List.of() : statuses,
                resourceType,
                resourceId,
                retryable,
                page,
                size,
                parsedSort));
    }

    public AgentRunSnapshot detail(UUID userId, UUID agentRunId) {
        return queryPort.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public void delete(UUID userId, UUID agentRunId, Instant deletedAt) {
        deleteAll(userId, Set.of(agentRunId), deletedAt);
    }

    @Transactional
    public void deleteAll(UUID userId, Set<UUID> agentRunIds, Instant deletedAt) {
        if (userId == null || agentRunIds == null || deletedAt == null
                || agentRunIds.isEmpty() || agentRunIds.size() > 100
                || agentRunIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        LinkedHashSet<UUID> orderedIds = new LinkedHashSet<>(agentRunIds);
        List<AgentRunSnapshot> runs = orderedIds.stream()
                .map(runId -> queryPort.findByOwner(userId, runId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                .toList();
        if (runs.stream().anyMatch(run -> !run.status().isTerminal())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        for (AgentRunSnapshot run : runs) {
            deletionContributors.stream()
                    .filter(contributor -> contributor.supports(run))
                    .findFirst()
                    .ifPresent(contributor -> contributor.beforeDelete(run, deletedAt));
            historyDeletionPort.softDeleteTerminalRuns(
                    userId, Set.of(run.id()), deletedAt);
        }
    }
}
