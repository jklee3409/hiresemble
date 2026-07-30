CREATE TABLE cover_letters (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    title varchar(300) NOT NULL,
    status varchar(20) NOT NULL,
    finalized_at timestamptz NULL,
    archived_at timestamptz NULL,
    deleted_at timestamptz NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT cover_letters_pk PRIMARY KEY (id),
    CONSTRAINT cover_letters_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letters_job_owner_fk FOREIGN KEY (user_id, job_posting_id)
        REFERENCES job_postings(user_id, id),
    CONSTRAINT cover_letters_title_ck CHECK (
        title = btrim(title) AND char_length(title) BETWEEN 1 AND 300
    ),
    CONSTRAINT cover_letters_status_ck CHECK (
        status IN ('DRAFT', 'FINALIZED', 'ARCHIVED')
    ),
    CONSTRAINT cover_letters_archive_shape_ck CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        OR (status IN ('DRAFT', 'FINALIZED') AND archived_at IS NULL)
    ),
    CONSTRAINT cover_letters_finalized_shape_ck CHECK (
        status <> 'FINALIZED' OR finalized_at IS NOT NULL
    ),
    CONSTRAINT cover_letters_version_ck CHECK (version >= 0)
);

CREATE UNIQUE INDEX cover_letters_active_job_uk
    ON cover_letters (user_id, job_posting_id)
    WHERE deleted_at IS NULL AND status IN ('DRAFT', 'FINALIZED');
CREATE INDEX cover_letters_owner_updated_ix
    ON cover_letters (user_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX cover_letters_owner_status_ix
    ON cover_letters (user_id, status, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE cover_letter_questions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    cover_letter_id uuid NOT NULL,
    question_order integer NOT NULL,
    question_text varchar(2000) NOT NULL,
    max_length integer NULL,
    memo varchar(2000) NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT cover_letter_questions_pk PRIMARY KEY (id),
    CONSTRAINT cover_letter_questions_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letter_questions_cover_owner_id_uk
        UNIQUE (user_id, cover_letter_id, id),
    CONSTRAINT cover_letter_questions_cover_owner_fk
        FOREIGN KEY (user_id, cover_letter_id)
        REFERENCES cover_letters(user_id, id),
    CONSTRAINT cover_letter_questions_order_ck CHECK (question_order BETWEEN 1 AND 20),
    CONSTRAINT cover_letter_questions_text_ck CHECK (
        question_text = btrim(question_text)
        AND char_length(question_text) BETWEEN 1 AND 2000
    ),
    CONSTRAINT cover_letter_questions_max_length_ck CHECK (
        max_length IS NULL OR max_length BETWEEN 1 AND 10000
    ),
    CONSTRAINT cover_letter_questions_memo_ck CHECK (
        memo IS NULL OR char_length(memo) BETWEEN 1 AND 2000
    ),
    CONSTRAINT cover_letter_questions_version_ck CHECK (version >= 0)
);

CREATE UNIQUE INDEX cover_letter_questions_active_order_uk
    ON cover_letter_questions (user_id, cover_letter_id, question_order)
    WHERE deleted_at IS NULL;
CREATE INDEX cover_letter_questions_cover_ix
    ON cover_letter_questions (user_id, cover_letter_id, question_order, id)
    WHERE deleted_at IS NULL;

CREATE TABLE cover_letter_answer_versions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    question_id uuid NOT NULL,
    parent_version_id uuid NULL,
    restored_from_version_id uuid NULL,
    version_no integer NOT NULL,
    content_json jsonb NOT NULL,
    content_text varchar(20000) NOT NULL,
    character_count integer NOT NULL,
    source_type varchar(30) NOT NULL,
    is_current boolean NOT NULL,
    created_by varchar(10) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT cover_letter_answer_versions_pk PRIMARY KEY (id),
    CONSTRAINT cover_letter_answer_versions_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letter_answer_versions_question_owner_id_uk
        UNIQUE (user_id, question_id, id),
    CONSTRAINT cover_letter_answer_versions_question_version_uk
        UNIQUE (user_id, question_id, version_no),
    CONSTRAINT cover_letter_answer_versions_question_owner_fk
        FOREIGN KEY (user_id, question_id)
        REFERENCES cover_letter_questions(user_id, id),
    CONSTRAINT cover_letter_answer_versions_parent_owner_fk
        FOREIGN KEY (user_id, question_id, parent_version_id)
        REFERENCES cover_letter_answer_versions(user_id, question_id, id),
    CONSTRAINT cover_letter_answer_versions_restored_owner_fk
        FOREIGN KEY (user_id, question_id, restored_from_version_id)
        REFERENCES cover_letter_answer_versions(user_id, question_id, id),
    CONSTRAINT cover_letter_answer_versions_version_ck CHECK (version_no >= 1),
    CONSTRAINT cover_letter_answer_versions_content_json_ck CHECK (
        jsonb_typeof(content_json) = 'object'
        AND content_json ->> 'type' = 'doc'
    ),
    CONSTRAINT cover_letter_answer_versions_content_ck CHECK (
        char_length(content_text) <= 20000
        AND character_count BETWEEN 0 AND 20000
    ),
    CONSTRAINT cover_letter_answer_versions_source_ck CHECK (
        source_type IN ('AI_GENERATED', 'USER_EDITED', 'AI_REVISED', 'RESTORED')
    ),
    CONSTRAINT cover_letter_answer_versions_created_by_ck CHECK (
        created_by IN ('USER', 'AI')
    ),
    CONSTRAINT cover_letter_answer_versions_source_actor_ck CHECK (
        (source_type IN ('AI_GENERATED', 'AI_REVISED') AND created_by = 'AI')
        OR (source_type IN ('USER_EDITED', 'RESTORED') AND created_by = 'USER')
    ),
    CONSTRAINT cover_letter_answer_versions_restore_shape_ck CHECK (
        (source_type = 'RESTORED' AND restored_from_version_id IS NOT NULL)
        OR (source_type <> 'RESTORED' AND restored_from_version_id IS NULL)
    )
);

