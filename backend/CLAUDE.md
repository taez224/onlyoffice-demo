# Backend - Spring Boot API

**✨ ONLYOFFICE SDK 1.7.0 Integration - Type-safe Config & Callbacks**

> ONLYOFFICE SDK Github: https://github.com/ONLYOFFICE/docs-integration-sdk-java

> ONLYOFFICE Integration Example Github: https://github.com/ONLYOFFICE/document-server-integration/tree/master/web/documentserver-example/java
## Quick Start

```bash
cd backend
./gradlew bootRun  # Runs on port 8080
```

## Key Files

### SDK Integration (Type-safe)
- `config/OnlyOfficeConfig.java` - SDK Beans configuration (SettingsManager, DocumentManager, UrlManager, JwtManager, ConfigService)
- `sdk/CustomSettingsManager.java` - Provides application settings to SDK
- `sdk/CustomDocumentManager.java` - Document key generation, format database
- `sdk/CustomUrlManager.java` - File, callback, goback URL generation
- `service/EditorConfigService.java` - SDK ConfigService wrapper

### Controllers & Services
- `EditorController.java` - Returns type-safe editor config via SDK
- `CallbackController.java` - Handles SDK Callback model (Status enum: SAVE, FORCESAVE, etc.)
- `FileController.java` - Serves files to ONLYOFFICE
- `DocumentService.java` - File I/O operations
- `util/KeyUtils.java` - Document key sanitization
- `application.yml` - Configuration

## API Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/config?fileName=X` | Returns type-safe SDK Config object with JWT |
| `GET /files/{fileName}` | File download (ONLYOFFICE calls this) |
| `POST /callback?fileName=X` | Processes SDK Callback model (Status enum) |

## Critical Configuration (application.yml)

```yaml
server:
  baseUrl: http://host.docker.internal:8080  # Must be reachable from Docker

onlyoffice:
  url: http://localhost:9980
  secret: <must-match-.env-JWT_SECRET>  # Min 32 chars, from .env file

storage:
  path: storage  # Relative to backend/ directory → backend/storage/
```

**Note**:
- Storage path is relative to the backend directory. Files are stored in `backend/storage/`.
- `onlyoffice.secret` must match the `JWT_SECRET` value in `.env` file.

## Document Flow (SDK-based)

1. Frontend requests config → EditorConfigService creates type-safe Config via SDK
2. SDK ConfigService generates Config object with proper types
3. SDK JwtManager signs the config automatically
4. ONLYOFFICE fetches file → FileController serves from `backend/storage/`
5. User edits and saves → ONLYOFFICE posts SDK Callback with Status enum
6. CallbackController parses SDK Callback model (no more magic numbers!)
7. Backend downloads edited file and saves to `backend/storage/`

## Common Tasks

**Add file type support**: SDK's DocumentManager provides format database - use `documentManager.isEditable(fileId)`, `isViewable()`, etc.

**Debug callbacks**: Check `server.baseUrl` is reachable from Docker container. Callback uses SDK Callback model with Status enum.

**JWT issues**:
- Ensure `onlyoffice.secret` matches `docker-compose.yml` JWT_SECRET
- SDK JwtManager handles signing/verification automatically
- Check `CustomSettingsManager.getSetting("files.docservice.secret")`

**Customize SDK Managers**:
- `CustomSettingsManager` - Modify `getSetting()` to add new settings
- `CustomDocumentManager` - Override `getDocumentKey()` for custom key logic
- `CustomUrlManager` - Override URL generation methods (`getFileUrl()`, `getCallbackUrl()`)

## SDK Benefits

✅ **Type Safety** - Compile-time validation, no ClassCastException
✅ **No Magic Numbers** - Status.SAVE instead of `status == 2`
✅ **IDE Support** - Auto-completion for Config and Callback fields
✅ **Format Database** - Built-in file type detection and conversion support
✅ **Maintainability** - SDK updates handle API changes automatically