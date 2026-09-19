# 감정지도 — FE·BE API 명세서 초안

- 문서 버전: **API 0.2.0-draft**
- 기준일: **2026-09-19**
- 기준: 최종 MVP 기획서 **1.4-final**, **ERD 1.0**, 사용자가 이 대화에 전달한 **FE API 요청 v2** 및 **이메일·비밀번호 로그인만 구현한다는 최신 확정**
- 대상: FE 2명 · BE 2명 · AI 1명, 기존 19시간 MVP
- 결과물: 이 문서, `openapi.yaml`(OpenAPI 3.1.0), TypeScript 타입, JSON mock 예시, 검증 시나리오
- 상태: **검토·합의용 계약 초안**. 앱 구현, 실제 HTTP 호출, 인증/DB/동시성/AI 실행 검증은 수행하지 않았다.
- 원문 ERD·기획서와 실제 GitHub 저장소는 수정하지 않았다.

> **자료 접근 범위:** ERD 상세 문서와 첨부 패키지의 SQL을 확인했다. `KOSSCCHTHON-Team4/Frontend`의 `docs/필요api.md`를 연결 도구로 읽으려 했으나 HTTP 404가 반환되었다. 따라서 FE 기준은 사용자가 붙여 넣은 v2 전문이며, 실제 `src/api/`·화면·Zod 구현을 확인했다고 주장하지 않는다. 404만으로 저장소가 존재하지 않는다고 단정하지 않는다.

## 읽는 기준

**[유지]**는 이미 확정한 제품 정책이다. **[계약 제안]**은 이번에 요청·응답을 구체화하기 위해 선택한 초안이다. **[합의 필요]**는 토큰 TTL·수치 한도·운영 도구처럼 실제 연동 전에 정할 의존성이다. OpenAPI에 수록되었다는 사실이 승인이나 구현 완료를 뜻하지 않는다.

**[최신 확정] 로그인은 오직 이메일·비밀번호 방식이다.** 공급자 인증·소셜 로그인·공급자 토큰 교환·계정 연결은 구현하지 않는다. `LoginRequest`는 `{email, password}` 단일 객체이며 `/config.auth.mode`는 `EMAIL_PASSWORD`만 반환한다. 성공 응답의 이메일은 필수 문자열이다. 회원가입·비밀번호 재설정 API는 이번 변경으로 추가하지 않는다.

v0.1의 인증 방식 선택지는 폐기한다. 상세 ERD 보완은 이 패키지의 `AUTH_ERD_DELTA.md`가 ERD 1.0 인증 절보다 우선하는 제안이다. 기존 원본 ERD 파일이나 운영 DB를 수정한 것은 아니다.

**MOCK 숫자 주의:** `/config` 예시의 반경 1000m, 중심 좌표, 토큰 TTL·파일/본문/작성량/페이지 제한은 로컬 mock 구성 예시이다. 실제 운영 수치를 임의 확정하지 않았다. 배달 시간 `09:00`, 시간대 `Asia/Seoul`, 4축의 `-1/+1`, 카테고리 최대 3개는 이미 확정된 정책이다.

## 목차

1. FE 요청 반영표와 ERD 차이
2. 공통 HTTP·타입·오류·재시도 계약
3. 인증과 계정 설정
4. 경험 분석·생성 및 이미지 수명
5. 수신함 상태·열람·필터·페이지
6. 좋아요·독립 PRIVATE 및 삭제
7. 지도·신고·운영 범위
8. 엔드포인트별 요청·응답
9. 공통 DTO 필드 사전
10. 오류 코드 전체 목록
11. ERD 보완안과 무결성 경계
12. FE 구현 예시 및 연동 우선순위
13. 계약 검증 시나리오
14. 남은 합의와 참고 자료

---

## 1. FE 요청 반영표와 ERD 차이

| FE 요청 | 처리 | 계약·스키마 영향 |
|---|---|---|
| 경로를 기획서 12장과 통일 | [유지] | 기존 경로 유지. `/config`, `/images`만 FE 추가 제안으로 포함 |
| Bearer access token | [계약 제안] 채택 | 서비스 자체 발급 토큰 사용. 실제 TTL·갱신 정책은 기존 합의 대상으로 유지 |
| 로그인 `hasOnboarded` | [계약 제안] 채택 | 파생 응답값, 별도 boolean 컬럼 없이 일관된 온보딩 상태로 계산 |
| `email: string` | [최신 확정 반영] 필수 문자열 | 이메일·비밀번호 계정이므로 로그인·내 계정 응답에서 null을 허용하지 않음. 인증 저장 구조 보완 |
| 로그인 5회 실패·1분 제한 | [FE 제안] 서버 적용 권고 | 앱 재시작/브라우저 변경으로 우회 불가한 서버 상태 필요. 관찰 구간·키·저장소 미결 |
| `/config` | [계약 제안] 추가 | 반경·지도 초기 중심·배달 시각·제한을 서버 설정으로 제공. 사용자별 선택값 아님 |
| `preference_version` 응답 | [계약 제안] 채택 | JSON은 `preferenceVersion` 10진 문자열. PATCH에 `expectedPreferenceVersion` 추가 |
| 분석 결과 | [계약 제안] FE 필드 유지+보완 | `analysisToken`, `expiresAt`, `atmosphereStatus`, `warnings` 추가. 출처 위조 방지 |
| `imageId` 선행 업로드 | [계약 제안] 채택 | **기존 10테이블에는 업로드 대기 자원이 없음**. `image_uploads` 등 메타데이터 필요 |
| 생성 실패 후 재요청 | [계약 제안] 보완 | 직접 생성/업로드/신고에 `Idempotency-Key`. 키 저장소 필요, 좋아요에는 적용하지 않음 |
| 오늘 상태 5종 | [계약 제안] 유지 | `pendingReason`, `errorReason`, 날짜·예정 시각 추가. 내부 enum과 외부 enum 분리 |
| 삭제된 오늘의 편지 | [계약 제안] tombstone | `DELIVERED` 유지+`availability=UNAVAILABLE`. 0개로 바꾸거나 재배달하지 않음 |
| 좋아요 사본 ID 반환 | [조건부 제안] 최초 성공만 | 즉시 응답에서만 `copiedMemoryId`. 원문·배달·응답 캐시·로그에 연결 금지 |
| 중복 좋아요 오류 | [수정 제안] **200** | `ALREADY_COPIED`, 사본 ID null. 실패처럼 재시도시켜 중복 생성하지 않음 |
| 지도 핀·장소명 | [계약 제안] 내부 핀 | 새 작성은 새 Place, 명시적 가시 placeId 재사용만 허용. 자동 인접 병합 없음 |
| 신고 코드 | [계약 제안] 7개 코드 | `reports.reason`에 코드 저장. 선택 `details`를 별도 열로 추가 권고 |
| 내 보낸 편지·좋아요 수 | [누락 보완] | 원래 기획의 `GET /users/me/memories?type=LETTER`를 포함 |

**그대로 유지하는 비노출 원칙:** 수신자 응답에 원작성자 ID·이메일·닉네임·프로필·우편함 좌표를 넣지 않는다. `memoryId`와 `deliveryId`는 접근 대상 식별자이지 작성자 식별정보가 아니다. 미선정 글의 본문·핀·개수를 반환하지 않는다. 합성 예시인지 구분하는 `dataOrigin`은 사람의 신원이 아닌 데이터 생성 유형이다.

## 2. 공통 HTTP·타입·오류·재시도 계약

### 2.1 기본 규칙

| 항목 | 계약 초안 |
|---|---|
| API base | 배포 환경의 `API_BASE_URL`. OpenAPI 서버 `/`. `/api/v1` 같은 접두어를 임의 추가하지 않음 |
| 전송 | HTTPS. JSON 요청/응답은 `application/json`; 이미지 업로드만 `multipart/form-data` |
| 인증 | `Authorization: Bearer <accessToken>`; 로그인만 무인증. 다른 경로는 ACTIVE 초대 계정 필요 |
| 온보딩 전 허용 | `/config`, `/atmosphere-axes`, `/place-categories`, `/users/me`, `/users/me/onboarding` |
| 성공 포맷 | `{data:...}` 봉투 없이 DTO를 바로 반환. 목록만 `{items, pageInfo}` |
| 식별자 | UUID 문자열. 카테고리는 고정 문자열 코드. DB bigint revision은 10진 문자열 |
| 시각 | ISO 8601 오프셋 포함. 예시는 `+09:00`, 서버는 `Z`도 가능. 날짜 계산은 Asia/Seoul |
| nullable | 스키마의 필수 키는 값이 없어도 `null`로 보냄. 임의로 누락하지 않음 |
| 금지 필드 | 요청의 ownerId/sourceMemoryId/status/likedAt 등은 받아서 무시하지 않고 거절 |
| 캐시 | 개인 JSON·이미지 응답 `Cache-Control: private, no-store`. 공유 캐시·서비스워커 장기 저장 제외 |
| 로그 | 토큰, 비밀번호, 본문, 자연어 취향, 이미지경로, 원문+사본ID 쌍의 본문 로그 금지 |
| HTTP 204 | 본문이 없음. FE가 `response.json()`을 호출하지 않음 |

원래 DB enum과 API enum은 구분하며 raw Entity 전체를 직렬화하지 않는다. `/config`의 숫자 제한은 모든 인스턴스와 FE가 같은 버전을 사용한다. 설정이 준비되지 않으면 503 `CONFIGURATION_UNAVAILABLE`로 실패하고 조용히 기본 반경을 넣지 않는다.

### 2.2 JSON·텍스트 검증

4축은 이름이 정확한 네 키이고 각각 숫자 `-1` 또는 `1`이어야 한다. 누락·추가 축·문자열 `"1"`·null·0·0.5·중복 키를 거절한다. **1.0처럼 소수 표기를 거절하려면 JSON 토큰 수준 검사도 필요**하다. OpenAPI 3.1/JSON Schema에서는 수학적으로 1과1.0을 같은 정수로 볼 수 있으므로 YAML 검증만으로 이 요구를 다 검사했다고 하지 않는다. JSON 중복 키 역시 파싱 뒤 객체만으로 복원할 수 없으므로 서버 파서에서 검사한다. [W1]

본문은 저장·분석·토큰 검증에서 같은 문자열을 사용한다. 본문 원문을 임의로 Unicode 정규화/개행 변경/trim해서 다른 해시로 만들지 않는다. 공백 문자만 있으면 거절한다. 길이는 Unicode 코드 포인트 기준으로 세도록 제안하며 JS `.length`만을 그대로 쓰지 않는다. 자연어 취향과 신고 details는 앞뒤 공백을 제거하고 비었으면 null로 정규화하는 안이다. 최대 길이는 `/config.limits`로 통일한다. 비밀번호는 trim/정규화하지 않는다.

### 2.3 공통 오류

```json
{
  "code": "INVALID_ATMOSPHERES",
  "message": "분위기 4축을 각각 -1 또는 1로 입력해 주세요",
  "retryAfterSeconds": null,
  "fieldErrors": [{ "field": "atmospheres.STAY_STYLE", "reason": "REQUIRED" }],
  "requestId": "80000000-0000-4000-8000-000000000001"
}
```

FE는 `code`로 분기하고 `message`를 파싱하지 않는다. 에러 객체 키는 항상 포함한다. `retryAfterSeconds`가 없으면 null이며 429/재시도 가능한 일부409·503에서는 서버의 실제 잔여 초를 넣고 `Retry-After` 헤더와 일치시킨다. `WWW-Authenticate: Bearer`는 보호 자원의401 응답에 포함한다. 오류에 SQL·로컬 경로·원작성자 정보를 포함하지 않는다. [W2]

| HTTP | 용도 |
|---|---|
| 400 | JSON·중복 키·query·커서·지원하지 않는 요청 구조 |
| 401 | 인증 누락·만료·잘못된 토큰·잘못된 로그인 자격증명 |
| 403 | 초대 미승인·계정 제한·온보딩 필요 |
| 404 | 없는 대상 또는 권한 없는 대상. 상세한 존재 여부를 구분해 노출하지 않음 |
| 409 | 버전 충돌·다른 내용으로 재온보딩·이미지 재사용·요청키 충돌·처리 중 |
| 410 | 소유/기수신 등 과거 접근 자격이 확인된 삭제·숨김 원문, 본인 만료 이미지 |
| 413 /415 | 파일 바이트 초과 / 지원하지 않는 이미지 종류 |
| 422 | 읽을 수 있는 JSON이지만 필수값·축·카테고리 등 의미 검증 실패 |
| 429 | 로그인 시도·요청률·일일 직접 작성 제한 |
| 500 /503 | 내부 오류 / 일시 의존성·저장소 장애 |

권한 없는 객체는 404로 통일하는 제안이다. 사라진 기록의410은 누군가의 게시물 존재를 새로 알려주는 수단이 되지 않도록 과거 권한부터 확인한다. HTTP는 금지된 자원의 존재를 감추기 위해404를 사용할 수 있다. [W2]

### 2.4 생성 재시도

`POST /memories`, `POST /images`, `POST /reports`에는 **[계약 제안] `Idempotency-Key: <UUID>`를 필수**로 둔다. FE는 한 번의 의도된 제출에 키 하나를 만들고, 응답 유실·시간 초과 때 **동일 키·동일 payload**를 재전송한다. 내용을 의도적으로 바꾸면 새 키를 사용한다.

서버는 사용자+메서드+경로+키를 고유하게 관리하고 요청 정규화 해시를 비교한다. 첫 완료는201, 같은 요청의 완료 재확인은200이다. 다른 payload면409 `IDEMPOTENCY_KEY_REUSED`, 실행 중이면409 `REQUEST_IN_PROGRESS`와 재시도 안내다. 업로드 지문은 원본 요청 파일 바이트를 기준으로 하며 처리 후 인코딩 결과로 키 동등성을 바꾸지 않는다.

완료 기록 확인은 만료된 분석토큰·이미 소비한 imageId 검사보다 먼저 수행한다. 완료했던 경험이 삭제됐으면410이고 새 경험을 만들지 않는다. 완료 업로드가 만료됐으면410이며 명시적 새 업로드를 유도한다. 접수했던 신고는 원문이 나중에 숨겨져도 본인에게 접수증만 재반환할 수 있다.

MVP의 요청키 보존 범위/정리 시점은 운영 계약이다. 최소한 테스트 운영과 재시도 기간에는 완료 키를 보존한다. 키 보존이 끝났다고 무조건 자동 재생성하는 FE 재시도는 금지한다. 저장소에는 원문 응답 전체 대신 자원 종류·ID·요청 지문만 보관하고 현재 접근 상태를 재검사한다.

**좋아요에는 이 범용 응답 캐시를 사용하지 않는다.** 자연 키인 `deliveryId`와 `liked_at`이 중복 방지 기준이다. 사본ID를 포함한 성공 응답을 지속 저장하면 원문·사본 비연결 정책과 충돌한다.

## 3. 인증과 계정 설정

### 3.1 이메일·비밀번호 전용 로그인 — 확정

`POST /auth/login`은 아래 두 필드만 받는다. 클라이언트는 공급자 인증 SDK, 외부 토큰, 리다이렉트·콜백을 사용하지 않는다.

