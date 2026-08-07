package com.hiresemble.githubsource.application;

public interface GitHubSnapshotStoragePort {

    void upload(String storageKey, byte[] gzipJson, String checksumSha256);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
