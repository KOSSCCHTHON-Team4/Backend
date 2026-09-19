# 운영 배포 가이드 — API + PostgreSQL Compose

> **상태: 구성 계약 문서이며, 배포 완료 증명서가 아니다.** 이 문서는 Linux 운영 호스트의
> `compose.yaml` / `Dockerfile` / `.env.example` 인터페이스를 설명한다. 로컬 개발과 CI는 계속
> Docker를 사용하지 않는다. 이 문서의 명령은 **운영자가 나중에 수행할 절차 예시**일 뿐이며,
> 이 작업에서 Docker 설치·이미지 pull/build·컨테이너 시작·DB bootstrap·Flyway·AI 호출·실제
> 트래픽 개방은 모두 실행하지 않았다.

## 1. 적용 범위와 릴리스 상태

- 기준 산출물은 `1d73bcf`에 통합된 Compose, V9 image-storage lifecycle, 세 lifecycle 설정 및
  `storage-init-empty` CLI이다. 이 문서는 그 통합 후보의 정적 구성 계약을 설명한다.
- 이는 전체 MVP의 완료 또는 실제 배포 성공을 뜻하지 않는다. Docker 설치·이미지 pull/build·컨테이너
  시작·DB bootstrap·Flyway·AI 호출·실제 트래픽 개방은 이 작업에서 실행하지 않았다.
- 사용자 정책상 구현 병합은 CI 통과 뒤에만 한다. 독립 검토는 새 필수 pre-merge gate가 아니라,
  통합 IV 단계에서 최종 실제 diff와 축적된 실행 증거를 대상으로 수행한다.
- 스케줄링, AI 분류·안전검사·동률 처리의 실제 provider 결과, 완전한 pagination/error/idempotency
  계약, 운영 도구 등은 구현 또는 런타임 검증 완료라고 주장하지 않는다.

## 2. 고정 토폴로지와 보안 경계

| 구성 요소 | 고정 계약 | 운영상 의미 |
| --- | --- | --- |
| Docker | 유지보수 중인 **Linux Docker Engine 28 이상** 및 Compose plugin이 이미 준비되어 있어야 한다. | Desktop/비Linux 호환성을 이 문서가 주장하지 않는다. 설치 자체는 이 절차 범위 밖이다. |
| API | `network_mode: host`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=${API_PORT:-8080}` | API는 호스트 네트워크 namespace를 공유한다. Compose `ports`/`networks`를 API에 추가하면 안 된다. 이 선택은 AI loopback을 유지하지만 API의 container network 격리·포트 namespace 이점을 포기하는 trade-off다. |
| AI host | 별도 호스트 프로세스가 계속 `127.0.0.1:8001`에서 제공되고, API는 `AI_BASE_URL=http://127.0.0.1:8001`을 쓴다. | AI를 Compose service로 추가하지 않는다. loopback은 인증의 대체물이 아니다. |
| DB | `db_network` bridge의 고정 IPv4와 `127.0.0.1:${DB_HOST_PORT}:5432` publish만 사용한다. | DB 포트는 외부에 publish되지 않는다. API는 host loopback port로 연결하고, L03 binding은 관찰한 DB IPv4/port/OID를 보존한다. |
| 외부 공개 | Compose에는 reverse proxy/TLS service가 없다. | 외부 TLS 종료·인증/방화벽 정책을 갖춘 별도 reverse proxy가 먼저 준비되어야 한다. 그 전에는 API 직접 포트를 공개하거나 traffic을 열지 않는다. |
| 영속 데이터 | `db_data` → `/var/lib/postgresql/data`, `image_data` → `/var/lib/emotionmap/uploads` | 두 named volume은 `COMPOSE_PROJECT_NAME`으로 project-scoped 이름이 된다. named volume 자체는 백업이 아니다. |
| API hardening | UID:GID `10001:10001`, read-only rootfs, `/tmp`만 `1777` tmpfs, `cap_drop: ALL`, `no-new-privileges` | image volume은 API 사용자에게 쓰기 가능해야 한다. rootless/NFS/공유 원격 volume의 FileLock/fsync 의미를 이 계약이 보장하지 않는다. |

`db`는 `pgvector/pgvector:0.8.6-pg17-bookworm@sha256:cf134a767f474095eeba57e0117be8e568e011a63f33fbf252f14c9b760f8e6f`로
pin되어 있다. PostgreSQL/vector image 또는 major version을 바꾸는 일은 별도 호환성 검증 대상이다.
`API_IMAGE`는 Compose의 image-only 입력이므로, 이 문서는 Compose build/push 절차를 새로 만들지 않는다.


Docker-admin, 호스트 root 및 `docker inspect` 권한자는 container environment를 볼 수 있다. `0600`
환경 파일은 호스트의 일반 사용자·저장소·build context로부터의 유출을 줄일 뿐, 이 신뢰 경계를
제거하지 않는다.

## 3. 사전 조건

다음 항목을 모두 승인하고 확인하기 전에는 시작 명령을 실행하지 않는다.

1. V9 image-storage lifecycle와 `storage-init-empty` CLI가 포함된 통합 후보를 사용한다.
2. 운영 승인된, **서로 비어 있는** `db_data`와 `image_data` volume을 사용한다. 기존 cluster,
   기존 이미지 root, 부분 초기화 marker/lock이 있는 volume을 신규 초기화 대상으로 삼지 않는다.
