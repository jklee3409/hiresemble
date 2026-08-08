package com.hiresemble.document.application.port;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

public interface ObjectStoragePort {

    void upload(String storageKey, byte[] content, String mimeType, String checksumSha256);

    byte[] read(String storageKey);

    void delete(String storageKey);

    ObjectMetadata metadata(String storageKey);

    PresignedObject presignGet(String storageKey, Duration ttl);

    /** Additive attachment overload; existing adapters and fakes retain source compatibility. */
    default PresignedObject presignGet(String storageKey, Duration ttl, String filename) {
        return presignGet(storageKey, ttl);
    }

    record ObjectMetadata(long size, String contentType, String checksumSha256) {}

    record PresignedObject(URI uri, Instant expiresAt) {}
}
