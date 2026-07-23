package com.hiresemble.document.infrastructure.config;

import com.hiresemble.document.infrastructure.adapter.DocumentParser;
import com.hiresemble.document.infrastructure.adapter.S3ObjectStorageAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.document.application.port.ObjectStorageException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStorageAdapterTest {

    private static final String ACCESS_KEY = "hiresemble";
    private static final String SECRET_KEY = "hiresemble-local-secret";
    private static final String BUCKET = "hiresemble-test";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    private static S3Client client;
    private static S3Presigner presigner;
    private static S3ObjectStorageAdapter adapter;
    private static String endpoint;

    @BeforeAll
    static void startStorage() {
        MINIO.start();
        endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEndpoint(endpoint);
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setBucket(BUCKET);
        properties.setRegion("us-east-1");
        DocumentInfrastructureConfiguration configuration = new DocumentInfrastructureConfiguration();
        client = configuration.documentS3Client(properties);
        presigner = configuration.documentS3Presigner(properties);
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        adapter = new S3ObjectStorageAdapter(client, presigner, properties);
    }

    @AfterAll
    static void stopStorage() {
        if (presigner != null) presigner.close();
        if (client != null) client.close();
        MINIO.stop();
    }

    @Test
    void uploadHeadReadFiveMinutePresignAndIdempotentDeleteUsePrivateMinio() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String key = "users/" + userId + "/documents/" + documentId + "/content";
        byte[] content = "private document content".getBytes(StandardCharsets.UTF_8);
        String checksum = "a".repeat(64);

        adapter.upload(key, content, DocumentParser.TXT, checksum);

        assertThat(adapter.read(key)).isEqualTo(content);
        var metadata = adapter.metadata(key);
        assertThat(metadata.size()).isEqualTo(content.length);
        assertThat(metadata.contentType()).isEqualTo(DocumentParser.TXT);
        assertThat(metadata.checksumSha256()).isEqualTo(checksum);
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<Void> unsigned = http.send(
                HttpRequest.newBuilder(URI.create(endpoint + "/" + BUCKET + "/" + key))
                        .GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(unsigned.statusCode()).isIn(401, 403);

        Instant before = Instant.now();
        var presigned = adapter.presignGet(key, Duration.ofMinutes(5));
        assertThat(presigned.expiresAt()).isBetween(
                before.plus(Duration.ofMinutes(5)).minusSeconds(2),
                before.plus(Duration.ofMinutes(5)).plusSeconds(2));
        HttpResponse<byte[]> downloaded = http.send(
                HttpRequest.newBuilder(presigned.uri()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);

        adapter.delete(key);
        adapter.delete(key);
        assertThatThrownBy(() -> adapter.metadata(key))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Object storage operation failed");
    }
}
