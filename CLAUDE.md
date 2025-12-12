# Claude Code Review Guide

## Project Snapshot
- Spring Boot 3.3 (Java 21) backend + React 18/Vite frontend embedding ONLYOFFICE Document Server 9.1 via the Java SDK 1.7.0.
- Storage is filesystem-based (`storage/`) and mounted for the Document Server container; docker-compose also provisions PostgreSQL + MinIO for future persistence.
- JWT auth is enforced between backend and Document Server; `.env` defines shared secrets and infra credentials.

## Architecture & Flow
1. Frontend requests `GET /api/config?fileName=foo.docx`.
2. Backend creates config via SDK `ConfigService`, signs it through `JwtManager`, and returns URLs pointing to `host.docker.internal:8080`.
3. Document Server downloads `/files/{fileKey}`, users edit collaboratively, and callbacks hit `/callback?fileName=...`.
4. Backend downloads the edited asset from the callback payload and overwrites `storage/{file}`.
Ensure callback URLs remain reachable from inside Docker; regressions here block saving.

## Build & Test Commands
```bash
# Infra
cp .env.example .env
docker-compose up -d && docker-compose logs -f onlyoffice-docs

# Backend
cd backend
./gradlew bootRun
./gradlew test        # unit/integration suite
./gradlew build       # fat JAR + verification

# Frontend
cd frontend
pnpm install
pnpm dev              # local dev server
pnpm build && pnpm preview
pnpm lint
```
Call `http://localhost:5173?fileName=sample.docx` for smoke testing.

## Coding & Design Conventions
- Java uses layered `controller/service/sdk/util` packages, Lombok, four-space indent, PascalCase classes, camelCase methods, `/api/*` routes, and `KeyUtils` for sanitized doc keys.
- TypeScript uses two-space indent, PascalCase component files, hooks-first ordering, and ESLint+tsc enforcement.
- Favor SDK abstractions (`CustomSettingsManager`, `CustomDocumentManager`, `CustomUrlManager`) over ad-hoc JSON.

## Review Priorities
- **Config correctness**: verify document URLs, callback URLs, `key` generation, and JWT secret alignment (`onlyoffice.secret` vs `.env JWT_SECRET` vs docker-compose).
- **Security**: ensure no plaintext secrets in code, restrict file names to prevent path traversal, and double-check MinIO/Postgres creds stay in env files.
- **Persistence**: confirm modified documents land in `storage/` and that permissions suit Docker (use `ls -la storage/`).
- **Error handling**: callbacks should handle SAVE vs FORCESAVE enums distinctly and return `{ "error": 0 }` on success.
- **Frontend**: ensure query params are validated before hitting backend and that editor props map to backend config fields.

## Testing Expectations
- Backend tests live in `backend/src/test/java`; mirror the source package, suffix with `Test`, and cover controller/service logic plus SDK helpers (see `CustomCallbackServiceTest`).
- Frontend specs are being added (`*.test.tsx`); when touching UI, stub ONLYOFFICE config fetches and assert query handling.
- For regressions, run `./gradlew test` and `pnpm lint` minimally; mention additional manual doc-edit verification for complex flows.

## Configuration & Risk Notes
- `server.baseUrl` must point to `http://host.docker.internal:8080` when Dockerized; local dev can use `http://localhost:8080`.
- Rotate JWT secrets with `openssl rand -hex 32` when sharing configs; never commit `.env`.
- docker-compose currently exposes PostgreSQL/MinIO—flag any new ports, volume mappings, or elevation of privileges in reviews.
