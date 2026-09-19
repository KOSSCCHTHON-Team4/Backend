-- ============================================================================
-- V1__init.sql : 감정지도 초기 스키마 (ERD: User/Place/Memory/Reaction/LetterDelivery/Report)
--   * 사진은 로컬 경로에 저장하고, DB 에는 상대경로(image_path)만 기록한다.
--   * 감정 태그(emotion_tag)는 서버가 본문(content)에서 Claude 로 자동 추출해 채운다.
--   * 임베딩(embedding)은 pgvector vector 타입으로 저장해 유사도 검색을 지원한다.
--     임베딩 생성 모델(Voyage 등)은 추후 확정. Claude 는 임베딩 모델이 아님(감정 태그 추출용).
--   * 컬럼 네이밍은 snake_case 로 통일한다 (JPA 물리 네이밍 전략과 일치).
--
-- 사전 조건: 각자 로컬 PostgreSQL 17 에 pgvector 확장이 설치돼 있어야 한다.
--
-- Flyway 규칙(팀 합의): 파일명은 V{버전}__{설명}.sql  (더블 언더스코어, 설명은 snake_case)
--   예) V2__add_place_category.sql
--   번호 충돌 방지: 만들기 전 git pull 로 최고 번호 확인 후 +1. 겹치면 나중 머지자가 리네임.
--   다음 새 마이그레이션은 누구든 V2 부터.
--   상세는 ARCHITECTURE.md 의 "9. Flyway 마이그레이션 작성 양식" 참고.
-- ============================================================================

-- pgvector 확장 활성화 (vector 타입 사용을 위해 필수)
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- User : 사용자
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    nickname      VARCHAR(50)      NOT NULL UNIQUE,
    email         VARCHAR(255)     NOT NULL UNIQUE,
    -- 비밀번호는 BCrypt 해시로만 저장한다(평문 금지).
    password_hash VARCHAR(255)     NOT NULL,
    home_lat      DOUBLE PRECISION,
    home_lng      DOUBLE PRECISION,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Place : 장소 (위경도 + 이름). 중복 허용(해커톤 단순화).
-- ---------------------------------------------------------------------------
CREATE TABLE place (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)     NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL
);

-- ---------------------------------------------------------------------------
-- Memory : 장소에 남긴 기억(본문 + 사진 + 감정태그 + 임베딩)
--   emotion_tag : 서버가 content 에서 Claude 로 자동 추출 (JOY, SADNESS ...). nullable(추출 전).
--   visibility  : LETTER(편지로 배달 대상) / PRIVATE(비공개)
--   status      : ACTIVE / HIDDEN(신고 등으로 숨김) / DELETED(소프트/하드 겸용 표시)
--   embedding   : vector(1024). 생성 모델 추후 확정. nullable(생성 전).
-- ---------------------------------------------------------------------------
CREATE TABLE memory (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    place_id    BIGINT           REFERENCES place (id) ON DELETE SET NULL,
    content     VARCHAR(2000)    NOT NULL,
    image_path  VARCHAR(500),
    visibility  VARCHAR(20)      NOT NULL,
    emotion_tag VARCHAR(30),
    embedding   vector(1024),
    status      VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Reaction : 사용자가 Memory 에 남기는 반응. (user, memory) 중복 불가.
-- ---------------------------------------------------------------------------
CREATE TABLE reaction (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    memory_id   BIGINT           NOT NULL REFERENCES memory (id)   ON DELETE CASCADE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_reaction_user_memory UNIQUE (user_id, memory_id)
);

-- ---------------------------------------------------------------------------
-- LetterDelivery : Memory 를 특정 수신자에게 편지로 배달. score = 추천 점수(유사도 등).
--   read_at 은 안 읽었으면 NULL.
-- ---------------------------------------------------------------------------
CREATE TABLE letter_delivery (
    id           BIGSERIAL PRIMARY KEY,
    receiver_id  BIGINT           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    memory_id    BIGINT           NOT NULL REFERENCES memory (id)   ON DELETE CASCADE,
    score        DOUBLE PRECISION NOT NULL,
    delivered_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ
);

-- ---------------------------------------------------------------------------
-- Report : Memory 신고. reason 은 자유 텍스트. 중복 신고 허용(제약 없음).
-- ---------------------------------------------------------------------------
CREATE TABLE report (
    id           BIGSERIAL PRIMARY KEY,
    reporter_id  BIGINT          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    memory_id    BIGINT          NOT NULL REFERENCES memory (id)   ON DELETE CASCADE,
    reason       VARCHAR(500)    NOT NULL,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 인덱스
-- ---------------------------------------------------------------------------
CREATE INDEX idx_memory_user_id     ON memory (user_id);
CREATE INDEX idx_memory_place_id    ON memory (place_id);
CREATE INDEX idx_memory_status      ON memory (status);
CREATE INDEX idx_place_location     ON place (lat, lng);
CREATE INDEX idx_reaction_memory_id ON reaction (memory_id);
CREATE INDEX idx_letter_receiver_id ON letter_delivery (receiver_id);
CREATE INDEX idx_report_memory_id   ON report (memory_id);

-- 벡터 유사도 검색용 인덱스 (HNSW, 코사인 거리 기준).
CREATE INDEX idx_memory_embedding
    ON memory USING hnsw (embedding vector_cosine_ops);
