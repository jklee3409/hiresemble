package com.hiresemble.research.application.service;

import com.hiresemble.coverletter.application.model.CoverLetterModels.CompanyResearch;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CompanyResearchSource;
import com.hiresemble.coverletter.application.port.CoverLetterCompanyResearchPort;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.infrastructure.ResearchStore;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes the latest succeeded company research to cover-letter writing. Interview reviews and
 * community posts describe hiring processes rather than the company, so they are excluded.
 */
@Service
public class ResearchCoverLetterContextAdapter implements CoverLetterCompanyResearchPort {

    private static final Set<ResearchSourceType> WRITING_SOURCES = EnumSet.of(
            ResearchSourceType.OFFICIAL, ResearchSourceType.TECH_BLOG, ResearchSourceType.NEWS);
    private static final int MAX_SOURCES = 8;

    private final ResearchStore store;

    public ResearchCoverLetterContextAdapter(ResearchStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyResearch> latestCompanyResearch(UUID userId, UUID coverLetterId) {
        if (userId == null || coverLetterId == null) {
            return Optional.empty();
        }
        return store.latestSucceededForCoverLetter(userId, coverLetterId)
                .map(run -> new CompanyResearch(
                        run.summary(),
                        store.allSources(userId, run.id()).stream()
                                .filter(source -> WRITING_SOURCES.contains(source.sourceType()))
                                .limit(MAX_SOURCES)
                                .map(source -> new CompanyResearchSource(
                                        source.sourceType().name(),
                                        source.title(),
                                        source.snippet(),
                                        source.reliabilityNotice()))
                                .toList()));
    }
}
