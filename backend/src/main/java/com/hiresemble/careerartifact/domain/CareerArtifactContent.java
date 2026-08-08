package com.hiresemble.careerartifact.domain;

import com.hiresemble.careerartifact.domain.CareerArtifactTypes.EvidenceUsageType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioSlideType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioVisualType;
import java.util.List;
import java.util.UUID;

public final class CareerArtifactContent {

    private CareerArtifactContent() {}

    public record EvidenceRef(
            UUID experienceItemId,
            UUID evidenceId,
            EvidenceUsageType usageType,
            String title) {}

    public record ResumePlan(
            String headlineDirection,
            List<String> sectionOrder,
            List<EvidenceRef> evidenceRefs,
            List<String> warnings) {}

    public record ResumeContent(
            String headline,
            String summary,
            List<String> skills,
            List<ResumeSection> sections,
            List<String> warnings) {}

    public record ResumeSection(
            String type,
            String title,
            List<ResumeItem> items) {}

    public record ResumeItem(
            String heading,
            String subheading,
            String period,
            List<String> bullets,
            List<EvidenceRef> evidenceRefs) {}

    public record ResumeFactCheckResult(
            ResumeContent groundedDraft,
            List<ValidationIssue> issues,
            List<String> warnings) {}

    public record PortfolioPlan(
            String audience,
            List<String> coreMessages,
            List<EvidenceRef> evidenceRefs,
            List<String> warnings) {}

    public record PortfolioContent(
            List<PortfolioSlide> slides,
            List<String> warnings) {}

    public record PortfolioSlide(
            int slideNo,
            PortfolioSlideType slideType,
            String title,
            String subtitle,
            List<String> items,
            PortfolioVisualType visualType,
            List<EvidenceRef> evidenceRefs) {}

    public record PortfolioFactCheckResult(
            PortfolioContent groundedDraft,
            List<ValidationIssue> issues,
            List<String> warnings) {}

    public record ValidationIssue(
            String code,
            String location,
            String safeMessage,
            boolean correctable) {}
}
