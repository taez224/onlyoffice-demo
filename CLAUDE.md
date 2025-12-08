# ONLYOFFICE Document Editor Demo

Spring Boot + Next.js application integrating ONLYOFFICE Document Server.

## Architecture

```
┌─────────────┐
│  Next.js 16 │  Port 3000 - User interface
│  React 19   │
└──────┬──────┘
       │
┌──────┴──────┐
│ Spring Boot │  Port 8080 - API & file storage
│   Backend   │
└──────┬──────┘
       │
┌──────┴──────┐
│ ONLYOFFICE  │  Port 9980 - Document editor
│  (Docker)   │
└─────────────┘
```

## Quick Start

```bash
# 0. Setup environment
cp .env.example .env  # Edit passwords and JWT_SECRET

# 1. Start services (PostgreSQL + MinIO + ONLYOFFICE)
docker-compose up -d

# 2. Start backend (port 8080)
cd backend && ./gradlew bootRun

# 3. Start frontend (port 3000)
cd frontend && pnpm install && pnpm dev

# 4. Open browser
http://localhost:3000?fileName=sample.docx
```

## Project Structure

- `backend/` - Spring Boot API (Java 21)
  - `backend/storage/` - Document files storage
  - See `backend/CLAUDE.md` for details
- `frontend/` - Next.js 16 + React 19 (TypeScript)
  - See `frontend/CLAUDE.md` for details
- `docker-compose.yml` - PostgreSQL + MinIO + ONLYOFFICE
- `.env.example` - Environment variables template

## How It Works

1. Frontend requests editor config from backend
2. Backend generates config with JWT and file URLs
3. Frontend renders ONLYOFFICE editor
4. User edits document
5. ONLYOFFICE saves via callback to backend

## Key Configuration

### .env
```env
JWT_SECRET=<your-secret-key-min-32-chars>
POSTGRES_PASSWORD=<your-password>
MINIO_ROOT_PASSWORD=<your-password>
```

### backend/src/main/resources/application.yml
```yaml
server:
  baseUrl: http://host.docker.internal:8080  # For Docker callback

onlyoffice:
  secret: <must-match-JWT_SECRET>  # From .env
```

### docker-compose.yml
```yaml
environment:
  - JWT_SECRET=${JWT_SECRET}  # From .env
```

