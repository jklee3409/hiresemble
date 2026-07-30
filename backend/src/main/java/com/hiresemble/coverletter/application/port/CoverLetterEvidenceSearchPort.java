package com.hiresemble.coverletter.application.port;

import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import java.util.List;
import java.util.UUID;

public interface CoverLetterEvidenceSearchPort {

    List<CandidateChunk> searchMaskedCandidates(
            UUID userId, List<Double> queryVector, int limit);
}
