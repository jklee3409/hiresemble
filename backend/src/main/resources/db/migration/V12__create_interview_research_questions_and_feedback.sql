CREATE TABLE research_runs (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    cover_letter_id uuid NOT NULL,
    retry_of_research_run_id uuid NULL,
    research_quality varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    source_coverage varchar(20) NULL,
    missing_coverage_topics jsonb NOT NULL DEFAULT '[]'::jsonb,
    summary varchar(10000) NULL,
    agent_run_id uuid NOT NULL,
    retryable boolean NOT NULL DEFAULT false,
    safe_error_code varchar(100) NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz NULL,
    completed_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT research_runs_pk PRIMARY KEY (id),
    CONSTRAINT research_runs_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT research_runs_agent_run_uk UNIQUE (user_id, agent_run_id),
    CONSTRAINT research_runs_retry_owner_id_uk
        UNIQUE (user_id, retry_of_research_run_id, id),
    CONSTRAINT research_runs_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT research_runs_job_owner_fk FOREIGN KEY (user_id, job_posting_id)
        REFERENCES job_postings(user_id, id),
    CONSTRAINT research_runs_cover_owner_fk FOREIGN KEY (user_id, cover_letter_id)
        REFERENCES cover_letters(user_id, id),
    CONSTRAINT research_runs_retry_owner_fk
        FOREIGN KEY (user_id, retry_of_research_run_id)
        REFERENCES research_runs(user_id, id),
    CONSTRAINT research_runs_agent_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT research_runs_quality_ck CHECK (
        research_quality IN ('BASIC', 'ADVANCED')
    ),
    CONSTRAINT research_runs_status_ck CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT research_runs_coverage_ck CHECK (
        source_coverage IS NULL OR source_coverage IN ('SUFFICIENT', 'LIMITED', 'NONE')
    ),
    CONSTRAINT research_runs_result_shape_ck CHECK (
        jsonb_typeof(missing_coverage_topics) = 'array'
        AND jsonb_array_length(missing_coverage_topics) <= 20
        AND (summary IS NULL OR char_length(summary) BETWEEN 1 AND 10000)
        AND (safe_error_code IS NULL OR char_length(safe_error_code) BETWEEN 1 AND 100)
    ),
    CONSTRAINT research_runs_terminal_shape_ck CHECK (
        (status IN ('QUEUED', 'RUNNING')
            AND source_coverage IS NULL
            AND completed_at IS NULL
            AND safe_error_code IS NULL)
        OR (status = 'SUCCEEDED'
            AND source_coverage IS NOT NULL
            AND completed_at IS NOT NULL
            AND safe_error_code IS NULL
            AND NOT retryable)
        OR (status = 'FAILED'
            AND source_coverage IS NULL
            AND completed_at IS NOT NULL
            AND safe_error_code IS NOT NULL)
        OR (status = 'CANCELLED'
            AND source_coverage IS NULL
            AND completed_at IS NOT NULL
            AND safe_error_code IS NULL
            AND NOT retryable)
    ),
    CONSTRAINT research_runs_time_ck CHECK (
        started_at IS NULL OR started_at >= created_at
    ),
    CONSTRAINT research_runs_completed_time_ck CHECK (
        completed_at IS NULL OR completed_at >= COALESCE(started_at, created_at)
    )
);

CREATE INDEX research_runs_owner_created_ix
    ON research_runs (user_id, created_at DESC, id DESC);
