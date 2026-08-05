package com.hiresemble.coverletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.CoverLetterApplicationService;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Detail;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceUse;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Question;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationIssue;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedClaim;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterEvidenceUsageType;
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

class CoverLetterApplicationIntegrationTest extends PostgresIntegrationTest {

    @Autowired private CoverLetterApplicationService service;
    @Autowired private CoverLetterStore store;

    @Test
    void questionVersionRestoreFinalizeAndArchiveLifecycleRemainOwnerScoped() {
        UUID owner = seedUser("cover-owner@example.com");
        UUID other = seedUser("cover-other@example.com");
        UUID job = seedJob(owner, "cover-lifecycle");
        Detail created = service.create(
                        owner, job, "Backend application", "cover-create-key-0001")
                .body();
        UUID coverId = created.summary().id();

        assertThatThrownBy(() -> service.create(
                        owner, job, "Duplicate active", "cover-create-key-0002"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.ACTIVE_COVER_LETTER_EXISTS));
        assertThatThrownBy(() -> service.detail(other, coverId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        Question first = service.addQuestion(
                owner, coverId, 1, "첫 번째 문항", 1000, null, 0);
        long coverVersion = service.detail(owner, coverId).summary().version();
        Question second = service.addQuestion(
                owner, coverId, 2, "두 번째 문항", 1000, "memo", coverVersion);
        coverVersion = service.detail(owner, coverId).summary().version();
        Question third = service.addQuestion(
                owner, coverId, 3, "세 번째 문항", null, null, coverVersion);
        coverVersion = service.detail(owner, coverId).summary().version();

        Detail reordered = service.reorderQuestions(
                owner, coverId, List.of(third.id(), first.id(), second.id()), coverVersion);
        assertThat(reordered.questions().stream()
                        .filter(question -> question.deletedAt() == null)
                        .map(Question::id))
                .containsExactly(third.id(), first.id(), second.id());
        assertThat(reordered.questions().stream()
                        .filter(question -> question.deletedAt() == null)
                        .map(Question::questionOrder))
                .containsExactly(1, 2, 3);

        AnswerVersion firstVersion =
                service.saveUserVersion(owner, first.id(), content("첫 답변"), null);
        AnswerVersion secondVersion = service.saveUserVersion(
                owner, first.id(), content("둘째\r\n답변\u00A0"), firstVersion.id());
        AnswerVersion restored = service.restoreVersion(
                owner, first.id(), firstVersion.id(), secondVersion.id());
        assertThat(restored.sourceType()).isEqualTo(CoverLetterVersionSource.RESTORED);
        assertThat(restored.parentVersionId()).isEqualTo(secondVersion.id());
        assertThat(restored.restoredFromVersionId()).isEqualTo(firstVersion.id());
        assertThat(restored.plainText()).isEqualTo("첫 답변");
        assertThat(service.listVersions(owner, first.id(), 0, 20, "versionNo,desc")
                        .items())
                .extracting(AnswerVersion::versionNo)
                .containsExactly(3, 2, 1);

        Detail beforeDeletes = service.detail(owner, coverId);
        Question currentSecond = activeQuestion(beforeDeletes, second.id());
        Question currentThird = activeQuestion(beforeDeletes, third.id());
        service.deleteQuestion(
                owner, coverId, currentSecond.id(), currentSecond.version());
        service.deleteQuestion(
                owner, coverId, currentThird.id(), currentThird.version());
        store.insertVerification(
                UUID.randomUUID(),
                owner,
                restored.id(),
                new VerificationResult(
                        VerificationStatus.PASSED, List.of(), List.of(), List.of()),
                null,
                Instant.now());

        Detail finalized = service.finalizeCover(
                owner,
                coverId,
                service.detail(owner, coverId).summary().version(),
                List.of());
        assertThat(finalized.summary().status()).isEqualTo(CoverLetterStatus.FINALIZED);
        assertThat(finalized.summary().finalizedAt()).isNotNull();

        Question currentFirst = activeQuestion(finalized, first.id());
        service.updateQuestion(
                owner,
                coverId,
                first.id(),
                currentFirst.questionOrder(),
                "수정된 첫 번째 문항",
                1000,
                null,
                currentFirst.version());
        Detail draftAgain = service.detail(owner, coverId);
        assertThat(draftAgain.summary().status()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(draftAgain.summary().finalizedAt()).isEqualTo(finalized.summary().finalizedAt());

        Detail archived = service.archive(
                owner, coverId, draftAgain.summary().version());
        assertThat(archived.summary().status()).isEqualTo(CoverLetterStatus.ARCHIVED);
        assertThatThrownBy(() -> service.saveUserVersion(
                        owner, first.id(), content("보관 후 변경"), restored.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.COVER_LETTER_ARCHIVED));

        Detail replacement = service.create(
                        owner, job, "Replacement", "cover-create-key-0003")
                .body();
        assertThatThrownBy(() -> service.unarchive(
                        owner, coverId, archived.summary().version()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.ACTIVE_COVER_LETTER_EXISTS));
        Detail replacementArchived = service.archive(
                owner, replacement.summary().id(), replacement.summary().version());
        assertThat(replacementArchived.summary().status())
                .isEqualTo(CoverLetterStatus.ARCHIVED);
        Detail unarchived = service.unarchive(
                owner, coverId, archived.summary().version());
        assertThat(unarchived.summary().status()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(unarchived.summary().finalizedAt()).isEqualTo(finalized.summary().finalizedAt());
    }

    @Test
    void verificationDtoResolvesCurrentEvidenceReferencedByAUserEditedAnswerCheck() {
        UUID owner = seedUser("cover-verification-reference@example.com");
        UUID job = seedJob(owner, "verification-reference");
        Detail cover = service.create(
                        owner, job, "Verification reference", "cover-create-reference-0001")
                .body();
        Question question = service.addQuestion(
                owner, cover.summary().id(), 1, "지원 동기를 작성해 주세요.", 1000, null, 0);
        AnswerVersion answer =
                service.saveUserVersion(owner, question.id(), content("사용자 편집 답변"), null);
        UUID evidenceId = seedEvidence(owner, "Approved reference");
        store.insertVerification(
                UUID.randomUUID(),
                owner,
                answer.id(),
                new VerificationResult(
                        VerificationStatus.WARNING,
                        List.of(new VerificationIssue(
                                VerificationIssueCode.UNVERIFIED_CLAIM,
                                IssueSeverity.WARNING,
                                "승인 근거로 표현을 구체화하세요.",
                                "사용자 편집 답변",
                                List.of(evidenceId))),
                        List.of("승인 근거를 반영한 제안"),
                        List.of(new VerifiedClaim(
                                "현재 승인 근거와 연결됩니다.",
                                true,
                                List.of(evidenceId)))),
                null,
                Instant.now());

        var verification = service.listVerifications(
                        owner, answer.id(), 0, 20, "createdAt,desc")
                .items()
                .getFirst();
        assertThat(verification.evidenceReferences())
                .extracting(reference -> reference.id())
                .containsExactly(evidenceId);
        assertThat(verification.issues().getFirst().evidenceIds())
                .containsExactly(evidenceId);
        assertThat(verification.verifiedClaims().getFirst().evidenceIds())
                .containsExactly(evidenceId);
    }

    @Test
    void coverLetterProvenanceContributesToDocumentEvidenceDeletionReferences() {
        UUID owner = seedUser("cover-provenance-reference@example.com");
        UUID job = seedJob(owner, "provenance-reference");
        Detail cover = service.create(
                        owner, job, "Provenance reference", "cover-create-reference-0002")
                .body();
        Question question = service.addQuestion(
                owner, cover.summary().id(), 1, "경험을 작성해 주세요.", 1000, null, 0);
        AnswerVersion answer =
                service.saveUserVersion(owner, question.id(), content("근거 연결 답변"), null);
        UUID evidenceId = seedEvidence(owner, "Cover provenance");
        store.insertEvidenceLinks(
                owner,
                answer.id(),
                List.of(new EvidenceUse(
                        evidenceId,
                        "근거 연결 답변",
                        CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)),
                Instant.now());

        assertThat(service.isReferenced(owner, evidenceId)).isTrue();
        assertThat(service.isReferenced(owner, UUID.randomUUID())).isFalse();
    }

    @Test
    void userEditedVersionPreservesOnlyParentClaimExcerptsStillPresent() {
        UUID owner = seedUser("cover-user-edit-provenance@example.com");
        UUID job = seedJob(owner, "user-edit-provenance");
        Detail cover = service.create(
                        owner, job, "User edit provenance", "cover-user-edit-provenance-0001")
                .body();
        Question question = service.addQuestion(
                owner, cover.summary().id(), 1, "경험을 작성해 주세요.", 1000, null, 0);
        AnswerVersion parent = service.saveUserVersion(
                owner,
                question.id(),
                content("Spring Boot API를 구현했습니다. 성능을 30% 개선했습니다."),
                null);
        UUID retainedEvidence = seedEvidence(owner, "Retained evidence");
        UUID removedEvidence = seedEvidence(owner, "Removed evidence");
        store.insertEvidenceLinks(
                owner,
                parent.id(),
                List.of(
                        new EvidenceUse(
                                retainedEvidence,
                                "Spring Boot API를 구현했습니다.",
                                CoverLetterEvidenceUsageType.SUPPORTING_CLAIM),
                        new EvidenceUse(
                                removedEvidence,
                                "성능을 30% 개선했습니다.",
                                CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)),
                Instant.now());

        AnswerVersion edited = service.saveUserVersion(
                owner,
                question.id(),
                content("Spring Boot API를 구현했습니다. 운영 안정성을 점검했습니다."),
                parent.id());

        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT profile_evidence_id
                        FROM cover_letter_evidence_links
                        WHERE user_id=? AND answer_version_id=?
                        """,
                        UUID.class,
                        owner,
                        edited.id()))
                .containsExactly(retainedEvidence)
                .doesNotContain(removedEvidence);
    }

    private Question activeQuestion(Detail detail, UUID questionId) {
        return detail.questions().stream()
                .filter(question -> question.id().equals(questionId))
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

    private UUID seedUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,'hash','Cover User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """,
                userId,
                email);
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

    private UUID seedEvidence(UUID userId, String title) {
        UUID evidenceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    ?,?,'MANUAL',NULL,NULL,'EXPERIENCE',?,'Built Java services.',
                    '{}',NULL,'VERIFIED',now(),NULL,0,now(),now()
                )
                """,
                evidenceId,
                userId,
                title);
        return evidenceId;
    }

    private UUID seedJob(UUID userId, String key) {
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
                "https://jobs.example.com/" + key,
                "https://jobs.example.com/" + key);
        return jobId;
    }
}
