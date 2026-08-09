package com.hiresemble.careerartifact.domain;

import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioPlan;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioSlide;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeItem;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumePlan;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeSection;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Canonicalizes provider output without inventing or repairing user-facing claims. */
public final class CareerArtifactContentCanonicalizer {

    private final Map<EvidenceKey, VerifiedEvidence> evidenceById;

    public CareerArtifactContentCanonicalizer(List<VerifiedEvidence> selected) {
        if (selected == null) throw new IllegalArgumentException("selected evidence is required");
        Map<EvidenceKey, VerifiedEvidence> values = new HashMap<>();
        selected.forEach(value -> {
            if (value == null) throw new IllegalArgumentException("selected evidence is invalid");
            values.put(new EvidenceKey(value.experienceItemId(), value.evidenceId()), value);
        });
        evidenceById = Map.copyOf(values);
    }

    public ResumePlan canonicalize(ResumePlan value) {
        if (value == null) return null;
        return new ResumePlan(
                value.headlineDirection(),
                value.sectionOrder(),
                refs(value.evidenceRefs()),
                value.warnings());
    }

    public PortfolioPlan canonicalize(PortfolioPlan value) {
        if (value == null) return null;
        return new PortfolioPlan(
                value.audience(),
                value.coreMessages(),
                refs(value.evidenceRefs()),
                value.warnings());
    }

    public ResumeContent canonicalize(ResumeContent value) {
        if (value == null) return null;
        List<ResumeSection> sections = value.sections() == null
                ? null
                : value.sections().stream().map(this::section).toList();
        return new ResumeContent(
                nullable(value.headline()),
                nullable(value.summary()),
                value.skills(),
                sections,
                value.warnings());
    }

    public PortfolioContent canonicalize(PortfolioContent value) {
        if (value == null) return null;
        List<PortfolioSlide> slides = value.slides() == null
                ? null
                : value.slides().stream().map(this::slide).toList();
        return new PortfolioContent(slides, value.warnings());
    }

    public List<EvidenceRef> refs(List<EvidenceRef> values) {
        if (values == null) return null;
        return values.stream().map(this::ref).toList();
    }

    private ResumeSection section(ResumeSection value) {
        if (value == null) return null;
        List<ResumeItem> items = value.items() == null
                ? null
                : value.items().stream().map(this::item).toList();
        return new ResumeSection(value.type(), value.title(), items);
    }

    private ResumeItem item(ResumeItem value) {
        if (value == null) return null;
        return new ResumeItem(
                nullable(value.heading()),
                nullable(value.subheading()),
                nullable(value.period()),
                value.bullets(),
                refs(value.evidenceRefs()));
    }

    private PortfolioSlide slide(PortfolioSlide value) {
        if (value == null) return null;
        return new PortfolioSlide(
                value.slideNo(),
                value.slideType(),
                value.title(),
                nullable(value.subtitle()),
                value.items(),
                value.visualType(),
                refs(value.evidenceRefs()));
    }

    private EvidenceRef ref(EvidenceRef value) {
        if (value == null || value.experienceItemId() == null || value.evidenceId() == null) {
            return value;
        }
        VerifiedEvidence canonical = evidenceById.get(
                new EvidenceKey(value.experienceItemId(), value.evidenceId()));
        if (canonical == null) return value;
        return new EvidenceRef(
                canonical.experienceItemId(),
                canonical.evidenceId(),
                canonical.usageType(),
                canonical.title());
    }

    private String nullable(String value) {
        return value != null && value.isBlank() ? null : value;
    }

    private record EvidenceKey(UUID experienceItemId, UUID evidenceId) {}
}
