CREATE TABLE activities (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    activity_type varchar(40) NOT NULL,
    organizer varchar(200) NOT NULL,
    started_at date NULL,
    ended_at date NULL,
    ongoing boolean NOT NULL,
    role varchar(200) NULL,
    description varchar(10000) NOT NULL,
    achievements varchar(10000) NULL,
    related_url varchar(1000) NULL,
    use_as_material boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT activities_pk PRIMARY KEY (id),
    CONSTRAINT activities_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT activities_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT activities_title_ck CHECK (
        title = btrim(title) AND char_length(title) BETWEEN 1 AND 200 AND title !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT activities_type_ck CHECK (activity_type IN (
        'CLUB', 'VOLUNTEERING', 'CONTEST', 'SUPPORTERS', 'PRESS_CORPS',
        'STUDENT_COUNCIL', 'EDUCATION_PROGRAM', 'INTERNATIONAL', 'OTHER'
    )),
    CONSTRAINT activities_organizer_ck CHECK (
        organizer = btrim(organizer) AND char_length(organizer) BETWEEN 1 AND 200
        AND organizer !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT activities_dates_ck CHECK (
        started_at IS NULL OR ended_at IS NULL OR started_at <= ended_at
    ),
    CONSTRAINT activities_ongoing_ck CHECK (NOT ongoing OR ended_at IS NULL),
    CONSTRAINT activities_description_ck CHECK (char_length(description) BETWEEN 1 AND 10000),
    CONSTRAINT activities_version_ck CHECK (version >= 0),
    CONSTRAINT activities_time_ck CHECK (
        updated_at >= created_at AND (deleted_at IS NULL OR deleted_at >= created_at)
    )
);

CREATE INDEX activities_owner_started_ix
    ON activities (user_id, started_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_type_ck;
ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_shape_ck;
DROP INDEX profile_evidence_one_direct_source_ix;

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_source_type_ck CHECK (
        source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER',
                        'ACTIVITY', 'DOCUMENT_CHUNK', 'MANUAL')
    ),
    ADD CONSTRAINT profile_evidence_source_shape_ck CHECK (
        (source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER', 'ACTIVITY')
            AND source_entity_id IS NOT NULL
            AND document_id IS NULL
            AND verification_status <> 'SOURCE_DELETED')
        OR (source_type = 'EDUCATION'
            AND source_entity_id IS NULL
            AND document_id IS NULL
            AND verification_status = 'SOURCE_DELETED')
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status <> 'SOURCE_DELETED'
            AND source_entity_id IS NOT NULL
            AND document_id IS NOT NULL)
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status = 'SOURCE_DELETED'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
        OR (source_type = 'MANUAL'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
    );

CREATE UNIQUE INDEX profile_evidence_one_direct_source_ix
    ON profile_evidence (user_id, source_type, source_entity_id)
    WHERE source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER', 'ACTIVITY');

CREATE OR REPLACE FUNCTION assert_structured_profile_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM certifications source WHERE source.deleted_at IS NULL AND NOT EXISTS (
            SELECT 1 FROM profile_evidence evidence WHERE evidence.user_id = source.user_id
              AND evidence.source_type = 'CERTIFICATION' AND evidence.source_entity_id = source.id)
        UNION ALL
        SELECT 1 FROM language_scores source WHERE source.deleted_at IS NULL AND NOT EXISTS (
            SELECT 1 FROM profile_evidence evidence WHERE evidence.user_id = source.user_id
              AND evidence.source_type = 'LANGUAGE_SCORE' AND evidence.source_entity_id = source.id)
        UNION ALL
        SELECT 1 FROM awards source WHERE source.deleted_at IS NULL AND NOT EXISTS (
            SELECT 1 FROM profile_evidence evidence WHERE evidence.user_id = source.user_id
              AND evidence.source_type = 'AWARD' AND evidence.source_entity_id = source.id)
        UNION ALL
        SELECT 1 FROM careers source WHERE source.deleted_at IS NULL AND NOT EXISTS (
            SELECT 1 FROM profile_evidence evidence WHERE evidence.user_id = source.user_id
              AND evidence.source_type = 'CAREER' AND evidence.source_entity_id = source.id)
        UNION ALL
        SELECT 1 FROM activities source WHERE source.deleted_at IS NULL AND NOT EXISTS (
            SELECT 1 FROM profile_evidence evidence WHERE evidence.user_id = source.user_id
              AND evidence.source_type = 'ACTIVITY' AND evidence.source_entity_id = source.id)
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            CONSTRAINT = 'structured_profile_direct_evidence_ck',
            MESSAGE = 'active structured profile source must have one owner-matched direct evidence';
    END IF;

    IF EXISTS (
        SELECT 1 FROM profile_evidence evidence
        WHERE evidence.source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER', 'ACTIVITY')
          AND NOT (
              (evidence.source_type = 'CERTIFICATION' AND EXISTS (
                  SELECT 1 FROM certifications source WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id AND source.deleted_at IS NULL))
              OR (evidence.source_type = 'LANGUAGE_SCORE' AND EXISTS (
                  SELECT 1 FROM language_scores source WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id AND source.deleted_at IS NULL))
              OR (evidence.source_type = 'AWARD' AND EXISTS (
                  SELECT 1 FROM awards source WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id AND source.deleted_at IS NULL))
              OR (evidence.source_type = 'CAREER' AND EXISTS (
                  SELECT 1 FROM careers source WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id AND source.deleted_at IS NULL))
              OR (evidence.source_type = 'ACTIVITY' AND EXISTS (
                  SELECT 1 FROM activities source WHERE source.id = evidence.source_entity_id
                    AND source.user_id = evidence.user_id AND source.deleted_at IS NULL))
          )
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            CONSTRAINT = 'structured_profile_direct_evidence_ck',
            MESSAGE = 'direct evidence must reference an active source owned by the same user';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER activities_direct_evidence_ct
AFTER INSERT OR UPDATE OR DELETE ON activities
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_structured_profile_evidence();