CREATE UNIQUE INDEX cover_letter_answer_versions_current_uk
    ON cover_letter_answer_versions (user_id, question_id)
    WHERE is_current;
CREATE INDEX cover_letter_answer_versions_question_ix
    ON cover_letter_answer_versions (user_id, question_id, version_no DESC, id DESC);

CREATE TABLE cover_letter_evidence_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    answer_version_id uuid NOT NULL,
    profile_evidence_id uuid NOT NULL,
    claim_text varchar(2000) NOT NULL,
    usage_type varchar(40) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT cover_letter_evidence_links_pk PRIMARY KEY (id),
    CONSTRAINT cover_letter_evidence_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letter_evidence_links_answer_owner_fk
        FOREIGN KEY (user_id, answer_version_id)
        REFERENCES cover_letter_answer_versions(user_id, id),
    CONSTRAINT cover_letter_evidence_links_evidence_owner_fk
        FOREIGN KEY (user_id, profile_evidence_id)
        REFERENCES profile_evidence(user_id, id),
    CONSTRAINT cover_letter_evidence_links_claim_ck CHECK (
        claim_text = btrim(claim_text)
        AND char_length(claim_text) BETWEEN 1 AND 2000
    ),
    CONSTRAINT cover_letter_evidence_links_usage_ck CHECK (
        usage_type IN ('SUPPORTING_CLAIM', 'PREFERRED_CONTEXT', 'FACT_CHECK')
    ),
    CONSTRAINT cover_letter_evidence_links_identity_uk
        UNIQUE (user_id, answer_version_id, profile_evidence_id, usage_type, claim_text)
);

CREATE INDEX cover_letter_evidence_links_answer_ix
    ON cover_letter_evidence_links (user_id, answer_version_id, created_at, id);
CREATE INDEX cover_letter_evidence_links_evidence_ix
    ON cover_letter_evidence_links (user_id, profile_evidence_id, created_at DESC, id DESC);

CREATE TABLE cover_letter_verifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    answer_version_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    issues jsonb NOT NULL DEFAULT '[]'::jsonb,
    suggestions jsonb NOT NULL DEFAULT '[]'::jsonb,
    verified_claims jsonb NOT NULL DEFAULT '[]'::jsonb,
    agent_run_id uuid NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT cover_letter_verifications_pk PRIMARY KEY (id),
    CONSTRAINT cover_letter_verifications_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letter_verifications_answer_owner_fk
        FOREIGN KEY (user_id, answer_version_id)
        REFERENCES cover_letter_answer_versions(user_id, id),
    CONSTRAINT cover_letter_verifications_run_owner_fk
        FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT cover_letter_verifications_status_ck CHECK (
        status IN ('PENDING', 'PASSED', 'WARNING', 'FAILED')
    ),
    CONSTRAINT cover_letter_verifications_json_ck CHECK (
        jsonb_typeof(issues) = 'array'
        AND jsonb_typeof(suggestions) = 'array'
        AND jsonb_typeof(verified_claims) = 'array'
        AND jsonb_array_length(issues) <= 100
        AND jsonb_array_length(suggestions) <= 20
        AND jsonb_array_length(verified_claims) <= 100
    )
);

