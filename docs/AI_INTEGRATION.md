# AI 연동 가이드 (Backend ↔ 별도 AI 서비스)

## 1. 현재 합의 상태

백엔드가 기대하는 분석 wire는 **AX-AI-WIRE-v2**이며 상태는 **BACKEND_EXPECTED_NOT_PROVIDER_VERIFIED**다. 백엔드와 AI 서비스는 별도 프로세스이고 예상 내부 대상은 `http://127.0.0.1:8001`이다.

이는 backend decoder·analysis receipt 경계의 계약이지 실제 AI provider endpoint, listener, 인증, model 호출, 소수 축 생성 품질 또는 end-to-end 동작의 검증이 아니다. 모두 **NOT_RUN**이다. Docker 구성·실행, AI provider key·gateway 설정, AI 저장소 설치 지침은 이 백엔드 문서 범위가 아니다.

```
Backend public API (/v1/*)
  └─ internal analysis adapter
       └─ HTTP → separate AI service (expected 127.0.0.1:8001)
```

AI 서비스는 저장, 접근 제어, moderation/배달 적격성 전이, source-copy 관계, 최종 tie-break 선택을 수행하지 않는다. 백엔드가 이 책임을 보유한다.

## 2. 분석 요청·응답 계약

### 요청: `POST /ai/analyze`

`application/json`의 필수 field는 `content`, `axisDefinitionVersion`, `taxonomyVersion`이고 `naver_category`만 선택적이다.

```json
{
  "content": "창가 자리에 앉아 조용히 오래 책을 읽기 좋은 카페였다.",
  "axisDefinitionVersion": 2,
  "taxonomyVersion": 1,
  "naver_category": "카페"
}
```

본문 외 사진·계정 취향·작성자·좌표·사용자 식별자·source-copy 관계는 보내지 않는다. client timeout은 HTTP wire field가 아니다.

### 응답: flat AX-AI-WIRE-v2

성공 또는 부분 결과는 nested `provenance` 없이 flat body로 수신한다. 필수 field는 `atmospheres`, `categories`, `model`, `promptVersion`, `axisDefinitionVersion`, `taxonomyVersion`이다.

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

선택적 primary enrichment는 `atmosphereStatus`, `categoryStatus`, `error`, `evidence`, `tags`, `category_confidence`, `category_source`, `masked_content`, `pii_found`, `safe`, `unsafe_reason`이다. 모두 snake_case만 사용한다. 누락된 선택 field는 absent이며 backend가 값을 추정하지 않는다. 제공된 선택 field가 type·enum·유도 결과와 맞지 않으면 응답 전체가 실패다.

`dictionaryVersion`, nested `provenance`, camelCase alias, singular category alias, 문자열·`Number` coercion은 지원하지 않는다. v1/누락 metadata를 local v2 성공으로 승격하거나 이전 nested/int-only wire를 병행 지원하지 않는다.

## 3. 축·카테고리·상태 해석

- 축은 canonical 네 key를 모두 정확히 한 번 포함한다. null object, missing/duplicate/unknown key는 전체 응답 실패다.
- 각 값은 명시적 `null` 또는 유한 JSON number `[-1,1]`이다. integer/fraction/exponent 표기를 허용하며 `0`은 known axis다. `-0`은 `+0`으로 정규화한다.
- 원래 number lexeme은 최대 1000자이고 binary64 변환 전에 범위를 확인한다. 표준 binary64 underflow로 작은 비영수가 `+0`이 될 수 있지만 추가 반올림·양자화·clamp는 없다.
- string, boolean, object, array, NaN/infinity, 범위 밖 값, missing axis를 null/PARTIAL로 바꾸지 않는다.
- known 축 수는 `SUCCEEDED`(4), `PARTIAL`(1~3), `FAILED`(0)를 유도한다. 제공된 status는 유도 상태와 일치하고 `0`을 unknown으로 취급하지 않아야 한다.
- categories는 0~3개의 distinct known code다. `OTHER`는 배타적이며 unknown drop, dedup repair, truncation은 하지 않는다.
- `axisDefinitionVersion`은 정확히 2, `taxonomyVersion`은 정확히 1이며 `model`과 `promptVersion`은 수신 body의 nonblank string이다. 틀리거나 누락되면 AI provenance·성공 token 근거가 아니다.

명시적 upstream failure는 성공 제안을 폐기한다. malformed/type/range/key/version response 및 network/timeout/`5xx`는 기존 analysis 실패 경로로 변환한다. 원격 결과가 없는 실패를 PARTIAL, null, `NOT_RUN`, fabricated v2 metadata로 바꾸지 않는다.

## 4. receipt 및 백엔드 책임

검증된 v2 분석 결과만 payload version 3 receipt의 AI 근거가 된다. v3 축 claim은 고정 canonical 순서의 `axes-v2:<field>,<field>,<field>,<field>`다. `null`은 `?`, 알려진 값은 `Double.doubleToLongBits` 기반의 정규화된 16자리 lowercase hex다. old/unknown payload 또는 옛 `+/-/?` claim은 자동 변환하지 않고 거절하며 재분석이 필요하다.

AI `null`은 미결이고 0은 known이다. source 판단은 canonical exact bits를 사용한다. token이 없는 수동 흐름은 계속 ALL_USER/`NOT_RUN`이며 AI provider 결과가 아니다.

이 receipt 설명은 backend-expected contract다. 실제 token 발급, adapter/runtime, provider fractional response, 저장·읽기·copy 또는 full MVP가 검증되었다는 주장이 아니다.

## 5. 다른 AI endpoint의 선택적 경계

`POST /ai/moderate`, `POST /ai/tiebreak`, `GET /ai/axes`, `GET /ai/categories`는 선택적 제안이고 provider 검증은 **NOT_RUN**이다. `POST /ai/preference-suggest`, `POST /ai/arrival-reason`, `POST /ai/precheck`은 미래 예약·미사용이다.

- moderation은 LETTER 저장 후 배달 적격화 전에만 호출하며 PRIVATE에는 호출하지 않는다. 본문과 선택적 0~1개 살균 JPEG/PNG binary만 보내며 실패는 승인으로 취급하지 않는다.
- tie-break는 원래 최고점 후보 전체와 자연어 취향에 한정한다. 유효한 complete ordered rank-group partition만 소비하고 무효·실패·예산 초과에는 원래 최고점 전체로 fallback한다. 최종 무작위 선택은 백엔드가 한다.
- 실제 endpoint shape, moderation 판정, tie-break 품질, 인증·secret, rate/size/timeout 예산은 이 문서가 발명하거나 검증하지 않는다.

## 6. 보안과 미해결 운영 게이트

업무 사용자 JWT를 AI 서비스에 재사용하지 않는다. 본문·취향·사진·원 요청/응답·자격증명·source-copy ID 쌍을 로그에 남기지 않는다. 대상은 `127.0.0.1:8001`로 제한하고 redirect를 허용하지 않는다.

loopback은 인증 증명이 아니다. service-to-service 인증, secret 공급·회전, 실제 provider 실행, endpoint 수용, model/prompt revision, fractional quality, HTTP/JSON/이미지/후보/token 한도 및 timeout은 모두 미해결 운영 게이트다. 따라서 이 문서는 runtime provider 또는 전체 MVP 완료를 주장하지 않는다.