```json
{
  "email": "tester@example.invalid",
  "password": "example-only-not-a-real-password"
}
```

두 필드 모두 필수 문자열이다. `provider`, `providerToken` 등 다른 필드, 필드 누락·null·잘못된 타입은 `400 INVALID_REQUEST`로 거절한다. 해석 가능한 문자열이지만 이메일 형식이 틀리거나 비밀번호가 비어 있으면 `422 VALIDATION_ERROR`다. 잘못된 JSON과 중복키는 기존 `INVALID_JSON`·`DUPLICATE_JSON_KEY`를 사용한다. `additionalProperties: false`로 추가 필드를 받지 않는다.

서버는 자체 자격증명에서 로그인 이메일을 조회하고 저장된 비밀번호 해시를 검증한다. 검증한 뒤 계정의 초대·접근 상태를 확인하고 서비스 access token을 발급한다. 로그인 요청의 이메일로 계정을 자동 생성하거나 기존 계정과 임의 연결하지 않는다. 로그인 성공은 이메일 소유권 검증 완료와 동일하지 않다.

`AuthResponse.user.email`과 `UserProfile.email`은 항상 유효한 문자열이다. `/config.auth.mode`는 `EMAIL_PASSWORD` 고정값이며, 로그인 화면을 띄우기 위해 인증 후의 config를 먼저 읽을 필요는 없다. 기존 Bearer 기반 보호 API 계약은 유지한다.

**비밀번호 저장 설계 제안:** ERD 1.0의 공급자 식별자용 `auth_identities` 대신 `email_password_credentials`를 사용한다. 필수 로그인 이메일·정규화 조회키·`password_hash`를 두고 계정과 1:1로 연결한다. 원문 비밀번호·복호화 가능한 비밀번호·단순 SHA-256 값은 저장하지 않고, Argon2id 등 비밀번호 전용 해시를 검토한다. 정확한 알고리즘·비용은 배포 환경에서 검증한다. [W7]

Spring Security의 `PasswordEncoder`로 해시 생성과 `matches` 검증을 수행하는 구현을 제안한다. 비밀번호는 trim·대소문자 변환·임의 Unicode 정규화 없이 사용하고 요청·응답·DB 조회 로그·APM에서 마스킹한다. [W8]

이메일 조회키 정규화는 초기 자격증명 생성과 로그인에 동일하게 적용한다. 서비스 정책으로 앞뒤 공백 제거·소문자화를 검토할 수 있으나, 제공자별 점·플러스 주소 제거를 자동 추가하지 않는다. 구체적인 정규화·길이·비밀번호 정책은 자격증명 생성 절차와 함께 합의한다.

공개 회원가입·비밀번호 재설정은 이 변경의 범위가 아니다. 초대 참여자의 최초 계정과 비밀번호 해시를 안전하게 준비하는 내부 생성·전달 절차는 로그인 연동의 선행 조건이며, 공용 비밀번호를 코드·SQL·Git에 넣지 않는다. `EMAIL_ALREADY_EXISTS`는 예약 코드만 유지하고 로그인에서는 반환하지 않는다.

### 3.2 토큰·초대·로그인 제한

**[계약 제안]** access token만 사용하고 refresh endpoint는 이번 초안에서 제외한다. 서버는 `expiresAt`, `expiresInSeconds`, `tokenType`, `refreshSupported=false`를 반환한다. TTL의 실제 수치는 합의가 필요하다. FE는 토큰을 메모리에 보관하고 만료되면 다시 로그인하는 단순한 방식을 제안한다. 지속 로그인이나 refresh를 선택하면 저장·회전·폐기 계약을 추가해야 한다. 브라우저에서 토큰을 지우는 것과 서버에서 토큰을 폐기하는 것은 다르다.

자격증명이 유효한 뒤 초대를 확인한다. 미초대/PENDING은 `403 INVITATION_REQUIRED`, SUSPENDED는 `403 ACCOUNT_SUSPENDED`, CLOSED는 `403 ACCOUNT_CLOSED`다. 존재하지 않는 계정과 비밀번호 오류는 같은 `401 INVALID_CREDENTIALS`로 응답한다. 보호 API에서도 현재 계정 상태를 확인하며, 토큰의 과거 발급 상태만으로 접근을 계속 허용하지 않는다. [W3]

FE가 요청한 **5회 실패·60초 제한은 서버 적용 제안**으로 반영했다. 실제 관찰 구간과 초기화 조건·동시 요청 처리는 서버 인증 계약에서 정한다. 앱 종료·브라우저 변경으로 카운터가 사라져서는 안 된다. 다중 인스턴스와 서버 재시작을 고려한 저장소를 사용하고, 사용자별 제한과 IP 등의 남용 방어를 함께 설계한다. 존재하는 이메일만 잠금 응답이 달라져 계정 존재 여부가 드러나지 않도록 한다. DB·해시 검증기 등 서버 장애를 사용자 오입력으로 계산하지 않는다. 시도 제한은 등록 여부와 무관한 키에도 적용하여 계정 존재 여부를 드러내지 않도록 한다. [W3]

### 3.3 온보딩 및 취향 버전

`hasOnboarded`는 위치와 최초 취향 버전의 원자적 완료에서 계산한다. 온보딩 전에는 `mailbox`, `atmospheres`, `preferenceVersion`, `preferenceEffectiveAt`가 모두 null이다.

온보딩은 사용자 행을 잠근 뒤 위치와 최초 취향을 함께 저장한다. 정규화한 최초 입력을 그대로 재전송하면 변경 없이 200을 반환하고, 다른 값으로 다시 초기화하면 409다. 동일 여부는 이후 수정할 수 있는 최신 취향이 아니라 **최초 v1 입력**과 비교한다.

취향 PATCH에는 4축·설명 전체와 `expectedPreferenceVersion`을 보낸다. 최신 버전이 다르면 409 후 GET으로 다시 확인하고, 변경에 성공하면 전체 설정을 가진 새 불변 버전을 INSERT한다. 같은 버전·같은 값은 새 버전 없이 200이다. `preferenceVersion`은 DB revision의 10진 문자열이며 내부 버전 UUID와 구분한다.

설명을 null로 보내면 이후 설정에서 설명을 비우지만, 과거 선정에 사용한 버전은 수정하지 않는다. 위치는 변경할 수 없고 적용 시각도 서버가 정한다. 09시 기준 설정과 이미 확정된 날짜의 선정 결과를 소급 교체하지 않는다.

## 4. 경험 분석·생성 및 이미지 수명

### 4.1 분석 결과의 출처를 확인한다

FE가 돌려보낸 값만으로 `AI/USER` 출처나 모델 실행 상태를 신뢰하지 않는다. 이를 위해 **[계약 제안] `analysisToken`**을 추가한다.

서버는 사용자 귀속, 본문 해시, 분석한 4축·카테고리, 실행 상태, 모델·프롬프트·사전 버전, 만료를 서명한 확인값을 반환한다. FE는 이를 해석하지 않고 저장 요청에 그대로 포함한다. 본문이나 식별정보를 확인값에 불필요하게 중복 저장하지 않으며, 이 값을 영구 분석 로그나 원문 연결 키로 저장하지 않는다.

최종 저장에서 서명·로그인 사용자·본문·만료를 확인한다. AI 제안과 같은 최종값은 AI, 사용자가 변경하거나 보완한 값은 USER로 기록한다. token을 생략하거나 null로 보내면 명시적인 수동 저장으로 취급하고 실행 상태는 NOT_RUN, 출처는 USER다. 실패한 AI 시도의 이력을 남기려면 그 실패 응답의 유효한 token을 함께 보낸다.

**분석 응답의 `categoryStatus`와 최종 경험의 `categoryStatus`는 구분한다.** 전자는 AI가 분류했는지, 후자는 최종 카테고리가 존재하는지다. AI가 실패했어도 작성자가 카페를 직접 고르면 최종 상태는 CLASSIFIED이고, 소유자에게 보이는 AI 이력은 FAILED일 수 있다.

| API의 분석 결과 | ERD 매핑 |
|---|---|
| category CLASSIFIED | `category_analysis_status=SUCCEEDED` |
| category UNCLASSIFIED | `category_analysis_status=INSUFFICIENT` |
| category FAILED | `category_analysis_status=FAILED` |
| 분석하지 않은 수동 저장 | `category_analysis_status=NOT_RUN` |
| atmosphere SUCCEEDED / PARTIAL / FAILED | 동일한 실행 상태 |
| 분석하지 않은 수동 저장 | `atmosphere_analysis_status=NOT_RUN` |

계정 취향·사진·주변 업체 정보로 본문에 없는 분위기를 채우지 않는다. 분석이 실패하면 200 응답에 FAILED와 미결 축을 넣어 수동 보완을 지원한다. 우리 서버 자체 장애나 요청 검증 실패는 별도 503/4xx다. **최종 저장에는 네 축이 모두 필요하며, 안전 검사 실패를 분류 실패처럼 통과시키지 않는다.**

본문을 수정한 뒤 도착한 옛 분석 응답은 FE가 폐기한다. AbortController나 요청 번호로 대응시키고 서버도 token의 본문 해시를 검사한다. 만료되면 다시 분석하거나, token을 null로 바꾸어 수동 확정하는 선택을 사용자에게 명확히 제공한다.

### 4.2 생성 성공과 배달 성공은 다르다

`POST /memories`는 DB 리소스를 생성한 뒤 201을 반환한다. LETTER의 최초 `ownerState.moderationStatus=PENDING`, `availableAt=null`은 정상이다. 안전 승인이 끝나 최초 available_at이 설정되어야 일일 후보가 된다. 작성 직후 모든 주변 사용자에게 배달하지 않는다.

상태는 내 기록 진입·수동 갱신으로 확인하고 자동 폴링을 필수로 추가하지 않는다. 직접 PRIVATE도 분위기·카테고리 AI 분석 대상이지만 일일 후보는 아니다. PRIVATE의 별도 moderation 여부는 운영 계약이다.

요청에는 ownerId, originKind, moderationStatus, dataOrigin을 받지 않는다. 서버가 직접 작성과 운영 환경을 판단하며, LETTER_COPY는 like의 내부 작업에서만 만든다.

### 4.3 선행 이미지 업로드

`POST /images → imageId → POST /memories`에는 기존 ERD에 없는 **미첨부 업로드 상태**가 필요하다. 로컬 파일 저장은 유지한다.

추가 메타데이터의 제안은 `image_uploads(id, owner_id, storage_path, media_type, size_bytes, width, height, status, expires_at, attached_memory_id, created_at)`다. STAGED/ATTACHED/EXPIRED를 구분하고 서버가 생성한 임의 파일명을 사용한다.

경험에 첨부할 때 본인 소유·미만료·미사용 여부를 확인하고 경험 생성과 ATTACHED 전환을 같은 트랜잭션으로 처리한다. 같은 imageId를 두 경험에 재사용하지 않는다. 임시 TTL 정리 대상은 미첨부 파일이며, ATTACHED 파일을 만료 처리로 지워서는 안 된다. 업로드 직후의 미리보기는 FE가 원래 선택한 Blob으로 처리한다.

서버에서도 실제 파일 디코딩, 형식·바이트·폭·높이·픽셀 수 검사와 재인코딩·EXIF 제거를 수행하도록 권고한다. 확장자나 클라이언트 MIME만 신뢰하지 않고, FE의 EXIF 제거가 서버 검증을 대체하지 않는다. [W4]

파일과 DB는 자동으로 함께 롤백되지 않으므로 실패·프로세스 중단 이후의 파일 정리가 필요하다. 업로드 실패를 사진 없는 성공으로 바꾸지 않는다. 반대로 사용자가 사진을 첨부하지 않은 imageId=null은 정상이다.

### 4.4 Bearer로 보호된 이미지 표시

`imageUrl`은 `/memories/{id}/image`라는 보호 API 경로다. 일반 `<img src>`에 임의 Authorization 헤더를 붙이는 방식이 아니라, FE가 fetch로 Bearer를 보내 Blob을 받은 뒤 object URL로 표시한다. 컴포넌트 해제·로그아웃 때 URL을 해제한다. 예시는 `frontend/http_examples.ts`에 있다. [W5][W6]

토큰을 URL query에 넣지 않는다. imageUrl이 설정된 API origin인지 확인한 뒤 자격증명을 첨부한다. CORS는 허용 FE origin과 Authorization/Content-Type/Idempotency-Key를 명시하고, 읽어야 하는 응답 헤더도 노출한다. FormData의 Content-Type은 브라우저가 boundary와 함께 생성하므로 직접 고정하지 않는다.

서버의 접근 차단이 이미 내려받은 이미지·스크린샷까지 회수한다는 의미는 아니다. 이후의 API 접근과 불필요한 공유 캐시를 차단하는 범위다.

## 5. 수신함 상태·열람·필터·페이지

### 5.1 DB 상태와 FE 상태의 매핑

FE가 제시한 5개 status를 유지한다. 상태 조회가 정상 수행되면 HTTP 200이며, 시스템 오류를 후보 없음으로 바꾸지 않는다.

| 상황/내부 상태 | 외부 status | pendingReason / errorReason | delivery |
|---|---|---|---|
| 온보딩 전 | HTTP 403 | ONBOARDING_REQUIRED 오류 | — |
| 오늘 09시 전의 대상 계정 | PENDING | BEFORE_SCHEDULE | null |
| 오늘 09시 이후 온보딩 | PENDING | FIRST_DELIVERY_TOMORROW | null |
| 대상이나 슬롯 대기·생성 지연 | PENDING | SCHEDULE_DELAYED | null |
| PROCESSING | PENDING | PROCESSING | null |
| NO_CANDIDATE | NO_CANDIDATE | null | null |
| DELIVERED + 유효 원문 | DELIVERED | null | AVAILABLE 편지 |
| DELIVERED + 삭제/숨김 원문 | DELIVERED | null | UNAVAILABLE 안내 항목 |
| RETRYABLE_ERROR | RETRYING | RETRYABLE_FAILURE | null |
| 작업 상태·수신 기록 불일치 | ERROR | SELECTION_STATE_INCONSISTENT | null |
| 복구할 수 없는 당일 처리 실패 | ERROR | PROCESSING_FAILURE | null |
| 계정 제한·SKIPPED_ACCESS | HTTP 403 | ACCOUNT_SUSPENDED 등 | — |
| API 자체의 DB 조회 장애 | HTTP 503 | SERVICE_UNAVAILABLE | — |

EXPIRED_ERROR는 지난 날짜의 종료 상태이므로 오늘의 오류로 복사해 오지 않는다. 오늘 행이 없으면 현재 시각·온보딩 여부에 맞는 PENDING이다. `serviceDate`는 오늘 KST, `scheduledAt`은 오늘 09시다. `nextScheduledAt`은 09시 전이면 오늘 09시, 이후면 내일 09시이며 처리 중인 작업의 실제 완료 시각을 예측하는 값은 아니다.

삭제된 원문도 배달 성공은 유지한다. 안내 항목에는 본문·사진·좌표·태그를 넣지 않고 unavailableReason만 제공한다. 이를 이유로 그날의 할당을 다시 열거나 대체 편지를 만들지 않는다.

