CREATE FUNCTION valid_canonical_string_array(value jsonb, max_count integer, max_length integer)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    element jsonb;
    text_value text;
    canonical_values text[] := ARRAY[]::text[];
BEGIN
    IF jsonb_typeof(value) <> 'array' OR jsonb_array_length(value) > max_count THEN
        RETURN false;
    END IF;

    FOR element IN SELECT item FROM jsonb_array_elements(value) AS items(item)
    LOOP
        IF jsonb_typeof(element) <> 'string' THEN
            RETURN false;
        END IF;
        text_value := element #>> '{}';
        IF text_value <> btrim(text_value)
                OR char_length(text_value) NOT BETWEEN 1 AND max_length
                OR text_value ~ '[[:cntrl:]]'
                OR lower(text_value) = ANY(canonical_values) THEN
            RETURN false;
        END IF;
        canonical_values := array_append(canonical_values, lower(text_value));
    END LOOP;
    RETURN true;
END;
$$;

ALTER TABLE user_profiles
    DROP CONSTRAINT user_profiles_desired_roles_array_ck,
    DROP CONSTRAINT user_profiles_desired_industries_array_ck,
    DROP CONSTRAINT user_profiles_desired_locations_array_ck,
    ADD CONSTRAINT user_profiles_user_id_id_uk UNIQUE (user_id, id),
    ADD CONSTRAINT user_profiles_legal_name_ck CHECK (
        legal_name IS NULL
        OR (
            legal_name = btrim(legal_name)
            AND char_length(legal_name) BETWEEN 1 AND 100
            AND legal_name !~ '[[:cntrl:]/\\]'
        )
    ),
    ADD CONSTRAINT user_profiles_desired_roles_ck CHECK (
        valid_canonical_string_array(desired_roles, 10, 100)
    ),
    ADD CONSTRAINT user_profiles_desired_industries_ck CHECK (
        valid_canonical_string_array(desired_industries, 10, 100)
    ),
    ADD CONSTRAINT user_profiles_desired_locations_ck CHECK (
        valid_canonical_string_array(desired_locations, 10, 100)
    );

