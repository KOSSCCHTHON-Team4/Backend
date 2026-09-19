# BE1 엔티티 비의존 우선 개발 — 조율 확인 목록 · 완료 보고

- 대응: `docs/prompts/BE1_PROMPT.md` "공통 사항과 인지사항" + "엔티티 비의존 우선 개발" 섹션
- 브랜치: `feat/be1-entity-independent-foundation` (main 직접 push 없음, PR 예정)
- 범위: BE1 우선 개발 자체는 엔티티 비의존 기능이다. 이후 `origin/main`의 UUID 영속성·`/v1` 전환(`bbe1256`)을 병합했으며, 해당 변경의 엔티티·Repository·마이그레이션도 현재 브랜치에 포함된다.

---

## 1. 착수 전 조율 확인 목록 (다른 백엔드 담당자와 겹칠 수 있는 지점)

아래는 BE1 이 **가정하고 진행한 결정**이다. 반대 의견이 있으면 코드 수정 전에 이 표에서 먼저 맞춘다.

| # | 지점 | BE1 가정(현재 코드) | 확인 요청 |
|---|---|---|---|
| 1 | 공통 패키지 위치 | WORK_PLAN §13.1 의 `contracts/` 를 `team4.emotionmap.contracts` 한 모듈로 두고, `common/` 은 만들지 않음. C01~C09 구현은 소유 모듈(`platform`, `catalog`, `memory.ai`, `media`)에 둔다 | `common/` 별도 패키지가 필요하면 지금 이름·소유 결정 |
| 2 | 모듈 의존 규칙 | 모든 모듈 → `contracts..` 허용, `contracts` 는 내부 무의존, `platform` 은 업무 모듈에 무의존. main의 동일 DB 트랜잭션용 명시 타입 allowlist 유지 | 신규 의존성은 `ModuleArchitectureTest`의 정확한 타입 목록으로 검토 |
| 3 | 사전·설정 API 소유 모듈 | `catalog` 모듈(A03). `/v1/config`·`/v1/atmosphere-axes`·`/v1/place-categories` | BE2 는 `ServiceConfigSource.limits()` 로 페이지 한도 읽기 |
| 4 | 사용자 식별자 타입 | 포트·JWT subject·인증 principal 모두 `UUID` | main의 UUID 영속성과 통합 |
| 5 | 오류 계약 | `ContractError` → `ApiError`. main의 `ResponseStatusException`은 알려진 코드·HTTP 상태를 보존하고, 그 외 사유는 안전한 공통 코드로 변환 | 새 코드 추가는 OpenAPI 와 같은 PR |
| 6 | 4축 JSON 검사 위치 | 공통 `contracts.dictionary.Atmospheres`는 토큰 수준 검사와 fieldErrors를 제공. main의 `account.dto.Atmospheres`는 자체 엄격 검증을 유지하며 감싸진 예외도 `422 INVALID_ATMOSPHERES`로 처리 | account 타입의 오류에는 공통 타입과 같은 축별 fieldErrors가 아직 없음 |
| 7 | 포트 시그니처 | §3 표대로 `contracts.account/memory/media/ai/signing` 에 선언. BE2 제공 포트(`MemoryAccessPolicy`, `DeliveryReadPort`, `IdempotencyExecutor`, `ServiceClock`, `DistancePolicy`)는 **BE2 가 같은 패키지 규칙으로 추가** | BE2 포트 추가 전 시그니처 초안 공유 요청 |
| 8 | 사본 moderation 상태 | `MemoryStore.insertIndependentCopy` 의 `moderation_status` 는 D09 운영 계약. 가짜 구현은 `NOT_REQUIRED` | D09 확정 시 실제 구현에 반영 |
| 9 | 서명 비밀 | `app.signing.secret`(`SIGNING_SECRET`) 를 JWT 비밀과 분리 | 배포 환경변수 목록(C10)에 추가 |
| 10 | Idempotency-Key 누락 코드 | `MissingRequestHeaderException` → `IDEMPOTENCY_KEY_REQUIRED`(400) 를 전역 핸들러가 처리. 키 저장·지문 비교는 BE2 `IdempotencyExecutor` | BE2 가 헤더 값 UUID 형식 검사 위치(핸들러 vs executor) 결정 |
| 11 | baseline 전환 | main의 공개 signup·기존 emotion/embedding 모델 제거를 유지. 공통 설정에서도 `app.embedding` 제거. 서비스 설정·서명·AI mock 설정은 유지 | 과거 V1의 pgvector 요구는 마이그레이션 불변 정책에 따라 유지 |
| 12 | 마이그레이션 번호 | 이번 단계 SQL 없음. 다음 V 번호는 엔티티 담당의 PR 이후 `git pull` 로 확인 | C05 취합 시점 조율 |