3. `COMPOSE_PROJECT_NAME`, `DB_NETWORK_SUBNET`, `DB_IPV4_ADDRESS`, `DB_HOST_PORT`, `DB_NAME`,
   `DB_SCHEMA=public` 및 두 volume의 정체성은 activation 이후 유지할 수 있도록 승인되었다.
4. 실제 service policy 값, BCrypt cost, shutdown grace 기간, AI model/prompt provenance, AI 인증
   방식, browser origin, TLS/reverse-proxy 정책, 호스트/heap/tmpfs/disk 용량이 별도로 승인되었다.
   `.env.example`의 빈 칸에 local/test 또는 추측한 production 숫자를 넣지 않는다.
5. `API_IMAGE`는 `latest`가 아닌 release별 고유 tag(가능하면 변경 불가능한 digest를 함께
   추적)이다. 이전 writer와 새 writer의 migration/storage 호환성이 검토되었다.

## 4. 보호된 Compose 환경 파일

`.env.example`은 값 없는 형식만 제공한다. 실제 파일은 예를 들어
`/secure/emotionmap/secret.env`처럼 저장소 밖의 보호된 경로에 만들고, `umask 077`으로 생성한 뒤
mode `0600`을 유지한다. 이 파일에는 비밀뿐 아니라 `COMPOSE_PROJECT_NAME`과 모든 M 입력을 넣는다.

```sh
# 운영자 절차 예시 — 이 작업에서 실행하지 않음
umask 077
# 승인된 보안 편집기로 /secure/emotionmap/secret.env 작성
chmod 0600 /secure/emotionmap/secret.env
```

### 4.1 dotenv literal과 shell 우선순위

- `secret.env`를 `source`/`.`/`eval`하지 않는다. shell expansion, command substitution, `set -x`,
  전체 config dump, 환경 전체 출력은 금지한다.
- `$`, `#`, 공백을 포함하는 literal은 Compose dotenv의 single-quoted 값으로 쓴다. 예시의 값은
  실제 비밀이 아니다.

  ```dotenv
  DB_PASSWORD='literal $ and # and a space'
  SIGNING_SECRET='a literal quote is escaped here: it\'s still one value'
  ```

  single quote 자체는 Compose dotenv literal 규칙에 따라 `\'`로 escape한다. 줄바꿈/CR을 넣지
  말고, shell quoting을 이어 붙이거나 shell escape 문법으로 해석하려 하지 않는다.
- host shell의 동명 환경 변수가 `--env-file` 값보다 우선할 수 있다. 실행 전 값은 출력하지 말고
  충돌하는 host 환경을 제거한 제한된 운영 shell을 사용한다. 특히 이미 export된 `DB_PASSWORD`,
  `JWT_SECRET`, `COMPOSE_PROJECT_NAME`을 무심코 상속하지 않는다.
- 실제 값을 렌더링하는 `docker compose config`, `config --environment`, `docker inspect`, process
  argv/환경 덤프를 증적에 남기지 않는다. Compose 설정 해석에는 아래의 **값 비출력** 명령만 쓴다.

  ```sh
  # 운영자 구성 검사 예시 — 실제 secret은 출력하지 않는다.
  docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml config --quiet
  ```

  `config --quiet` 성공은 Compose interpolation 확인일 뿐 Java binding, DB 권한, image build 또는
  runtime readiness의 증명이 아니다.

### 4.2 M — 반드시 채우는 Compose 입력

아래 M은 `compose.yaml`이 `${NAME:?…}`로 요구하는 입력이다. 비밀은 build ARG/이미지 ENV/COPY,
URL query, JVM argv에 넣지 않는다.

#### 배포 identity·네트워크·중지 시간

| 변수 | Compose/설정 binding | 단위·범위·제약 |
| --- | --- | --- |
| `COMPOSE_PROJECT_NAME` | Compose project name, volume/network 이름 | activation 뒤 바꾸면 다른 named volume/network를 가리킬 수 있다. 안정적인 식별자여야 한다. |
| `API_IMAGE` | `api.image` | release별 고유 image reference; `latest` 금지. |
| `DB_NAME` | bootstrap 대상 DB, JDBC URL의 database | `[a-z_][a-z0-9_]{0,62}`; `postgres`, `template0`, `template1`, `pg_` 시작 이름 금지. |
| `DB_NETWORK_SUBNET` | `db_network.ipam.config.subnet` | 겹치지 않는 IPv4 CIDR. activation 뒤 변경 금지. |
| `DB_IPV4_ADDRESS` | DB의 `db_network` 고정 IPv4 | 위 subnet 안의 유효 host 주소여야 하며 network/broadcast/gateway 주소가 아니어야 한다. |
| `DB_HOST_PORT` | host loopback publish 및 `DB_URL` | TCP port `1..65535`; API/AI/기존 host service와 충돌하지 않는다. |
| `API_SHUTDOWN_TIMEOUT` | `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` | 양의 Spring `Duration`; 승인된 graceful shutdown phase 기간. |
| `API_STOP_GRACE_PERIOD` | `api.stop_grace_period` | Compose duration; 관찰된 전체 API shutdown보다 길게 승인한다. |
| `DB_STOP_GRACE_PERIOD` | `db.stop_grace_period` | Compose duration; Postgres signal/stop 동작을 임의로 바꾸지 않는다. |

#### DB·서명·로그인 보안

