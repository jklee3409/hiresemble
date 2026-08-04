ALTER TABLE job_analyses
    ALTER COLUMN fit_score DROP NOT NULL,
    ADD COLUMN analysis_coverage numeric(5,2);

ALTER TABLE job_analyses
    ADD CONSTRAINT job_analyses_analysis_coverage_ck
        CHECK (analysis_coverage IS NULL OR analysis_coverage BETWEEN 0.00 AND 100.00);

COMMENT ON COLUMN job_analyses.analysis_coverage IS
    'job-fit-rubric-v2에서 UNKNOWN을 제외하고 실제 판정한 가중치 비율; 이전 rubric 결과는 NULL';