CREATE INDEX cover_letter_verifications_answer_ix
    ON cover_letter_verifications (user_id, answer_version_id, created_at DESC, id DESC);
CREATE INDEX cover_letter_verifications_run_ix
    ON cover_letter_verifications (user_id, agent_run_id)
    WHERE agent_run_id IS NOT NULL;

CREATE TABLE cover_letter_verification_acknowledgements (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    cover_letter_id uuid NOT NULL,
    verification_id uuid NOT NULL,
    acknowledged_at timestamptz NOT NULL,
    CONSTRAINT cover_letter_verification_acknowledgements_pk PRIMARY KEY (id),
    CONSTRAINT cover_letter_verification_ack_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT cover_letter_verification_ack_cover_owner_fk
        FOREIGN KEY (user_id, cover_letter_id)
        REFERENCES cover_letters(user_id, id),
    CONSTRAINT cover_letter_verification_ack_verification_owner_fk
        FOREIGN KEY (user_id, verification_id)
        REFERENCES cover_letter_verifications(user_id, id),
    CONSTRAINT cover_letter_verification_ack_identity_uk
        UNIQUE (user_id, cover_letter_id, verification_id)
);

CREATE FUNCTION require_verified_cover_letter_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM cover_letter_answer_versions restored
        JOIN cover_letter_evidence_links original
          ON original.user_id = restored.user_id
         AND original.answer_version_id = restored.restored_from_version_id
         AND original.profile_evidence_id = NEW.profile_evidence_id
         AND original.claim_text = NEW.claim_text
         AND original.usage_type = NEW.usage_type
        WHERE restored.user_id = NEW.user_id
          AND restored.id = NEW.answer_version_id
          AND restored.source_type = 'RESTORED'
    ) THEN
        RETURN NEW;
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM profile_evidence evidence
        WHERE evidence.user_id = NEW.user_id
          AND evidence.id = NEW.profile_evidence_id
          AND evidence.verification_status = 'VERIFIED'
          AND evidence.source_deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'cover_letter_evidence_links_verified_ck',
            MESSAGE = 'new cover letter provenance requires verified active evidence';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER cover_letter_evidence_links_verified_tr
BEFORE INSERT ON cover_letter_evidence_links
FOR EACH ROW EXECUTE FUNCTION require_verified_cover_letter_evidence();

CREATE FUNCTION reject_cover_letter_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = TG_TABLE_NAME || '_immutable_ck',
        MESSAGE = 'persisted cover letter provenance is immutable';
END;
$$;

CREATE FUNCTION enforce_cover_letter_answer_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.is_current
       AND NOT NEW.is_current
       AND (to_jsonb(OLD) - 'is_current') = (to_jsonb(NEW) - 'is_current') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'cover_letter_answer_versions_immutable_ck',
        MESSAGE = 'answer versions are immutable except for current retirement';
END;
$$;

CREATE FUNCTION enforce_cover_letter_verification_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.status = 'PENDING'
       AND NEW.status IN ('PASSED', 'WARNING', 'FAILED')
       AND OLD.id = NEW.id
       AND OLD.user_id = NEW.user_id
       AND OLD.answer_version_id = NEW.answer_version_id
       AND OLD.agent_run_id IS NOT DISTINCT FROM NEW.agent_run_id
       AND OLD.created_at = NEW.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'cover_letter_verifications_immutable_ck',
        MESSAGE = 'terminal cover letter verifications are immutable';
END;
$$;

CREATE TRIGGER cover_letter_answer_versions_immutable_tr
BEFORE UPDATE OR DELETE ON cover_letter_answer_versions
FOR EACH ROW EXECUTE FUNCTION enforce_cover_letter_answer_immutability();
CREATE TRIGGER cover_letter_evidence_links_immutable_tr
BEFORE UPDATE OR DELETE ON cover_letter_evidence_links
FOR EACH ROW EXECUTE FUNCTION reject_cover_letter_immutable_mutation();
CREATE TRIGGER cover_letter_verifications_immutable_tr
BEFORE UPDATE OR DELETE ON cover_letter_verifications
FOR EACH ROW EXECUTE FUNCTION enforce_cover_letter_verification_immutability();
CREATE TRIGGER cover_letter_verification_ack_immutable_tr
BEFORE UPDATE OR DELETE ON cover_letter_verification_acknowledgements
FOR EACH ROW EXECUTE FUNCTION reject_cover_letter_immutable_mutation();

