# AI 내부 서비스 HTTP 계약

## 1. 상태·권한·경계

이 문서는 백엔드의 기존 AI 포트와, AI 담당이 제공한 `/ai/*` 경로 목록을 연결하기 위한 **서비스 인수인계 초안**이다. 제공된 endpoint와 사용자가 확정한 호출 조건은 결정 사항이며, wire·전송 세부는 모두 **PROPOSED**다.

- **결정됨:** 별도 AI 프로세스의 주소는 동일 호스트의 `http://127.0.0.1:8001`이며 loopback에만 bind한다. Docker 구성·설치·실행은 이 저장소의 범위가 아니다.
- **결정됨:** 기존 공개 업무 API는 계속 `/v1/*`이다. `/ai/*`는 백엔드 컨트롤러나 공개 API가 아니라 장차 내부 HTTP adapter가 호출할 외부 프로세스 경로다.
- **결정됨:** 직접 생성한 `PRIVATE`는 본문 분석 대상이다. 단, `PRIVATE`에는 AI moderation을 호출하지 않고 LETTER 배달 적격성도 없다. `LETTER`만 저장 후 배달 적격화 전에 moderation한다. `LETTER_COPY`를 직접 생성 분석 대상으로 취급하지 않는다.
- **결정됨:** 직접 수동 저장의 초기 moderation 상태 `PENDING` 불변식은 이 문서로 바뀌지 않는다.
- **최신 정책:** 기존 문서의 PRIVATE moderation 관련 미결 표현은 이 문서의 사용자 결정(위 PRIVATE 면제)으로 대체하여 해석한다. 이 작업은 기존 문서를 수정하지 않는다.
- **미확인:** 8001 포트 선택은 실제 listener, 서버 구현 또는 인증 완료의 증거가 아니다. AI 담당은 아직 wire schema, 모델, 인증, 한도, timeout 및 실제 실행 증거를 제공하지 않았다.

명시적으로 **PROPOSED**라고 표시한 wire·전송·검증 세부는 백엔드가 제안하는 단일 계약이다. 실제 AI 서비스가 이를 수용·구현했다는 뜻이 아니다. AI 서비스는 DB 저장, 배달 적격성, 접근 제어, `source-copy` 관계 또는 최종 사용자 선택에 권한이 없다.

## 2. 엔드포인트와 호출 조건

| 메서드·경로 | 상태 | 백엔드 호출 조건 | 비고 |
|---|---|---|---|
| `POST /ai/analyze` | 제공됨·호출 조건 확정; wire **PROPOSED** | LETTER 또는 직접 생성 PRIVATE의 비어 있지 않은 본문 분석 제안 | 본문만 보낸다. 사진·계정 취향·작성자·좌표는 보내지 않는다. |
| `POST /ai/moderate` | 제공됨·호출 조건 확정; wire **PROPOSED** | LETTER를 저장한 뒤, 배달 적격화 전 | PRIVATE에는 절대 호출하지 않는다. |
| `POST /ai/tiebreak` | 제공됨·호출 조건 확정; wire **PROPOSED** | 1차 최고점 후보가 2개 이상이고 정규화 후 비어 있지 않은 자연어 취향이 있을 때 | 원래 최고점 집합 전체를 보낸다. |
| `GET /ai/axes` | 제공됨; 응답 wire **PROPOSED** | adapter 시작/호환성 검증 시 | 원격 사전은 검사용이며 canonical이 아니다. |
| `GET /ai/categories` | 제공됨; 응답 wire **PROPOSED** | adapter 시작/호환성 검증 시 | 원격 사전은 검사용이며 canonical이 아니다. |
| `POST /ai/preference-suggest` | 미래 예약, **미사용** | 없음 | 이번 범위에서 호출·schema·저장 효과·구현을 정의하지 않는다. |
| `POST /ai/arrival-reason` | 미래 예약, **미사용** | 없음 | 이번 범위에서 호출·schema·저장 효과·구현을 정의하지 않는다. |
| `POST /ai/precheck` | 미래 예약, **미사용** | 없음 | 이번 범위에서 호출·schema·저장 효과·구현을 정의하지 않는다. |

