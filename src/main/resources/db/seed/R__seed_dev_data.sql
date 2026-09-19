-- ============================================================================
-- R__seed_dev_data.sql  —  개발/시연 전용 더미데이터 (Flyway repeatable)
--
--  적용 범위(중요):
--    * 이 파일은 classpath:db/seed 에 있으며, local 프로필에서만 로드된다.
--      (application-local.yml 의 spring.flyway.locations 에 db/seed 를 추가)
--    * 공통 application.yml / prod 는 classpath:db/migration 만 로드하므로
--      운영·CI 스키마에는 이 더미가 절대 들어가지 않는다. (WORK_PLAN 옵션 A)
--
--  데이터 원칙(조직 규칙 + ERD/MVP):
--    * 모든 경험의 data_origin 은 SYNTHETIC / TEAM_TEST 만 사용한다(실참여자 PARTICIPANT 아님).
--    * 실제 개인정보 없음. 이메일/닉네임/본문 모두 합성값.
--    * 비밀번호 원문은 저장하지 않는다. pgcrypto crypt(...,gen_salt('bf'))로 BCrypt 해시만 저장.
--      개발용 공통 비밀번호: 'password123'  (로그인 테스트용, 시연/개발 전용)
--
--  repeatable(R__) 특성: 체크섬이 바뀌면 매 부팅 시 재실행된다. 따라서
--    멱등(idempotent)하게 작성한다 — 먼저 이 시드가 만든 데이터를 지우고 다시 삽입한다.
--    place_categories(V3 시드)는 건드리지 않는다.
--
--  좌표 배치 원칙(반경 1000m = local mock radius 기준, haversine):
--    * 계정 mailbox 3곳과 장소 8곳을 한 점에 몰아두지 않고, 반경 "안/경계/밖" 이 모두 생기게 배분한다.
--      데모 중심(37.6109, 126.9977)은 u1 의 mailbox 다.
--    * u1 민들레  (37.6109, 126.9977)  ← 데모 중심
--      u2 바람개비 (37.6140, 127.0220)  ← u1 에서 동쪽 약 2.2km
--      u3 자갈길   (37.6010, 126.9860)  ← u1 에서 남서쪽 약 1.5km
--    * 장소 → (u1 / u2 / u3 mailbox 까지 거리)
--      p10 골목 안 조용한 카페   258m /  2391m / 1439m   u1 반경 안, u2 밖  → u2 09-17 NO_CANDIDATE 의 근거
--      p11 광장 앞 브런치       684m /  1502m / 2141m   u1 반경 안
--      p12 언덕 위 산책로       948m /  2155m / 2323m   u1 반경 "경계 안"(~950m)
--      p13 작은 서점 겸 북카페 1505m /  3611m /  426m   u3 반경 안, u1 밖
--      p14 심야 재즈 바        2016m /   208m / 3375m   u2 반경 안, u1 밖
--      p15 전시 공간          1048m /  2493m / 1103m   u1 반경 "경계 밖"(~1050m) → 경계 제외 테스트
--      p16 노트북 작업 카페    2370m /   257m / 3636m   u2 반경 안
--      p17 (label 없음)       2564m /  2870m / 2443m   모두 밖(PRIVATE 는 반경과 무관함을 보임)
--    * memories.place_lat/lng 는 항상 그 place 의 좌표 스냅샷과 같다(PLACE_COORDINATE_MISMATCH 방지).
--      LETTER_COPY 사본(mc1)은 원문과 같은 장소 좌표를 복사한다 — 이것은 원문 연결이 아니다.
--    * 배달·선정 행(8·9절)의 후보 수·점수는 이 지리 배치와 일치하도록 계산했다.
-- ============================================================================

-- BCrypt 해시 생성을 위해 pgcrypto 가 필요하다(로컬엔 이미 설치됨). 없으면 예외 대신 건너뛰도록.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- 0) 멱등성: 이 시드가 만드는 합성 데이터만 정리한다(실데이터 보호).
--    FK(RESTRICT) 역순으로 삭제한다. data_origin 이 SYNTHETIC/TEAM_TEST 인
--    memories 에 매달린 것만 지운다. 계정은 고정 UUID 로 식별한다.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    seed_users UUID[] := ARRAY[
        '00000000-0000-0000-0000-0000000000a1',
        '00000000-0000-0000-0000-0000000000a2',
        '00000000-0000-0000-0000-0000000000a3',
        '00000000-0000-0000-0000-0000000000a4'
    ]::UUID[];
