# ONLYOFFICE 문서 편집기 통합 데모

Spring Boot 4 + Next.js 16 기반 ONLYOFFICE Document Server 연동 예제 프로젝트입니다.

## 프로젝트 구조

```
onlyoffice-demo/
├── backend/                                     # Spring Boot 백엔드
│   ├── src/main/java/com/example/onlyoffice/
│   │   ├── config/                              # SDK/보안/MinIO 설정
│   │   ├── controller/                          # Document/Callback/File API
│   │   ├── entity/                              # JPA 엔티티 (Hibernate 7 Soft Delete)
│   │   ├── sdk/                                 # ONLYOFFICE SDK 커스텀 매니저
│   │   ├── service/                             # 문서/콜백/저장소 서비스
│   │   └── util/                                # KeyUtils (UUID/Editor Key)
│   ├── src/main/resources/
│   │   └── application.yml                      # 애플리케이션 설정
│   └── build.gradle                             # Gradle 빌드 설정
│
└── frontend/                                    # Next.js 프론트엔드 (App Router)
    ├── src/app/                                 # 라우팅 (page.tsx, layout.tsx)
    │   └── editor/[fileKey]/page.tsx            # 에디터 페이지
    ├── src/components/                          # UI 컴포넌트
    ├── src/hooks/                               # TanStack Query 훅
    ├── src/lib/                                 # API 클라이언트/유틸
    └── next.config.ts                           # /api 리라이트
```

## 주요 기능

### 1. 문서 편집
- Word, Excel, PowerPoint, PDF 등 다양한 문서 포맷 지원
- ONLYOFFICE Document Server를 통한 브라우저 기반 편집
- 실시간 협업 편집 가능

### 2. UUID 기반 fileKey
- 문서는 UUID 기반 `fileKey`로 식별
- 에디터 키는 `{fileKey}_v{version}` 형식 사용

### 3. JWT 기반 보안 (활성화됨)
- ONLYOFFICE Document Server와 백엔드 간 JWT 서명/검증
- `.env`의 `JWT_SECRET`과 `application.yml`의 `onlyoffice.secret` 값이 일치해야 함

### 4. 저장 및 콜백 처리
- SAVE 콜백 시 파일 다운로드 후 저장 및 버전 증가
- FORCESAVE 콜백 시 저장하되 버전은 증가하지 않음

## 기술 스택

### Backend
- Java 21
- Spring Boot 4.0.1
- Gradle
- ONLYOFFICE Java SDK 1.7.0
- JJWT 0.13.0
- PostgreSQL
- MinIO 8.6.0
- Hibernate 7 (Soft Delete)

### Frontend
- Next.js 16.1.1 (App Router)
- React 19.2.3
- TypeScript 5.x
- TanStack Query v5
- Tailwind CSS v4 + shadcn/ui
- Axios
- @onlyoffice/document-editor-react 2.1.1

## 사전 요구사항

1. Java 21 이상
2. Node.js 18 이상 및 pnpm
3. Docker (ONLYOFFICE Document Server 및 인프라 실행용)

## 빠른 시작

### 1단계: 환경 변수 설정

`.env.example`을 복사하여 `.env` 생성:

```bash
cp .env.example .env
```

필수 항목:

```env
POSTGRES_DB=onlyoffice_demo
POSTGRES_USER=demo
POSTGRES_PASSWORD=your-secure-password-here
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=your-minio-password-here
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET=onlyoffice-documents
JWT_SECRET=your-secret-key-must-be-at-least-32-characters-long-for-hs256
```

### 2단계: 인프라 실행

```bash
docker-compose up -d
```

서비스 확인:
- ONLYOFFICE: http://localhost:9980/welcome/
- MinIO Console: http://localhost:9001

### 3단계: Backend 실행

```bash
cd backend
./gradlew bootRun
```

Backend: http://localhost:8080

### 4단계: Frontend 실행

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend: http://localhost:3000

### 5단계: 문서 편집기 열기

`fileKey`를 사용해 에디터를 엽니다:

```
http://localhost:3000/editor/{fileKey}
```

## API 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | /api/documents | 문서 목록 조회 |
| POST | /api/documents/upload | 문서 업로드 |
| DELETE | /api/documents/{fileKey} | 문서 삭제 (Soft Delete) |
| GET | /api/documents/{fileKey}/config | ONLYOFFICE 에디터 설정 조회 |
| GET | /files/{fileKey} | ONLYOFFICE가 호출하는 파일 다운로드 |
| POST | /callback?fileKey={uuid} | ONLYOFFICE 콜백 처리 |
| POST | /api/admin/migration/files | 기존 스토리지 파일 마이그레이션 |
| GET | /api/config?fileKey={uuid} | 레거시 에디터 설정 엔드포인트 |

## 설정 파일

### Backend: application.yml

```yaml
server:
  baseUrl: http://host.docker.internal:8080

onlyoffice:
  url: http://localhost:9980
  secret: ${JWT_SECRET:your-secret-key-must-be-at-least-32-characters-long-for-hs256}
  jwt:
    expiration-hours: 1

storage:
  path: backend/storage

minio:
  endpoint: http://localhost:9000
  accessKey: minioadmin
  secretKey: your-minio-password-here
  bucket: onlyoffice-documents
  presigned-url-expiry: 3600
```

### Frontend: next.config.ts

```typescript
const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";

const nextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};
```

## 파일 식별 및 저장 방식

- `fileKey`: UUID 기반 문서 식별자
- `editorKey`: `{fileKey}_v{version}` 형식
- MinIO 저장 경로: `documents/{fileKey}/{fileName}`
- 레거시 로컬 스토리지 마이그레이션용 디렉터리: `storage.path`

## 문서

- docs/onlyoffice-integration-guide.md
- docs/document-service-saga-pattern.md

## 참고 자료

- https://api.onlyoffice.com/editors/basic
- https://github.com/ONLYOFFICE/DocumentServer
- https://github.com/ONLYOFFICE/docs-integration-sdk-java
