# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

"감정지도" (Emotion Map) is a hackathon project — users leave a **Memory** (text +
optional photo) at a **Place**; the server derives an emotion tag from the text and
an embedding for vector similarity search. Other domains: Reaction, LetterDelivery
(deliver a memory to a receiver with a score), Report. This is the backend team's
shared starting baseline. See `ARCHITECTURE.md` for the ERD, diagrams, and rationale.

Domain (V1): app_user, place, memory, reaction, letter_delivery, report.

- **emotion_tag** on memory is auto-derived by Claude (sonnet-5) from `content` — NOT
  accepted in the request. **embedding** is produced by a separate embedding model
  (Voyage etc., TBD) — Claude cannot embed. Both are filled server-side via the
  `ai/EmotionTagger` and `ai/Embedder` ports (Optional-injected; no impl yet).
- MemoryStatus: ACTIVE / HIDDEN / DELETED. Visibility: LETTER / PRIVATE.

## Stack

- Spring Boot 4.1.0, Java 21 (LTS), Gradle Wrapper 9.0.0 (**Kotlin DSL**: `*.gradle.kts`)
- **Security**: Spring Security + JWT (jjwt 0.13.x). Only `POST /auth/signup` and
  `POST /auth/login` are public; all other endpoints require `Authorization: Bearer <token>`.
  Controllers read the current user via `@AuthenticationPrincipal Long userId` (the filter
  puts userId as principal). Passwords are BCrypt-hashed in `app_user.password_hash`.
  `JWT_SECRET` env var in CI/prod (never commit). See `ARCHITECTURE.md` §14.
- **API docs**: springdoc-openapi 3.1.x (Swagger UI). Auto-generated from controllers/DTOs.
  `/swagger-ui.html`, `/v3/api-docs`. Metadata in `config/OpenApiConfig`. 3.1.x is the
  Spring Boot 4.x line (2.9.x is for Boot 3.x). See `ARCHITECTURE.md` §13.
- PostgreSQL 17 + **pgvector**. JPA mapping uses **hibernate-vector** (NOT the JDBC-only
  `com.pgvector:pgvector`). Map with `@JdbcTypeCode(SqlTypes.VECTOR) @Array(length=N) float[]`.
- **Lombok** for boilerplate. Entities: `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@Builder`
  only (no `@Setter`/`@Data`). Beans: `@RequiredArgsConstructor` + `@Slf4j`. DTOs: Java `record`.
  Shared settings in `lombok.config` (repo root).
- Flyway for DB migrations (`src/main/resources/db/migration/`); V1 enables the
  `vector` extension and adds a `vector(1024)` embedding column
- Package root: `team4.emotionmap` (domain / repository / dto / service / controller / storage / ai / config)
- Images: optional (content required). Stored on the local filesystem under
  `app.storage.upload-dir` with a **UUID key**; DB (`memory.image_path`) holds only the key.
  Two APIs: `POST /api/images` (multipart → JSON `{key,url}`) and `GET /api/images/{key}`
  (binary). Memory JSON carries `imageKey`/`imageUrl`, never binary. Upload is validated
  (extension + size) with path-traversal defense. See `ARCHITECTURE.md` §12.
- Embedding model is **not yet chosen**. `app.embedding.*` in `application.yml` is a
  placeholder only; the model/provider/key are injected later via `EMBEDDING_*` env
  vars (`EMBEDDING_API_KEY` never committed). The `vector(1024)` dimension is a
  temporary value — change it (new migration) once the real model is picked. Note:
  Anthropic has no first-party embedding model, so a separate provider is required.

## Key policies

- **No Docker.** Each developer installs PostgreSQL 17 + pgvector locally.
- **Never upload DB data/schema dumps to GitHub.** CI does NOT connect to any DB;
  it runs compile + DB-less unit tests only. DB-backed integration tests are run
  manually on each developer's local machine.
- Secrets are never committed — the `prod` profile references env vars only.

## Commands

Backend-only repo (`KOSSCCHTHON-Team4/Backend`): project root = repo root. Run all
Gradle commands from the repo root. CI/release workflows live under `.github/`.

```bash
# one-time local setup (macOS example) — see ARCHITECTURE.md for details
brew install postgresql@17 pgvector && brew services start postgresql@17

./gradlew bootRun     # run app (default profile = local; needs local DB)
./gradlew test        # DB-less unit tests (no Docker, no DB needed)
./gradlew build       # build runnable JAR
```

- Active profile via `SPRING_PROFILES_ACTIVE` (local | ci | prod). Default: local.
- Local DB connection overridable via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`.

## Architecture

Layered: Controller → Service → Repository (Spring Data JPA) → PostgreSQL+pgvector.
Schema is owned by Flyway; JPA runs `ddl-auto: validate` only. Columns are
snake_case. The `ci` profile disables DataSource/JPA/Flyway auto-config so the
context and unit tests run without a database. See `ARCHITECTURE.md`.

## Constraints

- Organization rule: never paste customer personal data into the chat or upload
  files containing it. Any emotion/location data used for development must be
  synthetic or anonymized.