| 변수 | binding | 단위·범위·제약 |
| --- | --- | --- |
| `DB_USERNAME` | bootstrap의 app owner, `spring.datasource.username`, 두 내부 CLI | `DB_NAME`와 같은 식별자 규칙. `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS` app role이며 cluster superuser가 아니다. |
| `DB_PASSWORD` | app datasource/내부 CLI/bootstrap | 비어 있지 않은 단일 행 secret. `POSTGRES_PASSWORD`와 반드시 달라야 한다. API와 app CLI만 쓰며 admin 비밀과 재사용하지 않는다. |
| `POSTGRES_PASSWORD` | DB container 최초 bootstrap admin | 비어 있지 않은 단일 행 secret, `DB_PASSWORD`와 다름. API environment에 전달되지 않는다. 기존 cluster에서 값만 바꿔도 DB admin password가 자동 rotation되지 않는다. |
| `JWT_SECRET` | `app.jwt.secret` | 무작위 UTF-8 **최소 32 bytes**, `SIGNING_SECRET`와 다름. 개발값/공유값 금지. |
| `SIGNING_SECRET` | `app.signing.secret` | 무작위 UTF-8 **최소 32 bytes**, `JWT_SECRET`와 다름. |
| `PASSWORD_BCRYPT_STRENGTH` | `app.security.password.bcrypt-strength`, account CLI | 정수 `4..31`. 라이브러리 허용 범위일 뿐 권장 운영값이 아니다. 실제 hardware/공격 모델/로그인 latency 측정 뒤 운영자가 선택한다. |
| `LOGIN_FAILED_ATTEMPT_WINDOW_SECONDS` | `app.account.login.failed-attempt-window-seconds` | 초, 정수 `1..2147483647`. 누락/오류는 login 쪽 `CONFIGURATION_UNAVAILABLE`(503)일 수 있으며 health down을 보장하지 않는다. |

DB bootstrap은 빈 PGDATA의 공식 image 초기화 때만 실행된다. socket admin `postgres`로 app role,
app-owned DB/public schema 및 `vector` extension을 만든다. app은 Flyway DDL owner일 수 있지만
cluster superuser는 아니다. initializer가 실패·중단된 뒤에는 기존 PGDATA에서 자동 재실행되지
않는다. volume을 지우거나 repair/reset하여 다시 시도하지 말고, 보존한 상태로 원인을 조사하고
승인된 복구 절차로 넘긴다.

#### M — service policy (`app.service.*`)

이 표의 값은 모두 `/v1/config` 모델의 필수 입력이다. 누락/범위 오류 시 process가 살아 있어도
`ServiceConfigProvider.current()`는 `CONFIGURATION_UNAVAILABLE`(503)가 될 수 있다. 값이 존재한다고
일일 quota, 보고서/pagination/map 정책이 모두 집행된다는 뜻은 아니다. 특히
`SERVICE_RADIUS_METERS`는 아래 `MATCH_RADIUS_METERS`의 alias가 아니다.

| 변수 | source binding | 단위·허용 범위 |
| --- | --- | --- |
| `SERVICE_CONFIG_VERSION` | `app.service.config-version` | 비어 있지 않은 문자열 |
| `SERVICE_RADIUS_METERS` | `app.service.radius-meters` | 양의 정수, m |
| `SERVICE_DEMO_CENTER_LAT` | `app.service.demo-center.lat` | finite `Double`, `[-90, 90]` 도 |
| `SERVICE_DEMO_CENTER_LNG` | `app.service.demo-center.lng` | finite `Double`, `[-180, 180]` 도 |
| `SERVICE_LIMIT_MEMORY_CONTENT_MAX_CODE_POINTS` | `app.service.limits.memory-content-max-code-points` | 양의 정수, Unicode code point 수 |
| `SERVICE_LIMIT_PREFERENCE_DESCRIPTION_MAX_CODE_POINTS` | `app.service.limits.preference-description-max-code-points` | 양의 정수, Unicode code point 수 |
| `SERVICE_LIMIT_REPORT_DETAILS_MAX_CODE_POINTS` | `app.service.limits.report-details-max-code-points` | 양의 정수, Unicode code point 수 |
| `SERVICE_LIMIT_IMAGE_MAX_BYTES` | `app.service.limits.image-max-bytes` | bytes, `1..2147483646` (`B+1` bounded byte-array 표현 가능 범위) |
| `SERVICE_LIMIT_IMAGE_MAX_WIDTH` | `app.service.limits.image-max-width` | 양의 정수, pixel |
| `SERVICE_LIMIT_IMAGE_MAX_HEIGHT` | `app.service.limits.image-max-height` | 양의 정수, pixel |
| `SERVICE_LIMIT_IMAGE_MAX_PIXELS` | `app.service.limits.image-max-pixels` | 양의 `long`, pixel 수 |
| `SERVICE_LIMIT_IMAGE_UPLOAD_TTL_SECONDS` | `app.service.limits.image-upload-ttl-seconds` | 양의 정수, 초 |
| `SERVICE_LIMIT_ANALYSIS_TTL_SECONDS` | `app.service.limits.analysis-ttl-seconds` | 양의 정수, 초 |
| `SERVICE_LIMIT_DAILY_DIRECT_MEMORY_LIMIT` | `app.service.limits.daily-direct-memory-limit` | 양의 정수, count |
| `SERVICE_LIMIT_DEFAULT_PAGE_LIMIT` | `app.service.limits.default-page-limit` | 양의 정수, count; `<= SERVICE_LIMIT_MAX_PAGE_LIMIT` |
| `SERVICE_LIMIT_MAX_PAGE_LIMIT` | `app.service.limits.max-page-limit` | 양의 정수, count |
| `SERVICE_LIMIT_MAX_MAP_PAGE_LIMIT` | `app.service.limits.max-map-page-limit` | 양의 정수, count |
| `SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS` | `app.service.auth.access-token-ttl-seconds` | 양의 정수, 초; JWT TTL의 단일 원천 |
| `SERVICE_AUTH_FAILED_LOGIN_LIMIT` | `app.service.auth.failed-login-limit` | 양의 정수, count |
| `SERVICE_AUTH_LOGIN_LOCK_SECONDS` | `app.service.auth.login-lock-seconds` | 양의 정수, 초 |

