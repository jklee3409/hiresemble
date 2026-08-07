package com.hiresemble.profile.infrastructure.persistence;

import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceLinkKind;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ExperienceRecords.EvidenceExperienceLink;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemDetail;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemRecord;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceSourceRecord;
import com.hiresemble.profile.domain.model.ExperienceRecords.SimilarExperienceRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.PageSlice;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExperienceStore {

    private static final String ITEM_COLUMNS = """
            item.id,item.user_id,item.canonical_evidence_id,item.evidence_category,
            item.title,item.content,item.verification_status,item.match_kind,
            item.matched_experience_item_id,item.match_similarity,item.match_policy_version,
            item.canonical_fingerprint,item.version,item.created_at,item.updated_at,
            (SELECT count(*) FROM experience_evidence_links link
             JOIN profile_evidence evidence
               ON evidence.user_id=link.user_id AND evidence.id=link.profile_evidence_id
             WHERE link.user_id=item.user_id AND link.experience_item_id=item.id
               AND evidence.source_deleted_at IS NULL) AS source_count,
            (SELECT count(DISTINCT evidence.document_id)
             FROM experience_evidence_links link
             JOIN profile_evidence evidence
               ON evidence.user_id=link.user_id AND evidence.id=link.profile_evidence_id
             WHERE link.user_id=item.user_id AND link.experience_item_id=item.id
               AND evidence.document_id IS NOT NULL AND evidence.source_deleted_at IS NULL)
                AS document_source_count
            """;

    private final JdbcClient jdbc;

    public ExperienceStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void lockUserMatching(UUID userId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text), 0))")
                .param("userId", userId.toString())
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }

    public Optional<ExperienceItemRecord> findActive(UUID userId, UUID itemId) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM experience_items item "
                        + "WHERE item.user_id=:userId AND item.id=:itemId AND item.deleted_at IS NULL")
                .param("userId", userId)
                .param("itemId", itemId)
                .query(this::item)
                .optional();
    }

    public Optional<ExperienceItemRecord> findActiveExact(
            UUID userId, String category, String fingerprint) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM experience_items item "
                        + "WHERE item.user_id=:userId AND item.evidence_category=:category "
                        + "AND item.canonical_fingerprint=:fingerprint AND item.deleted_at IS NULL")
                .param("userId", userId)
                .param("category", category)
                .param("fingerprint", fingerprint)
                .query(this::item)
                .optional();
    }

    public List<SimilarExperienceRecord> findSimilar(
            UUID userId,
            String category,
            List<Double> query,
            EmbeddingPolicy policy,
            int limit) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS
                        + ", embedding.embedding <=> CAST(:query AS vector) AS distance "
                        + "FROM experience_items item "
                        + "JOIN experience_item_embeddings embedding "
                        + "  ON embedding.user_id=item.user_id "
                        + " AND embedding.experience_item_id=item.id "
                        + " AND embedding.experience_version=item.version "
                        + "JOIN embedding_policy_versions policy "
                        + "  ON policy.version=embedding.embedding_policy_version "
                        + "WHERE item.user_id=:userId AND item.deleted_at IS NULL "
                        + "AND item.evidence_category=:category "
                        + "AND embedding.embedding_policy_version=:policyVersion "
                        + "AND embedding.embedding_generation=:generation "
                        + "AND embedding.embedding_provider=policy.provider_key "
                        + "AND embedding.embedding_model=policy.product_key "
                        + "AND embedding.embedding_dimension=policy.dimension "
                        + "AND policy.enabled "
                        + "ORDER BY embedding.embedding <=> CAST(:query AS vector), item.id LIMIT :limit")
                .param("query", vector(query))
                .param("userId", userId)
                .param("category", category)
                .param("policyVersion", policy.version())
                .param("generation", policy.generation())
                .param("limit", limit)
                .query((resultSet, rowNumber) ->
                        new SimilarExperienceRecord(item(resultSet, rowNumber), resultSet.getDouble("distance")))
                .list();
    }

    public void createItem(
            UUID itemId,
            UUID userId,
            UUID canonicalEvidenceId,
            String category,
            String title,
            String content,
            ExperienceMatchKind matchKind,
            UUID matchedItemId,
            BigDecimal similarity,
            String policyVersion,
            String fingerprint,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO experience_items (
                            id,user_id,canonical_evidence_id,evidence_category,title,content,
                            verification_status,match_kind,matched_experience_item_id,
                            match_similarity,match_policy_version,canonical_fingerprint,
                            version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:canonicalEvidenceId,:category,:title,:content,'PENDING',:matchKind,
                            :matchedItemId,:similarity,:policyVersion,:fingerprint,0,:now,:now,NULL
                        )
                        """)
                .param("id", itemId)
                .param("userId", userId)
                .param("canonicalEvidenceId", canonicalEvidenceId)
                .param("category", category)
                .param("title", title)
                .param("content", content)
                .param("matchKind", matchKind.name())
                .param("matchedItemId", matchedItemId)
                .param("similarity", similarity)
                .param("policyVersion", policyVersion)
                .param("fingerprint", fingerprint)
                .param("now", utc(now))
                .update();
    }

    public void addEvidenceLink(
            UUID userId,
            UUID itemId,
            UUID evidenceId,
            ExperienceLinkKind relationKind,
            BigDecimal similarity,
            String policyVersion,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO experience_evidence_links (
                            id,user_id,experience_item_id,profile_evidence_id,relation_kind,
                            similarity,match_policy_version,created_at
                        ) VALUES (
                            :id,:userId,:itemId,:evidenceId,:relationKind,:similarity,
                            :policyVersion,:now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("itemId", itemId)
                .param("evidenceId", evidenceId)
                .param("relationKind", relationKind.name())
                .param("similarity", similarity)
                .param("policyVersion", policyVersion)
                .param("now", utc(now))
                .update();
    }

    public void storeEmbedding(
            UUID userId,
            UUID itemId,
            long itemVersion,
            List<Double> embedding,
            EmbeddingPolicy policy,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO experience_item_embeddings (
                            user_id,experience_item_id,experience_version,embedding,
                            embedding_policy_version,embedding_provider,embedding_model,
                            embedding_dimension,embedding_generation,created_at
                        ) VALUES (
                            :userId,:itemId,:itemVersion,CAST(:embedding AS vector),
                            :policyVersion,:provider,:model,:dimension,:generation,:now
                        )
                        """)
                .param("userId", userId)
                .param("itemId", itemId)
                .param("itemVersion", itemVersion)
                .param("embedding", vector(embedding))
                .param("policyVersion", policy.version())
                .param("provider", policy.provider())
                .param("model", policy.model())
                .param("dimension", policy.dimension())
                .param("generation", policy.generation())
                .param("now", utc(now))
                .update();
    }

    public Optional<EvidenceExperienceLink> findBySourceEvidence(UUID userId, UUID evidenceId) {
        return jdbc.sql("""
                        SELECT link.profile_evidence_id,link.experience_item_id,
                               item.canonical_evidence_id,link.relation_kind,item.match_kind
                        FROM experience_evidence_links link
                        JOIN experience_items item
                          ON item.user_id=link.user_id AND item.id=link.experience_item_id
                        WHERE link.user_id=:userId AND link.profile_evidence_id=:evidenceId
                          AND item.deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .query((resultSet, rowNumber) -> new EvidenceExperienceLink(
                        resultSet.getObject("profile_evidence_id", UUID.class),
                        resultSet.getObject("experience_item_id", UUID.class),
                        resultSet.getObject("canonical_evidence_id", UUID.class),
                        ExperienceLinkKind.valueOf(resultSet.getString("relation_kind")),
                        ExperienceMatchKind.valueOf(resultSet.getString("match_kind"))))
                .optional();
    }

    public java.util.Map<UUID, EvidenceExperienceLink> findBySourceEvidence(
            UUID userId, List<UUID> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return java.util.Map.of();
        }
        return jdbc.sql("""
                        SELECT link.profile_evidence_id,link.experience_item_id,
                               item.canonical_evidence_id,link.relation_kind,item.match_kind
                        FROM experience_evidence_links link
                        JOIN experience_items item
                          ON item.user_id=link.user_id AND item.id=link.experience_item_id
                        WHERE link.user_id=:userId
                          AND link.profile_evidence_id IN (:evidenceIds)
                          AND item.deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("evidenceIds", evidenceIds)
                .query((resultSet, rowNumber) -> new EvidenceExperienceLink(
                        resultSet.getObject("profile_evidence_id", UUID.class),
                        resultSet.getObject("experience_item_id", UUID.class),
                        resultSet.getObject("canonical_evidence_id", UUID.class),
                        ExperienceLinkKind.valueOf(resultSet.getString("relation_kind")),
                        ExperienceMatchKind.valueOf(resultSet.getString("match_kind"))))
                .list()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        EvidenceExperienceLink::sourceEvidenceId,
                        java.util.function.Function.identity()));
    }

    public PageSlice<ExperienceItemRecord> list(
            UUID userId,
            EvidenceVerificationStatus status,
            ExperienceMatchKind matchKind,
            int page,
            int size,
            String sort) {
        String order = switch (sort) {
            case "updatedAt,desc" -> "item.updated_at DESC,item.id DESC";
            case "createdAt,desc" -> "item.created_at DESC,item.id DESC";
            default -> throw new IllegalArgumentException("unsupported experience sort");
        };
        String statusValue = status == null ? "" : status.name();
        String matchValue = matchKind == null ? "" : matchKind.name();
        String where = "item.user_id=:userId AND item.deleted_at IS NULL "
                + "AND (:status='' OR item.verification_status=:status) "
                + "AND (:matchKind='' OR item.match_kind=:matchKind)";
        List<ExperienceItemRecord> items = jdbc.sql(
                        "SELECT " + ITEM_COLUMNS + " FROM experience_items item WHERE "
                                + where + " ORDER BY " + order + " LIMIT :size OFFSET :offset")
                .param("userId", userId)
                .param("status", statusValue)
                .param("matchKind", matchValue)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::item)
                .list();
        long count = jdbc.sql("SELECT count(*) FROM experience_items item WHERE " + where)
                .param("userId", userId)
                .param("status", statusValue)
                .param("matchKind", matchValue)
                .query(Long.class)
                .single();
        int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new PageSlice<>(items, page, size, count, totalPages);
    }

    public Optional<ExperienceItemDetail> findDetail(UUID userId, UUID itemId) {
        return findActive(userId, itemId).map(item -> new ExperienceItemDetail(
                item,
                jdbc.sql("""
                                SELECT evidence.id AS evidence_id,evidence.source_type,
                                       evidence.document_id,evidence.verification_status,
                                       link.relation_kind,link.similarity,evidence.source_deleted_at,
                                       evidence.created_at
                                FROM experience_evidence_links link
                                JOIN profile_evidence evidence
                                  ON evidence.user_id=link.user_id
                                 AND evidence.id=link.profile_evidence_id
                                WHERE link.user_id=:userId AND link.experience_item_id=:itemId
                                ORDER BY evidence.created_at,evidence.id
                                """)
                        .param("userId", userId)
                        .param("itemId", itemId)
                        .query(this::source)
                        .list()));
    }

    public boolean updateItem(
            UUID userId,
            UUID itemId,
            String title,
            String content,
            String fingerprint,
            long version,
            Instant now) {
        return jdbc.sql("""
                        UPDATE experience_items
                        SET title=:title,content=:content,canonical_fingerprint=:fingerprint,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:itemId AND version=:version
                          AND deleted_at IS NULL
                        """)
                .param("title", title)
                .param("content", content)
                .param("fingerprint", fingerprint)
                .param("now", utc(now))
                .param("userId", userId)
                .param("itemId", itemId)
                .param("version", version)
                .update() == 1;
    }

    public boolean updateVerification(
            UUID userId,
            UUID itemId,
            EvidenceVerificationStatus status,
            long version,
            Instant now) {
        return jdbc.sql("""
                        UPDATE experience_items
                        SET verification_status=:status,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:itemId AND version=:version
                          AND deleted_at IS NULL
                        """)
                .param("status", status.name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("itemId", itemId)
                .param("version", version)
                .update() == 1;
    }

    public void synchronizeVerification(
            UUID userId, UUID itemId, EvidenceVerificationStatus status, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE experience_items
                        SET verification_status=:status,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:itemId AND deleted_at IS NULL
                        """)
                .param("status", status.name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("itemId", itemId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("experience verification could not be synchronized");
        }
    }

    public boolean keepSeparate(UUID userId, UUID itemId, long version, Instant now) {
        return jdbc.sql("""
                        UPDATE experience_items
                        SET match_kind='NEW',matched_experience_item_id=NULL,
                            match_similarity=NULL,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:itemId AND version=:version
                          AND deleted_at IS NULL AND match_kind <> 'NEW'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("itemId", itemId)
                .param("version", version)
                .update() == 1;
    }

    public void moveEvidenceLinks(UUID userId, UUID fromItemId, UUID toItemId) {
        jdbc.sql("""
                        UPDATE experience_evidence_links
                        SET experience_item_id=:toItemId,relation_kind='CORROBORATING'
                        WHERE user_id=:userId AND experience_item_id=:fromItemId
                        """)
                .param("toItemId", toItemId)
                .param("userId", userId)
                .param("fromItemId", fromItemId)
                .update();
    }

    public void redirectInboundMatches(UUID userId, UUID fromItemId, UUID toItemId, Instant now) {
        jdbc.sql("""
                        UPDATE experience_items
                        SET matched_experience_item_id=:toItemId,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND matched_experience_item_id=:fromItemId
                          AND deleted_at IS NULL
                        """)
                .param("toItemId", toItemId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("fromItemId", fromItemId)
                .update();
    }

    public void clearInboundMatches(UUID userId, UUID itemId, Instant now) {
        jdbc.sql("""
                        UPDATE experience_items
                        SET match_kind='NEW',matched_experience_item_id=NULL,match_similarity=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND matched_experience_item_id=:itemId
                          AND deleted_at IS NULL
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("itemId", itemId)
                .update();
    }

    public void deleteItem(UUID userId, UUID itemId) {
        jdbc.sql("DELETE FROM experience_items WHERE user_id=:userId AND id=:itemId")
                .param("userId", userId)
                .param("itemId", itemId)
                .update();
    }

    public List<UUID> findOrphanUnverifiedItems(UUID userId) {
        return jdbc.sql("""
                        SELECT item.id
                        FROM experience_items item
                        WHERE item.user_id=:userId AND item.deleted_at IS NULL
                          AND item.verification_status <> 'VERIFIED'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM experience_evidence_links link
                              JOIN profile_evidence evidence
                                ON evidence.user_id=link.user_id
                               AND evidence.id=link.profile_evidence_id
                              WHERE link.user_id=item.user_id
                                AND link.experience_item_id=item.id
                                AND evidence.source_deleted_at IS NULL
                          )
                        ORDER BY item.id
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    private ExperienceItemRecord item(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExperienceItemRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("canonical_evidence_id", UUID.class),
                resultSet.getString("evidence_category"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                EvidenceVerificationStatus.valueOf(resultSet.getString("verification_status")),
                ExperienceMatchKind.valueOf(resultSet.getString("match_kind")),
                resultSet.getObject("matched_experience_item_id", UUID.class),
                resultSet.getBigDecimal("match_similarity"),
                resultSet.getString("match_policy_version"),
                resultSet.getString("canonical_fingerprint"),
                resultSet.getInt("source_count"),
                resultSet.getInt("document_source_count"),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private ExperienceSourceRecord source(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExperienceSourceRecord(
                resultSet.getObject("evidence_id", UUID.class),
                EvidenceSourceType.valueOf(resultSet.getString("source_type")),
                resultSet.getObject("document_id", UUID.class),
                EvidenceVerificationStatus.valueOf(resultSet.getString("verification_status")),
                ExperienceLinkKind.valueOf(resultSet.getString("relation_kind")),
                resultSet.getBigDecimal("similarity"),
                instantNullable(resultSet, "source_deleted_at"),
                instant(resultSet, "created_at"));
    }

    private String vector(List<Double> values) {
        return values.stream().map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant instantNullable(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
