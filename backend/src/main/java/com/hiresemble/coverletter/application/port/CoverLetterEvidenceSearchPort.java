package com.hiresemble.coverletter.application.port;

import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceSourceExcerpt;
import java.util.List;
import java.util.UUID;

public interface CoverLetterEvidenceSearchPort {

    List<CandidateChunk> searchMaskedCandidates(
            UUID userId, List<Double> queryVector, int limit);

    /**
     * Returns masked original chunks behind the owner's current VERIFIED evidence, ordered by
     * evidence request order, document and chunk index. Deleted documents and retired sources
     * are excluded.
     */
    List<EvidenceSourceExcerpt> findMaskedSourceExcerpts(
            UUID userId, List<UUID> evidenceIds, int limit);
}