`SERVICE_LIMIT_IMAGE_MAX_WIDTH * SERVICE_LIMIT_IMAGE_MAX_HEIGHT`와
`SERVICE_LIMIT_IMAGE_MAX_PIXELS`, image decode/re-encode memory, `SERVICE_LIMIT_IMAGE_MAX_BYTES`를
실제 workload에 맞게 함께 승인한다. 이 표는 각 값의 parse/모델 제약이며, 구성 검사만으로
용량 적합성이 증명되지는 않는다.

#### M — storage·요청 coordination

| 변수 | source binding | 단위·범위·상태 |
| --- | --- | --- |
| `APP_STORAGE_MULTIPART_REQUEST_OVERHEAD_BYTES` | `app.storage.multipart-request-overhead-bytes` | 양의 `long` H, bytes. `SERVICE_LIMIT_IMAGE_MAX_BYTES` B와 **B + H가 long overflow하지 않아야** 하며 오류면 신규 upload gate가 503으로 닫힌다. |
| `REQUEST_COORDINATION_LEASE_DURATION` | `app.request-coordination.lease-duration` | 양의 Spring `Duration`; duration 표현/나노초 변환이 가능한 승인값이어야 한다. |
| `IMAGE_CLEANUP_INTERVAL` | `app.storage.cleanup-interval` | 양의 Spring `Duration`. 누락/비양수/표현 불가는 새 파일 생성과 cleanup을 fail-closed로 만든다. |
| `IMAGE_CLEANUP_BATCH_SIZE` | `app.storage.cleanup-batch-size` | 양의 정수 count. |

dataset/root UUID 환경 기본값, `cleanup-enabled`, file-age/grace/retention 기본값, auto-adopt,
auto-rebind, reset, repair, flyway-only 또는 password provisioning 환경변수를 추가로 발명하지 않는다.

#### M — 실제 HTTP AI provenance·timeout

| 변수 | source binding | 단위·제약 |
| --- | --- | --- |
| `AI_TIMEOUT` | `app.ai.timeout` | 양의 Spring `Duration`. 현재 HTTP factory는 connect를 최소 2초, read를 최소 20초로 잡으므로 **전체 호출 deadline이 아니다**. 실제 provider timeout과 함께 승인한다. |
| `AI_ANALYSIS_MODEL` | `app.ai.analysis-model` | 비어 있지 않은 실제 분석 model provenance 값. 원격 AI가 이 값을 실제로 사용한다는 증명은 별도다. |
| `AI_ANALYSIS_PROMPT_VERSION` | `app.ai.analysis-prompt-version` | 비어 있지 않은 실제 analysis prompt revision. |
| `AI_MODERATION_MODEL` | `app.ai.moderation-model` | 비어 있지 않은 실제 moderation model provenance 값. |
| `AI_MODERATION_PROMPT_VERSION` | `app.ai.moderation-prompt-version` | 비어 있지 않은 실제 moderation prompt revision. |

### 4.3 F — Compose가 고정·유도하는 값

아래 값은 `secret.env`에서 바꾸지 않는다. Compose service별 allowlist가 전달하며, 모든 환경을
`env_file:`로 DB/API에 통째로 주입하지 않는다.

| F 값 | 고정값 또는 유도식 | 이유 |
| --- | --- | --- |
| `POSTGRES_USER` / `POSTGRES_DB` | `postgres` / `postgres` (DB service만) | bootstrap 관리 DB/role이다. app user를 `POSTGRES_USER`로 쓰면 superuser가 되어 금지다. |
| `POSTGRES_INITDB_ARGS` | `--auth-host=scram-sha-256` | host trust auth를 쓰지 않는다. |
| `SPRING_PROFILES_ACTIVE` | `prod` | local/CI profile을 production container에 가져오지 않는다. |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:${DB_HOST_PORT}/${DB_NAME}?currentSchema=public` | API와 두 CLI의 app DB locator다. |
| `DB_SCHEMA` | `public` | API는 JDBC `currentSchema`를 사용하고, account CLI 및 L03 CLI는 명시값을 직접 확인한다. |
| `APP_UPLOAD_DIR` | `/var/lib/emotionmap/uploads` | `image_data` mount target과 storage CLI `--root`가 정확히 같아야 한다. |
| `AI_PROVIDER` / `AI_BASE_URL` | `http` / `http://127.0.0.1:8001` | Linux host-network API의 현재 HTTP AI adapter 선택이다. |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `${API_PORT:-8080}` | API는 loopback만 bind한다. |
| `SERVER_SHUTDOWN` | `graceful` | `API_SHUTDOWN_TIMEOUT`/Compose grace와 함께 관찰해야 한다. |
| `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` | `${API_SHUTDOWN_TIMEOUT}` | 위 M shutdown duration에서 유도한다. |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | schema DDL은 Flyway가 소유한다. |
| `SPRING_FLYWAY_ENABLED` | `true` | 정상 API boot에서 migration을 적용한다. |
| `SPRING_FLYWAY_VALIDATE_ON_MIGRATE` | `true` | migration 검증을 유지한다. |
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `false` | 기존 DB를 guessed baseline으로 채택하지 않는다. |
| `SPRING_FLYWAY_CLEAN_DISABLED` | `true` | clean을 배포 절차로 사용하지 않는다. |