CREATE INDEX research_runs_job_created_ix
    ON research_runs (user_id, job_posting_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX research_runs_retry_successor_uk
    ON research_runs (user_id, retry_of_research_run_id)
    WHERE retry_of_research_run_id IS NOT NULL;

CREATE FUNCTION require_research_cover_job_consistency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM cover_letters cover
        WHERE cover.user_id = NEW.user_id
          AND cover.id = NEW.cover_letter_id
          AND cover.job_posting_id = NEW.job_posting_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'research_runs_cover_job_consistency_ck',
            MESSAGE = 'research cover letter must belong to the selected job';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER research_runs_cover_job_consistency_tr
BEFORE INSERT OR UPDATE ON research_runs
FOR EACH ROW EXECUTE FUNCTION require_research_cover_job_consistency();

CREATE TABLE research_topics (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    research_run_id uuid NOT NULL,
    topic varchar(30) NOT NULL,
    query_text varchar(500) NOT NULL,
    topic_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT research_topics_pk PRIMARY KEY (id),
    CONSTRAINT research_topics_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT research_topics_run_owner_id_uk
        UNIQUE (user_id, research_run_id, id),
    CONSTRAINT research_topics_run_owner_fk FOREIGN KEY (user_id, research_run_id)
        REFERENCES research_runs(user_id, id),
    CONSTRAINT research_topics_identity_uk
        UNIQUE (user_id, research_run_id, topic, query_text),
    CONSTRAINT research_topics_order_uk
        UNIQUE (user_id, research_run_id, topic_order),
    CONSTRAINT research_topics_topic_ck CHECK (
        topic IN ('COMPANY', 'INTERVIEW_PROCESS', 'ROLE_TECHNICAL')
    ),
    CONSTRAINT research_topics_query_ck CHECK (
        query_text = btrim(query_text) AND char_length(query_text) BETWEEN 1 AND 500
    ),
    CONSTRAINT research_topics_order_ck CHECK (topic_order BETWEEN 1 AND 4)
);

CREATE TABLE research_sources (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    research_run_id uuid NOT NULL,
    source_url varchar(2000) NOT NULL,
    title varchar(500) NULL,
    source_type varchar(30) NOT NULL,
    published_at timestamptz NULL,
    retrieved_at timestamptz NOT NULL,
    snippet varchar(2000) NULL,
    reliability_notice varchar(500) NOT NULL,
    provider_rank integer NOT NULL,
    content_hash char(64) NOT NULL,
    CONSTRAINT research_sources_pk PRIMARY KEY (id),
    CONSTRAINT research_sources_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT research_sources_run_owner_id_uk
        UNIQUE (user_id, research_run_id, id),
    CONSTRAINT research_sources_run_owner_fk FOREIGN KEY (user_id, research_run_id)
        REFERENCES research_runs(user_id, id),
    CONSTRAINT research_sources_run_url_uk
        UNIQUE (user_id, research_run_id, source_url),
    CONSTRAINT research_sources_type_ck CHECK (
        source_type IN (
            'OFFICIAL', 'TECH_BLOG', 'NEWS', 'INTERVIEW_REVIEW', 'COMMUNITY', 'OTHER'
        )
    ),
    CONSTRAINT research_sources_text_ck CHECK (
        source_url = btrim(source_url)
        AND char_length(source_url) BETWEEN 1 AND 2000
        AND (title IS NULL OR char_length(title) BETWEEN 1 AND 500)
        AND (snippet IS NULL OR char_length(snippet) BETWEEN 1 AND 2000)
        AND reliability_notice = btrim(reliability_notice)
        AND char_length(reliability_notice) BETWEEN 1 AND 500
    ),
    CONSTRAINT research_sources_provider_rank_ck CHECK (provider_rank >= 1),
    CONSTRAINT research_sources_hash_ck CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX research_sources_run_rank_ix
    ON research_sources (user_id, research_run_id, provider_rank, id);
CREATE INDEX research_sources_run_retrieved_ix
    ON research_sources (user_id, research_run_id, retrieved_at DESC, id DESC);

CREATE TABLE research_topic_source_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    research_topic_id uuid NOT NULL,
    research_source_id uuid NOT NULL,
    is_primary boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT research_topic_source_links_pk PRIMARY KEY (id),
    CONSTRAINT research_topic_source_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT research_topic_source_links_topic_owner_fk
        FOREIGN KEY (user_id, research_topic_id)
        REFERENCES research_topics(user_id, id),
    CONSTRAINT research_topic_source_links_source_owner_fk
        FOREIGN KEY (user_id, research_source_id)
        REFERENCES research_sources(user_id, id),
    CONSTRAINT research_topic_source_links_identity_uk
        UNIQUE (user_id, research_topic_id, research_source_id)
);

CREATE UNIQUE INDEX research_topic_source_links_primary_uk
    ON research_topic_source_links (user_id, research_source_id)
    WHERE is_primary;
CREATE INDEX research_topic_source_links_topic_ix
    ON research_topic_source_links (user_id, research_topic_id, created_at, id);

CREATE FUNCTION require_same_research_run_topic_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM research_topics topic
        JOIN research_sources source
          ON source.user_id = topic.user_id
         AND source.research_run_id = topic.research_run_id
        WHERE topic.user_id = NEW.user_id
          AND topic.id = NEW.research_topic_id
          AND source.id = NEW.research_source_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'research_topic_source_links_same_run_ck',
            MESSAGE = 'research topic and source must belong to the same owner-scoped run';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER research_topic_source_links_same_run_tr
BEFORE INSERT OR UPDATE ON research_topic_source_links
FOR EACH ROW EXECUTE FUNCTION require_same_research_run_topic_source();

CREATE TABLE interview_question_sets (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    cover_letter_id uuid NOT NULL,
    research_run_id uuid NOT NULL,
    title varchar(300) NOT NULL,
    generation_config jsonb NOT NULL,
    agent_run_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT interview_question_sets_pk PRIMARY KEY (id),
    CONSTRAINT interview_question_sets_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_question_sets_research_uk UNIQUE (user_id, research_run_id),
    CONSTRAINT interview_question_sets_agent_run_uk UNIQUE (user_id, agent_run_id),
    CONSTRAINT interview_question_sets_job_owner_fk
        FOREIGN KEY (user_id, job_posting_id) REFERENCES job_postings(user_id, id),
    CONSTRAINT interview_question_sets_cover_owner_fk
        FOREIGN KEY (user_id, cover_letter_id) REFERENCES cover_letters(user_id, id),
    CONSTRAINT interview_question_sets_research_owner_fk
        FOREIGN KEY (user_id, research_run_id) REFERENCES research_runs(user_id, id),
    CONSTRAINT interview_question_sets_agent_owner_fk
        FOREIGN KEY (user_id, agent_run_id) REFERENCES agent_runs(user_id, id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT interview_question_sets_title_ck CHECK (
        title = btrim(title) AND char_length(title) BETWEEN 1 AND 300
    ),
    CONSTRAINT interview_question_sets_config_ck CHECK (
        jsonb_typeof(generation_config) = 'object'
    )
);

CREATE INDEX interview_question_sets_owner_updated_ix
    ON interview_question_sets (user_id, updated_at DESC, id DESC);
CREATE INDEX interview_question_sets_job_created_ix
    ON interview_question_sets (user_id, job_posting_id, created_at DESC, id DESC);

CREATE FUNCTION require_question_set_research_consistency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM research_runs research
        WHERE research.user_id = NEW.user_id
          AND research.id = NEW.research_run_id
          AND research.job_posting_id = NEW.job_posting_id
          AND research.cover_letter_id = NEW.cover_letter_id
          AND research.agent_run_id = NEW.agent_run_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_question_sets_research_consistency_ck',
            MESSAGE = 'question set must match its research preparation lineage';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER interview_question_sets_research_consistency_tr
BEFORE INSERT OR UPDATE ON interview_question_sets
FOR EACH ROW EXECUTE FUNCTION require_question_set_research_consistency();

CREATE TABLE interview_questions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    question_set_id uuid NOT NULL,
    question_order integer NOT NULL,
    question_type varchar(40) NOT NULL,
    question_text varchar(2000) NOT NULL,
    intent varchar(2000) NULL,
    evaluation_points jsonb NOT NULL DEFAULT '[]'::jsonb,
    answer_guide varchar(10000) NULL,
    follow_up_questions jsonb NOT NULL DEFAULT '[]'::jsonb,
    source_based boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT interview_questions_pk PRIMARY KEY (id),
    CONSTRAINT interview_questions_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_questions_set_owner_id_uk
        UNIQUE (user_id, question_set_id, id),
    CONSTRAINT interview_questions_set_owner_fk
        FOREIGN KEY (user_id, question_set_id)
        REFERENCES interview_question_sets(user_id, id),
    CONSTRAINT interview_questions_order_uk
        UNIQUE (user_id, question_set_id, question_order),
    CONSTRAINT interview_questions_order_ck CHECK (question_order BETWEEN 1 AND 20),
    CONSTRAINT interview_questions_type_ck CHECK (
        question_type IN (
            'COVER_LETTER', 'RESUME', 'PORTFOLIO', 'TECHNICAL',
            'PROJECT_DEEP_DIVE', 'BEHAVIORAL', 'COMPANY_MOTIVATION', 'FOLLOW_UP'
        )
    ),
    CONSTRAINT interview_questions_text_ck CHECK (
        question_text = btrim(question_text)
        AND char_length(question_text) BETWEEN 1 AND 2000
        AND (intent IS NULL OR char_length(intent) BETWEEN 1 AND 2000)
        AND (answer_guide IS NULL OR char_length(answer_guide) BETWEEN 1 AND 10000)
    ),
    CONSTRAINT interview_questions_json_ck CHECK (
        jsonb_typeof(evaluation_points) = 'array'
        AND jsonb_array_length(evaluation_points) <= 20
        AND jsonb_typeof(follow_up_questions) = 'array'
        AND jsonb_array_length(follow_up_questions) <= 10
    )
);

CREATE INDEX interview_questions_set_order_ix
    ON interview_questions (user_id, question_set_id, question_order, id);

CREATE TABLE interview_question_evidence_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    interview_question_id uuid NOT NULL,
    profile_evidence_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT interview_question_evidence_links_pk PRIMARY KEY (id),
    CONSTRAINT interview_question_evidence_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_question_evidence_links_question_owner_fk
        FOREIGN KEY (user_id, interview_question_id)
        REFERENCES interview_questions(user_id, id),
    CONSTRAINT interview_question_evidence_links_evidence_owner_fk
        FOREIGN KEY (user_id, profile_evidence_id)
        REFERENCES profile_evidence(user_id, id),
    CONSTRAINT interview_question_evidence_links_identity_uk
        UNIQUE (user_id, interview_question_id, profile_evidence_id)
);

