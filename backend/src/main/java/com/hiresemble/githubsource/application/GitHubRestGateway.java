package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.application.GitHubGatewayModels.AccountDiscovery;
import com.hiresemble.githubsource.application.GitHubGatewayModels.Blob;
import com.hiresemble.githubsource.application.GitHubGatewayModels.CommitMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeSnapshot;
import java.util.Map;

public interface GitHubRestGateway {

    AccountDiscovery discoverAccount(String ownerLogin);

    ConditionalRepository repository(String ownerLogin, String repositoryName, String etag);

    CommitMetadata defaultBranchCommit(
            String ownerLogin, String repositoryName, String defaultBranch);

    TreeSnapshot tree(String ownerLogin, String repositoryName, String treeSha);

    Map<String, Long> languages(String ownerLogin, String repositoryName);

    Blob blob(String ownerLogin, String repositoryName, String blobSha);

    default AccountDiscovery discoverAccount(GitHubAccessContext access, String ownerLogin) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return discoverAccount(ownerLogin);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }

    default ConditionalRepository repository(
            GitHubAccessContext access, String ownerLogin, String repositoryName, String etag) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return repository(ownerLogin, repositoryName, etag);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }

    default CommitMetadata defaultBranchCommit(
            GitHubAccessContext access,
            String ownerLogin,
            String repositoryName,
            String defaultBranch) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return defaultBranchCommit(ownerLogin, repositoryName, defaultBranch);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }

    default TreeSnapshot tree(
            GitHubAccessContext access,
            String ownerLogin,
            String repositoryName,
            String treeSha) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return tree(ownerLogin, repositoryName, treeSha);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }

    default Map<String, Long> languages(
            GitHubAccessContext access, String ownerLogin, String repositoryName) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return languages(ownerLogin, repositoryName);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }

    default Blob blob(
            GitHubAccessContext access,
            String ownerLogin,
            String repositoryName,
            String blobSha) {
        if (access.mode() == com.hiresemble.githubsource.domain.GitHubAccessMode.PUBLIC) {
            return blob(ownerLogin, repositoryName, blobSha);
        }
        throw new GitHubGatewayException(GitHubGatewayException.Kind.AUTHENTICATION);
    }
}