BEGIN
    -- 신고 → 배달 → 일일선정 → 이미지업로드 → 기억분류 → 기억 → 취향 → 자격증명 → 계정
    DELETE FROM reports              WHERE reporter_id = ANY(seed_users) OR handled_by = ANY(seed_users);
    DELETE FROM letter_deliveries    WHERE receiver_id = ANY(seed_users);
    DELETE FROM daily_selections     WHERE user_id = ANY(seed_users);
    DELETE FROM image_uploads        WHERE owner_id = ANY(seed_users);
    DELETE FROM memory_categories    WHERE memory_id IN (SELECT id FROM memories WHERE owner_id = ANY(seed_users));
    DELETE FROM memories             WHERE owner_id = ANY(seed_users);
    DELETE FROM user_preference_versions WHERE user_id = ANY(seed_users);
    DELETE FROM email_password_credentials WHERE user_id = ANY(seed_users);
    DELETE FROM app_users            WHERE id = ANY(seed_users);
    -- 시드가 만든 장소도 정리(고정 UUID)
    DELETE FROM places WHERE id IN (
        '00000000-0000-0000-0000-000000000010','00000000-0000-0000-0000-000000000011',
        '00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000013',
        '00000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000015',
        '00000000-0000-0000-0000-000000000016','00000000-0000-0000-0000-000000000017'
    );
END $$;

-- ---------------------------------------------------------------------------
-- 1) 계정 (app_users)
--    mailbox_lat/lng/enabled_at 는 "셋 다 NULL(온보딩 전)" 또는 "셋 다 값(온보딩 완료)".
--    mailbox 는 서로 1.5~3.5km 떨어진 세 지점에 둔다(헤더 "좌표 배치 원칙" 참조). u1 = 데모 중심.
--    u1~u3: ACTIVE + 온보딩 완료.  u4: ACTIVE + 온보딩 전(mailbox NULL) — 온보딩 전 상태 테스트용.
-- ---------------------------------------------------------------------------
INSERT INTO app_users (id, nickname, access_status, app_role, mailbox_lat, mailbox_lng, mailbox_enabled_at, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-0000000000a1', '민들레',   'ACTIVE', 'USER',     37.6109, 126.9977, TIMESTAMPTZ '2026-09-10 09:00:00+09', TIMESTAMPTZ '2026-09-10 09:00:00+09', TIMESTAMPTZ '2026-09-10 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a2', '바람개비', 'ACTIVE', 'USER',     37.6140, 127.0220, TIMESTAMPTZ '2026-09-11 09:00:00+09', TIMESTAMPTZ '2026-09-11 09:00:00+09', TIMESTAMPTZ '2026-09-11 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a3', '자갈길',   'ACTIVE', 'OPERATOR', 37.6010, 126.9860, TIMESTAMPTZ '2026-09-11 09:00:00+09', TIMESTAMPTZ '2026-09-11 09:00:00+09', TIMESTAMPTZ '2026-09-11 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a4', '새벽안개', 'ACTIVE', 'USER',     NULL, NULL, NULL, TIMESTAMPTZ '2026-09-19 08:30:00+09', TIMESTAMPTZ '2026-09-19 08:30:00+09');

-- ---------------------------------------------------------------------------
-- 2) 자격증명 (email_password_credentials)
--    email_lookup_key = lower(strip(email)) — 앱의 normalizeEmailLookupKey 와 동일.
--    password_hash = BCrypt('password123'). 원문은 저장하지 않는다.
-- ---------------------------------------------------------------------------
INSERT INTO email_password_credentials (user_id, email, email_lookup_key, password_hash, password_changed_at, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'dandelion@example.com', 'dandelion@example.com', crypt('password123', gen_salt('bf', 10)), now(), now(), now()),
    ('00000000-0000-0000-0000-0000000000a2', 'pinwheel@example.com',  'pinwheel@example.com',  crypt('password123', gen_salt('bf', 10)), now(), now(), now()),
    ('00000000-0000-0000-0000-0000000000a3', 'gravel@example.com',    'gravel@example.com',    crypt('password123', gen_salt('bf', 10)), now(), now(), now()),
    ('00000000-0000-0000-0000-0000000000a4', 'dawnmist@example.com',  'dawnmist@example.com',  crypt('password123', gen_salt('bf', 10)), now(), now(), now());

