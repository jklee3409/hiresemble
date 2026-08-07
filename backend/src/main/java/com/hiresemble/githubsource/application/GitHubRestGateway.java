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
}
