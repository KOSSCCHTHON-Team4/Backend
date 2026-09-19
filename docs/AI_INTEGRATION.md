# AI 연동 가이드 (Backend ↔ AI FastAPI)

- 대상: 백엔드/AI/프론트 개발자
- 범위: 별도 AI 서버(FastAPI, Claude)를 백엔드가 HTTP 로 호출하는 연동. `contracts/ai` 포트의 HTTP 구현.
- 원칙: **두 저장소 코드를 합치지 않는다.** AI 는 `:8000`, 백엔드는 `:8080` 별도 프로세스. 백엔드는 AI 서버만 부르고 Claude 키는 갖지 않는다.

---

## 0. 아키텍처

```
Frontend(3000) → Backend(8080)
                   └─ AnalysisPort / ModerationPort / PreferenceTieBreakPort  (contracts/ai)
                        └─ provider=mock → Mock*Adapter (고정 규칙, 키 불필요)
                        └─ provider=http → Http*Adapter ──HTTP──▶ AI FastAPI(8000) ──▶ Claude(국민대 게이트웨이)
```

- 포트는 이미 정의돼 있고, provider 값으로 구현이 교체된다. `MemoryService` 등 상위 로직은 포트만 의존하므로 바뀌지 않는다.
- `app.ai.provider=mock`(기본): 실제 모델 호출 없이 고정 규칙. `=http`: 실제 AI 서버 호출.

---

## 1. AI 서버 실행 (담당: AI, 로컬 재현용)

AI 저장소(`KOSSCCHTHON-Team4/AI`)를 백엔드 옆에 clone.

```bash
git clone https://github.com/KOSSCCHTHON-Team4/AI.git 감정지도-AI
cd 감정지도-AI
cp .env.example .env         # 아래 키 입력 (파일에만, 커밋 금지)
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000     # 문서 http://localhost:8000/docs
curl -s localhost:8000/health         # anthropic_key_set:true 확인
```

`.env` 필수 값 (국민대 게이트웨이):
```
ANTHROPIC_API_KEY=<발급받은 키>        # 절대 커밋 금지, .gitignore 로 제외됨
ANTHROPIC_BASE_URL=https://ai.cs.kookmin.ac.kr
CLAUDE_MODEL=claude-haiku-4-5
MODERATION_PROVIDER=llm                # 안전검사도 Claude 사용
AI_SERVICE_TOKEN=                      # 설정 시 백엔드도 같은 값 필요(선택)
```

> 보안: 키는 AI 서버 `.env` 에만 둔다. 백엔드/프론트/커밋 어디에도 넣지 않는다. 채팅·로그·PR 에 노출되면 즉시 재발급(rotate).

---

## 2. 백엔드를 실제 AI 서버에 붙이기 (담당: 백엔드)

AI 서버가 `:8000` 에 떠 있는 상태에서, 백엔드를 http provider 로 실행한다.

```bash
cd 감정지도   # 백엔드
AI_PROVIDER=http AI_BASE_URL=http://localhost:8000 ./gradlew bootRun
# AI_SERVICE_TOKEN 을 AI 서버에 설정했다면 동일 값도 함께 주입
```

- `AI_PROVIDER=http` 이면 `Http*Adapter` 3종이 활성화되고 `Mock*Adapter` 는 자동 비활성(`matchIfMissing`).
- `app.ai.base-url` 이 비어 있으면 부팅 시 명확한 오류로 실패한다(잘못된 설정 조기 발견).
- 기본(`AI_PROVIDER` 미설정 또는 mock)은 AI 서버 없이도 개발 가능.

관련 설정 키 (`application.yml` / `application-local.yml`):
```yaml
app:
  ai:
    provider: ${AI_PROVIDER:mock}          # local 기본 mock
    base-url: ${AI_BASE_URL:http://localhost:8000}
    service-token: ${AI_SERVICE_TOKEN:}    # AI 서버와 공유 비밀(선택)
    timeout: ${AI_TIMEOUT:PT8S}
```

---

## 3. 포트 ↔ AI 엔드포인트 매핑 (실제 계약)

> 노션 기획안의 `vibe:[-0.9,...]` 실수 벡터가 아니라, **실제 배포된 AI 서버 계약**(4축 -1/+1/null, 카테고리 code)을 기준으로 구현했다.

| 백엔드 포트 | AI 엔드포인트 | 요청 | 응답 핵심 | 어댑터 |
|---|---|---|---|---|
| `AnalysisPort.analyze` | `POST /ai/analyze` | `{content}` | `atmospheres{CROWD_LEVEL/SPATIAL_FEEL/COMPANY_FIT/STAY_STYLE: -1/1/null}`, `categories[code]`, `atmosphereStatus`, `categoryStatus`, `suggestedTitle`, `model` | `HttpAnalysisAdapter` |
| `ModerationPort.moderate` | `POST /ai/moderate` | `{content, imageDataUrl?}` | `decision(APPROVED/REVIEW_REQUIRED/REJECTED/ERROR)`, `categories[]` | `HttpModerationAdapter` |
| `PreferenceTieBreakPort.rank` | `POST /ai/tiebreak` | `{preferenceText, candidates[{id,content}]}` | `status`, `topIds[]`, `scores{}` | `HttpTieBreakAdapter` |

