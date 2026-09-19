# Backend — 감정지도 (사사삭)

> 장소 취향에 맞춰 도착한 익명의 경험 한 편을 읽고, 마음에 드는 내용을 나만의 기록으로 간직한다.

감정지도는 관심 지역의 경험을 취향에 맞춰 하루 한 편 받아보는 장소 기반 서비스입니다. 이 저장소는 백엔드 코드와 FE·BE·AI가 함께 검토할 기획·설계·API 계약을 관리합니다.

**현재는 최신 ERD 영속성 모델과 일부 기존 호출부를 전환한 단계입니다.** UUID·필수 연속 4축·취향 이력·수신/좋아요·이미지 소유권을 사용하며 업무 API는 모두 `/v1/...`입니다. 이미지 업로드에는 영속 요청 소유권·재생과 파일 수명 경계가 추가됐지만, 정기 배달·실제 AI provider·AI 분류/안전 검사·동률 해소 등 전체 MVP 구현 완료를 뜻하지 않습니다. 실행 시 생성되는 Swagger가 실제 제공 API의 기준입니다.

## 문서 안내

| 문서 | 버전·역할 |
| --- | --- |
| [MVP 기획서](./docs/MVP_PLAN.md) | `1.4-final` — 제품 정책, 범위, 사용자 흐름과 수용 기준 |
| [ERD 및 데이터베이스 설계서](./docs/ERD.md) | `ERD 1.0` — 관계·컬럼·제약·트랜잭션의 구현 제안 |
| [API 명세서](./docs/API_SPEC.md) | `API 0.2.0-draft` — FE·BE 요청·응답·오류·권한 계약 초안 |
| [OpenAPI](./docs/openapi.yaml) | OpenAPI `3.1.0`, API `0.2.0-draft` — 기계가 읽는 계약 초안 |
| [현재 코드의 아키텍처](./ARCHITECTURE.md) | 패키지 소유권, 현재 스택·실행 환경, 개발 규칙 |
| [개발 가이드](./CLAUDE.md) | 저장소 작업 지침 |

문서 기준일은 **2026-09-19**입니다. 로그인은 **이메일·비밀번호 전용**이며 ERD에도 자격증명 보완안을 반영했습니다. 제품 계약과 이번 영속성 전환의 구현 범위를 구분합니다. 문서의 ‘최종’이나 OpenAPI 수록 여부가 전체 구현·연동 검증 완료를 뜻하지는 않습니다.

## MVP 핵심 정책

운영 범위는 **단일 시연 지역·사전 초대 참여자**, 개발 대상은 FE 2명·BE 2명·AI 1명의 19시간 MVP입니다.

| 영역 | 제품 정책 |
| --- | --- |
| 계정 설정 | 수신 기준 위치 1개와 서비스 고정 반경. 위치·반경은 변경 불가, 분위기 4축은 모두 필수, 자연어 취향은 선택 |
| 경험 작성 | 네이버 지도 핀, 필수 본문, 선택 JPG/PNG 사진 0~1장. 작성자가 AI 제안을 확인·보완해 4축과 카테고리를 확정 |
| 분류 | 각 축은 유한 binary64 `[-1, 1]`; `-1/+1`은 endpoint 라벨 anchor이며 0과 중간값도 유효. 자체 장소 카테고리 8종 중 0~3개를 저장하며 미분류도 허용 |
| 매칭 | 승인된 정책은 `sum(1 - abs(p - m) / 2)`의 4축 동일 가중치(0~4)이나 daily selection 저장형·규칙 전환은 아직 별도 L06 작업이다 |
| 동률 해소 | 승인된 선형 점수에서 정확히 같은 computed binary64 점수의 최고 후보끼리만 선택 자연어 취향으로 비교. 실제 L06 구현/검증 전에는 완료로 주장하지 않음 |
| 정기 배달 | 매일 **09:00, Asia/Seoul**에 서버가 실행. 미접속이어도 적격 후보가 있으면 1개, 없으면 0개 |
| 후보·재시도 | 온보딩 이후 적격해진 미수신 LETTER는 다음 날에도 후보로 유지. 본인 글·이미 받은 원문·반경 밖·비공개·숨김·안전 미승인은 제외 |
| 추가 배달 제한 | 정시 이후 가입·새 후보에 대한 당일 보충, 배달 성공 후 원문 삭제에 대한 대체, 지난 날짜분 소급 배달 없음. 당일 미완료 오류만 재시도 |
| 수신함 | 실제 받은 편지의 누적 조회·수동 갱신. 분위기 조건 내부 OR, 카테고리 조건 내부 OR, 두 종류 사이 AND |
| 좋아요·보관 | 취소 없는 일회성 동작으로 새 PRIVATE 경험과 독립 이미지 생성. 원문과 지속적인 연결을 남기지 않으며 원문 일반 삭제 후에도 보존 |
| 익명·접근 | LETTER는 작성자와 실제 수신자만, PRIVATE는 소유자만 열람. 수신자에게 작성자 신원 비노출, 작성자에게 누적 반응 수만 제공 |

