# 운영 배포 가이드 — Ubuntu 24.04 unprivileged LXC의 API + PostgreSQL Compose

> **상태: 구성 계약 문서이며, 배포 완료 증명서가 아니다.** 이 문서는 하나의 Ubuntu
> 24.04 unprivileged Proxmox LXC 안에서 rootful Docker Engine과 Compose plugin으로 API와
> PostgreSQL을 각각의 container로 운용하는 절차를 설명한다. 명령은 나중에 권한을 받은
> 운영자가 실행할 예시일 뿐이다. 이 문서 갱신에서 Docker 설치·build/pull·container 시작·DB
> bootstrap·Flyway·AI 호출·public traffic 개방은 모두 **NOT_RUN**이다.

## 1. 처음 보는 운영자를 위한 순서

이 가이드는 Docker 설치 튜토리얼이 아니다. 유지보수되는 **Linux Docker Engine 28 이상**과
Compose plugin이 이미 LXC 안에 준비되어 있고, 명령을 실행하는 사람은 **LXC root 또는 승인된 Docker admin**이라고
전제한다. 이 LXC의 Docker daemon은 rootful이다. `docker` group과 Docker socket 접근은 사실상
root 수준의 container-admin 권한을 주며, `docker inspect` 권한자는 container environment를 볼 수
있다. `0600` env 파일은 일반 사용자·Git 저장소·build context로부터의 유출을 줄일 뿐 이 신뢰
경계를 제거하지 않는다.

처음 배포할 때는 다음 순서를 지킨다. 각 단계는 승인 후의 절차이며, 이 문서 자체가 실행하라는
지시는 아니다.

1. Proxmox에서 2절의 LXC와 mount 계약을 만들고, LXC 안에서 각 mount를 확인한다. mount가 없으면
   **멈춘다**. 빈 data directory를 만들거나 Docker volume을 만들지 않는다.
2. 3절의 고정 경로에 release source, protected env, PostgreSQL/image data를 분리한다.
3. 승인된 immutable release를 3.1절처럼 clone/checkout한다. **릴리스 태그 없이 최신 main을
   빠르게 배포하려면 대신 3.3절을 따른다.** 저장소 root의 `compose.yaml`, `Dockerfile`,
   `.env.example`을 검토한다. `compose.lxc.yaml`은 3.2절에서 저장소 밖에 만든다.
4. `/etc/emotionmap/secret.env`를 `.env.example`에서 만들고 승인된 값만 채운다. secret을
   source, print, Git add 또는 build context에 넣지 않는다.
5. **새롭고 비어 있는** storage일 때만 host-only override를 만든다. 기본 compose만으로는
   `/srv` storage를 사용하지 않는다.
6. release 경로는 5절에서, 최신 main 경로는 3.3절에서 API image를 build한다. 이미 승인된
   registry image도 사용할 수 있다. `API_IMAGE`에는 실제 준비한 exact reference를 넣는다.
7. 6절의 empty-only DB/storage activation과 readiness 절차를 수행한다. API health와 business
   readiness는 서로 다르며, 모두 확인되기 전에는 public traffic을 열지 않는다.

## 2. LXC, Proxmox storage와 고정 토폴로지

### 2.1 Proxmox UI/CT 기본값

이 계약의 CT는 **Ubuntu 24.04, unprivileged**이다. Proxmox UI에서 `nesting`과 `keyctl`을 enabled로
한다. mount options는 기본값인 빈 값으로 둔다. managed mount point `mp0`, `mp1`은 Backup을
checked로 하고, single-node에서 Skip replication은 unchecked로 둔다. 이것은 replication job을
자동으로 만들지 않는다. 백업 schedule, retention, restore rehearsal은 별도로 승인한다.

| Proxmox 항목 | LXC 안의 mountpoint | Docker가 실제 사용할 clean data directory |
| --- | --- | --- |
| `mp0` | `/srv/emotionmap/postgres` | `/srv/emotionmap/postgres/data` |
| `mp1` | `/srv/emotionmap/uploads` | `/srv/emotionmap/uploads/data` |

새로 만든 전용 empty storage라도 filesystem root에는 `lost+found` 등이 있을 수 있다. PostgreSQL
empty-PGDATA 및 V9 empty-root 검사는 그러한 root를 비어 있다고 보지 않을 수 있으므로, **mount root
자체가 아닌** 위의 새 `data` subdirectory를 쓴다. 다음 확인을 먼저 하고, 두 명령 모두 성공한
경우에만 새 storage의 해당 subdirectory를 만든다. mountpoint가 없거나 기대와 다르면 `mkdir`,
Docker volume 생성 및 초기화를 하지 않고 멈춰 조사한다.

```sh
# 운영자 절차 예시 — 이 문서에서 실행하지 않음
findmnt --mountpoint /srv/emotionmap/postgres &&
findmnt --mountpoint /srv/emotionmap/uploads &&
mkdir /srv/emotionmap/postgres/data /srv/emotionmap/uploads/data &&
chown 10001:10001 /srv/emotionmap/uploads/data &&
chmod 0750 /srv/emotionmap/uploads/data
```

마지막 `chown`은 **새로 준비한 빈 uploads data directory 한 개에만** 적용한다. 기존 DB/image
data에 재귀 `chown`을 하지 않는다. PostgreSQL directory의 ownership은 Docker image가 initialization
시 처리하도록 둔다. LXC가 unprivileged라는 이유로 Proxmox host의 guessed UID mapping을 추측하거나
설정하지 않는다.

### 2.2 컨테이너와 경로 map

| 구성 요소 | 고정 계약 | 운영상 의미 |
| --- | --- | --- |
| Source | `/srv/emotionmap/deploy/backend` | source는 data volume 아래가 아니다. 모든 Compose/build 명령의 working directory다. |
| Protected env | `/etc/emotionmap/secret.env` | repo/build context 밖의 protected Compose dotenv file이다. |
| LXC override | `/srv/emotionmap/deploy/compose.lxc.yaml` | repo 밖의 host-only file이며 named volume driver options만 override한다. |
| Docker | 동일 LXC의 rootful Engine + Compose plugin | API와 PostgreSQL은 별도 container지만 같은 LXC의 lifecycle 영향을 함께 받는다. |
| API (`api`) | `network_mode: host`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=${API_PORT:-8080}` | `ports`/`networks`를 API에 추가하지 않는다. API가 host network를 쓰는 trade-off로 container network 격리를 포기한다. |
| AI host | 같은 LXC의 별도 host process, `127.0.0.1:8001` | API는 `AI_BASE_URL=http://127.0.0.1:8001`을 쓴다. AI를 Compose service로 추가하지 않으며 loopback은 인증의 대체가 아니다. |
| DB (`db`) | `db_network` bridge의 fixed IPv4, `127.0.0.1:${DB_HOST_PORT}:5432` publish | API는 host loopback DB port를 쓴다. `db:5432`로 바꾸지 않는다. DB를 외부에 publish하지 않는다. |
| Persistent data | `db_data` → `/var/lib/postgresql/data`; `image_data` → `/var/lib/emotionmap/uploads` | named volumes는 `COMPOSE_PROJECT_NAME`으로 project-scoped다. 백업 자체가 아니다. |
| External exposure | Compose에 proxy/TLS가 없다 | TLS/auth/firewall을 갖춘 별도 reverse proxy 전에는 API 직접 port를 외부 공개하지 않는다. |

