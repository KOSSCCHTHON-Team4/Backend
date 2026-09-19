# 감정지도 백엔드 아키텍처

Java 21 · Spring Boot 4.1 · Gradle Wrapper 9.0 · PostgreSQL 17 · Spring Data JPA · Flyway

이 문서는 **현재 코드의 구조와 실행 방법**을 설명한다. 제품 요구는 [MVP_PLAN](docs/MVP_PLAN.md), 데이터 모델은 [ERD](docs/ERD.md), 전체 참여자 API 계약은 [API_SPEC](docs/API_SPEC.md)와 [OpenAPI](docs/openapi.yaml)를 참고한다. 최신 영속성 모델과 기존 호출부를 전환했지만 정기 배달·AI·전체 22개 API를 모두 구현한 것은 아니다.

## 1. 확정된 공통 스택

| 항목 | 현재 사용 |
| --- | --- |
| Java / Spring Boot | Java 21 toolchain / Spring Boot 4.1.0 |
| 빌드 | Gradle Wrapper 9.0.0, Kotlin DSL |
| DB | 로컬 PostgreSQL 17, Docker 미사용 |
| 영속성 | Spring Data JPA, Flyway, `ddl-auto: validate` |
| 인증 | Spring Security, jjwt 0.13.x, BCrypt |
| 문서 | springdoc-openapi 3.1.x |
| 테스트 | JUnit, ArchUnit, 별도 로컬 PostgreSQL 제약 검증 |
| 코드 | Lombok, 요청·응답 Java record |

현재 모델에는 벡터 컬럼이나 hibernate-vector 의존성이 없다. 다만 이미 배포된 마이그레이션 이력을 수정하지 않으므로 **V1 최초 실행에는 pgvector가 필요**하다. 확장을 임의로 제거하거나 V1의 체크섬을 바꾸지 않는다.

## 2. 저장소 구조 (백엔드 전용 레포)

프로젝트 루트가 Gradle 루트다. FE·AI는 별도 저장소이며, 이 저장소는 단일 Spring 애플리케이션·단일 DB를 사용한다.

| 모듈 | 소유 데이터·기능 |
| --- | --- |
| `account` | User, EmailPasswordCredential, UserPreferenceVersion, 로그인·온보딩·취향 |
| `memory` | Memory, MemoryCategory, 원문·사본·가시성·이미지 열람 |
| `place` | Place, PlaceCategory, 지도 핀·고정 분류 사전 |
| `letter` | DailySelection, LetterDelivery, 수신·읽음·좋아요 |
| `report` | Report, 신고 접수·처리 상태 |
| `media` | ImageUpload, 임시 업로드·로컬 파일 저장·독립 복사 |
| `platform` | JWT·접근 Guard 계약, JSON 파서, OpenAPI |

### 2-1. 모듈 경계와 의존성 규칙

- 각 기능 안에 Entity·Repository·Service·Controller·DTO를 배치한다. 계층별 최상위 패키지나 별도 마이크로서비스를 추가하지 않는다.
- URL과 코드 소유권은 다르다. `/v1/places/{id}/memories`와 `/v1/memories/{id}/image`는 기억 접근 정책을 사용하는 `memory`가 소유한다.
- UUID FK를 스칼라 필드로 매핑하고 실제 관계 무결성은 Flyway의 FK가 보장한다. 소유권이 다른 Entity 사이의 양방향 JPA 연관관계·삭제 CASCADE를 추가하지 않는다.
- 좋아요는 동일 DB 트랜잭션에서 Memory·분류·수신 기록을 함께 갱신한다. 이번 전환에서 필요한 Entity·Repository·서비스 타입만 `ModuleArchitectureTest.PUBLIC_CONTRACTS`에 명시적으로 허용한다. 패키지 전체 허용은 하지 않는다.
- `MemoryReadAccess`는 기억 모듈이 소유하고 수신 모듈이 구현한다. 기억 모듈이 수신 서비스에 역으로 의존하지 않는다.
- `AccountAccessGuard`는 platform이 소유하고 account가 구현한다. JWT 필터가 account Entity·Repository를 직접 가져오지 않는다.
- 지도 Repository는 공유 DB에 대한 가시성 EXISTS 조건으로 조회한다. `place → memory → place` Java 순환을 만들지 않는다.
- ArchUnit은 명시된 모듈 소속, 공개 계약 외 접근, 모듈 간 순환 의존을 검사한다.

