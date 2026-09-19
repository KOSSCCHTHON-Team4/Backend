# AI 활용 파이프라인 구현 노트 (기획 "AI 어떻게 활용할까요?" → 백엔드)

- 기준 문서: 기획 "AI 어떻게 활용할까요?"(§1~§9), `docs/AI_INTEGRATION.md`(실제 AI 서버 계약), `docs/API_SPEC.md` 8.8·8.9
- 마이그레이션: `V8__vibe_profiles_preferences_notifications.sql`
- 상태: 단위 테스트 174개 통과(DB 비연결), 로컬 PostgreSQL 에서 V8 적용·부팅·mock AI 로 end-to-end 흐름 확인(아래 §6)

## 1. 흐름 매핑

| 기획 단계 | 백엔드 구현 | 위치 |
|---|---|---|
| 리뷰 작성 → `/ai/analyze` 1회 | `POST /v1/memories/analyze` → `AnalysisPort`(본문 + `naver_category`) → `analysisToken` 서명 | `memory.MemoryAnalysisService`, `memory.analysis.*` |
| Memory 저장 | `POST /v1/memories`(`analysisToken` 검증, AI/USER 출처 판정, evidence·tags·신뢰도·안전·마스킹 저장) | `memory.MemoryService` |
| 안전 판정 | LETTER 저장 후 `ModerationPort` → APPROVED 시 최초 `available_at` 확정. PENDING/ERROR 는 5분 주기 DB 기반 재시도 | `memory.LetterModerationService` |
| Place.vibe_avg·category 갱신 | P = 평균 × n/(n+2), 카테고리 다수결(NAVER 우선). PRIVATE 는 즉시, LETTER 는 승인 시 | `memory.PlaceProfileService`, `place.Place#updateProfile` |
| 취향 설정(카드 2~4장 + 자연어 + 카테고리 필터 + 기준 위치) | `POST/GET/PUT/PATCH/DELETE /v1/preferences` | `notification.Preference*` |
| 자동 알림 1차(벡터) | `MemoryPublishedEvent`(커밋 후) → 반경(1km, 3개 미만이면 3km)·카테고리 필터 → `s=(cos+1)/2`, `score=0.85s+0.15·거리` | `notification.MatchingService`, `MatchScorer` |
| 자동 알림 2차(`/ai/verify`) | 0.65 ≤ s < 0.85 이고 취향 문장이 있을 때만 `PreferenceVerifyPort`. 실패/문장 없음 = 탈락 | `memory.ai.HttpPreferenceVerifyAdapter` |
| 알림 문구(`/ai/match-reason`) | 1차 통과 시 `MatchReasonPort`, 실패 시 템플릿 문구 | `memory.ai.HttpMatchReasonAdapter` |
| 알림 조회·읽음 | `GET /v1/notifications`, `GET /v1/notifications/unread-count`, `PATCH /v1/notifications/{id}/read` | `notification.Notification*` |

## 2. 기획과 다르게 구현한 점(이유)

| 기획 | 구현 | 이유 |
|---|---|---|
| `vibe: [-0.9, -0.6, ...]` 실수 벡터, `vector(4)` | 경험은 기존 ±1 정수 4컬럼 유지. 장소 평균·취향은 실수/정수 4컬럼(`vibe_*`) | 실제 AI 서버 계약(`AI_INTEGRATION.md` §3)이 ±1/null 이고 ERD 규칙(불변 규칙 1)이 최종 ±1. pgvector/hibernate-vector 의존은 이미 제거됨 |
| `Memory.tags text[]`, `Preference.cards text[]` | `jsonb` | Hibernate `ddl-auto: validate` 와 배열 타입 검증 위험 회피 |
| `Preference` 테이블 | 온보딩 취향 이력(`user_preference_versions`, 09:00 배달용)과 **별개 자원** `preferences`(사용자당 여러 개) | 두 기능이 공존: 09:00 하루 한 편 배달(BE2)은 그대로, 카드 기반 실시간 알림은 추가 |
| `confidence < 0.6 이면 기타` | analyze **제안**에서만 OTHER 로 바꾸고 `CATEGORY_LOW_CONFIDENCE` 경고. 상류 **실패**(FAILED)는 절대 OTHER 로 채우지 않음 | 불변 규칙 2(실패 → OTHER 금지)와 기획 요구를 함께 만족. 하한은 `app.matching.category-confidence-threshold` |
| `/ai/verify`, `/ai/match-reason` | 포트·HTTP 어댑터·mock 은 준비됨. 실제 AI 서버에 경로가 아직 없으면 어댑터가 실패로 처리(verify → 탈락, reason → 템플릿) | AI 담당 구현 대기. 백엔드 기능은 막히지 않음 |
| PII 마스킹 본문 저장 | 토큰에 원문 해시 + 마스킹 해시를 둠. FE 가 `maskedContent` 를 저장 본문으로 제출하면 같은 토큰으로 인정하고 `pii_masked=true` | 토큰에 본문 원문을 싣지 않기 위함 |
| 반경 1km/3km 전환 | 취향 기준점 반경 안 "리뷰 있는 장소"가 3개 미만이면 3km | 기획 문구의 "3개 미만"을 후보 장소 수로 해석 |
| 알림 대상 리뷰 | 안전 승인된 LETTER 만 매칭 트리거. PRIVATE 는 장소 벡터 집계에는 포함, 알림 트리거 아님. LETTER_COPY 는 집계 제외 | 미승인/비공개 글로 타인에게 알림이 가지 않게. 사본은 같은 리뷰의 복제 |

