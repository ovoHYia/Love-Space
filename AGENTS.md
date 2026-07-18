# Repository Guidelines

## Project Structure & Module Organization

Love Space is a two-part application. `frontend/` contains the Vue 3 and TypeScript single-page app: pages live in `src/views/`, shared UI in `src/components/`, API calls in `src/api/`, and client state in `src/stores/`. Static assets belong in `frontend/public/`. `backend/` is a Java 17 Spring Boot service organized by layer: REST controllers in `api/`, business logic in `service/`, persistence entities in `domain/`, and Spring Data repositories in `repository/`. Database migrations are ordered Flyway SQL files under `backend/src/main/resources/db/migration/`. Root `scripts/` holds PowerShell workflows; local runtime data is kept under `data/` and must not be committed.

## Build, Test, and Development Commands

- `powershell -ExecutionPolicy Bypass -File scripts/start-dev.ps1` installs missing frontend dependencies and starts Vite plus Spring Boot.
- `powershell -ExecutionPolicy Bypass -File scripts/setup-db.ps1` creates/configures the MySQL database from root `.env` values.
- `powershell -ExecutionPolicy Bypass -File scripts/build.ps1` runs the frontend production build, Maven tests, and packages `outputs/Love-Space-v1.0.jar`.
- `npm run dev` or `npm run build` from `frontend/` starts Vite or type-checks and produces `dist/`.
- `mvn -f backend/pom.xml test` runs backend integration tests using the `test` profile and H2.

## Coding Style & Naming Conventions

Use 2-space indentation in Vue, TypeScript, JSON, and CSS; retain the existing 4-space Java/XML indentation. Name Vue components and views in PascalCase (for example, `DashboardView.vue`), composables in camelCase (for example, `toast.ts`), and Java types in PascalCase. Keep Java packages lowercase beneath `com.lovespace`; use singular entity names and `*Controller`, `*Service`, and `*Repository` suffixes. Follow established import ordering and avoid unrelated formatting changes.

## Testing Guidelines

Add or update tests for backend behavior under `backend/src/test/java/com/lovespace/`. Use descriptive `*Test.java` names and exercise HTTP-facing behavior where practical. Include a Flyway migration for every schema change; migrations must be sequentially named, e.g. `V6__add_event_table.sql`. The frontend has no configured test runner yet, so at minimum run `npm run build` after UI changes.

## Commit & Pull Request Guidelines

This repository has no commit history yet, so use concise imperative Conventional Commit-style subjects such as `feat: add anniversary reminder`. Keep commits focused. Pull requests should explain the user-visible change, note database or configuration impacts, link relevant issues, and include screenshots for UI changes. Never commit `.env`, uploads, logs, build output, or credentials; update `.env.example` when adding a required setting.