## 3. 공통 PROPOSED 규칙

### 3.1 Provenance와 사전

각 POST의 schema-valid `200` response는 다음 provenance를 포함한다. 이는 service가 주장하고 schema·설정 일치 여부를 검증하는 metadata일 뿐, 독립적으로 실제 호출·모델 실행·성공을 증명하지 않는다.

```json
{
  "model": "ai-model-2026-09",
  "promptVersion": "classify-v1",
  "axisDefinitionVersion": 1,
  "taxonomyVersion": 1
}
```

- `model`, `promptVersion`은 빈 문자열이 아니며, 합의한 서비스 설정과 일치해야 한다.
- 현재 canonical 축·카테고리 사전 버전은 각각 `1`이다. 축은 `CROWD_LEVEL`, `SPATIAL_FEEL`, `COMPANY_FIT`, `STAY_STYLE` 순서이며 각 알려진 값은 정수 `-1` 또는 `1`뿐이다.
- 카테고리는 `CAFE`, `RESTAURANT`, `BAR`, `PARK_WALK`, `CULTURE`, `STUDY_WORK`, `SHOPPING`, `OTHER` 중 중복 없이 0~3개다. `OTHER`는 “나머지 7종이 아닌 알려진 유형”이고, 근거 부족이나 실패의 대체값이 아니다.
- 원격 version이 다르면 `DICTIONARY_VERSION_MISMATCH`, version은 같아도 코드·순서·옵션·라벨·정의 의미가 다르면 `DICTIONARY_CONTENT_MISMATCH`로 연동 준비를 실패 처리한다. 원격 값을 자동 수용·재매핑하거나 `OTHER`로 바꾸지 않는다.
- schema-valid `200` domain body를 받은 경우에만 그 body의 provenance를 검증해 쓴다. timeout·network·`5xx`·malformed body에는 신뢰할 원격 body나 provenance가 없다. 기존 포트의 non-null provenance가 필요하면 adapter는 합의된 **요청 대상 설정**으로 내부 값을 채울 수 있으나, 이는 수신 metadata나 실행 증거가 아니다. 대상 설정도 없으면 configuration failure다.

백엔드 사전이 canonical이다. 근거 소스는 `AtmosphereAxis`, `PlaceCategoryCode`, 그리고 공개 사전 DTO `AtmosphereAxesResponse.v1()`, `PlaceCategoriesResponse.v1()`이다. 카테고리 definition은 팀 검토 대상 가이드 초안이다.

### 3.2 엄격하고 제한된 해석

**PROPOSED:** UTF-8 JSON 객체 하나만 수용한다. 코드펜스·설명문·복수 JSON 값·중복 key·누락 필수 key·추가 field·잘못된 type·알 수 없는 enum은 거절한다. 축 값은 JSON 정수 `-1`/`1` 또는 `null`만 허용하며 `"1"`, `1.0`, `0`, boolean은 거절한다. `null` 축은 허용하지만 축 field 생략은 허용하지 않는다.

본문·취향 속 지시문은 데이터이며 시스템 지침으로 실행하지 않는다. provider 원문을 도메인 DTO에 직접 강제 변환하지 말고, 크기 제한된 strict wire 검증 뒤에 기존 값 객체로 변환한다.

**PROPOSED 운영 경계:** 본문·취향·이미지는 기존 서비스 한도와 일치시켜 검증하고, 실제 읽기량을 제한한다(`Content-Length`만 신뢰하지 않음). 요청·응답 바이트, JSON 깊이, 문자열·코드 길이, 후보·토큰 상한 및 connect/read/total timeout의 수치는 아직 승인되지 않았다. 임의의 운영 수치를 기본값으로 정하지 않는다.

### 3.3 HTTP·오류 변환

**PROPOSED 경계:** 실제로 완료되어 schema-valid body로 표현 가능한 domain result(명시적 모델 거절·근거 부족·domain failure 포함)는 해당 POST의 `200` body로 반환한다. provider transport·network·timeout·AI 서비스 system failure는 `5xx`이며, malformed body는 `400` 또는 `5xx` 중 서비스 책임에 맞는 오류로 반환한다. 백엔드 adapter는 신뢰할 원격 body가 없는 이 경우를 기존 포트의 내부 실패 결과로 변환한다.