## 3. 시스템 구성도

```mermaid
flowchart LR
    FE[클라이언트] -->|/v1 JSON·multipart| SEC[JWT·계정 Guard]
    SEC --> WEB[Controller]
    WEB --> SVC[Service 트랜잭션]
    SVC --> REPO[Spring Data Repository]
    REPO --> DB[(PostgreSQL)]
    SVC --> FILE[로컬 이미지 저장]
    FLY[Flyway] --> DB
```

이미지 파일은 파일시스템, 소유권·첨부 상태와 독립 경로는 DB에 둔다. DB 커밋과 파일 쓰기가 자동으로 원자적이라고 가정하지 않는다.

## 4. 데이터 모델

기본 도메인 10개와 업로드 보완 테이블 1개다.

- `app_users`: 초대 접근 상태·역할·온보딩 시 고정 위치.
- `email_password_credentials`: 계정 UUID PK/FK, 이메일·정규화 조회키·비밀번호 해시. 공급자 인증 모델은 사용하지 않는다.
- `user_preference_versions`: 불변 4축·설명·revision·effective_at. 같은 사용자의 cutoff 이전 최신 버전을 조회한다.
- `places`, `place_categories`: 내부 핀과 고정 8종 분류.
- `memories`: 필수 4축, 장소 스냅샷, 분류 출처·실행 상태, 공개 범위·기원·안전 상태·독립 이미지 경로.
- `memory_categories`: `(memory_id, category_id)` PK, 1~3 슬롯과 `(memory_id, slot_no)` UNIQUE.
- `daily_selections`: `(user_id, service_date)` PK, 사용자와 취향 버전의 복합 FK, cutoff·claim·lease·상태.
- `letter_deliveries`: `(receiver_id, service_date)`와 `(receiver_id, memory_id)`를 각각 UNIQUE로 유지. 최초 read_at·liked_at만 기록한다.
- `reports`: 사유 코드·details·처리 상태·담당자·처리 시각.
- `image_uploads`: 본인 소유 STAGED 이미지, 만료·ATTACHED 상태와 첨부 대상.

UUID ID, smallint 축·분류, `Instant`/timestamptz, `LocalDate`/date, 문자열 enum을 사용한다. FK는 RESTRICT이며 일반 삭제는 소프트 삭제다. Reaction·Bookmark·원문-사본 연결 테이블은 없다.

## 5. 프로필 전략

- `local` 기본: 실제 PostgreSQL 연결. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 변경한다.
- `ci`: DataSource/JPA/Flyway 자동구성 비활성화. DB 없는 테스트용이며 전체 앱 실행용이 아니다.
- `prod`: 시크릿은 환경변수로 공급한다. 개발용 DB 비밀번호와 JWT 기본값을 재사용하지 않는다.

## 6. CI / CD (DB 비연결)

`.github/workflows/ci.yml`은 DB 없는 테스트를 수행한다. 릴리스 워크플로는 `v*` 태그에서 JAR을 빌드한다. 실제 PostgreSQL·파일·동시성 검증은 별도 로컬 테스트 DB에서 실행한다. 고객 데이터·DB 덤프를 CI나 저장소에 업로드하지 않는다.

## 7. 로컬 준비 (Docker 미사용)

```bash
brew install postgresql@17 pgvector
brew services start postgresql@17

# 로컬 개발 전용 계정 예시. 관리자 권한으로 실행한다.
psql -d postgres -c "CREATE USER emotionmap WITH PASSWORD 'emotionmap';"
psql -d postgres -c "CREATE DATABASE emotionmap OWNER emotionmap;"
psql -d emotionmap -c "CREATE EXTENSION IF NOT EXISTS vector;"

./gradlew bootRun
./gradlew test
./gradlew build
```

`psql`이 PATH에 없다면 PostgreSQL 17의 bin 경로를 사용한다. 기존 계정·DB에 생성 명령을 반복하지 않는다. Ubuntu/Debian에서는 해당 배포판의 PostgreSQL 17·pgvector 패키지를 준비한다.

