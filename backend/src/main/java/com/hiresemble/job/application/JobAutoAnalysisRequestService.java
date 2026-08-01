package com.hiresemble.job.application;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutoAnalysisRequest;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutoAnalysisRequestedEvent;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutomaticAnalysisProjection;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutomaticAnalysisState;
import com.hiresemble.job.domain.JobAutoAnalysisStatus;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobAutoAnalysisStore;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobAutoAnalysisRequestService {

    private final JobAutoAnalysisStore store;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JobAutoAnalysisRequestService(
            JobAutoAnalysisStore store, ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AutoAnalysisRequest enqueue(JobRecord job) {
        if (!JobPolicy.hasUsableText(job.descriptionText()) || job.contentHash() == null) {
            throw new IllegalArgumentException("automatic analysis requires usable job text");
        }
        AutoAnalysisRequest request = store.enqueue(job, clock.instant());
        if (request.status() == JobAutoAnalysisStatus.PENDING) {
            events.publishEvent(new AutoAnalysisRequestedEvent(request.id()));
        }
        return request;
    }

    @Transactional(readOnly = true)
    public AutomaticAnalysisProjection projection(JobRecord job) {
        if (!JobPolicy.hasUsableText(job.descriptionText()) || job.contentHash() == null) {
            return new AutomaticAnalysisProjection(
                    AutomaticAnalysisState.WAITING_FOR_CONTENT,
                    AiQualityMode.BALANCED,
                    null,
                    null);
        }
        return store.findForRevision(job.userId(), job.id(), job.version())
                .map(this::projection)
                .orElseGet(() -> new AutomaticAnalysisProjection(
                        AutomaticAnalysisState.NOT_REQUESTED,
                        AiQualityMode.BALANCED,
                        null,
                        null));
    }

    private AutomaticAnalysisProjection projection(AutoAnalysisRequest request) {
        AutomaticAnalysisState state = switch (request.status()) {
            case PENDING, CLAIMED -> AutomaticAnalysisState.PENDING;
            case LAUNCHED -> AutomaticAnalysisState.LAUNCHED;
            case BLOCKED -> AutomaticAnalysisState.BLOCKED;
            case SUPERSEDED -> AutomaticAnalysisState.SUPERSEDED;
        };
        return new AutomaticAnalysisProjection(
                state, request.qualityMode(), request.agentRunId(), request.safeError());
    }
}