### 4.4 C — 조건부 입력

| 변수 | 언제 채우는가 | binding·제약 |
| --- | --- | --- |
| `AI_SERVICE_TOKEN` | 실제 AI host가 shared `X-AI-Token` 인증을 요구/수용하도록 합의된 경우만 | `app.ai.service-token`. API는 nonblank일 때 모든 HTTP AI 요청에 `X-AI-Token`을 붙인다. DB/admin 비밀과 재사용하지 않는다. loopback만으로 인증되었다고 가정하지 않는다. provider API/Claude key는 AI host의 별도 비밀이며 이 파일에 넣지 않는다. |
| `CORS_ALLOWED_ORIGINS` | TLS reverse proxy와 다른 browser origin이 필요한 경우만 | `app.cors.allowed-origins`; 정확한 origin의 comma-separated allowlist. 빈 값은 cross-origin 불허이고 wildcard는 기동 거절이다. |

### 4.5 O — 현재 구현의 선택 override

O의 현재 기본값은 운영 승인값이 아니다. 빈 `MATCH_*`는 현재 record 기본값을 사용하며,
`MODERATION_*`는 생략하거나 정확한 기본값을 유지한다.

| 변수 | source binding | 현재 기본값 | 단위·검증 제약 |
| --- | --- | --- | --- |
| `API_PORT` | `SERVER_PORT ← API_PORT` | `8080` | host loopback TCP port. proxy와 충돌하지 않는다. |
| `MATCH_RADIUS_METERS` | `app.matching.radius-meters` | `1000` | m, `>= 1` |
| `MATCH_FALLBACK_RADIUS_METERS` | `app.matching.fallback-radius-meters` | `3000` | m, `>= MATCH_RADIUS_METERS` |
| `MATCH_MIN_PLACES` | `app.matching.min-places-for-primary-radius` | `3` | count, `>= 1` |
| `MATCH_NOTIFY_THRESHOLD` | `app.matching.notify-threshold` | `0.85` | `0 <= VERIFY <= NOTIFY <= 1`의 NOTIFY |
| `MATCH_VERIFY_THRESHOLD` | `app.matching.verify-threshold` | `0.65` | `0 <= VERIFY <= NOTIFY <= 1`의 VERIFY |
| `MATCH_SIMILARITY_WEIGHT` | `app.matching.similarity-weight` | `0.85` | 현재 code는 합/범위를 검증하지 않는다. 합이 1이라고 가정하지 않는다. |
| `MATCH_DISTANCE_WEIGHT` | `app.matching.distance-weight` | `0.15` | 현재 code는 합/범위를 검증하지 않는다. |
| `MATCH_PLACE_VIBE_SMOOTHING` | `app.matching.place-vibe-smoothing` | `2` | count, `>= 0` |
| `MATCH_MERGE_DISTANCE_METERS` | `app.matching.merge-distance-meters` | `20` | m, `>= 0` |
| `MATCH_CATEGORY_CONFIDENCE_THRESHOLD` | `app.matching.category-confidence-threshold` | `0.6` | `[0, 1]` |
| `MATCH_REVIEWS_FOR_VERIFY` | `app.matching.reviews-for-verify` | `5` | count, `>= 1` |
| `MODERATION_RETRY_DELAY` | `app.moderation.retry-delay` | `PT5M` | Spring `Duration`; scheduled fixed delay |
| `MODERATION_RETRY_INITIAL_DELAY` | `app.moderation.retry-initial-delay` | `PT1M` | Spring `Duration`; 빈 문자열로 명시 전달해 default를 지우지 않는다. |

## 5. 최초의 **빈** dataset 초기화 절차

이 절의 모든 Compose 명령은 같은 `--env-file /secure/emotionmap/secret.env -f compose.yaml`와
그 파일 안의 `COMPOSE_PROJECT_NAME`을 사용한다. 아래 명령은 이 작업에서 실행하지 않았으며,
실행 권한을 받은 운영자만 최종 통합 후보에서 수행한다.

### 5.1 순서

1. **인프라 preflight.** 3절의 Linux Engine/Compose, host AI loopback, reverse proxy/TLS, 고정
   subnet/IP/port, 빈 승인 volumes, protected environment와 통합된 V9/activation CLI를 확인한다.
   실제 secret을 보이지 않는 Compose 해석 검사는 4.1의 `config --quiet`만 쓴다.
2. **DB만 시작하고 health를 기다린다.**

   ```sh
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml up -d db
   ```

   새 빈 `db_data`에서만 `10-init-app-db.sh`가 app DB/user/public schema/vector를 준비한다.
   DB health는 app credential으로 TCP `psql` scalar query를 하여 vector, public 권한,
   non-superuser/non-createdb/non-createrole/non-replication/non-bypassrls를 확인한다. 이는
   `pg_isready`만의 대체가 아니지만 **Flyway 완료 전에도** healthy가 될 수 있다.
