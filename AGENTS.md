# Repository Guidelines

## Project Structure & Module Organization

Love Space is a two-part application. `frontend/` contains the Vue 3 and TypeScript single-page app: pages live in `src/views/`, shared UI in `src/components/`, API calls in `src/api/`, and client state in `src/stores/`. Static assets belong in `frontend/public/`. `backend/` is a Java 17 Spring Boot service organized by layer: REST controllers in `api/`, business logic in `service/`, persistence entities in `domain/`, Spring Data repositories in `repository/`, and cross-cutting configuration, security, and time helpers in `config/`, `security/`, and `time/`. Database migrations are ordered Flyway SQL files under `backend/src/main/resources/db/migration/`. Root `scripts/` holds PowerShell workflows. Local uploads, logs, release staging, and generated artifacts live under `data/`, `work/`, `outputs/`, `frontend/dist/`, or `backend/target/`; these paths must not be committed.

## Build, Test, and Development Commands

- `pwsh -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1` validates or installs frontend dependencies, starts Vite plus Spring Boot, and writes logs to `work/logs/`. Use `-SkipInstall` only when the lock-file installation stamp is current.
- `pwsh -ExecutionPolicy Bypass -File .\scripts\setup-db.ps1` creates/configures the MySQL database named by the root `.env` `DB_URL` and requires the `mysql` client.
- `pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1` installs frontend dependencies unless `-SkipInstall` is supplied, runs `npm test`, builds the frontend, runs `mvn clean package`, validates the static frontend inside the JAR, and copies the release JAR to `outputs/Love-Space-v1.0.jar`.
- `pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1 -MySqlTests` additionally enables the real MySQL tests. It uses `MYSQL_TEST_URL` when provided, otherwise the local `love_space_test` database, uses `MYSQL_TEST_PASSWORD` first and falls back to the root `.env` `DB_PASSWORD`, and defaults the username to `root`. The local `_test` database must already exist; this option does not create it.
- When the user requests a build, deployment, release, or complete validation, execute `scripts/build.ps1 -MySqlTests` directly in the workspace and report the result; do not only provide the command for the user to run. Do not treat plain `mvn test` with skipped MySQL checks as complete validation.
- `npm run dev`, `npm test`, or `npm run build` from `frontend/` starts Vite, runs Vitest, or type-checks and produces `dist/`, respectively.
- `mvn -f backend/pom.xml test` runs backend tests with Spring's `test` profile and H2 by default; real MySQL tests remain skipped unless the required `MYSQL_TEST_URL` and `MYSQL_TEST_PASSWORD` process variables are present. Because the Maven build validates the freshness of `frontend/dist/` during `validate`, run `npm run build` first or use `scripts/build.ps1`.

## Coding Style & Naming Conventions

Use 2-space indentation in Vue, TypeScript, JSON, and CSS; retain the existing 4-space Java/XML indentation. Name Vue components and views in PascalCase (for example, `DashboardView.vue`), composables in camelCase (for example, `toast.ts`), and Java types in PascalCase. Keep Java packages lowercase beneath `com.lovespace`; use singular entity names and `*Controller`, `*Service`, and `*Repository` suffixes. Follow established import ordering and avoid unrelated formatting changes.

## Testing Guidelines

Add or update tests for backend behavior under `backend/src/test/java/com/lovespace/`. Use descriptive `*Test.java` names and exercise HTTP-facing behavior where practical. Include a Flyway migration for every schema change; migrations must be sequentially named, e.g. `V18__add_feature.sql`. Frontend unit tests use Vitest; run `npm test` and `npm run build` after UI changes.
Real MySQL tests must target a local database whose name ends with `_test`; do not point them at a production database.

## Commit & Pull Request Guidelines

This repository has an existing commit history. Use concise imperative Conventional Commit-style subjects in Simplified Chinese, such as `feat: 添加纪念日提醒`. Keep commits focused. Pull requests should explain the user-visible change, note database or configuration impacts, link relevant issues, and include screenshots for UI changes. Never commit `.env`, uploads, logs, build or release output, or credentials; update the relevant `.env.example` template when adding a required setting.
