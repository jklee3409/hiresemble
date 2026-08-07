package com.hiresemble.githubsource.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GitHubRepositorySelection(List<UUID> repositoryIds) {

    public GitHubRepositorySelection {
        if (repositoryIds == null
                || repositoryIds.isEmpty()
                || repositoryIds.size() > 10
                || repositoryIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(repositoryIds).size() != repositoryIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        repositoryIds = List.copyOf(repositoryIds);
    }
}
