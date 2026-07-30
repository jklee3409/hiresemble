package com.hiresemble.coverletter.api;

import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterAnswerVersionDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterDetailDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterQuestionDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterSummaryDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.JobRefDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.VerificationDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.VerificationIssueDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.VerifiedClaimDto;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Detail;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceReference;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Question;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Summary;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.job.api.JobAnalysisDtos.EvidenceRefDto;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CoverLetterApiMapper {

    public CoverLetterSummaryDto summary(Summary value) {
        return new CoverLetterSummaryDto(
                value.id(),
                new JobRefDto(
                        value.job().id(),
                        value.job().companyName(),
                        value.job().positionName(),
                        value.job().title()),
                value.title(),
                value.status(),
                value.questionCount(),
                value.answeredQuestionCount(),
                value.latestVerificationStatus(),
                value.warningCount(),
                value.canEdit(),
                value.canArchive(),
                value.canUnarchive(),
                value.canFinalize(),
                value.version(),
                value.finalizedAt(),
                value.archivedAt(),
                value.createdAt(),
                value.updatedAt());
    }

    public CoverLetterDetailDto detail(Detail value) {
        Summary summary = value.summary();
        return new CoverLetterDetailDto(
                summary.id(),
                new JobRefDto(
                        summary.job().id(),
                        summary.job().companyName(),
                        summary.job().positionName(),
                        summary.job().title()),
                summary.title(),
                summary.status(),
                summary.questionCount(),
                summary.answeredQuestionCount(),
                summary.latestVerificationStatus(),
                summary.warningCount(),
                summary.canEdit(),
                summary.canArchive(),
                summary.canUnarchive(),
                summary.canFinalize(),
                summary.version(),
                summary.finalizedAt(),
                summary.archivedAt(),
                summary.createdAt(),
                summary.updatedAt(),
                value.questions().stream().map(this::question).toList());
    }

    public CoverLetterQuestionDto question(Question value) {
        return new CoverLetterQuestionDto(
                value.id(),
                value.questionOrder(),
                value.questionText(),
                value.maxLength(),
                value.memo(),
                value.currentAnswer() == null ? null : answer(value.currentAnswer()),
                value.latestVerification() == null
                        ? null
                        : verification(value.latestVerification()),
                value.version(),
                value.deletedAt());
    }

    public CoverLetterAnswerVersionDto answer(AnswerVersion value) {
        return new CoverLetterAnswerVersionDto(
                value.id(),
                value.questionId(),
                value.parentVersionId(),
                value.restoredFromVersionId(),
                value.versionNo(),
                value.contentJson(),
                value.plainText(),
                value.characterCount(),
                value.sourceType(),
                value.current(),
                value.createdBy(),
                value.createdAt());
    }

    public VerificationDto verification(Verification value) {
        Map<UUID, EvidenceReference> evidence = value.evidenceReferences().stream()
                .collect(Collectors.toMap(
                        EvidenceReference::id,
                        Function.identity(),
                        (left, right) -> left));
        return new VerificationDto(
                value.id(),
                value.answerVersionId(),
                value.status(),
                value.issues().stream()
                        .map(issue -> new VerificationIssueDto(
                                issue.code(),
                                issue.severity(),
                                issue.message(),
                                issue.relatedText(),
                                issue.evidenceIds().stream()
                                        .map(evidence::get)
                                        .filter(java.util.Objects::nonNull)
                                        .map(this::evidence)
                                        .toList()))
                        .toList(),
                value.suggestions(),
                value.verifiedClaims().stream()
                        .map(claim -> new VerifiedClaimDto(
                                claim.claim(),
                                claim.supported(),
                                claim.evidenceIds().stream()
                                        .map(evidence::get)
                                        .filter(java.util.Objects::nonNull)
                                        .map(this::evidence)
                                        .toList()))
                        .toList(),
                value.evidenceReferences().stream().map(this::evidence).toList(),
                value.agentRunId(),
                value.createdAt());
    }

    private EvidenceRefDto evidence(EvidenceReference value) {
        return new EvidenceRefDto(
                value.id(),
                value.title(),
                value.evidenceCategory(),
                value.verificationStatus(),
                value.sourceType(),
                value.sourceDeleted());
    }
}
