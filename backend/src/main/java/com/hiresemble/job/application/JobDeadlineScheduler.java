package com.hiresemble.job.application;

import com.hiresemble.job.infrastructure.JobDeadlineSchedulerProperties;
import com.hiresemble.job.infrastructure.JobStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobDeadlineScheduler {

    private final JobStore store;
    private final JobStatusService statusService;
    private final JobDeadlineSchedulerProperties properties;
    private final Clock clock;

    public JobDeadlineScheduler(
            JobStore store,
            JobStatusService statusService,
            JobDeadlineSchedulerProperties properties,
            Clock clock) {
        this.store = store;
        this.statusService = statusService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${hiresemble.job-deadline-scheduler.cron:0 0 * * * *}")
    public int closeExpiredJobs() {
        Instant cutoff = clock.instant();
        UUID cursor = null;
        int closed = 0;
        while (true) {
            List<UUID> ids = store.findDueIds(cutoff, cursor, properties.getBatchSize());
            if (ids.isEmpty()) {
                return closed;
            }
            for (UUID id : ids) {
                if (statusService.closeExpired(id, cutoff)) {
                    closed++;
                }
            }
            cursor = ids.getLast();
            if (ids.size() < properties.getBatchSize()) {
                return closed;
            }
        }
    }
}