3. **외부 traffic을 닫은 채 정상 API를 기동한다.**

   ```sh
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml up -d api
   ```

   이는 migration-only profile/console 명령이 아니다. 정상 web application boot이므로 Flyway와
   JPA `validate`뿐 아니라 web/AI wiring/scheduler도 생긴다. `V9`까지 Flyway migration과 schema
   validate가 완료되었는지 운영자가 별도로 확인한다. 이 단계에서 L03 dataset은 아직 UNBOUND이므로
   storage 작업은 fail-closed여야 한다.
4. **API를 멈춘 뒤 unbound dataset ID를 read-only로 조회한다.** API writer를 반드시 멈춘 뒤,
   DB app credential의 read-only session으로 한 행만 읽는다.

   ```sh
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml stop api

   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml exec -T db \
     sh -c 'PGPASSWORD="$DB_PASSWORD" PGOPTIONS="-c default_transaction_read_only=on" exec psql -X -w -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 -Atqc "SELECT dataset_id FROM public.image_storage_binding WHERE singleton = TRUE;"'
   ```

   이 query는 비밀 값을 argv에 넣지 않는다. 출력 UUID를 읽었다는 사실은 activation 승인이 아니다.
   기대하는 빈 dataset의 UUID인지, DB/schema/volume/변경 요청이 승인되었는지를 사람이 확인한 뒤에만
   다음 단계의 `<approved-dataset-id>`로 사용한다.
5. **실제 빈-root activation CLI를 한 번만 실행한다.** 다음은 통합 후보의 exact interface다.

   ```sh
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml run --rm --no-deps api \
     storage-init-empty \
     --root /var/lib/emotionmap/uploads \
     --expected-dataset-id <approved-dataset-id> \
     --expected-database <DB_NAME> \
     --expected-schema public
   ```

   CLI는 이미 migrated된 `public` schema, unbound `image_storage_binding`, 이미지 참조가 없는
   dataset, 그리고 비어 있는 `image_data` root만 받아들인다. foreign/nonempty/BOUND root, 잘못된
   dataset/database/schema, partial marker/lock은 거절 사유이며 reset·삭제·재시도 지시가 아니다.
   실패가 marker/lock을 남기면 normal startup은 추측해서 repair/adopt하지 않고 fail-closed여야 한다.

   | exit code | 실제 CLI 의미 |
   | --- | --- |
   | `0` | `Storage initialization completed.` — 명시 activation 완료 |
   | `2` | 인수/UUID/schema 형식 오류 또는 `INVALID_REQUEST` (예: 비어 있지 않음, 이미 bound, 기대 locator 불일치) |
   | `1` | 설정 불가, DB/filesystem/boot 등 그 밖의 실패 |

   0 이외의 종료 시 다시 실행하거나 volume을 수정하지 말고 멈춰 조사·승인 절차로 넘긴다.
6. **개별 승인 계정을 하나씩 provisioning한다.** 아래의 `PropertiesLauncher` 명령은 I1의
   `AccountProvisioningCli`에 맞는 정확한 entrypoint다. `ACTIVE` 또는 `PENDING`만 쓰며, CLI는
   하나의 `USER` 역할 계정과 credential만 만든다. admin 계정/온보딩/이력/password reset/공개 운영
   API를 제공하지 않는다.

   ```sh
   # 실제 TTY에서 실행한다. 현재 CLI는 password prompt 문구 없이 console.readPassword()로 대기한다.
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml run --rm --no-deps \
     --entrypoint java api \
     -Dloader.main=team4.emotionmap.account.AccountProvisioningCli \
     -cp /app/app.jar \
     org.springframework.boot.loader.launch.PropertiesLauncher \
     --email <approved-email> \
     --status <ACTIVE-or-PENDING>
   ```

   비대화형 공급이 보안상 승인된 경우에만, 승인된 secret supplier의 **raw UTF-8 bytes (개행 없음)**를
   왼쪽에서 전달하고 `-T`와 `--password-stdin`을 추가한다. 아래 `<approved-raw-password-source>`는
   실행 가능한 도구 이름이 아니라, password를 argv/environment/plaintext file/`echo`에 넣지 않는
   승인된 비밀 공급 절차의 자리표시자다.

   ```sh
   <approved-raw-password-source> | \
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml run --rm -T --no-deps \
     --entrypoint java api \
     -Dloader.main=team4.emotionmap.account.AccountProvisioningCli \
     -cp /app/app.jar \
     org.springframework.boot.loader.launch.PropertiesLauncher \
     --email <approved-email> \
     --status <ACTIVE-or-PENDING> \
     --password-stdin
   ```

   현재 CLI는 password stdin을 line-delimited 입력으로 trim하지 않는다. **trailing newline도 password
   byte**이며, malformed UTF-8, 빈 password, 또는 UTF-8 72 bytes 초과는 거절된다. 허용되는 실제
   상한은 nonempty·well-formed UTF-8 `<= 72` bytes다. `PASSWORD_BCRYPT_STRENGTH` 비용으로 hash를
   만들므로 첫 계정 수와 cost를 운영 capacity에 맞춰 승인한다. duplicate email은 새 password로
   덮어쓰지 않는다.

   이 CLI에는 credential rotation/reset 명령이 없다. 초기 password를 바꿔야 하면 CLI를 재실행하거나
   DB를 직접 수정하지 말고, 별도 승인된 account/password-rotation 절차를 마련한 뒤 수행한다.
