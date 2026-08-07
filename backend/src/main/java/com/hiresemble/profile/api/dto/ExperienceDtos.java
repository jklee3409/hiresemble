package com.hiresemble.profile.api.dto;

import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceLinkKind;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExperienceDtos {

    private ExperienceDtos() {}

    @Schema(name = "ExperienceItemDto")
    public record ExperienceItemDto(
            UUID id,
            String evidenceCategory,
            String title,
            String content,
            EvidenceVerificationStatus verificationStatus,
            ExperienceMatchKind matchKind,
            @Schema(nullable = true) UUID matchedExperienceItemId,
            @Schema(nullable = true, minimum = "0", maximum = "1") BigDecimal matchSimilarity,
            boolean reviewRequired,
            int sourceCount,
            int documentSourceCount,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "ExperienceSourceDto")
    public record ExperienceSourceDto(
            UUID evidenceId,
            EvidenceSourceType sourceType,
            @Schema(nullable = true) UUID documentId,
            EvidenceVerificationStatus verificationStatus,
            ExperienceLinkKind relationKind,
            @Schema(nullable = true, minimum = "0", maximum = "1") BigDecimal similarity,
            @Schema(nullable = true) Instant sourceDeletedAt,
            Instant createdAt) {}

    @Schema(name = "ExperienceItemDetailDto")
    public record ExperienceItemDetailDto(
            ExperienceItemDto item, List<ExperienceSourceDto> sources) {
        public ExperienceItemDetailDto {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }
}
