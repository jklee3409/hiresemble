package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.support.PostgresIntegrationTest;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class Phase5MigrationContractTest extends PostgresIntegrationTest {

    private static final String V1_THROUGH_V28_SHA256 = """
            V1__enable_extensions.sql 9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c
            V2__create_identity_session_idempotency.sql c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf
            V3__create_structured_profiles_and_direct_evidence.sql 6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347
            V4__create_agent_runtime_and_ai_budget.sql 706db49cbd3f39e870c3101eae4f08534236e4954ffbbec0d55dfab48626e01f
            V5__create_documents_evidence_and_storage_outbox.sql cfae1322bbf7d1412d0be5b7f4535f78ae2702f189b6b095527b6b2186f1dcea
            V6__create_job_postings_and_extend_agent_resources.sql 08248409866a7b396b3f83285ef0b06d30160e1d2a81777ec4264400d80a7d88
            V7__create_job_analyses_and_provenance.sql 7d7b0088a0559626bbbf3aaa52c70a701c2e392d62da75381cd1b3fc55eb217c
            V8__create_cover_letters_versions_and_verifications.sql 4f549aa30f2b24a0d08e70b3105dd0a73c2df91f26ba873bfaacda65c0a466b8
            V9__exclude_education_evidence_and_soft_delete_agent_runs.sql 258a3ffa881a8fbb6f7ece7721039268abfaa866a9f21a0421cb411dfe50f282
            V10__exclude_document_education_evidence.sql 97459fa2eda415f8713d7db1644560106bf663f79761ede5a2481be46cadda27
            V11__derive_final_education.sql eba74b4e91fc4d9b00ad8b7b32cd892b61b604b632c0c2d64b6d1f34d336d06e
            V12__create_interview_research_questions_and_feedback.sql c7bc2332e5bcdfb112c91debe94f8cb98cebd6108dee5f96744c3ea17537c23f
            V13__add_external_ai_provider_price_catalog.sql 8af87f15f123388b095fcd62f339709e119847159a39a7db0aa01695264a104f
            V14__canonicalize_openai_embedding_policy.sql 70e495faf19126f3fb094a6be5f9e5101fef6add3c6854f629dca8f2f436cebf
            V15__create_user_activities.sql c50f614e0f8b12aba03e08a64ebce29af6415ead09372f4c0d1884d6c06b381f
            V16__create_job_auto_analysis_requests.sql cc446ca3e33cf7c9631fdfdf4c0e02c6ec030ff54d3d31262907199ade5b7c62
            V17__create_career_guide_posts.sql 1e37449bfa751d37228d2852591cd23176a92eb0a0994655393fa7b8931c9f43
            V18__expand_career_guide_content.sql b35b5c1748f9e6b8b6722fbec5c74237d19afc1764bcf685a50ce63fce4c5915
            V19__add_profile_eligibility_and_structured_fact_provenance.sql 3b728e2753d489b09dba9a76aafba97053aef7ef7fb26ca21c1c70c28c2eaa1c
            V20__add_job_analysis_coverage.sql df1c4024e313fe8ffd253bcbc254e3dab64489fccb47450dce811e67ac900e2b
            V21__classify_job_posting_periods.sql 80beaaa26de40608c069b4477457dfbfc5cd0f60643cc8d41264669de2124db2
            V22__finalize_job_posting_period_constraints.sql b911787ee31baf45bc097f0c17bd41993f6c6aaa7fce52d98c61c47993a3c656
            V23__add_selectable_openai_model_prices.sql de7f94496f2d5d7b2c560647c5d5ed8cb5e18738051153239c356aa361519a71
            V24__replace_scoped_ai_budgets_with_global_daily_budget.sql a1ca213e3ba6ec4d1410faab902750f4294ebd82e101b9737be6fa3100b51f23
            V25__rewrite_career_guide_content.sql ebef56d127ccd3845fb5591fff6afed15d7beee3ae32128701d90cfbc3f023f7
            V26__create_canonical_experience_library.sql 414a063a773b994084468dd04975c37461142452741dbd54dbfa3ac0d7dc8908
            V27__create_github_source_ingestion.sql 5754a6410887b93b4cb50d09cdf2162ea64266a400b8211a73359725dbdee666
            V28__create_career_artifacts.sql 6e6bd63dad296aa6a32e25a127b32d0d3a13a528a273b3fac5bf75eb110facd3
            """;

    @Test
    void freshSchemaIsV30WithPhase5TablesAndUnchangedWorkflowTypes() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT max(version::int) FROM flyway_schema_history WHERE success",
                        Integer.class))
                .isEqualTo(30);
        assertThat(jdbcTemplate.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema='public' AND table_name IN (
                          'github_connection_attempts','github_app_connections',
                          'github_app_connection_repository_access',
                          'github_installation_revocation_outbox','account_deletion_tasks'
                        ) ORDER BY table_name
                        """, String.class))
                .containsExactly(
                        "account_deletion_tasks",
                        "github_app_connection_repository_access",
                        "github_app_connections",
                        "github_connection_attempts",
                        "github_installation_revocation_outbox");
        assertThat(WorkflowType.values()).hasSize(11);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM information_schema.table_constraints
                        WHERE table_schema='public' AND table_name='account_deletion_tasks'
                          AND constraint_type='FOREIGN KEY'
                        """, Long.class))
                .isZero();
    }

    @Test
    void legacyPublicRowsReceiveSafeDefaultsAndOwnerShapeConstraintsRejectCrossBoundaryData() {
        UUID first = seedUser("phase5-schema-first@example.com");
        UUID second = seedUser("phase5-schema-second@example.com");
        UUID source = UUID.randomUUID();
        UUID repository = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO github_sources (
                  id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                  repository_name,source_status,created_at,updated_at)
                VALUES (?,?,'REPOSITORY',NULL,'https://github.com/acme/public',
                        'https://github.com/acme/public','acme','public','READY',now(),now())
                """, source, first);
        jdbcTemplate.update("""
                INSERT INTO github_repositories (
                  id,user_id,external_repository_id,node_id,owner_login,repository_name,
                  canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                  created_at,updated_at)
                VALUES (?,?,1001,'node-public','acme','public',
                        'https://github.com/acme/public','main',false,false,false,'[]',now(),now())
                """, repository, first);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT access_mode,github_app_connection_id FROM github_sources WHERE id=?",
                        source))
                .containsEntry("access_mode", "PUBLIC")
                .containsEntry("github_app_connection_id", null);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT visibility FROM github_repositories WHERE id=?",
                        String.class,
                        repository))
                .isEqualTo("PUBLIC");

        UUID connection = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO github_app_connections (
                  id,user_id,github_installation_id,target_account_id,target_account_login,
                  target_account_type,repository_selection,status,permission_snapshot,version,
                  connected_at,verified_at,last_checked_at,disconnected_at,created_at,updated_at)
                VALUES (?,?,7001,8001,'acme','ORGANIZATION','SELECTED','ACTIVE',
                        '{"metadata":"read","contents":"read"}',0,
                        now(),now(),now(),NULL,now(),now())
                """, connection, first);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO github_app_connections (
                          id,user_id,github_installation_id,target_account_id,target_account_login,
                          target_account_type,repository_selection,status,permission_snapshot,version,
                          connected_at,verified_at,last_checked_at,disconnected_at,created_at,updated_at)
                        VALUES (?,?,7001,8002,'other','USER','SELECTED','ACTIVE',
                                '{"metadata":"read","contents":"read"}',0,
                                now(),now(),now(),NULL,now(),now())
                        """, UUID.randomUUID(), second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO github_sources (
                          id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                          repository_name,source_status,access_mode,github_app_connection_id,
                          created_at,updated_at)
                        VALUES (?,?,'REPOSITORY',NULL,'https://github.com/acme/invalid',
                                'https://github.com/acme/invalid','acme','invalid','READY',
                                'PUBLIC',?,now(),now())
                        """, UUID.randomUUID(), first, connection))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO github_repositories (
                          id,user_id,external_repository_id,node_id,owner_login,repository_name,
                          canonical_url,default_branch,is_private,visibility,is_fork,is_archived,
                          topics,created_at,updated_at)
                        VALUES (?,?,1002,'node-invalid','acme','invalid',
                                'https://github.com/acme/invalid','main',true,'PUBLIC',
                                false,false,'[]',now(),now())
                        """, UUID.randomUUID(), first))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountDeletionTaskIsFkFreeButTerminalShapeIsStrict() {
        UUID userId = seedUser("phase5-task@example.com");
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account_deletion_tasks (
                  id,subject_user_id,status,policy_version,attempt_count,next_attempt_at,
                  claim_token,lease_expires_at,purge_by,last_error_code,requested_at,completed_at)
                VALUES (?,?,'QUEUED','terminal-purge-v1',0,now(),NULL,NULL,
                        now()+interval '24 hours',NULL,now(),NULL)
                """, taskId, userId);
        jdbcTemplate.update("DELETE FROM users WHERE id=?", userId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT subject_user_id FROM account_deletion_tasks WHERE id=?",
                        UUID.class,
                        taskId))
                .isEqualTo(userId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO account_deletion_tasks (
                          id,subject_user_id,status,policy_version,attempt_count,next_attempt_at,
                          claim_token,lease_expires_at,purge_by,last_error_code,requested_at,completed_at)
                        VALUES (?,?,'SUCCEEDED','terminal-purge-v1',0,now(),NULL,NULL,
                                now()+interval '24 hours',NULL,now(),now())
                        """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v1ThroughV28MigrationBytesRemainImmutable() throws Exception {
        for (String line : V1_THROUGH_V28_SHA256.strip().split("\\R")) {
            String[] expected = line.strip().split(" ", 2);
            try (InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/" + expected[0])) {
                assertThat(stream).as(expected[0]).isNotNull();
                String actual = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                assertThat(actual).as(expected[0]).isEqualTo(expected[1]);
            }
        }
    }

    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                  id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                  created_at,updated_at)
                VALUES (?,?,'fixture-hash','Migration User','USER','ACTIVE',now(),now(),now(),now())
                """, id, email);
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                  id,user_id,legal_name,introduction,desired_roles,desired_industries,
                  desired_locations,expected_graduation_date,version,created_at,updated_at)
                VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), id);
        return id;
    }
}
