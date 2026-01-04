# ONLYOFFICE Backend - Spring Boot Application

This is the backend service for the ONLYOFFICE document editor integration, built with Spring Boot 3.3 and Java 21.

## Overview

The backend provides REST APIs for:
- Generating ONLYOFFICE editor configurations with JWT signing
- Serving files to the Document Server
- Handling document save callbacks
- Managing document metadata and storage

## Quick Start

### Prerequisites
- Java 21
- Docker & Docker Compose (for PostgreSQL, MinIO, ONLYOFFICE Document Server)
- Gradle (wrapper included)

### Running the Application

```bash
# 1. Copy environment template
cp ../.env.example ../.env

# 2. Start infrastructure services
docker-compose up -d

# 3. Run the backend
./gradlew bootRun

# 4. Access the API
curl http://localhost:8080/api/config?fileKey=YOUR_FILE_KEY
```

## Configuration

### Environment Variables

The application uses `.env` file for configuration. Key variables:

```env
# Database
POSTGRES_DB=onlyoffice_demo
POSTGRES_USER=demo
POSTGRES_PASSWORD=demo_password

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin_password
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET=onlyoffice-documents

# ONLYOFFICE
ONLYOFFICE_URL=http://localhost:9980
JWT_SECRET=your-secret-key-must-be-at-least-32-characters-long-for-hs256
```

### Application Configuration

See `src/main/resources/application.yml` for detailed configuration options.

## MinIO Presigned URL Feature

### Overview

The application supports two modes for serving files to ONLYOFFICE Document Server:

1. **Backend Proxy Mode (Default)**: Document Server downloads files via backend API (`/files/{fileKey}`)
2. **MinIO Presigned URL Mode**: Document Server downloads files directly from MinIO using presigned URLs

### Configuration

Edit `application.yml`:

```yaml
onlyoffice:
  # Enable presigned URL mode
  use-presigned-urls: false  # Default: false (backend proxy)

  # MinIO endpoint accessible from ONLYOFFICE Document Server
  # Docker environment: http://minio:9000 (internal docker network)
  # Production: Use publicly accessible MinIO URL
  minio-external-endpoint: http://minio:9000
```

### Comparison

| Feature | Backend Proxy | Presigned URL |
|---------|---------------|---------------|
| **Security** | ✅ Higher (MinIO not exposed) | ⚠️ Lower (time-limited URLs) |
| **Performance** | ⚠️ Lower (extra hop) | ✅ Higher (direct download) |
| **Backend Load** | ⚠️ Higher (proxies all downloads) | ✅ Lower (only generates URLs) |
| **URL Expiry** | ✅ No expiry | ⚠️ 1 hour expiry |
| **Network Requirement** | Simple | MinIO must be accessible from Document Server |

### Enabling Presigned URLs

**For Docker Deployment:**

1. Ensure `docker-compose.yml` has network configuration:
   ```yaml
   services:
     minio:
       networks:
         - onlyoffice-net
     onlyoffice-docs:
       networks:
         - onlyoffice-net

   networks:
     onlyoffice-net:
       driver: bridge
   ```

2. Update `application.yml`:
   ```yaml
   onlyoffice:
     use-presigned-urls: true
     minio-external-endpoint: http://minio:9000
   ```

3. Restart the backend:
   ```bash
   ./gradlew bootRun
   ```

**For Kubernetes/Production:**

1. Set `minio-external-endpoint` to the publicly accessible MinIO URL
2. Ensure ONLYOFFICE Document Server can reach the MinIO endpoint
3. Consider using internal service DNS names for better performance and security

### URL Expiry Considerations

Presigned URLs expire after **1 hour** (configurable via `minio.presigned-url-expiry`).

- ✅ Sufficient for most document editing sessions
- ⚠️ Users who keep documents open longer than 1 hour may experience download errors
- 💡 Backend proxy mode has no such limitation

### Troubleshooting

**Symptom: ONLYOFFICE shows "Download failed" error**

1. Check Document Server logs:
   ```bash
   docker logs onlyoffice-docs
   ```

2. Verify MinIO accessibility from Document Server container:
   ```bash
   docker exec onlyoffice-docs curl http://minio:9000
   ```

3. Check presigned URL format in browser DevTools → Network tab
   - Should start with `http://minio:9000` (not `http://localhost:9000`)
   - Should contain `X-Amz-Signature` parameter

**Symptom: Presigned URL has `localhost:9000` instead of `minio:9000`**

- Verify `onlyoffice.minio-external-endpoint` is set correctly in `application.yml`
- Check `CustomUrlManager.replaceMinioEndpoint()` logic
- Restart the backend to pick up configuration changes

**Symptom: "Network error" after 1 hour**

- This is expected behavior for presigned URL mode
- Options:
  - Increase `minio.presigned-url-expiry` in `application.yml`
  - Switch to backend proxy mode (`use-presigned-urls: false`)
  - Implement auto-refresh mechanism (future enhancement)

## Architecture

