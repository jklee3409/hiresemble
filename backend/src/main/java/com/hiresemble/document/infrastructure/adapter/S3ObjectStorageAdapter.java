package com.hiresemble.document.infrastructure.adapter;

import com.hiresemble.document.infrastructure.config.ObjectStorageProperties;
import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.document.application.port.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Component
public final class S3ObjectStorageAdapter implements ObjectStoragePort {

    private static final String CHECKSUM_METADATA = "checksum-sha256";
    private final S3Client client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;

    public S3ObjectStorageAdapter(
            S3Client client, S3Presigner presigner, ObjectStorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public void upload(String storageKey, byte[] content, String mimeType, String checksumSha256) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(storageKey)
                            .contentType(mimeType)
                            .contentLength((long) content.length)
                            .metadata(java.util.Map.of(CHECKSUM_METADATA, checksumSha256))
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException exception) {
            throw new ObjectStorageException(exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(storageKey)
                            .build())
                    .asByteArray();
        } catch (RuntimeException exception) {
            throw new ObjectStorageException(exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new ObjectStorageException(exception);
        }
    }

    @Override
    public ObjectMetadata metadata(String storageKey) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
            return new ObjectMetadata(
                    response.contentLength(),
                    response.contentType(),
                    response.metadata().get(CHECKSUM_METADATA));
        } catch (RuntimeException exception) {
            throw new ObjectStorageException(exception);
        }
    }

    @Override
    public PresignedObject presignGet(String storageKey, Duration ttl) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();
            PresignedGetObjectRequest presigned = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .getObjectRequest(request)
                            .build());
            return new PresignedObject(presigned.url().toURI(), Instant.now().plus(ttl));
        } catch (RuntimeException | java.net.URISyntaxException exception) {
            throw new ObjectStorageException(exception);
        }
    }
}