CREATE INDEX interview_question_evidence_links_question_ix
    ON interview_question_evidence_links (user_id, interview_question_id, created_at, id);

CREATE FUNCTION require_verified_interview_question_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM profile_evidence evidence
        WHERE evidence.user_id = NEW.user_id
          AND evidence.id = NEW.profile_evidence_id
          AND evidence.verification_status = 'VERIFIED'
          AND evidence.source_deleted_at IS NULL
          AND evidence.source_type <> 'EDUCATION'
          AND NOT (
              upper(regexp_replace(evidence.evidence_category, '[[:space:]_-]+', '', 'g'))
                  IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                      'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
              OR regexp_replace(evidence.evidence_category, '[[:space:]_-]+', '', 'g')
                  IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_question_evidence_links_verified_ck',
            MESSAGE = 'interview question provenance requires active non-education verified evidence';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER interview_question_evidence_links_verified_tr
BEFORE INSERT ON interview_question_evidence_links
FOR EACH ROW EXECUTE FUNCTION require_verified_interview_question_evidence();

CREATE TABLE interview_question_source_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    interview_question_id uuid NOT NULL,
    research_source_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT interview_question_source_links_pk PRIMARY KEY (id),
    CONSTRAINT interview_question_source_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_question_source_links_question_owner_fk
        FOREIGN KEY (user_id, interview_question_id)
        REFERENCES interview_questions(user_id, id),
    CONSTRAINT interview_question_source_links_source_owner_fk
        FOREIGN KEY (user_id, research_source_id)
        REFERENCES research_sources(user_id, id),
    CONSTRAINT interview_question_source_links_identity_uk
        UNIQUE (user_id, interview_question_id, research_source_id)
);

