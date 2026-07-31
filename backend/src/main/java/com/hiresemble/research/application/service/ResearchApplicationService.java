package com.hiresemble.research.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.research.application.model.ResearchModels.PageSlice;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.infrastructure.ResearchStore;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchApplicationService {

    private static final Set<String> SOURCE_SORTS =
            Set.of("providerRank,asc", "retrievedAt,desc");

    private final ResearchStore store;

    public ResearchApplicationService(ResearchStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public ResearchRunRow get(UUID userId, UUID researchRunId) {
        return store.findRun(userId, researchRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageSlice<ResearchSourceRow> sources(
            UUID userId,
            UUID researchRunId,
            ResearchTopic topic,
            ResearchSourceType sourceType,
            int page,
            int size,
            String sort) {
        get(userId, researchRunId);
        if (page < 0 || size < 1 || size > 100 || !SOURCE_SORTS.contains(sort)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String order = "providerRank,asc".equals(sort)
                ? "source.provider_rank ASC,source.id ASC"
                : "source.retrieved_at DESC,source.id DESC";
        return store.listSources(
                userId, researchRunId, topic, sourceType, page, size, order);
    }
}
