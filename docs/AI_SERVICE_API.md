# AI 내부 서비스 HTTP 계약

## 1. 상태·권한·경계

이 문서는 백엔드가 기대하는 **AX-AI-WIRE-v2** 계약이다. 상태는 **BACKEND_EXPECTED_NOT_PROVIDER_VERIFIED**다. backend decoder·receipt 경계의 합의이며, 실제 AI 제공자가 이를 수용·구현했거나 endpoint·소수 축 품질을 검증했다는 증거가 아니다.

- AI 프로세스 대상은 고정 `http://127.0.0.1:8001`이다. loopback bind·listener·인증·실행은 **NOT_RUN**이다.
- 공개 업무 API는 `/v1/*`이고 `/ai/*`는 향후 내부 HTTP adapter의 대상 경로다.
- AI 서비스는 DB 저장, 배달 적격성, 접근 제어, source-copy 관계, 최종 선택에 권한이 없다.
- 직접 생성 `PRIVATE`는 본문 분석 제안 대상이지만 moderation과 LETTER 배달 적격화 대상이 아니다. `LETTER`는 저장 후 배달 적격화 전에 moderation한다. `LETTER_COPY`는 직접 생성 분석 대상이 아니다.

## 2. endpoint와 호출 조건

| 메서드·경로 | 백엔드 기대 상태 | 호출 조건 |
|---|---|---|
| `POST /ai/analyze` | **AX-AI-WIRE-v2** | LETTER 또는 직접 생성 PRIVATE의 비어 있지 않은 본문 분석 제안 |
| `POST /ai/moderate` | 선택적 제안, provider 미검증 | LETTER 저장 뒤 배달 적격화 전 |
| `POST /ai/tiebreak` | 선택적 제안, provider 미검증 | 최고점 후보가 복수이고 자연어 취향이 있을 때 |
| `GET /ai/axes`, `GET /ai/categories` | 선택적 제안, provider 미검증 | adapter 호환성 검사 시 |
| `POST /ai/preference-suggest`, `POST /ai/arrival-reason`, `POST /ai/precheck` | 미래 예약·미사용 | 없음 |

## 3. `POST /ai/analyze`: AX-AI-WIRE-v2

### 요청

`application/json` 객체다. `content`, `axisDefinitionVersion`, `taxonomyVersion`이 필수이고 `naver_category`만 선택적으로 허용한다. 요청은 반드시 `axisDefinitionVersion: 2`, `taxonomyVersion: 1`을 보낸다.

```json
{
  "content": "작은 카페 창가에서 친구와 책을 읽었고, 조용해서 오래 머물기 좋았다.",
  "axisDefinitionVersion": 2,
  "taxonomyVersion": 1,
  "naver_category": "카페"
}
```

본문 외 사진·계정 취향·작성자·좌표·사용자 식별자·source-copy 관계는 보내지 않는다. client timeout은 wire field가 아니다.

### 응답

응답은 **nested `provenance` envelope 없이** flat JSON 객체다. `atmospheres`, `categories`, `model`, `promptVersion`, `axisDefinitionVersion`, `taxonomyVersion`은 필수다. 수신 body의 `model`·`promptVersion`은 nonblank string이어야 한다.

```json
{
  "atmospheres": {
    "CROWD_LEVEL": -0.25,
    "SPATIAL_FEEL": null,
    "COMPANY_FIT": 0,
    "STAY_STYLE": 0.75
  },
  "categories": ["CAFE"],
  "model": "provider-model-revision",
  "promptVersion": "classify-v2",
  "axisDefinitionVersion": 2,
  "taxonomyVersion": 1,
  "atmosphereStatus": "PARTIAL",
  "categoryStatus": "SUCCEEDED"
}
```

선택적 primary snake_case enrichment는 `atmosphereStatus`, `categoryStatus`, `error`, `evidence`, `tags`, `category_confidence`, `category_source`, `masked_content`, `pii_found`, `safe`, `unsafe_reason`이다. 누락은 absent이며 backend가 추정·보충하지 않는다. 제공된 선택 field가 type·enum·유도 결과에 맞지 않으면 전체 응답을 거절한다. camelCase alias, `dictionaryVersion` envelope, nested provenance, 문자열·`Number` coercion은 fallback이나 호환 alias가 아니다.

### 엄격한 축·카테고리·상태 규칙