CREATE INDEX interview_question_source_links_question_ix
    ON interview_question_source_links (user_id, interview_question_id, created_at, id);

CREATE FUNCTION require_same_question_set_research_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM interview_questions question
        JOIN interview_question_sets question_set
          ON question_set.user_id = question.user_id
         AND question_set.id = question.question_set_id
        JOIN research_sources source
          ON source.user_id = question_set.user_id
         AND source.research_run_id = question_set.research_run_id
        WHERE question.user_id = NEW.user_id
          AND question.id = NEW.interview_question_id
          AND source.id = NEW.research_source_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_question_source_links_same_research_ck',
            MESSAGE = 'question source must belong to its question set research run';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER interview_question_source_links_same_research_tr
BEFORE INSERT OR UPDATE ON interview_question_source_links
FOR EACH ROW EXECUTE FUNCTION require_same_question_set_research_source();

CREATE FUNCTION require_interview_question_source_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.source_based IS DISTINCT FROM EXISTS (
        SELECT 1
        FROM interview_question_source_links link
        WHERE link.user_id = NEW.user_id
          AND link.interview_question_id = NEW.id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_questions_source_based_parity_ck',
            MESSAGE = 'sourceBased must match authoritative question source links';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER interview_questions_source_based_parity_tr
AFTER INSERT OR UPDATE OF source_based ON interview_questions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_interview_question_source_parity();

CREATE FUNCTION require_interview_source_link_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    question_user_id uuid;
    question_id uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        question_user_id := OLD.user_id;
        question_id := OLD.interview_question_id;
    ELSE
        question_user_id := NEW.user_id;
        question_id := NEW.interview_question_id;
    END IF;
    IF EXISTS (
        SELECT 1
        FROM interview_questions question
        WHERE question.user_id = question_user_id
          AND question.id = question_id
          AND question.source_based IS DISTINCT FROM EXISTS (
              SELECT 1
              FROM interview_question_source_links link
              WHERE link.user_id = question_user_id
                AND link.interview_question_id = question_id
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_questions_source_based_parity_ck',
            MESSAGE = 'sourceBased must match authoritative question source links';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER interview_question_source_links_parity_tr
AFTER INSERT OR UPDATE OR DELETE ON interview_question_source_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_interview_source_link_parity();

CREATE TABLE interview_answer_versions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    interview_question_id uuid NOT NULL,
    parent_version_id uuid NULL,
    version_no integer NOT NULL,
    content varchar(20000) NOT NULL,
    source_type varchar(30) NOT NULL,
    is_current boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT interview_answer_versions_pk PRIMARY KEY (id),
    CONSTRAINT interview_answer_versions_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_answer_versions_question_owner_id_uk
        UNIQUE (user_id, interview_question_id, id),
    CONSTRAINT interview_answer_versions_question_version_uk
        UNIQUE (user_id, interview_question_id, version_no),
    CONSTRAINT interview_answer_versions_question_owner_fk
        FOREIGN KEY (user_id, interview_question_id)
        REFERENCES interview_questions(user_id, id),
    CONSTRAINT interview_answer_versions_parent_owner_fk
        FOREIGN KEY (user_id, interview_question_id, parent_version_id)
        REFERENCES interview_answer_versions(user_id, interview_question_id, id),
    CONSTRAINT interview_answer_versions_version_ck CHECK (version_no >= 1),
    CONSTRAINT interview_answer_versions_content_ck CHECK (
        char_length(btrim(content)) >= 1 AND char_length(content) <= 20000
    ),
    CONSTRAINT interview_answer_versions_source_ck CHECK (source_type = 'USER_EDITED'),
    CONSTRAINT interview_answer_versions_parent_shape_ck CHECK (
        (version_no = 1 AND parent_version_id IS NULL)
        OR (version_no > 1 AND parent_version_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX interview_answer_versions_current_uk
    ON interview_answer_versions (user_id, interview_question_id)
    WHERE is_current;
CREATE INDEX interview_answer_versions_question_ix
    ON interview_answer_versions (user_id, interview_question_id, version_no DESC, id DESC);

CREATE FUNCTION require_interview_answer_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT NEW.is_current
       OR NEW.version_no <> (
            SELECT max(answer.version_no)
            FROM interview_answer_versions answer
            WHERE answer.user_id = NEW.user_id
              AND answer.interview_question_id = NEW.interview_question_id
       )
       OR (
            NEW.version_no = 1
            AND EXISTS (
                SELECT 1
                FROM interview_answer_versions answer
                WHERE answer.user_id = NEW.user_id
                  AND answer.interview_question_id = NEW.interview_question_id
                  AND answer.id <> NEW.id
            )
       )
       OR (
            NEW.version_no > 1
            AND NOT EXISTS (
                SELECT 1
                FROM interview_answer_versions parent
                WHERE parent.user_id = NEW.user_id
                  AND parent.interview_question_id = NEW.interview_question_id
                  AND parent.id = NEW.parent_version_id
                  AND parent.version_no = NEW.version_no - 1
                  AND NOT parent.is_current
            )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'interview_answer_versions_lineage_ck',
            MESSAGE = 'interview answer version must extend the immediately previous current version';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER interview_answer_versions_lineage_tr
AFTER INSERT ON interview_answer_versions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_interview_answer_lineage();

CREATE TABLE interview_answer_feedbacks (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    answer_version_id uuid NOT NULL,
    scores jsonb NOT NULL,
    strengths jsonb NOT NULL DEFAULT '[]'::jsonb,
    weaknesses jsonb NOT NULL DEFAULT '[]'::jsonb,
    suggestions jsonb NOT NULL DEFAULT '[]'::jsonb,
    revised_example varchar(10000) NULL,
    agent_run_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT interview_answer_feedbacks_pk PRIMARY KEY (id),
    CONSTRAINT interview_answer_feedbacks_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT interview_answer_feedbacks_run_uk UNIQUE (user_id, agent_run_id),
    CONSTRAINT interview_answer_feedbacks_answer_owner_fk
        FOREIGN KEY (user_id, answer_version_id)
        REFERENCES interview_answer_versions(user_id, id),
    CONSTRAINT interview_answer_feedbacks_agent_owner_fk
        FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT interview_answer_feedbacks_json_ck CHECK (
        jsonb_typeof(scores) = 'array'
        AND jsonb_array_length(scores) BETWEEN 1 AND 20
        AND jsonb_typeof(strengths) = 'array'
        AND jsonb_array_length(strengths) <= 20
        AND jsonb_typeof(weaknesses) = 'array'
        AND jsonb_array_length(weaknesses) <= 20
        AND jsonb_typeof(suggestions) = 'array'
        AND jsonb_array_length(suggestions) <= 20
    ),
    CONSTRAINT interview_answer_feedbacks_revised_ck CHECK (
        revised_example IS NULL OR char_length(revised_example) BETWEEN 1 AND 10000
    )
);

CREATE INDEX interview_answer_feedbacks_answer_ix
    ON interview_answer_feedbacks (user_id, answer_version_id, created_at DESC, id DESC);

CREATE FUNCTION enforce_interview_answer_immutability()
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
        CONSTRAINT = 'interview_answer_versions_immutable_ck',
        MESSAGE = 'interview answer versions are immutable except for current retirement';
END;
$$;

CREATE FUNCTION reject_interview_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = TG_TABLE_NAME || '_immutable_ck',
        MESSAGE = 'persisted interview provenance and feedback are immutable';
END;
$$;

CREATE TRIGGER interview_answer_versions_immutable_tr
BEFORE UPDATE OR DELETE ON interview_answer_versions
FOR EACH ROW EXECUTE FUNCTION enforce_interview_answer_immutability();
CREATE TRIGGER interview_answer_feedbacks_immutable_tr
BEFORE UPDATE OR DELETE ON interview_answer_feedbacks
FOR EACH ROW EXECUTE FUNCTION reject_interview_immutable_mutation();
CREATE TRIGGER interview_question_evidence_links_immutable_tr
BEFORE UPDATE OR DELETE ON interview_question_evidence_links
FOR EACH ROW EXECUTE FUNCTION reject_interview_immutable_mutation();
CREATE TRIGGER interview_question_source_links_immutable_tr
BEFORE UPDATE OR DELETE ON interview_question_source_links
FOR EACH ROW EXECUTE FUNCTION reject_interview_immutable_mutation();

ALTER TABLE agent_run_resource_links
    ADD COLUMN research_run_id uuid NULL,
    ADD COLUMN question_set_id uuid NULL,
    ADD COLUMN interview_answer_version_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_exactly_one_ck,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    DROP CONSTRAINT agent_run_resource_links_secondary_kind_ck,
    ADD CONSTRAINT agent_run_resource_links_research_owner_fk
        FOREIGN KEY (user_id, research_run_id) REFERENCES research_runs(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_question_set_owner_fk
        FOREIGN KEY (user_id, question_set_id)
        REFERENCES interview_question_sets(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_interview_answer_owner_fk
        FOREIGN KEY (user_id, interview_answer_version_id)
        REFERENCES interview_answer_versions(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_exactly_one_ck CHECK (
        num_nonnulls(
            document_id,
            job_posting_id,
            job_analysis_id,
            cover_letter_id,
            cover_letter_answer_version_id,
            research_run_id,
            question_set_id,
            interview_answer_version_id
        ) = 1
    ),
    ADD CONSTRAINT agent_run_resource_links_kind_ck CHECK (
        (resource_kind = 'DOCUMENT' AND document_id IS NOT NULL)
        OR (resource_kind = 'JOB' AND job_posting_id IS NOT NULL)
        OR (resource_kind = 'JOB_ANALYSIS' AND job_analysis_id IS NOT NULL)
        OR (resource_kind = 'COVER_LETTER' AND cover_letter_id IS NOT NULL)
        OR (resource_kind = 'COVER_LETTER_ANSWER_VERSION'
            AND cover_letter_answer_version_id IS NOT NULL)
        OR (resource_kind = 'RESEARCH_RUN' AND research_run_id IS NOT NULL)
        OR (resource_kind = 'QUESTION_SET' AND question_set_id IS NOT NULL)
        OR (resource_kind = 'INTERVIEW_ANSWER_VERSION'
            AND interview_answer_version_id IS NOT NULL)
    ),
    ADD CONSTRAINT agent_run_resource_links_secondary_kind_ck CHECK (
        resource_kind NOT IN (
            'JOB_ANALYSIS', 'COVER_LETTER_ANSWER_VERSION', 'RESEARCH_RUN'
        )
        OR NOT primary_resource
    );

CREATE INDEX agent_run_resource_links_research_ix
    ON agent_run_resource_links (user_id, research_run_id, created_at DESC)
    WHERE research_run_id IS NOT NULL;
CREATE INDEX agent_run_resource_links_question_set_ix
    ON agent_run_resource_links (user_id, question_set_id, created_at DESC)
    WHERE question_set_id IS NOT NULL;
CREATE INDEX agent_run_resource_links_interview_answer_ix
    ON agent_run_resource_links (user_id, interview_answer_version_id, created_at DESC)
    WHERE interview_answer_version_id IS NOT NULL;

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
              OR (link.resource_kind = 'QUESTION_SET'
                  AND (run.resource_type IS DISTINCT FROM 'QUESTION_SET'
                      OR run.resource_id IS DISTINCT FROM link.question_set_id))
              OR (link.resource_kind = 'INTERVIEW_ANSWER_VERSION'
                  AND (run.resource_type IS DISTINCT FROM 'INTERVIEW_ANSWER_VERSION'
                      OR run.resource_id IS DISTINCT FROM link.interview_answer_version_id))
          )
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type IN (
            'DOCUMENT', 'JOB', 'COVER_LETTER', 'QUESTION_SET', 'INTERVIEW_ANSWER_VERSION'
        )
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
                    OR (run.resource_type = 'QUESTION_SET'
                        AND link.resource_kind = 'QUESTION_SET'
                        AND link.question_set_id = run.resource_id)
                    OR (run.resource_type = 'INTERVIEW_ANSWER_VERSION'
                        AND link.resource_kind = 'INTERVIEW_ANSWER_VERSION'
                        AND link.interview_answer_version_id = run.resource_id)
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
