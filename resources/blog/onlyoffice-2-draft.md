# ONLYOFFICE 2편: SDK + MinIO로 문서 편집 시스템 만들기

## 📄 시리즈 정보

- **1편**: ONLYOFFICE 기본 연동 (수동 Config, 로컬 파일)
- **2편**: SDK + MinIO + JWT로 실제 동작하는 시스템 구축 ← 현재 글
- **3편**: 보안 강화 + 프로덕션 준비 (예정)

---

## 🎯 2편에서 만들 것

1편에서는 ONLYOFFICE를 연동하는 최소한의 코드를 작성했지만, 여러 한계가 있었습니다:

- ❌ 파일이 로컬 디스크에만 저장 (서버 재시작하면 사라짐)
- ❌ Config JSON을 수동으로 작성 (유지보수 어려움)
- ❌ 문서 메타데이터 관리 불가능
- ❌ UI 없음 (Postman으로만 테스트)

2편에서는 이런 문제를 해결하고 **실제로 동작하는** 문서 편집 시스템을 만듭니다:

✅ **MinIO**: S3 호환 스토리지로 파일 영구 저장
✅ **ONLYOFFICE SDK**: 공식 Java SDK로 Config 자동 생성
✅ **PostgreSQL**: 문서 메타데이터 DB 관리
✅ **JWT**: Callback 보안 (기본)
✅ **Next.js UI**: 브라우저에서 파일 업로드 → 편집 → 저장 가능

> **주의**: 2편은 "동작"에 집중합니다. 보안(파일 검증, Saga 패턴)은 3편에서 다룹니다.

---

## 🏗️ 아키텍처

### As-Is (1편)

```
Next.js → Spring Boot → ONLYOFFICE
               ↓
          Local Disk
```

### To-Be (2편)

```
┌─────────────┐
│  Next.js 16 │  Port 3000 - 문서 목록 + 에디터 페이지
│  React 19   │
└──────┬──────┘
       │ REST API
┌──────┴──────┐
│ Spring Boot │  Port 8080 - API + ONLYOFFICE SDK
│ + SDK       │
└──┬───┬──────┘
   │   │
   │   └─────→ PostgreSQL (문서 메타데이터)
   │
   └─────────→ MinIO (파일 저장소)

ONLYOFFICE (Port 9980) → Callback (JWT 서명)
```

---

## 📦 구현 순서

### Part 1: Infrastructure (Docker Compose)
- PostgreSQL, MinIO, ONLYOFFICE 컨테이너 설정
- 볼륨 마운트로 데이터 영속성 확보

### Part 2: Backend - 기본 구조
- Spring Boot Dependencies (SDK, MinIO, JPA, JWT)
- application.yml 설정
- Document Entity + Repository

### Part 3: Backend - Storage & SDK
- MinIO Storage Service (업로드, Presigned URL)
- ONLYOFFICE SDK Manager (JwtManager, DocumentManager)
- Editor Config Service (Config JSON 자동 생성)

### Part 4: Backend - REST API
- GET /api/documents (목록)
- POST /api/documents/upload (업로드)
- DELETE /api/documents/{id} (삭제)
- GET /api/documents/{id}/config (에디터 설정)
- POST /api/callback (저장 콜백)

### Part 5: Frontend
- Next.js 16 + TanStack Query 설정
- API Service + Hooks (useDocuments, useUploadDocument...)
- Document List Page (HTML table - 기본)
- Editor Page (ONLYOFFICE 렌더링)

---

## 💻 Part 1: Infrastructure Setup

### docker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQL - 문서 메타데이터
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: onlyoffice_demo
      POSTGRES_USER: demo
      POSTGRES_PASSWORD: demo123
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U demo"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MinIO - S3 호환 파일 저장소
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"   # API
      - "9001:9001"   # Console
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ONLYOFFICE Document Server
  onlyoffice:
    image: onlyoffice/documentserver:latest
    ports:
      - "9980:80"
    environment:
      - JWT_ENABLED=true
      - JWT_SECRET=your-secret-key-min-32-characters-xxxxxxxxxxxx
    volumes:
      - onlyoffice_data:/var/www/onlyoffice/Data

volumes:
  postgres_data:
  minio_data:
  onlyoffice_data:
```

### 실행 및 확인

```bash
docker-compose up -d

# 확인
docker-compose ps
# postgres, minio, onlyoffice 모두 healthy여야 함