`db` image는
`pgvector/pgvector:0.8.6-pg17-bookworm@sha256:cf134a767f474095eeba57e0117be8e568e011a63f33fbf252f14c9b760f8e6f`
로 pin되어 있다. PostgreSQL/vector image나 major version 변경은 별도 호환성 검증 대상이다.
API는 UID:GID `10001:10001`, read-only rootfs, `1777` `/tmp` tmpfs, `cap_drop: ALL`,
`no-new-privileges`로 실행된다. rootless/NFS/공유 원격 volume의 FileLock/fsync 의미는 이 계약이
보장하지 않는다.

LXC/Docker daemon/host reboot은 API와 DB를 모두 멈추게 한다. 현재 compose에는 `restart:` policy가
없으므로 자동 restart가 있다고 약속하지 않는다.

## 3. release source와 LXC-local named-volume override

### 3.1 source checkout

저장소와 data mount를 섞지 않는다. origin은 다음 URL로 확인되어 있다.

```sh
# 운영자 절차 예시 — 이 문서에서 실행하지 않음
install -d -m 0750 /srv/emotionmap/deploy
git clone https://github.com/KOSSCCHTHON-Team4/Backend.git /srv/emotionmap/deploy/backend
cd /srv/emotionmap/deploy/backend
git fetch --tags origin
git checkout --detach '<approved-immutable-release>'
git rev-parse HEAD
```

`<approved-immutable-release>`는 승인한 전체 commit ID로 교체하는 **비실행 placeholder**다.
서명된 release tag를 쓴다면 검증한 tag의 commit ID와 일치하는지도 확인한다. tag 이름만으로
불변성이 보장되지는 않는다. 이 release 경로에서는 자동으로 `main`/`latest`를 deploy하지 않는다.
**최신 main 직접 배포는 3.3절의 명시적 예외**다. 두 경로 모두 `git pull`, `git reset --hard`,
기존 working tree 삭제로 source를 바꾸지 않는다.
운영자는 승인한 reference와 나온 commit ID를 변경 기록에 대조한다. 이후의 `docker build`와 모든
Compose 명령은 이 directory에서 실행한다.

### 3.2 base Compose와 `/srv` storage의 관계

`compose.yaml`의 `db_data`, `image_data` top-level definition은 base named volume이다. base compose
단독 실행은 Docker default volume storage를 사용하며 **`/srv/emotionmap/...` mount를 사용하지
않는다.** Proxmox host bind mount가 아니라, 아래 방식은 Proxmox storage-backed LXC mount 위에
LXC-local Docker bind-backed named volume을 두는 것이다.