로그인용 앱 계정은 DB 접속 계정과 별개다. 공개 회원가입 API는 없다. 내부 운영 절차에서 ACTIVE `app_users`와 BCrypt 해시를 가진 `email_password_credentials`를 준비해야 한다. 이메일 조회키는 `EmailPasswordCredential.normalizeEmailLookupKey`와 동일하게 생성하고, 원문 비밀번호·공용 계정 시크릿을 SQL·Git·로그에 넣지 않는다. 계정 자동 seed는 제공하지 않는다.

## 8. 팀 합의 규칙

- 로컬 PostgreSQL 사용, Docker 미사용.
- 실제 데이터·DB 덤프·고객 개인정보·시크릿 커밋 금지. Flyway DDL과 합성 테스트 fixture는 코드로 관리한다.
- 이미 확정된 제품/API 계약은 재설계하지 않는다. 미정 운영 한도는 설정으로 관리하며 개발 기본값을 운영 확정값으로 취급하지 않는다.
- 각 모듈의 주 담당자는 [공통 계약서](docs/plan/SHARED_CONTRACTS.md)를 따르고 공유 타입 변경은 소비자와 함께 검토한다.

## 9. Flyway 마이그레이션 작성 양식

위치는 `src/main/resources/db/migration/`, 이름은 `V{정수}__{snake_case 설명}.sql`이다.

| 파일 | 역할 |
| --- | --- |
| `V1__init.sql` | 과거 BIGSERIAL·vector 스키마. 이미 적용된 이력이므로 수정하지 않음 |
| `V2__erd_uuid_schema.sql` | 구형 테이블을 잠근 뒤 비어 있는지 확인하고 최신 UUID 스키마로 전환 |
| `V3__seed_place_categories.sql` | 자체 카테고리 8종 seed |

**V2는 구형 데이터가 한 건이라도 있으면 실패한다.** 데이터를 삭제하거나 필수 4축·취향·수신 이력을 임의로 채우지 않는다. 별도 빈 개발 DB를 사용하거나 승인된 데이터 이관 설계를 먼저 마련한다. V2의 실패는 전체 트랜잭션을 롤백하므로 구형 테이블을 일부만 제거하지 않는다.

새 마이그레이션 작성 전 최신 번호를 확인한다. BE1이 번호·적용 순서를 취합하고 각 담당자가 자기 변경을 작성한다. 이미 적용된 파일은 이름·내용을 바꾸지 않고 새 마이그레이션으로 수정한다. DB 초기화·Flyway clean을 자동 해결책으로 실행하지 않는다.

전체 마이그레이션 후 전용 테스트 DB에서 실행:

```bash
psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/test/resources/db/erd_constraints.sql
```

SQL 검증은 합성 fixture를 롤백하며 축·카테고리 상한·복합 FK·중복 배달·날짜·RESTRICT를 검사한다. 실제 API의 권한·동시성·파일 복구 검증을 대신하지 않는다.

## 10. 브랜치 전략

경량 GitHub Flow: 작업별 `feat/*`, `fix/*`, `docs/*`, `refactor/*`, `chore/*` 브랜치와 PR을 사용한다. `main` 직접 push 금지, CI 통과와 상대 리뷰 후 병합한다. 사람별 장기 브랜치를 만들지 않는다. PR에는 실행한 검증과 실행하지 않은 검증을 구분한다.

## 11. 코딩 컨벤션 (Lombok · JPA 엔티티)

- Entity: `@Getter`, protected 기본 생성자, `@Builder`; `@Setter`/`@Data` 사용 금지. 상태 변경은 의미 있는 메서드로 제한한다.
- Service: 생성자 주입, 사용사례 단위 `@Transactional`. 같은 사용사례의 하위 저장 기능을 별도 커밋하지 않는다.
- DTO: Java record. Entity 전체를 그대로 직렬화하지 않는다.
- 스키마는 Flyway만 생성하며 Hibernate는 검증한다. 텍스트는 `text`, enum은 문자열로 매핑한다.
- 불변 취향 버전은 `@Immutable`, 읽기·저장 Repository로 제공한다. 버전 번호만 남기고 실제 값을 덮어쓰지 않는다.
- 글로벌 JSON 설정은 중복 키·알 수 없는 필드·실수의 정수 강제 변환을 거절한다. 4축은 정확히 정수 -1/+1을 검증한다.
- 현재 직접 경험 생성은 수동 분류만 지원한다. `USER`/`NOT_RUN`을 기록하고 안전 상태는 `PENDING`으로 남긴다. AI가 없는 상태를 승인·분석 성공으로 처리하지 않는다.

