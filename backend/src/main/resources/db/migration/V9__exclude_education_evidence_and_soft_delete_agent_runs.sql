-- Education remains a structured profile source, but it is no longer mirrored into the
-- extracurricular evidence workspace. Preserve legacy evidence IDs as privacy-safe
-- tombstones so immutable analysis and cover-letter provenance links remain valid.
DROP TRIGGER educations_direct_evidence_ct ON educations;
ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_shape_ck;
DROP INDEX profile_evidence_one_direct_source_ix;
ALTER TABLE profile_evidence DISABLE TRIGGER profile_evidence_structured_source_ct;

UPDATE profile_evidence
SET source_entity_id = NULL,
    document_id = NULL,
    title = '[대외활동에서 제외된 학력 정보]',
    content = '[학력 정보는 구조화 프로필에서만 관리됩니다.]',
    metadata = '{}'::jsonb,
    confidence = NULL,
    verification_status = 'SOURCE_DELETED',
    verified_at = NULL,
    source_deleted_at = COALESCE(source_deleted_at, CURRENT_TIMESTAMP),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_type = 'EDUCATION';

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_source_shape_ck CHECK (
        (source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER')
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
    WHERE source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER');

CREATE OR REPLACE FUNCTION assert_structured_profile_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
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
            MESSAGE = 'active non-education structured profile source must have one owner-matched direct evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM profile_evidence evidence
        WHERE evidence.source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER')
          AND NOT (
              (evidence.source_type = 'CERTIFICATION' AND EXISTS (
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

ALTER TABLE profile_evidence ENABLE TRIGGER profile_evidence_structured_source_ct;

-- Agent Run rows own retry lineage, budget/usage audit, typed resource links, and durable
-- outputs. User deletion therefore hides terminal history without physically removing it.
ALTER TABLE agent_runs
    ADD COLUMN deleted_at timestamptz NULL,
    ADD CONSTRAINT agent_runs_deleted_time_ck CHECK (
        deleted_at IS NULL
        OR (
            status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED')
            AND completed_at IS NOT NULL
            AND deleted_at >= queued_at
        )
    );

CREATE INDEX agent_runs_owner_visible_queued_ix
    ON agent_runs (user_id, queued_at DESC, id DESC)
    WHERE deleted_at IS NULL;