**D01~D10 중 이번 코드에 값이 필요한 항목:** D02(토큰 TTL·5회/60초), D03(반경·중심·한도 13종), D05(analysisToken TTL=`limits.analysisTtlSeconds`). 전부 `application.yml` 의 `app.service.*` 환경변수 키로 남겼고 코드 기본값은 없다. local 프로필만 API_SPEC 8.2 MOCK 값을 넣었다.

---

## 2. 완료 보고 (WORK_PLAN §13.2 양식)

### 작업 ID / 주 담당 / 상태
- C03 공통 DTO·오류·검증 / BE1 / **개별 테스트 및 main 예외 연동 테스트 완료**
- A03 사전·설정 API / BE1 / **`/v1` 전환 및 개별 테스트 완료** (실제 DB 연동 호출 미실행)
- C09 AI 호출 계약·어댑터 / BE1 / **인터페이스 합의 대기 + mock 완료**
- SignedValueCodec·analysisToken 순수 로직 / BE1 / **개별 테스트 완료**
- A06 파일 검증 순수 유틸 / BE1 / **개별 테스트 완료** (DB·LocalImageStore 구현은 다음 단계)
- 내부 포트 선언 6종 / BE1 / **시그니처 선언 + 가짜 구현 완료**

### 구현한 API·내부 기능
| 영역 | 위치 | 내용 |
|---|---|---|
| 오류 계약 | `contracts.error` | `ErrorCode`(46, HTTP·message 명세 1:1), `FieldError/FieldErrorReason`, `ApiError`(5키 항상 포함), `ContractError` |
| 페이지 | `contracts.page.PageInfo` | `hasMore ⇔ nextCursor!=null` 불변 |
| 사전 | `contracts.dictionary` | `AtmosphereAxis`(4축·라벨·컬럼명·version 1), `Atmospheres`(±1 강제), `AnalyzedAtmospheres`(null 허용), `PlaceCategoryCode`(8종·id·definition·version 1), `CategorySelection`(0~3·중복·미지 코드 → 422 INVALID_CATEGORIES) |
| 검증 | `contracts.validation` | `StrictValues`(정규 UUID·오프셋 ISO8601·좌표 범위), `TextRules`(본문 무정규화/공백 거절/코드포인트 길이, 선택 설명 trim→null, 비밀번호 무정규화) |
| 설정 계약 | `contracts.config` | `ServiceConfig/ServiceLimits/AuthConfig`(mode=EMAIL_PASSWORD, refresh=false 고정), `ServiceConfigSource` 포트 |
| 엄격 JSON | `platform.web.json` | Jackson 3: 중복 키·모르는 속성·`1.0`→int·`"1"`→숫자·`123`→문자열·primitive null·잉여 토큰 전부 거절. `AtmospheresJson` 토큰 수준 4축 검사(누락/0/소수/문자열/null/중복/미지 축 → 422 INVALID_ATMOSPHERES + fieldErrors) |
| 전역 오류 처리 | `platform.web` | `RequestIdFilter`(서버 생성 requestId, MDC, `X-Request-Id`), `ApiErrorWriter`(`Cache-Control: private, no-store`, 401 `WWW-Authenticate: Bearer`, `Retry-After`), `GlobalExceptionHandler`(JSON/검증/헤더/multipart/라우팅/500 매핑) |
| 보안 오류 형식 | `platform.security` | 401 을 `AUTH_REQUIRED/TOKEN_EXPIRED/INVALID_TOKEN` ApiError 로, 403(인가) 최후 방어선은 404. 전 응답 `Cache-Control: private, no-store`. `SecurityContextUserContext`(UUID principal 만 인증 인정) |
| 서명 코덱 | `platform.security.HmacSignedValueCodec` | HMAC-SHA256, 용도별 파생 키(ANALYSIS_TOKEN/PAGE_CURSOR 상호 재사용 불가), 사용자·버전·만료 검증, 오류 코드 용도별 분기 |
| A03 API | `catalog` | `GET /v1/config`(설정 누락 → 503 CONFIGURATION_UNAVAILABLE, 기본 반경 없음), `GET /v1/atmosphere-axes`, `GET /v1/place-categories` — 응답이 API_SPEC 예시 JSON 과 STRICT 일치. 이전 비버전 경로는 제공하지 않음 |
| C09 | `contracts.ai` + `memory.ai` | `AnalysisPort/ModerationPort/PreferenceTieBreakPort` + 결과 불변 조건(FAILED ⇒ 카테고리 0·OTHER 자동매핑 불가, 상태는 값에서 파생), `AiAdapterException`(우리 장애=503) vs 상류 실패=결과. mock 어댑터 3종(`app.ai.provider=mock`, 시나리오 마커 `[[AI_FAIL]] [[AI_PARTIAL]] [[AI_UNCLASSIFIED]] [[UNSAFE]] [[REVIEW]] [[MOD_ERROR]] [[TIE_FAIL]]`) |
| A04 순수 로직 | `memory.analysis` | `AnalysisReceipt`(본문 해시·제안·상태·provenance·만료, 원문 미포함), `AnalysisReceiptCodec`(INVALID/EXPIRED/CONTENT_MISMATCH 분기), `AxisSourceResolver`(AI/USER 출처를 서버가 비교로 판정, slot_no 1..n) |
| A06 순수 유틸 | `media.ImageSanitizer` | 매직 바이트 판정(확장자/MIME 무시) → 헤더 크기 선검사 → 디코딩 → 고정 색공간 재인코딩(EXIF/APP1·PNG tEXt 제거). 한도는 `ImageLimits`(= `/v1/config.limits`) 인자 |
| 포트 선언 | `contracts.account/memory/media` | `UserContext`, `AccountAccessReader`(+`requireActive/requireOnboarded`), `PreferenceHistoryReader.findAt/findLatest`, `MemoryStore`, `MemoryLifecyclePort`, `LocalImageStore`; 값 객체 `AccountAccess`, `PreferenceVersionSnapshot`, `MemorySnapshot`, `IndependentCopyCommand`(원문 ID 필드 없음), `StoredImageMeta`, `SanitizedImage` |
| 가짜 구현·fixture | `src/test/.../contracts/fakes`, `fixtures` | `FixedUserContext`, `InMemoryAccountAccessReader`, `InMemoryPreferenceHistoryReader`, `InMemoryMemoryStore`, `InMemoryLocalImageStore`, `DictionaryFixtures`, `src/test/resources/fixtures/*.json`(API_SPEC 8.2~8.4 예시 원문) |