# MinIO 콘솔 접속
open http://localhost:9001
# minioadmin / minioadmin 로그인
```

✅ **체크포인트 1**: 3개 컨테이너 정상 실행

---

## 💻 Part 2: Backend 기본 구조

### build.gradle

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // PostgreSQL
    runtimeOnly 'org.postgresql:postgresql'

    // ONLYOFFICE SDK (중요!)
    implementation 'com.onlyoffice:docs-integration-sdk-java:1.5.0'

    // JWT
    implementation 'com.auth0:java-jwt:4.4.0'

    // MinIO (AWS S3 SDK)
    implementation 'io.minio:minio:8.5.7'
}
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/onlyoffice_demo
    username: demo
    password: demo123
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

server:
  port: 8080
  # ONLYOFFICE Callback용 (Docker에서 접근 가능한 주소)
  baseUrl: http://host.docker.internal:8080

# MinIO 설정
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: documents

# ONLYOFFICE 설정
onlyoffice:
  url: http://localhost:9980
  secret: your-secret-key-min-32-characters-xxxxxxxxxxxx  # docker-compose.yml과 동일
```

### Document Entity

```java
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String fileName;      // 원본 파일명
    private String fileType;      // docx, xlsx, pptx
    private Long fileSize;        // 바이트
    private String s3Path;        // MinIO 저장 경로

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

```bash
./gradlew bootRun
# 서버 실행 후 로그 확인
# JPA가 documents 테이블 자동 생성
```

✅ **체크포인트 2**: Spring Boot 서버 8080 포트 실행

---

## 💻 Part 3: MinIO & ONLYOFFICE SDK

### MinIO Configuration

```java
@Configuration
public class MinIOConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }
}
```

### MinIO Storage Service

```java
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    // 파일 업로드
    public String uploadFile(MultipartFile file) throws Exception {
        String objectName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build()
        );

        return objectName;
    }

    // Presigned URL 생성 (ONLYOFFICE가 파일 다운로드용)
    public String generatePresignedUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .object(objectName)
                .expiry(1, TimeUnit.HOURS)  // 1시간 유효
                .build()
        );
    }
}
```

### ONLYOFFICE SDK Configuration

```java
@Configuration
public class OnlyOfficeConfig {

    @Value("${onlyoffice.secret}")
    private String jwtSecret;

    @Bean
    public JwtManager jwtManager() {
        return new JwtManager(jwtSecret);
    }

    @Bean
    public DocumentManager documentManager() {
        return new DocumentManager();
    }
}
```

### Editor Config Service

```java
@Service
@RequiredArgsConstructor
public class EditorConfigService {

    private final DocumentRepository documentRepository;
    private final MinioService minioService;
    private final JwtManager jwtManager;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    public Config generateEditorConfig(String documentId) throws Exception {
        // 1. 문서 조회
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));

        // 2. MinIO Presigned URL 생성
        String presignedUrl = minioService.generatePresignedUrl(doc.getS3Path());

        // 3. ONLYOFFICE Config 객체 생성 (SDK 사용!)
        Config config = Config.builder()
            .documentType(getDocumentType(doc.getFileType()))
            .document(Config.Document.builder()
                .title(doc.getFileName())
                .url(presignedUrl)
                .fileType(doc.getFileType())
                .key(doc.getId())  // 문서 고유 키
                .build())
            .editorConfig(Config.EditorConfig.builder()
                .mode(Mode.EDIT)
                .callbackUrl(serverBaseUrl + "/api/callback")
                .build())
            .build();

        // 4. JWT 서명
        String token = jwtManager.createToken(config);
        config.setToken(token);

        return config;
    }

    private DocumentType getDocumentType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "docx", "doc" -> DocumentType.WORD;
            case "xlsx", "xls" -> DocumentType.CELL;
            case "pptx", "ppt" -> DocumentType.SLIDE;
            default -> DocumentType.WORD;
        };
    }
}
```

---

## 💻 Part 4: REST API

### Document Controller

```java
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final EditorConfigService editorConfigService;

    // 문서 목록
    @GetMapping
    public List<DocumentDTO> getDocuments() {
        return documentService.getAllDocuments();
    }

    // 파일 업로드
    @PostMapping("/upload")
    public DocumentDTO uploadDocument(@RequestParam("file") MultipartFile file) {
        return documentService.uploadDocument(file);
    }

    // 문서 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // 에디터 설정 (ONLYOFFICE Config)
    @GetMapping("/{id}/config")
    public Config getEditorConfig(@PathVariable String id) throws Exception {
        return editorConfigService.generateEditorConfig(id);
    }
}
```

### Document Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MinioService minioService;

    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAll().stream()
            .map(DocumentDTO::from)
            .toList();
    }

    public DocumentDTO uploadDocument(MultipartFile file) {
        try {
            // 1. MinIO에 업로드
            String s3Path = minioService.uploadFile(file);

            // 2. DB에 메타데이터 저장
            Document doc = Document.builder()
                .fileName(file.getOriginalFilename())
                .fileType(getFileExtension(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .s3Path(s3Path)
                .build();

            doc = documentRepository.save(doc);
            return DocumentDTO.from(doc);

        } catch (Exception e) {
            // 간단한 롤백 (3편에서 Saga 패턴으로 개선)
            throw new RuntimeException("Upload failed", e);
        }
    }

    public void deleteDocument(String id) {
        Document doc = documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Document not found"));

        try {
            minioService.deleteFile(doc.getS3Path());
            documentRepository.delete(doc);
        } catch (Exception e) {
            throw new RuntimeException("Delete failed", e);
        }
    }
}
```