### 분위기 4축

`-1/+1`은 좋고 나쁨이 아니라 양쪽 endpoint 라벨입니다. 최종 저장에는 네 축이 모두 필요하며 값은 유한 binary64 `[-1, 1]`이고 `-0`은 `+0`으로 정규화합니다. 0은 알려진 정상 값이며 AI가 판단하지 못한 축만 null로 응답하고 작성자가 직접 보완합니다. slider의 수동 0.1 step과 표시 정밀도는 backend 양자화 규칙이 아니므로 기존/AI 값은 바꾸지 않습니다.

| 축 코드 제안 | -1 | +1 |
| --- | --- | --- |
| `CROWD_LEVEL` | 조용한 | 북적이는 |
| `SPATIAL_FEEL` | 아늑한 | 탁 트인 |
| `COMPANY_FIT` | 혼자 가기 좋은 | 함께 가기 좋은 |
| `STAY_STYLE` | 오래 머물기 좋은 | 잠깐 들르기 좋은 |

자체 카테고리는 **카페 / 음식점 / 술집 / 공원·산책 / 문화 / 공부·작업 공간 / 쇼핑 / 기타**입니다. 네이버 공식 업종 분류가 아니며, 카테고리는 수신함 조회 필터이지 매칭 가산점이 아닙니다.

### 경험 종류와 범위

- **LETTER**: 일일 선정으로 실제 수신자가 된 사용자에게 익명으로 전달하는 경험입니다.
- **PRIVATE**: 직접 작성하거나 LETTER에서 독립 복사한 개인 기록입니다. 열람·삭제만 제공하며 편집·복사본 재공유는 제외합니다.
- **BOOKMARK**: 본인이 소유한 PRIVATE 목록입니다. 별도 배포 유형이나 원문 연결 테이블이 아닙니다.
- **후순위·제외**: PUBLIC·댓글은 핵심 완료 후 후순위입니다. PUBLIC 정기 재추천, 채팅·답장, 푸시·자동 폴링, 다중 편지함은 이번 MVP 범위가 아닙니다.

직접 LETTER·PRIVATE는 외부 AI 분류 대상이라는 안내가 필요합니다. PRIVATE가 다른 사용자에게 비공개라는 사실은 외부 AI 전송이 없다는 뜻이 아닙니다. LETTER_COPY는 저장된 분류를 복사하고 AI를 다시 호출하지 않습니다.

## 데이터 설계 방향

[ERD](./docs/ERD.md)의 **10개 기본 도메인 테이블**에 `image_uploads`와 `image_storage_binding`을 더한 스키마를 사용합니다. 모든 경험·계정 식별자는 UUID이며 카테고리는 고정 smallint, 일부 관계는 복합키입니다.