새 data storage에서는 Docker가 처음 volume을 만들기 **전에** 다음 file을 정확히
`/srv/emotionmap/deploy/compose.lxc.yaml`로 만든다. Docker Compose volume syntax와 local-driver
bind pattern은 [Compose volumes reference](https://docs.docker.com/reference/compose-file/volumes/)를
따른다. service mount target과 named volume 이름은 base contract 그대로다.

```yaml
volumes:
  db_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /srv/emotionmap/postgres/data
  image_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /srv/emotionmap/uploads/data
```

기존 deployment는 live named volume의 options/data를 이 file로 조용히 바꾸지 않는다. volume,
driver options 또는 data를 삭제·rebind·switch하지 않는다. approved migration이 필요하면 writers를
중지하고, DB와 images를 보존한 migration/restore plan을 승인한 뒤에만 별도로 수행한다.

이 문서의 이후 모든 Compose operation은 **같은 env file과 두 Compose file**을 반드시 함께 쓴다:

```sh
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml
```

위 줄은 command prefix의 표시이며, 아래 각 예시는 copy/paste 안전성을 위해 완전한 form을 쓴다.

### 3.3 빠른 배포 — 릴리스 태그 없이 매번 최신 main 사용

이 경로는 **승인된 release tag/commit을 미리 지정하는 제약을 적용하지 않는다.**
매번 `origin`의 `main`을 fetch하고 **그 fetch 시점의 최신 commit**을 build한다. 배포 도중 main이
더 진행하면 다음 실행 때 반영된다. 자동 배포 watcher가 아니며, 새 main을 반영할 때마다 아래
절차를 다시 실행한다. 검증된 release보다 회귀·migration 위험이 크고, API 교체 중 중단이 생긴다.
기존 DB/image data가 있다면 7절의 일관된 백업과 migration 호환성 확인은 생략하지 않는다.

**처음 설치하는 LXC**에서는 2절의 mount 준비 후 아래 clone을 한 번만 실행한다.
이미 `/srv/emotionmap/deploy/backend`가 있으면 clone은 생략한다. 3.1절의 release checkout은
실행하지 않는다. source/data/env/override 경로는 다른 배포 경로와 동일하다.

```sh
install -d -m 0750 /srv/emotionmap/deploy &&
git clone --branch main https://github.com/KOSSCCHTHON-Team4/Backend.git /srv/emotionmap/deploy/backend
```

**최초 배포와 이후 업데이트 공통:** 다음 블록을 통째로 실행한다. 로컬 수정이나 untracked file이
있으면 덮어쓰지 않고 중단한다. 변경분을 별도로 보존·정리한 뒤 다시 실행하며, `reset --hard`나
`clean -fd`로 지우지 않는다. `origin`은 위 Backend 저장소여야 한다.

```sh
(
  set -eu
  cd /srv/emotionmap/deploy/backend
  if [ -n "$(git status --porcelain --untracked-files=all)" ]; then
    printf '%s\n' '로컬 변경이 있어 중단합니다. 변경분을 보존·정리한 뒤 재시도하세요.' >&2
    exit 1
  fi
  git fetch origin main
  git checkout --detach FETCH_HEAD
  COMMIT=$(git rev-parse HEAD)
  docker build -t "emotionmap-api:main-${COMMIT}" .
  printf 'API_IMAGE=emotionmap-api:main-%s\n' "$COMMIT"
)
```

fetch/checkout/build 중 하나라도 실패하면 이후 배포를 진행하지 않는다. 성공하면 마지막에 나온
`API_IMAGE=emotionmap-api:main-<전체 SHA>` 한 줄을 `/etc/emotionmap/secret.env`의 `API_IMAGE`에
반영한다. **Git release tag를 만들거나 승인 tag placeholder를 채울 필요가 없다.** 위 Docker
image tag는 commit으로 자동 생성하는 버전 식별자이며, moving `latest` tag를 쓰지 않는다.
같은 commit을 다시 build하면 같은 tag를 덮어쓸 수 있으므로 이 tag 자체가 image digest 수준의
불변성을 보장하지는 않는다. main에서 Compose/환경변수 계약이 달라졌다면 변경분을 검토하고
필수 입력을 반영하되, 기존 env 파일 전체를 `.env.example`로 덮어쓰지 않는다.
shell의 동명 `API_IMAGE`가 env 파일을 덮어쓰지 않도록 4.1절도 확인한다.

- **최초 배포:** 3.2절의 override와 4절의 env를 준비하고 위 image reference를 넣은 뒤,
  **6절 전체**를 수행한다. 아래 업데이트 명령만으로 DB/storage activation을 대체하지 않는다.
- **이미 초기화된 서비스 업데이트:** build와 env 수정이 끝나면 traffic을 차단하고 기존 요청을
  drain한 뒤 아래 블록을 실행한다. DB는 이미 실행 중이며 healthy여야 한다.

```sh
(
  set -eu
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml config --quiet
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml stop api
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml up -d --no-deps --force-recreate api
)
```

`--force-recreate`는 같은 commit을 재빌드한 경우에도 준비한 image로 API를 교체한다.
DB container를 재생성하지 않지만 API 기동의 Flyway migration은 DB를 변경할 수 있다.
기동 후 6절의 health·업무 준비 검증을 통과한 뒤 traffic을 재개한다. 실패하면 traffic을 닫아
둔 채 조사한다. 이전 image로 되돌리는 것만으로 DB migration이 취소되지는 않는다.
`storage-init-empty` 재실행, volume 삭제 또는 dataset/root 재연결은 업데이트 방법이 아니다.

## 4. protected Compose 환경 파일

`.env.example`은 값 없는 format template다. **최초 배포 때만** repo root에서 이를
`/etc/emotionmap/secret.env`로 복사하고 parent directory mode `0700`, file mode `0600`을 유지한다.
기존 env 파일이 있으면 덮어쓰지 말고 승인된 변경분만 편집한다. `/etc` 준비와 파일 생성은 LXC
root 또는 해당 경로에 권한이 있는 관리자가 수행한다. secret을 `source`/`.`/`eval`/print하지 않는다.

```sh
# 운영자 절차 예시 — 이 문서에서 실행하지 않음
install -d -m 0700 /etc/emotionmap
umask 077
test ! -e /etc/emotionmap/secret.env &&
cp .env.example /etc/emotionmap/secret.env &&
chmod 0600 /etc/emotionmap/secret.env
# 승인된 보안 편집기로 값 작성; terminal, shell history, Git에 값을 남기지 않음
```

### 4.1 dotenv literal과 shell 우선순위

- `$`, `#`, 공백을 포함하는 literal은 Compose dotenv의 single-quoted 값으로 쓴다. single quote는
  `\'`로 escape한다. 줄바꿈/CR, shell quoting 연결, command substitution을 쓰지 않는다.
  ```dotenv
  DB_PASSWORD='literal $ and # and a space'
  SIGNING_SECRET='a literal quote is escaped here: it\'s still one value'
  ```
- host shell의 동명 환경 변수는 `--env-file` 값보다 우선할 수 있다. 값을 출력하지 말고 제한된
  운영 shell에서 충돌하는 `DB_PASSWORD`, `JWT_SECRET`, `COMPOSE_PROJECT_NAME` 등을 제거한다.
- 실제 값을 render하는 `docker compose config`, `config --environment`, `docker inspect`, process
  argv/environment dump를 증적에 남기지 않는다. 다음 `config --quiet`은 interpolation 확인일
  뿐 Java binding, DB 권한, image build 또는 readiness 증명이 아니다.
  ```sh
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml config --quiet
  ```

### 4.2 M — 반드시 채우는 Compose 입력

아래 M은 Compose `${NAME:?…}`가 요구하는 입력이다. 비밀은 build ARG/image ENV/COPY, URL query,
JVM argv에 넣지 않는다.

#### 배포 identity·network·중지 시간

| 변수 | Compose/설정 binding | 단위·범위·제약 |
| --- | --- | --- |
| `COMPOSE_PROJECT_NAME` | project/volume/network 이름 | activation 뒤 바꾸면 다른 named volume/network를 가리킬 수 있다. 안정적 식별자여야 한다. |
| `API_IMAGE` | `api.image` | release 또는 3.3절의 main commit별 image reference; moving `latest` 금지. |
| `DB_NAME` | bootstrap DB, JDBC database | `[a-z_][a-z0-9_]{0,62}`; `postgres`, `template0`, `template1`, `pg_` 시작 이름 금지. |
| `DB_NETWORK_SUBNET` | `db_network.ipam.config.subnet` | 겹치지 않는 IPv4 CIDR; activation 뒤 변경 금지. |
| `DB_IPV4_ADDRESS` | DB fixed IPv4 | subnet 안의 유효 host address; network/broadcast/gateway 금지. |
| `DB_HOST_PORT` | DB loopback publish와 `DB_URL` | TCP `1..65535`; API/AI/host service와 충돌하지 않는다. |
| `API_SHUTDOWN_TIMEOUT` | Spring lifecycle timeout | 양의 Spring `Duration`. |
| `API_STOP_GRACE_PERIOD` / `DB_STOP_GRACE_PERIOD` | 각 `stop_grace_period` | Compose duration; 관찰한 shutdown보다 길게 승인한다. |

#### DB·서명·로그인 보안

| 변수 | binding | 단위·범위·제약 |
| --- | --- | --- |
| `DB_USERNAME` | bootstrap app owner, datasource, 내부 CLI | `DB_NAME` 식별자 규칙; `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS` app role이다. |
| `DB_PASSWORD` | app datasource/CLI/bootstrap | nonempty single-line secret; `POSTGRES_PASSWORD`와 다르고 app/CLI만 쓴다. |
| `POSTGRES_PASSWORD` | 최초 DB bootstrap admin | nonempty single-line secret; API environment에 전달되지 않으며 기존 cluster env 변경만으로 rotation되지 않는다. |
| `JWT_SECRET` / `SIGNING_SECRET` | app JWT/signing secret | 서로 다른 random UTF-8 최소 32 bytes; shared/development value 금지. |
| `PASSWORD_BCRYPT_STRENGTH` | BCrypt/account CLI | integer `4..31`; 운영 권장값은 hardware/attack model/latency 관측 뒤 승인한다. |
| `LOGIN_FAILED_ATTEMPT_WINDOW_SECONDS` | login window | integer `1..2147483647`; 오류/누락은 login `CONFIGURATION_UNAVAILABLE`(503)일 수 있고 health down을 보장하지 않는다. |

#### M — 구현된 지도 cursor 설정

| 변수 | binding | 단위·범위·실패 경계 |
| --- | --- | --- |
| `CURSOR_TTL` | `app.pagination.cursor-ttl` | 양의 Spring `Duration`. bare numeral은 초다. 값은 비어 있거나 0/음수일 수 없고, `Instant` 덧셈 또는 epoch-second 올림이 overflow하면 map 요청은 `CONFIGURATION_UNAVAILABLE`(503)으로 fail-closed 된다. 숫자 기본값은 없다. |

이 설정은 현재 구현된 `GET /v1/places` cursor 발급·검증 요청에서만 소비된다. 첫 page expiry는 `now + CURSOR_TTL`을 epoch second로 올림해 TTL보다 일찍 만료하지 않으며 추가 시간은 1초 미만이다. continuation은 최초 expiry를 보존하고 TTL을 연장하지 않는다. 이 설명은 다른 목록 pagination, analysis/auth/image TTL 또는 전체 pagination/error 계약의 구현을 뜻하지 않는다.

DB initializer는 empty PGDATA의 official-image initialization에서만 app role/DB/public schema/vector를
만든다. app은 Flyway DDL owner일 수 있지만 cluster superuser가 아니다. initializer 실패/중단 뒤
기존 PGDATA에서 자동 재실행되지 않는다. volume 삭제/reset 대신 보존·조사·승인 복구 절차를 쓰며,
보고된 **정확한 최초 `CREATE ROLE` 실패**에만 적용하는 수동 단회 예외는 6.1절이다.

#### M — service policy (`app.service.*`)

이 값은 `/v1/config` model의 필수 입력이다. 누락/범위 오류면 process가 살아 있어도
`ServiceConfigProvider.current()`가 `CONFIGURATION_UNAVAILABLE`(503)일 수 있다. 값 존재가 모든
quota/report/pagination/map 정책 집행을 뜻하지 않으며 `SERVICE_RADIUS_METERS`는
`MATCH_RADIUS_METERS` alias가 아니다.

| 변수 | source binding | 단위·허용 범위 |
| --- | --- | --- |
| `SERVICE_CONFIG_VERSION` | `app.service.config-version` | nonempty string |
| `SERVICE_RADIUS_METERS` | `app.service.radius-meters` | positive integer, m |
| `SERVICE_DEMO_CENTER_LAT` / `SERVICE_DEMO_CENTER_LNG` | demo center | finite `Double`, 각각 `[-90,90]`, `[-180,180]` degree |
| `SERVICE_LIMIT_MEMORY_CONTENT_MAX_CODE_POINTS` | memory content limit | positive integer, Unicode code points |
| `SERVICE_LIMIT_PREFERENCE_DESCRIPTION_MAX_CODE_POINTS` | preference description limit | positive integer, Unicode code points |
| `SERVICE_LIMIT_REPORT_DETAILS_MAX_CODE_POINTS` | report details limit | positive integer, Unicode code points |
| `SERVICE_LIMIT_IMAGE_MAX_BYTES` | image byte limit | bytes `1..2147483646` |
| `SERVICE_LIMIT_IMAGE_MAX_WIDTH` / `SERVICE_LIMIT_IMAGE_MAX_HEIGHT` | image dimensions | positive integer pixels |
| `SERVICE_LIMIT_IMAGE_MAX_PIXELS` | image pixel limit | positive `long` pixels |
| `SERVICE_LIMIT_IMAGE_UPLOAD_TTL_SECONDS` / `SERVICE_LIMIT_ANALYSIS_TTL_SECONDS` | TTL | positive integer seconds |
| `SERVICE_LIMIT_DAILY_DIRECT_MEMORY_LIMIT` | direct memory limit | positive integer count |
| `SERVICE_LIMIT_DEFAULT_PAGE_LIMIT` / `SERVICE_LIMIT_MAX_PAGE_LIMIT` / `SERVICE_LIMIT_MAX_MAP_PAGE_LIMIT` | page limits | positive integer count; default `<=` max |
| `SERVICE_AUTH_ACCESS_TOKEN_TTL_SECONDS` | access token TTL | positive integer seconds |
| `SERVICE_AUTH_FAILED_LOGIN_LIMIT` / `SERVICE_AUTH_LOGIN_LOCK_SECONDS` | login guard | positive integer count / seconds |

`SERVICE_LIMIT_IMAGE_MAX_WIDTH * SERVICE_LIMIT_IMAGE_MAX_HEIGHT`,
`SERVICE_LIMIT_IMAGE_MAX_PIXELS`, decode/re-encode memory, byte limit을 실제 workload와 함께 승인한다.
parse/model constraint만으로 capacity 적합성이 증명되지는 않는다.

#### M — storage·request coordination

| 변수 | source binding | 단위·범위·상태 |
| --- | --- | --- |
| `APP_STORAGE_MULTIPART_REQUEST_OVERHEAD_BYTES` | multipart overhead | positive `long` H bytes; image max B와 `B + H`가 long overflow하지 않아야 하며 오류면 upload gate는 503으로 닫힌다. |
| `REQUEST_COORDINATION_LEASE_DURATION` | request lease | 양의 Spring `Duration`; duration/nanosecond conversion 가능한 승인값. |
| `IMAGE_CLEANUP_INTERVAL` / `IMAGE_CLEANUP_BATCH_SIZE` | cleanup | 양의 Spring `Duration` / positive integer; missing/invalid은 new file/cleanup을 fail-closed로 만든다. |

dataset/root UUID 기본값, auto-adopt/rebind, reset/repair, flyway-only 또는 password provisioning
환경변수를 발명하지 않는다.

#### M — 실제 HTTP AI provenance·timeout

| 변수 | source binding | 단위·제약 |
| --- | --- | --- |
| `AI_TIMEOUT` | `app.ai.timeout` | positive Spring `Duration`; 현재 HTTP factory의 최소 2초 connect/20초 read는 total deadline이 아니다. |
| `AI_ANALYSIS_MODEL` / `AI_MODERATION_MODEL` | AI model provenance | nonempty real model provenance. |
| `AI_ANALYSIS_PROMPT_VERSION` / `AI_MODERATION_PROMPT_VERSION` | prompt provenance | nonempty real prompt revision. |

### 4.3 F — Compose가 고정·유도하는 값

다음 F는 `secret.env`에서 바꾸지 않는다. Compose service별 allowlist가 전달하며 `env_file:`로
전체 env를 DB/API에 주입하지 않는다: `POSTGRES_USER=postgres`, `POSTGRES_DB=postgres`,
`POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256`, `SPRING_PROFILES_ACTIVE=prod`,
`DB_URL=jdbc:postgresql://127.0.0.1:${DB_HOST_PORT}/${DB_NAME}?currentSchema=public`,
`DB_SCHEMA=public`, `APP_UPLOAD_DIR=/var/lib/emotionmap/uploads`, `AI_PROVIDER=http`,
`AI_BASE_URL=http://127.0.0.1:8001`, `SERVER_ADDRESS=127.0.0.1`,
`SERVER_PORT=${API_PORT:-8080}`, `SERVER_SHUTDOWN=graceful`,
`SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=${API_SHUTDOWN_TIMEOUT}`,
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SPRING_FLYWAY_ENABLED=true`,
`SPRING_FLYWAY_VALIDATE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`,
`SPRING_FLYWAY_CLEAN_DISABLED=true`.

### 4.4 C — 조건부 입력

| 변수 | 언제 채우는가 | binding·제약 |
| --- | --- | --- |
| `AI_SERVICE_TOKEN` | AI host가 shared `X-AI-Token`을 요구/수용하도록 합의한 경우만 | nonblank면 모든 HTTP AI 요청에 header를 붙인다. DB/admin secret 재사용 금지; provider/Claude key는 AI host의 별도 secret이다. |
| `CORS_ALLOWED_ORIGINS` | TLS proxy와 다른 browser origin이 필요한 경우만 | 정확한 comma-separated origin allowlist; blank는 cross-origin 불허, wildcard는 기동 거절이다. |

로컬 프런트엔드의 두 origin을 허용하려면 Compose가 읽는 `.env`에 다음 값을 설정한다.
이 값은 기존 허용 목록 전체를 대체한다. `localhost:3000` 등도 필요하면 같은 목록에 명시한다.

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:8082
```

origin은 `scheme://host:port`이며 경로나 끝의 `/`를 붙이지 않는다. `localhost`와 `127.0.0.1`,
HTTP와 HTTPS, 서로 다른 포트는 각각 다른 origin이다. 공백이 있는 쉼표 구분 목록도 처리한다.
설정은 **API 부팅 시** 적용되며 실행 중 자동 갱신하지 않는다. Compose `.env`를 변경했다면
API 컨테이너를 **재생성**해야 한다. 기존 컨테이너의 단순 restart는 환경변수를 갱신하지 않는다.

Gradle로 직접 실행할 때 Spring Boot가 `.env` 파일을 자동으로 읽는 것은 아니다.
실행 프로세스에 환경변수를 전달한다.

```bash
CORS_ALLOWED_ORIGINS='http://localhost:8081,http://localhost:8082' ./gradlew bootRun
```

환경변수를 생략하면 `local`/`ci`는 기존 `http://localhost:3000` 기본값을 사용하고,
`prod`는 교차 출처 요청을 허용하지 않는다. 명시적인 빈 값은 모든 프로필에서 교차 출처 요청을
허용하지 않는다. CORS 허용은 JWT 인증을 생략하거나 cookie credentials를 허용한다는 뜻이 아니다.

### 4.5 O — 현재 구현의 선택 override

O default는 승인된 운영값이 아니다. blank `MATCH_*`는 record default를 쓰며 `MODERATION_*`는
생략하거나 exact default를 유지한다.

| 변수 | source binding | 현재 기본값 | 제약 |
| --- | --- | --- | --- |
| `API_PORT` | `SERVER_PORT` | `8080` | loopback TCP port |
| `MATCH_RADIUS_METERS` / `MATCH_FALLBACK_RADIUS_METERS` | matching radius | `1000` / `3000` | m, 각각 `>=1`, `>= primary` |
| `MATCH_MIN_PLACES` | primary radius minimum | `3` | integer `>=1` |
| `MATCH_NOTIFY_THRESHOLD` / `MATCH_VERIFY_THRESHOLD` | matching threshold | `0.85` / `0.65` | `0 <= VERIFY <= NOTIFY <= 1` |
| `MATCH_SIMILARITY_WEIGHT` / `MATCH_DISTANCE_WEIGHT` | matching weights | `0.85` / `0.15` | current code는 합/범위를 검증하지 않는다. |
| `MATCH_PLACE_VIBE_SMOOTHING` / `MATCH_MERGE_DISTANCE_METERS` | matching smoothing/merge | `2` / `20` | count `>=0` / m `>=0` |
| `MATCH_CATEGORY_CONFIDENCE_THRESHOLD` | category threshold | `0.6` | `[0,1]` |
| `MATCH_REVIEWS_FOR_VERIFY` | reviews | `5` | integer `>=1` |
| `MODERATION_RETRY_DELAY` / `MODERATION_RETRY_INITIAL_DELAY` | scheduled delay | `PT5M` / `PT1M` | Spring `Duration`; blank initial delay로 default를 지우지 않는다. |

## 5. API image 준비

Compose `api` service는 `image: ${API_IMAGE}`만 가지며 `build:`가 없다. 따라서 `docker compose up`
은 source에서 API image를 build하지 않는다. build는 repo root
`/srv/emotionmap/deploy/backend`에서, secret build arg 없이 수행한다. **최신 main 배포는
3.3절의 build 명령을 사용한다.** 아래는 승인된 immutable release source용 명령이다:

```sh
# '<approved-api-image-tag>'를 실제 승인 tag로 교체 (형식 예시: emotionmap-api:release-0123456789ab)
cd /srv/emotionmap/deploy/backend
docker build -t '<approved-api-image-tag>' .
```

직접 build하는 경우 `API_IMAGE`에는 위 `-t`와 **정확히 같은 tag**를 넣는다. `docker build -t`에는
`@sha256:...` digest reference를 쓰지 않는다. `latest`도 쓰지 않는다. 이미 승인된 registry image를
쓰는 경우에는 그 고유 tag 또는 digest reference를 `API_IMAGE`에 넣고 build를 생략할 수 있다.
registry 인증은 별도의 승인된 방법으로 수행하고, 아래 명령으로 API image만 미리 내려받는다.

```sh
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml pull api
```
`Dockerfile`은 multi-stage로 JAR을 repo source에서 build하고 runtime stage에는 생성 JAR만 넣는다.
`.dockerignore`는 `.env*`, `.git`, docs, tests, data/uploads/pgdata/backups 등을 build context에서
제외한다. 그래도 secret을 repo/build context 또는 build args에 넣지 않는다.

## 6. 최초의 **빈** dataset 초기화 절차

다음은 새로 승인된 빈 `db_data`/`image_data`에만 적용한다. `COMPOSE_PROJECT_NAME`,
subnet/IP/port, DB/schema, volume identity는 activation 이후 바꾸지 않는다. 각 command는 같은
`--env-file`과 두 `-f` file을 쓴다.

1. approved V9 image-storage lifecycle와 `storage-init-empty` CLI, Linux Engine/Compose, same-LXC
   AI loopback, reverse proxy/TLS policy, protected env와 빈 volumes를 preflight한다.
2. DB만 시작한다. 새 empty `db_data`에서만 `10-init-app-db.sh`가 app DB/user/public
   schema/vector를 만든다. `POSTGRES_PASSWORD`는 official image가 고정
   `POSTGRES_USER=postgres`/`POSTGRES_DB=postgres` bootstrap admin을 만들 때만 쓰고,
   `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`는 이 initializer가 만드는 별도 app database/login role의
   값이다. 정상적인 첫 boot에는 수동 `CREATE USER`/`CREATE DATABASE`가 필요 없다.
   AccountProvisioningCli가 만드는 email/password 앱 로그인 계정은 이 DB role과 또 다른 계정이다.
   healthcheck는 app credential의 TCP `psql` scalar query로 vector, public `USAGE`/`CREATE`,
   non-superuser/non-createdb/non-createrole/non-replication/non-bypassrls를 확인한다. 이는
   `pg_isready`만의 대체가 아니지만 Flyway 전에도 healthy가 될 수 있다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml up -d db

   (
     set -eu
     db_id=$(docker compose --env-file /etc/emotionmap/secret.env \
       -f /srv/emotionmap/deploy/backend/compose.yaml \
       -f /srv/emotionmap/deploy/compose.lxc.yaml ps -q db)
     test -n "$db_id"
     attempts=0
     until [ "$(docker inspect --format '{{.State.Health.Status}}' "$db_id")" = healthy ]; do
       attempts=$((attempts + 1))
       if [ "$attempts" -ge 90 ]; then
         printf '%s\n' 'DB health가 180초 안에 healthy가 되지 않아 중단합니다.' >&2
         exit 1
       fi
       sleep 2
     done
   )
   ```
   `healthy`만으로 business readiness를 선언하지 않는다. 다음 명령은 container의 DB 환경을
   직접 읽어 app role로 TCP 인증하고, scalar `t|t|t|t`만 성공으로 받는다. secret을 출력하지
   않는다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml exec -T db sh -c '
       PGPASSWORD="$DB_PASSWORD" exec psql -X -w --host=127.0.0.1 \
         --username="$DB_USERNAME" --dbname="$DB_NAME" -v ON_ERROR_STOP=1 -Atqc "
           SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = '\''vector'\''),
                  has_schema_privilege(current_user, '\''public'\'', '\''USAGE'\''),
                  has_schema_privilege(current_user, '\''public'\'', '\''CREATE'\''),
                  NOT (SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                       FROM pg_roles WHERE rolname = current_user);
         "
     ' | { IFS= read -r result && test "$result" = 't|t|t|t' && printf '%s\n' "$result"; }
   ```
3. 외부 traffic을 닫은 채 API를 시작한다. 정상 web boot이므로 Flyway/JPA validate뿐 아니라
   web/AI wiring/scheduler도 생긴다. V9 migration과 schema validate 완료를 별도 확인한다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml up -d api
   ```
4. API를 멈춘 뒤 DB app credential의 read-only session으로 unbound dataset ID 한 행을 읽는다.
   출력 UUID는 activation 승인이 아니며 사람이 approved empty dataset/DB/schema/volume인지 확인한다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml stop api

   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml exec -T db \
     sh -c 'PGPASSWORD="$DB_PASSWORD" PGOPTIONS="-c default_transaction_read_only=on" exec psql -X -w -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 -Atqc "SELECT dataset_id FROM public.image_storage_binding WHERE singleton = TRUE;"'
   ```
5. 다음 `<approved-dataset-id>`는 operator가 승인한 값으로 교체하는 비실행 placeholder다. API
   writer를 멈춘 상태에서 empty-root activation CLI를 한 번만 실행한다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml run --rm --no-deps api \
     storage-init-empty \
     --root /var/lib/emotionmap/uploads \
     --expected-dataset-id '<approved-dataset-id>' \
     --expected-database '<approved-db-name>' \
     --expected-schema public
   ```
   CLI는 migrated `public`, unbound `image_storage_binding`, image reference가 없는 dataset, 빈
   `image_data` root만 받는다. nonempty/foreign/BOUND root, wrong locator, partial marker/lock은
   거절 사유이며 reset/delete/retry 지시가 아니다. exit `0`은 explicit initialization complete,
   `2`는 invalid request/argument, `1`은 그 밖의 boot/DB/filesystem failure다. 0 이외이면 volume을
   수정하거나 재실행하지 말고 보존·중지·조사·승인 절차로 넘긴다.
6. 승인 계정만 한 개씩 provisioning한다. 이 CLI는 one `USER` role account/credential만 만들며
   admin/onboarding/history/password reset/public operator API를 제공하지 않는다.
   아래 기본 명령은 실제 TTY에서 실행한다. CLI는 비밀번호 안내 문구 없이 숨김 입력을 기다리므로
   승인된 비밀번호를 입력하고 Enter를 누른다. 비대화형 자동화에 한해 별도로 승인된 raw supplier와
   `run -T`, CLI의 `--password-stdin`을 사용한다. TTY 숨김 입력과 raw stdin 방식을 혼동하지 않는다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml run --rm --no-deps \
     --entrypoint java api \
     -Dloader.main=team4.emotionmap.account.AccountProvisioningCli \
     -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
     --email '<approved-email>' --status '<ACTIVE-or-PENDING>'
   ```
   password stdin은 security-approved raw UTF-8 no-newline supplier만 사용한다. password를 argv,
   environment, plaintext file, `echo`에 넣지 않는다. CLI는 input을 trim하지 않으므로 trailing
   newline도 password byte이고, malformed UTF-8/empty/72 bytes 초과는 거절한다. duplicate email은
   새 password로 덮어쓰지 않으며, credential rotation은 별도 승인 절차가 필요하다.
7. API를 다시 시작하고 health와 business readiness를 분리한다.
   ```sh
   docker compose --env-file /etc/emotionmap/secret.env \
     -f /srv/emotionmap/deploy/backend/compose.yaml \
     -f /srv/emotionmap/deploy/compose.lxc.yaml up -d api
   ```
   health probe는 `http://127.0.0.1:${SERVER_PORT}/actuator/health`다. `/readiness` endpoint를
   발명하거나 anonymous probe로 쓰지 않는다. health `UP`은 service policy, storage binding,
   login/onboarding, L03 image I/O, real AI 또는 MVP readiness 증명이 아니다. approved login,
   authenticated `/v1/config`, L03 upload/attach/read, DB/image persistence와 real AI path를 별도
   확인하고 TLS proxy가 loopback API만 proxy하는 것을 확인한 뒤 traffic을 연다.

Compose service 생성에는 모든 M interpolation이 필요하지만, account/storage CLI가 JWT/AI/Flyway/
service policy를 모두 쓴다는 뜻은 아니다. account CLI는 raw DB URL/user/password, `DB_SCHEMA`,
BCrypt strength만, activation CLI는 raw DB URL/user/password, `DB_SCHEMA`, upload root만 isolated
environment에 쓴다.

### 6.1 PostgreSQL 최초 계정 생성과 초기화 실패 복구

#### 정상 최초 boot와 오류의 의미

위 6절 2단계가 **처음부터 비어 있던 PGDATA**에서 성공하면, official PostgreSQL image는
`POSTGRES_PASSWORD`로 bootstrap admin database role `postgres`의 비밀번호를 설정한다. 이 Compose
계약에서 `POSTGRES_USER`와 `POSTGRES_DB`는 모두 `postgres`로 고정되어 있으므로, 이 값들은
`DB_NAME`이나 `DB_USERNAME`을 만들지 않는다. 이어서 `10-init-app-db.sh`가 `DB_USERNAME` login
role과 `DB_NAME` database를 만들고 `DB_PASSWORD`를 그 app role에만 적용한다. 따라서 정상 최초
boot에는 operator가 수동으로 role/database를 만들거나 env를 다시 적용할 일이 없다.

이것은 사람이 API에 로그인할 때 쓰는 계정과도 다르다. `AccountProvisioningCli`는 DB에 접속하려고
`DB_PASSWORD`를 사용하지만, 그 값은 product account의 email/password credential이 아니며 CLI가
PostgreSQL role을 만들지 않는다. `POSTGRES_PASSWORD`나 `DB_PASSWORD`를 바꿔 restart하는 것은 이미
초기화된 cluster의 비밀번호 rotation이나 missing role/database 복구가 아니다.

보고된 첫 `CREATE ROLE` SQL의 `:` syntax error, 그 뒤 server가 반환한 `FATAL: role ... does not
exist`, 이후 restart의 `Skipping initialization`은 서로 다른 network 장애가 아니라 하나의 인과
사슬이다. `FATAL`은 PostgreSQL server까지 연결되었다는 뜻이다. official image는 PGDATA가 비어 있을
때만 init script를 실행한다. 첫 script 실패가 PGDATA를 이미 초기화된 것으로 남겨 재시작은 script를
건너뛰고, 따라서 누락된 app role/database가 자동으로 생기지 않는다.

수정본은 SQL `CREATE ROLE`과 `CREATE DATABASE` 모두를 quoted heredoc stdin으로 보내도록 바뀌어야
한다. 일반 SQL을 `psql --command`로 보낼 때의 `:name` variable-interpolation 기대는 안전한 계약이
아니며, PostgreSQL 17 [psql `--command` 문서](https://www.postgresql.org/docs/17/app-psql.html#APP-PSQL-OPTION-COMMAND)의
command-string 경계를 따른다. 반대로 `\password :db_username`은 psql meta-command로서 검증된
변수 확장을 사용하므로 변경하지 않는다.

#### 이 사건에만 허용되는 보존 복구

다음 절차는 일반적인 “DB 재생성” 방법이 아니다. **처음 storage가 비어 있었다는 이력**이 있고,
보고된 실패가 위의 첫 `CREATE ROLE` colon syntax error임을 확인한 경우에만 적용한다. traffic을
닫고 API와 모든 app/CLI writer를 멈춘 뒤, DB와 images를 같은 시점으로 복구 가능한 승인된
PostgreSQL-consistent backup/snapshot으로 함께 보존한다. 실행 중인 PGDATA의 단순 파일 복사는
backup 증명이 아니다. original `COMPOSE_PROJECT_NAME`, source/mount paths, database/role names,
두 DB secret, DB/images가 모두 실패 당시와 같아야 한다. env를 고치거나 container를 재생성해서
조건을 맞추지 않는다.

모든 Compose operation은 같은 protected env와 base/override를 사용한다. 아래 helper는 현재 shell에만
정의되며 secret을 읽거나 출력하지 않는다.

```sh
dc() {
  docker compose --env-file /etc/emotionmap/secret.env \
    -f /srv/emotionmap/deploy/backend/compose.yaml \
    -f /srv/emotionmap/deploy/compose.lxc.yaml "$@"
}

dc stop api
```

수정본이 실제 LXC host의 아래 source path에 **승인된 방법으로 전달되어 있어야** 한다. local worktree의
수정이 아직 commit/push되지 않았다면 `git pull`이나 “latest main”이 이를 전달한다고 말할 수 없다.
승인된 transfer record의 secret 없는 checksum과 대조한다. 이 host-sourced initializer 변경에는 API
image rebuild가 필요 없다.

```sh
sha256sum /srv/emotionmap/deploy/backend/deploy/postgres/10-init-app-db.sh
```

단일-file bind mount는 host file의 atomic replacement 뒤 이전 inode를 계속 볼 수 있다. 그래서 아래
mutation은 container mount 경로를 실행하지 않고, 승인되어 현재 host에 있는 source를 stdin으로
`sh -s`에 한 번 전달한다. host source의 checksum 또는 delivery/approval 이력이 불확실하면 mount를
restart·recreate·remount하거나 추측하지 말고 중단한다.

다음 read-only preflight는 새 authentication trust, `pg_hba` 변경 또는 privilege elevation을 만들지
않는다. `--user postgres`는 container OS user이고 `--username=postgres`는 bootstrap DB admin이다.
`-w` socket 인증이 실패하면 password를 argv에 넣거나 trust를 켜지 말고 즉시 멈춘다.

```sh
dc exec -T --user postgres db sh -c '
  PGOPTIONS="-c default_transaction_read_only=on" exec psql -X -w \
    --host=/var/run/postgresql --username=postgres --dbname=postgres \
    --set=ON_ERROR_STOP=1 \
    --set=db_username="$DB_USERNAME" --set=db_name="$DB_NAME"
' <<'SQL'
BEGIN READ ONLY;
SELECT current_user = 'postgres' AS postgres_admin,
       inet_client_addr() IS NULL AS local_socket;
SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_username') AS app_role_absent,
       NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name') AS app_db_absent;
SELECT datname, datistemplate, pg_get_userbyid(datdba) AS owner
FROM pg_database ORDER BY datname;
SELECT rolname, oid, rolsuper, rolcreatedb, rolcreaterole,
       rolreplication, rolbypassrls, rolcanlogin
FROM pg_roles ORDER BY rolname;
COMMIT;
SQL
```

모든 query가 성공하고 첫 두 결과의 boolean이 모두 `t`여야 한다. database catalog는
`postgres`/`template0`/`template1`만(각 owner `postgres`, 정상 template flag), role catalog는
`postgres`와 해당 pristine PG17 built-in `pg_*` role만 허용한다. 이름 prefix만 보고 role을 허용하지
말고 PG17 baseline과 이력을 대조한다. 이 catalog 검사는 충분조건이 아니다. default database의
사용자 object/data, image data, 다른 writer, 바뀐 built-in role, 불명확한 생성·restore 이력 또는
unknown role/database가 하나라도 있으면 멈춘다. app role 또는 database가 하나라도 존재해도
멈춘다.

preflight와 실행 사이에는 운영 변경을 동결한다. operator가 위 출력과 backup을 검토하여 **명시적으로
승인한 뒤에만**, `&&`, retry loop, 자동 script 없이 다음 mutation을 정확히 한 번 별도 실행한다.
`-T`는 `\password`의 stdin pipe에 controlling TTY가 끼어들지 않게 한다.

```sh
dc exec -T --user postgres db sh -s < /srv/emotionmap/deploy/backend/deploy/postgres/10-init-app-db.sh
```

명령이 실패·중단되거나 후속 app TCP 인증이 실패하면 API를 멈춘 채 data와 증거를 보존하고 조사로
escalate한다. script는 transaction 하나가 아니므로 partial role/database가 남을 수 있다. 두 번째
실행, `DROP`, volume delete/reset, env edit, `ALTER ROLE`, password overwrite, Flyway 변경은 모두
금지다.

exit 0 뒤에도 다음 app TCP query가 정확히 `t|t|t|t`를 출력해야 한다. 이는 `postgres` socket
`SELECT 1`, `pg_isready`, Compose config만으로 대체할 수 없다.

```sh
dc exec -T db sh -c '
  PGPASSWORD="$DB_PASSWORD" exec psql -X -w --host=127.0.0.1 \
    --username="$DB_USERNAME" --dbname="$DB_NAME" -v ON_ERROR_STOP=1 -Atqc "
      SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = '\''vector'\''),
             has_schema_privilege(current_user, '\''public'\'', '\''USAGE'\''),
             has_schema_privilege(current_user, '\''public'\'', '\''CREATE'\''),
             NOT (SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                  FROM pg_roles WHERE rolname = current_user);
    "
' | { IFS= read -r result && test "$result" = 't|t|t|t' && printf '%s\n' "$result"; }
```

그 뒤 DB container health가 `healthy`인지 `dc ps`와 `docker inspect`로 기다린 뒤 API를 시작한다.
DB health가 먼저 healthy가 되기 전 API `--wait`를 실행하지 않는다.

```sh
(
  set -eu
  db_id=$(dc ps -q db)
  test -n "$db_id"
  attempts=0
  until [ "$(docker inspect --format '{{.State.Health.Status}}' "$db_id")" = healthy ]; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 90 ]; then
      printf '%s\n' 'DB health가 180초 안에 healthy가 되지 않아 중단합니다.' >&2
      exit 1
    fi
    sleep 2
  done
)
dc up -d --wait --wait-timeout 180 api
```

이는 DB bootstrap 성공만 보일 뿐이다. 6절의 Flyway/JPA, image-storage activation,
AccountProvisioningCli, API health 및 authenticated/business/AI/TLS readiness gate를 끝까지 수행한
후에만 traffic을 연다. env literal과 account login credential의 입력 규칙은
[ENV_SETUP_GUIDE.md](ENV_SETUP_GUIDE.md)를 참고하되, 이 bounded recovery의 정본은 이 6.1절이다.

## 7. AI, update, backup 및 recovery gates

`AI_PROVIDER=http`일 때 API는 same-LXC AI host의 `127.0.0.1:8001`에서 `/ai/analyze`,
`/ai/moderate`, `/ai/verify`, `/ai/tiebreak`, `/ai/match-reason` HTTP POST를 쓴다. nonblank
`AI_SERVICE_TOKEN`은 모든 요청의 `X-AI-Token`이고 provider/Claude key는 backend secret이 아니다.
`AI_*_MODEL`/`AI_*_PROMPT_VERSION`은 backend provenance input일 뿐 remote AI가 실제 사용했다는
증명이 아니다.

traffic 전에 AI 연결/token rotation/five endpoint JSON과 네 axis(`CROWD_LEVEL`, `SPATIAL_FEEL`,
`COMPANY_FIT`, `STAY_STYLE`)의 유한 binary64 `[-1,1]` 값 및 category JSON mapping을 확인한다.
flat 분석 wire는 `axisDefinitionVersion=2`, `taxonomyVersion=1`을 기대한다. `0`은 알려진 값이고
`-0`은 `+0`으로 정규화하며 별도 양자화하지 않는다. 형식·버전·provenance 불일치는 실패로 처리한다.
synthetic 응답 검증은 실제 제공자의 fractional 출력 품질이나 연동 성공을 증명하지 않는다.
upstream error/timeout, moderation state, log data exposure, model/prompt provenance도 실제로
검증한다. `AI_TIMEOUT`은 total
deadline이 아니며 current HTTP factory의 최소 2초 connect/20초 read, provider latency/failure를
관찰해야 한다. image bytes B, multipart H, decode/re-encode/base64, Java heap, `/tmp`, image capacity와
cleanup을 함께 측정한다. L03 activation 뒤 DB name/OID/server IPv4/port/schema OID와 marker
dataset/root, restart 뒤 I/O 보존도 runtime 검증 대상이다.

### 7.1 API-only release update

이 절은 승인된 release용 경로다. **릴리스 태그 없이 최신 main으로 갱신하려면 3.3절을 사용한다.**
approved source release와 `API_IMAGE`의 migration/storage compatibility를 먼저 검토한다.
다음 `<approved-*>`는 실행 전 operator가 교체하는 placeholder이며, source tree를 destructive
reset/pull하지 않는다.

```sh
# 새 release에도 3.2절의 동일한 볼륨·네트워크 계약이 유지되는지 먼저 검토
cd /srv/emotionmap/deploy/backend
git fetch --tags origin
git checkout --detach '<approved-immutable-release>'
git rev-parse HEAD
docker build -t '<approved-api-image-tag>' .
# secret.env의 API_IMAGE를 같은 approved image reference로 보안 편집
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml stop api
docker compose --env-file /etc/emotionmap/secret.env \
  -f /srv/emotionmap/deploy/backend/compose.yaml \
  -f /srv/emotionmap/deploy/compose.lxc.yaml up -d --no-deps api
```

registry image를 쓰는 경우 위 build 대신 새 `API_IMAGE`를 편집하고 5절의 `pull api`를 수행한다.
traffic 차단과 기존 요청 drain을 완료한 뒤 `stop api`를 실행한다. 새 버전 기동 후 6절의 health와
업무 준비 검증을 다시 통과해야 traffic을 재개한다. API가 한 개이므로 교체 중 API 중단은 발생한다.

이는 `api`만 stop/start하며 DB container를 process로서 건드리지 않는다. 그러나 normal API boot의
Flyway는 DB를 forward-migrate할 수 있다. old/new writer가 같은 DB/image dataset에 동시에 쓰지 않게
drain한다. `COMPOSE_PROJECT_NAME`, `db_data`, `image_data`, LXC data paths, subnet/IP/DB host port,
DB/schema/OID를 보존한다. `docker compose down -v`, volume delete, root reset, rebind, Flyway
clean/baseline, marker removal, auto adopt/repair/rebind는 정상 update/rollback 방법이 아니다.

[TAILSCALE_SERVE_GUIDE.md](TAILSCALE_SERVE_GUIDE.md)는 approved TLS/reverse-proxy를 준비하기 전
제한된 임시 public-path 검증에만 참고할 수 있다. production approval, authentication/TLS/firewall
정책, business readiness를 대체하거나 우회하지 않는다.

### 7.2 secret rotation, backup과 restore

secret/key rotation은 automatic zero-downtime 절차가 아니다. `JWT_SECRET` 교체는 existing JWT를,
`SIGNING_SECRET` 교체는 signed analysis token/cursor를 무효화할 수 있다. `DB_PASSWORD`는 DB role,
API와 두 CLI의 coordinated change가 필요하고, existing cluster의 `POSTGRES_PASSWORD` env edit은
rotation이 아니다. `AI_SERVICE_TOKEN`은 AI host와 API 동시 합의가 필요하다.

`db_data`와 `image_data`는 하나의 dataset이다. API writer를 drain/stop하고 둘을 같은 시점의
일관된 쌍으로 backup/restore한다. DB만 또는 image data만 restore하면 binding/marker/reference가
어긋날 수 있다. logical restore가 database/schema OID 또는 DB server locator를 바꾸면 L03는
fail-closed 거절할 수 있다. 새 DB/IP/OID에 auto rebind/adopt하는 일반 절차는 없다. physically
identical clone도 original과 동시에 writer를 활성화하지 않으며 별도 recovery-validation boundary다.

## 8. 구현 근거와 검증 경계

이 문서는 `Dockerfile` (multi-stage Java 21 build/runtime, UID 10001, `/app/app.jar`),
`.dockerignore` (secrets/data/test/docs excluded build context), `compose.yaml` (host-network API,
loopback DB publish, fixed IPv4, image-only API, volume/service/health contract), `.env.example`
(M/F/C/O inventory), `deploy/postgres/10-init-app-db.sh` (empty-PGDATA one-shot app/admin separation),
그리고 기존 AccountProvisioning/ImageStorageActivation/V9/service/storage/AI static evidence를
대조해 작성했다.

기존 `DEPLOY-CONFIG-C1`/`C2`는 당시 required M 45개의 누락·빈 값 거절과 base manifest 구조를
검증한 과거 정적 증거다. 이를 현재 required M 46개의 검증으로 소급하지 않는다.
현재 통합본에는 지도 전용 `CURSOR_TTL` 입력과 `app.pagination.cursor-ttl`의 fail-closed 경계를
반영했다. 최신 Compose와 LXC override를 합성 M 입력 46개로 다시 해석했고, `CURSOR_TTL`의
누락·빈 값이 각각 거절됨을 확인했다. 이는 과거 검증과 별개의 현재 통합본 검사이며,
Java binding이나 실제 DB·API 기동 검증은 아니다. 세 배포 가이드의 셸 예시 44개도 `sh -n`을 통과했다.

앞선 LXC 경로 문서 갱신에서는 문서의 LXC override를 임시 파일로 추출하고 실제 `compose.yaml`과 함께
Compose v5.1.2 parser에 입력했다. 실제 비밀 대신 합성 M 입력 45개를 사용했으며 두 named volume의
`driver_opts`/data 경로, API host network, DB loopback publish, bootstrap script 경로의 보존을
확인했다. 문서 셸 예시의 `sh -n` 문법 검사도 수행했다. 임시 검증 파일은 제거했다.

최신 main 경로 추가 시에는 전체 셸 예시 17개의 `sh -n` 검사를 통과했다. 3.3절에서 추출한
Git 절차를 임시 local origin/clone에 실행하여 연속된 두 main 갱신을 detached HEAD에서도
선택하는지, tracked/untracked 로컬 변경이 있으면 보존한 채 중단하는지 확인했다.
임시 저장소는 제거했으며, 이 검증에서는 Docker build와 실제 배포 명령을 실행하지 않았다.

`PG-BOOTSTRAP-C2`에서 main은 corrected initializer source snapshot `8E58`을 격리된 PostgreSQL
17.11 + pgvector 환경으로 실행해 app role password의 TCP 인증과 vector/public
privilege·nonprivileged-role health 조건을 확인했다. 이는 main이 수행한 isolated database 증거이며
tester 완료나 LXC 운영 검증을 뜻하지 않는다. 특히 실제 Linux LXC Docker의 host-file bind mount/inode,
backup/snapshot, API/Flyway/JPA, image activation, AccountProvisioning, same-LXC AI, TLS/public
traffic, business readiness 및 production deployment는 모두 **NOT_RUN**이다.

공식 참고 문서:

- [Proxmox LXC Mount Points와 backup/replication](https://pve.proxmox.com/pve-docs/chapter-pct.html#pct_mount_points)
- [Docker Compose의 bind-backed named volume](https://docs.docker.com/reference/compose-file/volumes/)
- [여러 Compose 파일의 병합 순서](https://docs.docker.com/compose/how-tos/multiple-compose-files/merge/)
