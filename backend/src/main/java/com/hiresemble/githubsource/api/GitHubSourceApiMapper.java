package com.hiresemble.githubsource.api;

import com.hiresemble.agentrun.api.dto.RequiredUserActionDto;
import com.hiresemble.agentrun.api.dto.ResourceRefDto;
import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubRefreshResultDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubRepositoryDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubSourceDetailDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubSourceSummaryDto;
import com.hiresemble.githubsource.application.GitHubSourceApplicationService.RefreshResult;
import com.hiresemble.githubsource.application.GitHubSourceMutationService;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
import org.springframework.stereotype.Component;

@Component
public final class GitHubSourceApiMapper {

    public GitHubSourceSummaryDto summary(Source source) {
        return new GitHubSourceSummaryDto(
                source.id(),
                source.sourceKind(),
                source.accountType(),
                source.canonicalUrl(),
                source.ownerLogin(),
                source.repositoryName(),
                source.status(),
                source.discoveredRepositoryCount(),
                source.selectedRepositoryCount(),
                source.repositoryDiscoveryTruncated(),
                source.newExperienceCount(),
                source.corroboratedExperienceCount(),
                source.reviewRequiredCount(),
                source.rejectedCandidateCount(),
                source.snapshotIncomplete(),
                source.latestAgentRunId(),
                source.lastSuccessfulSyncAt(),
                source.version(),
                source.createdAt(),
                source.updatedAt());
    }

    public GitHubSourceDetailDto detail(Source source) {
        RequiredUserActionDto action = source.status() == GitHubSourceStatus.WAITING_USER
                ? new RequiredUserActionDto(
                        RequiredUserActionType.SELECT_GITHUB_REPOSITORIES,
                        new ResourceRefDto(
                                GitHubSourceMutationService.RESOURCE_TYPE,
                                source.id(),
                                source.canonicalUrl()),
                        "/profile/github",
                        "분석할 공개 저장소를 1개 이상 선택해 주세요.")
                : null;
        return new GitHubSourceDetailDto(summary(source), action);
    }

    public GitHubRepositoryDto repository(Repository repository) {
        return new GitHubRepositoryDto(
                repository.id(),
                repository.ownerLogin(),
                repository.repositoryName(),
                repository.canonicalUrl(),
                repository.description(),
                repository.defaultBranch(),
                repository.fork(),
                repository.archived(),
                repository.selected(),
                repository.pushedAt());
    }

    public RunAcceptedDto run(WorkflowLaunchResult result, boolean replayed) {
        return new RunAcceptedDto(
                result.agentRunId(),
                result.status(),
                result.resourceType(),
                result.resourceId(),
                replayed);
    }

    public GitHubRefreshResultDto refresh(RefreshResult result, boolean replayed) {
        return new GitHubRefreshResultDto(
                result.changed(),
                detail(result.source()),
                result.run() == null ? null : run(result.run(), replayed));
    }
}