-- ---------------------------------------------------------------------------
-- 3) 불변 취향 버전 (user_preference_versions)
--    각 축 -1/+1 필수. revision>0. axis_definition_version=1.
--    u1: 조용/아늑/혼자/오래 (모두 -1)   u2: 북적/탁트임/함께/잠깐 (모두 +1)
--    u3: 혼합.  u1 은 취향 변경 이력(rev1→rev2)도 하나 둔다.  u4 는 온보딩 전이라 취향 없음.
-- ---------------------------------------------------------------------------
INSERT INTO user_preference_versions (id, user_id, revision, effective_at, crowd_level, spatial_feel, company_fit, stay_style, description, axis_definition_version) VALUES
    ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 1, TIMESTAMPTZ '2026-09-10 09:00:00+09', -1, -1, -1, -1, '조용하고 아늑한 혼자만의 공간을 오래 즐기고 싶어요', 1),
    ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1', 2, TIMESTAMPTZ '2026-09-15 21:00:00+09', -1, -1,  1, -1, '가끔은 함께 가기 좋은 곳도 좋아요', 1),
    ('00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000a2', 1, TIMESTAMPTZ '2026-09-11 09:00:00+09',  1,  1,  1,  1, '북적이고 탁 트인 곳에서 함께 잠깐 들르기 좋아요', 1),
    ('00000000-0000-0000-0000-0000000000b4', '00000000-0000-0000-0000-0000000000a3', 1, TIMESTAMPTZ '2026-09-11 09:00:00+09',  1, -1, -1,  1, NULL, 1);

-- ---------------------------------------------------------------------------
-- 4) 장소 (places) — 고정 UUID 8곳. label 은 선택. 세 mailbox 의 반경 안/경계/밖에 나눠 배치(헤더 표 참조).
-- ---------------------------------------------------------------------------
INSERT INTO places (id, label, lat, lng, created_at) VALUES
    ('00000000-0000-0000-0000-000000000010', '골목 안 조용한 카페',   37.6118, 126.9950, now()),  -- u1 안(258m), u2 밖
    ('00000000-0000-0000-0000-000000000011', '광장 앞 브런치',       37.6130, 127.0050, now()),  -- u1 안(684m)
    ('00000000-0000-0000-0000-000000000012', '언덕 위 산책로',       37.6194, 126.9985, now()),  -- u1 경계 안(948m)
    ('00000000-0000-0000-0000-000000000013', '작은 서점 겸 북카페',  37.6040, 126.9830, now()),  -- u3 안(426m), u1 밖(1505m)
    ('00000000-0000-0000-0000-000000000014', '심야 재즈 바',         37.6150, 127.0200, now()),  -- u2 안(208m), u1 밖
    ('00000000-0000-0000-0000-000000000015', '전시 공간',           37.6015, 126.9985, now()),  -- u1 경계 밖(1048m)
    ('00000000-0000-0000-0000-000000000016', '노트북 작업 카페',     37.6128, 127.0245, now()),  -- u2 안(257m)
    ('00000000-0000-0000-0000-000000000017', NULL,                  37.5900, 127.0100, now());  -- 모두 밖(PRIVATE 전용 핀)

