ALTER TABLE job_postings
    ADD COLUMN posting_year integer,
    ADD COLUMN posting_half varchar(20);

UPDATE job_postings
SET posting_year = EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Seoul')::integer,
    posting_half = CASE
        WHEN EXTRACT(MONTH FROM created_at AT TIME ZONE 'Asia/Seoul') <= 6
            THEN 'FIRST_HALF'
        ELSE 'SECOND_HALF'
    END;
