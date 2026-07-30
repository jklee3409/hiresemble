CREATE TABLE job_analyses (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    analysis_version integer NOT NULL,
    job_version bigint NOT NULL,
    job_content_hash char(64) NOT NULL,
    profile_snapshot_hash char(64) NOT NULL,
    evidence_snapshot_hash char(64) NOT NULL,
    context_hash char(64) NOT NULL,
    eligibility varchar(30) NOT NULL,
    fit_score numeric(5,2) NOT NULL,
    responsibilities jsonb NOT NULL DEFAULT '[]'::jsonb,
    required_qualifications jsonb NOT NULL DEFAULT '[]'::jsonb,
    preferred_qualifications jsonb NOT NULL DEFAULT '[]'::jsonb,
    strengths jsonb NOT NULL DEFAULT '[]'::jsonb,
    gaps jsonb NOT NULL DEFAULT '[]'::jsonb,
    analysis_summary varchar(10000) NULL,
    rubric_version varchar(100) NOT NULL,
    workflow_version varchar(100) NOT NULL,
    quality_mode varchar(30) NOT NULL,
    embedding_policy_version bigint NOT NULL,
    embedding_generation integer NOT NULL,
    retrieval_policy_version varchar(100) NOT NULL,
    agent_run_id uuid NOT NULL,
    sealed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT job_analyses_pk PRIMARY KEY (id),
    CONSTRAINT job_analyses_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_analyses_job_version_uk
        UNIQUE (user_id, job_posting_id, analysis_version),
    CONSTRAINT job_analyses_job_owner_fk FOREIGN KEY (user_id, job_posting_id)
        REFERENCES job_postings(user_id, id),
    CONSTRAINT job_analyses_run_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT job_analyses_embedding_policy_fk FOREIGN KEY (embedding_policy_version)
        REFERENCES embedding_policy_versions(version),
    CONSTRAINT job_analyses_version_ck CHECK (analysis_version >= 1 AND job_version >= 0),
    CONSTRAINT job_analyses_hash_ck CHECK (
        job_content_hash ~ '^[0-9a-f]{64}$'
        AND profile_snapshot_hash ~ '^[0-9a-f]{64}$'
        AND evidence_snapshot_hash ~ '^[0-9a-f]{64}$'
        AND context_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT job_analyses_eligibility_ck CHECK (
        eligibility IN ('ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE', 'UNKNOWN')
    ),
    CONSTRAINT job_analyses_fit_score_ck CHECK (fit_score BETWEEN 0.00 AND 100.00),
    CONSTRAINT job_analyses_display_json_ck CHECK (
        jsonb_typeof(responsibilities) = 'array'
        AND jsonb_typeof(required_qualifications) = 'array'
        AND jsonb_typeof(preferred_qualifications) = 'array'
        AND jsonb_typeof(strengths) = 'array'
        AND jsonb_typeof(gaps) = 'array'
    ),
    CONSTRAINT job_analyses_summary_ck CHECK (
        analysis_summary IS NULL OR char_length(analysis_summary) BETWEEN 1 AND 10000
    ),
    CONSTRAINT job_analyses_versions_ck CHECK (
        char_length(rubric_version) BETWEEN 1 AND 100
        AND char_length(workflow_version) BETWEEN 1 AND 100
        AND char_length(retrieval_policy_version) BETWEEN 1 AND 100
        AND embedding_generation >= 1
    ),
    CONSTRAINT job_analyses_quality_ck CHECK (quality_mode IN ('ECONOMY', 'BALANCED'))
);

CREATE INDEX job_analyses_job_latest_ix
    ON job_analyses (user_id, job_posting_id, analysis_version DESC, id DESC);
CREATE INDEX job_analyses_job_created_ix
    ON job_analyses (user_id, job_posting_id, created_at DESC, id DESC);
CREATE INDEX job_analyses_reuse_ix
    ON job_analyses (user_id, job_posting_id, context_hash, quality_mode, created_at DESC);

CREATE TABLE job_analysis_score_criteria (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_analysis_id uuid NOT NULL,
    category varchar(60) NOT NULL,
    criterion varchar(2000) NOT NULL,
    weight numeric(5,2) NOT NULL,
    match_level varchar(20) NOT NULL,
    score numeric(5,2) NOT NULL,
    explanation varchar(2000) NOT NULL,
    source_location varchar(500) NULL,
    criterion_order integer NOT NULL,
    CONSTRAINT job_analysis_score_criteria_pk PRIMARY KEY (id),
    CONSTRAINT job_analysis_score_criteria_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_analysis_score_criteria_owner_analysis_id_uk
        UNIQUE (user_id, job_analysis_id, id),
    CONSTRAINT job_analysis_score_criteria_analysis_order_uk
        UNIQUE (user_id, job_analysis_id, criterion_order),
    CONSTRAINT job_analysis_score_criteria_analysis_owner_fk
        FOREIGN KEY (user_id, job_analysis_id)
        REFERENCES job_analyses(user_id, id),
    CONSTRAINT job_analysis_score_criteria_category_ck CHECK (
        category IN (
            'REQUIRED_QUALIFICATION',
            'CORE_RESPONSIBILITY_OR_SKILL',
            'PREFERRED_QUALIFICATION',
            'RELATED_EXPERIENCE_OR_DOMAIN',
            'EDUCATION_CERTIFICATION_LANGUAGE'
        )
    ),
    CONSTRAINT job_analysis_score_criteria_match_ck CHECK (
        match_level IN ('MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN')
    ),
    CONSTRAINT job_analysis_score_criteria_text_ck CHECK (
        criterion = btrim(criterion)
        AND explanation = btrim(explanation)
        AND char_length(criterion) BETWEEN 1 AND 2000
        AND char_length(explanation) BETWEEN 1 AND 2000
        AND (source_location IS NULL OR char_length(source_location) BETWEEN 1 AND 500)
    ),
    CONSTRAINT job_analysis_score_criteria_score_ck CHECK (
        weight BETWEEN 0.00 AND 100.00
        AND score BETWEEN 0.00 AND weight
    ),
    CONSTRAINT job_analysis_score_criteria_order_ck CHECK (criterion_order >= 0)
);

CREATE INDEX job_analysis_score_criteria_analysis_ix
    ON job_analysis_score_criteria (user_id, job_analysis_id, criterion_order, id);

CREATE TABLE job_analysis_evidence_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_analysis_id uuid NOT NULL,
    score_criterion_id uuid NULL,
    profile_evidence_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    evidence_hash char(64) NOT NULL,
    usage_type varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT job_analysis_evidence_links_pk PRIMARY KEY (id),
    CONSTRAINT job_analysis_evidence_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_analysis_evidence_links_analysis_owner_fk
        FOREIGN KEY (user_id, job_analysis_id)
        REFERENCES job_analyses(user_id, id),
    CONSTRAINT job_analysis_evidence_links_criterion_owner_fk
        FOREIGN KEY (user_id, job_analysis_id, score_criterion_id)
        REFERENCES job_analysis_score_criteria(user_id, job_analysis_id, id),
    CONSTRAINT job_analysis_evidence_links_evidence_owner_fk
        FOREIGN KEY (user_id, profile_evidence_id)
        REFERENCES profile_evidence(user_id, id),
    CONSTRAINT job_analysis_evidence_links_usage_ck CHECK (
        usage_type IN ('CRITERION_MATCH', 'ELIGIBILITY', 'STRENGTH')
    ),
    CONSTRAINT job_analysis_evidence_links_shape_ck CHECK (
        (usage_type = 'CRITERION_MATCH' AND score_criterion_id IS NOT NULL)
        OR (usage_type IN ('ELIGIBILITY', 'STRENGTH') AND score_criterion_id IS NULL)
    ),
    CONSTRAINT job_analysis_evidence_links_snapshot_ck CHECK (
        evidence_version >= 0 AND evidence_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE UNIQUE INDEX job_analysis_evidence_links_identity_uk
    ON job_analysis_evidence_links (
        user_id,
        job_analysis_id,
        COALESCE(score_criterion_id, '00000000-0000-0000-0000-000000000000'::uuid),
        profile_evidence_id,
        usage_type
    );
CREATE INDEX job_analysis_evidence_links_analysis_ix
    ON job_analysis_evidence_links (user_id, job_analysis_id, created_at, id);
CREATE INDEX job_analysis_evidence_links_evidence_ix
    ON job_analysis_evidence_links (user_id, profile_evidence_id, created_at DESC, id DESC);

CREATE FUNCTION enforce_job_analysis_next_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    expected_version integer;
BEGIN
    PERFORM 1
    FROM job_postings
    WHERE user_id = NEW.user_id AND id = NEW.job_posting_id
    FOR UPDATE;

    SELECT COALESCE(max(analysis_version), 0) + 1
    INTO expected_version
    FROM job_analyses
    WHERE user_id = NEW.user_id AND job_posting_id = NEW.job_posting_id;

    IF NEW.analysis_version <> expected_version THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'job_analyses_monotonic_version_ck',
            MESSAGE = 'job analysis version must increase by one';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER job_analyses_monotonic_version_tr
BEFORE INSERT ON job_analyses
FOR EACH ROW EXECUTE FUNCTION enforce_job_analysis_next_version();

CREATE FUNCTION require_verified_job_analysis_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
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
            CONSTRAINT = 'job_analysis_evidence_links_verified_ck',
            MESSAGE = 'job analysis provenance requires verified active evidence';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER job_analysis_evidence_links_verified_tr
BEFORE INSERT ON job_analysis_evidence_links
FOR EACH ROW EXECUTE FUNCTION require_verified_job_analysis_evidence();

ALTER TABLE agent_run_resource_links
    ADD COLUMN job_analysis_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_primary_ck,
    DROP CONSTRAINT agent_run_resource_links_exactly_one_ck,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    ADD CONSTRAINT agent_run_resource_links_analysis_owner_fk
        FOREIGN KEY (user_id, job_analysis_id) REFERENCES job_analyses(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_exactly_one_ck CHECK (
        num_nonnulls(document_id, job_posting_id, job_analysis_id) = 1
    ),
    ADD CONSTRAINT agent_run_resource_links_kind_ck CHECK (
        (resource_kind = 'DOCUMENT'
            AND document_id IS NOT NULL
            AND job_posting_id IS NULL
            AND job_analysis_id IS NULL)
        OR (resource_kind = 'JOB'
            AND document_id IS NULL
            AND job_posting_id IS NOT NULL
            AND job_analysis_id IS NULL)
        OR (resource_kind = 'JOB_ANALYSIS'
            AND document_id IS NULL
            AND job_posting_id IS NULL
            AND job_analysis_id IS NOT NULL)
    ),
    ADD CONSTRAINT agent_run_resource_links_analysis_secondary_ck CHECK (
        resource_kind <> 'JOB_ANALYSIS' OR NOT primary_resource
    );

CREATE INDEX agent_run_resource_links_analysis_ix
    ON agent_run_resource_links (user_id, job_analysis_id, created_at DESC)
    WHERE job_analysis_id IS NOT NULL;
CREATE UNIQUE INDEX agent_run_resource_links_analysis_run_uk
    ON agent_run_resource_links (user_id, agent_run_id, job_analysis_id)
    WHERE resource_kind = 'JOB_ANALYSIS';

CREATE FUNCTION reject_job_analysis_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = TG_TABLE_NAME || '_immutable_ck',
        MESSAGE = 'persisted job analysis provenance is immutable';
END;
$$;

CREATE FUNCTION enforce_job_analysis_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NOT OLD.sealed
       AND NEW.sealed
       AND (to_jsonb(OLD) - 'sealed') = (to_jsonb(NEW) - 'sealed') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'job_analyses_immutable_ck',
        MESSAGE = 'persisted job analysis is immutable';
END;
$$;

CREATE TRIGGER job_analyses_immutable_tr
BEFORE UPDATE OR DELETE ON job_analyses
FOR EACH ROW EXECUTE FUNCTION enforce_job_analysis_immutability();

CREATE TRIGGER job_analysis_score_criteria_immutable_tr
BEFORE UPDATE OR DELETE ON job_analysis_score_criteria
FOR EACH ROW EXECUTE FUNCTION reject_job_analysis_mutation();

CREATE TRIGGER job_analysis_evidence_links_immutable_tr
BEFORE UPDATE OR DELETE ON job_analysis_evidence_links
FOR EACH ROW EXECUTE FUNCTION reject_job_analysis_mutation();

CREATE FUNCTION require_open_job_analysis()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM job_analyses analysis
        WHERE analysis.user_id = NEW.user_id
          AND analysis.id = NEW.job_analysis_id
          AND NOT analysis.sealed
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'job_analysis_children_sealed_ck',
            MESSAGE = 'sealed job analysis provenance cannot be extended';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER job_analysis_score_criteria_open_tr
BEFORE INSERT ON job_analysis_score_criteria
FOR EACH ROW EXECUTE FUNCTION require_open_job_analysis();

CREATE TRIGGER job_analysis_evidence_links_open_tr
BEFORE INSERT ON job_analysis_evidence_links
FOR EACH ROW EXECUTE FUNCTION require_open_job_analysis();

CREATE FUNCTION assert_job_analysis_has_criteria()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM job_analyses analysis
        WHERE NOT analysis.sealed
           OR NOT EXISTS (
            SELECT 1
            FROM job_analysis_score_criteria criterion
            WHERE criterion.user_id = analysis.user_id
              AND criterion.job_analysis_id = analysis.id
        )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'job_analyses_criteria_required_ck',
            MESSAGE = 'a persisted job analysis requires at least one criterion';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER job_analyses_criteria_required_ct
AFTER INSERT ON job_analyses
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_job_analysis_has_criteria();

CREATE CONSTRAINT TRIGGER job_analysis_criteria_required_ct
AFTER INSERT OR UPDATE OR DELETE ON job_analysis_score_criteria
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_job_analysis_has_criteria();