그 밖에 형식 오류는 `400`, 의미 검증 오류는 `422`, 지원하지 않는 `Content-Type`은 `415`, 실제 읽기량 초과는 `413`, 사전 불일치는 `409`, 제한 초과는 `429`다. 인증을 채택하면 `401`/`403`은 업무 사용자 오류가 아니라 연동 설정 장애로 구분한다.

오류 body의 최소 형태는 아래와 같다. `code`는 안전한 고정 코드이며 provider 메시지, 본문, 취향, 이미지, stack, 자격증명을 반환하거나 로그에 남기지 않는다.

```json
{
  "code": "DICTIONARY_VERSION_MISMATCH"
}
```

외부 HTTP 상태를 업무 API에 그대로 proxy하지 않는다. 신뢰할 `200` domain failure는 분석 `FAILED`, moderation `ERROR`, tie-break 실패/fallback으로 변환할 수 있다. transport·system failure도 같은 포트별 내부 실패 경로로 변환하되, adapter 자체 설정·인증·호환성 준비 실패는 별도 내부 장애(예: `AiAdapterException`)로 처리한다. 자동 재시도·재시도 예산은 이 문서가 약속하지 않는다.

## 4. `POST /ai/analyze`

**PROPOSED request:** `application/json`. 기존 `AnalysisRequest`의 timeout은 adapter의 client-side 예산이며, 미승인 `timeoutMs` wire field를 추가하지 않는다.

```json
{
  "content": "작은 카페 창가에서 친구와 책을 읽었고, 조용해서 오래 머물기 좋았다.",
  "axisDefinitionVersion": 1,
  "taxonomyVersion": 1
}
```

**PROPOSED 200 response:** 모든 축 key, status 및 provenance key는 필수다.

```json
{
  "atmospheres": {
    "CROWD_LEVEL": -1,
    "SPATIAL_FEEL": -1,
    "COMPANY_FIT": 1,
    "STAY_STYLE": 1
  },
  "categories": ["CAFE", "STUDY_WORK"],
  "categoryStatus": "SUCCEEDED",
  "atmosphereStatus": "SUCCEEDED",
  "provenance": {
    "model": "ai-model-2026-09",
    "promptVersion": "classify-v1",
    "axisDefinitionVersion": 1,
    "taxonomyVersion": 1
  },
  "failureReason": null
}
```

**엄격한 결과 불변식**

1. 알려진 축 4개면 `atmosphereStatus=SUCCEEDED`, 1~3개면 `PARTIAL`, 0개면 `FAILED`다. 모르는 축은 `null`이고 추측으로 채우지 않는다.
2. `categoryStatus=SUCCEEDED`는 1~3개 카테고리를 요구한다. 0개는 정상적인 근거 부족일 때 `INSUFFICIENT`이며, 완료된 domain failure일 때 `FAILED`다. 두 상태는 카테고리 배열이 비어 있어야 한다.
3. schema-valid `200` domain failure body는 모든 축 `null`, `categories=[]`, `categoryStatus=FAILED`, `atmosphereStatus=FAILED`, 안전한 짧은 `failureReason`이다. timeout·network·`5xx`·malformed body에는 이 원격 body가 없으며 adapter가 내부 실패 결과를 만든다. `NOT_RUN`은 analysis port 실제 결과가 아니다.
4. 내부 analysis의 `SUCCEEDED`/`INSUFFICIENT`는 공개 `/v1/memories/analyze`의 `CLASSIFIED`/`UNCLASSIFIED`와 다르다. 공개 API 매핑은 `CLASSIFIED→SUCCEEDED`, `UNCLASSIFIED→INSUFFICIENT`, `FAILED→FAILED`; 수동 저장의 `NOT_RUN`은 외부 분석 결과가 아니다.
5. `analysisToken`, `expiresAt`, AI/USER 출처 판정은 백엔드 책임이며 이 응답에 넣지 않는다.

