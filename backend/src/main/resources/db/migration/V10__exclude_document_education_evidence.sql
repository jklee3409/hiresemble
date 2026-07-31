-- Document extraction must not recreate education rows in the extracurricular
-- evidence workspace. Preserve existing IDs as sanitized tombstones because
-- immutable job-analysis and cover-letter provenance can reference them.
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
WHERE verification_status <> 'SOURCE_DELETED'
  AND (
      upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
          IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
              'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
      OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
          IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
  );

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_no_active_education_category_ck CHECK (
        verification_status = 'SOURCE_DELETED'
        OR NOT (
            upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
                IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                    'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
            OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
                IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
        )
    );
