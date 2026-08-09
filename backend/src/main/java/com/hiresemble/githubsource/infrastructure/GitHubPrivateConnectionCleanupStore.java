package com.hiresemble.githubsource.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GitHubPrivateConnectionCleanupStore {

    private final JdbcClient jdbc;

    public GitHubPrivateConnectionCleanupStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int finalizeReady(Instant now) {
        List<UUID> ready = jdbc.sql("""
                        SELECT connection.id
                        FROM github_app_connections connection
                        WHERE connection.status='DISCONNECTING'
                          AND EXISTS (
                              SELECT 1 FROM github_installation_revocation_outbox revocation
                              WHERE revocation.github_app_connection_id=connection.id
                                AND revocation.status='SUCCEEDED'
                          )
                          AND NOT EXISTS (
                              SELECT 1 FROM github_repository_snapshots snapshot
                              WHERE snapshot.user_id=connection.user_id
                                AND snapshot.github_app_connection_id=connection.id
                                AND NOT EXISTS (
                                    SELECT 1 FROM github_snapshot_object_deletion_outbox deletion
                                    WHERE deletion.storage_key=snapshot.snapshot_storage_key
                                      AND deletion.status='SUCCEEDED'
                                )
                          )
                        ORDER BY connection.updated_at,connection.id
                        FOR UPDATE SKIP LOCKED
                        LIMIT 20
                        """)
                .query(UUID.class)
                .list();
        ready.forEach(connectionId -> finalizeConnection(connectionId, now));
        return ready.size();
    }

    private void finalizeConnection(UUID connectionId, Instant now) {
        jdbc.sql("""
                        DELETE FROM github_evidence_unit_links link
                        USING profile_evidence evidence,github_repository_snapshots snapshot
                        WHERE link.user_id=evidence.user_id
                          AND link.profile_evidence_id=evidence.id
                          AND evidence.user_id=snapshot.user_id
                          AND evidence.github_snapshot_id=snapshot.id
                          AND snapshot.github_app_connection_id=:connectionId
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        UPDATE profile_evidence evidence
                        SET source_entity_id=NULL,document_id=NULL,title='[SOURCE DELETED]',
                            content='[SOURCE DELETED]',metadata='{}'::jsonb,confidence=NULL,
                            verification_status='SOURCE_DELETED',verified_at=NULL,
                            source_deleted_at=COALESCE(source_deleted_at,:now),
                            github_source_id=NULL,github_repository_id=NULL,
                            github_snapshot_id=NULL,github_claim_key=NULL,
                            version=version+1,updated_at=:now
                        FROM github_repository_snapshots snapshot
                        WHERE evidence.user_id=snapshot.user_id
                          AND evidence.github_snapshot_id=snapshot.id
                          AND snapshot.github_app_connection_id=:connectionId
                        """)
                .param("now", utc(now))
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        DELETE FROM github_source_units unit
                        USING github_repository_snapshots snapshot
                        WHERE unit.user_id=snapshot.user_id AND unit.snapshot_id=snapshot.id
                          AND snapshot.github_app_connection_id=:connectionId
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        UPDATE github_snapshot_object_deletion_outbox deletion
                        SET snapshot_id=NULL
                        FROM github_repository_snapshots snapshot
                        WHERE deletion.snapshot_id=snapshot.id
                          AND deletion.user_id=snapshot.user_id
                          AND snapshot.github_app_connection_id=:connectionId
                          AND deletion.status='SUCCEEDED'
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        DELETE FROM github_repository_snapshots
                        WHERE github_app_connection_id=:connectionId
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        DELETE FROM github_source_repository_links link
                        USING github_sources source
                        WHERE link.user_id=source.user_id AND link.github_source_id=source.id
                          AND source.github_app_connection_id=:connectionId
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        DELETE FROM github_app_connection_repository_access
                        WHERE github_app_connection_id=:connectionId
                        """)
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        DELETE FROM github_repositories repository
                        WHERE NOT EXISTS (
                            SELECT 1 FROM github_source_repository_links link
                            WHERE link.user_id=repository.user_id
                              AND link.github_repository_id=repository.id
                        ) AND NOT EXISTS (
                            SELECT 1 FROM github_app_connection_repository_access access
                            WHERE access.user_id=repository.user_id
                              AND access.github_repository_id=repository.id
                        ) AND NOT EXISTS (
                            SELECT 1 FROM github_repository_snapshots snapshot
                            WHERE snapshot.user_id=repository.user_id
                              AND snapshot.github_repository_id=repository.id
                        )
                        """).update();
        jdbc.sql("""
                        UPDATE github_sources
                        SET original_url=CASE source_kind
                                WHEN 'ACCOUNT' THEN 'https://github.com/disconnected-' || left(id::text,8)
                                ELSE 'https://github.com/disconnected-' || left(id::text,8) || '/private-source'
                            END,
                            canonical_url=CASE source_kind
                                WHEN 'ACCOUNT' THEN 'https://github.com/disconnected-' || left(id::text,8)
                                ELSE 'https://github.com/disconnected-' || left(id::text,8) || '/private-source'
                            END,
                            owner_login='disconnected-' || left(id::text,8),
                            repository_name=CASE source_kind WHEN 'REPOSITORY' THEN 'private-source' ELSE NULL END,
                            deleted_at=COALESCE(deleted_at,:now),updated_at=:now,version=version+1
                        WHERE github_app_connection_id=:connectionId
                        """)
                .param("now", utc(now))
                .param("connectionId", connectionId)
                .update();
        jdbc.sql("""
                        UPDATE github_app_connections
                        SET status='DISCONNECTED',disconnected_at=:now,
                            version=version+1,updated_at=:now,last_checked_at=:now
                        WHERE id=:connectionId AND status='DISCONNECTING'
                        """)
                .param("now", utc(now))
                .param("connectionId", connectionId)
                .update();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