7. **API를 다시 시작하고 business readiness를 분리해 확인한다.**

   ```sh
   docker compose --env-file /secure/emotionmap/secret.env -f compose.yaml up -d api
   ```

   Compose API health는 정확히 `http://127.0.0.1:${SERVER_PORT}/actuator/health`에 5초 curl을
   수행한다. `/readiness` anonymous endpoint를 추가하거나 probe로 쓰지 않는다. health `UP`은
   인프라 health 신호일 뿐 service policy, storage binding, login/onboarding, L03 upload/read,
   실제 AI 또는 전체 MVP readiness를 뜻하지 않는다.
8. **승인된 business readiness 뒤에만 traffic을 연다.** 최소한 개별 계정 login,
   authenticated `/v1/config`, L03 image upload/attach/read, DB/image 지속성, 실제 AI adapter path를
   별도 승인된 시나리오로 확인한다. TLS reverse proxy가 loopback API로만 proxy하고 DB/AI/API의
   직접 외부 접근을 열지 않는지 확인한 뒤에만 traffic을 개방한다.

Compose service를 만들 때 모든 M interpolation이 필요하지만, 좁은 account/storage CLI 자체가
JWT/AI/Flyway/service policy를 모두 사용한다는 뜻은 아니다. account CLI는 raw DB URL/user/password,
`DB_SCHEMA`, BCrypt strength만 isolated environment에 넣고, L03 activation CLI는 raw DB URL/user/password,
`DB_SCHEMA`, upload root만 isolated environment에 넣는다.

## 6. AI wire, provenance, 용량과 recovery의 별도 게이트

`AI_PROVIDER=http`일 때 API는 host AI에 다음 HTTP POST를 사용한다:

- `/ai/analyze` — content와 선택 `naver_category`
- `/ai/moderate` — content와, 있으면 re-encoded image `data:<mime>;base64,...`
- `/ai/verify`, `/ai/tiebreak`, `/ai/match-reason`

`AI_SERVICE_TOKEN`이 nonblank이면 모든 요청에 `X-AI-Token`을 붙인다. API backend에는 provider/Claude
API key를 넣지 않는다. `AI_*_MODEL`과 `AI_*_PROMPT_VERSION`은 backend가 남기는 provenance 입력이지,
원격 provider가 해당 model/prompt를 실제 사용했다는 증명이 아니다.

다음은 아직 runtime proof가 아니며, traffic 개방 전에 실제 AI host와 검증해야 한다.

- host `127.0.0.1:8001` 연결, `X-AI-Token` 필요 여부와 token rotation, 다섯 endpoint의 request/response
  JSON, 4개 axis(`CROWD_LEVEL`, `SPATIAL_FEEL`, `COMPANY_FIT`, `STAY_STYLE`)의 `-1/+1` 값 및 category
  JSON mapping이 실제 계약과 맞는지 확인한다. 형식 불량/미지 axis는 backend가 unknown/failed로 다룰 수
  있으며 AI 품질을 증명하지 않는다.
- 실제 model/prompt provenance, 상류 오류/timeout behavior, retry/recovery, moderation의
  APPROVED/REVIEW_REQUIRED/REJECTED/ERROR 처리, 데이터가 로그에 남지 않는지를 확인한다.
- `AI_TIMEOUT`은 total deadline이 아니다. 최소 2초 connect와 최소 20초 read timeout, provider latency,
  host/network failure를 실제로 관찰한다.
- image 최대 bytes B, multipart overhead H, decode/re-encode/base64 확장, Java heap, API `/tmp` tmpfs,
  image volume capacity와 cleanup batch/interval을 같이 측정한다. 구성 값만으로 memory/disk 안전성을
  주장하지 않는다.
- L03 activation 뒤에는 bound DB name/OID/server IPv4/port/schema name/OID와 marker dataset/root가
  일치해야 한다. 새 file/cleanup/expired-claim recovery 설정의 missing/invalid 상황, nonempty/foreign/
  partial root 거절, 재시작 후 읽기/쓰기 보존을 runtime으로 확인한다.

## 7. 업데이트, 비밀 rotation, 백업과 복구

### 7.1 업데이트 전후

1. release별 `API_IMAGE`를 명시하고, 새 image가 현재 migration/storage와 호환되는지 먼저 승인한다.
2. old writer를 drain한 뒤 완전히 stop한다. 동시에 서로 다른 release가 같은 DB/image dataset에 쓰게
   하지 않는다.
3. `COMPOSE_PROJECT_NAME`, `db_data`, `image_data`, `DB_NETWORK_SUBNET`, `DB_IPV4_ADDRESS`,
   `DB_HOST_PORT`, `DB_NAME`, `public` schema와 DB/schema OID를 유지한다. DB container recreate는 같은
   persistent DB volume과 고정 주소를 보존하는 경우에만 locator continuity를 논의할 수 있다.
4. 정상 API boot는 Flyway forward migration + JPA validate다. 별도의 flyway-only profile/console을
   발명하지 않는다. 이미 적용한 migration은 수정하지 않고 forward-only compatibility와 운영자 승인을
   거친다.

비밀/key rotation은 자동 무중단 절차가 아니다.

- `JWT_SECRET` 교체는 기존 JWT를 무효화한다. `SIGNING_SECRET` 교체는 analysis token/cursor 같은
  서명 값을 무효화할 수 있으므로 강제 재인증·재시도 계획이 필요하다.