CREATE TABLE educations (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    school_name varchar(200) NOT NULL,
    major varchar(200) NULL,
    degree varchar(100) NULL,
    education_status varchar(30) NOT NULL,
    admission_date date NULL,
    graduation_date date NULL,
    gpa numeric(5,2) NULL,
    gpa_scale numeric(5,2) NULL,
    is_primary boolean NOT NULL,
    description varchar(5000) NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT educations_pk PRIMARY KEY (id),
    CONSTRAINT educations_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT educations_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT educations_school_name_ck CHECK (
        school_name = btrim(school_name)
        AND char_length(school_name) BETWEEN 1 AND 200
        AND school_name !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT educations_education_status_ck CHECK (
        education_status IN ('ENROLLED', 'LEAVE_OF_ABSENCE', 'EXPECTED_GRADUATION', 'GRADUATED', 'WITHDRAWN')
    ),
    CONSTRAINT educations_dates_ck CHECK (
        admission_date IS NULL OR graduation_date IS NULL OR admission_date <= graduation_date
    ),
    CONSTRAINT educations_gpa_pair_ck CHECK ((gpa IS NULL) = (gpa_scale IS NULL)),
    CONSTRAINT educations_gpa_ck CHECK (
        gpa IS NULL OR (gpa BETWEEN 0 AND 10 AND gpa_scale BETWEEN 0.01 AND 10 AND gpa <= gpa_scale)
    )
);

CREATE UNIQUE INDEX educations_one_active_primary_ix
    ON educations (user_id)
    WHERE is_primary AND deleted_at IS NULL;
CREATE INDEX educations_user_created_ix ON educations (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE certifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name varchar(200) NOT NULL,
    issuer varchar(200) NULL,
    credential_number varchar(200) NULL,
    acquired_date date NULL,
    expires_at date NULL,
    description varchar(5000) NULL,
    evidence_document_id uuid NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT certifications_pk PRIMARY KEY (id),
    CONSTRAINT certifications_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT certifications_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT certifications_name_ck CHECK (
        name = btrim(name) AND char_length(name) BETWEEN 1 AND 200 AND name !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT certifications_dates_ck CHECK (
        acquired_date IS NULL OR expires_at IS NULL OR acquired_date <= expires_at
    )
);
CREATE INDEX certifications_user_created_ix ON certifications (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE language_scores (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    test_name varchar(100) NOT NULL,
    score varchar(100) NOT NULL,
    grade varchar(100) NULL,
    tested_at date NULL,
    expires_at date NULL,
    evidence_document_id uuid NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT language_scores_pk PRIMARY KEY (id),
    CONSTRAINT language_scores_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT language_scores_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT language_scores_test_name_ck CHECK (
        test_name = btrim(test_name)
        AND char_length(test_name) BETWEEN 1 AND 100
        AND test_name !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT language_scores_score_ck CHECK (
        score = btrim(score) AND char_length(score) BETWEEN 1 AND 100 AND score !~ '[[:cntrl:]]'
    ),
    CONSTRAINT language_scores_dates_ck CHECK (
        tested_at IS NULL OR expires_at IS NULL OR tested_at <= expires_at
    )
);
CREATE INDEX language_scores_user_created_ix ON language_scores (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE awards (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name varchar(200) NOT NULL,
    organizer varchar(200) NULL,
    awarded_at date NULL,
    description varchar(5000) NULL,
    evidence_document_id uuid NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT awards_pk PRIMARY KEY (id),
    CONSTRAINT awards_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT awards_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT awards_name_ck CHECK (
        name = btrim(name) AND char_length(name) BETWEEN 1 AND 200 AND name !~ '[[:cntrl:]/\\]'
    )
);
CREATE INDEX awards_user_created_ix ON awards (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE careers (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    organization varchar(200) NOT NULL,
    position varchar(200) NULL,
    employment_type varchar(50) NULL,
    started_at date NULL,
    ended_at date NULL,
    is_current boolean NOT NULL,
    responsibilities varchar(20000) NULL,
    achievements varchar(20000) NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT careers_pk PRIMARY KEY (id),
    CONSTRAINT careers_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT careers_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT careers_organization_ck CHECK (
        organization = btrim(organization)
        AND char_length(organization) BETWEEN 1 AND 200
        AND organization !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT careers_dates_ck CHECK (
        started_at IS NULL OR ended_at IS NULL OR started_at <= ended_at
    ),
    CONSTRAINT careers_current_ck CHECK (NOT is_current OR ended_at IS NULL)
);
CREATE INDEX careers_user_created_ix ON careers (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE profile_evidence (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    source_type varchar(50) NOT NULL,
    source_entity_id uuid NULL,
    document_id uuid NULL,
    evidence_category varchar(80) NOT NULL,
    title varchar(250) NOT NULL,
    content varchar(20000) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence numeric(4,3) NULL,
    verification_status varchar(30) NOT NULL,
    verified_at timestamptz NULL,
    source_deleted_at timestamptz NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT profile_evidence_pk PRIMARY KEY (id),
    CONSTRAINT profile_evidence_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT profile_evidence_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT profile_evidence_source_type_ck CHECK (
        source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER', 'DOCUMENT_CHUNK', 'MANUAL')
    ),
    CONSTRAINT profile_evidence_source_shape_ck CHECK (
        (source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER')
            AND source_entity_id IS NOT NULL AND document_id IS NULL)
        OR (source_type = 'DOCUMENT_CHUNK' AND source_entity_id IS NOT NULL AND document_id IS NOT NULL)
        OR (source_type = 'MANUAL' AND source_entity_id IS NULL AND document_id IS NULL)
    ),
    CONSTRAINT profile_evidence_category_ck CHECK (
        evidence_category = btrim(evidence_category)
        AND char_length(evidence_category) BETWEEN 1 AND 80
    ),
    CONSTRAINT profile_evidence_title_ck CHECK (
        title = btrim(title)
        AND char_length(title) BETWEEN 1 AND 250
        AND title !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT profile_evidence_content_ck CHECK (char_length(content) BETWEEN 1 AND 20000),
    CONSTRAINT profile_evidence_metadata_ck CHECK (
        jsonb_typeof(metadata) = 'object' AND octet_length(metadata::text) <= 16384
    ),
    CONSTRAINT profile_evidence_confidence_ck CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT profile_evidence_verification_status_ck CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SOURCE_DELETED')
    ),
    CONSTRAINT profile_evidence_verification_time_ck CHECK (
        (verification_status = 'VERIFIED' AND verified_at IS NOT NULL)
        OR (verification_status <> 'VERIFIED' AND verified_at IS NULL)
    ),
    CONSTRAINT profile_evidence_source_deleted_ck CHECK (
        (verification_status = 'SOURCE_DELETED' AND source_deleted_at IS NOT NULL)
        OR (verification_status <> 'SOURCE_DELETED' AND source_deleted_at IS NULL)
    )
);

CREATE UNIQUE INDEX profile_evidence_one_direct_source_ix
    ON profile_evidence (user_id, source_type, source_entity_id)
    WHERE source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER');
CREATE INDEX profile_evidence_user_updated_ix ON profile_evidence (user_id, updated_at DESC);

CREATE FUNCTION assert_structured_profile_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM educations source
        WHERE source.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM profile_evidence evidence
              WHERE evidence.user_id = source.user_id
                AND evidence.source_type = 'EDUCATION'
                AND evidence.source_entity_id = source.id
          )
        UNION ALL
        SELECT 1 FROM certifications source
        WHERE source.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM profile_evidence evidence
              WHERE evidence.user_id = source.user_id
                AND evidence.source_type = 'CERTIFICATION'
                AND evidence.source_entity_id = source.id
          )
        UNION ALL
        SELECT 1 FROM language_scores source
        WHERE source.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM profile_evidence evidence
              WHERE evidence.user_id = source.user_id
                AND evidence.source_type = 'LANGUAGE_SCORE'
                AND evidence.source_entity_id = source.id
          )
        UNION ALL
        SELECT 1 FROM awards source
        WHERE source.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM profile_evidence evidence
              WHERE evidence.user_id = source.user_id
                AND evidence.source_type = 'AWARD'
                AND evidence.source_entity_id = source.id
          )
        UNION ALL
        SELECT 1 FROM careers source
        WHERE source.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM profile_evidence evidence
              WHERE evidence.user_id = source.user_id
                AND evidence.source_type = 'CAREER'
                AND evidence.source_entity_id = source.id
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'structured_profile_direct_evidence_ck',
            MESSAGE = 'active structured profile source must have one owner-matched direct evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM profile_evidence evidence
        WHERE evidence.source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER')
          AND NOT (
              (evidence.source_type = 'EDUCATION' AND EXISTS (
                  SELECT 1 FROM educations source
                  WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id
                    AND source.deleted_at IS NULL
              ))
              OR (evidence.source_type = 'CERTIFICATION' AND EXISTS (
                  SELECT 1 FROM certifications source
                  WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id
                    AND source.deleted_at IS NULL
              ))
              OR (evidence.source_type = 'LANGUAGE_SCORE' AND EXISTS (
                  SELECT 1 FROM language_scores source
                  WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id
                    AND source.deleted_at IS NULL
              ))
              OR (evidence.source_type = 'AWARD' AND EXISTS (
                  SELECT 1 FROM awards source
                  WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id
                    AND source.deleted_at IS NULL
              ))
              OR (evidence.source_type = 'CAREER' AND EXISTS (
                  SELECT 1 FROM careers source
                  WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id
                    AND source.deleted_at IS NULL
              ))
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'structured_profile_direct_evidence_ck',
            MESSAGE = 'direct evidence must reference an active source owned by the same user';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER educations_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON educations
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();

CREATE CONSTRAINT TRIGGER certifications_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON certifications
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();

CREATE CONSTRAINT TRIGGER language_scores_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON language_scores
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();

CREATE CONSTRAINT TRIGGER awards_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON awards
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();

CREATE CONSTRAINT TRIGGER careers_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON careers
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();

CREATE CONSTRAINT TRIGGER profile_evidence_structured_source_ct
AFTER INSERT OR UPDATE OR DELETE ON profile_evidence
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();