### 매핑 규칙 / 주의점

- **analyze**: 축 값은 `-1`/`+1` 만 인정(그 외·0·null 은 근거 없음 → null, 작성자 입력 필요). 카테고리는 정확히 일치하는 8종 code 만 최대 3개. AI 상류 실패·형식 불량·연결 실패는 예외가 아니라 `AnalysisResult.failed()`(200 + FAILED).
- **moderate**: 이미지가 있으면 재인코딩 바이트를 `data:<mime>;base64,...` 로 전송(외부 URL 금지). `decision` 을 그대로 verdict 로 매핑. 상류 실패는 `ERROR`(배달 승인 아님). `categories`(짧은 코드)만 reasonCodes 로, 본문 문장은 넣지 않는다.
- **tiebreak (중요)**: AI 는 LLM 이 후보 id 를 그대로 복제해 점수에 실어 돌려줘야 한다. **UUID 는 LLM 이 정확히 복제하지 못해** AI 가 `FAILED("유효한 점수 없음")` 를 반환한다. 그래서 어댑터가 후보에 짧은 별칭(`c0,c1,...`)을 부여해 보내고, 응답 `topIds`(별칭)를 UUID 로 역매핑한다. 결과 `rankGroups=[공동1위, 나머지]` 분할이며, 소비자(BE2)는 `validateFor(request)` 로 검증 후 1위 그룹에서 무작위로 고른다. SKIPPED/FAILED·무효·연결 실패는 모두 `TieBreakResult.failed()`.
- **장애 구분**: AI 상류 실패는 결과 객체(failed/ERROR), 우리 쪽 어댑터 설정 오류만 `AiAdapterException`(503).

---

## 4. 연동 검증

### 4-1. AI 서버 단독 (curl)
```bash
curl -s -X POST localhost:8000/ai/analyze -H 'Content-Type: application/json' \
  -d '{"content":"창가 자리에 앉아 혼자 조용히 오래 책을 읽기 좋은 아늑한 카페였어요."}'
# → atmospheres 4축 -1, categories ["CAFE"], atmosphereStatus SUCCEEDED
```

### 4-2. 어댑터 단위 테스트 (DB·AI 서버 불필요, CI 안전)
```bash
./gradlew test --tests "team4.emotionmap.memory.ai.HttpAdaptersTest"
```
MockRestServiceServer 로 AI 응답을 흉내 내어 변환·실패 계약을 검증한다(analyze 전축/부분/서버오류, moderate 판정/오류, tiebreak 별칭매핑/비성공).

### 4-3. 실제 Claude end-to-end (검증 완료)
AI 서버 실행 + `AI_PROVIDER=http` 로 어댑터를 붙이면 실제 응답이 온다. 확인된 결과:
- ANALYZE: SUCCEEDED, 4축 [-1,-1,-1,-1], CAFE
- MODERATE: APPROVED
- TIEBREAK: Valid, 조용한 카페 후보 선택

---

## 5. 아직 남은 백엔드 작업 (이 연동 밖)

어댑터(포트 구현)는 완료됐지만, 이를 실제로 호출하는 상위 기능은 별도 구현이 필요하다.

- `POST /v1/memories/analyze` 컨트롤러·서비스(A04) + analysisToken 발급/검증 — 현재 엔드포인트 없음.
- 배달 전 안전 검사·`available_at` 활성화(A07) — moderation 결과를 저장/상태 전이.
- 일일 선정 스케줄러·4축 점수 매칭·동률 시 tiebreak 호출(B01/B02) — 노션의 코사인 1차/2차 매칭 로직은 여기(BE2)에서 구현.

> 노션 문서의 매칭(코사인 유사도, 1차/2차 판정, 알림 문구)은 백엔드 매칭/스케줄러 담당(BE2) 범위이며, AI 어댑터 연동과는 별개다.

---

## 6. 개발 프롬프트 (다음 작업자용 요약)

```
컨텍스트: 감정지도 백엔드(Spring Boot 4, Java 21)와 AI 서버(FastAPI, Claude)를 HTTP 로 연동함.
포트(contracts/ai): AnalysisPort/ModerationPort/PreferenceTieBreakPort. provider=http 이면
memory/ai 의 Http*Adapter 가 :8000 AI 서버를 호출(RestClient, SimpleClientHttpRequestFactory 명시 필수).
tiebreak 은 후보 id 를 c0,c1.. 별칭으로 보내고 topIds 를 UUID 로 역매핑함(LLM UUID 복제 한계 회피).
Claude 키는 AI 서버 .env 에만. 백엔드엔 base-url/service-token 만.

다음 작업: (1) POST /v1/memories/analyze 컨트롤러+서비스로 AnalysisPort 연결, analysisToken 서명/검증.
(2) A07 moderation 저장·available_at 전이. (3) B01/B02 매칭·스케줄러에서 PreferenceTieBreakPort 사용.
계약: 축 -1/+1만 유효(그외 null), 상류 실패는 결과객체(failed/ERROR)로 예외 아님, tiebreak 결과는
validateFor(request) 통과해야 사용, 최종 선택/무작위는 BE 책임. 실제 AI 계약 기준(노션의 실수벡터 아님).
```
