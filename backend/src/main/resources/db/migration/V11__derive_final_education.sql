-- Final education is selected by the server from an explicit education level.
-- Backfill legacy rows from their free-text degree and school name before making
-- the new classification mandatory.
ALTER TABLE educations
    ADD COLUMN education_level varchar(30) NULL;

UPDATE educations
SET education_level = CASE
    WHEN lower(coalesce(degree, '') || ' ' || school_name)
        ~ '(박사|doctor|ph[.]?[[:space:]]?d)' THEN 'DOCTORATE'
    WHEN lower(coalesce(degree, '') || ' ' || school_name)
        ~ '(석사|master|대학원|graduate[[:space:]]school)' THEN 'MASTER'
    WHEN lower(coalesce(degree, '') || ' ' || school_name)
        ~ '(전문학사|associate|전문대|junior[[:space:]]college)' THEN 'ASSOCIATE'
    WHEN lower(coalesce(degree, '') || ' ' || school_name)
        ~ '(고등학교|고교|high[[:space:]-]?school)' THEN 'HIGH_SCHOOL'
    WHEN lower(coalesce(degree, '') || ' ' || school_name)
        ~ '(학사|bachelor|대학교|대학|university|college)' THEN 'BACHELOR'
    ELSE 'OTHER'
END;

ALTER TABLE educations
    ALTER COLUMN education_level SET NOT NULL,
    ADD CONSTRAINT educations_education_level_ck CHECK (
        education_level IN (
            'OTHER', 'HIGH_SCHOOL', 'ASSOCIATE', 'BACHELOR', 'MASTER', 'DOCTORATE'
        )
    );

-- Recompute every user's final education from the hierarchy. Higher education
-- levels win first; status and dates provide deterministic tie-breakers.
DROP INDEX educations_one_active_primary_ix;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY user_id
               ORDER BY
                   CASE education_level
                       WHEN 'DOCTORATE' THEN 50
                       WHEN 'MASTER' THEN 40
                       WHEN 'BACHELOR' THEN 30
                       WHEN 'ASSOCIATE' THEN 20
                       WHEN 'HIGH_SCHOOL' THEN 10
                       ELSE 0
                   END DESC,
                   CASE education_status
                       WHEN 'GRADUATED' THEN 40
                       WHEN 'EXPECTED_GRADUATION' THEN 30
                       WHEN 'ENROLLED' THEN 20
                       WHEN 'LEAVE_OF_ABSENCE' THEN 10
                       ELSE 0
                   END DESC,
                   graduation_date DESC NULLS LAST,
                   admission_date DESC NULLS LAST,
                   created_at DESC,
                   id DESC
           ) AS final_rank
    FROM educations
    WHERE deleted_at IS NULL
)
UPDATE educations education
SET is_primary = (ranked.final_rank = 1),
    version = education.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM ranked
WHERE education.id = ranked.id
  AND education.is_primary IS DISTINCT FROM (ranked.final_rank = 1);

UPDATE educations
SET is_primary = false,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NOT NULL
  AND is_primary;

CREATE UNIQUE INDEX educations_one_active_primary_ix
    ON educations (user_id)
    WHERE is_primary AND deleted_at IS NULL;
