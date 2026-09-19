# Ubuntu LXC Docker 배포 환경 파일 작성 안내

이 문서는 Ubuntu 24.04 unprivileged LXC에서 이미 준비된 Docker Engine/Compose plugin으로 감정지도 API와 PostgreSQL을 배포할 때, Compose 환경 파일을 **처음 만들고 안전하게 편집하는 방법**을 설명한다. 실행 전 전체 설치·storage activation·traffic 절차는 [DEPLOY_GUIDE.md](DEPLOY_GUIDE.md)를 따른다. 이 안내의 값과 명령은 예시이며, 실행 또는 운영 준비 완료의 증명은 아니다.

> **중요:** `.env.example`은 형식만 담은 빈 template이다. 아래의 숫자·CIDR·정책 값은 모두 **승인된 기본값이 아닌 설명용 예시**다. 실제 운영값은 보안/용량/서비스 정책 책임자가 결정한다. 실제 secret, 비밀번호, 토큰을 이 문서·shell history·Git·build context에 넣지 않는다.

## 1. 시작 전 확인과 경로 지도

다음이 준비되어 있어야 한다.

- LXC 안에 rootful Docker Engine 28 이상과 Compose plugin이 있고, 실행자는 LXC root 또는 승인된 Docker admin이다.
- Proxmox의 승인된 mount와 빈 storage 준비 여부를 먼저 [배포 가이드 2절](DEPLOY_GUIDE.md#2-lxc-proxmox-storage와-고정-토폴로지)에서 확인했다. mount가 없거나 다른 위치이면 directory/volume을 새로 만들지 말고 멈춘다.
- 최신 main 경로라면 3.3절의 source 안전성 확인과 image build를 수행할 권한이 있다. 일반 immutable release 경로와 섞지 않는다.

| 항목 | 고정 경로/계약 | 용도 |
| --- | --- | --- |
| source root | `/srv/emotionmap/deploy/backend` | `.env.example`, `compose.yaml`, Docker build와 Compose 명령의 working directory |
| protected dotenv | `/etc/emotionmap/secret.env` | repo 밖의 mode `0600` Compose 입력 파일 |
| dotenv parent | `/etc/emotionmap` | mode `0700` |
| LXC override | `/srv/emotionmap/deploy/compose.lxc.yaml` | repo 밖의 host-only volume override |
| API | host network, `127.0.0.1:${API_PORT:-8080}` | Compose에 API `ports`/`networks`를 추가하지 않는다 |
| DB | `127.0.0.1:${DB_HOST_PORT}:5432` | API도 이 loopback port를 사용하며 `db:5432`가 아니다 |
| AI host | same-LXC `http://127.0.0.1:8001` | Compose service가 아닌 별도 host process |

Docker socket/`docker inspect` 권한자는 container environment를 볼 수 있다. `0600`은 유출 범위를 줄일 뿐 이 신뢰 경계를 없애지 않는다.

## 2. 최초 파일 생성: 덮어쓰지 않기

다음은 **`/etc/emotionmap/secret.env`가 아직 없는 최초 설치에만** 쓰는 예시다. source root에서 실행한다고 가정한다. 기존 파일이 하나라도 있으면 오류로 끝나며 복사하지 않는다. 기존 설치에서는 이 블록을 실행하지 말고 3절의 editor로 승인된 항목만 수정한다.

```sh
# 운영자 절차 예시 — 이 문서는 실행하지 않음
(
  set -eu
  cd /srv/emotionmap/deploy/backend
  install -d -m 0700 /etc/emotionmap
  umask 077
  if [ -e /etc/emotionmap/secret.env ] || [ -L /etc/emotionmap/secret.env ]; then
    printf '%s\n' '기존 또는 symlink secret.env가 있어 덮어쓰지 않습니다.' >&2
    exit 1
  fi
  cp .env.example /etc/emotionmap/secret.env
  chmod 0600 /etc/emotionmap/secret.env
)
```

그 뒤 승인된 보안 editor를 명시적으로 연다. 예를 들어 권한이 있는 관리 shell에서는 다음처럼 한다.

```sh
sudoedit /etc/emotionmap/secret.env
```

`source`, `.`, `eval`로 dotenv를 shell에 읽어 들이지 않는다. `cat`, `printenv`, `docker inspect`, `docker compose config`처럼 값을 표시할 수 있는 명령도 증적이나 지원 요청에 쓰지 않는다.

### 선택 사항: 네 개의 새 secret만 한 번 생성해 채우기

직접 값을 복사/붙여넣지 않으려면, **방금 template에서 만든 첫 설치 파일이고 네 대상 assignment가 각각 정확히 하나이며 빈 경우에만** 다음 Python 3 표준 라이브러리 예시를 실행할 수 있다. 실행 시 서로 독립적인 `secrets.token_hex(32)` 네 개를 파일에만 기록한다. 값은 terminal/history에 출력하지 않으며, nonblank/duplicate/누락 대상이 있으면 변경 없이 실패한다. 기존 deployment의 DB password 변경/rotation 방법이 아니다.

```sh
# 운영자 절차 예시 — 이 문서는 실행하지 않음
python3 - <<'PY'
from pathlib import Path
import os
import secrets
import stat
import sys
import tempfile

path = Path('/etc/emotionmap/secret.env')
targets = ('DB_PASSWORD', 'POSTGRES_PASSWORD', 'JWT_SECRET', 'SIGNING_SECRET')
if path.is_symlink():
    sys.exit('secret.env must not be a symlink')
mode = stat.S_IMODE(path.stat().st_mode)
if mode != 0o600:
    sys.exit('secret.env mode must be 0600 before secret generation')
lines = path.read_text(encoding='utf-8').splitlines(keepends=True)
positions = {}
for name in targets:
    assignments = [
        (i, line.rstrip('\r\n'))
        for i, line in enumerate(lines)
        if line.rstrip('\r\n').startswith(name + '=')
    ]
    if len(assignments) != 1 or assignments[0][1] != name + '=':
        sys.exit(f'{name} must occur exactly once and be blank; no change made')
    positions[name] = assignments[0][0]
for name, index in positions.items():
    lines[index] = f'{name}={secrets.token_hex(32)}\n'
data = ''.join(lines).encode('utf-8')
temp_path = None
try:
    fd, temp_path = tempfile.mkstemp(prefix='.secret.env.', dir=path.parent)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, 'wb') as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temp_path, path)
    temp_path = None
    directory_fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)
finally:
    if temp_path is not None:
        os.unlink(temp_path)
PY
```

이 방법으로도 `DB_PASSWORD`(non-superuser application/CLI용)와 `POSTGRES_PASSWORD`(empty cluster 최초 bootstrap admin용)는 서로 다른 값이다. `JWT_SECRET`과 `SIGNING_SECRET`도 서로 다른 application secret이다. 계정 provisioning password는 이 env 파일의 입력이 아니며 [배포 가이드 6절](DEPLOY_GUIDE.md#6-최초의-빈-dataset-초기화-절차)의 승인된 숨김 TTY 절차를 사용한다.

## 3. dotenv 문법과 host shell 우선순위

한 줄에 `NAME=value`만 쓴다. `$`, `#`, 공백이 literal에 들어가면 single quote로 감싼다. single quote 자체는 `\'`로 escape한다. 줄바꿈/CR, command substitution, 여러 줄 shell quoting은 쓰지 않는다.

```dotenv
DB_PASSWORD='literal $ and # and a space'
SIGNING_SECRET='a literal quote is escaped here: it\'s one value'
```

Compose는 `--env-file`보다 **현재 host shell의 동명 환경 변수**를 우선할 수 있다. secret을 표시하지 않고 배포 전용 shell에서 충돌을 지우려면, 다음과 같이 이름만 unset한다. 이 예시는 파일을 읽거나 값을 출력하지 않는다.

```sh
for name in \
  COMPOSE_PROJECT_NAME API_IMAGE DB_NAME DB_NETWORK_SUBNET DB_IPV4_ADDRESS DB_HOST_PORT \
  API_SHUTDOWN_TIMEOUT API_STOP_GRACE_PERIOD DB_STOP_GRACE_PERIOD DB_USERNAME DB_PASSWORD \
  POSTGRES_PASSWORD JWT_SECRET SIGNING_SECRET PASSWORD_BCRYPT_STRENGTH \
  LOGIN_FAILED_ATTEMPT_WINDOW_SECONDS SERVICE_CONFIG_VERSION SERVICE_RADIUS_METERS \
  SERVICE_DEMO_CENTER_LAT SERVICE_DEMO_CENTER_LNG \
  SERVICE_LIMIT_MEMORY_CONTENT_MAX_CODE_POINTS \
  SERVICE_LIMIT_PREFERENCE_DESCRIPTION_MAX_CODE_POINTS \
  SERVICE_LIMIT_REPORT_DETAILS_MAX_CODE_POINTS SERVICE_LIMIT_IMAGE_MAX_BYTES \
  SERVICE_LIMIT_IMAGE_MAX_WIDTH SERVICE_LIMIT_IMAGE_MAX_HEIGHT SERVICE_LIMIT_IMAGE_MAX_PIXELS \
  SERVICE_LIMIT_IMAGE_UPLOAD_TTL_SECONDS SERVICE_LIMIT_ANALYSIS_TTL_SECONDS \
  SERVICE_LIMIT_DAILY_DIRECT_MEMORY_LIMIT SERVICE_LIMIT_DEFAULT_PAGE_LIMIT \
  SERVICE_LIMIT_MAX_PAGE_LIMIT SERVICE_LIMIT_MAX_MAP_PAGE_LIMIT CURSOR_TTL \
  SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS SERVICE_AUTH_FAILED_LOGIN_LIMIT \
  SERVICE_AUTH_LOGIN_LOCK_SECONDS APP_STORAGE_MULTIPART_REQUEST_OVERHEAD_BYTES \
  REQUEST_COORDINATION_LEASE_DURATION IMAGE_CLEANUP_INTERVAL IMAGE_CLEANUP_BATCH_SIZE \
  AI_TIMEOUT AI_ANALYSIS_MODEL AI_ANALYSIS_PROMPT_VERSION AI_MODERATION_MODEL \
  AI_MODERATION_PROMPT_VERSION AI_SERVICE_TOKEN CORS_ALLOWED_ORIGINS API_PORT \
  MATCH_RADIUS_METERS MATCH_FALLBACK_RADIUS_METERS MATCH_MIN_PLACES \
  MATCH_NOTIFY_THRESHOLD MATCH_VERIFY_THRESHOLD MATCH_SIMILARITY_WEIGHT \
  MATCH_DISTANCE_WEIGHT MATCH_PLACE_VIBE_SMOOTHING MATCH_MERGE_DISTANCE_METERS \
  MATCH_CATEGORY_CONFIDENCE_THRESHOLD MATCH_REVIEWS_FOR_VERIFY \
  MODERATION_RETRY_DELAY MODERATION_RETRY_INITIAL_DELAY
 do
  unset "$name"
done
```

이후에도 terminal에 값을 render하지 않고 interpolation만 검사한다. 모든 Compose 예시에는 동일한 protected env와 **두 absolute Compose 파일**을 포함한다.

```sh
# 운영자 절차 예시 — 이 문서는 실행하지 않음
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml config --quiet
```

`config --quiet` 성공은 누락/형식 interpolation의 조기 발견일 뿐이다. Java property binding, DB user/password, image 존재, AI 연결, container start 또는 runtime readiness를 검증하지 않는다.

## 4. 반드시 채우는 입력 (M)

`compose.yaml`은 아래 M 값을 `${NAME:?…}`로 요구한다. 같은 변수가 여러 곳에서 참조되어도 변수 하나로 센다. 현재 template/Compose 기준으로 M은 **46개**이며, 모두 아래 표에 있다. 조건부 `AI_SERVICE_TOKEN`과 `CORS_ALLOWED_ORIGINS`는 `${NAME:-}` 계약이므로 M 46개에 포함하지 않는다. 빈 M은 의도적으로 local/test default가 아니다.

### 4.1 identity, DB topology, 종료 시간 (9개)

| 변수 | 어떻게 정할지와 제약 | 설명용 값 (승인 default 아님) |
| --- | --- | --- |
| `COMPOSE_PROJECT_NAME` | activation 후 volume/network 이름이 바뀌지 않을 안정된 lowercase 식별자를 정한다. | `emotionmap_prod` |
| `API_IMAGE` | 준비된 exact image reference를 넣는다. `latest`는 쓰지 않는다. | `emotionmap-api:main-<full-SHA>` |
| `DB_NAME` | `[a-z_][a-z0-9_]{0,62}`; `postgres`, `template0`, `template1`, `pg_` 시작 이름은 금지다. | `emotionmap` |
| `DB_NETWORK_SUBNET` | 다른 Docker/LXC network와 겹치지 않는 IPv4 CIDR을 network 담당자가 정한다. activation 후 바꾸지 않는다. | `172.28.60.0/24` |
| `DB_IPV4_ADDRESS` | 위 CIDR 안의 host address이며 gateway/network/broadcast가 아니어야 한다. | `172.28.60.10` |
| `DB_HOST_PORT` | 사용 중이지 않은 loopback TCP `1..65535`; API/AI/host service와 충돌하지 않게 정한다. | `54329` |
| `API_SHUTDOWN_TIMEOUT` | Spring ISO-8601 `Duration` 양수. 예를 들어 `PT30S`; multi-phase shutdown의 실제 전체 관찰값보다 짧지 않게 승인한다. | `PT30S` |
| `API_STOP_GRACE_PERIOD` / `DB_STOP_GRACE_PERIOD` | Compose duration은 Spring 형식과 구별해 `45s`처럼 쓴다. API grace는 `API_SHUTDOWN_TIMEOUT`보다 길어야 하며, multi-phase shutdown이면 전체 관찰 시간을 덮어야 한다. DB도 별도 종료 관찰값보다 길게 정한다. 두 값 모두 별도 입력이다. | `45s` / `60s` |

### 4.2 DB·로그인·서명 보안 (7개)

| 변수 | 어떻게 정할지와 제약 | 설명용 값 (승인 default 아님) |
| --- | --- | --- |
| `DB_USERNAME` | `DB_NAME`와 같은 identifier 규칙을 만족하는 app role. superuser가 아닌 `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS` role이다. | `emotionmap_app` |
| `DB_PASSWORD` | application datasource와 internal CLI가 쓰는 nonempty single-line random secret. `POSTGRES_PASSWORD`와 반드시 다르게 한다. | editor/생성 절차로 입력 |
| `POSTGRES_PASSWORD` | empty PGDATA 최초 bootstrap의 `postgres` admin secret. API environment에는 전달되지 않는다. | editor/생성 절차로 입력 |
| `JWT_SECRET` | 다른 secret과 중복되지 않는 random UTF-8 최소 32 bytes. 바꾸면 기존 token 호환성 영향을 승인한다. | editor/생성 절차로 입력 |
| `SIGNING_SECRET` | JWT secret과 별개인 random UTF-8 최소 32 bytes. signing 용도와 DB password를 혼용하지 않는다. | editor/생성 절차로 입력 |
| `PASSWORD_BCRYPT_STRENGTH` | integer `4..31`; hardware, 공격 모델, login latency를 측정한 뒤 승인한다. | `12` |
| `LOGIN_FAILED_ATTEMPT_WINDOW_SECONDS` | integer `1..2147483647`; 실패 login을 셀 정책 window를 정한다. | `900` |

기존 PostgreSQL cluster에서는 env의 `DB_PASSWORD`나 `POSTGRES_PASSWORD`만 고쳐도 database credential이 자동 변경되지 않는다. DB secret rotation은 reset/volume 삭제/자동 password rotation이 아닌 별도 승인 migration 절차다.

### 4.2.1 PostgreSQL 최초 계정의 입력과 생성 경계

다음 세 종류를 같은 “DB 계정”으로 취급하지 않는다. `secret.env`는 빈 `PGDATA`를 처음 초기화할 때 Compose와 initializer에 전달하는 **입력**일 뿐이며, 이미 초기화된 cluster의 계정을 생성·수정·회전하는 명령이 아니다.

| 구분 | 값 또는 고정값 | 빈 `PGDATA` 최초 초기화에서 하는 일 | 하지 않는 일 |
| --- | --- | --- | --- |
| PostgreSQL 공식 image bootstrap admin | `POSTGRES_PASSWORD`; Compose가 `POSTGRES_USER=postgres`, `POSTGRES_DB=postgres`로 고정 | official entrypoint가 `postgres` admin과 `postgres` DB의 최초 인증을 준비한다. | API datasource나 application login 계정을 만들지 않는다. |
| 이 배포의 application DB role | `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | host에서 제공되는 `deploy/postgres/10-init-app-db.sh`가 custom non-superuser role을 만들고 `psql \password`로 secret을 설정한 뒤 application DB, `public` schema, vector를 준비한다. | `POSTGRES_*`의 admin 이름/DB를 바꾸지 않으며, application 사용자 계정을 만들지 않는다. |
| API application login 사용자 | `AccountProvisioningCli`의 승인된 별도 provisioning 절차 | API가 인증할 application account를 provision한다. | `DB_USERNAME` 또는 어떤 PostgreSQL env만으로 만들거나 DB admin/app role로 대체하지 않는다. |

따라서 `DB_PASSWORD`와 `POSTGRES_PASSWORD`는 서로 다른 secret으로 채운다. 이번 bootstrap 실패는 첫 `CREATE ROLE`의 logged colon syntax bug 때문에 발생했으므로 env 값만 채우거나 바꾸어도 원인을 고치거나 init script를 다시 실행하지 않는다. 빈 volume에 쓸 **수정된 host initializer**가 필요하며, 그 파일을 실제 LXC의 `/srv/emotionmap/deploy/backend/deploy/postgres/10-init-app-db.sh`에 승인된 방식으로 전달했는지 확인한다. 로컬 source 수정본이 commit/push되었거나 latest main에 이미 있다는 뜻은 아니며, 이 host-sourced initializer 변경에는 API image rebuild가 필요하지 않다.

정상적인, 아직 비어 있는 storage의 최초 순서는 다음과 같다. `secret.env`의 필수 값을 채우고 수정된 host initializer의 전달을 확인한 뒤, 모든 Compose 동작에 같은 protected env와 두 manifest를 쓰도록 helper를 정의한다.

```sh
# 운영자 절차 예시 — 이 문서는 실행하지 않음
dc() {
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml "$@"
}

dc config --quiet && dc up -d --wait --wait-timeout 180 db
```

`config --quiet`은 입력 interpolation만, 이어지는 `up ... --wait`는 Docker health 대기만 확인한다. 그 다음에는 [DEPLOY_GUIDE 6.1의 application TCP health 증적](DEPLOY_GUIDE.md#61-postgresql-최초-계정-생성과-초기화-실패-복구)에서 app credential TCP 인증 뒤 query 결과가 정확히 `t|t|t|t`인지 확인한다. 이는 차례로 vector extension, current user의 `public` `USAGE`, current user의 `public` `CREATE`, role의 `SUPERUSER`/`CREATEDB`/`CREATEROLE`/`REPLICATION`/`BYPASSRLS` 부재를 뜻한다. container status, `pg_isready`, socket admin query만으로 business readiness를 판단하지 않으며, 이어서 배포 가이드 6절의 남은 activation 절차도 수행한다.

app role이 **없다**는 사실은, 원래 빈 설치에서 정확히 이 첫 `CREATE ROLE` 오류가 났다는 확인된 사건일 때만 6.1의 엄격한 예외 복구를 검토할 단서다. 반대로 app role이 **이미 있는데 password가 틀린** 경우는 이미 존재하는 계정의 인증 불일치이므로 env를 고치거나 restart하는 복구가 아니다. 어느 경우든 기존 `PGDATA`에서 `secret.env`를 편집하고 container를 restart해도 role/database를 새로 만들거나 password를 회전하지 않는다. 특히 `docker compose exec`는 실행 중 container의 environment를 사용하므로, edit한 `secret.env`를 자동으로 다시 적용하지 않는다.

실패한 첫 초기화를 실제로 복구해야 할 때는 recovery SQL 또는 password 명령을 여기서 복제하지 않는다. data overwrite/reset을 계속 금지하고, traffic 중지·일관된 backup/snapshot·read-only catalog gate·사람의 명시적 승인·단 한 번의 mutation과 실패 시 중단을 모두 요구하는 [DEPLOY_GUIDE 6.1 PostgreSQL 최초 계정 생성과 초기화 실패 복구](DEPLOY_GUIDE.md#61-postgresql-최초-계정-생성과-초기화-실패-복구)만 따른다. 알려지지 않은 데이터, 기존 role/database, 변경된 env 또는 불확실한 이력이 있으면 그 예외를 적용하지 않는다.

### 4.3 service policy (20개)

이 값들은 `/v1/config` model의 필수 입력이다. 값이 있다고 quota/report/pagination 정책 집행이 증명되는 것은 아니며, invalid/missing이면 process가 떠도 configuration 503일 수 있다.

| 변수 | 선택 기준·단위·제약 | 설명용 값 (승인 default 아님) |
| --- | --- | --- |
| `SERVICE_CONFIG_VERSION` | 사람이 추적하는 nonempty policy revision을 정한다. | `2026-09-20-policy-1` |
| `SERVICE_RADIUS_METERS` | positive integer, meter. `MATCH_RADIUS_METERS`와 같은 값/alias가 아니다. | `1500` |
| `SERVICE_DEMO_CENTER_LAT` | 실제 승인된 demo 중심의 finite latitude, degree `[-90,90]`. | `37.5665` |
| `SERVICE_DEMO_CENTER_LNG` | 위 장소의 finite longitude, degree `[-180,180]`. | `126.9780` |
| `SERVICE_LIMIT_MEMORY_CONTENT_MAX_CODE_POINTS` | positive integer Unicode code points. | `2000` |
| `SERVICE_LIMIT_PREFERENCE_DESCRIPTION_MAX_CODE_POINTS` | positive integer Unicode code points. | `500` |
| `SERVICE_LIMIT_REPORT_DETAILS_MAX_CODE_POINTS` | positive integer Unicode code points. | `1000` |
| `SERVICE_LIMIT_IMAGE_MAX_BYTES` | bytes `1..2147483646`; upload/heap/capacity와 함께 정한다. | `10485760` |
| `SERVICE_LIMIT_IMAGE_MAX_WIDTH` | positive integer pixels. | `4096` |
| `SERVICE_LIMIT_IMAGE_MAX_HEIGHT` | positive integer pixels. | `4096` |
| `SERVICE_LIMIT_IMAGE_MAX_PIXELS` | positive `long` pixels; width×height와 decode/re-encode memory를 함께 제한한다. | `16000000` |
| `SERVICE_LIMIT_IMAGE_UPLOAD_TTL_SECONDS` | positive integer seconds; staged upload 보관 정책이다. | `3600` |
| `SERVICE_LIMIT_ANALYSIS_TTL_SECONDS` | positive integer seconds; analysis 보관 정책이다. | `86400` |
| `SERVICE_LIMIT_DAILY_DIRECT_MEMORY_LIMIT` | positive integer count/day. | `10` |
| `SERVICE_LIMIT_DEFAULT_PAGE_LIMIT` | positive integer count; `MAX_PAGE_LIMIT` 이하여야 한다. | `20` |
| `SERVICE_LIMIT_MAX_PAGE_LIMIT` | positive integer count; default 이상이어야 한다. | `100` |
| `SERVICE_LIMIT_MAX_MAP_PAGE_LIMIT` | positive integer count; map query capacity에 맞춘다. | `200` |
| `SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS` | positive integer seconds. | `3600` |
| `SERVICE_AUTH_FAILED_LOGIN_LIMIT` | positive integer failed-attempt count. | `5` |
| `SERVICE_AUTH_LOGIN_LOCK_SECONDS` | positive integer seconds. | `900` |

### 4.4 upload/request coordination과 AI provenance (9개)

| 변수 | 선택 기준·단위·제약 | 설명용 값 (승인 default 아님) |
| --- | --- | --- |
| `APP_STORAGE_MULTIPART_REQUEST_OVERHEAD_BYTES` | positive `long` H bytes. `IMAGE_MAX_BYTES` B와 `B + H`가 long overflow하지 않아야 하며 multipart overhead를 측정해 정한다. | `1048576` |
| `REQUEST_COORDINATION_LEASE_DURATION` | 양의 Spring `Duration`; nanosecond 변환 가능한 승인 lease 시간. | `PT30S` |
| `IMAGE_CLEANUP_INTERVAL` | 양의 Spring `Duration`; cleanup cadence를 storage workload에 맞춘다. | `PT15M` |
| `IMAGE_CLEANUP_BATCH_SIZE` | positive integer; 한 cleanup batch의 부하를 제한한다. | `100` |
| `AI_TIMEOUT` | 양의 Spring `Duration`. total deadline이 아니며 current HTTP factory의 최소 connect 2초/read 20초와 upstream latency를 고려한다. | `PT45S` |
| `AI_ANALYSIS_MODEL` | same-LXC 실제 AI host 운영자에게 받은 nonempty analysis model provenance를 그대로 입력한다. fake provider/model을 만들지 않는다. | **AI host 담당자 입력** |
| `AI_ANALYSIS_PROMPT_VERSION` | 실제 AI host가 사용하는 nonempty analysis prompt revision을 받는다. | **AI host 담당자 입력** |
| `AI_MODERATION_MODEL` | 실제 AI host 운영자에게 받은 nonempty moderation model provenance를 입력한다. | **AI host 담당자 입력** |
| `AI_MODERATION_PROMPT_VERSION` | 실제 AI host moderation prompt revision을 입력한다. | **AI host 담당자 입력** |

AI provenance 값은 backend 기록 입력일 뿐 remote AI가 해당 model/prompt를 실제 사용했다는 증거는 아니다. real endpoint와 timeout/error/axes mapping은 traffic 전 별도로 검증한다.

### 4.5 지도 cursor 유효기간 (1개)

| 변수 | 선택 기준·단위·제약 | 설명용 값 (승인 default 아님) |
| --- | --- | --- |
| `CURSOR_TTL` | `app.pagination.cursor-ttl`; 양의 Spring `Duration`. bare numeral은 초다. 빈 값·0·음수는 허용하지 않으며 숫자 기본값이 없다. | `PT15M` |

이 값은 현재 `GET /v1/places`의 cursor 발급·검증에만 사용한다. 첫 page의 만료는
`now + CURSOR_TTL`을 epoch second로 올림하며, 후속 page는 최초 만료를 유지한다.
`Instant` 덧셈 또는 epoch-second 올림이 overflow하면 요청은 `CONFIGURATION_UNAVAILABLE`(503)으로
거절된다. 다른 목록·analysis/auth/image TTL 설정을 대체하지 않는다.

## 5. 조건부(C), optional(O), fixed/derived(F)

### 조건부 입력 (2개)

| 변수 | 언제/어떻게 정할지 |
| --- | --- |
| `AI_SERVICE_TOKEN` | AI host contract가 shared `X-AI-Token`을 요구하고 수용할 때만 nonblank로 채운다. DB/admin secret 또는 provider/Claude key를 재사용하지 않는다. 그렇지 않으면 빈 칸으로 둔다. |
| `CORS_ALLOWED_ORIGINS` | TLS proxy와 다른 browser origin이 실제로 필요할 때만 exact comma-separated origin을 쓴다. 예: `https://app.example.invalid`은 형식 예시일 뿐 승인 origin이 아니다. blank는 cross-origin 불허이며 wildcard는 기동 거절이다. Tailscale Serve topology를 승인해 browser origin이 생기는 경우에만 [TAILSCALE_SERVE_GUIDE.md](TAILSCALE_SERVE_GUIDE.md)를 함께 검토한다. |

### 구현된 선택 override (14개)

이 항목을 빈 칸으로 둔다고 “무시”되는 것이 아니라 current Compose의 `:-` fallback 또는 application default가 전달된다. O default도 승인된 운영 정책이 아니다.

| 변수 | blank 시 current 값 | 채울 때 제약 |
| --- | --- | --- |
| `API_PORT` | `8080` | loopback TCP port; reverse proxy/listener와 충돌하지 않게 정한다. |
| `MATCH_RADIUS_METERS` | `1000` | meter, `>=1`. |
| `MATCH_FALLBACK_RADIUS_METERS` | `3000` | meter, `>= MATCH_RADIUS_METERS`. |
| `MATCH_MIN_PLACES` | `3` | integer `>=1`. |
| `MATCH_NOTIFY_THRESHOLD` | `0.85` | `0 <= VERIFY <= NOTIFY <= 1`. |
| `MATCH_VERIFY_THRESHOLD` | `0.65` | 위 threshold 관계를 만족한다. |
| `MATCH_SIMILARITY_WEIGHT` | `0.85` | current code는 합/범위를 검증하지 않으므로 정책상 명시적으로 검토한다. |
| `MATCH_DISTANCE_WEIGHT` | `0.15` | 위와 같다. |
| `MATCH_PLACE_VIBE_SMOOTHING` | `2` | count `>=0`. |
| `MATCH_MERGE_DISTANCE_METERS` | `20` | meter `>=0`. |
| `MATCH_CATEGORY_CONFIDENCE_THRESHOLD` | `0.6` | `[0,1]`. |
| `MATCH_REVIEWS_FOR_VERIFY` | `5` | integer `>=1`. |
| `MODERATION_RETRY_DELAY` | `PT5M` | Spring `Duration`; explicit override면 양수 정책값을 쓴다. |
| `MODERATION_RETRY_INITIAL_DELAY` | `PT1M` | Spring `Duration`; blank initial delay로 fallback을 지우지 않는다. |

### Compose가 고정하거나 유도하는 값 (F)

아래는 `secret.env`에 추가/override하지 않는다. `env_file:`로 전체 dotenv를 주입하지 않고 Compose의 service별 allowlist가 전달한다.

- DB: `POSTGRES_USER=postgres`, `POSTGRES_DB=postgres`, `POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256`, `DB_URL=jdbc:postgresql://127.0.0.1:${DB_HOST_PORT}/${DB_NAME}?currentSchema=public`, `DB_SCHEMA=public`.
- API runtime: `SPRING_PROFILES_ACTIVE=prod`, `APP_UPLOAD_DIR=/var/lib/emotionmap/uploads`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=${API_PORT:-8080}`, `SERVER_SHUTDOWN=graceful`, `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=${API_SHUTDOWN_TIMEOUT}`.
- schema/AI: `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SPRING_FLYWAY_ENABLED=true`, `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`, `SPRING_FLYWAY_CLEAN_DISABLED=true`, `AI_PROVIDER=http`, `AI_BASE_URL=http://127.0.0.1:8001`.

## 6. 최신 main image와 env 반영

`api` Compose service에는 `build:`가 없다. 즉 `docker compose up`은 source에서 API image를 build하지 않는다. latest main을 명시적으로 선택했을 때는 [DEPLOY_GUIDE 3.3절](DEPLOY_GUIDE.md#33-빠른-배포--릴리스-태그-없이-매번-최신-main-사용)의 fetch/clean-tree/build block이 현재 commit의 **전체 SHA**로 자동 tag한 `emotionmap-api:main-<full-SHA>`를 만든다.

그 마지막 `API_IMAGE=emotionmap-api:main-<full-SHA>`만 protected env의 `API_IMAGE`에 반영한다. 이 경로에 Git release tag 생성이나 승인 tag placeholder는 필요 없다. 다만 image tag 자체가 digest 불변성을 보장하는 것은 아니며, main의 Compose/env/migration compatibility 검토와 배포 가이드의 traffic/backup gate는 그대로 필요하다.

새 image 또는 API env 변경은 단순 `restart`가 아니라 approved maintenance window에서 API container를 recreate해야 반영된다. DB를 recreate하거나 volume을 reset하는 명령은 사용하지 않는다.

```sh
# 운영자 절차 예시 — traffic drain 및 DEPLOY_GUIDE update gate 뒤에만
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml \
  up -d --no-deps --force-recreate api
```

## 7. 흔한 문제를 안전하게 구분하기

| 증상 | 안전한 해석과 다음 조치 |
| --- | --- |
| `must be set`, interpolation 오류, `config --quiet` 실패 | 누락/blank M 이름과 dotenv 철자를 확인한다. `.env.example`로 기존 env를 덮어쓰지 말고 해당 승인 항목만 editor에서 수정한다. |
| 수정했는데 이전 값이 적용되는 것 같다 | 현재 deployment shell의 동명 host variable이 우선했을 수 있다. 3절의 name-only `unset` block을 새 shell에서 실행하고 다시 `config --quiet`으로 interpolation만 확인한다. 값을 출력하지 않는다. |
| 기존 DB의 password만 env에서 바꿨다 | existing cluster의 user password는 변경되지 않는다. login 실패를 volume delete/reinitialize로 해결하지 말고 data overwrite/reset 없이 보존·중지·조사 후 승인된 DB credential rotation 절차를 사용한다. 단, 원래 빈 설치에서 기록된 첫 `CREATE ROLE` colon 오류라는 정확한 조건을 검증한 경우에만 [DEPLOY_GUIDE 6.1의 gated recovery](DEPLOY_GUIDE.md#61-postgresql-최초-계정-생성과-초기화-실패-복구)를 따른다. |
| `API_IMAGE`를 바꾸거나 API env를 편집했다 | image가 이미 준비되어 있고 `config --quiet`이 성공한 것을 전제로, maintenance 절차에서 API를 `--force-recreate` 한다. `restart api`만으로는 새 environment/image activation을 대신하지 않는다. |
| API image를 activate하려 한다 | exact `API_IMAGE`가 local build 또는 승인 registry pull로 준비되어 있고, protected env/두 Compose files/LXC override가 존재하며, DB storage/AI loopback/traffic gates가 [DEPLOY_GUIDE](DEPLOY_GUIDE.md)에 따라 준비되었는지 확인한다. Compose가 image build, empty storage activation 또는 business readiness를 자동으로 증명하지 않는다. |
| CORS가 필요한 browser가 생겼다 | 정확한 public browser origin만 `CORS_ALLOWED_ORIGINS`에 넣는다. proxy/TLS와 Tailscale topology가 관련될 때에만 Tailscale guide를 검토하며 wildcard나 추측한 origin을 넣지 않는다. |

이 문서는 config interpolation 안내만 제공한다. 실제 container start, health, authenticated business readiness, database/image persistence, AI endpoint와 external exposure 검증은 수행하지 않았으며 [DEPLOY_GUIDE.md](DEPLOY_GUIDE.md)의 별도 절차와 승인 대상이다.