### 변경한 migration·공통 계약
- BE1 자체 신규 migration 없음. main의 UUID 전환·카테고리 seed 마이그레이션을 수정 없이 병합.
- `ModuleArchitectureTest`: 모듈 목록에 `contracts`, `catalog` 추가, 의존 규칙 §1-2 대로.
- `application.yml`: `app.service.*`(환경변수 키만), `app.signing.secret`, `app.ai.*` 추가. `application-local.yml`: MOCK 값. `application-prod.yml`: 주석(SERVICE_*/SIGNING_SECRET 필수).
- `SecurityConfig`/`JwtAuthenticationFilter`: main의 UUID principal·현재 계정 상태/온보딩 Guard와 BE1의 ApiError·캐시 헤더·만료/위조 구분을 통합. 공개 업무 경로는 `POST /v1/auth/login`만 유지.

### 실행한 테스트와 결과
- main 병합 후 `SPRING_PROFILES_ACTIVE=ci ./gradlew build` → **BUILD SUCCESSFUL, 107 tests / 0 failed / 0 errors / 0 skipped** (DB 비연결).
- 명세 시나리오 대응: A05(401 형식·내부 정보 비노출), A07(축 누락·0·소수·문자열·null·중복 키), A09/A12(부분·전체 실패, FAILED 영수증 왕복), A10(본문 수정 → CONTENT_MISMATCH), A13(카테고리 4개·중복·미지 코드), A16(가짜 확장자·과대·픽셀 초과·EXIF/tEXt 제거), A41/A42(추가 필드·누락·타입 오류), A44(auth.mode 고정), §4.1(08:40 v1 → 10:00 v2 → 09:00 cutoff = v1), §4.4/§4.5(독립 사본에 원문 ID 없음, 원문 삭제 후 사본·파일 유지).
- 병합 회귀 검증: UUID·현재 권한 인증, 온보딩/삭제 계정 거부, 만료·잘못된 JWT·subject 없는 JWT, 로그인 stale Bearer 우회, 실제 account DTO의 감싸진 오류, main HTTP 예외 및 media 상태 예외의 공통 오류 변환, 사전 `/v1` 경로와 비버전 경로 미노출.