ALTER TABLE agent_run_resource_links
    ADD COLUMN cover_letter_id uuid NULL,
    ADD COLUMN cover_letter_answer_version_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_exactly_one_ck,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    DROP CONSTRAINT agent_run_resource_links_analysis_secondary_ck,
    ADD CONSTRAINT agent_run_resource_links_cover_owner_fk
        FOREIGN KEY (user_id, cover_letter_id) REFERENCES cover_letters(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_answer_owner_fk
        FOREIGN KEY (user_id, cover_letter_answer_version_id)
        REFERENCES cover_letter_answer_versions(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_exactly_one_ck CHECK (
        num_nonnulls(
            document_id,
            job_posting_id,
            job_analysis_id,
            cover_letter_id,
            cover_letter_answer_version_id
        ) = 1
    ),
    ADD CONSTRAINT agent_run_resource_links_kind_ck CHECK (
        (resource_kind = 'DOCUMENT'
            AND document_id IS NOT NULL
            AND num_nonnulls(job_posting_id, job_analysis_id, cover_letter_id,
                cover_letter_answer_version_id) = 0)
        OR (resource_kind = 'JOB'
            AND job_posting_id IS NOT NULL
            AND num_nonnulls(document_id, job_analysis_id, cover_letter_id,
                cover_letter_answer_version_id) = 0)
        OR (resource_kind = 'JOB_ANALYSIS'
            AND job_analysis_id IS NOT NULL
            AND num_nonnulls(document_id, job_posting_id, cover_letter_id,
                cover_letter_answer_version_id) = 0)
        OR (resource_kind = 'COVER_LETTER'
            AND cover_letter_id IS NOT NULL
            AND num_nonnulls(document_id, job_posting_id, job_analysis_id,
                cover_letter_answer_version_id) = 0)
        OR (resource_kind = 'COVER_LETTER_ANSWER_VERSION'
            AND cover_letter_answer_version_id IS NOT NULL
            AND num_nonnulls(document_id, job_posting_id, job_analysis_id,
                cover_letter_id) = 0)
    ),
    ADD CONSTRAINT agent_run_resource_links_secondary_kind_ck CHECK (
        resource_kind NOT IN ('JOB_ANALYSIS', 'COVER_LETTER_ANSWER_VERSION')
        OR NOT primary_resource
    );

CREATE INDEX agent_run_resource_links_cover_ix
    ON agent_run_resource_links (user_id, cover_letter_id, created_at DESC)
    WHERE cover_letter_id IS NOT NULL;
CREATE INDEX agent_run_resource_links_answer_ix
    ON agent_run_resource_links (user_id, cover_letter_answer_version_id, created_at DESC)
    WHERE cover_letter_answer_version_id IS NOT NULL;
CREATE UNIQUE INDEX agent_run_resource_links_answer_run_uk
    ON agent_run_resource_links (user_id, agent_run_id, cover_letter_answer_version_id)
    WHERE resource_kind = 'COVER_LETTER_ANSWER_VERSION';

CREATE OR REPLACE FUNCTION assert_agent_run_document_resource_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agent_run_resource_links link
        JOIN agent_runs run ON run.user_id = link.user_id AND run.id = link.agent_run_id
        WHERE link.primary_resource
          AND (
              (link.resource_kind = 'DOCUMENT'
                  AND (run.resource_type IS DISTINCT FROM 'DOCUMENT'
                      OR run.resource_id IS DISTINCT FROM link.document_id))
              OR (link.resource_kind = 'JOB'
                  AND (run.resource_type IS DISTINCT FROM 'JOB'
                      OR run.resource_id IS DISTINCT FROM link.job_posting_id))
              OR (link.resource_kind = 'COVER_LETTER'
                  AND (run.resource_type IS DISTINCT FROM 'COVER_LETTER'
                      OR run.resource_id IS DISTINCT FROM link.cover_letter_id))
          )
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type IN ('DOCUMENT', 'JOB', 'COVER_LETTER')
          AND NOT EXISTS (
              SELECT 1
              FROM agent_run_resource_links link
              WHERE link.user_id = run.user_id
                AND link.agent_run_id = run.id
                AND link.primary_resource
                AND (
                    (run.resource_type = 'DOCUMENT'
                        AND link.resource_kind = 'DOCUMENT'
                        AND link.document_id = run.resource_id)
                    OR (run.resource_type = 'JOB'
                        AND link.resource_kind = 'JOB'
                        AND link.job_posting_id = run.resource_id)
                    OR (run.resource_type = 'COVER_LETTER'
                        AND link.resource_kind = 'COVER_LETTER'
                        AND link.cover_letter_id = run.resource_id)
                )
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'agent_run_typed_resource_parity_ck',
            MESSAGE = 'resource projection must match the authoritative typed link';
    END IF;
    RETURN NULL;
END;
$$;
