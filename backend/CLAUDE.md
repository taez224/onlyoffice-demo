# Backend - Spring Boot API

## Quick Start

```bash
cd backend
./gradlew bootRun  # Runs on port 8080
```

## Key Files

- `EditorController.java` - Generates ONLYOFFICE editor config with JWT
- `CallbackController.java` - Handles document save callbacks (status=2)
- `FileController.java` - Serves files to ONLYOFFICE
- `DocumentService.java` - File I/O operations
- `JwtManager.java` - JWT token signing (HS256)
- `application.yml` - Configuration

## API Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/config?fileName=X` | Returns editor config JSON with JWT |
| `GET /files/{fileName}` | File download (ONLYOFFICE calls this) |
| `POST /callback?fileName=X` | Save callback from ONLYOFFICE |

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

## Document Flow

1. Frontend requests config → EditorController generates config + JWT
2. ONLYOFFICE fetches file → FileController serves from `backend/storage/`
3. User edits and saves → ONLYOFFICE posts to CallbackController
4. Backend downloads edited file and saves to `backend/storage/`

## Common Tasks

**Add file type support**: Edit `EditorController.getDocumentType()` switch statement

**Debug callbacks**: Check `server.baseUrl` is reachable from Docker container

**JWT issues**: Ensure `onlyoffice.secret` matches `docker-compose.yml` JWT_SECRET