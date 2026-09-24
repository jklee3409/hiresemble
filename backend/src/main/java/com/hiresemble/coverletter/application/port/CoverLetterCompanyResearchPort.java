package com.hiresemble.coverletter.application.port;

import com.hiresemble.coverletter.application.model.CoverLetterModels.CompanyResearch;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped read of the cover letter's latest succeeded company research. */
public interface CoverLetterCompanyResearchPort {

    Optional<CompanyResearch> latestCompanyResearch(UUID userId, UUID coverLetterId);
}
