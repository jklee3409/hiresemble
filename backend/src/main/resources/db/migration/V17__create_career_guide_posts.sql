CREATE TABLE career_guide_posts (
    id uuid NOT NULL,
    slug varchar(120) NOT NULL,
    status varchar(20) NOT NULL,
    display_order integer NOT NULL,
    category varchar(60) NOT NULL,
    title varchar(200) NOT NULL,
    summary varchar(500) NOT NULL,
    body text NOT NULL,
    published_at timestamptz NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT career_guide_posts_pk PRIMARY KEY (id),
    CONSTRAINT career_guide_posts_slug_uk UNIQUE (slug),
    CONSTRAINT career_guide_posts_status_ck CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT career_guide_posts_display_order_ck CHECK (display_order >= 0),
    CONSTRAINT career_guide_posts_category_ck CHECK (
        category = btrim(category) AND char_length(category) BETWEEN 1 AND 60
    ),
    CONSTRAINT career_guide_posts_title_ck CHECK (
        title = btrim(title) AND char_length(title) BETWEEN 1 AND 200
    ),
    CONSTRAINT career_guide_posts_summary_ck CHECK (
        summary = btrim(summary) AND char_length(summary) BETWEEN 1 AND 500
    ),
    CONSTRAINT career_guide_posts_body_ck CHECK (
        body = btrim(body) AND char_length(body) BETWEEN 1 AND 10000
    ),
    CONSTRAINT career_guide_posts_publish_state_ck CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL
    ),
    CONSTRAINT career_guide_posts_version_ck CHECK (version >= 0)
);

CREATE INDEX career_guide_posts_published_order_idx
    ON career_guide_posts (display_order, published_at DESC, id)
    WHERE status = 'PUBLISHED';

INSERT INTO career_guide_posts (
    id, slug, status, display_order, category, title, summary, body,
    published_at, version, created_at, updated_at
) VALUES
(
    '17000000-0000-4000-8000-000000000001',
    'before-job-analysis',
    'PUBLISHED',
    10,
    '공고 분석',
    '공고 분석 전에 확인할 항목',
    '직무명보다 실제 업무와 필수 조건을 먼저 분리해 보세요.',
    E'1. 담당 업무에서 반복되는 동사와 기술을 표시하세요.\n\n2. 필수 조건과 우대 조건을 나누고, 충족 여부를 근거와 함께 적으세요.\n\n3. 근무 지역, 고용 형태, 전형 절차와 마감 시각을 마지막에 다시 확인하세요.\n\n확신하기 어려운 조건은 추측하지 말고 지원 전에 채용 페이지 원문에서 확인하는 것이 안전합니다.',
    '2026-08-02T00:00:00Z',
    1,
    '2026-08-02T00:00:00Z',
    '2026-08-02T00:00:00Z'
),
(
    '17000000-0000-4000-8000-000000000002',
    'turn-experience-into-material',
    'PUBLISHED',
    20,
    '경험 정리',
    '경험을 자기소개서 소재로 정리하는 방법',
    '상황보다 내가 한 판단과 변화가 보이도록 경험을 구조화해 보세요.',
    E'경험 하나를 상황, 목표, 행동, 결과 네 칸으로 나눠 적어 보세요.\n\n행동에는 팀 전체가 한 일이 아니라 내가 직접 선택하고 실행한 일을 씁니다. 결과는 가능하면 수치, 기간, 사용자 반응처럼 확인 가능한 변화로 남깁니다.\n\n마지막에는 이 경험에서 얻은 기준이나 다음에 다르게 할 점을 한 문장으로 덧붙이면 면접 답변에도 활용하기 좋습니다.',
    '2026-08-02T00:01:00Z',
    1,
    '2026-08-02T00:01:00Z',
    '2026-08-02T00:01:00Z'
),
(
    '17000000-0000-4000-8000-000000000003',
    'choose-role-strengths',
    'PUBLISHED',
    30,
    '강점 선택',
    '지원 직무에 맞는 강점 선택 방법',
    '좋아 보이는 강점보다 공고의 업무와 근거가 연결되는 강점을 고르세요.',
    E'공고에서 중요한 업무 세 가지를 고른 뒤, 각 업무에 필요한 역량을 한 단어로 적어 보세요.\n\n그다음 내 경험 중 그 역량을 실제로 사용한 사례가 있는지 확인합니다. 사례가 없는 강점은 빼고, 행동과 결과를 설명할 수 있는 강점 두세 개만 남기는 편이 설득력이 높습니다.\n\n같은 경험도 지원 직무에 따라 강조할 행동과 결과가 달라질 수 있습니다.',
    '2026-08-02T00:02:00Z',
    1,
    '2026-08-02T00:02:00Z',
    '2026-08-02T00:02:00Z'
),
(
    '17000000-0000-4000-8000-000000000004',
    'concise-interview-answer',
    'PUBLISHED',
    40,
    '면접 준비',
    '면접 답변을 간결하게 구성하는 방법',
    '결론을 먼저 말하고 근거가 되는 경험을 짧게 연결하세요.',
    E'첫 문장에는 질문에 대한 답을 바로 말합니다. 이어서 그 판단을 뒷받침하는 경험을 상황, 행동, 결과 순서로 설명하세요.\n\n한 답변에는 핵심 메시지를 하나만 두고, 세부 정보는 후속 질문에서 말할 수 있도록 남겨 둡니다.\n\n연습할 때는 답변을 외우기보다 결론과 근거를 나타내는 키워드만 기억하면 말투가 더 자연스럽습니다.',
    '2026-08-02T00:03:00Z',
    1,
    '2026-08-02T00:03:00Z',
    '2026-08-02T00:03:00Z'
),
(
    '17000000-0000-4000-8000-000000000005',
    'final-deadline-check',
    'PUBLISHED',
    50,
    '최종 점검',
    '마감 전 최종 점검 체크리스트',
    '제출 파일과 입력 내용, 마감 시각을 마지막으로 한 번 더 확인하세요.',
    E'제출 전에는 다음 항목을 순서대로 확인해 보세요.\n\n- 공고명과 지원 직무가 맞는지\n- 최신 이력서와 자기소개서 파일인지\n- 회사명이나 다른 공고명이 남아 있지 않은지\n- 필수 문항과 첨부 파일이 모두 입력됐는지\n- 마감 날짜뿐 아니라 정확한 시각과 시간대가 무엇인지\n\n제출 완료 화면이나 접수 메일도 보관해 두면 이후 지원 현황을 정리하기 쉽습니다.',
    '2026-08-02T00:04:00Z',
    1,
    '2026-08-02T00:04:00Z',
    '2026-08-02T00:04:00Z'
);