| 책임 | 테이블 |
| --- | --- |
| 계정·인증 | `app_users`, `email_password_credentials` |
| 불변 취향 이력 | `user_preference_versions` |
| 장소·경험 | `places`, `memories` |
| 자체 분류 | `place_categories`, `memory_categories` |
| 일일 작업·실제 수신 | `daily_selections`, `letter_deliveries` |
| 신고·운영 처리 | `reports` |
| 업로드 소유권·첨부 | `image_uploads` |
| 이미지 root 소유권 | `image_storage_binding` |

- UUID 식별자와 필수 연속 4축 컬럼을 사용하고, V10부터 새 write는 `axis_definition_version=2`와 유한 `double precision [-1,1]`을 저장합니다. v1 역사 행은 endpoint 값·버전·identity를 재작성하지 않으며 수치상 no-op PATCH도 보존합니다. 취향은 덮어쓰기 대신 불변 버전으로 보존합니다.
- 일일 작업과 실제 배달을 분리해 후보 없음·실패·성공을 구분합니다. 사용자·날짜별 작업, 날짜별 배달, 같은 원문의 재수신 금지는 각각 별도 제약입니다.
- 좋아요는 `letter_deliveries.liked_at`으로 관리합니다. 별도 Reaction·Bookmark·원문-사본 연결 테이블은 없습니다.
- 일반 삭제는 소프트 삭제이며 FK는 `RESTRICT`입니다. 원문 삭제가 수신 이력이나 독립 PRIVATE를 함께 삭제하지 않습니다.
- 자격증명은 계정과 분리하고 신고에는 `details`를 저장합니다. 이미지 업로드의 요청 키는 소유자·경로·UUID 키·요청 지문으로 영속 조정하며 응답 본문을 캐시하지 않습니다. 메모리·신고의 전체 재생 계약은 이 범위에서 완료로 주장하지 않습니다.

**기존 데이터 자동 이관은 지원하지 않습니다.** V1은 그대로 유지하고 V2에서 구형 6개 테이블이 모두 빈 경우에만 전환합니다. 데이터가 있으면 삭제하지 않고 마이그레이션을 중단합니다. 별도 빈 개발 DB를 준비하세요. V3는 자체 카테고리 8종을 초기화합니다.

## API 계약 초안

[API 명세서](./docs/API_SPEC.md)와 [OpenAPI YAML](./docs/openapi.yaml)에 참여자용 **22개 API 작업**이 정의되어 있습니다. 다음 목록은 구현 완료 목록이 아닙니다.

| 영역 | 메서드·경로 |
| --- | --- |
| 로그인 | `POST /v1/auth/login` |
| 서비스 설정·사전 | `GET /v1/config`, `GET /v1/atmosphere-axes`, `GET /v1/place-categories` |
| 계정·취향 | `GET /v1/users/me`, `POST /v1/users/me/onboarding`, `PATCH /v1/users/me/preferences` |
| 경험 분석·생성 | `POST /v1/memories/analyze`, `POST /v1/memories` |
| 경험 조회·삭제 | `GET /v1/memories/{id}`, `DELETE /v1/memories/{id}`, `GET /v1/users/me/memories` |
| 이미지 | `POST /v1/images`, `GET /v1/memories/{id}/image` |
| 편지 | `GET /v1/letters/today`, `GET /v1/letters`, `PATCH /v1/letters/{deliveryId}/read`, `POST /v1/letters/{deliveryId}/like` |
| 개인 보관함 | `GET /v1/bookmarks` |
| 지도 | `GET /v1/places`, `GET /v1/places/{id}/memories` |
| 신고 | `POST /v1/reports` |

호출 형식은 `https://{domain 또는 IP}/v1/{endpoint}`입니다. `API_BASE_URL`에는 origin만 두고 명세의 경로를 붙입니다. 무버전·`/api/...` 호환 경로는 제공하지 않습니다. Swagger·OpenAPI JSON·Actuator는 업무 API와 별도의 문서·운영 경로입니다.

