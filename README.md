# Backend — 감정지도 (사사삭)

> 장소 취향에 맞춰 도착한 익명의 경험 한 편을 읽고, 마음에 드는 내용을 나만의 기록으로 간직한다.

감정지도는 관심 지역의 경험을 취향에 맞춰 하루 한 편 받아보는 장소 기반 서비스입니다. 이 저장소는 백엔드 코드와 FE·BE·AI가 함께 검토할 기획·설계·API 계약을 관리합니다.

**현재는 최신 ERD의 영속성 모델과 기존 API 호출부를 전환한 단계입니다.** UUID·필수 4축·취향 이력·수신/좋아요·이미지 소유권을 사용하며 업무 API는 모두 `/v1/...`입니다. 정기 배달·AI 등 전체 MVP 구현 완료를 뜻하지 않습니다. 실행 시 생성되는 Swagger가 실제 제공 API의 기준입니다.

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
| 분류 | 각 축은 `-1` 또는 `+1`. 자체 장소 카테고리 8종 중 0~3개를 저장하며 미분류도 허용 |
| 매칭 | 4축 동일 가중치, 일치당 1점으로 총 0~4점. 최소 점수 없이 적격 후보 중 최고점 선정 |
| 동률 해소 | 최고점 후보끼리만 선택 자연어 취향으로 비교. 설명 없음·평가 실패·최종 동률은 최고점 범위 안에서 무작위 해소 |
| 정기 배달 | 매일 **09:00, Asia/Seoul**에 서버가 실행. 미접속이어도 적격 후보가 있으면 1개, 없으면 0개 |
| 후보·재시도 | 온보딩 이후 적격해진 미수신 LETTER는 다음 날에도 후보로 유지. 본인 글·이미 받은 원문·반경 밖·비공개·숨김·안전 미승인은 제외 |
| 추가 배달 제한 | 정시 이후 가입·새 후보에 대한 당일 보충, 배달 성공 후 원문 삭제에 대한 대체, 지난 날짜분 소급 배달 없음. 당일 미완료 오류만 재시도 |
| 수신함 | 실제 받은 편지의 누적 조회·수동 갱신. 분위기 조건 내부 OR, 카테고리 조건 내부 OR, 두 종류 사이 AND |
| 좋아요·보관 | 취소 없는 일회성 동작으로 새 PRIVATE 경험과 독립 이미지 생성. 원문과 지속적인 연결을 남기지 않으며 원문 일반 삭제 후에도 보존 |
| 익명·접근 | LETTER는 작성자와 실제 수신자만, PRIVATE는 소유자만 열람. 수신자에게 작성자 신원 비노출, 작성자에게 누적 반응 수만 제공 |

### 분위기 4축

`-1/+1`은 좋고 나쁨이 아니라 경험·취향의 양쪽 선택값입니다. 최종 저장에는 네 축이 모두 필요하며, AI가 판단하지 못한 축은 작성자가 직접 보완합니다.

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

[ERD](./docs/ERD.md)의 **10개 기본 도메인 테이블**에 `image_uploads`를 추가한 11개 테이블을 사용합니다. 모든 경험·계정 식별자는 UUID이며 카테고리는 고정 smallint, 일부 관계는 복합키입니다.

| 책임 | 테이블 |
| --- | --- |
| 계정·인증 | `app_users`, `email_password_credentials` |
| 불변 취향 이력 | `user_preference_versions` |
| 장소·경험 | `places`, `memories` |
| 자체 분류 | `place_categories`, `memory_categories` |
| 일일 작업·실제 수신 | `daily_selections`, `letter_deliveries` |
| 신고·운영 처리 | `reports` |
| 업로드 소유권·첨부 | `image_uploads` |

- UUID 식별자와 필수 4축 컬럼을 사용하고, 취향은 덮어쓰기 대신 불변 버전으로 보존합니다.
- 일일 작업과 실제 배달을 분리해 후보 없음·실패·성공을 구분합니다. 사용자·날짜별 작업, 날짜별 배달, 같은 원문의 재수신 금지는 각각 별도 제약입니다.
- 좋아요는 `letter_deliveries.liked_at`으로 관리합니다. 별도 Reaction·Bookmark·원문-사본 연결 테이블은 없습니다.
- 일반 삭제는 소프트 삭제이며 FK는 `RESTRICT`입니다. 원문 삭제가 수신 이력이나 독립 PRIVATE를 함께 삭제하지 않습니다.
- 자격증명은 계정과 분리하고 신고에는 `details`를 저장합니다. 범용 요청 멱등성 저장소는 아직 구현하지 않았습니다.

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

