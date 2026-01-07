# Backend – Spring Boot + ONLYOFFICE SDK

## Purpose & Stack
- Java 21 + Spring Boot 4.0.1 (Hibernate 7, Spring Framework 7) providing REST APIs at `/api` plus `/files`/`/callback`.
- Gradle 8.14 builds the service; Lombok reduces boilerplate; JJWT + ONLYOFFICE Java SDK 1.7.0 handle JWT-secured editor configs.
- Jackson 2 backward compatibility via `spring-boot-jackson2` (required for JJWT and ONLYOFFICE SDK).
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
- `service/CallbackQueueService.java`: manages per-document callback queues for sequential/parallel processing control.
- `service/DocumentService.java`: file I/O inside `storage/`; implements pessimistic locking for atomic callback updates.
- `service/FileMigrationService.java`: utilities for migrating legacy assets (triggered via `MigrationController`).
- `sdk/*`: overrides default SDK managers for custom URLs, document keys, permissions, and callbacks.
- `util/KeyUtils.java`: UUID-based fileKey generation and editor key versioning (`{fileKey}_v{version}`); sanitizes keys for Document Server spec compliance.

## API Surface
| Method | Path | Notes |
| --- | --- | --- |
| `GET /api/config?fileKey=` | Returns `{ documentServerUrl, config }`. Query param is a required UUID fileKey (e.g., `550e8400-e29b-41d4-a716-446655440000`). |
| `GET /files/{fileKey}` | Serves binary content with JWT validation. |
| `POST /callback?fileKey=` | Handles SAVE/FORCESAVE, downloads edited doc from SDK payload URL, and overwrites storage. |
| `POST /api/admin/migration/files` | Scans `storage/` and generates UUIDs for legacy files using `FileMigrationService`. |

## FileKey & Document Identification

Documents are now identified by **fileKey** (UUID) rather than fileName:

- **fileKey**: Unique, immutable UUID generated on upload (via `KeyUtils.generateFileKey()`)
- **fileName**: Original user-provided filename (may be duplicated across documents)
- **editorVersion**: Increments after each SAVE callback; enables versioning for Document Server (`editorKey = fileKey_v{version}`)

**Benefits**: Better security (unpredictable identifiers), support for duplicate filenames, and scalability for distributed systems.

**Migration** (Issue #30): Use `POST /api/admin/migration/files` to scan `storage/` and generate fileKeys for legacy files. All new uploads automatically receive UUIDs.

## Hibernate 7 Soft Delete

The `Document` entity uses Hibernate 7's native `@SoftDelete` annotation:

```java
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Document { ... }
```

**Key behaviors**:
- `repository.delete(entity)` automatically sets `deleted_at` timestamp (no manual status update needed)
- All queries automatically filter out soft-deleted records
- Use `repository.restore(id)` native query to undelete records
- Repository methods no longer need `AndDeletedAtIsNull` suffix

**Repository pattern**:
```java
findByFileKey(String fileKey)           // automatically excludes deleted
findWithLockByFileKey(String fileKey)   // with pessimistic lock
findAllByStatus(DocumentStatus status)  // automatically excludes deleted
restore(Long id)                        // native query to undelete
```

## Callback Concurrency Control

ONLYOFFICE Document Server may issue concurrent callback requests (e.g., SAVE/FORCESAVE) for the same document. To prevent race conditions, we employ a **per-document queue architecture**:

- **CallbackQueueService**: Maintains a `Map<String, ExecutorService>` with one single-threaded executor per `fileKey`.
  - **Same document**: callbacks queued sequentially on the document's executor, preventing concurrent writes.
  - **Different documents**: callbacks execute in parallel across separate executors for optimal performance.
  
- **Pessimistic Locking**: `DocumentRepository.findWithLockByFileKey()` acquires `PESSIMISTIC_WRITE` lock (3s timeout) during `processCallbackSave`/`processCallbackForceSave` to ensure atomic file overwrites and version updates.

- **Graceful Shutdown**: Service awaits pending tasks for 30 seconds; forces shutdown if timeout exceeded.

- **Limitation**: Single-JVM architecture. For multi-instance deployments, upgrade to Redis/Kafka-based distributed queues.

**Performance**: Sequential processing of same document + parallel processing of different documents yields ~3× throughput improvement over global sequential queues.

## Testing Cues
- Tests live under `src/test/java` mirroring the main packages (`controller`, `sdk`, `service`, `util`). Use descriptive method names (`shouldHandleForceSaveStatus`).
- Use `@MockitoBean` (not deprecated `@MockBean`) for Spring Boot 4.0 compatibility.
- `CallbackQueueServiceTest` validates sequential (same-document) and parallel (different-document) callback processing, plus error handling and graceful shutdown.
- `CustomCallbackServiceTest`, `CustomDocumentManagerTest`, etc., already cover happy paths; extend them when adding logic (e.g., new permission modes or key rules).
- Mock filesystem interactions via temporary directories; avoid touching real `storage/`.

## Review Checklist
1. **FileKey usage** – ensure all endpoints use UUID fileKey (not fileName); verify `DocumentRepository` queries use `findByFileKey()` for active documents (Hibernate 7 auto-filters deleted).
2. **Config validity** – `documentUrl`, `callbackUrl`, `editorKey` (fileKey + version), and `jwt` must align with Docker hostnames; ensure `CustomUrlManager` updates stay consistent with controllers.
3. **Security** – fileKey validation via `KeyUtils.isValidKey()` (UUID format + ONLYOFFICE spec), reject path traversal, never log secrets, and keep `.env`-sourced properties outside version control.
4. **Callbacks** – `CallbackController` extracts fileKey from query param, distinguishes between `Status.SAVE` (increments editorVersion), `FORCESAVE` (no version increment), errors, and returns `{ "error": 0 }` on success.
5. **Storage** – confirm overwrites happen atomically with pessimistic locks; migrations use `FileMigrationService` to generate UUIDs for legacy files.
6. **SDK upgrades** – prefer extending SDK managers rather than recreating DTOs; update `OnlyOfficeConfig` when new beans are required.

## Troubleshooting
- **JWT mismatch**: `onlyoffice.secret` (application.yml) must equal `JWT_SECRET` in `.env` and docker-compose.
- **Callback failures**: check Docker logs (`docker-compose logs onlyoffice-docs`) and ensure `server.baseUrl` is reachable (`curl http://host.docker.internal:8080/api/health` if added).
- **Permissions issues**: run `ls -la storage/` to verify host/containers share ownership; adjust volume mappings if callbacks cannot write edits.
- **Legacy files without fileKey**: run `POST /api/admin/migration/files` to generate UUIDs for files in `storage/`; idempotent (skips already-migrated documents).
