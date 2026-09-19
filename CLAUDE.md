# CLAUDE.md

This file provides guidance for work in this backend repository.

## Project status

"감정지도" (Emotion Map) stores place-based experiences and delivers anonymous LETTERs to matching users. Users may keep a LETTER as an independent PRIVATE copy. Product policy is in `docs/MVP_PLAN.md`; the data contract is `docs/ERD.md`; the complete API contract is `docs/API_SPEC.md` and `docs/openapi.yaml`.

The current code contains the UUID persistence cutover and migrated existing callers, not the complete MVP. Scheduling, AI classification/moderation/tie-breaking, full pagination/error/idempotency contracts and operator tooling are not implemented. Never describe a planned contract or test scenario as an implemented/verified feature.

- Business API paths MUST be `/v1/{endpoint}`. Do not retain unversioned or `/api/...` aliases. Swagger/OpenAPI and Actuator are separate infrastructure paths.
- Entities map `app_users`, `email_password_credentials`, `user_preference_versions`, `places`, `place_categories`, `memories`, `memory_categories`, `daily_selections`, `letter_deliveries`, `reports`, and `image_uploads`.
- UUID IDs and scalar FK fields; category IDs/axes are smallint. `Instant` maps timestamptz; service dates use `LocalDate` in Asia/Seoul. Composite keys preserve category slots and user/date selections.
- Four mandatory axes use -1/+1. Direct manual creation records USER sources, NOT_RUN analyses and PENDING moderation. Never invent AI success, safe approval, preferences or delivery history.
- ContentStatus: ACTIVE/HIDDEN/DELETED; DistributionType: LETTER/PRIVATE; OriginKind: DIRECT/LETTER_COPY. Soft deletion preserves delivery/like history and independent copies. No source-copy FK, log pair, response cache or shared image file.
- Reaction, emotion-tag and embedding models are removed. Reintroducing vector infrastructure is not a prerequisite for the four-axis design.

## Stack

- Spring Boot 4.1.0, Java 21 LTS, Gradle Wrapper 9.0.0, Kotlin DSL.
- PostgreSQL 17, Spring Data JPA, Flyway; `ddl-auto: validate` only.
- Immutable historical V1 still requires pgvector during initial migration. Current entities do not use vector or hibernate-vector.
- Lombok: `@Getter`, protected no-args constructor, `@Builder`; no `@Setter`/`@Data`. DTOs use Java records. Follow `lombok.config`.
- Spring Security and jjwt: only `POST /v1/auth/login` is a public business API. JWT subject and `@AuthenticationPrincipal` are UUID. Check current ACTIVE/onboarding state, not only token validity.
- Passwords use BCrypt in separate `email_password_credentials`. No public signup. Initial credentials require a secure internal provisioning procedure; never seed shared plaintext passwords.
- springdoc-openapi 3.1.x: `/swagger-ui.html`, `/v3/api-docs` describe actual controllers. Static `docs/openapi.yaml` describes the wider product contract.
- Images: local UUID paths under `app.storage.upload-dir`. `POST /v1/images` returns an owner-bound staged imageId; `GET /v1/memories/{id}/image` authorizes against the experience. No public raw-key image endpoint. JPEG/PNG are decoded, bounded and re-encoded.

## Architecture

Single Gradle module, feature packages under `team4.emotionmap`: account / memory / place / letter / report / media / catalog / platform / contracts. Each feature owns its Entity, Repository, Service, Controller and DTO.

Every module may depend on `contracts` (shared ports, value objects and errors); `contracts` must not depend on application modules. `catalog` owns `/v1/config`, `/v1/atmosphere-axes` and `/v1/place-categories`.

Module dependencies must remain acyclic and restricted to the exact public contracts in `architecture/ModuleArchitectureTest`. Shared same-DB transactions deliberately use a small set of explicit model/repository/service contracts; this does not make every public type a cross-module API. Review new dependencies and update the explicit allowlist, never grant wildcard package access. Platform may depend on contracts, not business modules.

Error handling: throw `contracts.error.ContractError` (with an `ErrorCode` from API_SPEC §10);
`platform.web.GlobalExceptionHandler` turns it into the `ApiError` body with `Cache-Control`,
`WWW-Authenticate`, `Retry-After`, and `X-Request-Id`. JSON is strict (Jackson 3): duplicate keys,
unknown properties, and scalar coercion (`1.0`, `"1"`, `123`→string) are rejected; 4-axis
`Atmospheres` is validated at the token level by `platform.web.json.AtmospheresJson`.
Service limits/radius come only from `app.service.*` (`ServiceConfigSource`); no defaults in code.
See `docs/plan/BE1_STAGE0_REPORT.md` for the current contract inventory and coordination list.

`MemoryReadAccess` is owned by memory and implemented by letter. `AccountAccessGuard` is owned by platform and implemented by account. Platform must not import business entities or repositories. Do not split one transactional use case into internal HTTP calls or independent commits.

URL nesting does not determine ownership: place-memory lookup and protected memory-image retrieval belong to memory. JPA scalar UUID FKs do not replace SQL foreign keys; Flyway owns RESTRICT constraints and indexes.

## Migration safety

- Never edit an already applied migration, including V1.
- V2 transitions only EMPTY legacy tables. It locks all six tables and fails without deleting data if any contains records. Use a separate empty development DB, not an automatic reset or guessed backfill.
- V3 seeds eight fixed categories. Check latest migration numbers before adding another file; coordinate shared numbering and FK changes.
- Run PostgreSQL constraints against a dedicated local test DB after all migrations: `psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/test/resources/db/erd_constraints.sql`.

## Commands

Run from the repository root (`KOSSCCHTHON-Team4/Backend`).

```bash
brew install postgresql@17 pgvector
brew services start postgresql@17
./gradlew bootRun     # default local profile; needs actual DB
./gradlew test        # DB-less unit and architecture tests
./gradlew build      # tests and executable JAR
```

Profiles: `SPRING_PROFILES_ACTIVE=local|ci|prod`; default local. Override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. The ci profile disables DB auto-configuration for tests and is not a standalone application boot profile. Inject `JWT_SECRET` and deployment secrets via environment variables. See `ARCHITECTURE.md` for setup and feature boundaries.

## Key policies

- Use **graphify for code analysis** before targeted source inspection. Keep generated graphs outside tracked project files unless explicitly requested. Graph extraction alone is not compilation or behavioral verification.
- No Docker. Local PostgreSQL only. CI runs DB-less tests; DB-backed checks run separately on local machines.
- Never upload DB dumps or real customer data. Synthetic/anonymized fixtures only. Flyway schema code may be committed; database dumps may not.
- Never commit credentials, tokens or provider keys. Do not log passwords, private text, upload paths or source-copy ID pairs.
- Work on task branches and publish PRs; do not push directly to main.
