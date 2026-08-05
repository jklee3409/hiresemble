CREATE FUNCTION classify_job_posting_period()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.posting_year := EXTRACT(YEAR FROM NEW.created_at AT TIME ZONE 'Asia/Seoul')::integer;
    NEW.posting_half := CASE
        WHEN EXTRACT(MONTH FROM NEW.created_at AT TIME ZONE 'Asia/Seoul') <= 6
            THEN 'FIRST_HALF'
        ELSE 'SECOND_HALF'
    END;
    RETURN NEW;
END;
$$;

CREATE TRIGGER job_postings_classify_period_trg
BEFORE INSERT OR UPDATE OF created_at ON job_postings
FOR EACH ROW EXECUTE FUNCTION classify_job_posting_period();

ALTER TABLE job_postings
    ALTER COLUMN posting_year SET NOT NULL,
    ALTER COLUMN posting_half SET NOT NULL,
    ADD CONSTRAINT job_postings_posting_year_ck
        CHECK (posting_year BETWEEN 2000 AND 9999),
    ADD CONSTRAINT job_postings_posting_half_ck
        CHECK (posting_half IN ('FIRST_HALF','SECOND_HALF'));

CREATE INDEX job_postings_owner_period_ix
    ON job_postings (user_id, posting_year DESC, posting_half DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN job_postings.posting_year IS
    '공고 등록 시작 시각을 Asia/Seoul로 변환한 연도';
COMMENT ON COLUMN job_postings.posting_half IS
    '공고 등록 시작 시각 기준 FIRST_HALF(1~6월) 또는 SECOND_HALF(7~12월)';