- 로그인은 이메일·비밀번호만 지원합니다. 공개 회원가입·비밀번호 재설정·소셜 로그인은 이 초안 범위에 포함하지 않으며, 초대 계정의 초기 자격증명 공급 절차가 필요합니다.
- 로그인만 무인증이며 다른 참여자 API는 Bearer 토큰과 ACTIVE 초대 계정을 요구합니다. 온보딩 전 허용 경로는 명세서에서 별도로 정의합니다.
- 식별자는 UUID 문자열, `preferenceVersion`은 10진 문자열입니다. 목록 응답은 `{items, pageInfo}`, 공통 오류는 `code`를 기준으로 처리합니다.
- 직접 경험 생성·이미지 업로드·신고에는 `Idempotency-Key`, 취향 변경에는 `expectedPreferenceVersion`, 분석 결과 검증에는 `analysisToken`을 제안합니다.
- 좋아요는 범용 응답 캐시와 구분합니다. 최초 성공에서만 사본 ID를 반환하고, 중복은 `200 ALREADY_COPIED`와 사본 ID `null`로 응답하는 안입니다.
- 이미지는 선행 업로드한 `imageId`를 경험에 첨부합니다. 조회는 Bearer 인증으로 바이너리를 받아 Blob으로 표시하며 토큰을 URL에 넣지 않습니다.

반경·토큰 TTL·입력 길이·파일 크기·페이지 제한 등의 **예시 숫자는 운영 확정값이 아닙니다.** 인증 저장·제한, AI 모델·서명 계약, 파일 수명·실패 복구, 운영 도구는 [API 명세서](./docs/API_SPEC.md)의 합의 항목을 확인합니다.

## 현재 구현 범위

| 영역 | 이번 영속성 전환에 포함 |
| --- | --- |
| 모델·저장소 | UUID, 필수 4축, 불변 취향 버전, 카테고리 복합키, 일일 슬롯·수신·신고 모델 |
| 계정 | 이메일·비밀번호 로그인, ACTIVE 계정·온보딩 Guard, 위치 최초 설정, 취향 버전 변경 |
| 경험·장소 | 수동 4축·카테고리 저장, 신규 핀/가시 핀 재사용, 권한 기반 조회, 소프트 삭제 |
| 수신·좋아요 | 기존 수신 목록·최초 읽음, 독립 PRIVATE·분류·파일 복사와 일회성 좋아요 |
| 이미지·신고 | 본인 임시 업로드만 첨부, Bearer 경험 이미지 조회, 열람 가능한 경험 신고 |

정기 후보 선정·배달 스케줄러, AI 분류·안전 승인·자연어 동률 평가, Today/config/사전/BOOKMARK 전용 API, 전체 커서·필터·공통 오류·요청 멱등성·로그인 제한·운영 도구는 이번 전환에 포함하지 않습니다. 전체 API_SPEC의 응답 필드·목록 포맷까지 완성한 단계는 아닙니다.

직접 생성은 명시적 수동 입력만 지원하며 분류 상태는 `NOT_RUN`, 출처는 `USER`입니다. 안전 검사는 `PENDING`, `available_at`은 NULL이므로 새 LETTER를 자동 승인·배달하지 않습니다. 유효성을 확인할 AI 어댑터가 없는 `analysisToken`은 거절합니다.

공개 회원가입과 위치 변경 API는 제거했습니다. 개발 로그인에는 내부 절차로 만든 ACTIVE 계정과 BCrypt 자격증명이 필요합니다. 신규 Entity에 기본 설정만 넣고 공개 가입을 대신하는 우회 API는 제공하지 않습니다.

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

- DB 연결: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 기본 로컬 설정을 변경합니다.
- 프로필: `SPRING_PROFILES_ACTIVE=local|ci|prod`. `ci`는 DB 없는 테스트용이지 독립적인 앱 부팅용이 아닙니다.
- JWT·서명 비밀은 `JWT_SECRET`·`SIGNING_SECRET`, 업로드 경로는 `APP_UPLOAD_DIR`로 설정합니다. prod의 두 비밀은 서로 다른 32바이트 이상 값이어야 하며 개발용 기본값을 거부합니다.
- 토큰 TTL은 `SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS` 하나로 설정합니다. 브라우저 origin은 `CORS_ALLOWED_ORIGINS`의 정확한 allowlist로 지정하며 운영 FE 주소를 추측해 허용하지 않습니다.
- prod는 실제 AI provider와 분석·안전 검사·동률 평가 구현이 없으면 기동을 거부합니다. 현재 mock 구현만으로 운영 준비가 완료되었다고 판단하지 않습니다.
- 실행 후 [Swagger UI](http://localhost:8080/swagger-ui.html)와 [현재 OpenAPI JSON](http://localhost:8080/v3/api-docs)을 확인할 수 있습니다. 최신 계약 YAML이 자동으로 서버에 적용되는 것은 아닙니다.

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