### Callback Controller (저장 처리)

```java
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final MinioService minioService;
    private final DocumentRepository documentRepository;
    private final JwtManager jwtManager;

    @PostMapping("/api/callback")
    public Map<String, Integer> handleCallback(
        @RequestBody CallbackRequest request,
        @RequestHeader("Authorization") String authHeader
    ) {
        // 1. JWT 검증
        String token = authHeader.replace("Bearer ", "");
        if (!jwtManager.verify(token)) {
            return Map.of("error", 1);  // 실패
        }

        // 2. status=2 (저장 완료)일 때만 처리
        if (request.getStatus() == 2) {
            try {
                // ONLYOFFICE가 제공한 URL에서 파일 다운로드
                InputStream fileStream = downloadFromUrl(request.getUrl());

                // MinIO에 덮어쓰기
                Document doc = documentRepository.findById(request.getKey())
                    .orElseThrow();
                minioService.updateFile(doc.getS3Path(), fileStream);

            } catch (Exception e) {
                return Map.of("error", 1);
            }
        }

        return Map.of("error", 0);  // 성공
    }
}
```

✅ **체크포인트 3**: Postman으로 API 테스트 성공

---

## 💻 Part 5: Frontend (기본 버전)

### Next.js 프로젝트 설정

```bash
cd frontend
pnpm install @tanstack/react-query
```

### app/layout.tsx (QueryClient Provider)

```tsx
'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';

export default function RootLayout({ children }) {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <html lang="ko">
      <body>
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      </body>
    </html>
  );
}
```

### lib/api.ts (API 함수)

```typescript
const API_BASE = 'http://localhost:8080';

export const api = {
  async getDocuments() {
    const res = await fetch(`${API_BASE}/api/documents`);
    return res.json();
  },

  async uploadDocument(file: File) {
    const formData = new FormData();
    formData.append('file', file);

    const res = await fetch(`${API_BASE}/api/documents/upload`, {
      method: 'POST',
      body: formData,
    });
    return res.json();
  },

  async deleteDocument(id: string) {
    await fetch(`${API_BASE}/api/documents/${id}`, {
      method: 'DELETE',
    });
  },

  async getEditorConfig(id: string) {
    const res = await fetch(`${API_BASE}/api/documents/${id}/config`);
    return res.json();
  },
};
```

### hooks/useDocuments.ts

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

export function useDocuments() {
  return useQuery({
    queryKey: ['documents'],
    queryFn: api.getDocuments,
  });
}

export function useUploadDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.uploadDocument,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.deleteDocument,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
```

### app/page.tsx (문서 목록 - 기본 버전)

```tsx
'use client';

import { useDocuments, useUploadDocument, useDeleteDocument } from '@/hooks/useDocuments';
import Link from 'next/link';

