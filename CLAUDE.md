# ONLYOFFICE Document Editor Demo

Spring Boot + React application integrating ONLYOFFICE Document Server.

**✨ Type-safe ONLYOFFICE SDK 1.7.0 Integration**

## Architecture

```
┌─────────────┐
│  React 18   │  Port 5173 - User interface (Vite)
│  TypeScript │
└──────┬──────┘
       │
┌──────┴──────┐
│ Spring Boot │  Port 8080 - API & file storage
│   Backend   │  + ONLYOFFICE SDK (Type-safe Config & Callbacks)
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

# 3. Start frontend (port 5173)
cd frontend && pnpm install && pnpm dev

# 4. Open browser
http://localhost:5173?fileName=sample.docx
```

## Project Structure

- `backend/` - Spring Boot API (Java 21) with ONLYOFFICE SDK 1.7.0
  - `backend/storage/` - Document files storage
  - See `backend/CLAUDE.md` for SDK integration details
- `frontend/` - React 18 + Vite (TypeScript)
  - See `frontend/CLAUDE.md` for details
- `docker-compose.yml` - PostgreSQL + MinIO + ONLYOFFICE
- `.env.example` - Environment variables template

## How It Works

1. Frontend requests editor config from backend
2. Backend generates type-safe Config using SDK ConfigService
3. SDK JwtManager signs the config with JWT
4. Frontend renders ONLYOFFICE editor with config
5. User edits document
6. ONLYOFFICE sends type-safe Callback (Status enum: SAVE, FORCESAVE, etc.)
7. Backend processes callback and saves document

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

