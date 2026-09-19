# Tailscale Funnel로 로컬 백엔드 임시 공개하기

Tailscale이 이미 설치된 환경에서, 실행 중인 감정지도 백엔드를 **Tailscale이 없는 사람도 접속할 수 있는 HTTPS 주소**로 잠시 공개하는 절차입니다. 파일명은 `TAILSCALE_SERVE_GUIDE.md`이지만, 인터넷 공개에는 **Serve가 아니라 Funnel**을 사용합니다.

> 아래 명령은 직접 실행하는 안내입니다. 문서 작성 중 서버를 인터넷에 공개하거나 Tailscale 계정·정책을 변경하지 않았습니다.

## 1. 먼저 확인할 것

- 백엔드의 기본 주소는 `http://127.0.0.1:8080`입니다.
- **Funnel 명령은 이 주소에 접속할 수 있는 같은 장치에서 실행합니다.** 다른 장치의 `127.0.0.1`은 내 백엔드가 아닙니다.
- 운영 DB·실제 개인 데이터 대신 별도 데모 DB와 합성 이미지를 사용합니다.
- Git에 있는 개발용 `JWT_SECRET`, `SIGNING_SECRET`을 그대로 공개 환경에 사용하지 않습니다. 서로 다른 무작위 값으로 교체한 서버를 사용합니다.
- URL은 비밀번호가 아닙니다. Funnel을 켜면 인터넷 누구나 요청할 수 있으며, 앱의 JWT 인증은 그대로 필요합니다.
- DB 5432, Proxmox 8006, Docker 관리 API, 업로드 폴더 자체는 공개하지 않습니다.

### 현재 프로젝트에서 함께 노출되는 경로

이 문서의 명령은 **8080 웹 서버 전체를 프록시**합니다. `/v1`만 선택해서 공개하는 것이 아닙니다. 현재 보안 설정에서 다음 경로는 인증 없이 접근 가능합니다.

| 경로 | 역할 |
| --- | --- |
| `POST /v1/auth/login` | 로그인 시도 |
| `/actuator/health` | 서버 상태 확인 |
| `/swagger-ui.html`, `/swagger-ui/**` | Swagger UI |
| `/v3/api-docs/**` | OpenAPI 문서 |

Swagger 공개가 필요 없다면 서버 시작 시 `SPRINGDOC_SWAGGER_UI_ENABLED=false`, `SPRINGDOC_API_DOCS_ENABLED=false`를 지정하세요. 이미지 조회는 기존 권한 검사 API를 사용합니다. `tailscale funnel /폴더경로`로 프로젝트나 이미지 저장소를 공유하지 마세요.

### macOS 사용자 주의