## 12. 이미지 처리 (로컬 저장 · JSON/바이너리 API 분리)

1. `POST /v1/images`에 JPG/PNG를 multipart로 전송한다.
2. 파일을 디코딩해 실제 형식·바이트·픽셀 제한을 검사하고 재인코딩한다. UUID 파일명과 본인 소유 STAGED 메타데이터를 저장하고 `imageId`를 반환한다.
3. `POST /v1/memories`에서 본인·미만료·미사용 imageId를 잠그고 경험 저장과 첨부를 같은 트랜잭션으로 확정한다.
4. `GET /v1/memories/{id}/image`는 경험 소유/수신 권한과 상태를 검사한 뒤 바이너리를 반환한다. 원본 key 기반 공개 GET은 없다.

설정: `app.storage.upload-dir`, `allowed-extensions`, `max-size-bytes`, `staging-ttl`(개발 기본 PT30M), `max-pixels`(개발 기본 20000000). 파일 최대 10MB 등 기본값은 운영 합의값이 아니다. 파일은 배포 후에도 유지되는 경로에 보관한다.

좋아요는 별도 UUID 경로로 이미지를 복사한다. PRIVATE·분류 INSERT와 liked_at 갱신은 하나의 DB 트랜잭션이다. 확실한 롤백이면 생성한 파일만 정리하고, 커밋 여부 불명 상태에서는 잠재적으로 유효한 사본 파일을 지우지 않는다. 프로세스 중단 고아 파일·만료 이미지의 주기적 정리와 운영 보존 정책은 별도 작업이다.

## 13. API 명세 (springdoc-openapi / Swagger UI)

- 업무 경로: `https://{domain 또는 IP}/v1/{endpoint}`. 무버전 및 `/api/...` 호환 경로는 없다.
- `API_BASE_URL`은 origin이고, `docs/openapi.yaml`의 paths에 `/v1`이 포함된다. 중복 접두어를 붙이지 않는다.
- `/swagger-ui.html`, `/v3/api-docs`는 현재 Controller/DTO에서 생성한 실제 제공 API 문서다. Actuator와 함께 업무 API 버전 경로 밖의 기술 경로다.
- `docs/API_SPEC.md`, `docs/openapi.yaml`은 전체 제품 계약이다. 현재 코드의 모든 응답·페이지·오류가 이 계약을 완성했다는 뜻은 아니다. 현재 범위와 미구현 항목은 [README](README.md#현재-구현-범위)를 참고한다.

## 14. 인증/인가 (Spring Security + JWT)

- 공개 업무 API는 `POST /v1/auth/login`뿐이다. Swagger와 health는 별도 공개 기술 경로다.
- Principal과 JWT subject는 UUID다. 클라이언트가 보내는 ownerId/userId를 신뢰하지 않는다.
- BCrypt 자격증명을 검사한 뒤 ACTIVE 상태를 확인한다. 없는 이메일·틀린 비밀번호는 같은 인증 실패를 반환한다.
- 보호 요청에서도 현재 계정 상태를 확인하고 온보딩 전 허용 경로를 제한한다. JWT 발급 당시 ACTIVE였다는 사실만 믿지 않는다.
- `JWT_SECRET`은 환경변수로 주입하며 개발용 기본값은 배포에 사용하지 않는다. 현재 access token만 발급하고 refresh·공개 가입·위치 변경 경로는 제공하지 않는다.
- 본인 또는 실제 수신자라는 객체별 접근 자격을 확인한다. 삭제·숨김 대상은 기존 접근자에게 410, 미권한자에게 404를 반환한다. 이미지·핀·집계에도 같은 가시성 원칙을 적용한다.
- 내부 ERROR dispatch는 원래 오류 상태를 보존하도록 허용한다. 이는 외부 `/error` 요청을 공개한다는 뜻이 아니다.
- Swagger의 Authorize에 발급된 JWT를 넣어 보호 API를 호출한다. 로그인 제한·운영자 도구·전체 공통 오류 계약은 별도 구현 항목이다.
