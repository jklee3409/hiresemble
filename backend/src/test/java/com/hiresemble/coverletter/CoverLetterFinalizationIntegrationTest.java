package com.hiresemble.coverletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.CoverLetterApplicationService;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Detail;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Question;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationIssue;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.coverletter.infrastructure.CoverLetterStore;
import com.hiresemble.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CoverLetterFinalizationIntegrationTest extends PostgresIntegrationTest {

    @Autowired private CoverLetterApplicationService service;
    @Autowired private CoverLetterStore store;

    @Test
    void finalizationRequiresCurrentLengthFreshTerminalVerificationAndExactWarningIds() {
        UUID userId = seedUser();
        UUID jobId = seedJob(userId);
        Detail created =
                service.create(userId, jobId, "Finalization contract", "finalization-create-key")
                        .body();
        UUID coverLetterId = created.summary().id();

        assertNotFinalizable(
                () -> service.finalizeCover(userId, coverLetterId, 0, List.of()));

        Question question =
                service.addQuestion(userId, coverLetterId, 1, "지원 동기를 설명해 주세요.", 4, null, 0);
        assertNotFinalizable(() -> service.finalizeCover(
                userId,
                coverLetterId,
                service.detail(userId, coverLetterId).summary().version(),
                List.of()));

        AnswerVersion tooLong = store.insertAnswer(
                userId,
                question.id(),
                null,
                null,
                content("12345"),
                "12345",
                5,
                CoverLetterVersionSource.USER_EDITED,
                AnswerCreatedBy.USER,
                Instant.parse("2026-07-30T00:00:00Z"));
        store.insertVerification(
                UUID.randomUUID(),
                userId,
                tooLong.id(),
                result(VerificationStatus.PASSED),
                null,
                Instant.parse("2026-07-30T00:00:01Z"));
        assertNotFinalizable(() -> service.finalizeCover(
                userId,
                coverLetterId,
                service.detail(userId, coverLetterId).summary().version(),
                List.of()));

        Question currentQuestion = activeQuestion(service.detail(userId, coverLetterId));
        service.updateQuestion(
                userId,
                coverLetterId,
                question.id(),
                currentQuestion.questionOrder(),
                currentQuestion.questionText(),
                10,
                currentQuestion.memo(),
                currentQuestion.version());

        UUID pendingId = UUID.randomUUID();
        store.insertVerification(
                pendingId,
                userId,
                tooLong.id(),
                result(VerificationStatus.PENDING),
                null,
                Instant.parse("2026-07-30T00:00:02Z"));
        assertNotFinalizable(() -> service.finalizeCover(
                userId,
                coverLetterId,
                service.detail(userId, coverLetterId).summary().version(),
                List.of()));

        UUID failedId = UUID.randomUUID();
        store.insertVerification(
                failedId,
                userId,
                tooLong.id(),
                result(VerificationStatus.FAILED),
                null,
                Instant.parse("2026-07-30T00:00:03Z"));
        assertNotFinalizable(() -> service.finalizeCover(
                userId,
                coverLetterId,
                service.detail(userId, coverLetterId).summary().version(),
                List.of()));

        UUID warningId = UUID.randomUUID();
        store.insertVerification(
                warningId,
                userId,
                tooLong.id(),
                warningResult(),
                null,
                Instant.parse("2026-07-30T00:00:04Z"));
        long warningCoverVersion =
                service.detail(userId, coverLetterId).summary().version();
        assertNotFinalizable(() -> service.finalizeCover(
                userId, coverLetterId, warningCoverVersion, List.of()));
        assertNotFinalizable(() -> service.finalizeCover(
                userId, coverLetterId, warningCoverVersion, List.of(failedId)));

        Detail warningFinalized = service.finalizeCover(
                userId, coverLetterId, warningCoverVersion, List.of(warningId));
        assertThat(warningFinalized.summary().status()).isEqualTo(CoverLetterStatus.FINALIZED);

        AnswerVersion edited = service.saveUserVersion(
                userId, question.id(), content("수정 답변"), tooLong.id());
        Detail stale = service.detail(userId, coverLetterId);
        assertThat(stale.summary().status()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(stale.summary().finalizedAt())
                .isEqualTo(warningFinalized.summary().finalizedAt());
        assertNotFinalizable(() -> service.finalizeCover(
                userId, coverLetterId, stale.summary().version(), List.of(warningId)));

        store.insertVerification(
                UUID.randomUUID(),
                userId,
                edited.id(),
                result(VerificationStatus.PASSED),
                null,
                Instant.parse("2026-07-30T00:00:05Z"));
        Detail passedFinalized = service.finalizeCover(
                userId,
                coverLetterId,
                service.detail(userId, coverLetterId).summary().version(),
                List.of());
        assertThat(passedFinalized.summary().status()).isEqualTo(CoverLetterStatus.FINALIZED);
    }

    @Test
    void questionOrderAndAnswerCasRejectIncompleteForeignDuplicateAndStaleInputs() {
        UUID ownerId = seedUser();
        UUID otherId = seedUser();
        Detail ownerCover = service.create(
                        ownerId, seedJob(ownerId), "Owner cover", "owner-question-contract")
                .body();
        Detail otherCover = service.create(
                        otherId, seedJob(otherId), "Other cover", "other-question-contract")
                .body();
        Question first = service.addQuestion(
                ownerId, ownerCover.summary().id(), 1, "첫 문항", 4, null, 0);
        Question second = service.addQuestion(
                ownerId, ownerCover.summary().id(), 2, "둘째 문항", null, null, 1);
        Question foreign = service.addQuestion(
                otherId, otherCover.summary().id(), 1, "타 사용자 문항", null, null, 0);
        long ownerVersion =
                service.detail(ownerId, ownerCover.summary().id()).summary().version();

        assertValidation(() -> service.reorderQuestions(
                ownerId, ownerCover.summary().id(), List.of(first.id()), ownerVersion));
        assertValidation(() -> service.reorderQuestions(
                ownerId,
                ownerCover.summary().id(),
                List.of(first.id(), first.id()),
                ownerVersion));
        assertValidation(() -> service.reorderQuestions(
                ownerId,
                ownerCover.summary().id(),
                List.of(first.id(), foreign.id()),
                ownerVersion));
        assertThatThrownBy(() -> service.updateQuestion(
                        ownerId,
                        ownerCover.summary().id(),
                        foreign.id(),
                        1,
                        "변경",
                        null,
                        null,
                        foreign.version()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        AnswerVersion firstAnswer =
                service.saveUserVersion(ownerId, first.id(), content("1234"), null);
        assertValidation(() ->
                service.saveUserVersion(ownerId, first.id(), content("12345"), firstAnswer.id()));
        assertThatThrownBy(() ->
                        service.saveUserVersion(ownerId, first.id(), content("123"), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RESOURCE_VERSION_CONFLICT));
        assertThatThrownBy(() -> service.deleteQuestion(
                        ownerId,
                        ownerCover.summary().id(),
                        second.id(),
                        second.version() + 1))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RESOURCE_VERSION_CONFLICT));
    }

    private VerificationResult warningResult() {
        return new VerificationResult(
                VerificationStatus.WARNING,
                List.of(new VerificationIssue(
                        VerificationIssueCode.OTHER,
                        IssueSeverity.WARNING,
                        "사용자 확인이 필요한 표현입니다.",
                        "수정 답변",
                        List.of())),
                List.of("표현을 구체화해 주세요."),
                List.of());
    }

    private VerificationResult result(VerificationStatus status) {
        return new VerificationResult(status, List.of(), List.of(), List.of());
    }

    private void assertNotFinalizable(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.COVER_LETTER_NOT_FINALIZABLE));
    }

    private void assertValidation(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private Question activeQuestion(Detail detail) {
        return detail.questions().stream()
                .filter(question -> question.deletedAt() == null)
                .findFirst()
                .orElseThrow();
    }

    private TipTapDocumentDto content(String text) {
        return new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new TipTapNodeDto(
                                "text", text, List.of(), List.of())))));
    }

    private UUID seedUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,'hash','Finalize User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """,
                userId,
                "cover-finalize-" + userId + "@example.com");
        jdbcTemplate.update(
                """
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?, ?, NULL, NULL, '[]', '[]', '[]', NULL, 0, now(), now())
                """,
                UUID.randomUUID(),
                userId);
        return userId;
    }

    private UUID seedJob(UUID userId) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'Backend Engineer','Backend Engineer',
                    'Build reliable Java services.','USER_ENTERED','UNKNOWN','IN_PROGRESS',
                    'MANUAL_INPUT_PROVIDED',0,now(),now()
                )
                """,
                jobId,
                userId,
                "https://jobs.example.com/finalize-" + jobId,
                "https://jobs.example.com/finalize-" + jobId);
        return jobId;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
