# Backend – Spring Boot + ONLYOFFICE SDK

## Purpose & Stack
- Java 21 + Spring Boot 3.3.0 providing REST APIs at `/api` plus `/files`/`/callback`.
- Gradle builds the service; Lombok reduces boilerplate; JJWT + ONLYOFFICE Java SDK 1.7.0 handle JWT-secured editor configs.
- `storage/` (alongside `build.gradle`) stores working documents; ensure Docker volume permissions allow read/write.

## Run & Test
```bash
cd backend
./gradlew bootRun          # dev server on :8080
./gradlew test             # JUnit 5 suite
./gradlew build            # shaded jar + checks
```
Before running, copy `.env.example` to `.env` at repo root and ensure `onlyoffice.secret` in `src/main/resources/application.yml` matches `JWT_SECRET`. When Dockerized, keep `server.baseUrl` at `http://host.docker.internal:8080`.

## Key Modules
- `config/OnlyOfficeConfig.java`: wires SDK managers (Settings, Document, Url, Permission, User, Callback, Config) plus `EditorConfigService`.
- `controller/EditorController.java`: `GET /api/config?fileKey=` returning SDK-generated config + token.
- `controller/FileController.java`: exposes `/files/{fileKey}` for Document Server downloads.
- `controller/CallbackController.java`: consumes SDK Callback model to download updated docs.
- `service/DocumentService.java`: file I/O inside `storage/`.
- `service/FileMigrationService.java`: utilities for migrating legacy assets (triggered via `MigrationController`).
- `sdk/*`: overrides default SDK managers for custom URLs, document keys, permissions, and callbacks.
- `util/KeyUtils.java`: sanitizes keys (timestamp/UUID) so Document Server versioning works.

## API Surface
| Method | Path | Notes |
| --- | --- | --- |
| `GET /api/config?fileKey=` | Returns `{ documentServerUrl, config }`. Query param may be file name or UUID; service resolves metadata. |
| `GET /files/{fileKey}` | Serves binary content with JWT validation. |
| `POST /callback?fileKey=` | Handles SAVE/FORCESAVE, downloads edited doc from SDK payload URL, and overwrites storage. |
| `POST /api/migrate` | (Optional tooling) bulk-imports files using `FileMigrationService`. |

## Testing Cues
- Tests live under `src/test/java` mirroring the main packages (`controller`, `sdk`, `service`, `util`). Use descriptive method names (`shouldHandleForceSaveStatus`).
- `CustomCallbackServiceTest`, `CustomDocumentManagerTest`, etc., already cover happy paths; extend them when adding logic (e.g., new permission modes or key rules).
- Mock filesystem interactions via temporary directories; avoid touching real `storage/`.

## Review Checklist
1. **Config validity** – `documentUrl`, `callbackUrl`, `key`, and `jwt` must align with Docker hostnames; ensure `CustomUrlManager` updates stay consistent with controllers.
2. **Security** – reject path traversal (see `KeyUtils`), never log secrets, and keep `.env`-sourced properties outside version control.
3. **Callbacks** – `CallbackController` should distinguish between `Status.SAVE`, `FORCESAVE`, errors, and return `{ "error": 0 }` on success.
4. **Storage** – confirm overwrites happen atomically and that migrations or cleanups respect `storage.path`.
5. **SDK upgrades** – prefer extending SDK managers rather than recreating DTOs; update `OnlyOfficeConfig` when new beans are required.

## Troubleshooting
- **JWT mismatch**: `onlyoffice.secret` (application.yml) must equal `JWT_SECRET` in `.env` and docker-compose.
- **Callback failures**: check Docker logs (`docker-compose logs onlyoffice-docs`) and ensure `server.baseUrl` is reachable (`curl http://host.docker.internal:8080/api/health` if added).
- **Permissions issues**: run `ls -la storage/` to verify host/containers share ownership; adjust volume mappings if callbacks cannot write edits.
