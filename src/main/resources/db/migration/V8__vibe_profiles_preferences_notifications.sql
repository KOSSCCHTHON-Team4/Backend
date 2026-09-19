-- 기획 "AI 어떻게 활용할까요?" §5·§6·§9: 장소 분위기 벡터·카테고리 다수결, 경험 부가 출력,
-- 취향 알림 설정(preferences), 알림(notifications). 기존 4축 ±1 저장 규칙과 배달 테이블은 바꾸지 않는다.
-- 벡터는 pgvector 대신 4개 double 컬럼으로 둔다(차원이 4로 고정, hibernate-vector 미사용).

-- 1) places: 네이버 정보·카테고리 다수결·분위기 평균·리뷰 수
ALTER TABLE places
    ADD COLUMN naver_title TEXT,
    ADD COLUMN naver_address TEXT,
    ADD COLUMN naver_category TEXT,
    ADD COLUMN category_code TEXT REFERENCES place_categories(code) ON DELETE RESTRICT,
    ADD COLUMN category_source TEXT CHECK (category_source IN ('NAVER', 'REVIEWS')),
    ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0 CHECK (review_count >= 0),
    ADD COLUMN vibe_crowd DOUBLE PRECISION CHECK (vibe_crowd BETWEEN -1 AND 1),
    ADD COLUMN vibe_spatial DOUBLE PRECISION CHECK (vibe_spatial BETWEEN -1 AND 1),
    ADD COLUMN vibe_company DOUBLE PRECISION CHECK (vibe_company BETWEEN -1 AND 1),
    ADD COLUMN vibe_stay DOUBLE PRECISION CHECK (vibe_stay BETWEEN -1 AND 1),
    ADD COLUMN vibe_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_place_category CHECK ((category_code IS NULL) = (category_source IS NULL)),
    ADD CONSTRAINT ck_place_vibe CHECK (
        (vibe_crowd IS NULL AND vibe_spatial IS NULL AND vibe_company IS NULL AND vibe_stay IS NULL)
        OR (vibe_crowd IS NOT NULL AND vibe_spatial IS NOT NULL AND vibe_company IS NOT NULL AND vibe_stay IS NOT NULL));
CREATE INDEX idx_places_category ON places(category_code) WHERE category_code IS NOT NULL;

-- 2) memories: 분석 부가 출력(근거·태그·카테고리 신뢰도·안전·마스킹). 최종 4축은 그대로 ±1.
ALTER TABLE memories
    ADD COLUMN evidence JSONB,
    ADD COLUMN tags JSONB,
    ADD COLUMN category_pred TEXT REFERENCES place_categories(code) ON DELETE RESTRICT,
    ADD COLUMN category_conf DOUBLE PRECISION CHECK (category_conf BETWEEN 0 AND 1),
    ADD COLUMN safe BOOLEAN,
    ADD COLUMN pii_masked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN unsafe_reason TEXT;

-- 3) preferences: 키워드 카드 기반 취향 알림 설정(기획 §4·§9). 온보딩 취향 이력(user_preference_versions)과 별개.
CREATE TABLE preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    cards JSONB NOT NULL,
    vibe_crowd SMALLINT NOT NULL CHECK (vibe_crowd IN (-1, 0, 1)),
    vibe_spatial SMALLINT NOT NULL CHECK (vibe_spatial IN (-1, 0, 1)),
    vibe_company SMALLINT NOT NULL CHECK (vibe_company IN (-1, 0, 1)),
    vibe_stay SMALLINT NOT NULL CHECK (vibe_stay IN (-1, 0, 1)),
    preference_text TEXT CHECK (preference_text IS NULL OR btrim(preference_text) <> ''),
    category_filter JSONB,
    center_lat DOUBLE PRECISION NOT NULL CHECK (center_lat BETWEEN -90 AND 90),
    center_lng DOUBLE PRECISION NOT NULL CHECK (center_lng BETWEEN -180 AND 180),
    radius_m INTEGER NOT NULL CHECK (radius_m > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_preference_nonzero CHECK (vibe_crowd <> 0 OR vibe_spatial <> 0 OR vibe_company <> 0 OR vibe_stay <> 0)
);
CREATE INDEX idx_preferences_active ON preferences(active, user_id);

-- 4) notifications: 매칭 통과 알림(기획 §9). 같은 취향·같은 장소는 하루(KST) 1회.
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    preference_id UUID NOT NULL REFERENCES preferences(id) ON DELETE RESTRICT,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE RESTRICT,
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE RESTRICT,
    service_date DATE NOT NULL,
    similarity DOUBLE PRECISION NOT NULL CHECK (similarity BETWEEN 0 AND 1),
    score DOUBLE PRECISION NOT NULL CHECK (score BETWEEN 0 AND 1),
    stage SMALLINT NOT NULL CHECK (stage IN (1, 2)),
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_daily UNIQUE (preference_id, place_id, service_date),
    CONSTRAINT ck_notification_read CHECK (read_at IS NULL OR read_at >= created_at)
);
CREATE INDEX idx_notifications_inbox ON notifications(user_id, created_at DESC, id DESC);
