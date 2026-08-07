package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.document.application.port.ObjectStoragePort;
import com.hiresemble.githubsource.application.GitHubSnapshotStoragePort;
import org.springframework.stereotype.Component;

@Component
public final class S3GitHubSnapshotStorageAdapter implements GitHubSnapshotStoragePort {

    private final ObjectStoragePort storage;

    public S3GitHubSnapshotStorageAdapter(ObjectStoragePort storage) {
        this.storage = storage;
    }

    @Override
    public void upload(String storageKey, byte[] gzipJson, String checksumSha256) {
        requireKey(storageKey);
        storage.upload(storageKey, gzipJson, "application/gzip", checksumSha256);
    }

    @Override
    public byte[] read(String storageKey) {
        requireKey(storageKey);
        return storage.read(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        requireKey(storageKey);
        storage.delete(storageKey);
    }

    private void requireKey(String key) {
        if (key == null || !key.matches(
                "users/[0-9a-f-]{36}/github-sources/[0-9a-f-]{36}/snapshots/[0-9a-f-]{36}/snapshot[.]json[.]gz")) {
            throw new IllegalArgumentException("GitHub snapshot storage key is invalid");
        }
    }
}
