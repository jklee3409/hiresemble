package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedRepository;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedUnit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class GitHubSnapshotPayloadCodec {

    private static final int MAX_UNCOMPRESSED_BYTES = 4 * 1024 * 1024;

    private final ObjectMapper objectMapper;

    public GitHubSnapshotPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EncodedSnapshot encode(SanitizedRepository repository) {
        StoredSnapshot stored = new StoredSnapshot(
                repository.repository().ownerLogin(),
                repository.repository().repositoryName(),
                repository.commitSha(),
                repository.treeSha(),
                repository.selectionComplete(),
                repository.upstreamTruncated(),
                repository.units().stream().map(StoredUnit::from).toList());
        try {
            byte[] json = objectMapper.writeValueAsBytes(stored);
            if (json.length < 1 || json.length > MAX_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("GitHub snapshot JSON exceeds its bound");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json);
            }
            byte[] value = bytes.toByteArray();
            return new EncodedSnapshot(value, sha256(value), json.length);
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub snapshot could not be encoded", exception);
        }
    }

    public StoredSnapshot decode(byte[] gzipJson) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipJson))) {
            byte[] json = gzip.readNBytes(MAX_UNCOMPRESSED_BYTES + 1);
            if (json.length > MAX_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("GitHub snapshot JSON exceeds its bound");
            }
            StoredSnapshot snapshot = objectMapper.readValue(json, StoredSnapshot.class);
            if (snapshot.units() == null || snapshot.units().isEmpty()
                    || snapshot.units().size() > 81
                    || snapshot.commitSha() == null
                    || !snapshot.commitSha().matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("GitHub snapshot JSON is invalid");
            }
            return snapshot;
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub snapshot could not be decoded", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record EncodedSnapshot(byte[] bytes, String checksumSha256, long uncompressedBytes) {
        public EncodedSnapshot {
            bytes = bytes.clone();
        }
    }

    public record StoredSnapshot(
            String ownerLogin,
            String repositoryName,
            String commitSha,
            String treeSha,
            boolean selectionComplete,
            boolean upstreamTruncated,
            List<StoredUnit> units) {
        public StoredSnapshot {
            units = List.copyOf(units);
        }
    }

    public record StoredUnit(
            String unitType,
            String repositoryPath,
            String blobSha,
            String language,
            Integer lineStart,
            Integer lineEnd,
            String contentHash,
            String excerpt,
            String content) {
        static StoredUnit from(SanitizedUnit unit) {
            return new StoredUnit(
                    unit.unitType(), unit.repositoryPath(), unit.blobSha(), unit.language(),
                    unit.lineStart(), unit.lineEnd(), unit.contentHash(), unit.excerpt(), unit.content());
        }
    }
}