export default function DocumentListPage() {
  const { data: documents, isLoading } = useDocuments();
  const uploadMutation = useUploadDocument();
  const deleteMutation = useDeleteDocument();

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      uploadMutation.mutate(file);
    }
  };

  const handleDelete = (id: string) => {
    if (window.confirm('삭제하시겠습니까?')) {
      deleteMutation.mutate(id);
    }
  };

  if (isLoading) return <div>Loading...</div>;

  return (
    <div style={{ padding: '20px' }}>
      <h1>문서 목록</h1>

      {/* 파일 업로드 */}
      <input
        type="file"
        accept=".docx,.xlsx,.pptx"
        onChange={handleFileUpload}
      />

      {/* 문서 목록 (못생긴 HTML table) */}
      <table border="1" style={{ marginTop: '20px', width: '100%' }}>
        <thead>
          <tr>
            <th>파일명</th>
            <th>타입</th>
            <th>크기</th>
            <th>생성일</th>
            <th>작업</th>
          </tr>
        </thead>
        <tbody>
          {documents?.map((doc) => (
            <tr key={doc.id}>
              <td>
                <Link href={`/editor/${doc.id}`}>
                  {doc.fileName}
                </Link>
              </td>
              <td>{doc.fileType}</td>
              <td>{(doc.fileSize / 1024).toFixed(2)} KB</td>
              <td>{new Date(doc.createdAt).toLocaleString()}</td>
              <td>
                <button onClick={() => handleDelete(doc.id)}>
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

### app/editor/[id]/page.tsx (에디터 페이지)

```tsx
'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useEffect, useRef } from 'react';

export default function EditorPage({ params }: { params: { id: string } }) {
  const { data: config, isLoading } = useQuery({
    queryKey: ['editorConfig', params.id],
    queryFn: () => api.getEditorConfig(params.id),
  });

  const editorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (config && window.DocsAPI) {
      new window.DocsAPI.DocEditor('editor', config);
    }
  }, [config]);

  // ONLYOFFICE API 스크립트 로드
  useEffect(() => {
    const script = document.createElement('script');
    script.src = 'http://localhost:9980/web-apps/apps/api/documents/api.js';
    document.body.appendChild(script);

    return () => {
      document.body.removeChild(script);
    };
  }, []);

  if (isLoading) return <div>Loading...</div>;

  return (
    <div>
      <a href="/">← 목록으로</a>
      <div id="editor" style={{ height: '100vh' }} />
    </div>
  );
}
```

✅ **체크포인트 4**: Next.js 서버 3000 포트 실행

---

## 🎬 최종 데모

### 전체 플로우 테스트

1. **파일 업로드**
   ```
   http://localhost:3000 접속
   → 파일 선택 (sample.docx)
   → 업로드
   → 목록에 파일 추가됨
   ```

2. **MinIO 확인**
   ```
   http://localhost:9001 접속 (minioadmin / minioadmin)
   → documents 버킷 확인
   → 업로드된 파일 존재 확인
   ```

3. **문서 편집**
   ```
   목록에서 파일명 클릭
   → 에디터 페이지 이동
   → ONLYOFFICE 에디터 로드
   → 문서 내용 수정
   → Ctrl+S 저장
   ```

4. **저장 확인**
   ```
   브라우저 새로고침
   → 변경사항 유지됨
   → Backend 로그에서 Callback 확인
   ```

✅ **모든 체크포인트 통과!**

---

## 🤔 현재 시스템의 한계 (3편 예고)

### ⚠️ 보안 취약점

```java
// 현재 코드: 파일 업로드 시 검증 없음
public DocumentDTO uploadDocument(MultipartFile file) {
    // 악의적 파일(exe를 docx로 위장) 업로드 가능!
    String s3Path = minioService.uploadFile(file);
    // ...
}
```

**3편에서 해결**:
- Apache Tika로 매직 바이트 검증
- MIME Type 확인
- Path Traversal 방지

### ⚠️ 트랜잭션 원자성

```java
// 현재 코드: MinIO 업로드 성공 → DB 저장 실패 시?
String s3Path = minioService.uploadFile(file);  // 성공
doc = documentRepository.save(doc);  // 실패하면?
// → MinIO에 파일만 남고 DB 레코드 없음 (고아 파일)
```

**3편에서 해결**:
- Saga 패턴으로 보상 트랜잭션
- 실패 시 MinIO 파일 자동 삭제

### ⚠️ 동시성 문제

```java
// 현재 코드: 여러 사용자가 동시에 저장하면?
@PostMapping("/api/callback")
public Map<String, Integer> handleCallback(...) {
    // Race condition 발생 가능
}
```

**3편에서 해결**:
- CallbackQueueService로 순차 처리
- Pessimistic Lock

### 🎨 UI 개선

현재 UI는 못생겼습니다 (HTML table, window.confirm).

**3편에서 해결**:
- shadcn/ui로 세련된 디자인
- TanStack Table (정렬, 필터링)
- Optimistic Update (삭제 즉시 반영)

---

## 📝 정리

### 2편에서 배운 것

1. **MinIO**: S3 호환 스토리지로 파일 영구 저장
2. **ONLYOFFICE SDK**: Config JSON 자동 생성 (수동 작성 탈피)
3. **PostgreSQL**: 문서 메타데이터 DB 관리
4. **JWT**: Callback 기본 보안
5. **Next.js + TanStack Query**: 서버 상태 관리

### 다음 글 (3편 예고)

1. **보안 강화**: Apache Tika, 파일 검증, Path Traversal 방지
2. **Saga 패턴**: 분산 트랜잭션 원자성 보장
3. **동시성 제어**: Callback 순차 처리, Pessimistic Lock
4. **UI 고도화**: shadcn/ui, TanStack Table, Optimistic Update
5. **모니터링**: Spring Actuator, Swagger

---

## 📚 참고 자료

- [ONLYOFFICE Java SDK](https://github.com/ONLYOFFICE/docs-integration-sdk-java)
- [MinIO 공식 문서](https://min.io/docs/minio/linux/index.html)
- [TanStack Query](https://tanstack.com/query/latest)

---

**GitHub**: [onlyoffice-demo](https://github.com/taez224/onlyoffice-demo)

**Milestone**: [2편 - Basic Implementation](https://github.com/taez224/onlyoffice-demo/milestone/1)