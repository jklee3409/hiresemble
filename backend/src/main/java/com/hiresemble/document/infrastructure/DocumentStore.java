package com.hiresemble.document.infrastructure;

import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.DocumentRecords.ChunkDraft;
import com.hiresemble.document.domain.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.domain.DocumentRecords.PageSlice;
import com.hiresemble.document.domain.DocumentRecords.SimilarChunk;
import com.hiresemble.document.domain.DocumentType;
import com.hiresemble.document.domain.EvidenceExtractionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class DocumentStore {

    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DocumentStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DocumentRecord create(
            UUID id,
            UUID userId,
            DocumentType type,
            String originalFilename,
            String displayName,
            String storageKey,
            String mimeType,
            long size,
            String checksum,
            Instant now) {
        return jdbc.sql("""
                        INSERT INTO documents (
                            id,user_id,document_type,original_filename,display_name,storage_key,
                            mime_type,file_size_bytes,checksum_sha256,parse_status,
                            evidence_extraction_status,manual_text_provided,source_revision,
                            latest_agent_run_id,version,uploaded_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:type,:filename,:displayName,:storageKey,
                            :mimeType,:size,:checksum,'UPLOADED','NOT_STARTED',false,1,
                            NULL,0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("type", type.name())
                .param("filename", originalFilename)
                .param("displayName", displayName)
                .param("storageKey", storageKey)
                .param("mimeType", mimeType)
                .param("size", size)
                .param("checksum", checksum)
                .param("now", utc(now))
                .query(this::document)
                .single();
    }

    public void attachLatestRun(UUID userId, UUID documentId, UUID runId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE documents SET latest_agent_run_id=:runId,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("documentId", documentId)
                .update();
        if (updated != 1) throw new IllegalStateException("document disappeared during run attachment");
    }

    public Optional<DocumentRecord> findActive(UUID userId, UUID documentId) {
        return jdbc.sql("SELECT * FROM documents WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", documentId)
                .query(this::document)
                .optional();
    }

    public Optional<DocumentRecord> findAny(UUID userId, UUID documentId) {
        return jdbc.sql("SELECT * FROM documents WHERE user_id=:userId AND id=:id")
                .param("userId", userId)
                .param("id", documentId)
                .query(this::document)
                .optional();
    }

    public PageSlice<DocumentRecord> list(
            UUID userId,
            DocumentType type,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceStatus,
            int page,
            int size,
            String sort) {
        String order = switch (sort) {
            case "uploadedAt,desc" -> "uploaded_at DESC, id DESC";
            case "updatedAt,desc" -> "updated_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported document sort");
        };
        StringBuilder where = new StringBuilder("user_id=:userId AND deleted_at IS NULL");
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("userId", userId);
        if (type != null) {
            where.append(" AND document_type=:type");
            params.put("type", type.name());
        }
        if (parseStatus != null) {
            where.append(" AND parse_status=:parseStatus");
            params.put("parseStatus", parseStatus.name());
        }
        if (evidenceStatus != null) {
            where.append(" AND evidence_extraction_status=:evidenceStatus");
            params.put("evidenceStatus", evidenceStatus.name());
        }
        long total = jdbc.sql("SELECT count(*) FROM documents WHERE " + where)
                .params(params)
                .query(Long.class)
                .single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<DocumentRecord> items = jdbc.sql("SELECT * FROM documents WHERE " + where
                        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query(this::document)
                .list();
        int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageSlice<>(items, page, size, total, pages);
    }

    public Optional<DocumentTextRecord> findText(UUID userId, UUID documentId, long revision) {
        return jdbc.sql("""
                        SELECT *, page_offsets::text AS page_offsets_text
                        FROM document_texts
                        WHERE user_id=:userId AND document_id=:documentId AND source_revision=:revision
                        """)
                .param("userId", userId)
                .param("documentId", documentId)
                .param("revision", revision)
                .query(this::text)
                .optional();
    }

    public DocumentTextRecord saveExtractedText(
            UUID userId,
            UUID documentId,
            long revision,
            String extractedText,
            int characterCount,
            Integer pageCount,
            List<Integer> pageOffsets,
            String parserName,
            String parserVersion,
            boolean parsed,
            Instant now) {
        return jdbc.sql("""
                        INSERT INTO document_texts (
                            id,user_id,document_id,source_revision,extracted_text,masked_text,
                            character_count,page_count,page_offsets,parser_name,parser_version,
                            parsed_at,version,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:documentId,:revision,:text,NULL,
                            :characterCount,:pageCount,CAST(:pageOffsets AS jsonb),:parserName,:parserVersion,
                            :parsedAt,0,:now,:now
                        )
                        ON CONFLICT (user_id,document_id,source_revision) DO UPDATE SET
                            extracted_text=EXCLUDED.extracted_text,masked_text=NULL,
                            character_count=EXCLUDED.character_count,page_count=EXCLUDED.page_count,
                            page_offsets=EXCLUDED.page_offsets,parser_name=EXCLUDED.parser_name,
                            parser_version=EXCLUDED.parser_version,parsed_at=EXCLUDED.parsed_at,
                            version=document_texts.version+1,updated_at=EXCLUDED.updated_at
                        RETURNING *, page_offsets::text AS page_offsets_text
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("documentId", documentId)
                .param("revision", revision)
                .param("text", extractedText)
                .param("characterCount", characterCount)
                .param("pageCount", pageCount)
                .param("pageOffsets", json(pageOffsets))
                .param("parserName", parserName)
                .param("parserVersion", parserVersion)
                .param("parsedAt", parsed ? utc(now) : null)
                .param("now", utc(now))
                .query(this::text)
                .single();
    }

    @Transactional
    public DocumentTextRecord saveExtractedTextAndState(
            UUID userId,
            UUID documentId,
            UUID runId,
            long revision,
            String extractedText,
            int characterCount,
            Integer pageCount,
            List<Integer> pageOffsets,
            String parserName,
            String parserVersion,
            boolean needsManualText,
            Instant now) {
        DocumentTextRecord saved = saveExtractedText(
                userId, documentId, revision, extractedText, characterCount, pageCount,
                pageOffsets, parserName, parserVersion, false, now);
        if (needsManualText) markNeedsManual(userId, documentId, runId, now);
        return saved;
    }

    public DocumentTextRecord saveMaskedText(
            UUID userId, UUID documentId, long revision, String maskedText, Instant now) {
        return jdbc.sql("""
                        UPDATE document_texts SET masked_text=:maskedText,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND document_id=:documentId AND source_revision=:revision
                        RETURNING *, page_offsets::text AS page_offsets_text
                        """)
                .param("maskedText", maskedText)
                .param("now", utc(now))
                .param("userId", userId)
                .param("documentId", documentId)
                .param("revision", revision)
                .query(this::text)
                .single();
    }

    public List<DocumentChunkRecord> replaceChunks(
            UUID userId, UUID documentId, long revision, List<ChunkDraft> drafts, Instant now) {
        jdbc.sql("DELETE FROM document_chunks WHERE user_id=:userId AND document_id=:documentId AND source_revision=:revision")
                .param("userId", userId)
                .param("documentId", documentId)
                .param("revision", revision)
                .update();
        for (ChunkDraft draft : drafts) {
            jdbc.sql("""
                            INSERT INTO document_chunks (
                                id,user_id,document_id,source_revision,chunk_index,page_from,page_to,
                                content,masked_content,token_count,embedding,embedding_policy_version,
                                embedding_provider,embedding_model,embedding_dimension,
                                embedding_generation,metadata,created_at
                            ) VALUES (
                                :id,:userId,:documentId,:revision,:chunkIndex,:pageFrom,:pageTo,
                                :content,:maskedContent,:tokenCount,NULL,NULL,NULL,NULL,NULL,NULL,
                                CAST(:metadata AS jsonb),:now
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId)
                    .param("documentId", documentId)
                    .param("revision", revision)
                    .param("chunkIndex", draft.chunkIndex())
                    .param("pageFrom", draft.pageFrom())
                    .param("pageTo", draft.pageTo())
                    .param("content", draft.content())
                    .param("maskedContent", draft.maskedContent())
                    .param("tokenCount", draft.tokenCount())
                    .param("metadata", json(draft.metadata()))
                    .param("now", utc(now))
                    .update();
        }
        return findChunks(userId, documentId, revision);
    }

    public List<DocumentChunkRecord> findChunks(UUID userId, UUID documentId, long revision) {
        return jdbc.sql("""
                        SELECT *, metadata::text AS metadata_text,
                               CASE WHEN embedding IS NULL THEN NULL ELSE embedding::text END AS embedding_text
                        FROM document_chunks
                        WHERE user_id=:userId AND document_id=:documentId AND source_revision=:revision
                        ORDER BY chunk_index
                        """)
                .param("userId", userId)
                .param("documentId", documentId)
                .param("revision", revision)
                .query(this::chunk)
                .list();
    }

    public void storeEmbedding(UUID userId, UUID documentId, UUID chunkId, EmbeddingPolicy policy, List<Double> vector) {
        int updated = jdbc.sql("""
                        UPDATE document_chunks SET
                            embedding=CAST(:embedding AS vector),embedding_policy_version=:policyVersion,
                            embedding_provider=:provider,embedding_model=:model,
                            embedding_dimension=:dimension,embedding_generation=:generation
                        WHERE user_id=:userId AND document_id=:documentId AND id=:chunkId
                        """)
                .param("embedding", vector(vector))
                .param("policyVersion", policy.version())
                .param("provider", policy.provider())
                .param("model", policy.model())
                .param("dimension", policy.dimension())
                .param("generation", policy.generation())
                .param("userId", userId)
                .param("documentId", documentId)
                .param("chunkId", chunkId)
                .update();
        if (updated != 1) throw new IllegalArgumentException("embedding chunk is not owner scoped");
    }

    public EmbeddingPolicy activeEmbeddingPolicy() {
        return jdbc.sql("""
                        SELECT version,provider_key,product_key,dimension,distance_metric,generation
                        FROM embedding_policy_versions WHERE enabled
                        """)
                .query((rs, row) -> new EmbeddingPolicy(
                        rs.getLong("version"), rs.getString("provider_key"),
                        rs.getString("product_key"), rs.getInt("dimension"),
                        rs.getString("distance_metric"), rs.getInt("generation")))
                .single();
    }

    public List<SimilarChunk> exactCosineSearch(
            UUID userId, List<Double> query, long policyVersion, int generation, int limit) {
        return jdbc.sql("""
                        SELECT c.id,c.document_id,c.chunk_index,c.masked_content,
                               c.embedding <=> CAST(:query AS vector) AS distance
                        FROM document_chunks c
                        JOIN documents d ON d.user_id=c.user_id AND d.id=c.document_id
                        JOIN embedding_policy_versions p ON p.version=c.embedding_policy_version
                        WHERE c.user_id=:userId AND d.deleted_at IS NULL
                          AND c.embedding IS NOT NULL
                          AND c.embedding_policy_version=:policyVersion
                          AND c.embedding_generation=:generation
                          AND c.embedding_provider=p.provider_key
                          AND c.embedding_model=p.product_key
                          AND c.embedding_dimension=p.dimension
                          AND p.enabled
                        ORDER BY c.embedding <=> CAST(:query AS vector), c.id
                        LIMIT :limit
                        """)
                .param("query", vector(query))
                .param("userId", userId)
                .param("policyVersion", policyVersion)
                .param("generation", generation)
                .param("limit", limit)
                .query((rs, row) -> new SimilarChunk(
                        uuid(rs, "id"), uuid(rs, "document_id"), rs.getInt("chunk_index"),
                        rs.getString("masked_content"), rs.getDouble("distance")))
                .list();
    }

    public Optional<DocumentRecord> beginParsing(UUID userId, UUID documentId, UUID runId, Instant now) {
        return jdbc.sql("""
                        UPDATE documents SET parse_status='PARSING',parse_error_code=NULL,
                            latest_agent_run_id=:runId,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                          AND parse_status IN ('UPLOADED','FAILED','NEEDS_MANUAL_TEXT','PARSING')
                        RETURNING *
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("documentId", documentId)
                .query(this::document)
                .optional();
    }

    public void markNeedsManual(UUID userId, UUID documentId, UUID runId, Instant now) {
        requireUpdate(jdbc.sql("""
                        UPDATE documents SET parse_status='NEEDS_MANUAL_TEXT',parse_error_code=NULL,
                            evidence_extraction_status='NOT_STARTED',evidence_error_code=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("runId", runId).update());
    }

    public void markParseFailed(
            UUID userId, UUID documentId, UUID runId, String errorCode, Instant now) {
        requireUpdate(jdbc.sql("""
                        UPDATE documents SET parse_status='FAILED',parse_error_code=:errorCode,
                            evidence_extraction_status='NOT_STARTED',evidence_error_code=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                        """)
                .param("errorCode", errorCode).param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("runId", runId).update());
    }

    public void markParsed(UUID userId, UUID documentId, UUID runId, Instant now) {
        requireUpdate(jdbc.sql("""
                        UPDATE documents SET parse_status='PARSED',parse_error_code=NULL,
                            evidence_extraction_status='QUEUED',evidence_error_code=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("runId", runId).update());
    }

    public void markExtracting(UUID userId, UUID documentId, UUID runId, Instant now) {
        updateEvidenceState(userId, documentId, runId, "EXTRACTING", null, now);
    }

    public void markEvidenceSucceeded(UUID userId, UUID documentId, UUID runId, Instant now) {
        updateEvidenceState(userId, documentId, runId, "SUCCEEDED", null, now);
    }

    public void markEvidenceFailed(UUID userId, UUID documentId, UUID runId, String errorCode, Instant now) {
        updateEvidenceState(userId, documentId, runId, "FAILED", errorCode, now);
    }

    public void stableCompensation(UUID userId, UUID documentId, UUID runId, Instant now) {
        jdbc.sql("""
                        UPDATE documents SET
                            parse_status=CASE WHEN parse_status='PARSING' AND EXISTS (
                                SELECT 1 FROM document_texts t
                                WHERE t.user_id=documents.user_id AND t.document_id=documents.id
                                  AND t.source_revision=documents.source_revision
                                  AND t.masked_text IS NOT NULL
                            ) THEN 'PARSED' WHEN parse_status='PARSING' THEN 'UPLOADED' ELSE parse_status END,
                            evidence_extraction_status=CASE
                                WHEN evidence_extraction_status IN ('QUEUED','EXTRACTING') THEN 'NOT_STARTED'
                                ELSE evidence_extraction_status END,
                            evidence_error_code=NULL,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND latest_agent_run_id=:runId
                          AND deleted_at IS NULL
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("runId", runId).update();
    }

    public Optional<DocumentRecord> setManualText(
            UUID userId, UUID documentId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                        UPDATE documents SET manual_text_provided=true,source_revision=source_revision+1,
                            parse_status='PARSING',parse_error_code=NULL,
                            evidence_extraction_status='NOT_STARTED',evidence_error_code=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND version=:expectedVersion
                        RETURNING *
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("expectedVersion", expectedVersion)
                .query(this::document).optional();
    }

    public Optional<DocumentRecord> resetForReparse(
            UUID userId, UUID documentId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                        UPDATE documents SET source_revision=source_revision+1,
                            parse_status='UPLOADED',parse_error_code=NULL,
                            evidence_extraction_status='NOT_STARTED',evidence_error_code=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND version=:expectedVersion
                        RETURNING *
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("expectedVersion", expectedVersion)
                .query(this::document).optional();
    }

    public boolean softDelete(UUID userId, UUID documentId, long version, Instant now) {
        return jdbc.sql("""
                        UPDATE documents SET deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND version=:version AND deleted_at IS NULL
                        """)
                .param("now", utc(now)).param("userId", userId)
                .param("documentId", documentId).param("version", version).update() == 1;
    }

    public void deleteDerivedContent(UUID userId, UUID documentId) {
        jdbc.sql("DELETE FROM document_chunks WHERE user_id=:userId AND document_id=:documentId")
                .param("userId", userId).param("documentId", documentId).update();
        jdbc.sql("DELETE FROM document_texts WHERE user_id=:userId AND document_id=:documentId")
                .param("userId", userId).param("documentId", documentId).update();
    }

    private void updateEvidenceState(
            UUID userId, UUID documentId, UUID runId, String status, String errorCode, Instant now) {
        requireUpdate(jdbc.sql("""
                        UPDATE documents SET evidence_extraction_status=:status,
                            evidence_error_code=:errorCode,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId AND parse_status='PARSED'
                        """)
                .param("status", status).param("errorCode", errorCode).param("now", utc(now))
                .param("userId", userId).param("documentId", documentId).param("runId", runId).update());
    }

    private void requireUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("document workflow state is stale");
    }

    private DocumentRecord document(ResultSet rs, int row) throws SQLException {
        return new DocumentRecord(
                uuid(rs, "id"), uuid(rs, "user_id"), DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("original_filename"), rs.getString("display_name"), rs.getString("storage_key"),
                rs.getString("mime_type"), rs.getLong("file_size_bytes"), rs.getString("checksum_sha256").trim(),
                DocumentParseStatus.valueOf(rs.getString("parse_status")),
                EvidenceExtractionStatus.valueOf(rs.getString("evidence_extraction_status")),
                rs.getString("parse_error_code"), rs.getString("evidence_error_code"),
                rs.getBoolean("manual_text_provided"), rs.getLong("source_revision"),
                uuidNullable(rs, "latest_agent_run_id"), rs.getLong("version"),
                instant(rs, "uploaded_at"), instant(rs, "updated_at"), instantNullable(rs, "deleted_at"));
    }

    private DocumentTextRecord text(ResultSet rs, int row) throws SQLException {
        return new DocumentTextRecord(
                uuid(rs, "id"), uuid(rs, "user_id"), uuid(rs, "document_id"),
                rs.getLong("source_revision"), rs.getString("extracted_text"), rs.getString("masked_text"),
                rs.getInt("character_count"), (Integer) rs.getObject("page_count"),
                read(rs.getString("page_offsets_text"), INTEGER_LIST), rs.getString("parser_name"),
                rs.getString("parser_version"), instantNullable(rs, "parsed_at"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private DocumentChunkRecord chunk(ResultSet rs, int row) throws SQLException {
        return new DocumentChunkRecord(
                uuid(rs, "id"), uuid(rs, "user_id"), uuid(rs, "document_id"),
                rs.getLong("source_revision"), rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_from"), (Integer) rs.getObject("page_to"),
                rs.getString("content"), rs.getString("masked_content"), rs.getInt("token_count"),
                parseVector(rs.getString("embedding_text")), (Long) rs.getObject("embedding_policy_version"),
                rs.getString("embedding_provider"), rs.getString("embedding_model"),
                (Integer) rs.getObject("embedding_dimension"), (Integer) rs.getObject("embedding_generation"),
                read(rs.getString("metadata_text"), OBJECT_MAP), instant(rs, "created_at"));
    }

    private List<Double> parseVector(String value) {
        if (value == null) return null;
        String body = value.substring(1, value.length() - 1);
        if (body.isBlank()) return List.of();
        String[] values = body.split(",");
        List<Double> result = new ArrayList<>(values.length);
        for (String element : values) result.add(Double.valueOf(element));
        return List.copyOf(result);
    }

    private String vector(List<Double> values) {
        return values.stream().map(value -> Double.toString(value)).collect(
                java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("document metadata is invalid", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored document JSON is invalid", exception);
        }
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private UUID uuidNullable(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant instantNullable(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
