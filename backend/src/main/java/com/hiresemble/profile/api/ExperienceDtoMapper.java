package com.hiresemble.profile.api;

import com.hiresemble.profile.api.dto.ExperienceDtos.ExperienceItemDetailDto;
import com.hiresemble.profile.api.dto.ExperienceDtos.ExperienceItemDto;
import com.hiresemble.profile.api.dto.ExperienceDtos.ExperienceSourceDto;
import com.hiresemble.profile.api.dto.PageResponse;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemDetail;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.PageSlice;

final class ExperienceDtoMapper {

    private ExperienceDtoMapper() {}

    static ExperienceItemDto item(ExperienceItemRecord value) {
        return new ExperienceItemDto(
                value.id(),
                value.evidenceCategory(),
                value.title(),
                value.content(),
                value.verificationStatus(),
                value.matchKind(),
                value.matchedExperienceItemId(),
                value.matchSimilarity(),
                value.matchKind() == ExperienceMatchKind.RELATED_DIFFERENT
                        || value.matchKind() == ExperienceMatchKind.CONFLICT,
                value.sourceCount(),
                value.documentSourceCount(),
                value.githubRepositorySourceCount(),
                value.primaryDocumentName(),
                value.version(),
                value.createdAt(),
                value.updatedAt());
    }

    static ExperienceItemDetailDto detail(ExperienceItemDetail value) {
        return new ExperienceItemDetailDto(
                item(value.item()),
                value.sources().stream()
                        .map(source -> new ExperienceSourceDto(
                                source.evidenceId(),
                                source.sourceType(),
                                source.documentId(),
                                source.verificationStatus(),
                                source.relationKind(),
                                source.similarity(),
                                source.githubSourceId(),
                                source.githubRepositoryId(),
                                source.repositoryName(),
                                source.repositoryUrl(),
                                source.commitShaShort(),
                                source.capturedAt(),
                                source.sourceExcerpt(),
                                source.sourceDeletedAt(),
                                source.createdAt()))
                        .toList());
    }

    static PageResponse<ExperienceItemDto> page(PageSlice<ExperienceItemRecord> value) {
        return new PageResponse<>(
                value.items().stream().map(ExperienceDtoMapper::item).toList(),
                value.page(),
                value.size(),
                value.totalElements(),
                value.totalPages());
    }
}