공식 macOS variant 비교표는 Funnel을 open-source `tailscaled` 방식에서만 지원한다고 안내하지만, Funnel CLI 문서에는 GUI 앱의 포트 공유 설명도 있어 문서 간 차이가 있습니다. **GUI 앱에서 `funnel --help`가 나온다는 것만으로 인터넷 공개를 지원한다고 판단하지 마세요.** 설치 방식·버전을 확인하고, 지원 오류가 나면 [macOS 지원 안내](https://tailscale.com/docs/concepts/macos-variants)를 따르거나 아래 7장의 Ubuntu 경유 방법을 사용하세요. 기존 앱과 별도 daemon을 무작정 중복 실행하지 않습니다.

## 2. 로컬 서버와 기존 공유 확인

백엔드를 실행한 장치의 **새 터미널**에서 확인합니다. 이미 서버가 실행 중이면 두 번째 서버를 띄우지 않습니다.

```bash
curl --fail-with-body http://127.0.0.1:8080/actuator/health
```

HTTP 200과 `status: UP`이면 기본 상태 확인은 성공입니다. 연결 거부나 503이면 먼저 백엔드·DB 문제를 해결하세요. health 성공만으로 로그인·서비스 설정·이미지 쓰기 권한까지 정상이라고 판단하지 않습니다.

이어서 설치된 CLI와 기존 공유 상태를 확인합니다.

```bash
tailscale version
tailscale status
tailscale serve status
tailscale funnel status
```

- `status`가 미연결이면 기존 Tailscale 앱에서 연결합니다.
- 이미 443에서 다른 공유가 실행 중이면 덮어쓰지 않습니다.
- Serve와 Funnel은 같은 포트를 동시에 비공개·공개 상태로 유지할 수 없습니다. 마지막으로 적용한 방식에 따라 접근 범위가 달라집니다.
- Mac에서 앱은 있지만 `tailscale: command not found`가 나오면 현재 터미널에 다음 별칭을 설정할 수 있습니다. 앱이 이 경로에 설치되어 있을 때만 사용합니다.

```bash
alias tailscale='/Applications/Tailscale.app/Contents/MacOS/Tailscale'
```

새 터미널에서는 별칭을 다시 설정하거나 기존 CLI integration을 사용합니다. 아래는 1.52 이후 CLI 형식이며 최신 안정 버전을 권장합니다.

## 3. 인터넷 공개 시작

공개 범위와 비밀값을 확인했다면 다음을 실행합니다.

```bash
tailscale funnel --https=443 http://127.0.0.1:8080
```

- `8080`: 내 백엔드의 로컬 포트
- `443`: 외부 HTTPS 포트
- Linux에서 권한 오류가 나면 같은 명령에 `sudo`를 붙입니다. Mac 앱 CLI에는 무조건 붙이지 않습니다.

처음에는 MagicDNS·HTTPS·Funnel 권한 활성화를 위한 승인 주소가 출력될 수 있습니다. 내용을 확인하고 브라우저에서 승인한 뒤 필요하면 명령을 다시 실행하세요. 권한이 없으면 tailnet 관리자에게 요청합니다. **Funnel 사용 권한은 공개 방문자를 제한하는 인증 정책이 아닙니다.** 기존 정책 파일을 예시로 통째로 덮어쓰지 마세요.

HTTPS 인증서 발급 시 장치 이름과 tailnet 도메인이 공개 인증서 기록에 남을 수 있습니다. 장치 이름에 개인·고객 정보를 넣지 않습니다. `tailscale cert`를 따로 반복 실행할 필요는 없습니다.

정상 실행 시 다음 형태의 메시지가 나옵니다. 주소는 예시이므로 **내 터미널에 표시된 실제 주소**를 복사하세요.

```text
Available on the internet:
https://demo-backend.example-tailnet.ts.net

|-- / proxy http://127.0.0.1:8080

Press Ctrl+C to exit.
```

이 터미널을 켜 둔 상태로 사용합니다. 임시 데모에는 이렇게 `--bg` 없이 실행하는 방식을 권장합니다. 공유기 포트 포워딩이나 백엔드의 HTTPS 인증서 설치는 필요하지 않습니다.

## 4. 외부 접속 확인·프런트엔드 연결

### 외부에서 확인

**Tailscale을 끈 휴대전화의 모바일 데이터 연결** 등 tailnet 밖에서 다음 주소를 열어 보세요.

```text
https://실제-Funnel-주소/actuator/health
```

명령줄로 확인할 때는 아래 주소를 실제 주소로 바꿉니다.

```bash
PUBLIC_URL='https://demo-backend.example-tailnet.ts.net'
curl --fail-with-body "$PUBLIC_URL/actuator/health"
curl -i "$PUBLIC_URL/v1/config"
```

두 번째 요청은 토큰이 없으므로 현재 정책상 **401이 예상**됩니다. 터널 오류가 아니라 인증이 필요한 것입니다. 로그인 후 유효한 Bearer 토큰으로 `/v1/config`가 정상 응답하는지도 확인하세요.

| 용도 | 주소 |
| --- | --- |
| API 기본 주소 | `https://실제-Funnel-주소` |
| 로그인 | `POST https://실제-Funnel-주소/v1/auth/login` |
| 설정 조회 | `GET https://실제-Funnel-주소/v1/config` — 인증 필요 |
| Swagger | `https://실제-Funnel-주소/swagger-ui.html` — 비활성화했다면 사용 불가 |
| 이미지 조회 | `GET https://실제-Funnel-주소/v1/memories/{id}/image` — 접근 권한 필요 |

경로에 `/api`를 덧붙이거나 기존 `/v1`을 제거하지 않습니다. 루트 `/`에 홈페이지가 뜨지 않는다고 실패로 판단하지 마세요.

로그인 계정은 DB 접속 계정과 별개입니다. 공개 회원가입·자동 계정 seed를 가정하지 말고 [프로젝트 계정 공급 절차](../ARCHITECTURE.md#71-최초-계정-공급)를 따릅니다. 로그인에는 ACTIVE 상태가 필요하며, 업무 API에 필요한 온보딩도 진행합니다.

팀원에게는 **HTTPS 기본 주소·API 경로·사용 가능 시간**을 전달합니다. 테스트 계정 비밀번호는 별도 안전한 경로로 전달하고, JWT·서명 비밀·DB 비밀번호는 공유 문서에 넣지 않습니다.

### 브라우저에서만 실패한다면 CORS 확인

현재 프로젝트는 `CORS_ALLOWED_ORIGINS`로 허용할 **프런트엔드의 origin**을 지정할 수 있습니다. 프런트가 `http://localhost:3000`에서 실행된다면, 백엔드를 시작하는 터미널에서 다음 값을 설정한 뒤 서버를 재시작합니다.

```bash
export CORS_ALLOWED_ORIGINS='http://localhost:3000'
```

배포된 프런트라면 `https://frontend.example.com`처럼 실제 주소를 지정합니다. 여러 출처는 쉼표로 구분합니다. origin은 `프로토콜 + 호스트 + 포트`이며 `/화면경로`나 끝의 `/`를 넣지 않습니다. 백엔드 Funnel 주소를 무조건 넣는 것이 아닙니다.

현재 구현은 Bearer `Authorization` 헤더를 허용하며 credentials는 허용하지 않습니다. 쿠키 인증을 가정해 `credentials: include`를 켜거나 `*`로 전부 허용하지 마세요. CORS는 브라우저 정책이지 인터넷 방문자를 제한하는 인증 수단이 아닙니다.

## 5. 공개 종료 — 데모가 끝나면 반드시 실행

Funnel을 실행한 터미널에서 **`Ctrl+C`**를 누릅니다. 백엔드 터미널이 아니라 Funnel 터미널입니다. 이어서 확인합니다.

```bash
tailscale funnel status
tailscale serve status
```

이번에 만든 443 공개 설정이 제거되었는지 확인합니다. 필요하면 해당 리스너를 명시적으로 끕니다.

```bash
tailscale funnel --https=443 off
```

마지막으로 tailnet 밖 장치에서 **새 health 요청이 더 이상 성공하지 않는지** 확인합니다.

백엔드만 꺼서 502가 되게 하는 것은 공개 설정 삭제가 아닙니다. 나중에 같은 포트에 서버를 띄우면 재노출될 수 있으므로 Funnel 설정 자체를 끄세요.

### 선택 사항: 터미널을 닫아도 유지해야 하는 경우

foreground 공유를 종료한 뒤, 필요한 경우에만 background 방식으로 전환합니다.

```bash
tailscale funnel --bg --https=443 http://127.0.0.1:8080
```

**자동 종료 시간이 없으며 재부팅·Tailscale 재연결 후에도 다시 공개될 수 있습니다.** 종료 시 같은 플래그로 해제합니다.

```bash
tailscale funnel --bg --https=443 off
tailscale funnel status
```

`tailscale funnel reset`과 `tailscale serve reset`은 전체 관련 설정을 지우므로 다른 공유가 있는 장치에서 무작정 실행하지 않습니다.

## 6. 서버 재시작이 필요할 때의 데모 설정

이미 안전하게 설정된 서버가 실행 중이면 이 장은 건너뜁니다. 개발용 비밀을 사용 중이거나 설정이 누락되었다면 **공개하기 전에** 기존 서버를 정상 종료하고 설정을 보완합니다.

백엔드 저장소 루트에서 아래처럼 실행할 수 있습니다. Java 21·PostgreSQL 17·초기 마이그레이션용 pgvector와 전용 데모 DB를 준비하고, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`는 해당 데모 DB의 값을 안전하게 공급해야 합니다. 비밀번호를 명령문·Git·공유 로그에 남기지 마세요.

아래 숫자는 임시 데모 예시이며 승인된 운영 정책값은 아닙니다. `local`은 데모 정책과 mock AI를 사용합니다. 실제 운영·실제 AI 검증으로 간주하지 않습니다.

```bash
export SPRING_PROFILES_ACTIVE=local
export SERVER_ADDRESS=127.0.0.1
export JWT_SECRET="$(openssl rand -hex 32)"
export SIGNING_SECRET="$(openssl rand -hex 32)"
export PASSWORD_BCRYPT_STRENGTH=12
export LOGIN_FAILED_ATTEMPT_WINDOW_SECONDS=600
export APP_STORAGE_MULTIPART_REQUEST_OVERHEAD_BYTES=65536
export MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=never
export SPRING_JPA_SHOW_SQL=false
export LOGGING_LEVEL_ORG_HIBERNATE_SQL=INFO
export LOGGING_LEVEL_TEAM4_EMOTIONMAP=INFO
export SPRINGDOC_SWAGGER_UI_ENABLED=false
export SPRINGDOC_API_DOCS_ENABLED=false
./gradlew bootRun
```

- 비밀값 생성에는 `openssl`이 필요합니다. 생성값을 출력·공유하지 않습니다. 다시 생성하면 이전 JWT·서명값을 사용할 수 없으므로 재로그인합니다.
- 로그인 실패 집계 창 누락 시 로그인에서 `CONFIGURATION_UNAVAILABLE`이 발생할 수 있습니다.
- 이미지 multipart 여유 크기 누락 시 신규 이미지 업로드가 503으로 닫힐 수 있습니다.
- `ci`는 DB 없는 테스트용이며 실제 서버 실행용이 아닙니다. 임시 공유를 위해 `prod`의 보안·AI 설정 검증을 우회하지 않습니다.
- DB에 기존 데이터가 있을 때 마이그레이션을 수정하거나 DB를 초기화해 기동 오류를 넘기지 않습니다.

Docker에서 실행 중이라면 환경변수는 Compose의 `environment`/`env_file`에 전달하고 백엔드만 재생성합니다. Tailscale이 같은 Docker 호스트에서 실행되는 경우 포트는 `127.0.0.1:8080:8080`으로 연결할 수 있습니다. **이 경우 위 `SERVER_ADDRESS=127.0.0.1`은 컨테이너 안에 적용하지 않습니다.** Spring이 컨테이너 인터페이스에서 수신해야 합니다. Tailscale을 다른 컨테이너에 넣으면 그 컨테이너의 `127.0.0.1`은 백엔드가 아닙니다.

## 7. Mac에서 Funnel 지원 오류가 나는 경우: Ubuntu 경유

Mac의 백엔드를 그대로 두고 **이미 Tailscale이 연결되어 있으며 SSH로 접근 가능한 Ubuntu**를 공개 진입점으로 사용할 수 있습니다.

```text
인터넷 → Ubuntu Funnel → Ubuntu 127.0.0.1:18080
                              │ SSH reverse tunnel
                              ▼
                         Mac 127.0.0.1:8080
```

1. Mac에서 로컬 백엔드 health를 확인합니다.
2. Mac의 새 터미널에서 아래 명령을 실행합니다. 사용자명·주소는 실제 SSH 계정·주소로 바꾸고 최초 호스트 키는 관리자로부터 확인합니다.

```bash
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -R 127.0.0.1:18080:127.0.0.1:8080 \
  demo-user@ubuntu-host
```

3. Ubuntu에서 `curl --fail-with-body http://127.0.0.1:18080/actuator/health`가 성공하는지 확인합니다. SSH 서버가 remote forwarding을 허용하고 해당 리스너를 loopback에만 바인딩해야 합니다. `GatewayPorts yes`로 외부 전체에 열지 않습니다.
4. Ubuntu에서 기존 공유 설정을 확인한 뒤 `tailscale funnel --https=443 http://127.0.0.1:18080`을 실행합니다. 필요하면 `sudo`를 사용합니다.
5. tailnet 밖에서 HTTPS 주소를 확인합니다. 종료할 때 **Ubuntu Funnel부터 끄고**, Mac의 SSH 터미널에서도 `Ctrl+C`를 누릅니다.

SSH 연결·Mac 백엔드·Ubuntu Tailscale이 모두 켜져 있어야 합니다. Proxmox 관리 포트나 DB 포트를 외부로 열 필요는 없습니다. 이 경유 절차도 실제 환경에서 확인하기 전까지 성공했다고 간주하지 않습니다.

## 8. 문제 해결과 종료 체크리스트

| 증상 | 확인할 것 |
| --- | --- |
| 로컬 health부터 실패 | Spring·DB 실행, 8080 포트, 서버 설정 |
| HTTPS/Funnel 권한 안내 | MagicDNS·HTTPS·Funnel node attribute, 관리자 승인 |
| 외부 주소에서 502/연결 실패 | Funnel 실행 장치의 로컬 health, 절전·네트워크·SSH 터널 종료 |
| 외부에서만 DNS 실패 | 주소 오타 확인. 공개 DNS 반영에 최대 약 10분이 걸릴 수 있음 |
| API에서 401/403 | 자격증명·JWT·ACTIVE 상태·온보딩·대상 리소스 권한 |
| 로그인/이미지 업로드에서 503 | 서버 로그의 필수 설정 누락 확인. 6장 참고 |
| curl은 성공, 브라우저만 실패 | CORS origin 또는 HTTPS 프런트에서 HTTP API를 호출하는 mixed content |
| `/` 또는 Swagger가 열리지 않음 | `/actuator/health`로 확인. Swagger 비활성화 여부 |
| 429 응답 | 앱의 로그인 실패 제한 등 확인. 터널 오류로 단정하지 않기 |
| 종료했는데 다시 열림 | background 또는 기존 동일 포트 공유 설정 |

Funnel은 현재 beta이며 변경 불가능한 대역폭 제한이 있습니다. 공개 포트는 443·8443·10000, 도메인은 tailnet의 `*.ts.net` 범위로 제한됩니다. 노트북 절전·백엔드/DB 종료 시 끊기며 부하 테스트·상시 운영의 대체 수단이 아닙니다. 앱의 로그인 실패 제한도 전체 API의 DDoS 방어를 대신하지 않습니다.

- [ ] 데모 데이터·승인된 테스트 계정만 사용했다.
- [ ] 개발용 JWT·서명 비밀을 교체했다.
- [ ] 기존 공유와 Swagger 등 인증 없는 경로의 공개 범위를 확인했다.
- [ ] 로컬 health와 tailnet 밖 HTTPS health가 성공했다.
- [ ] 로그인·인증된 `/v1/config`·필요한 업무 API를 확인했다.
- [ ] 브라우저 연동 시 실제 프런트 origin의 CORS를 확인했다.
- [ ] 종료 후 Funnel 설정 제거와 외부 접근 종료를 확인했다.

## 9. 근거와 검증 범위

현재 저장소 기준:

- [공통 설정](../src/main/resources/application.yml): 포트·CORS·로그인 집계 창·업로드 요청 여유 크기
- [local 설정](../src/main/resources/application-local.yml): 개발용 비밀·mock AI·데모 정책
- [SecurityConfig](../src/main/java/team4/emotionmap/platform/security/SecurityConfig.java): 공개 경로·CORS
- [로그인 정책](../src/main/java/team4/emotionmap/account/LoginPolicyProperties.java): 필수 집계 창
- [프로젝트 실행·계정 공급 안내](../ARCHITECTURE.md)

공식 문서:

- [Funnel 개요·요구 조건·제한](https://tailscale.com/docs/features/tailscale-funnel)
- [Funnel CLI: 실행·종료·background 동작](https://tailscale.com/docs/reference/tailscale-cli/funnel)
- [Serve와 공개 범위 차이](https://tailscale.com/docs/features/tailscale-serve)
- [CLI 경로](https://tailscale.com/docs/reference/tailscale-cli)
- [macOS 설치 방식별 지원](https://tailscale.com/docs/concepts/macos-variants)
- [HTTPS 인증서·장치 이름 공개 주의](https://tailscale.com/docs/how-to/set-up-https-certificates)

작성 시 공식 문서·현재 프로젝트 설정과 대조하고, 로컬 Tailscale 1.102.4의 `version`·`funnel --help`를 확인했습니다. 문서의 셸 명령은 문법·상대 링크 검사를 수행합니다. **CLI 도움말 확인은 macOS Funnel 실제 지원이나 공개 URL 동작 검증이 아닙니다.** 실제 로그인·정책 변경·인터넷 공개·종료·SSH 연결은 이 문서의 체크리스트로 확인해야 합니다.
