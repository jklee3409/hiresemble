package com.hiresemble.coverletter.application.port;

import com.hiresemble.coverletter.application.model.CoverLetterModels.AppliedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistVerification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import java.util.UUID;

public interface CoverLetterCommandPort {

    AppliedAnswer applyGeneratedAnswer(
            UUID userId, UUID agentRunId, PersistGeneratedAnswer command);

    Verification persistVerification(
            UUID userId, UUID agentRunId, PersistVerification command);

    void failPendingVerification(UUID userId, UUID agentRunId);
}
