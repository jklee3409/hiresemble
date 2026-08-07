package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawRepository;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedRepository;
import java.util.List;

public interface GitHubSourceSanitizerPort {

    List<TreeEntry> selectCandidateFiles(List<TreeEntry> entries);

    SanitizedRepository sanitize(RawRepository repository);
}
