CREATE TABLE profile_eligibility_declarations (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    work_available_date date,
    military_status varchar(30) NOT NULL DEFAULT 'UNSPECIFIED',
    overseas_travel_eligibility varchar(30) NOT NULL DEFAULT 'UNSPECIFIED',
    employment_disqualification_status varchar(30) NOT NULL DEFAULT 'UNSPECIFIED',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT profile_eligibility_declarations_pk PRIMARY KEY (id),
    CONSTRAINT profile_eligibility_declarations_user_uk UNIQUE (user_id),
    CONSTRAINT profile_eligibility_declarations_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT profile_eligibility_declarations_version_ck CHECK (version >= 0),
    CONSTRAINT profile_eligibility_declarations_military_ck CHECK (
        military_status IN ('COMPLETED','EXEMPT','NOT_APPLICABLE','NOT_COMPLETED','UNSPECIFIED')
    ),
    CONSTRAINT profile_eligibility_declarations_travel_ck CHECK (
        overseas_travel_eligibility IN ('ELIGIBLE','RESTRICTED','UNSPECIFIED')
    ),
    CONSTRAINT profile_eligibility_declarations_disqualification_ck CHECK (
        employment_disqualification_status IN ('NONE_DECLARED','HAS_RESTRICTION','UNSPECIFIED')
    )
);

INSERT INTO profile_eligibility_declarations (
    id,user_id,work_available_date,military_status,overseas_travel_eligibility,
    employment_disqualification_status,version,created_at,updated_at
)
SELECT gen_random_uuid(),id,NULL,'UNSPECIFIED','UNSPECIFIED','UNSPECIFIED',0,created_at,created_at
FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE job_analysis_structured_fact_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_analysis_id uuid NOT NULL,
    score_criterion_id uuid,
    source_entity_id uuid NOT NULL,
    source_entity_version bigint NOT NULL,
    fact_type varchar(60) NOT NULL,
    fact_hash char(64) NOT NULL,
    usage_type varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT job_analysis_structured_fact_links_pk PRIMARY KEY (id),
    CONSTRAINT job_analysis_structured_fact_links_owner_fk
        FOREIGN KEY (user_id,job_analysis_id)
        REFERENCES job_analyses (user_id,id),
    CONSTRAINT job_analysis_structured_fact_links_criterion_fk
        FOREIGN KEY (user_id,job_analysis_id,score_criterion_id)
        REFERENCES job_analysis_score_criteria (user_id,job_analysis_id,id),
    CONSTRAINT job_analysis_structured_fact_links_version_ck CHECK (source_entity_version >= 0),
    CONSTRAINT job_analysis_structured_fact_links_hash_ck CHECK (fact_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT job_analysis_structured_fact_links_type_ck CHECK (fact_type IN (
        'PRIMARY_EDUCATION','EXPECTED_GRADUATION_DATE','WORK_AVAILABLE_DATE','MILITARY_STATUS',
        'OVERSEAS_TRAVEL_ELIGIBILITY','EMPLOYMENT_DISQUALIFICATION_STATUS'
    )),
    CONSTRAINT job_analysis_structured_fact_links_usage_ck CHECK (
        usage_type IN ('CRITERION_MATCH','ELIGIBILITY')
    ),
    CONSTRAINT job_analysis_structured_fact_links_shape_ck CHECK (
        (usage_type='CRITERION_MATCH' AND score_criterion_id IS NOT NULL)
        OR (usage_type='ELIGIBILITY' AND score_criterion_id IS NULL)
    )
);

CREATE UNIQUE INDEX job_analysis_structured_fact_links_identity_uk
    ON job_analysis_structured_fact_links (
        user_id,job_analysis_id,COALESCE(score_criterion_id,'00000000-0000-0000-0000-000000000000'::uuid),
        source_entity_id,source_entity_version,fact_type,usage_type
    );

CREATE INDEX job_analysis_structured_fact_links_analysis_ix
    ON job_analysis_structured_fact_links (user_id,job_analysis_id,created_at,id);

CREATE TRIGGER job_analysis_structured_fact_links_open_tr
BEFORE INSERT ON job_analysis_structured_fact_links
FOR EACH ROW EXECUTE FUNCTION require_open_job_analysis();

CREATE TRIGGER job_analysis_structured_fact_links_immutable_tr
BEFORE UPDATE OR DELETE ON job_analysis_structured_fact_links
FOR EACH ROW EXECUTE FUNCTION reject_job_analysis_mutation();
