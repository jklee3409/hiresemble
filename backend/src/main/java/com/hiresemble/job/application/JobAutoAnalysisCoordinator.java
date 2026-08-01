package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutoAnalysisRequest;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutoAnalysisRequestedEvent;
import com.hiresemble.job.infrastructure.JobAutoAnalysisProperties;
import com.hiresemble.job.infrastructure.JobAutoAnalysisStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JobAutoAnalysisCoordinator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(JobAutoAnalysisCoordinator.class);

    private static final SafeError NEEDS_JOB_TEXT = new SafeError(
            "INSUFFICIENT_JOB_DATA",
            "공고 내용을 조금 더 채우면 자동 분석을 이어갈 수 있어요.");
    private static final SafeError BUDGET_LIMIT = new SafeError(
            "RATE_OR_BUDGET_LIMIT_EXCEEDED",
            "현재 사용할 수 있는 AI 분석 한도를 확인해 주세요.");
    private static final SafeError STATE_CONFLICT = new SafeError(
            "RESOURCE_STATE_CONFLICT",
            "현재 공고 상태를 확인한 뒤 다시 분석해 주세요.");
    private static final SafeError TEMPORARY_FAILURE = new SafeError(
            "AUTO_ANALYSIS_TEMPORARILY_UNAVAILABLE",
            "자동 분석을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");

    private final JobAutoAnalysisStore store;
    private final JobAnalysisApplicationService analysisService;
    private final AgentRunQueryPort runQuery;
    private final JobAutoAnalysisProperties properties;
    private final Clock clock;

    public JobAutoAnalysisCoordinator(
            JobAutoAnalysisStore store,
            JobAnalysisApplicationService analysisService,
            AgentRunQueryPort runQuery,
            JobAutoAnalysisProperties properties,
            Clock clock) {
        properties.validate();
        this.store = store;
        this.analysisService = analysisService;
        this.runQuery = runQuery;
        this.properties = properties;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequested(AutoAnalysisRequestedEvent event) {
        try {
            process(event.requestId());
        } catch (RuntimeException ignored) {
            // The committed request remains authoritative and reconciliation will recover it.
        }
    }

    @Scheduled(fixedDelayString = "${hiresemble.job.auto-analysis.scan-interval:5s}")
    public void reconcile() {
        reconcileOnce();
    }

    public void reconcileOnce() {
        for (int index = 0; index < properties.getBatchSize(); index++) {
            Instant now = clock.instant();
            Optional<AutoAnalysisRequest> request =
                    store.claimNext(now, properties.getLeaseDuration());
            if (request.isEmpty()) {
                return;
            }
            processClaimed(request.orElseThrow());
        }
    }

    public void process(UUID requestId) {
        Instant now = clock.instant();
        store.claim(requestId, now, properties.getLeaseDuration())
                .ifPresent(this::processClaimed);
    }

    private void processClaimed(AutoAnalysisRequest request) {
        Instant now = clock.instant();
        try {
            Optional<AgentRunSnapshot> existing =
                    runQuery.findByOwner(request.userId(), request.id());
            if (existing.isPresent()) {
                AgentRunSnapshot run = existing.orElseThrow();
                if (!matches(request, run)) {
                    blockOrRetry(request, TEMPORARY_FAILURE, now, false);
                    return;
                }
                store.markLaunched(request.id(), request.claimToken(), run.id(), now);
                return;
            }
            WorkflowLaunchResult launched = analysisService.launchAutomatic(
                    request.userId(),
                    request.jobId(),
                    request.jobVersion(),
                    request.id());
            if (!request.id().equals(launched.agentRunId())) {
                blockOrRetry(request, TEMPORARY_FAILURE, now, false);
                return;
            }
            store.markLaunched(
                    request.id(), request.claimToken(), launched.agentRunId(), now);
        } catch (BusinessException exception) {
            LOGGER.warn(
                    "Automatic job analysis request {} could not launch with safe code {}",
                    request.id(),
                    exception.errorCode().code());
            handleBusinessFailure(request, exception, now);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Automatic job analysis request {} will be reconciled after {}",
                    request.id(),
                    exception.getClass().getSimpleName());
            blockOrRetry(request, TEMPORARY_FAILURE, now, true);
        }
    }

    private void handleBusinessFailure(
            AutoAnalysisRequest request, BusinessException exception, Instant now) {
        ErrorCode code = exception.errorCode();
        if (code == ErrorCode.RESOURCE_NOT_FOUND
                || code == ErrorCode.RESOURCE_VERSION_CONFLICT) {
            store.markSuperseded(request.id(), request.claimToken(), now);
            return;
        }
        if (code == ErrorCode.INSUFFICIENT_JOB_DATA) {
            store.markBlocked(request.id(), request.claimToken(), NEEDS_JOB_TEXT, now);
            return;
        }
        if (code == ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED) {
            store.markBlocked(request.id(), request.claimToken(), BUDGET_LIMIT, now);
            return;
        }
        if (code == ErrorCode.RESOURCE_STATE_CONFLICT) {
            store.markBlocked(request.id(), request.claimToken(), STATE_CONFLICT, now);
            return;
        }
        blockOrRetry(request, TEMPORARY_FAILURE, now, true);
    }

    private void blockOrRetry(
            AutoAnalysisRequest request, SafeError error, Instant now, boolean retryable) {
        if (retryable && request.attemptCount() < properties.getMaxAttempts()) {
            store.releaseForRetry(
                    request.id(),
                    request.claimToken(),
                    now.plus(properties.getRetryDelay()),
                    now);
            return;
        }
        store.markBlocked(request.id(), request.claimToken(), error, now);
    }

    private boolean matches(AutoAnalysisRequest request, AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.JOB_ANALYSIS
                && "JOB".equals(run.resourceType())
                && request.jobId().equals(run.resourceId())
                && run.requestedQualityMode() == AiQualityMode.BALANCED;
    }
}