### 아직 실행하지 않은 테스트
- 앱 부팅(`bootRun`)·Swagger 확인 — 로컬 DB 필요.
- 실제 DB 계정과 Bearer 로 `/v1/config` 3종 호출. 인증 필터·Guard 오류 응답은 DB 비연결 테스트로만 검증.
- `Idempotency-Key` 값 형식·중복 처리(BE2 `IdempotencyExecutor` 연동 후).
- 실제 AI 어댑터(모델·프롬프트 합의 후).

### FE·AI·상대 BE 에 전달할 정보
- **FE**: 공통 오류 본문은 `{code,message,retryAfterSeconds,fieldErrors,requestId}`. 공통 4축 타입 위반은 `422 INVALID_ATMOSPHERES` + `atmospheres.<AXIS>` 필드 오류이며, main account 타입의 제한은 §1-6 참조. `X-Request-Id` 응답 헤더로 문의. `/v1/config` 는 설정 누락 시 503 이며 FE 가 1000m 를 추정하지 않는다.
- **AI**: `contracts.ai.*` 시그니처가 계약. 상류 실패는 예외가 아닌 `AnalysisResult.failed`/`ModerationVerdict.ERROR`. 카테고리는 `PlaceCategoryCode` 8종 코드만, 실패 시 OTHER 금지. 사전 fixture: `src/test/resources/fixtures/*.v1.json`, `DictionaryFixtures`.
- **BE2**: `ContractError` 만 던지면 형식이 맞는다. `ServiceConfigSource.limits()` 로 페이지 한도. `SignedValueCodec`(PAGE_CURSOR 용도) 로 커서 서명 → 실패는 `INVALID_CURSOR`. 포트 가짜 구현은 `contracts.fakes`. BE2 제공 포트는 `contracts` 규칙으로 추가하고 `ModuleArchitectureTest.MODULES` 갱신.

### 잔여 오류·임시 처리·운영 설정
- main의 자유문 `ResponseStatusException`은 원문을 공개하지 않고 공통 코드로 변환한다. 알려진 오류 코드·HTTP 상태 조합은 그대로 유지한다.
- `MockAnalysisAdapter` 의 키워드 규칙은 fixture 이며 품질 기준이 아니다. 실제 어댑터 도입 시 삭제.
- 운영 필수 환경변수: `JWT_SECRET`, `SIGNING_SECRET`, `SERVICE_*`(19개, `application.yml` 참조). 서비스 설정 누락 시 `/v1/config` 503.
- CORS 허용 origin·노출 헤더(`X-Request-Id`, `Retry-After`)는 FE origin 합의 후 설정값으로 추가(C02 잔여).