## 3. 데이터 모델 추가(V8)

- `places` +`naver_title, naver_address, naver_category, category_code(FK place_categories.code), category_source(NAVER|REVIEWS), review_count, vibe_crowd/spatial/company/stay, vibe_updated_at`
- `memories` +`evidence jsonb, tags jsonb, category_pred, category_conf, safe, pii_masked, unsafe_reason`
- `preferences(id, user_id, cards jsonb, vibe_* smallint ∈{-1,0,1}, preference_text, category_filter jsonb, center_lat/lng, radius_m, active, created_at, updated_at)`
- `notifications(id, user_id, preference_id, place_id, memory_id, service_date, similarity, score, stage(1|2), reason, created_at, read_at)` — `UNIQUE(preference_id, place_id, service_date)` = 같은 장소 하루 1회

## 4. 설정 키

`app.matching.*`(비우면 기획값): `radius-meters=1000`, `fallback-radius-meters=3000`, `min-places-for-primary-radius=3`,
`notify-threshold=0.85`, `verify-threshold=0.65`, `similarity-weight=0.85`, `distance-weight=0.15`,
`place-vibe-smoothing=2`, `merge-distance-meters=20`, `category-confidence-threshold=0.6`, `reviews-for-verify=5`.
`app.moderation.retry-delay=PT5M`, `retry-initial-delay=PT1M`.

## 5. AI 서버에 요청하는 것(AI 담당)

1. `POST /ai/analyze` 요청에 선택 필드 `naver_category` 수용, 응답에 기획 §8 필드
   (`evidence`, `tags`, `category_confidence`, `category_source`, `masked_content`, `pii_found`, `safe`, `unsafe_reason`) 추가.
   백엔드는 없는 필드를 null 로 처리하므로 점진 추가 가능. 축은 계속 ±1/null.
2. `POST /ai/verify` `{preference_text, reviews[]}` → `{fit, confidence, reason, evidence[]}`.
3. `POST /ai/match-reason` `{cards[], place_name, reviews[]}` → `{reason}`.

## 6. 로컬 end-to-end 확인 절차(mock AI)

```bash
./gradlew bootRun                                    # local 프로필, V8 자동 적용
# 1) 로그인(시드 계정) → 토큰
# 2) POST /v1/preferences  (수신자: 카드 ["조용한","혼자 가기 좋은"], 기준점 = 우편함)
# 3) POST /v1/memories/analyze (작성자) → analysisToken
# 4) POST /v1/memories type=LETTER + analysisToken → 201, moderation APPROVED(mock), place vibe 갱신
# 5) GET /v1/notifications (수신자) → stage 1 알림 1건, reason 문구
```

## 7. 검증 중 발견·수정한 결함

장소 벡터(`Place.vibe_*`/`VibeVector`)는 경험 1건(±1 정수)과 달리 -1~1 **실수**다(§2 표). 이 실수 처리가
엔티티(`Place`)와 엔드포인트(`GET /v1/places` → `PlaceResponse`) 전 구간(경계·소수 포함)에서 온전한지
회귀 테스트로 검증하는 과정에서 결함을 하나 발견해 수정했다.

- **증상**: `VibeVector.isZero()`가 JavaBean 접근자 명명 규칙(`isXxx`)을 따르는 바람에 Jackson이 이를
  `"zero"`라는 JSON 프로퍼티로 오인해, `GET /v1/places` 응답의 `vibe` 객체에 API 계약에 없는
  `"zero": false` 필드가 함께 나갔다. 순수 단위 테스트(`VibeVectorTest`)만으로는 드러나지 않고,
  앱과 동일한 `StrictJson` mapper로 `PlaceResponse`를 직렬화→역직렬화 왕복시키는 테스트에서
  `UnrecognizedPropertyException`으로 발각됐다(`FAIL_ON_UNKNOWN_PROPERTIES` 정책 때문에 되읽기가 막힘).
- **수정**: `VibeVector.isZero()`에 `@JsonIgnore` 추가. 값·검증 로직 변경 없음, 응답에서 의도치 않은
  필드만 사라진다.
- **추가한 테스트**:
  - `VibeVectorTest`: -1~1 경계·소수 스윕(`-1.0, -0.87654321, -0.333333, 0.0001, 1.0` 등) 허용 확인,
    경계 바로 밖(`±1.0000001`)·`NaN` 거부 확인
  - `place.PlaceTest`(신규): `Place.updateProfile()`로 전 구간 실수를 넣고 `place.vibe()`가 정밀도
    손실 없이 그대로 돌아오는지 확인
  - `place.dto.PlaceResponseTest`(신규): 위 결함을 실제로 잡아낸 JSON 왕복 테스트