주요 연동 계약은 다음과 같습니다. 로그인 방식 외의 세부 구현 제안은 팀 검토가 필요합니다.

- 로그인은 이메일·비밀번호만 지원합니다. 공개 회원가입·비밀번호 재설정·소셜 로그인은 제공하지 않습니다. 최초 USER 자격증명은 [내부 계정 공급 CLI](./ARCHITECTURE.md#71-최초-계정-공급)로 생성하며, 실제 참여자 승인과 개인별 비밀 전달은 별도 운영 절차입니다.
- 로그인만 무인증이며 다른 참여자 API는 Bearer 토큰과 ACTIVE 초대 계정을 요구합니다. 온보딩 전 허용 경로는 명세서에서 별도로 정의합니다.
- 식별자는 UUID 문자열, `preferenceVersion`은 10진 문자열입니다. 목록 응답은 `{items, pageInfo}`, 공통 오류는 `code`를 기준으로 처리합니다.
- 이미지 업로드는 단 하나의 UUID `Idempotency-Key`를 요구합니다. 같은 소유자·동일 원본 바이트의 완료 요청은 저장된 영수증을 재생하고, 새 저장은 `201`, 재생은 `200`입니다. 다른 경로·다른 원본·다른 사용자의 일반 멱등성 계약을 이 설명으로 확대하지 않습니다.
- 좋아요는 범용 응답 캐시와 구분합니다. 최초 성공에서만 사본 ID를 반환하고, 중복은 `200 ALREADY_COPIED`와 사본 ID `null`로 응답하는 안입니다.
- 이미지는 선행 업로드한 `imageId`를 경험에 첨부합니다. 이미지 영수증은 STAGED의 미만료 상태와 ATTACHED를 재생할 수 있고, 만료된 STAGED/EXPIRED는 `410 IMAGE_UPLOAD_EXPIRED`입니다. 경험 조회는 Bearer 인증·경험 접근 검사를 먼저 통과한 뒤 바이너리 스트림을 받으며 토큰·storage key를 URL에 넣지 않습니다.

반경·토큰 TTL·입력 길이·파일 크기·페이지 제한 등의 **예시 숫자는 운영 확정값이 아닙니다.** 인증 저장·제한, AI 모델·서명 계약, 파일 수명·실패 복구, 운영 도구는 [API 명세서](./docs/API_SPEC.md)의 합의 항목을 확인합니다.

## 현재 구현 범위

| 영역 | 이번 영속성 전환에 포함 |
| --- | --- |
| 모델·저장소 | UUID, 필수 4축, 불변 취향 버전, 카테고리 복합키, 일일 슬롯·수신·신고 모델 |
| 계정 | 이메일·비밀번호 로그인, DB 영속 로그인 제한, 안전한 내부 USER 공급 CLI, ACTIVE 계정·온보딩 Guard, 위치 최초 설정, 취향 버전 변경 |
| 경험·장소 | 수동 4축·카테고리 저장, 신규 핀/가시 핀 재사용, 권한 기반 조회, 소프트 삭제 |
| 수신·좋아요 | 기존 수신 목록·최초 읽음, 독립 PRIVATE·분류·파일 복사와 일회성 좋아요 |
| 이미지·신고 | 이미지 단일 multipart·영속 요청 키/영수증 재생, 본인 임시 업로드 첨부, 접근 검사 뒤의 경험 이미지 스트림, 열람 가능한 경험 신고 |

정기 후보 선정·배달 스케줄러, AI 분류·안전 승인·자연어 동률 평가, Today/BOOKMARK 전용 API, 전체 커서·필터·공통 오류·메모리·신고의 전체 요청 멱등성·공개 운영 도구는 아직 완성하지 않았습니다. 전체 API_SPEC의 응답 필드·목록 포맷까지 완성한 단계는 아닙니다. 서비스 설정·고정 사전과 영속 로그인 제한은 별도 구현됐으며 운영 수치는 환경별 승인·주입이 필요합니다.

직접 생성은 명시적 수동 입력만 지원하며 분류 상태는 `NOT_RUN`, 출처는 `USER`입니다. 안전 검사는 `PENDING`, `available_at`은 NULL이므로 새 LETTER를 자동 승인·배달하지 않습니다. 유효성을 확인할 AI 어댑터가 없는 `analysisToken`은 거절합니다.

공개 회원가입과 위치 변경 API는 제거했습니다. 개발 로그인도 명시적 DB·스키마·해시 비용을 받는 내부 CLI로 만든 계정이 필요합니다. CLI는 비밀번호를 숨김 콘솔 또는 승인된 stdin으로만 받고 기존 자격증명을 재설정하지 않으며 역할은 USER로 고정합니다. ACTIVE/PENDING은 명시하되 위치·취향·온보딩 이력을 자동 생성하지 않습니다. 공개 가입을 대신하는 우회 API는 제공하지 않습니다.

Hibernate 벡터 매핑·임베딩 의존성은 제거했습니다. 단, 변경하지 않은 **V1을 처음 실행할 때는 pgvector 확장이 여전히 필요**합니다.

## 현재 기술 스택과 구조

| 구분 | 현재 저장소 |
| --- | --- |
| 언어·런타임 | Java 21 LTS |
| 프레임워크·빌드 | Spring Boot 4.1.0, Gradle Wrapper 9.0.0, Kotlin DSL |
| DB·영속성 | PostgreSQL 17, Spring Data JPA, Flyway. 과거 V1 실행에만 pgvector 필요 |
| 인증 | Spring Security, JWT(jjwt), BCrypt |
| 문서·테스트 | springdoc-openapi, JUnit, ArchUnit |
| 보일러플레이트 | Lombok, DTO는 Java record |

단일 Gradle 모듈 안에서 기능별로 Entity·Repository·Service·Controller·DTO를 묶습니다.

```text
src/main/java/team4/emotionmap/
├── account/           # 계정·이메일 자격증명·불변 취향
├── memory/            # 경험·분류 연결·접근 정책
├── place/             # 장소·자체 카테고리 사전
├── letter/            # 일일 슬롯·수신·읽음·좋아요
├── report/            # 신고·처리 상태
├── media/             # 업로드 메타데이터·파일 저장
└── platform/          # 보안·OpenAPI 설정
```

ArchUnit으로 모듈 간 내부 타입 접근과 순환 의존성을 검사합니다. 공개 계약과 패키지 소유권은 [아키텍처 문서](./ARCHITECTURE.md)를 따릅니다.

## 빠른 시작 — 현재 기반 코드 실행

Java 21과 로컬 PostgreSQL 17을 준비합니다. Docker는 사용하지 않으며, pgvector가 PostgreSQL 17에서 사용 가능한 상태여야 합니다. 아래 계정·비밀번호는 현재 `local` 프로필에 맞춘 **로컬 개발 전용 예시**입니다.

```bash
# macOS 예시: 최초 설치·기동
brew install postgresql@17 pgvector
brew services start postgresql@17

# psql은 PostgreSQL 관리자 권한으로 실행
psql -d postgres -c "CREATE USER emotionmap WITH PASSWORD 'emotionmap';"
psql -d postgres -c "CREATE DATABASE emotionmap OWNER emotionmap;"
psql -d emotionmap -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 저장소 루트에서 실행
./gradlew bootRun     # 기본 local 프로필, 실제 DB 필요
./gradlew test        # DB 없는 단위·모듈 경계 테스트
./gradlew build       # 테스트와 실행 가능한 JAR 빌드
```

DB와 계정이 이미 있다면 생성 명령을 반복하지 않습니다. `psql`이 PATH에 없다면 설치한 PostgreSQL 17의 `bin` 경로를 사용합니다.

**개발 시드 자동 실행 중단:** 기본 `local` 부팅도 공통 `classpath:db/migration`만 사용합니다. `db/seed/R__seed_dev_data.sql`은 원본 보존용이며 현재 실행 절차가 아닙니다(파일 내부의 local 자동 실행 설명도 과거 기준입니다). 공통 비밀번호·운영자 계정 생성, 기존 데이터 삭제 및 현행 제약과 맞지 않는 값을 포함하므로 직접 실행하거나 Flyway 경로에 다시 추가하지 마세요. 새 환경에는 V3의 고정 카테고리 8개만 초기화되며, 로그인 계정·자격증명은 별도의 안전한 내부 절차로 준비해야 합니다.

이미 이 repeatable의 적용 이력이 있는 DB는 현재 Flyway 기본 검증에서 스크립트 누락으로 부팅이 중단될 수 있습니다. 이 변경은 기존 데이터·적용 이력을 삭제하거나 자동 복구하지 않습니다. 오류를 우회하려고 `repair`/`clean`/reset/버전 down, 이력 삭제, 검증 비활성화·누락 무시 설정 또는 시드 재실행을 하지 마세요. 기존 DB는 보존하고, 개발 재개에는 별도의 빈 개발 DB만 사용하세요. 이 변경은 DB 생성·초기화·정리 같은 자동 DB 작업을 수행하지 않습니다.

- DB 연결: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 기본 로컬 설정을 변경합니다.
- 프로필: `SPRING_PROFILES_ACTIVE=local|ci|prod`. `ci`는 DB 없는 테스트용이지 독립적인 앱 부팅용이 아닙니다.
- JWT·서명 비밀은 `JWT_SECRET`·`SIGNING_SECRET`, 업로드 root는 `APP_UPLOAD_DIR`로 설정합니다. 이미지 요청 lease는 양수 ISO-8601 `Duration`인 `REQUEST_COORDINATION_LEASE_DURATION`, 정리 주기는 양수 ISO-8601 `Duration`인 `IMAGE_CLEANUP_INTERVAL`, 정리 배치는 양의 정수 `IMAGE_CLEANUP_BATCH_SIZE`로 각각 명시합니다. 이 값들에는 운영 기본값이 없으며 누락·무효면 새 이미지 쓰기/새 요청 선점 또는 정리가 보존 방향으로 닫힙니다. prod의 두 비밀은 서로 다른 32바이트 이상 값이어야 하며 개발용 기본값을 거부합니다.
- 토큰 TTL은 `SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS` 하나로 설정합니다. 브라우저 origin은 `CORS_ALLOWED_ORIGINS`의 정확한 allowlist로 지정하며 운영 FE 주소를 추측해 허용하지 않습니다.
- 지도 cursor TTL은 `CURSOR_TTL`로 명시한다. 양의 Spring `Duration`만 허용하고 bare numeral 단위는 초이며 운영 기본값은 없다. 누락·blank·0·음수 또는 expiry 계산 overflow면 구현된 `GET /v1/places` 요청만 `CONFIGURATION_UNAVAILABLE`(503)으로 fail-closed 된다. 첫 page의 expiry는 epoch second로 올림되어 TTL보다 일찍 만료하지 않고(추가 시간 1초 미만), continuation은 그 최초 expiry를 재사용한다. 이 설정이 다른 목록 pagination이나 auth/analysis/image TTL에 적용된다고 가정하지 않는다.
- prod는 실제 AI provider와 분석·안전 검사·동률 평가 구현이 없으면 기동을 거부합니다. 현재 mock 구현만으로 운영 준비가 완료되었다고 판단하지 않습니다.
- 실행 후 [Swagger UI](http://localhost:8080/swagger-ui.html)와 [현재 OpenAPI JSON](http://localhost:8080/v3/api-docs)을 확인할 수 있습니다. 최신 계약 YAML이 자동으로 서버에 적용되는 것은 아닙니다.

### 이미지 저장소 초기화와 복구 경계

일반 부팅은 `image_storage_binding`이 UNBOUND이면 root를 만들거나 claim하지 않으며 파일 생성·독립 복사·만료·정리를 허용하지 않습니다. 권한을 확인한 이미지 읽기도 binding·marker·현재 DB/schema locator가 일치하지 않으면 파일을 열지 않고 `503 IMAGE_FILE_UNAVAILABLE`로 닫습니다.

새 빈 dataset과 새 전용 빈 root만 다음 내부 subcommand로 명시적으로 연결합니다. 먼저 현재 Flyway를 **승인한 대상 DB**에 적용하고, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA`를 명시합니다. `--expected-database`와 `--expected-schema`는 그 실제 연결의 DB·schema와 일치해야 하며, `--expected-dataset-id`는 V9이 만든 UUID입니다.

```bash
java -jar build/libs/emotion-map-0.0.1-SNAPSHOT.jar storage-init-empty \
  --root /approved/new-empty-image-root \
  --expected-dataset-id <v9-dataset-uuid> \
  --expected-database <database-name> \
  --expected-schema <schema-name>
```

이 명령은 이미지 업로드 행과 모든 `memories.image_path` 참조가 없고, root에 이 시도가 만든 lock 파일 외 다른 항목이 없을 때만 marker와 DB binding을 기록합니다. 종료 코드는 성공 `0`, 잘못된 인자 또는 안전 전제 거절 `2`, DB·파일 의존성 실패 `1`입니다. 기존 root·legacy 데이터·복원본은 자동 adopt·삭제·reset하지 않습니다. 이미지가 있는 dataset의 복구/재결합은 DB와 파일 백업의 연관성을 보존하는 별도 승인 절차이며 이 CLI 대상이 아닙니다. clone은 copied dataset UUID만으로 root를 쓸 수 없고, locator가 달라지면 거절됩니다. locator까지 동일한 in-place physical restore는 코드만으로 일반 재시작과 구분할 수 없으므로 운영자가 writer/collector를 중지하고 백업 일관성을 관리해야 합니다.

DB 제약 회귀 검증은 전체 마이그레이션이 적용된 **별도 테스트 DB**에서 실행합니다. 합성 fixture는 트랜잭션 끝에 롤백됩니다.

```bash
psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/test/resources/db/erd_constraints.sql
```

## 협업·검증 원칙

- `main` 직접 push 대신 작업 브랜치와 PR을 사용합니다.
- DB 스키마는 [Flyway 마이그레이션](./src/main/resources/db/migration/)으로 관리합니다. 이미 적용된 파일은 수정하지 않고, 최신 번호를 확인해 새 마이그레이션을 추가합니다.
- CI에서는 DB 없는 테스트만 실행합니다. DB·동시성·파일·실제 API 연동은 별도 로컬 검증이 필요하며, 문서의 수용 시나리오를 실행 결과로 간주하지 않습니다.
- 코드·채팅·업로드에 고객 개인정보나 DB 덤프를 포함하지 않습니다. 개발·시연 데이터는 합성 또는 익명화하며, 시크릿은 환경변수로 주입하고 커밋하지 않습니다.
- 역할 분담과 공유 계약은 [`docs/plan/WORK_PLAN.md`](./docs/plan/WORK_PLAN.md), [`docs/plan/SHARED_CONTRACTS.md`](./docs/plan/SHARED_CONTRACTS.md)를 참고합니다. 원본 문서의 외부 첨부 SQL·mock·검증 보고서가 모두 이 저장소에 포함된 것은 아닙니다. 실제 스키마는 `src/main/resources/db/migration/`을 기준으로 합니다.