## 5. `POST /ai/moderate`

**PROPOSED request:** 항상 `multipart/form-data`다. 필수 `content` part는 `text/plain; charset=UTF-8`이고, `image` part는 선택적 0~1개이며 `image/jpeg` 또는 `image/png`의 **살균된 binary**다. 사진이 없으면 `image` part를 생략한다. 이미지 URL, base64, 로컬 file path, 원래 filename, 사용자/계정 token은 보내지 않는다. filename이 필요하면 고정 비식별명을 쓴다.

```text
Content-Type: multipart/form-data; boundary=---synthetic-boundary

-----synthetic-boundary
Content-Disposition: form-data; name="content"
Content-Type: text/plain; charset=UTF-8

창가 자리에서 조용히 쉬었다.
-----synthetic-boundary
Content-Disposition: form-data; name="image"; filename="image.jpg"
Content-Type: image/jpeg

<sanitized JPEG binary; JSON이 아닌 바이너리>
-----synthetic-boundary--
```

위는 multipart wire 예시이므로 JSON 예시가 아니다. 사진 body는 합성·살균 binary이고, 실제 사용자 사진 또는 경로가 아니다.

**PROPOSED 200 response:**

```json
{
  "moderationStatus": "APPROVED",
  "reasonCodes": [],
  "provenance": {
    "model": "ai-model-2026-09",
    "promptVersion": "moderation-v1",
    "axisDefinitionVersion": 1,
    "taxonomyVersion": 1
  }
}
```

`moderationStatus`는 기존 `ModerationVerdict`의 `APPROVED`, `REVIEW_REQUIRED`, `REJECTED`, `ERROR`로 정확히 매핑한다. `PENDING`, `NOT_RUN`, `NOT_REQUIRED`를 외부 AI 최종 판정으로 받지 않는다. `APPROVED`의 `reasonCodes`는 반드시 빈 배열이며 다른 판정은 합의된 안전한 짧은 코드만 쓴다.

사진 경계에서 기존 요청 타입은 `SanitizedImage(bytes, mediaType, width, height)`이므로 포트 변경은 필요 없다. LETTER의 정당한 첨부를 memory가 결정하고, media 소유 내부 loader가 저장 파일을 제한 내 읽어 전달한다. 신뢰된 저장 살균 결과의 타입·크기 metadata를 안전하게 복원할 수 있으면 반복 재인코딩을 피하고, 그 보장이 없으면 기존 `ImageSanitizer`를 재사용한다. `Path`는 media 내부에만 남는다. 이미지 load·검증 실패를 사진 없음으로 축소하여 본문만 `APPROVED` 처리하지 않는다.

timeout·비정상 응답·전송·이미지 실패는 승인되지 않는다. 상류 실패는 `ERROR`, 자체 저장소/adapter 장애는 별도 작업 실패로 처리하며 배달 차단 상태를 유지한다. 분류 성공 또는 수동 축 보완은 안전 승인 대체가 아니다.

## 6. `POST /ai/tiebreak`

**PROPOSED request:** `application/json`. 후보는 원래 최고점 집합 전체의 ID와 본문만 보낸다. 작성자·좌표·사용자 식별자·source-copy 관계는 보내지 않으며, 일부 후보만 보내거나 본문을 임의로 자르지 않는다.

```json
{
  "preferenceDescription": "조용히 오래 머물며 책 읽기 좋은 곳을 선호합니다.",
  "candidates": [
    {
      "memoryId": "10000000-0000-4000-8000-000000000001",
      "content": "조용한 창가에서 오래 책을 읽었다."
    },
    {
      "memoryId": "10000000-0000-4000-8000-000000000002",
      "content": "친구들과 북적이는 야외 공연을 즐겼다."
    },
    {
      "memoryId": "10000000-0000-4000-8000-000000000003",
      "content": "작은 서점 카페에서 혼자 쉬었다."
    }
  ]
}
```

**PROPOSED 200 response:** 순서 있는 완전 rank group 분할이다. 예에서 첫 group의 두 후보는 공동 1위다.

