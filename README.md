# Backend — 감정지도 (사사삭)

장소에 감정 기억(Memory)을 남기고, 지도에서 보고, 편지로 배달받는 서비스의 백엔드.

> 상세 설계·컨벤션은 [`ARCHITECTURE.md`](./ARCHITECTURE.md), 개발 가이드는 [`CLAUDE.md`](./CLAUDE.md) 참고.

## 기술 스택

| 구분 | 사용 |
|------|------|
| 언어/런타임 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 4.1 |
| 빌드 | Gradle (Kotlin DSL) + Wrapper 9.0 |
| DB | PostgreSQL 17 + **pgvector** (벡터 유사도 검색) |
| 영속성 | Spring Data JPA (`hibernate-vector`), Flyway 마이그레이션 |
| 인증 | Spring Security + JWT (jjwt) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 보일러플레이트 | Lombok |

- DB 는 각자 로컬에 설치(Docker 미사용). CI 는 DB 비연결 단위테스트만 수행.
- 시크릿(JWT_SECRET, DB 접속정보, 임베딩 키)은 환경변수로 주입 — 커밋 금지.

## 데이터 모델 (ERD)

```
User(id, nickname, email, password_hash, home_lat, home_lng, created_at)
Place(id, name, lat, lng)
Memory(id, user_id, place_id, content, image_path, visibility, emotion_tag, embedding vector, status, created_at)
Reaction(id, user_id, memory_id, created_at)          -- (user, memory) UNIQUE
LetterDelivery(id, receiver_id, memory_id, score, delivered_at, read_at)
Report(id, reporter_id, memory_id, reason, created_at)
```

- `emotion_tag` : 서버가 본문(content)에서 **Claude 로 자동 추출** (요청에서 안 받음).
- `embedding`   : `vector(1024)`, 임베딩 모델(추후 확정)로 생성. Claude 는 임베딩 모델이 아님.
- `visibility`  : `LETTER` / `PRIVATE` · `status` : `ACTIVE` / `HIDDEN` / `DELETED`

## 이미지 처리

- 이미지는 **선택 항목**(본문 필수). Object Storage 대신 **로컬 경로 저장**(MVP).
- DB 에는 UUID **key** 만 저장(`memory.image_path`), 원본 경로/파일명 비노출.
- **응답 타입 분리** — 게시글 조회는 JSON(이미지는 key/url), 이미지는 바이너리:
  - `POST /api/images` (multipart) → `{ key, url }` (JSON)
  - `GET /api/images/{key}` → 이미지 바이너리
- 업로드 검증(확장자/크기) + path traversal 방어 포함.

## API 개요

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/auth/signup`, `/auth/login` | 회원가입 / 로그인(JWT 발급) | 공개 |
| GET | `/users/me` | 내 정보 | 필요 |
| PATCH | `/users/me/location` | 홈 위치 수정 | 필요 |
| GET | `/places?bbox=minLng,minLat,maxLng,maxLat` | 지도 영역 내 핀 | 필요 |
| GET | `/places/{id}/memories` | 장소의 기억 목록 | 필요 |
| POST | `/memories` | 기억 저장(AI 태깅·필터·임베딩) | 필요 |
| GET / DELETE | `/memories/{id}` | 조회 / 삭제 | 필요 |
| POST | `/memories/{id}/reactions` | 반응 추가 | 필요 |
| GET | `/letters` | 추천 편지 목록 | 필요 |
| PATCH | `/letters/{id}/read` | 편지 읽음 | 필요 |
| POST | `/reports` | 신고 | 필요 |

- `signup`/`login` 외 모든 엔드포인트는 `Authorization: Bearer <token>` 필요.
- 전체 명세는 실행 후 Swagger UI(`/swagger-ui.html`)에서 확인.

## 빠른 시작

```bash
# 로컬 PostgreSQL 17 + pgvector 준비 (최초 1회)
brew install postgresql@17 pgvector && brew services start postgresql@17
psql -d postgres -c "CREATE USER emotionmap WITH PASSWORD 'emotionmap';"
psql -d postgres -c "CREATE DATABASE emotionmap OWNER emotionmap;"

# 실행 / 테스트 (레포 루트)
./gradlew bootRun     # 기본 프로필 = local
./gradlew test        # DB 비연결 단위테스트
```

## 협업 규칙

- `main` 직접 push 금지 — feature 브랜치(`feat/*`, `fix/*`) + PR 로만 병합.
- Flyway 마이그레이션: `V{버전}__{설명}.sql`, 만들기 전 `git pull` 로 최신 번호 확인.
- 자세한 규칙은 [`ARCHITECTURE.md`](./ARCHITECTURE.md) 참고.
