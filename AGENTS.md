# Claude Code Review Guide

## Project Snapshot
- Spring Boot 3.5.8 (Java 21) backend + React 18/Vite frontend embedding ONLYOFFICE Document Server 9.1 via the Java SDK 1.7.0.
- Storage is filesystem-based (`storage/`) and mounted for the Document Server container; docker-compose also provisions PostgreSQL + MinIO for future persistence.
- JWT auth is enforced between backend and Document Server; `.env` defines shared secrets and infra credentials.
- **Documents identified by UUID fileKey** (not fileName); see [Issue #30](https://github.com/taez224/onlyoffice-demo/issues/30) for migration details.

## FileKey Identification System

Documents use **UUID-based fileKey** as the primary identifier:
- **fileKey**: Immutable UUID generated at upload (e.g., `550e8400-e29b-41d4-a716-446655440000`)
- **fileName**: User-provided filename (may be duplicated; used for display/storage paths)
- **editorKey**: Document Server document identifier in format `{fileKey}_v{version}` where version increments after SAVE callbacks

**Benefits**: Unpredictable identifiers (better security), support for duplicate filenames, and scalability. **Migration**: Run `POST /api/admin/migration/files` to generate UUIDs for legacy files in `storage/`.

## Architecture & Flow
1. Frontend requests `GET /api/config?fileKey=550e8400-e29b-41d4-a716-446655440000` (UUID).
2. Backend creates config via SDK `ConfigService`, signs it through `JwtManager`, and returns URLs pointing to `host.docker.internal:8080`.
3. Document Server downloads `/files/{fileKey}`, users edit collaboratively, and callbacks hit `/callback?fileKey={uuid}`.
4. Backend downloads the edited asset from the callback payload and overwrites `storage/{fileName}` while incrementing `editorVersion`.
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
For smoke testing:
1. Upload a file via the backend (or migrate existing files: `POST /api/admin/migration/files`)
2. Note the returned `fileKey` (UUID)
3. Call `http://localhost:5173?fileKey={fileKey}` to open the editor

## Coding & Design Conventions
- Java uses layered `controller/service/sdk/util` packages, Lombok, four-space indent, PascalCase classes, camelCase methods, `/api/*` routes, and `KeyUtils` for sanitized doc keys.
- TypeScript uses two-space indent, PascalCase component files, hooks-first ordering, and ESLint+tsc enforcement.
- Favor SDK abstractions (`CustomSettingsManager`, `CustomDocumentManager`, `CustomUrlManager`) over ad-hoc JSON.

## Review Priorities
- **FileKey correctness**: verify all APIs use UUID fileKey (not fileName); check `editorKey` format is `{fileKey}_v{version}`; ensure fileKey validation via `KeyUtils.isValidKey()`.
- **Config correctness**: verify document URLs, callback URLs, `key` generation, and JWT secret alignment (`onlyoffice.secret` vs `.env JWT_SECRET` vs docker-compose).
- **Security**: ensure no plaintext secrets in code, restrict file names to prevent path traversal, validate fileKey as UUID format, and double-check MinIO/Postgres creds stay in env files.
- **Persistence**: confirm modified documents land in `storage/` with correct fileName; verify `editorVersion` increments after SAVE callbacks (not FORCESAVE); check permissions suit Docker (use `ls -la storage/`).
- **Error handling**: callbacks should handle SAVE (increment version) vs FORCESAVE (no version change) enums distinctly and return `{ "error": 0 }` on success.
- **Frontend**: ensure query params are validated as UUID fileKey before hitting backend and that editor props map to backend config fields (fileKey, not fileName).

## Testing Expectations
- Backend tests live in `backend/src/test/java`; mirror the source package, suffix with `Test`, and cover controller/service logic plus SDK helpers (see `CustomCallbackServiceTest`).
- Frontend specs are being added (`*.test.tsx`); when touching UI, stub ONLYOFFICE config fetches and assert query handling.
- For regressions, run `./gradlew test` and `pnpm lint` minimally; mention additional manual doc-edit verification for complex flows.

## Configuration & Risk Notes
- `server.baseUrl` must point to `http://host.docker.internal:8080` when Dockerized; local dev can use `http://localhost:8080`.
- Rotate JWT secrets with `openssl rand -hex 32` when sharing configs; never commit `.env`.
- docker-compose currently exposes PostgreSQL/MinIO—flag any new ports, volume mappings, or elevation of privileges in reviews.