-- ---------------------------------------------------------------------------
-- 5) 경험 (memories)
--    제약 요약:
--      * 4축 -1/+1 필수, 각 source AI|USER.
--      * LETTER 는 moderation_status <> 'NOT_REQUIRED'.
--        - 배달 후보가 되려면: content_status=ACTIVE + moderation_status=APPROVED + available_at 설정.
--        - available_at 은 (LETTER 이고) created_at 이상.
--      * PRIVATE 는 available_at IS NULL. moderation 은 NOT_REQUIRED 사용 가능.
--      * origin_kind=LETTER_COPY 는 반드시 PRIVATE.
--      * data_origin ∈ (SYNTHETIC, TEAM_TEST).  이미지 컬럼 3개는 all-null(사진 없음)로 둔다.
--    구성:
--      m1..m6  : u3(operator)·u2 가 작성한 LETTER, APPROVED + available_at → 배달 후보/배달됨.
--      m7      : LETTER, PENDING(안전검사 전) → 후보 아님(가시성 테스트).
--      mp1..mp3: PRIVATE 직접 작성(DIRECT).
--      mc1     : PRIVATE + LETTER_COPY (좋아요 독립 복사 결과 모사) — u1 소유.
-- ---------------------------------------------------------------------------
INSERT INTO memories (
    id, owner_id, place_id, distribution_type, origin_kind, data_origin, content,
    place_label_snapshot, place_lat, place_lng,
    crowd_level, spatial_feel, company_fit, stay_style,
    crowd_source, spatial_source, company_source, stay_source,
    axis_definition_version, atmosphere_analysis_status, category_analysis_status,
    analysis_model, analysis_prompt_version,
    image_path, image_media_type, image_size_bytes,
    content_status, moderation_status, available_at, created_at, deleted_at
) VALUES
    -- m1: 조용/아늑/혼자/오래 카페 LETTER (u3 작성) — APPROVED 후보
    ('00000000-0000-0000-0000-0000000000c1','00000000-0000-0000-0000-0000000000a3','00000000-0000-0000-0000-000000000010',
     'LETTER','DIRECT','SYNTHETIC','창가 자리에 앉아 오래 책을 읽기 좋은 조용한 카페였어요.',
     '골목 안 조용한 카페',37.6118,126.9950, -1,-1,-1,-1, 'AI','AI','AI','USER',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-16 09:00:00+09', TIMESTAMPTZ '2026-09-16 08:00:00+09', NULL),
    -- m2: 북적/탁트임/함께/잠깐 브런치 LETTER (u2 작성) — APPROVED 후보
    ('00000000-0000-0000-0000-0000000000c2','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-000000000011',
     'LETTER','DIRECT','SYNTHETIC','친구들과 북적이는 광장 앞에서 브런치를 즐겼습니다.',
     '광장 앞 브런치',37.6130,127.0050, 1,1,1,1, 'AI','AI','USER','AI',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-16 09:00:00+09', TIMESTAMPTZ '2026-09-16 07:30:00+09', NULL),
    -- m3: 조용/아늑/혼자/오래 산책로 LETTER (u2 작성) — APPROVED 후보 (u1 취향과 4점 일치)
    ('00000000-0000-0000-0000-0000000000c3','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-000000000012',
     'LETTER','DIRECT','TEAM_TEST','이른 아침 혼자 걷기 좋은 조용한 언덕 산책로.',
     '언덕 위 산책로',37.6194,126.9985, -1,-1,-1,-1, 'USER','USER','USER','USER',
     1,'SUCCEEDED','UNCLASSIFIED',NULL,NULL, NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-17 09:00:00+09', TIMESTAMPTZ '2026-09-17 06:00:00+09', NULL),
    -- m4: 북카페 LETTER (u3) — APPROVED 후보
    ('00000000-0000-0000-0000-0000000000c4','00000000-0000-0000-0000-0000000000a3','00000000-0000-0000-0000-000000000013',
     'LETTER','DIRECT','SYNTHETIC','작은 서점 겸 북카페, 아늑하지만 사람이 제법 있었어요.',
     '작은 서점 겸 북카페',37.6040,126.9830, 1,-1,-1,1, 'AI','USER','AI','AI',
     1,'PARTIAL','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-18 09:00:00+09', TIMESTAMPTZ '2026-09-18 08:10:00+09', NULL),
    -- m5: 재즈 바 LETTER (u2) — APPROVED 후보
    ('00000000-0000-0000-0000-0000000000c5','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-000000000014',
     'LETTER','DIRECT','SYNTHETIC','늦은 밤 함께 듣기 좋은 재즈 바, 잠깐 들르기 좋아요.',
     '심야 재즈 바',37.6150,127.0200, 1,1,1,1, 'AI','AI','AI','AI',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-18 09:00:00+09', TIMESTAMPTZ '2026-09-18 07:45:00+09', NULL),
    -- m6: 전시 공간 LETTER (u3) — APPROVED 후보
    ('00000000-0000-0000-0000-0000000000c6','00000000-0000-0000-0000-0000000000a3','00000000-0000-0000-0000-000000000015',
     'LETTER','DIRECT','TEAM_TEST','탁 트인 전시 공간에서 혼자 오래 머물렀습니다.',
     '전시 공간',37.6015,126.9985, -1,1,-1,-1, 'USER','AI','USER','USER',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','APPROVED', TIMESTAMPTZ '2026-09-19 09:00:00+09', TIMESTAMPTZ '2026-09-19 08:00:00+09', NULL),
    -- m7: LETTER 이지만 안전검사 전(PENDING) + available_at NULL → 후보/타인 조회 제외 대상
    ('00000000-0000-0000-0000-0000000000c7','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-000000000016',
     'LETTER','DIRECT','SYNTHETIC','아직 안전검사 전인 작업용 카페 후기입니다.',
     '노트북 작업 카페',37.6128,127.0245, 1,1,-1,-1, 'AI','AI','AI','AI',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','PENDING', NULL, TIMESTAMPTZ '2026-09-19 08:50:00+09', NULL),
    -- mp1: PRIVATE 직접(u1)
    ('00000000-0000-0000-0000-0000000000d1','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000017',
     'PRIVATE','DIRECT','SYNTHETIC','나만 보려고 남긴 개인 기록입니다.',
     NULL,37.5900,127.0100, -1,-1,-1,-1, 'USER','USER','USER','USER',
     1,'NOT_RUN','NOT_RUN',NULL,NULL, NULL,NULL,NULL,
     'ACTIVE','NOT_REQUIRED', NULL, TIMESTAMPTZ '2026-09-14 12:00:00+09', NULL),
    -- mp2: PRIVATE 직접(u2)
    ('00000000-0000-0000-0000-0000000000d2','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-000000000010',
     'PRIVATE','DIRECT','TEAM_TEST','조용한 카페에서의 개인 메모.',
     '골목 안 조용한 카페',37.6118,126.9950, -1,-1,1,-1, 'USER','USER','USER','USER',
     1,'NOT_RUN','NOT_RUN',NULL,NULL, NULL,NULL,NULL,
     'ACTIVE','NOT_REQUIRED', NULL, TIMESTAMPTZ '2026-09-15 18:00:00+09', NULL),
    -- mc1: PRIVATE + LETTER_COPY (좋아요로 독립 복사된 사본 모사, u1 소유). 원문 링크는 남기지 않음.
    ('00000000-0000-0000-0000-0000000000e1','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000011',
     'PRIVATE','LETTER_COPY','SYNTHETIC','친구들과 북적이는 광장 앞에서 브런치를 즐겼습니다.',
     '광장 앞 브런치',37.6130,127.0050, 1,1,1,1, 'AI','AI','USER','AI',
     1,'SUCCEEDED','CLASSIFIED','mock-analysis','mock-v1', NULL,NULL,NULL,
     'ACTIVE','NOT_REQUIRED', NULL, TIMESTAMPTZ '2026-09-16 10:00:00+09', NULL);

-- ---------------------------------------------------------------------------
-- 6) 기억-카테고리 (memory_categories)  0~3개, slot_no 1~3 unique, category_id 1~8
--    label_snapshot 은 그 시점 라벨. taxonomy_version=1.
-- ---------------------------------------------------------------------------
INSERT INTO memory_categories (memory_id, category_id, slot_no, assignment_source, label_snapshot, taxonomy_version) VALUES
    ('00000000-0000-0000-0000-0000000000c1', 1, 1, 'AI',   '카페',          1),   -- m1 카페
    ('00000000-0000-0000-0000-0000000000c2', 2, 1, 'AI',   '음식점',        1),   -- m2 음식점
    ('00000000-0000-0000-0000-0000000000c2', 1, 2, 'USER', '카페',          1),   -- m2 +카페(2슬롯)
    ('00000000-0000-0000-0000-0000000000c4', 1, 1, 'AI',   '카페',          1),   -- m4 카페
    ('00000000-0000-0000-0000-0000000000c4', 5, 2, 'AI',   '문화',          1),   -- m4 문화
    ('00000000-0000-0000-0000-0000000000c4', 6, 3, 'USER', '공부·작업 공간', 1),  -- m4 공부(3슬롯 채움)
    ('00000000-0000-0000-0000-0000000000c5', 3, 1, 'AI',   '술집',          1),   -- m5 술집
    ('00000000-0000-0000-0000-0000000000c6', 5, 1, 'USER', '문화',          1),   -- m6 문화
    ('00000000-0000-0000-0000-0000000000e1', 2, 1, 'AI',   '음식점',        1),   -- 사본 mc1 카페/음식점 복사
    ('00000000-0000-0000-0000-0000000000e1', 1, 2, 'USER', '카페',          1);
    -- m3, m7, mp1, mp2 는 카테고리 0개(미분류 허용).

-- ---------------------------------------------------------------------------
-- 7) 임시 이미지 업로드 (image_uploads)  — 사진 없는 경험만 있으므로 STAGED/EXPIRED 만 둔다.
--    ATTACHED 상태는 attached_memory_id 가 있어야 하는데, 시드 경험엔 image_path 가 없어
--    무결성을 위해 STAGED(첨부 전) / EXPIRED(만료) 만 생성한다.
-- ---------------------------------------------------------------------------
INSERT INTO image_uploads (id, owner_id, storage_path, media_type, size_bytes, width, height, status, expires_at, attached_memory_id, created_at) VALUES
    ('00000000-0000-0000-0000-0000000000f1','00000000-0000-0000-0000-0000000000a1','seed/staged-dandelion.jpg','image/jpeg', 204800, 1200, 800, 'STAGED',  now() + INTERVAL '30 minutes', NULL, now()),
    ('00000000-0000-0000-0000-0000000000f2','00000000-0000-0000-0000-0000000000a2','seed/expired-pinwheel.png','image/png',  102400,  900, 900, 'EXPIRED', now() - INTERVAL '10 minutes', NULL, now() - INTERVAL '1 hour');

-- ---------------------------------------------------------------------------
-- 8) 일일 선정 (daily_selections)
--    cutoff_at 은 (service_date + 09:00) AT TIME ZONE 'Asia/Seoul' 정확히 일치해야 함(체크 제약).
--    preference_version_id 는 (id, user_id) 로 FK. radius_m>0.
--    status 별 completed_at 규칙:
--      DELIVERED/NO_CANDIDATE/EXPIRED_ERROR/SKIPPED_ACCESS → completed_at NOT NULL
--      PENDING/PROCESSING/RETRYABLE_ERROR                  → completed_at NULL
--    claim 규칙: PROCESSING 만 claim_token+lease 필요, 그 외엔 둘 다 NULL.
--
--    후보 = 타인 작성·APPROVED·available_at<=cutoff·미수신·수신자 mailbox 반경(1000m) 안 LETTER.
--    s1: u1, 2026-09-16 DELIVERED — 후보 m1(258m, 4점)·m2(684m, 0점) → 2건, 최고점 1건, m1 수신
--    s2: u2, 2026-09-17 NO_CANDIDATE — m1 은 u2 반경 밖(2391m), m2·m3 은 u2 본인 작성 → 0건
--    s3: u1, 2026-09-17 DELIVERED — 취향 rev2(-1,-1,+1,-1). 후보 m2(1점)·m3(948m, 3점), m1 은 기수신 → 2건, m3 수신
--    s4: u3, 2026-09-18 PENDING (아직 처리 전, completed_at NULL, claim NULL)
-- ---------------------------------------------------------------------------
INSERT INTO daily_selections (
    user_id, service_date, cutoff_at, preference_version_id, radius_m, rule_version, random_seed,
    status, attempt_count, claim_token, lease_expires_at, last_attempt_at, last_error_code,
    candidate_count, top_tie_count, fixed_score, tie_break_method, completed_at, created_at
) VALUES
    ('00000000-0000-0000-0000-0000000000a1', DATE '2026-09-16',
        (DATE '2026-09-16' + TIME '09:00') AT TIME ZONE 'Asia/Seoul',
        '00000000-0000-0000-0000-0000000000b1', 1000, 'atmosphere-v1', gen_random_uuid(),
        'DELIVERED', 1, NULL, NULL, TIMESTAMPTZ '2026-09-16 09:00:05+09', NULL,
        2, 1, 4, 'SINGLE_TOP', TIMESTAMPTZ '2026-09-16 09:00:06+09', TIMESTAMPTZ '2026-09-16 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a2', DATE '2026-09-17',
        (DATE '2026-09-17' + TIME '09:00') AT TIME ZONE 'Asia/Seoul',
        '00000000-0000-0000-0000-0000000000b3', 1000, 'atmosphere-v1', gen_random_uuid(),
        'NO_CANDIDATE', 1, NULL, NULL, TIMESTAMPTZ '2026-09-17 09:00:03+09', NULL,
        0, 0, NULL, NULL, TIMESTAMPTZ '2026-09-17 09:00:03+09', TIMESTAMPTZ '2026-09-17 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a1', DATE '2026-09-17',
        (DATE '2026-09-17' + TIME '09:00') AT TIME ZONE 'Asia/Seoul',
        '00000000-0000-0000-0000-0000000000b2', 1000, 'atmosphere-v1', gen_random_uuid(),
        'DELIVERED', 1, NULL, NULL, TIMESTAMPTZ '2026-09-17 09:00:04+09', NULL,
        2, 1, 3, 'SINGLE_TOP', TIMESTAMPTZ '2026-09-17 09:00:05+09', TIMESTAMPTZ '2026-09-17 09:00:00+09'),
    ('00000000-0000-0000-0000-0000000000a3', DATE '2026-09-18',
        (DATE '2026-09-18' + TIME '09:00') AT TIME ZONE 'Asia/Seoul',
        '00000000-0000-0000-0000-0000000000b4', 1000, 'atmosphere-v1', gen_random_uuid(),
        'PENDING', 0, NULL, NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, TIMESTAMPTZ '2026-09-18 09:00:00+09');

-- ---------------------------------------------------------------------------
-- 9) 실제 배달 (letter_deliveries)
--    (receiver_id, service_date) 는 daily_selections FK + UNIQUE.
--    (receiver_id, memory_id) UNIQUE (같은 원문 재수신 금지).
--    delivered_at: 같은 service_date 이고 그날 09:00(KST) 이후.
--    read_at/liked_at 은 delivered_at 이상.
--    d1: u1 이 2026-09-16 에 m1 수신 → 읽음 + 좋아요(사본 mc1 이 그 결과).
--    d2: u1 이 2026-09-17 에 m3 수신 → 읽음, 좋아요 없음.
-- ---------------------------------------------------------------------------
INSERT INTO letter_deliveries (id, receiver_id, service_date, memory_id, delivered_at, read_at, liked_at) VALUES
    ('00000000-0000-0000-0000-0000000000d9','00000000-0000-0000-0000-0000000000a1', DATE '2026-09-16',
        '00000000-0000-0000-0000-0000000000c1', TIMESTAMPTZ '2026-09-16 09:00:06+09',
        TIMESTAMPTZ '2026-09-16 10:15:00+09', TIMESTAMPTZ '2026-09-16 10:16:00+09'),
    ('00000000-0000-0000-0000-0000000000da','00000000-0000-0000-0000-0000000000a1', DATE '2026-09-17',
        '00000000-0000-0000-0000-0000000000c3', TIMESTAMPTZ '2026-09-17 09:00:05+09',
        TIMESTAMPTZ '2026-09-17 20:00:00+09', NULL);

-- ---------------------------------------------------------------------------
-- 10) 신고 (reports)
--     열람 가능한 경험 대상. reason 코드 유효. status 별 handled_by/handled_at 규칙:
--       RESOLVED/DISMISSED → handled_by + handled_at 필수
--       OPEN/IN_REVIEW     → handled_at NULL
--     r1: u1 이 m5 를 SPAM 으로 신고, OPEN.
--     r2: u2 가 m4 를 OTHER 로 신고 → operator(u3)가 DISMISSED 처리.
-- ---------------------------------------------------------------------------
INSERT INTO reports (id, reporter_id, memory_id, reason, details, status, handled_by, handling_note, created_at, handled_at) VALUES
    ('00000000-0000-0000-0000-0000000000fa','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000c5',
        'SPAM', '광고성으로 의심됨', 'OPEN', NULL, NULL, now(), NULL),
    ('00000000-0000-0000-0000-0000000000fb','00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-0000000000c4',
        'OTHER', '분류가 애매함', 'DISMISSED', '00000000-0000-0000-0000-0000000000a3', '정상 콘텐츠로 판단', now() - INTERVAL '2 hours', now() - INTERVAL '1 hour');
