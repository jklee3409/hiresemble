package com.hiresemble.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.JobRecords.StatusChange;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobDomainTest {

    private static final Instant FIRST_SUBMISSION = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");

    @Test
    void everyAllowedBusinessTransitionKeepsTheTwoStateAxesIndependent() {
        assertThat(JobPolicy.allowed(JobStatus.IN_PROGRESS, JobStatus.SUBMITTED)).isTrue();
        assertThat(JobPolicy.allowed(JobStatus.IN_PROGRESS, JobStatus.CLOSED)).isTrue();
        assertThat(JobPolicy.allowed(JobStatus.SUBMITTED, JobStatus.CLOSED)).isTrue();
        assertThat(JobPolicy.allowed(JobStatus.CLOSED, JobStatus.IN_PROGRESS)).isTrue();
        assertThat(JobPolicy.allowed(JobStatus.CLOSED, JobStatus.SUBMITTED)).isTrue();

        assertThat(JobPolicy.allowed(JobStatus.IN_PROGRESS, JobStatus.IN_PROGRESS)).isFalse();
        assertThat(JobPolicy.allowed(JobStatus.SUBMITTED, JobStatus.IN_PROGRESS)).isFalse();
        assertThat(JobPolicy.allowed(JobStatus.SUBMITTED, JobStatus.SUBMITTED)).isFalse();
        assertThat(JobPolicy.allowed(JobStatus.CLOSED, JobStatus.CLOSED)).isFalse();

        assertThat(JobStatus.values())
                .containsExactly(JobStatus.IN_PROGRESS, JobStatus.SUBMITTED, JobStatus.CLOSED);
        assertThat(JobExtractionStatus.values())
                .containsExactly(
                        JobExtractionStatus.QUEUED,
                        JobExtractionStatus.EXTRACTING,
                        JobExtractionStatus.EXTRACTED,
                        JobExtractionStatus.MANUAL_INPUT_PROVIDED,
                        JobExtractionStatus.NEEDS_MANUAL_INPUT,
                        JobExtractionStatus.FAILED);
    }

    @Test
    void firstSubmissionIsPermanentAndReopenOnlyClearsCurrentCloseFields() {
        StatusChange firstSubmit = JobPolicy.transition(
                JobStatus.IN_PROGRESS, JobStatus.SUBMITTED, null, FIRST_SUBMISSION, null);
        assertThat(firstSubmit.submittedAt()).isEqualTo(FIRST_SUBMISSION);
        assertThat(firstSubmit.closedAt()).isNull();

        StatusChange close = JobPolicy.transition(
                JobStatus.SUBMITTED,
                JobStatus.CLOSED,
                firstSubmit.submittedAt(),
                NOW,
                ClosedReason.USER_CLOSED);
        assertThat(close.submittedAt()).isEqualTo(FIRST_SUBMISSION);
        assertThat(close.closedAt()).isEqualTo(NOW);
        assertThat(close.closedReason()).isEqualTo(ClosedReason.USER_CLOSED);

        StatusChange reopen = JobPolicy.transition(
                JobStatus.CLOSED,
                JobStatus.IN_PROGRESS,
                close.submittedAt(),
                NOW.plusSeconds(60),
                null);
        assertThat(reopen.submittedAt()).isEqualTo(FIRST_SUBMISSION);
        assertThat(reopen.closedAt()).isNull();
        assertThat(reopen.closedReason()).isNull();

        StatusChange resubmit = JobPolicy.transition(
                JobStatus.CLOSED,
                JobStatus.SUBMITTED,
                FIRST_SUBMISSION,
                NOW.plusSeconds(120),
                null);
        assertThat(resubmit.submittedAt()).isEqualTo(FIRST_SUBMISSION);
    }

    @Test
    void forbiddenTransitionAndMissingCloseReasonUseSharedConflict() {
        assertThatThrownBy(() -> JobPolicy.transition(
                        JobStatus.SUBMITTED,
                        JobStatus.IN_PROGRESS,
                        FIRST_SUBMISSION,
                        NOW,
                        null))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.errorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CONFLICT));
        assertThatThrownBy(() -> JobPolicy.transition(
                        JobStatus.IN_PROGRESS, JobStatus.CLOSED, null, NOW, null))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.errorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CONFLICT));
    }
}
