package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import java.util.List;

/** Bounded public-repository archive download that does not consume GitHub REST core quota per file. */
public interface GitHubPublicArchiveGateway {

    PublicArchive download(String ownerLogin, String repositoryName);

    record ArchiveFile(TreeEntry entry, byte[] content) {
        public ArchiveFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    record PublicArchive(String commitSha, List<ArchiveFile> files, boolean truncated) {
        public PublicArchive {
            files = List.copyOf(files);
        }
    }
}