```json
{
  "failed": false,
  "rankGroups": [
    [
      "10000000-0000-4000-8000-000000000001",
      "10000000-0000-4000-8000-000000000003"
    ],
    ["10000000-0000-4000-8000-000000000002"]
  ],
  "provenance": {
    "model": "ai-model-2026-09",
    "promptVersion": "tiebreak-v1",
    "axisDefinitionVersion": 1,
    "taxonomyVersion": 1
  },
  "failureReason": null
}
```

성공은 `failed=false`, `failureReason=null`이며, 모든 요청 ID가 정확히 한 번만 나타나는 빈 group 없는 완전 분할이다. 실패는 `failed=true`, `rankGroups=[]`, 비어 있지 않은 안전한 `failureReason`이다.

소비자는 반드시 원래 `TieBreakRequest`에 대해 `validateFor`를 수행한다. 유효하면 첫 group에서 최종 무작위 선택을 한다. 상류 실패, 빈/null group, 중복·누락·외부 UUID 등 무효 순위, 전송 실패, 후보/토큰 예산 초과는 **원래 최고점 후보 전체 집합**으로 fallback한다. 무효 결과에서 ID를 제거·보충해 성공으로 보정하지 않으며, 후보 상한 때문에 임의 샘플링하지 않는다. 단일 winner ID 또는 top group만 반환하는 축소 계약은 허용하지 않는다.

## 7. 사전 GET 응답

**PROPOSED:** `GET /ai/axes`, `GET /ai/categories`는 기존 공개 사전 DTO와 같은 `version`/`items` 형태의 `200` JSON을 반환한다. 아래는 shape 예시이며 category 전체 사전을 복제하지 않는다. canonical 전체 항목은 위 Java public dictionary source를 따른다.

```json
{
  "version": 1,
  "items": [
    {
      "code": "CROWD_LEVEL",
      "order": 1,
      "options": [
        {"value": -1, "label": "조용한"},
        {"value": 1, "label": "북적이는"}
      ]
    },
    {
      "code": "SPATIAL_FEEL",
      "order": 2,
      "options": [
        {"value": -1, "label": "아늑한"},
        {"value": 1, "label": "탁 트인"}
      ]
    }
  ]
}
```

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
    }
  ]
}
```

shape 예시의 `items`는 축/카테고리 전체 목록이 아니므로, 실제 응답 검증에서는 version 1의 canonical 전체·순서와 비교한다. 원격 GET 실패·불일치를 성공 동기화나 로컬 사전 자동 갱신으로 표시하지 않는다.

## 8. 개인정보·인증·redirect 결정 게이트

- **기존 저장소 정책:** AI는 DB 저장·배달 적격성·접근권한에 권한이 없고, 업무 사용자 JWT를 AI 서비스에 재사용하지 않는다. PRIVATE 본문, LETTER 사진, 요청/응답 원문 multipart는 로그에 남기지 않는다.
- **PROPOSED 보안 경계:** loopback은 호출자 인증 증명이 아니므로 service-to-service 인증을 별도 채택한다. 대상은 고정 `127.0.0.1:8001`로 제한하고 redirect를 허용하지 않는다. loopback HTTP 선택은 외부 네트워크 평문 전송을 허용한다는 뜻이 아니다.
- **미확정 운영 게이트:** service-to-service 인증 방식, 키 전달, secret 공급·회전, AI 제공자의 보존·학습 미사용 정책, 실제 모델별 model/prompt revision과 provenance 일치 기준은 아직 승인되지 않았다.
- **PROPOSED 수용 조건:** AI 담당은 이 PROPOSED schema, multipart, 완전 rank partition 및 오류 코드를 수용하는 실제 서버와 비민감 합성 fixture를 제공해야 한다. 합성 품질 검증 합격 기준도 합의해야 한다.
- **미확정 운영 게이트:** 본문·취향·이미지 한도, HTTP/JSON 크기, 후보·토큰 상한, connect/read/total timeout과 AI 팀 수용 기준은 운영 전 승인 게이트다.

따라서 이 문서는 static contract review에는 준비되었지만, 실제 adapter 구현·실서비스 통합·운영 승인의 증거는 아니다.