### Component Overview

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Frontend   │────▶│   Backend    │────▶│  MinIO      │
│  (React)    │     │ (Spring Boot)│     │  (Storage)  │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ PostgreSQL   │
                    │ (Metadata)   │
                    └──────────────┘
                           ▲
                           │
                    ┌──────────────┐
                    │  ONLYOFFICE  │
                    │    Docs      │
                    └──────────────┘
```

### Key Components

- **Controllers**: REST API endpoints (`/api/*`, `/files/*`, `/callback`)
- **Services**: Business logic (DocumentService, MinioStorageService, EditorConfigService)
- **SDK Customizations**: Custom implementations of ONLYOFFICE SDK managers
- **Utilities**: Key generation, validation helpers

### Document Key System

The application uses a two-tier key system:

1. **fileKey (Immutable)**: UUID-based permanent identifier
   - Example: `550e8400-e29b-41d4-a716-446655440000`
   - Used for file downloads, callbacks, and external references

2. **editorKey (Versioned)**: Combination of fileKey + version
   - Format: `{fileKey}_v{version}`
   - Example: `550e8400-e29b-41d4-a716-446655440000_v1`
   - Used as ONLYOFFICE `document.key` to track editing sessions
   - Version increments on each SAVE (not FORCESAVE)

## API Endpoints

### GET /api/config

Generate ONLYOFFICE editor configuration.

**Request:**
```
GET /api/config?fileKey=550e8400-e29b-41d4-a716-446655440000
```

**Response:**
```json
{
  "documentServerUrl": "http://localhost:9980",
  "config": {
    "document": {
      "key": "550e8400-e29b-41d4-a716-446655440000_v1",
      "url": "http://localhost:8080/files/550e8400-e29b-41d4-a716-446655440000",
      "fileType": "docx",
      "title": "Document.docx"
    },
    "editorConfig": {
      "callbackUrl": "http://localhost:8080/callback?fileKey=550e8400-e29b-41d4-a716-446655440000",
      "user": {
        "id": "demo-user-1",
        "name": "Demo User"
      }
    },
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### GET /files/{fileKey}

Download file content.

**Request:**
```
GET /files/550e8400-e29b-41d4-a716-446655440000
```

**Response:**
- Content-Type: application/octet-stream
- Binary file data

### POST /callback

Handle document save callbacks from ONLYOFFICE Document Server.

**Request:**
```
POST /callback?fileKey=550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <JWT>

{
  "status": 2,
  "url": "https://documentserver/url/to/edited/file.docx",
  "key": "550e8400-e29b-41d4-a716-446655440000_v1"
}
```

**Response:**
```json
{
  "error": 0
}
```

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests CustomUrlManagerTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Test Structure

Tests are organized in `src/test/java` mirroring the main package structure:

- `controller/*Test.java`: Controller layer tests
- `service/*Test.java`: Service layer tests
- `sdk/*Test.java`: SDK customization tests
- `util/*Test.java`: Utility class tests

### Test Coverage Goals

- **Unit Tests**: 80%+ coverage
- **Integration Tests**: Critical flows (upload, edit, save)
- **Edge Cases**: Error handling, validation, edge cases

## Building for Production

```bash
# Build JAR
./gradlew build

# Run JAR
java -jar build/libs/onlyoffice-backend-0.0.1-SNAPSHOT.jar

# Build Docker image (if Dockerfile exists)
docker build -t onlyoffice-backend .
```

## Security Considerations

### JWT Secret

- **Minimum length**: 32 characters (HS256 requirement)
- **Sharing**: Must match between backend and ONLYOFFICE Document Server
- **Rotation**: Use `openssl rand -hex 32` to generate new secrets
- **Storage**: Never commit `.env` to version control

### File Access

- File downloads are validated against Document metadata
- Path traversal prevention via `KeyUtils` sanitization
- MinIO credentials stored in environment variables

### Presigned URLs

- Time-limited (1 hour default)
- Scoped to specific object
- No additional authentication required (signature in URL)
- Consider HTTPS in production

## Development Tips

### Code Style

- Java: 4-space indentation, PascalCase classes, camelCase methods
- Package structure: `controller`, `service`, `sdk`, `util`, `entity`, `config`
- Use Lombok for boilerplate reduction
- Follow Spring Boot best practices

### Common Tasks

**Add new ONLYOFFICE SDK customization:**
1. Create class extending SDK default implementation
2. Override required methods
3. Register as `@Component`
4. Inject into `OnlyOfficeConfig` if needed

**Add new API endpoint:**
1. Create or update controller in `controller/` package
2. Add request/response DTOs if needed
3. Implement service logic
4. Add unit and integration tests

**Modify document key format:**
1. Update `KeyUtils.generateEditorKey()`
2. Update tests
3. Consider migration strategy for existing documents

## Related Documentation

- [Project README](../README.md) - Project overview and setup
- [ONLYOFFICE Integration Guide](../docs/onlyoffice-integration-guide.md) - Detailed integration documentation
- [PRD v2.0](../docs/prd.md) - Product requirements
- [Frontend README](../frontend/README.md) - Frontend documentation

## License

This project is for demonstration purposes.