### 5.2 필터 query

반복 파라미터를 사용한다. OpenAPI 설정은 `style=form, explode=true`다.

```text
GET /letters?atmospheres=CROWD_LEVEL%3A-1&atmospheres=STAY_STYLE%3A-1&categories=CAFE&categories=STUDY_WORK&limit=20
```

이는 `(조용한 OR 오래 머물기 좋은) AND (카페 OR 공부·작업 공간)`이다. 쉼표로 묶는 형식과 반복 형식을 동시에 지원하지 않는다. 빈 조건은 `atmospheres=` 대신 파라미터를 생략한다.

조회에서는 같은 축의 양쪽 값을 선택할 수 있다. 현재 경험의 해당 축은 반드시 둘 중 하나이므로, 이 경우 **전체 분위기 OR 조건이 모든 경험에 참이 될 수 있다.** FE는 조건이 넓어진다는 점을 표시하고 조용히 AND로 바꾸지 않는다. 계정·최종 경험의 양쪽 값 동시 저장은 여전히 금지다.

조회 카테고리는 8종 중 여러 종류를 선택할 수 있다. 저장 상한 3개와 혼동하지 않는다. 필터를 적용할 때는 현재 열람 가능한 기록만 검사하고, 삭제 안내 항목은 필터 없는 전체 보기에서만 유지한다.

### 5.3 페이지네이션

목록은 `{items, pageInfo:{nextCursor,hasMore}}`다. 다음이 없으면 null/false이고 첫 요청에서는 cursor를 생략한다. 수신함은 `(delivered_at DESC,id DESC)`, 경험 목록은 `(created_at DESC,id DESC)`, 지도 핀은 `id ASC`를 제안한다.

커서는 서버가 서명한 불투명 값이며 사용자·경로·필터·bbox·정렬·첫 페이지의 상한에 귀속된다. 조건을 바꾸면 CURSOR_CONTEXT_MISMATCH, 변조·해석 실패는 INVALID_CURSOR다. 필터를 바꾼 FE는 커서를 버리고 첫 페이지부터 조회한다.

카테고리 연결 때문에 같은 경험이 여러 번 나타나지 않도록 EXISTS 등으로 처리한다. 조회 순서는 **권한 → 필터 → 정렬 → 페이지**다. 전체 미선정 글이나 전체 사용자 개수는 반환하지 않는다.

삭제·권한 변경으로 후속 페이지 항목이 줄어들 수 있다. 보안상 숨겨진 내용을 페이지 일관성을 위해 복원하지 않으며, 키셋 페이지가 완전히 불변인 DB 스냅샷을 보장하는 것은 아니다.

## 6. 좋아요·독립 PRIVATE 및 삭제

### 6.1 중복 좋아요는 오류가 아니다

최초 동작과 재요청 모두 HTTP 200으로 응답한다.

```json
{"deliveryId":"40000000-0000-4000-8000-000000000001","status":"COPIED","likedAt":"2026-09-20T10:06:00+09:00","copiedMemoryId":"30000000-0000-4000-8000-000000000003"}
```

```json
{"deliveryId":"40000000-0000-4000-8000-000000000001","status":"ALREADY_COPIED","likedAt":"2026-09-20T10:06:00+09:00","copiedMemoryId":null}
```

사본 ID의 최초 응답 반환은 FE 편의를 위한 신규 제안이다. 실행 중 알고 있는 ID를 응답에만 담고, 원문·수신 행·범용 응답 캐시·로그에 연결 정보를 보존하지 않는다. 첫 응답을 놓쳤다면 ID를 재구성하거나 내용 해시로 역추적하지 않고 보관함 목록으로 이동한다.

서버는 실제 수신자 여부를 먼저 확인한다. liked_at이 이미 있으면 원문이나 사본의 이후 삭제와 무관하게 ALREADY_COPIED를 반환한다. 아직 실행하지 않았고 원문이 무효라면 410이다. 유효하다면 원문·배달 행 잠금 안에서 PRIVATE·카테고리·독립 이미지 생성과 liked_at을 함께 확정한다.

사본 파일에는 새 UUID 경로를 사용한다. 원문 ID·원작성자 ID·배달 ID·self-FK·내용 해시 연결을 사본에 저장하지 않는다. LETTER_COPY는 생성 유형이지 원문 추적 키가 아니다.

### 6.2 삭제·읽음

DELETE는 본인 소유 경험만 가능하다. 최초 삭제와 이미 삭제된 본인 경험의 재요청은 204, 타인 경험은 404다. 삭제 후 내용 접근은 막지만 날짜 슬롯·수신 이력·누적 liked_at은 남기며, 독립 사본과 그 이미지도 유지한다.

수신자가 원문을 삭제하거나 받은 편지를 숨기는 API를 새로 추가하지 않는다. 읽음 기록은 PATCH에서만 변경하고 GET 상세 조회에는 쓰기 부작용을 넣지 않는다. readAt은 최초 서버 시각을 유지한다.

## 7. 지도·신고·운영 범위

### 7.1 지도 핀

bbox의 순서는 **서쪽 경도, 남쪽 위도, 동쪽 경도, 북쪽 위도**다. 예시는 `126.9900,37.6000,127.0100,37.6200`이다. 한국 단일 지역 MVP에서 반자오선을 가로지르는 bbox는 제외하는 계약안이다.

placeId 없이 직접 경험을 만들면 새 Place를 생성하고 label은 null이다. 기존의 가시 핀을 명시적으로 선택할 때만 placeId를 함께 보내는 안을 추가했다. 그 핀에 현재 열람 가능한 경험이 있어야 하며 좌표도 기존 값과 일치해야 한다. 불일치는 422, 없는 핀이나 비가시 핀은 404다.

같은 좌표의 여러 핀을 DB에서 자동 병합하지 않는다. FE가 겹치는 핀을 시각적으로 묶더라도 각각의 placeId는 유지한다. 서버의 memoryCount는 현재 사용자의 가시 경험만 중복 제거해 집계하며, 가시 경험이 0개인 핀은 반환하지 않는다. label이 null이면 일반 안내 문구를 쓰고 실제 상호명을 추측하지 않는다.

### 7.2 신고 및 운영 도구

신고 사유는 **SPAM / ABUSE / SEXUAL_CONTENT / VIOLENCE_OR_DANGEROUS_CONTENT / PERSONAL_INFORMATION / COPYRIGHT / OTHER**를 초안으로 제안한다. reason은 코드, details는 선택 설명이다. OTHER에서 설명을 필수로 할지는 운영 합의 사항이다.

`reports.reason`에 코드를 저장할 수 있다. details를 별도로 보관하려면 nullable 열을 추가한다. 같은 사람의 새로운 신고를 UNIQUE로 막지는 않고, 동일 제출의 네트워크 재시도만 요청 키로 구분한다.

POST 성공은 접수이지 숨김 완료가 아니다. 운영자는 별도 제한된 도구로 신고 조회·검토·종결, 특정 원문 숨김, 계정 접근 제한을 수행할 수 있어야 한다. **이 OpenAPI는 참여자용 22개 API이며 관리자 기능이 구현됐다는 뜻은 아니다.** 운영 HTTP API 또는 제한된 CLI를 정하고, 관리자 인가·처리 이력·보존 정책을 초대 테스트 전에 합의해야 한다.

독립 PRIVATE를 원문 ID로 일괄 회수할 수 없다는 기존 제약은 유지한다.

---

## 8. 엔드포인트별 요청·응답

아래 예시는 모두 가상 mock이다. 상세 정의는 9장 DTO와 OpenAPI를 함께 사용한다. 성공 응답의 null 필드를 생략하지 않는다. 모든 API의 전체 성공·오류 예시는`examples/mock_examples.json`에있다. 경로의 UUID·토큰·cursor는 실제 서비스 값이 아니다.

| 순서 | 메서드 | 경로 | 용도 |
|---:|---|---|---|
| 1 | POST | `/auth/login` | 로그인 및 초기 설정 여부 |
| 2 | GET | `/config` | 고정 반경·제한·정기 배달 설정 |
| 3 | GET | `/atmosphere-axes` | 확정 분위기4축 사전 |
| 4 | GET | `/place-categories` | 자체 장소 카테고리8종 |
| 5 | GET | `/users/me` | 본인 설정·온보딩 상태 |
| 6 | POST | `/users/me/onboarding` | 최초 위치·4축 설정 |
| 7 | PATCH | `/users/me/preferences` | 4축·자연어 취향 변경 |
| 8 | POST | `/memories/analyze` | 본문의 분위기·카테고리 AI 제안 |
| 9 | POST | `/memories` | LETTER 또는 직접 PRIVATE 생성 |
| 10 | POST | `/images` | JPEG/PNG1장 선행 업로드 |
| 11 | GET | `/memories/{id}/image` | 권한 확인 후 경험 이미지 |
| 12 | GET | `/letters/today` | 오늘 배달 상태 조회 |
| 13 | GET | `/letters` | 누적 수신함·조회 필터 |
| 14 | PATCH | `/letters/{deliveryId}/read` | 최초 읽음 기록 |
| 15 | POST | `/letters/{deliveryId}/like` | 일회성 좋아요·독립 PRIVATE 복사 |
| 16 | GET | `/bookmarks` | 내 PRIVATE 보관함 |
| 17 | GET | `/memories/{id}` | 경험 상세·작성자 상태 |
| 18 | DELETE | `/memories/{id}` | 내 원문·PRIVATE 일반 삭제 |
| 19 | GET | `/users/me/memories` | 내가 보낸 LETTER·누적 반응 |
| 20 | GET | `/places` | 현재 사용자 권한 내 지도 핀 |
| 21 | GET | `/places/{id}/memories` | 한 내부 핀의 권한 있는 경험 |
| 22 | POST | `/reports` | 열람 가능한 경험 신고 접수 |

### 8.1 `POST /auth/login`

**로그인 및 초기 설정 여부** · operationId: `login` · 성공 `200`

이메일·비밀번호로만 로그인한다. email과 password 두 필드를 필수로 받으며 그 외 필드는 INVALID_REQUEST로 거절한다. 비밀번호 해시 검증 뒤 초대·계정 상태를 확인하고 서버 access token을 발급한다. 로그인 시 새 계정을 자동 생성하지 않는다. 5회 실패·60초 서버 제한은 기존 FE 계약 제안이며 관찰 구간·초기화·저장 방식은 합의 대상이다.

**요청 스키마:** `LoginRequest`

**이메일·비밀번호 요청 예시**

```json
{
  "email": "tester@example.invalid",
  "password": "example-only-not-a-real-password"
}
```

**응답 스키마:** `AuthResponse`

**응답 예시 — success**

```json
{
  "accessToken": "mock-access-token-not-a-real-credential",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-19T11:00:00+09:00",
  "expiresInSeconds": 3600,
  "refreshSupported": false,
  "user": {
    "id": "10000000-0000-4000-8000-000000000001",
    "email": "tester@example.invalid",
    "hasOnboarded": false
  }
}
```

**주요 오류 코드:** `INVALID_CREDENTIALS`, `VALIDATION_ERROR`, `TOO_MANY_ATTEMPTS`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `SERVICE_UNAVAILABLE`。

### 8.2 `GET /config`

**고정 반경·제한·정기 배달 설정** · operationId: `getConfig` · 성공 `200`

새 경로 채택 제안. 인증·초대 허용 계정이면 온보딩 전 조회 가능. 예시의1000m와 모든 제한/중심좌표는 MOCK 전용. 실제 운영값을 서버가 내려주며 누락 시503, FE가1000m를 추정해 사용하지 않는다.

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `ServiceConfig`

**응답 예시 — mockOnly**