- `atmospheres`는 null이 아닌 객체이고 canonical 네 key `CROWD_LEVEL`, `SPATIAL_FEEL`, `COMPANY_FIT`, `STAY_STYLE`를 모두 정확히 한 번 포함한다. missing/duplicate/unknown key는 전체 응답 실패다.
- 각 축은 명시적 `null` 또는 유한 JSON number `[-1,1]`이다. integer/fraction/exponent 표기를 허용한다. `0`은 known이고 `null`과 다르며 `-0`은 `+0`으로 정규화한다.
- 원래 numeric lexeme은 최대 1000자이며 binary64 변환 전에 수학적 범위를 검사한다. 범위 안의 작은 비영 수는 표준 binary64 underflow로 `+0`이 될 수 있다. 추가 반올림·양자화·clamp는 없다.
- string, boolean, object, array, NaN/infinity, 범위 밖 값, 변환 전 범위 검사 우회 값은 거절한다. 하나의 잘못된 축을 `null` 또는 PARTIAL로 salvage하지 않는다.
- known 축 수는 `SUCCEEDED`(4), `PARTIAL`(1~3), `FAILED`(0)를 유도한다. 제공된 status는 유도 결과 및 유효 enum과 일치해야 하며 `0`을 unknown으로 낮추지 않는다. 명시적 upstream failure는 성공 제안을 폐기한다.
- categories는 0~3개의 distinct known code다. `OTHER`는 배타적이다. singular alias, unknown drop, dedup repair, truncation은 하지 않는다.
- unknown top-level field는 거절한다. version이 틀리거나 누락된 metadata를 local v2 성공으로 승격하지 않는다.

검증된 `axisDefinitionVersion: 2`, `taxonomyVersion: 1`, flat nonblank `model`/`promptVersion`만 AI 성공 provenance와 payload 3 receipt의 근거가 된다. malformed/type/range/key/version response 및 network/timeout/`5xx`는 기존 analysis 실패 경로로 변환하며 성공·부분성공·null salvage나 fabricated v2 provenance로 바꾸지 않는다. `NOT_RUN`은 AI analyze 응답이 아니라 호출하지 않은 수동 흐름의 상태다.

### receipt 경계

분석 receipt는 payload version **3**이다. 축 claim은 고정 canonical 순서의 `axes-v2:<field>,<field>,<field>,<field>`이고 `null`은 `?`, 알려진 값은 zero-normalized `Double.doubleToLongBits`의 16자리 lowercase hex다. outer payload version 3, `axisDefinitionVersion=2`, taxonomy/status/축 개수 일관성을 검증한다. 이전·알 수 없는 payload 또는 구형 `+/-/?` claim은 자동 이행하지 않고 거절하며 재분석이 필요하다. 이는 receipt 구현·서명·실제 token 발급이 검증되었다는 주장이 아니다.

## 4. 선택적 moderation·tie-break 경계

이 endpoint는 실제 제공자 수용·실행이 **NOT_RUN**인 선택적 제안이며 AX-AI-WIRE-v2 analyze wire의 alias나 대체 성공 근거가 아니다.

- moderation은 `multipart/form-data`로 `content`와 선택적 0~1개 살균 JPEG/PNG binary만 보낸다. image URL, base64, path, 원 filename, 사용자 token은 보내지 않는다. 사진 load·검증 실패를 사진 없음으로 축소하지 않고 전송 실패는 승인되지 않는다.
- moderation 결과는 `APPROVED`, `REVIEW_REQUIRED`, `REJECTED`, `ERROR`만 기존 `ModerationVerdict`에 매핑한다. `PENDING`, `NOT_RUN`, `NOT_REQUIRED`는 외부 최종 판정이 아니다.
- tie-break에는 원래 최고점 후보 전체의 ID·본문·자연어 취향만 보낸다. 성공 결과는 모든 요청 ID가 정확히 한 번인 빈 group 없는 complete ordered rank-group partition이어야 한다. 무효·실패·예산 초과에는 원래 최고점 전체로 fallback하며 최종 무작위 선택은 백엔드 책임이다.

## 5. 보안·미해결 운영 게이트

본문·취향·사진·원 요청/응답·자격증명·source-copy ID 쌍을 로그에 남기지 않는다. 업무 사용자 JWT를 AI 서비스에 재사용하지 않는다. 대상은 `127.0.0.1:8001`로 제한하고 redirect를 허용하지 않는다.

service-to-service 인증, secret 공급·회전, 실제 provider endpoint·listener·model/prompt revision, fractional output 품질, moderation/tie-break behavior, HTTP/JSON/이미지/후보/token 한도 및 timeout은 **NOT_RUN** 또는 미해결 운영 게이트다. 이 문서는 runtime provider 또는 full MVP 검증을 주장하지 않는다.
