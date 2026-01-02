# Repository Guidelines

## Project Structure & Module Organization
- `backend/` hosts the Spring Boot app with layered `controller/service/util/config` packages under `src/main/java/com/example/onlyoffice`.
- `frontend/` is the Vite + React client; keep UI logic in `src/components/` and bootstrap through `App.tsx`/`main.tsx`.
- `storage/` stores working docs, `docs/` and `resources/` keep samples, and `docker-compose.yml` plus `.env` files define ONLYOFFICE connectivity.

## Build, Test, and Development Commands
```bash
docker-compose up -d
docker-compose logs -f onlyoffice-docs
cd backend && ./gradlew bootRun
./gradlew build test
cd frontend && pnpm install
pnpm dev
pnpm build && pnpm preview
pnpm lint
```
Open `http://localhost:5173?fileName=sample.docx` to verify the full stack.

## Coding Style & Naming Conventions
- Java 21 uses four-space indentation, Lombok for boilerplate, `/api` REST prefixes, PascalCase classes (`EditorController`), camelCase methods, and UPPER_SNAKE_CASE constants.
- TypeScript uses two-space indentation and PascalCase component files; favor function components, keep hooks at the top, and order imports React → libs → local modules.
- ESLint plus `tsc` gate the frontend; Gradle builds fail on formatting or missing Lombok annotations.

## Testing Guidelines
- Backend tests sit in `backend/src/test/java` with JUnit 5; mirror package paths and suffix files with `Test` (for example, `CustomCallbackServiceTest`).
- Cover controllers, services, and JWT helpers, especially editor config builders and callback flows, before requesting review.
- Frontend suites are emerging; add Vitest/React Testing Library specs named `*.test.tsx` whenever you touch UI logic and run them with linting.

## Commit & Pull Request Guidelines
- Follow the existing `type: summary` convention visible in `git log` (`feat`, `fix`, `refactor`, `test`, `docs`, etc.); keep the first line ≤72 characters and describe scope in the body when needed.
- Each PR must describe motivation, list backend/frontend impact, and note any configuration changes (e.g., new `.env` keys or Docker volumes); attach screenshots if the UI changes.
- Link the relevant GitHub issue or ticket, keep diffs focused, and ensure `storage/` or other generated artifacts remain untracked.

## Security & Configuration Tips
- Secrets such as `onlyoffice.secret` stay in `.env` or Docker env vars; never commit plaintext keys.
- Ensure ONLYOFFICE containers reach the backend via `host.docker.internal:8080`, and confirm write access in `storage/` (`ls -la storage/`) before collaborative tests.
- When sharing configs, redact JWTs and rotate the secret with `openssl rand -hex 32` if leakage is suspected.