- `DB_PASSWORD` 교체는 DB role 값, API와 두 CLI 환경을 coordinated change해야 한다.
  `POSTGRES_PASSWORD`는 최초 빈-cluster bootstrap 값이므로 기존 cluster의 env 파일만 바꿔도 자동으로
  rotation되지 않는다.
- `AI_SERVICE_TOKEN` 교체는 AI host와 API의 동시 합의가 필요하다. 다중-key grace가 구현되어 있다고
  가정하지 않는다.
- 사용자 credential은 provisioning CLI가 rotation하지 않는다. 5.1의 password 제한과 별도 승인 절차를
  지킨다.

### 7.2 일관된 백업/복구 단위

`db_data`와 `image_data`는 하나의 dataset이다. API writer를 drain/stop하고 두 volume을 같은 시점의
일관된 쌍으로 백업·복구한다. DB만 또는 image volume만 보존/복원하면 `image_storage_binding`, marker,
DB image reference가 어긋날 수 있다. named volume이 존재한다는 사실은 backup이나 restore 검증이 아니다.

Logical restore는 database/schema OID나 DB server locator를 바꿀 수 있어 L03 binding이 fail-closed로
거절할 수 있다. 새 DB/IP/OID에 자동 rebind/adopt하는 일반 절차는 없다. restore는 forward-only
migration compatibility, DB+images consistency, locator/binding 영향, writer ownership을 운영자가 승인한
뒤에만 수행한다.

원본과 물리적으로 같은 DB locator/OID/IP 및 marker를 그대로 복제한 clone은 L03가 일반 재시작과
구별하지 못할 수 있다. 이런 clone은 동시에 writer를 활성화하지 말고, 원본과 분리된 회복 검증 경계로
다룬다.

### 7.3 rollback 제한과 금지 절차

다음은 정상 rollback/복구 방법이 아니다.

- 이전 non-cooperating writer image로 단순 rollback하기 — 특히 L03 binding/lifecycle를 모르는 writer는
  같은 dataset에 다시 쓰면 안 된다.
- `docker compose down -v`, volume 삭제, DB/image root reset, Flyway clean/baseline 변경.
- 기존 root의 marker를 지우고 재실행하기, 자동 adopt/repair/rebind, 부분 초기화에 대해 guessed recovery하기.
- DB/image volume 중 하나만 restore하거나, 고정 subnet/IP/OID 변경을 무시하고 traffic을 재개하기.

실패는 보존·중지·조사·승인으로 처리한다. forward migration 이후의 rollback은 application/DB/image
호환성 증거와 명시적 운영 승인이 없는 한 허용되지 않는다.

## 8. 구현 근거와 검증 경계

이 문서는 다음 실제 인터페이스를 대조해 작성했다.

| 근거 | 문서에 반영한 내용 | 상태 |
| --- | --- | --- |
| `Dockerfile` | Java 21 build/runtime, UID 10001, `/app/app.jar` entrypoint, image upload root | 정적 대조만 수행 |
| `compose.yaml` | Linux host-network API, loopback DB publish, fixed IPv4, volume mounts, service allowlist, health probes, fixed Spring flags | 정적 대조만 수행 |
| `.env.example` | M/F/C/O의 변수명과 빈 required policy 입력 | 정적 대조만 수행 |
| `deploy/postgres/10-init-app-db.sh` | empty PGDATA 단발 bootstrap, 별도 app/admin secret, non-superuser role, public/vector 권한 | 정적 대조만 수행 |
| `AccountProvisioningCli` / `AccountProvisioningBootstrap` | `PropertiesLauncher` command, TTY/raw stdin, `--password-stdin`, `ACTIVE|PENDING`, BCrypt `4..31` | 정적 소스 대조만 수행 |
| `ImageStorageActivationCli`, `ImageRootBinding`, `V9__image_storage_lifecycle.sql` | `storage-init-empty` flags, exit `0/1/2`, empty-only binding, DB/IP/OID/schema/marker constraints | 정적 소스 대조만 수행 |
| service/storage/AI property source | M units/ranges, `CONFIGURATION_UNAVAILABLE` 경계, AI HTTP/header/timeout behavior | 정적 소스 대조만 수행 |

`DEPLOY-CONFIG-C1`의 구성 증거는 synthetic complete env에서 Compose 해석 성공, required M 45개 각각의
누락·빈 값 거절(총 90회), 그리고 34개 구조 검사를 포함한다. 이 증거는 값 없는 환경/정적 manifest
계약만 확인하며 real secret render나 Java binding, DB 권한, image build 또는 runtime readiness를 증명하지
않는다. 기존 artifact-scope `APPROVE`도 이 정적 산출물 범위의 판단일 뿐, 최종 통합 후보나 배포의 승인이 아니다.

구현 병합은 사용자 정책대로 CI 통과 뒤에만 한다. 독립 검토는 이를 대체하거나 새 mandatory pre-merge
gate가 아니며, IV에서 통합된 최종 candidate의 실제 diff와 누적 실행 증거를 대상으로 한다. Main이
구성 재검증, CI 및 PR을 소유한다.

그 전까지 실제 Docker build/pull/startup, empty-volume bootstrap, Flyway/JPA 실행, storage activation,
계정 provisioning, Linux host-network AI 연결, 실제 AI, TLS 공개, business readiness와 production
deployment는 **NOT_RUN**이다.