```json
{
  "configVersion": "mock-config-v1",
  "radiusMeters": 1000,
  "demoCenter": {
    "lat": 37.6109,
    "lng": 126.9977
  },
  "deliveryTimeZone": "Asia/Seoul",
  "deliveryLocalTime": "09:00",
  "limits": {
    "memoryContentMaxCodePoints": 3000,
    "preferenceDescriptionMaxCodePoints": 1000,
    "reportDetailsMaxCodePoints": 1000,
    "imageMaxBytes": 5242880,
    "imageMaxWidth": 6000,
    "imageMaxHeight": 6000,
    "imageMaxPixels": 20000000,
    "imageUploadTtlSeconds": 1800,
    "analysisTtlSeconds": 900,
    "dailyDirectMemoryLimit": 20,
    "defaultPageLimit": 20,
    "maxPageLimit": 50,
    "maxMapPageLimit": 200
  },
  "auth": {
    "mode": "EMAIL_PASSWORD",
    "refreshSupported": false,
    "accessTokenTtlSeconds": 3600,
    "failedLoginLimit": 5,
    "loginLockSeconds": 60
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `CONFIGURATION_UNAVAILABLE`。

### 8.3 `GET /atmosphere-axes`

**확정 분위기4축 사전** · operationId: `getAtmosphereAxes` · 성공 `200`

네 축 코드/라벨/표시 순서를 제공한다. ERD의 코드 상수에서 생성하며 DB 동적 사전 추가 없음.

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `AtmosphereAxesResponse`

**응답 예시 — v1**

```json
{
  "version": 1,
  "items": [
    {
      "code": "CROWD_LEVEL",
      "order": 1,
      "options": [
        {
          "value": -1,
          "label": "조용한"
        },
        {
          "value": 1,
          "label": "북적이는"
        }
      ]
    },
    {
      "code": "SPATIAL_FEEL",
      "order": 2,
      "options": [
        {
          "value": -1,
          "label": "아늑한"
        },
        {
          "value": 1,
          "label": "탁 트인"
        }
      ]
    },
    {
      "code": "COMPANY_FIT",
      "order": 3,
      "options": [
        {
          "value": -1,
          "label": "혼자 가기 좋은"
        },
        {
          "value": 1,
          "label": "함께 가기 좋은"
        }
      ]
    },
    {
      "code": "STAY_STYLE",
      "order": 4,
      "options": [
        {
          "value": -1,
          "label": "오래 머물기 좋은"
        },
        {
          "value": 1,
          "label": "잠깐 들르기 좋은"
        }
      ]
    }
  ]
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`。

### 8.4 `GET /place-categories`

**자체 장소 카테고리8종** · operationId: `getPlaceCategories` · 성공 `200`

네이버 업종 코드가 아닌 자체 사전. 반환 definition은 팀 검토가 필요한 분류 가이드 초안.

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `PlaceCategoriesResponse`

**응답 예시 — v1**

```json
{
  "version": 1,
  "items": [
    {
      "code": "CAFE",
      "label": "카페",
      "definition": "음료·카페 이용이 중심인 공간",
      "order": 1
    },
    {
      "code": "RESTAURANT",
      "label": "음식점",
      "definition": "식사 제공·식사 이용이 중심인 공간",
      "order": 2
    },
    {
      "code": "BAR",
      "label": "술집",
      "definition": "술을 마시는 이용이 중심인 공간",
      "order": 3
    },
    {
      "code": "PARK_WALK",
      "label": "공원·산책",
      "definition": "공원·산책로 등 걷거나 쉬는 야외 공간",
      "order": 4
    },
    {
      "code": "CULTURE",
      "label": "문화",
      "definition": "전시·공연·박물관 등 문화 경험을 위한 공간",
      "order": 5
    },
    {
      "code": "STUDY_WORK",
      "label": "공부·작업 공간",
      "definition": "공부·작업 용도가 본문에서 드러나는 공간",
      "order": 6
    },
    {
      "code": "SHOPPING",
      "label": "쇼핑",
      "definition": "상품을 둘러보거나 구매하는 매장·시장 등",
      "order": 7
    },
    {
      "code": "OTHER",
      "label": "기타",
      "definition": "장소 유형은 알 수 있으나 다른 일곱 유형에 해당하지 않는 경우",
      "order": 8
    }
  ]
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`。

### 8.5 `GET /users/me`

**본인 설정·온보딩 상태** · operationId: `getMe` · 성공 `200`

hasOnboarded는 위치와 최초 취향버전의 원자적 완료로 서버가 계산한다. email은 필수 문자열이며 null을 반환하지 않는다. preferenceVersion은 revision의10진 문자열, 내부 버전 UUID는 노출하지 않는다.

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `UserProfile`

**응답 예시 — beforeOnboarding**

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "email": "tester@example.invalid",
  "hasOnboarded": false,
  "mailbox": null,
  "atmospheres": null,
  "preferenceDescription": null,
  "preferenceVersion": null,
  "preferenceEffectiveAt": null
}
```

**응답 예시 — afterOnboarding**

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "email": "tester@example.invalid",
  "hasOnboarded": true,
  "mailbox": {
    "lat": 37.6109,
    "lng": 126.9977,
    "enabledAt": "2026-09-19T10:00:00+09:00"
  },
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": -1,
    "STAY_STYLE": -1
  },
  "preferenceDescription": "혼자 책을 읽으며 오래 머물 수 있는 아늑한 곳을 좋아해요.",
  "preferenceVersion": "1",
  "preferenceEffectiveAt": "2026-09-19T10:00:00+09:00"
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`。

### 8.6 `POST /users/me/onboarding`

**최초 위치·4축 설정** · operationId: `completeOnboarding` · 성공 `200`

사용자 행을 잠근 뒤 최초 위치와 취향 v1을 함께 저장한다. 같은 최초 요청의 재전송은 변경 없이 200, 다른 값으로 재초기화하면 409다. 09시 이후 완료하면 다음 정기 배달부터 대상이다.

**요청 스키마:** `OnboardingRequest`

**요청 예시**

```json
{
  "mailboxLat": 37.6109,
  "mailboxLng": 126.9977,
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": -1,
    "STAY_STYLE": -1
  },
  "preferenceDescription": "혼자 책을 읽으며 오래 머물 수 있는 아늑한 곳을 좋아해요."
}
```

**응답 스키마:** `UserProfile`

**응답 예시 — completed**

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "email": "tester@example.invalid",
  "hasOnboarded": true,
  "mailbox": {
    "lat": 37.6109,
    "lng": 126.9977,
    "enabledAt": "2026-09-19T10:00:00+09:00"
  },
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": -1,
    "STAY_STYLE": -1
  },
  "preferenceDescription": "혼자 책을 읽으며 오래 머물 수 있는 아늑한 곳을 좋아해요.",
  "preferenceVersion": "1",
  "preferenceEffectiveAt": "2026-09-19T10:00:00+09:00"
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `VALIDATION_ERROR`, `INVALID_ATMOSPHERES`, `ONBOARDING_ALREADY_COMPLETED`。

### 8.7 `PATCH /users/me/preferences`

**4축·자연어 취향 변경** · operationId: `updatePreferences` · 성공 `200`

4축·설명 전체와 현재 버전을 보낸다. 최신 revision이 다르면409. 정상 변경은 전체 새 불변 버전 INSERT. 같은 버전·같은 값은 새 버전 없이200. 위치 필드 제출은422 IMMUTABLE_FIELD. 오늘 확정된/09:00 컷오프 설정을 소급 교체하지 않는다.

**요청 스키마:** `PreferencesRequest`

**요청 예시**

```json
{
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": -1,
    "STAY_STYLE": -1
  },
  "preferenceDescription": "친구와 조용히 대화할 수 있는 공간을 좋아해요.",
  "expectedPreferenceVersion": "1"
}
```

**응답 스키마:** `UserProfile`

**응답 예시 — updated**

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "email": "tester@example.invalid",
  "hasOnboarded": true,
  "mailbox": {
    "lat": 37.6109,
    "lng": 126.9977,
    "enabledAt": "2026-09-19T10:00:00+09:00"
  },
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": -1,
    "STAY_STYLE": -1
  },
  "preferenceDescription": "친구와 조용히 대화할 수 있는 공간을 좋아해요.",
  "preferenceVersion": "2",
  "preferenceEffectiveAt": "2026-09-20T10:10:00+09:00"
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `VALIDATION_ERROR`, `INVALID_ATMOSPHERES`, `IMMUTABLE_FIELD`, `PREFERENCE_VERSION_CONFLICT`。

### 8.8 `POST /memories/analyze`

**본문의 분위기·카테고리 AI 제안** · operationId: `analyzeMemory` · 성공 `200`

본문만 분석하며 계정 취향으로 빈 축을 채우지 않는다. 유효한 요청의 AI 분석 실패는200+FAILED+null축으로 수동 입력을 지원한다. 서버 자체 장애503와 구분. analysisToken은 서버 서명 확인값 제안이며 최종 저장에서 위변조·본문·사용자·만료 검사. 수동 저장은 토큰null로 가능하다.

**요청 스키마:** `AnalyzeRequest`

**요청 예시**

```json
{
  "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다."
}
```

**응답 스키마:** `AnalyzeResponse`

**응답 예시 — partial**

```json
{
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": null
  },
  "categories": [
    "CAFE",
    "STUDY_WORK"
  ],
  "categoryStatus": "CLASSIFIED",
  "atmosphereStatus": "PARTIAL",
  "analysisToken": "mock-signed-analysis-receipt-not-a-live-token",
  "expiresAt": "2026-09-19T11:15:00+09:00",
  "warnings": [
    "ATMOSPHERE_NEEDS_INPUT"
  ]
}
```

**응답 예시 — upstreamFailure**

```json
{
  "atmospheres": {
    "CROWD_LEVEL": null,
    "SPATIAL_FEEL": null,
    "COMPANY_FIT": null,
    "STAY_STYLE": null
  },
  "categories": [],
  "categoryStatus": "FAILED",
  "atmosphereStatus": "FAILED",
  "analysisToken": "mock-signed-analysis-receipt-not-a-live-token",
  "expiresAt": "2026-09-19T11:15:00+09:00",
  "warnings": [
    "ANALYSIS_FAILED"
  ]
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `VALIDATION_ERROR`, `RATE_LIMITED`。

### 8.9 `POST /memories`

**LETTER 또는 직접 PRIVATE 생성** · operationId: `createMemory` · 성공 `201`

원문과 분류를 DB에 저장한 뒤 201을 반환한다. LETTER의 안전 검사와 배달은 별도이며 PENDING 상태가 정상이다. 완료된 요청 키를 이미지 소비·분석 token 만료 검사보다 먼저 확인한다. 같은 완료 요청은 기존 자원을 200으로 반환하고, 이후 삭제되었다면 410이며 새 경험을 만들지 않는다. 사본은 like 전용 경로에서 생성한다. placeId 생략은 새 핀, 명시적 재사용은 가시성·좌표 검사를 적용한다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| header | `Idempotency-Key` | 예 | uuid — 직접 생성 재시도에 유지하는 UUID. 사용자+HTTP 메서드+경로에 귀속. 같은 키의 다른 payload는409. 좋아요에는 사용하지 않음. |

**요청 스키마:** `CreateMemoryRequest`

**요청 예시**

```json
{
  "type": "LETTER",
  "lat": 37.6109,
  "lng": 126.9977,
  "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
  "imageId": "50000000-0000-4000-8000-000000000001",
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": -1
  },
  "categoryCodes": [
    "CAFE",
    "STUDY_WORK"
  ],
  "analysisToken": "mock-signed-analysis-receipt-not-a-live-token"
}
```

**요청 예시**

```json
{
  "type": "PRIVATE",
  "lat": 37.6109,
  "lng": 126.9977,
  "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
  "imageId": null,
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": -1
  },
  "categoryCodes": [],
  "analysisToken": null
}
```

**응답 스키마:** `MemoryDetail`

**응답 예시 — savedAwaitingModeration**

```json
{
  "id": "30000000-0000-4000-8000-000000000002",
  "type": "LETTER",
  "originKind": "DIRECT",
  "dataOrigin": "TEAM_TEST",
  "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
  "imageUrl": "/memories/30000000-0000-4000-8000-000000000002/image",
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": -1
  },
  "categories": [
    "CAFE",
    "STUDY_WORK"
  ],
  "categoryStatus": "CLASSIFIED",
  "place": {
    "id": "20000000-0000-4000-8000-000000000001",
    "lat": 37.6109,
    "lng": 126.9977,
    "label": null
  },
  "createdAt": "2026-09-19T11:00:00+09:00",
  "viewerRole": "OWNER",
  "ownerState": {
    "moderationStatus": "PENDING",
    "availableAt": null,
    "likeCount": 0,
    "analysis": {
      "atmosphereStatus": "SUCCEEDED",
      "categoryStatus": "SUCCEEDED"
    }
  },
  "delivery": null
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `VALIDATION_ERROR`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `INVALID_ATMOSPHERES`, `INVALID_CATEGORIES`, `DAILY_WRITE_LIMIT_EXCEEDED`, `IMAGE_NOT_FOUND`, `IMAGE_UPLOAD_EXPIRED`, `IMAGE_ALREADY_ATTACHED`, `ANALYSIS_TOKEN_INVALID`, `ANALYSIS_TOKEN_EXPIRED`, `ANALYSIS_CONTENT_MISMATCH`, `RESOURCE_NOT_FOUND`, `PLACE_COORDINATE_MISMATCH`, `MEMORY_UNAVAILABLE`。

### 8.10 `POST /images`

**JPEG/PNG1장 선행 업로드** · operationId: `uploadImage` · 성공 `201`

file 파트 하나. 서버가 실제 포맷·바이트·픽셀 수를 확인하고 재인코딩/EXIF 제거. 영속 로컬 파일+업로드 메타데이터 준비 후201. imageId는 사용자 귀속 임시 자원, 외부 경로 아님. 만료/미첨부 파일은 정리, ATTACHED 파일은 임시 TTL 정리에서 제외. 별도 공개 업로드 조회 API는 없음.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| header | `Idempotency-Key` | 예 | uuid — 직접 생성 재시도에 유지하는 UUID. 사용자+HTTP 메서드+경로에 귀속. 같은 키의 다른 payload는409. 좋아요에는 사용하지 않음. |

**요청 스키마:** `ImageUploadRequest`

```http
POST /images
Authorization: Bearer <accessToken>
Idempotency-Key: <이번 업로드에 고정한 UUID>
Content-Type: multipart/form-data; boundary=<브라우저 생성>

file: <JPEG/PNG binary 1개>
```

**응답 스키마:** `ImageUploadResponse`

**응답 예시 — uploaded**

```json
{
  "imageId": "50000000-0000-4000-8000-000000000001",
  "expiresAt": "2026-09-19T11:30:00+09:00",
  "mediaType": "image/jpeg",
  "sizeBytes": 245760,
  "width": 1280,
  "height": 960
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `INVALID_REQUEST`, `IMAGE_TOO_LARGE`, `UNSUPPORTED_IMAGE_TYPE`, `INVALID_IMAGE`, `IMAGE_DIMENSIONS_EXCEEDED`, `IMAGE_UPLOAD_EXPIRED`, `IMAGE_STORAGE_UNAVAILABLE`, `RATE_LIMITED`。

### 8.11 `GET /memories/{id}/image`

**권한 확인 후 경험 이미지** · operationId: `getMemoryImage` · 성공 `200`

Bearer 헤더 필수. 존재하지 않음/권한 없음/사진 없음404. 과거 수신 또는 소유가 확인된 삭제·숨김은410. 파일 메타데이터는 있지만 IO 장애면503. 공개 리다이렉트·토큰 query 없음. Cache-Control:private,no-store; 고정 안전 파일명; X-Content-Type-Options:nosniff.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `id` | 예 | uuid — 경험ID |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 본문:** `image/jpeg` 또는 `image/png` binary. 실패일때만 ApiError JSON.

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `RESOURCE_NOT_FOUND`, `MEMORY_UNAVAILABLE`, `IMAGE_FILE_UNAVAILABLE`。

### 8.12 `GET /letters/today`

**오늘 배달 상태 조회** · operationId: `getTodayLetter` · 성공 `200`

조회는 선정 트리거가 아니다. FE의 5개 상태와 보조 사유를 반환한다. 원문 삭제 후에도 DELIVERED를 유지하고 내용 없는 안내 항목을 제공한다. API 자체의 DB 장애는 503이다. 지난 날짜의 EXPIRED_ERROR를 오늘 결과로 반환하지 않는다.

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `TodayResponse`

**응답 예시 — delivered**

```json
{
  "status": "DELIVERED",
  "serviceDate": "2026-09-20",
  "scheduledAt": "2026-09-20T09:00:00+09:00",
  "nextScheduledAt": "2026-09-21T09:00:00+09:00",
  "serverTime": "2026-09-20T09:01:00+09:00",
  "pendingReason": null,
  "errorReason": null,
  "delivery": {
    "deliveryId": "40000000-0000-4000-8000-000000000001",
    "memoryId": "30000000-0000-4000-8000-000000000001",
    "serviceDate": "2026-09-20",
    "deliveredAt": "2026-09-20T09:00:02+09:00",
    "readAt": null,
    "likedAt": null,
    "availability": "AVAILABLE",
    "unavailableReason": null,
    "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
    "imageUrl": "/memories/30000000-0000-4000-8000-000000000001/image",
    "atmospheres": {
      "CROWD_LEVEL": -1,
      "SPATIAL_FEEL": -1,
      "COMPANY_FIT": 1,
      "STAY_STYLE": -1
    },
    "categories": [
      "CAFE",
      "STUDY_WORK"
    ],
    "categoryStatus": "CLASSIFIED",
    "place": {
      "id": "20000000-0000-4000-8000-000000000001",
      "lat": 37.6109,
      "lng": 126.9977,
      "label": null
    },
    "createdAt": "2026-09-19T11:00:00+09:00",
    "dataOrigin": "TEAM_TEST"
  }
}
```

**응답 예시 — newAfterCutoff**

```json
{
  "status": "PENDING",
  "serviceDate": "2026-09-20",
  "scheduledAt": "2026-09-20T09:00:00+09:00",
  "nextScheduledAt": "2026-09-21T09:00:00+09:00",
  "serverTime": "2026-09-20T09:01:00+09:00",
  "pendingReason": "FIRST_DELIVERY_TOMORROW",
  "errorReason": null,
  "delivery": null
}
```

**응답 예시 — deletedAfterDelivery**

```json
{
  "status": "DELIVERED",
  "serviceDate": "2026-09-20",
  "scheduledAt": "2026-09-20T09:00:00+09:00",
  "nextScheduledAt": "2026-09-21T09:00:00+09:00",
  "serverTime": "2026-09-20T09:01:00+09:00",
  "pendingReason": null,
  "errorReason": null,
  "delivery": {
    "deliveryId": "40000000-0000-4000-8000-000000000001",
    "memoryId": "30000000-0000-4000-8000-000000000001",
    "serviceDate": "2026-09-20",
    "deliveredAt": "2026-09-20T09:00:02+09:00",
    "readAt": null,
    "likedAt": null,
    "availability": "UNAVAILABLE",
    "unavailableReason": "DELETED",
    "content": null,
    "imageUrl": null,
    "atmospheres": null,
    "categories": [],
    "categoryStatus": null,
    "place": null,
    "createdAt": null,
    "dataOrigin": null
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`。

### 8.13 `GET /letters`

**누적 수신함·조회 필터** · operationId: `listLetters` · 성공 `200`

실제 수신 이력을 delivered_at DESC, id DESC로 조회한다. 필터 종류 안에서는 OR, 종류 사이는 AND다. 특정 필터를 적용하면 열람 가능한 경험만 포함하고, 필터 없는 전체 보기에서는 삭제 안내 항목도 유지한다. 미선정 후보와 타인의 PRIVATE는 개수도 노출하지 않는다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| query | `atmospheres` | 아니오 | string[] — 반복 파라미터. 예: atmospheres=CROWD_LEVEL:-1&atmospheres=SPATIAL_FEEL:1. 같은 축 양쪽도 허용하되 전체 분위기OR의 넓어진 결과를 FE가 표시. |
| query | `categories` | 아니오 | PlaceCategoryCode[] — 반복 파라미터. categories=CAFE&categories=PARK_WALK. 빈값 대신 파라미터 생략. |
| query | `cursor` | 아니오 | string — opaque cursor. 첫 페이지에서는 생략. 필터가 바뀌면 폐기한다. |
| query | `limit` | 아니오 | integer — 생략 시 /config.limits.defaultPageLimit. 일반 목록 상한 maxPageLimit, 지도 maxMapPageLimit. |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `LetterPage`

**응답 예시 — page**

```json
{
  "items": [
    {
      "deliveryId": "40000000-0000-4000-8000-000000000001",
      "memoryId": "30000000-0000-4000-8000-000000000001",
      "serviceDate": "2026-09-20",
      "deliveredAt": "2026-09-20T09:00:02+09:00",
      "readAt": null,
      "likedAt": null,
      "availability": "AVAILABLE",
      "unavailableReason": null,
      "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
      "imageUrl": "/memories/30000000-0000-4000-8000-000000000001/image",
      "atmospheres": {
        "CROWD_LEVEL": -1,
        "SPATIAL_FEEL": -1,
        "COMPANY_FIT": 1,
        "STAY_STYLE": -1
      },
      "categories": [
        "CAFE",
        "STUDY_WORK"
      ],
      "categoryStatus": "CLASSIFIED",
      "place": {
        "id": "20000000-0000-4000-8000-000000000001",
        "lat": 37.6109,
        "lng": 126.9977,
        "label": null
      },
      "createdAt": "2026-09-19T11:00:00+09:00",
      "dataOrigin": "TEAM_TEST"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_CURSOR`, `CURSOR_CONTEXT_MISMATCH`, `INVALID_REQUEST`, `INVALID_ATMOSPHERES`, `INVALID_CATEGORIES`。

### 8.14 `PATCH /letters/{deliveryId}/read`

**최초 읽음 기록** · operationId: `markLetterRead` · 성공 `200`

요청 본문은 없다. 서버 시각으로 최초 read_at을 기록하며 반복 요청은 같은 readAt으로 200이다. 타인의 배달은 404, 열람할 수 없는 원문은 410이다. GET 상세 조회로 읽음 상태를 바꾸지 않는다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `deliveryId` | 예 | uuid — 실제 수신ID |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `ReadResponse`

**응답 예시 — read**

```json
{
  "deliveryId": "40000000-0000-4000-8000-000000000001",
  "readAt": "2026-09-20T10:05:00+09:00"
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `RESOURCE_NOT_FOUND`, `MEMORY_UNAVAILABLE`。

### 8.15 `POST /letters/{deliveryId}/like`

**일회성 좋아요·독립 PRIVATE 복사** · operationId: `likeAndCopyLetter` · 성공 `200`

요청 본문은 없다. 최초와 중복 모두 200이다. 실제 수신자를 확인한 뒤 이미 liked_at이 있으면 ALREADY_COPIED, 아직 미실행이면 원문 상태·잠금·독립 복사 후 성공을 기록한다. copiedMemoryId는 최초 응답에만 제공하며 원문·배달·범용 응답 캐시·로그에 지속 저장하지 않는다. 응답 유실 후에는 ID=null로 보관함 목록으로 이동한다. 이미 좋아요한 원문이 삭제돼도 완료 결과는 재사용하며, 아직 실행하지 않은 삭제 원문에는 410을 반환한다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `deliveryId` | 예 | uuid — 실제 수신ID |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `LikeResponse`

**응답 예시 — firstCopy**

```json
{
  "deliveryId": "40000000-0000-4000-8000-000000000001",
  "status": "COPIED",
  "likedAt": "2026-09-20T10:06:00+09:00",
  "copiedMemoryId": "30000000-0000-4000-8000-000000000003"
}
```

**응답 예시 — alreadyCopied**

```json
{
  "deliveryId": "40000000-0000-4000-8000-000000000001",
  "status": "ALREADY_COPIED",
  "likedAt": "2026-09-20T10:06:00+09:00",
  "copiedMemoryId": null
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `RESOURCE_NOT_FOUND`, `MEMORY_UNAVAILABLE`, `COPY_FAILED`。

### 8.16 `GET /bookmarks`

**내 PRIVATE 보관함** · operationId: `listBookmarks` · 성공 `200`

본인 소유이며 ACTIVE인 PRIVATE만 created_at DESC, id DESC로 조회한다. DIRECT와 LETTER_COPY를 구분한다. 원문을 JOIN하지 않으며 일반 삭제·숨김 기록은 제외한다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| query | `cursor` | 아니오 | string — opaque cursor. 첫 페이지에서는 생략. 필터가 바뀌면 폐기한다. |
| query | `limit` | 아니오 | integer — 생략 시 /config.limits.defaultPageLimit. 일반 목록 상한 maxPageLimit, 지도 maxMapPageLimit. |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `MemoryPage`

**응답 예시 — page**

```json
{
  "items": [
    {
      "id": "30000000-0000-4000-8000-000000000003",
      "type": "PRIVATE",
      "originKind": "LETTER_COPY",
      "dataOrigin": "TEAM_TEST",
      "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
      "imageUrl": "/memories/30000000-0000-4000-8000-000000000003/image",
      "atmospheres": {
        "CROWD_LEVEL": -1,
        "SPATIAL_FEEL": -1,
        "COMPANY_FIT": 1,
        "STAY_STYLE": -1
      },
      "categories": [
        "CAFE",
        "STUDY_WORK"
      ],
      "categoryStatus": "CLASSIFIED",
      "place": {
        "id": "20000000-0000-4000-8000-000000000001",
        "lat": 37.6109,
        "lng": 126.9977,
        "label": null
      },
      "createdAt": "2026-09-20T10:06:00+09:00",
      "viewerRole": "OWNER",
      "ownerState": {
        "moderationStatus": "NOT_REQUIRED",
        "availableAt": null,
        "likeCount": 0,
        "analysis": {
          "atmosphereStatus": "SUCCEEDED",
          "categoryStatus": "SUCCEEDED"
        }
      },
      "delivery": null
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_CURSOR`, `CURSOR_CONTEXT_MISMATCH`, `INVALID_REQUEST`。

### 8.17 `GET /memories/{id}`

**경험 상세·작성자 상태** · operationId: `getMemory` · 성공 `200`

소유자 또는 승인된 LETTER의 실제 수신자만 조회할 수 있다. 소유자는 검사 대기·반려 등 본인 상태를 볼 수 있다. 삭제·숨김은 기존 권한 확인 후 410, 그 외 없는/비인가 대상은 404다. ownerState는 소유자에게만, delivery는 수신자에게만 제공한다. 작성자 ID·이메일·닉네임·우편함 위치는 없다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `id` | 예 | uuid — 경험ID |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `MemoryDetail`

**응답 예시 — owner**

```json
{
  "id": "30000000-0000-4000-8000-000000000002",
  "type": "LETTER",
  "originKind": "DIRECT",
  "dataOrigin": "TEAM_TEST",
  "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
  "imageUrl": "/memories/30000000-0000-4000-8000-000000000002/image",
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": -1
  },
  "categories": [
    "CAFE",
    "STUDY_WORK"
  ],
  "categoryStatus": "CLASSIFIED",
  "place": {
    "id": "20000000-0000-4000-8000-000000000001",
    "lat": 37.6109,
    "lng": 126.9977,
    "label": null
  },
  "createdAt": "2026-09-19T11:00:00+09:00",
  "viewerRole": "OWNER",
  "ownerState": {
    "moderationStatus": "PENDING",
    "availableAt": null,
    "likeCount": 0,
    "analysis": {
      "atmosphereStatus": "SUCCEEDED",
      "categoryStatus": "SUCCEEDED"
    }
  },
  "delivery": null
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `RESOURCE_NOT_FOUND`, `MEMORY_UNAVAILABLE`。

### 8.18 `DELETE /memories/{id}`

**내 원문·PRIVATE 일반 삭제** · operationId: `deleteMemory` · 성공 `204`

본인 소유 기록만 soft delete하고 본문 없이 204를 반환한다. 이미 삭제한 본인 기록도 204, 타인 기록은 404다. 원문의 수신·좋아요·일일 성공 이력과 독립 사본은 유지한다. 당일 대체 배달은 없으며 파일 정리는 해당 경험 경로만 대상으로 한다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `id` | 예 | uuid — 본인소유경험ID |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 본문:** 없음.

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `RESOURCE_NOT_FOUND`。

### 8.19 `GET /users/me/memories`

**내가 보낸 LETTER·누적 반응** · operationId: `listMyLetters` · 성공 `200`

FE 목록에서 빠졌지만 기획서에 있는 내 기록 API다. 본인 LETTER만 조회하며 삭제·숨김은 제외한다. 검사 대기·반려·오류는 ownerState로 표현한다. likeCount는 성공한 liked_at의 누적 개수이며 반응자 목록은 제공하지 않는다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| query | `type` | 예 | LETTER — 이번계약은LETTER만. PRIVATE는/bookmarks |
| query | `cursor` | 아니오 | string — opaque cursor. 첫 페이지에서는 생략. 필터가 바뀌면 폐기한다. |
| query | `limit` | 아니오 | integer — 생략 시 /config.limits.defaultPageLimit. 일반 목록 상한 maxPageLimit, 지도 maxMapPageLimit. |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `MemoryPage`

**응답 예시 — page**

```json
{
  "items": [
    {
      "id": "30000000-0000-4000-8000-000000000002",
      "type": "LETTER",
      "originKind": "DIRECT",
      "dataOrigin": "TEAM_TEST",
      "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
      "imageUrl": "/memories/30000000-0000-4000-8000-000000000002/image",
      "atmospheres": {
        "CROWD_LEVEL": -1,
        "SPATIAL_FEEL": -1,
        "COMPANY_FIT": 1,
        "STAY_STYLE": -1
      },
      "categories": [
        "CAFE",
        "STUDY_WORK"
      ],
      "categoryStatus": "CLASSIFIED",
      "place": {
        "id": "20000000-0000-4000-8000-000000000001",
        "lat": 37.6109,
        "lng": 126.9977,
        "label": null
      },
      "createdAt": "2026-09-19T11:00:00+09:00",
      "viewerRole": "OWNER",
      "ownerState": {
        "moderationStatus": "PENDING",
        "availableAt": null,
        "likeCount": 0,
        "analysis": {
          "atmosphereStatus": "SUCCEEDED",
          "categoryStatus": "SUCCEEDED"
        }
      },
      "delivery": null
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_CURSOR`, `CURSOR_CONTEXT_MISMATCH`, `INVALID_REQUEST`。

### 8.20 `GET /places`

**현재 사용자 권한 내 지도 핀** · operationId: `listVisiblePlaces` · 성공 `200`

bbox 안에서 현재 사용자에게 가시 경험이 있는 Place만 반환한다. 본인 ACTIVE 경험과 승인된 실제 수신 LETTER를 중복 제거해 집계한다. 자동 인접 병합·업체 검색은 없고, placeId를 명시한 경우에만 기존 가시 핀을 재사용한다. FE의 시각 클러스터는 식별자와 권한을 바꾸지 않는다. label이 없으면 null이다. id ASC 커서에 bbox를 귀속한다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| query | `bbox` | 예 | string — westLng,southLat,eastLng,northLat. 위경도범위·west<east/south<north 서버검증. 반자오선횡단은MVP지원안함. |
| query | `cursor` | 아니오 | string — opaque cursor. 첫 페이지에서는 생략. 필터가 바뀌면 폐기한다. |
| query | `limit` | 아니오 | integer — 생략 시 /config.limits.defaultPageLimit. 일반 목록 상한 maxPageLimit, 지도 maxMapPageLimit. |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `PlacePage`

**응답 예시 — page**

```json
{
  "items": [
    {
      "id": "20000000-0000-4000-8000-000000000001",
      "lat": 37.6109,
      "lng": 126.9977,
      "label": null,
      "memoryCount": 2
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_CURSOR`, `CURSOR_CONTEXT_MISMATCH`, `INVALID_REQUEST`, `INVALID_BBOX`。

### 8.21 `GET /places/{id}/memories`

**한 내부 핀의 권한 있는 경험** · operationId: `listVisiblePlaceMemories` · 성공 `200`

해당 핀에 현재 가시 경험이 하나도 없으면 404다. 권한 있는 기록을 created_at DESC, id DESC로 조회하며 카테고리 JOIN으로 중복시키지 않는다. 인접 좌표의 다른 Place는 자동으로 합치지 않는다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| path | `id` | 예 | uuid — 내부Place ID |
| query | `cursor` | 아니오 | string — opaque cursor. 첫 페이지에서는 생략. 필터가 바뀌면 폐기한다. |
| query | `limit` | 아니오 | integer — 생략 시 /config.limits.defaultPageLimit. 일반 목록 상한 maxPageLimit, 지도 maxMapPageLimit. |

**요청 본문:** 없음. GET/query 또는 경로 식별자만 사용.

**응답 스키마:** `MemoryPage`

**응답 예시 — page**

```json
{
  "items": [
    {
      "id": "30000000-0000-4000-8000-000000000001",
      "type": "LETTER",
      "originKind": "DIRECT",
      "dataOrigin": "TEAM_TEST",
      "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
      "imageUrl": "/memories/30000000-0000-4000-8000-000000000001/image",
      "atmospheres": {
        "CROWD_LEVEL": -1,
        "SPATIAL_FEEL": -1,
        "COMPANY_FIT": 1,
        "STAY_STYLE": -1
      },
      "categories": [
        "CAFE",
        "STUDY_WORK"
      ],
      "categoryStatus": "CLASSIFIED",
      "place": {
        "id": "20000000-0000-4000-8000-000000000001",
        "lat": 37.6109,
        "lng": 126.9977,
        "label": null
      },
      "createdAt": "2026-09-19T11:00:00+09:00",
      "viewerRole": "RECIPIENT",
      "ownerState": null,
      "delivery": {
        "deliveryId": "40000000-0000-4000-8000-000000000001",
        "serviceDate": "2026-09-20",
        "deliveredAt": "2026-09-20T09:00:02+09:00",
        "readAt": null,
        "likedAt": null
      }
    },
    {
      "id": "30000000-0000-4000-8000-000000000003",
      "type": "PRIVATE",
      "originKind": "LETTER_COPY",
      "dataOrigin": "TEAM_TEST",
      "content": "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.",
      "imageUrl": "/memories/30000000-0000-4000-8000-000000000003/image",
      "atmospheres": {
        "CROWD_LEVEL": -1,
        "SPATIAL_FEEL": -1,
        "COMPANY_FIT": 1,
        "STAY_STYLE": -1
      },
      "categories": [
        "CAFE",
        "STUDY_WORK"
      ],
      "categoryStatus": "CLASSIFIED",
      "place": {
        "id": "20000000-0000-4000-8000-000000000001",
        "lat": 37.6109,
        "lng": 126.9977,
        "label": null
      },
      "createdAt": "2026-09-20T10:06:00+09:00",
      "viewerRole": "OWNER",
      "ownerState": {
        "moderationStatus": "NOT_REQUIRED",
        "availableAt": null,
        "likeCount": 0,
        "analysis": {
          "atmosphereStatus": "SUCCEEDED",
          "categoryStatus": "SUCCEEDED"
        }
      },
      "delivery": null
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_CURSOR`, `CURSOR_CONTEXT_MISMATCH`, `INVALID_REQUEST`, `RESOURCE_NOT_FOUND`。

### 8.22 `POST /reports`

**열람 가능한 경험 신고 접수** · operationId: `createReport` · 성공 `201`

사유 코드는 운영 초안이다. reason은 기존 text 열에 저장하고 details는 별도 nullable 열을 제안한다. 같은 사용자·원문의 새로운 신고는 허용하되 동일 요청 키의 재시도는 기존 접수증을 반환한다. OPEN은 접수 상태이지 숨김 완료가 아니다. 운영자 도구는 별도 계약이다. 완료된 요청을 재확인할 때 원문이 이후 숨겨졌어도 본인 접수증만 반환할 수 있다.

| 위치 | 이름 | 필수 | 형식·의미 |
|---|---|---|---|
| header | `Idempotency-Key` | 예 | uuid — 직접 생성 재시도에 유지하는 UUID. 사용자+HTTP 메서드+경로에 귀속. 같은 키의 다른 payload는409. 좋아요에는 사용하지 않음. |

**요청 스키마:** `ReportRequest`

**요청 예시**

```json
{
  "memoryId": "30000000-0000-4000-8000-000000000001",
  "reason": "PERSONAL_INFORMATION",
  "details": "본문에 타인의 연락처가 포함되어 있습니다."
}
```

**응답 스키마:** `ReportResponse`

**응답 예시 — created**

```json
{
  "reportId": "60000000-0000-4000-8000-000000000001",
  "memoryId": "30000000-0000-4000-8000-000000000001",
  "reason": "PERSONAL_INFORMATION",
  "status": "OPEN",
  "createdAt": "2026-09-20T10:07:00+09:00"
}
```

**주요 오류 코드:** `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVITATION_REQUIRED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_CLOSED`, `SERVICE_UNAVAILABLE`, `ONBOARDING_REQUIRED`, `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_REQUEST`, `VALIDATION_ERROR`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `RESOURCE_NOT_FOUND`, `MEMORY_UNAVAILABLE`, `RATE_LIMITED`。

---

## 9. 공통 DTO 필드 사전

필드의 형식은 이 장과`openapi.yaml`이기준이다. 타입상nullable과 optional을 구분한다. 요청의 선택적 분석 확인값·기존 핀·신고 설명을 제외하면 필수 키를 생략하지 않는다. `frontend/contracts.ts`는 이 OpenAPI 스키마에서 생성한 초안 타입이며 JSON 실행 검증은 별도로 필요하다.

### `Atmospheres`

최종 설정/경험의 필수 4축. JSON 중복 키와 소수 리터럴 검사는 HTTP 파서에서 별도 수행한다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `CROWD_LEVEL` | -1 / 1 | 예 |  |
| `SPATIAL_FEEL` | -1 / 1 | 예 |  |
| `COMPANY_FIT` | -1 / 1 | 예 |  |
| `STAY_STYLE` | -1 / 1 | 예 |  |

### `AnalyzedAtmospheres`

AI 분석 중에만 null 허용. 최종 저장에는 null 불가.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `CROWD_LEVEL` | -1 / 1 / null | 예 |  |
| `SPATIAL_FEEL` | -1 / 1 / null | 예 |  |
| `COMPANY_FIT` | -1 / 1 / null | 예 |  |
| `STAY_STYLE` | -1 / 1 / null | 예 |  |

### `Coordinates`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `lat` | number | 예 | 최소 -90; 최대 90 |
| `lng` | number | 예 | 최소 -180; 최대 180 |

### `PlaceSnapshot`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `id` | uuid | 예 |  |
| `lat` | number | 예 | 최소 -90; 최대 90 |
| `lng` | number | 예 | 최소 -180; 최대 180 |
| `label` | string / null | 예 |  |

### `PageInfo`

hasMore=false iff nextCursor=null. 커서는 사용자/경로/필터/정렬 범위에 귀속된다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `nextCursor` | string / null | 예 |  |
| `hasMore` | boolean | 예 |  |

### `FieldError`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `field` | string | 예 |  |
| `reason` | REQUIRED / INVALID_VALUE / DUPLICATE / OUT_OF_RANGE / UNKNOWN_FIELD / TOO_LONG / IMMUTABLE | 예 |  |

### `ApiError`

code로 분기. retryAfterSeconds는 없으면 null이며 429 응답의 Retry-After 헤더와 동일하다. message는 사용자 표시용으로 SQL/경로/작성자 정보 금지.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `code` | ErrorCode | 예 |  |
| `message` | string | 예 |  |
| `retryAfterSeconds` | integer / null | 예 |  |
| `fieldErrors` | FieldError[] | 예 |  |
| `requestId` | string | 예 |  |

### `LoginUser`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `id` | uuid | 예 |  |
| `email` | email | 예 | 이메일·비밀번호 계정의 필수 로그인 이메일 |
| `hasOnboarded` | boolean | 예 |  |

### `AuthResponse`

무갱신 토큰은 승인 전 MVP 제안. 실 토큰 TTL은 서버 설정이다. 토큰은 예시에 실제 값으로 포함하지 않는다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `accessToken` | string | 예 |  |
| `tokenType` | Bearer | 예 |  |
| `expiresAt` | date-time | 예 |  |
| `expiresInSeconds` | integer | 예 | 최소 1 |
| `refreshSupported` | false | 예 |  |
| `user` | LoginUser | 예 |  |

### `LoginRequest`

이메일·비밀번호 전용 단일 요청. 두 필드는 필수이며 그 외 필드는 허용하지 않는다. 서버가 저장된 해시로 검증한다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `email` | email | 예 | 필수 로그인 이메일 |
| `password` | string | 예 | writeOnly, 빈 문자열 불가, trim·정규화 금지 |

### `ServiceLimits`

실제 값은 팀 합의/서버 설정. 첨부 숫자는 MOCK 예시이며 확정 제품 제한값이 아니다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `memoryContentMaxCodePoints` | integer | 예 | 최소 1 |
| `preferenceDescriptionMaxCodePoints` | integer | 예 | 최소 1 |
| `reportDetailsMaxCodePoints` | integer | 예 | 최소 1 |
| `imageMaxBytes` | integer | 예 | 최소 1 |
| `imageMaxWidth` | integer | 예 | 최소 1 |
| `imageMaxHeight` | integer | 예 | 최소 1 |
| `imageMaxPixels` | integer | 예 | 최소 1 |
| `imageUploadTtlSeconds` | integer | 예 | 최소 1 |
| `analysisTtlSeconds` | integer | 예 | 최소 1 |
| `dailyDirectMemoryLimit` | integer | 예 | 최소 1 |
| `defaultPageLimit` | integer | 예 | 최소 1 |
| `maxPageLimit` | integer | 예 | 최소 1 |
| `maxMapPageLimit` | integer | 예 | 최소 1 |

### `ServiceConfig`

반경·한도는 서버 설정이다. auth.mode는 EMAIL_PASSWORD로 확정하며 TTL 등 mock 숫자는 실제 운영값이 아니다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `configVersion` | string | 예 |  |
| `radiusMeters` | integer | 예 | 최소 1 |
| `demoCenter` | Coordinates | 예 |  |
| `deliveryTimeZone` | Asia/Seoul | 예 |  |
| `deliveryLocalTime` | 09:00 | 예 |  |
| `limits` | ServiceLimits | 예 |  |
| `auth` | object | 예 |  |

### `AxisOption`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `value` | -1 / 1 | 예 |  |
| `label` | string | 예 |  |

### `AtmosphereAxis`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `code` | AxisCode | 예 |  |
| `order` | integer | 예 | 최소 1; 최대 4 |
| `options` | AxisOption[] | 예 | 최소개수 2; 최대개수 2 |

### `AtmosphereAxesResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `version` | 1 | 예 |  |
| `items` | AtmosphereAxis[] | 예 | 최소개수 4; 최대개수 4 |

### `PlaceCategory`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `code` | PlaceCategoryCode | 예 |  |
| `label` | string | 예 |  |
| `definition` | string | 예 |  |
| `order` | integer | 예 | 최소 1; 최대 8 |

### `PlaceCategoriesResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `version` | 1 | 예 |  |
| `items` | PlaceCategory[] | 예 | 최소개수 8; 최대개수 8 |

### `Mailbox`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `lat` | number | 예 | 최소 -90; 최대 90 |
| `lng` | number | 예 | 최소 -180; 최대 180 |
| `enabledAt` | date-time | 예 |  |

### `UserProfile`

온보딩 전 mailbox/atmospheres/preferenceVersion/preferenceEffectiveAt는 null. revision bigint는 정밀도 보존을 위해 10진 문자열로 노출한다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `id` | uuid | 예 |  |
| `email` | email | 예 | 이메일·비밀번호 계정의 필수 로그인 이메일 |
| `hasOnboarded` | boolean | 예 |  |
| `mailbox` | Mailbox / null | 예 |  |
| `atmospheres` | Atmospheres / null | 예 |  |
| `preferenceDescription` | string / null | 예 |  |
| `preferenceVersion` | string / null | 예 |  |
| `preferenceEffectiveAt` | date-time / null | 예 |  |

### `OnboardingRequest`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `mailboxLat` | number | 예 | 최소 -90; 최대 90 |
| `mailboxLng` | number | 예 | 최소 -180; 최대 180 |
| `atmospheres` | Atmospheres | 예 |  |
| `preferenceDescription` | string / null | 예 |  |

### `PreferencesRequest`

설정 하위자원의 전체 값을 제출하는 원자적 변경. expectedPreferenceVersion은 동시 수정 방지 제안. 위치/버전 적용 시각은 요청 불가.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `atmospheres` | Atmospheres | 예 |  |
| `preferenceDescription` | string / null | 예 |  |
| `expectedPreferenceVersion` | string | 예 |  |

### `AnalyzeRequest`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `content` | string | 예 |  |

### `AnalyzeResponse`

analysisToken은 서버 서명 확인값 제안. 본문 해시·사용자·AI 제안/실행상태·모델 버전·만료를 검증한다. 실패한 AI 호출도 수동 입력을 위한 200+FAILED로 표현 가능. 최종 축 null은 불가.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `atmospheres` | AnalyzedAtmospheres | 예 |  |
| `categories` | CategoryCodes | 예 |  |
| `categoryStatus` | CLASSIFIED / UNCLASSIFIED / FAILED | 예 |  |
| `atmosphereStatus` | SUCCEEDED / PARTIAL / FAILED | 예 |  |
| `analysisToken` | string | 예 |  |
| `expiresAt` | date-time | 예 |  |
| `warnings` | ATMOSPHERE_NEEDS_INPUT / CATEGORY_UNCLASSIFIED / ANALYSIS_FAILED[] | 예 |  |

### `CreateMemoryRequest`

analysisToken/placeId는 추가 제안. 토큰 없음은 명시적 수동 분류로 저장. 원본 ID/소유자/상태/likedAt/dataOrigin는 서버가 결정하므로 요청 불가.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `type` | LETTER / PRIVATE | 예 |  |
| `lat` | number | 예 | 최소 -90; 최대 90 |
| `lng` | number | 예 | 최소 -180; 최대 180 |
| `content` | string | 예 |  |
| `imageId` | uuid / null | 예 |  |
| `atmospheres` | Atmospheres | 예 |  |
| `categoryCodes` | CategoryCodes | 예 |  |
| `analysisToken` | string / null | 아니오 |  |
| `placeId` | uuid / null | 아니오 |  |

### `ImageUploadRequest`

multipart/form-data의 file 단일 파트. 실제 바이트가 JPEG/PNG인지 서버가 확인한다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `file` | binary | 예 |  |

### `ImageUploadResponse`

원본 파일명/저장경로/공개 URL 없음. 인증된 업로더만 만료 전 한 번 첨부 가능.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `imageId` | uuid | 예 |  |
| `expiresAt` | date-time | 예 |  |
| `mediaType` | image/jpeg / image/png | 예 |  |
| `sizeBytes` | integer | 예 | 최소 1 |
| `width` | integer | 예 | 최소 1 |
| `height` | integer | 예 | 최소 1 |

### `DeliveryContext`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `serviceDate` | date | 예 |  |
| `deliveredAt` | date-time | 예 |  |
| `readAt` | date-time / null | 예 |  |
| `likedAt` | date-time / null | 예 |  |

### `OwnerState`

본인 작성/소유 경험만 포함. likeCount는 원문 수신 이력의 누적 liked_at 개수이며 PRIVATE에서는 0. 모델·제공자 raw 응답은 노출하지 않는다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `moderationStatus` | PENDING / APPROVED / REVIEW_REQUIRED / REJECTED / ERROR / NOT_REQUIRED | 예 |  |
| `availableAt` | date-time / null | 예 |  |
| `likeCount` | integer | 예 | 최소 0 |
| `analysis` | object | 예 |  |

### `MemoryDetail`

OWNER면 ownerState 존재·delivery=null. RECIPIENT면 ownerState=null·delivery 존재. 사용자 ID/작성자 닉네임은 없다. 최종 categoryStatus는 현재 categories.length 기준, AI 실행 결과와 별개이다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `id` | uuid | 예 |  |
| `type` | LETTER / PRIVATE | 예 |  |
| `originKind` | DIRECT / LETTER_COPY | 예 |  |
| `dataOrigin` | PARTICIPANT / TEAM_TEST / SYNTHETIC | 예 |  |
| `content` | string | 예 |  |
| `imageUrl` | uri-reference / null | 예 |  |
| `atmospheres` | Atmospheres | 예 |  |
| `categories` | CategoryCodes | 예 |  |
| `categoryStatus` | CLASSIFIED / UNCLASSIFIED | 예 |  |
| `place` | PlaceSnapshot | 예 |  |
| `createdAt` | date-time | 예 |  |
| `viewerRole` | OWNER / RECIPIENT | 예 |  |
| `ownerState` | OwnerState / null | 예 |  |
| `delivery` | DeliveryContext / null | 예 |  |

### `AvailableLetter`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `memoryId` | uuid | 예 |  |
| `serviceDate` | date | 예 |  |
| `deliveredAt` | date-time | 예 |  |
| `readAt` | date-time / null | 예 |  |
| `likedAt` | date-time / null | 예 |  |
| `availability` | AVAILABLE | 예 |  |
| `unavailableReason` | null | 예 |  |
| `content` | string | 예 |  |
| `imageUrl` | uri-reference / null | 예 |  |
| `atmospheres` | Atmospheres | 예 |  |
| `categories` | CategoryCodes | 예 |  |
| `categoryStatus` | CLASSIFIED / UNCLASSIFIED | 예 |  |
| `place` | PlaceSnapshot | 예 |  |
| `createdAt` | date-time | 예 |  |
| `dataOrigin` | PARTICIPANT / TEAM_TEST / SYNTHETIC | 예 |  |

### `UnavailableLetter`

과거 수신 권한을 확인한 사용자에게만 tombstone. 본문/좌표/태그/사진은 반환하지 않는다. 일일 성공 슬롯을 취소하지 않는다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `memoryId` | uuid | 예 |  |
| `serviceDate` | date | 예 |  |
| `deliveredAt` | date-time | 예 |  |
| `readAt` | date-time / null | 예 |  |
| `likedAt` | date-time / null | 예 |  |
| `availability` | UNAVAILABLE | 예 |  |
| `unavailableReason` | DELETED / HIDDEN | 예 |  |
| `content` | null | 예 |  |
| `imageUrl` | null | 예 |  |
| `atmospheres` | null | 예 |  |
| `categories` | PlaceCategoryCode[] | 예 | 최대개수 0 |
| `categoryStatus` | null | 예 |  |
| `place` | null | 예 |  |
| `createdAt` | null | 예 |  |
| `dataOrigin` | null | 예 |  |

### `LetterSummary`



선택형: AvailableLetter, UnavailableLetter.

### `LetterPage`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `items` | LetterSummary[] | 예 |  |
| `pageInfo` | PageInfo | 예 |  |

### `MemoryPage`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `items` | MemoryDetail[] | 예 |  |
| `pageInfo` | PageInfo | 예 |  |

### `TodayResponse`

DELIVERED이면 delivery 존재(삭제 tombstone 포함). 그 외 delivery=null. 과거 EXPIRED_ERROR를 오늘 오류로 복사하지 않는다. 조회 자체 DB 장애는 HTTP503이며 NO_CANDIDATE로 변환하지 않는다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `status` | PENDING / NO_CANDIDATE / DELIVERED / RETRYING / ERROR | 예 |  |
| `serviceDate` | date | 예 |  |
| `scheduledAt` | date-time | 예 |  |
| `nextScheduledAt` | date-time | 예 |  |
| `serverTime` | date-time | 예 |  |
| `pendingReason` | BEFORE_SCHEDULE / FIRST_DELIVERY_TOMORROW / SCHEDULE_DELAYED / PROCESSING / null | 예 |  |
| `errorReason` | RETRYABLE_FAILURE / SELECTION_STATE_INCONSISTENT / PROCESSING_FAILURE / null | 예 |  |
| `delivery` | LetterSummary / null | 예 |  |

### `ReadResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `readAt` | date-time | 예 |  |

### `CopiedResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `status` | COPIED | 예 |  |
| `likedAt` | date-time | 예 |  |
| `copiedMemoryId` | uuid | 예 |  |

### `AlreadyCopiedResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `deliveryId` | uuid | 예 |  |
| `status` | ALREADY_COPIED | 예 |  |
| `likedAt` | date-time | 예 |  |
| `copiedMemoryId` | null | 예 |  |

### `LikeResponse`

둘 다 200. copiedMemoryId는 새 사본 생성 최초 응답에서만 임시 반환. 원문/배달/응답 캐시/로그에 원문-사본 연결을 저장하지 않는다. 재요청은 id=null로 보관함 목록 이동.

선택형: CopiedResponse, AlreadyCopiedResponse.

### `PlacePin`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `id` | uuid | 예 |  |
| `lat` | number | 예 | 최소 -90; 최대 90 |
| `lng` | number | 예 | 최소 -180; 최대 180 |
| `label` | string / null | 예 |  |
| `memoryCount` | integer | 예 | 최소 1 |

### `PlacePage`

현재 사용자에게 보이는 경험이 1개 이상인 내부 핀만 반환. 전체 공간/미수신 글의 개수를 반환하지 않는다.

| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `items` | PlacePin[] | 예 |  |
| `pageInfo` | PageInfo | 예 |  |

### `ReportRequest`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `memoryId` | uuid | 예 |  |
| `reason` | ReportReason | 예 |  |
| `details` | string / null | 아니오 |  |

### `ReportResponse`



| 필드 | 타입 | 키 필수 | 비고 |
|---|---|---|---|
| `reportId` | uuid | 예 |  |
| `memoryId` | uuid | 예 |  |
| `reason` | ReportReason | 예 |  |
| `status` | OPEN | 예 |  |
| `createdAt` | date-time | 예 |  |

## 10. 오류 코드 전체 목록

문구는 초안의 대표 사용자 메시지다. FE는 문구가 아닌 고정 code로 분기한다. 검증 세부 필드는 fieldErrors로 추가하고 원문 문자열을 오류 로그에 그대로 붙이지 않는다. 응답 전체 형식은 ApiError를 사용한다.

| code | HTTP | 대표 message | 상태 |
|---|---:|---|---|
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다 | 계약 초안 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 사용 중인 이메일입니다 | 예약: 이번 경로에서는 발생하지 않음 |
| `TOO_MANY_ATTEMPTS` | 429 | 로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요 | 계약 초안 |
| `AUTH_REQUIRED` | 401 | 로그인이 필요합니다 | 계약 초안 |
| `TOKEN_EXPIRED` | 401 | 로그인이 만료되었습니다. 다시 로그인해 주세요 | 계약 초안 |
| `INVALID_TOKEN` | 401 | 인증 정보를 확인할 수 없습니다 | 계약 초안 |
| `INVITATION_REQUIRED` | 403 | 초대된 계정만 사용할 수 있습니다 | 계약 초안 |
| `ACCOUNT_SUSPENDED` | 403 | 이용이 제한된 계정입니다 | 계약 초안 |
| `ACCOUNT_CLOSED` | 403 | 종료된 계정입니다 | 계약 초안 |
| `ONBOARDING_REQUIRED` | 403 | 초기 설정을 완료해 주세요 | 계약 초안 |
| `INVALID_JSON` | 400 | JSON 형식이 올바르지 않습니다 | 계약 초안 |
| `DUPLICATE_JSON_KEY` | 400 | 중복된 JSON 키가 있습니다 | 계약 초안 |
| `INVALID_REQUEST` | 400 | 요청 형식이 올바르지 않습니다 | 계약 초안 |
| `VALIDATION_ERROR` | 422 | 입력값을 확인해 주세요 | 계약 초안 |
| `INVALID_ATMOSPHERES` | 422 | 분위기 4축을 각각 -1 또는 1로 입력해 주세요 | 계약 초안 |
| `INVALID_CATEGORIES` | 422 | 정의된 카테고리를 중복 없이 최대 3개 선택해 주세요 | 계약 초안 |
| `IMMUTABLE_FIELD` | 422 | 변경할 수 없는 항목이 포함되어 있습니다 | 계약 초안 |
| `ONBOARDING_ALREADY_COMPLETED` | 409 | 초기 설정은 이미 완료되었습니다 | 계약 초안 |
| `PREFERENCE_VERSION_CONFLICT` | 409 | 다른 요청에서 취향 설정이 변경되었습니다. 최신 설정을 확인해 주세요 | 계약 초안 |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 항목을 찾을 수 없습니다 | 계약 초안 |
| `MEMORY_UNAVAILABLE` | 410 | 더 이상 열람할 수 없는 경험입니다 | 계약 초안 |
| `IMAGE_NOT_FOUND` | 404 | 사용할 수 있는 업로드 이미지를 찾을 수 없습니다 | 계약 초안 |
| `IMAGE_UPLOAD_EXPIRED` | 410 | 이미지 업로드 보관 시간이 만료되었습니다. 다시 업로드해 주세요 | 계약 초안 |
| `IMAGE_ALREADY_ATTACHED` | 409 | 이미 사용한 이미지입니다 | 계약 초안 |
| `IMAGE_TOO_LARGE` | 413 | 이미지 용량이 허용 범위를 초과했습니다 | 계약 초안 |
| `UNSUPPORTED_IMAGE_TYPE` | 415 | JPG 또는 PNG 이미지만 사용할 수 있습니다 | 계약 초안 |
| `INVALID_IMAGE` | 422 | 올바른 이미지 파일이 아닙니다 | 계약 초안 |
| `IMAGE_DIMENSIONS_EXCEEDED` | 422 | 이미지 해상도가 허용 범위를 초과했습니다 | 계약 초안 |
| `IMAGE_STORAGE_UNAVAILABLE` | 503 | 이미지를 저장하지 못했습니다. 다시 시도해 주세요 | 계약 초안 |
| `IMAGE_FILE_UNAVAILABLE` | 503 | 이미지 파일을 불러오지 못했습니다 | 계약 초안 |
| `ANALYSIS_TOKEN_INVALID` | 422 | 분석 결과를 확인할 수 없습니다 | 계약 초안 |
| `ANALYSIS_TOKEN_EXPIRED` | 422 | 분석 결과가 만료되었습니다. 다시 분석하거나 직접 분류해 주세요 | 계약 초안 |
| `ANALYSIS_CONTENT_MISMATCH` | 422 | 분석한 본문과 저장할 본문이 다릅니다 | 계약 초안 |
| `DAILY_WRITE_LIMIT_EXCEEDED` | 429 | 오늘 작성할 수 있는 경험 수를 초과했습니다 | 계약 초안 |
| `RATE_LIMITED` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요 | 계약 초안 |
| `INVALID_CURSOR` | 400 | 페이지 커서가 올바르지 않습니다 | 계약 초안 |
| `CURSOR_CONTEXT_MISMATCH` | 400 | 조회 조건이 변경되었습니다. 첫 페이지부터 다시 조회해 주세요 | 계약 초안 |
| `INVALID_BBOX` | 400 | 지도 조회 범위가 올바르지 않습니다 | 계약 초안 |
| `PLACE_COORDINATE_MISMATCH` | 422 | 선택한 장소와 좌표가 일치하지 않습니다 | 계약 초안 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | 재시도 식별자가 필요합니다 | 계약 초안 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 같은 재시도 식별자에 다른 요청이 사용되었습니다 | 계약 초안 |
| `REQUEST_IN_PROGRESS` | 409 | 동일한 요청을 처리하고 있습니다 | 계약 초안 |
| `COPY_FAILED` | 503 | 보관을 완료하지 못했습니다. 다시 시도해 주세요 | 계약 초안 |
| `CONFIGURATION_UNAVAILABLE` | 503 | 서비스 설정을 불러올 수 없습니다 | 계약 초안 |
| `SERVICE_UNAVAILABLE` | 503 | 일시적으로 요청을 처리할 수 없습니다 | 계약 초안 |
| `SERVER_ERROR` | 500 | 요청 처리 중 오류가 발생했습니다 | 계약 초안 |

## 11. ERD 보완안과 무결성 경계

### 11.1 기존 ERD에서 파생하는 값

| API 값 | ERD·서버 매핑 |
|---|---|
| hasOnboarded | mailbox_enabled_at과 최초 유효 취향 버전 |
| preferenceVersion | user_preference_versions.revision의 10진 문자열 |
| atmospheres | crowd_level·spatial_feel·company_fit·stay_style |
| type | memories.distribution_type |
| categories | memory_categories와 place_categories.code |
| imageUrl | image_path가 있을 때 권한 검사 API 경로를 파생 |
| ownerState.likeCount | 해당 원문의 liked_at IS NOT NULL 누적 개수 |
| originKind | memories.origin_kind |
| TodayResponse | 일일 작업·수신 기록·현재 원문 접근 상태를 외부 DTO로 변환 |
| BOOKMARK | 본인 PRIVATE·ACTIVE 조회. 별도 Bookmark 테이블 없음 |

### 11.2 추가 저장·검증 계약

**A. `image_uploads` — 선행 업로드 메타데이터**

| 필드 | 역할 |
|---|---|
| id UUID PK | FE가 사용하는 imageId |
| owner_id FK | 업로더 귀속과 권한 |
| storage_path UNIQUE | 서버 생성 상대 경로 |
| media_type·size_bytes·width·height | 검증 완료한 파일 정보 |
| status | STAGED / ATTACHED / EXPIRED 등 내부 상태 |
| expires_at | 미첨부 파일의 만료 시각 |
| attached_memory_id nullable UNIQUE FK | 직접 경험 생성에서 소비된 첨부 대상 |
| created_at | 업로드 완료 시각 |

업로드 파일과 직접 작성 경험의 연결은 **LETTER 원문과 PRIVATE 사본의 연결이 아니다.** 사본은 독립 경로로 복사하며 source_image_id 등으로 다시 원문 파일에 연결하지 않는다. 첨부 전환과 실패 파일 정리의 정합성을 검증해야 한다.

**B. `api_idempotency_records` — 직접 생성의 재시도 저장**

사용자·메서드·경로·키를 고유하게 관리하고, 요청 지문·처리 상태·반환 자원 종류와 ID·생성 및 완료 시각을 저장하는 안이다. 처리 중단 후 복구와 실제 자원 INSERT의 원자적 확정을 구현한다. 본문이나 전체 응답을 저장하지 않는다. **like 경로는 사용 대상에서 명시적으로 제외한다.** 사본 ID를 이 표에 기록해 원문과 연결해서는 안 된다.

**C. 분석 확인값 — 서명 방식이면 영구 테이블 없이 가능**

`analysisToken`은 서버 서명으로 검증한다. 영구 analysis_results 테이블이 필수는 아니다. 분석 ID별 캐시를 선택한다면 사용자 귀속·만료·정리 계약이 별도로 필요하지만, 두 방식을 동시에 구현하도록 요구하지 않는다. 서명키 회전·TTL·본문 해시·검증 순서는 구현 계약으로 확정한다.

**D. `reports.details` — 선택 설명의 저장**

reason text에는 신고 코드를 저장하고, 부연 설명은 nullable text 열로 분리하는 안이다. 기존 데이터가 있다면 코드 전환과 호환을 확인해야 한다.

**E. 인증 — 이메일·비밀번호 전용 자격증명 보완**

이메일·비밀번호 방식은 확정이다. `auth_identities`의 공급자 연결을 현재 로그인에 사용하지 않고, `email_password_credentials(user_id, email, email_lookup_key, password_hash, password_changed_at, created_at, updated_at)`를 사용하는 대체 모델을 제안한다. 이메일/조회키는 NOT NULL, 조회키는 UNIQUE, user_id는 app_users에 대한 PK·FK이며 비밀번호 해시는 API에 반환하지 않는다. 전체 컬럼·보존·운영 고려는 `AUTH_ERD_DELTA.md`를 따른다.

서버 실패 카운터·잠금 상태 저장은 별도로 필요하다. 존재하는 계정의 행만 갱신해서 등록 이메일 여부가 잠금 응답에 드러나지 않도록 한다. 카운터 관찰 구간·동시성·초기화·저장 방식은 미결이며, 토큰 갱신을 추후 채택한다면 그때 세션 저장을 추가한다. 최초 초대 자격증명 공급을 공개 회원가입 API로 임의 확장하지 않는다.

### 11.3 유지해야 하는 정합성

사용자·날짜별 일일 작업, 사용자·날짜별 수신 성공, 사용자·원문별 재수신 방지는 서로 다른 제약이다. like는 수신 행 잠금과 PRIVATE 생성·liked_at 갱신을 하나의 성공 단위로 처리한다. 카테고리 슬롯은 순위가 아니라 최대 개수 제약이다.

원문·사본의 self-FK나 숨은 응답 로그 연결은 만들지 않는다. 이미지 내용·JSON 중복 키·실제 사용자 권한은 DB 타입만으로 검증할 수 없으며 서비스 처리가 필요하다. EXPIRED_ERROR·NO_CANDIDATE·DELIVERED를 같은 상태로 합치지 않는다.

**기존 ERD 원본과 SQL 마이그레이션은 수정·실행하지 않았다.** 인증 저장 구조의 대체 제안은 `AUTH_ERD_DELTA.md`에 별도로 제공한다. 위 내용은 FE 요청을 연결하려면 필요한 변경 영향이다. 추가 저장 구조를 채택한 뒤 별도 Flyway 마이그레이션과 테스트로 이어간다.

## 12. FE 구현 예시 및 연동 우선순위

### 12.1 권고 연동 순서

로그인 방식은 이메일·비밀번호로 확정했다. 먼저 초대 자격증명 생성·공급, 토큰 TTL·API_BASE_URL·CORS·실제 config 값을 합의한다. 이후 FE가 요청한 우선순위를 유지한다.

| 단계 | 연결할 흐름 |
|---|---|
| 1 | config·사전·프로필·온보딩·취향 변경 |
| 2 | 텍스트 분석 → 미결 축 수동 보완 → 경험 생성 |
| 3 | 오늘 상태 5종 → 누적 수신함 → 페이지·필터 |
| 4 | 이미지 업로드 → 첨부 → Bearer 조회 → 독립 복사 |
| 5 | 최초·중복 좋아요 → BOOKMARK → 원문 삭제 |
| 6 | 지도 권한·내 기록·신고 및 운영 처리 |

기존 0~2시간 명세 합의, 공통 Mock 개발, 준비된 기능별 통합 병행 원칙을 유지한다. 특정 시각까지 첫 통합을 강제하는 일정을 새로 추가하지 않는다.

### 12.2 타입과 fetcher

`frontend/contracts.ts`는 같은 OpenAPI에서 만든 타입이다. `frontend/http_examples.ts`에는 이메일·비밀번호 로그인, Bearer JSON, FormData 업로드, Blob 이미지 조회, 204 및 오류 처리 예시를 넣었다. 이는 런타임 검증을 대신하지 않는다. Zod나 프로젝트 파일을 실제 설치·수정하지 않았으며, FE에서 스키마를 연결해 응답을 검증해야 한다.

배열 query에는 `URLSearchParams.append`를 사용한다. preferenceVersion을 number로 변환하지 않는다. TodayResponse와 LikeResponse는 상태별 분기를 통해 null인 원문·사본 ID·이미지를 처리한다.

### 12.3 mock에서 실제 연동으로 옮길 때

모든 예시 자격증명과 데이터는 가상이다. config의 반경·중심 좌표·숫자 한도는 운영 설정이 아니다. 무인증 이미지 mock이 표시되는 것만으로 Bearer 이미지 연동이 완료됐다고 판단하지 않는다.

POST 응답이 유실됐을 때 자동으로 새 요청 키를 만들지 않는다. 동일한 제출의 키를 유지해야 중복 생성을 막을 수 있다. 같은 날의 GET 반복이나 클라이언트 날짜 변경도 새 선정의 계기가 아니다.

nullable·enum·필드가 바뀌면 OpenAPI, Mock, Zod, BE DTO를 함께 관리한다. 실제 저장소를 읽지 못했으므로 현재 훅과 이 타입이 실행 수준에서 호환된다고 단정하지 않는다.

## 13. 계약 검증 시나리오

아래는 구현 후 실행할 수용 기준이다. 실제 HTTP·DB 테스트를 수행한 결과가 아니다.

| ID | 상황 | 기대 결과 |
|---|---|---|
| A01 | 유효 로그인·온보딩 전 | hasOnboarded=false, 설정 필드 null |
| A02 | 없는 계정·잘못된 비밀번호 | 같은 401 INVALID_CREDENTIALS |
| A03 | 인증은 유효하지만 미초대 | 403 INVITATION_REQUIRED |
| A04 | 로그인 실패 제한 후 앱 재시작 | 서버 제한 유지, 잔여 Retry-After 일치 |
| A05 | 토큰 만료 후 JSON·이미지 조회 | 401, 내부 경로·다른 사용자 정보 비노출 |
| A06 | 동일 온보딩 재전송·다른 값 재전송 | 200 /409, 위치 덮어쓰기 없음 |
| A07 | 축 누락·0·소수·문자열·null·중복 키 | 서버에서 거절, DB 캐스팅으로 통과 금지 |
| A08 | 취향 버전 충돌 | 409와 최신값 재조회, 과거 선정값 유지 |
| A09 | AI 일부·전체 실패 | 수동 4축 입력 가능, 최종 null 저장 불가 |
| A10 | 본문 수정 후 예전 분석 도착 | FE 응답 폐기, 서버 token 검증 실패 |
| A11 | token 없는 수동 저장 | 유효한 최종값 저장, USER /NOT_RUN 기록 |
| A12 | AI 실패 후 카테고리 수동 선택 | 최종 CLASSIFIED와 AI FAILED 구분 |
| A13 | 카테고리 4개·중복·허용 밖 코드 | 422 INVALID_CATEGORIES |
| A14 | 사진 없는 경험 | 정상 생성·조회·복사 |
| A15 | 타인·만료·이미 첨부한 이미지 | 404 /410 /409, 재사용 차단 |
| A16 | 가짜 확장자·과대 파일·픽셀 수 초과 | 서버의 실제 형식·크기 검증 |
| A17 | 업로드·생성 중복 요청 | 같은 키는 한 자원, 다른 payload는 409 |
| A18 | 응답 유실 후 사진 소비·분석 만료 | 완료 기록부터 확인해 기존 결과 반환 |
| A19 | 생성 201·검사 대기 LETTER | 소유자만 상태 확인, 타인 후보·지도 비노출 |
| A20 | 09시 전·정시 후 신규·실행 지연 | PENDING의 사유별 구분 |
| A21 | 후보 없음 종료 후 새 글 | 당일 추가 수신 없음 |
| A22 | 오늘 상태 DB 조회 장애 | 503, NO_CANDIDATE로 변환하지 않음 |
| A23 | 성공 배달의 원문 삭제 | DELIVERED 유지, 내용 없는 안내 항목 |
| A24 | OR/AND·같은 축 양쪽·미분류 | 계약에 맞는 조회, 계정 설정 불변 |
| A25 | 커서 변조·타인·필터 변경 | 400, 정보 누출 없음 |
| A26 | 카테고리 다대다 조회 | 같은 경험 중복 없이 페이지 |
| A27 | 최초 좋아요 | 200 COPIED, PRIVATE와 이미지 한 번 생성 |
| A28 | 동시·응답 유실 후 좋아요 | 200 ALREADY_COPIED, 사본 ID null |
| A29 | 이미 좋아요 후 원문·사본 삭제 | 완료 상태 유지, 재복사 없음 |
| A30 | 미실행 편지를 삭제한 뒤 좋아요 | 410, 사본 생성 없음 |
| A31 | like 응답 캐시·서버 로그 | 지속적인 원문·사본 ID 쌍 없음 |
| A32 | 미선정자의 상세·이미지·핀·집계 | 404 또는 결과 제외 |
| A33 | 본인 삭제 반복·타인 삭제 | 204 /404, 204 본문 없음 |
| A34 | PRIVATE 편집·재공유·위치 변경 | 미지원 경로 또는 명확한 거절 |
| A35 | Bearer 이미지 조회·로그아웃 | 인가 후 Blob 표시, 객체 URL 해제 |
| A36 | 기존 핀의 좌표 불일치·비가시 핀 | 422 /404, 자동 병합 없음 |
| A37 | 신고 동일 키·새 신고 | 같은 접수증 /새 접수, 처리 완료와 구분 |
| A38 | 작성자의 반응 조회 | 누적 수만 표시, 반응자 명단 없음 |
| A39 | 모든 수신자 응답 | 작성자 계정·닉네임·이메일·우편함 좌표 없음 |
| A40 | 정시 이후 후보·삭제 대체·과거 날짜 복구 | v1.4에서 금지한 추가 배달 없음 |

| A41 | 로그인에 외부 토큰 필드 또는 정상 필드와 추가 인증 필드 혼합 | 400 INVALID_REQUEST. 외부 인증 검증·폴백 없음 |
| A42 | 로그인 필드 누락·null·잘못된 타입 / 문자열 형식·빈 값 | 400 INVALID_REQUEST / 422 VALIDATION_ERROR |
| A43 | 로그인·내 계정 응답의 이메일이 null | 계약 위반. 필수 이메일 문자열 반환 |
| A44 | config 인증 방식 | EMAIL_PASSWORD 한 값만 반환 |
| A45 | 등록되지 않은 이메일의 반복 로그인 | 자동 계정 생성 없음. 동일한 일반 오류·서버 시도 제한 |
| A46 | DB·응답·로그에 비밀번호/해시 | DB는 해시만, 응답/로그는 원문·해시 모두 없음 |
| A47 | 정상 이메일·비밀번호 로그인 → 보호 API | 자체 access token 발급 후 Bearer 방식 유지 |

정적 검증은 YAML 파싱, 로컬 `$ref`, JSON Schema 예시와 부정 입력, TypeScript 컴파일을 별도 보고서로 기록한다. 정적 통과가 서버·DB·파일 복구·인가·AI 품질 테스트를 대신하지 않는다.

## 14. 남은 합의와 참고 자료

### 14.1 연동 전에 정할 계약

| 번호 | 항목 | 현재 초안 |
|---|---|---|
| D01 | 최초 초대 자격증명 생성·공급 | 로그인은 이메일·비밀번호로 확정. 계정/해시 생성·초기 비밀번호 전달·이메일 정규화·해시 파라미터는 구현 계약 |
| D02 | 토큰 만료·갱신 | 무갱신 재로그인 제안, 실제 TTL 미정 |
| D03 | 반경·초기 지도 중심·입력 및 사진 한도 | 서버 config로 통일, mock 수치는 미확정 |
| D04 | 선행 업로드·생성 재시도 저장 | 새 저장 구조와 Flyway 검증 필요 |
| D05 | analysisToken | 서명·만료·본문 대응·출처 검증 책임 확정 |
| D06 | 사본 ID의 최초 응답 | 일회성 반환 제안, 지속 연결 금지 |
| D07 | 핀 생성·재사용·표시 | 새 핀 기본, 명시적 가시 핀만 재사용 |
| D08 | 신고 사유·details·운영 도구 | 7개 코드 초안, 관리자 처리 수단 합의 |
| D09 | PRIVATE 안전 검사·보존 | AI 분류는 확정, moderation·보존·개별 조치는 운영 합의 |

이 목록은 확정한 하루 한 편·4축·익명·독립 보관 정책을 다시 여는 질문이 아니다. ERD와 FE 요청 사이에서 구현 계약으로 채워야 하는 내용을 구분한 것이다.

### 14.2 참고 자료

제품 정책의 근거는 첨부한 MVP 1.4, ERD 1.0, 사용자가 제공한 FE v2 및 이메일·비밀번호 전용 로그인이라는 최신 결정이다. W7·W8은 이번에 확인한 비밀번호 저장의 기술 근거이며 W1~W6은 기존 참고 자료다. 외부 자료는 기술 설명을 위한 것이며 이 앱의 실제 준수·보안성을 인증하지 않는다.

```text
W1 OpenAPI Specification 3.1.1
https://spec.openapis.org/oas/v3.1.1.html
W2 RFC 9110 — HTTP Semantics
https://www.rfc-editor.org/rfc/rfc9110.html
W3 OWASP Authentication Cheat Sheet
https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
W4 OWASP File Upload Cheat Sheet
https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
W5 MDN Using the Fetch API
https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch
W6 MDN URL.revokeObjectURL
https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static
W7 OWASP Password Storage Cheat Sheet
https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
W8 Spring Security Crypto Module — Password Encoding
https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html
```

제공한 YAML은 OpenAPI 3.1.0 형식으로 작성했다. 이 버전이 현재 최신이라는 의미는 아니다.

### 14.3 인계 상태

22개 참여자용 엔드포인트·공통 스키마·성공과 오류 예시를 같은 계약으로 정리했다. 실제 FE 저장소는 읽지 못했으므로 현재 Zod·훅과 실행 수준의 호환을 검증한 것은 아니다. API 서버·DB·인증 연동을 실행하거나 저장소에 커밋하지 않았다.

이 초안을 합의한 뒤 FE Mock·Zod·BE DTO와 필요한 추가 저장 구조를 함께 버전 관리한다.


## 부록 — 이번 개정의 정적 검사

이번 개정에서 YAML 파싱, 22개 API·48개 스키마·207개 로컬 참조, 354개 정상 요청/응답 예시, 19개 부정 입력, 310개 명세 내 예시를 검사했다. 이메일·비밀번호 단일 요청·외부 토큰 요청 거절·응답 이메일 null 거절·고정 auth.mode와 TypeScript `--strict --noEmit` 검사도 통과했다. 오류 예시의 반복을 포함한 수치이며 실제 354개 API 호출을 수행한 것은 아니다. 상세 결과는 `validation/validation_report.json`을 따른다. 실제 서버·DB·인증·CORS·비밀번호 검증·로그인 잠금·동시성·AI 호출 테스트는 수행하지 않는다.
