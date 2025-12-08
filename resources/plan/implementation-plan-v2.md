# ONLYOFFICE Demo 2편 - Implementation Plan

> PRD v2.2 기반 구현 계획 (3주)
>
> **확정 사항:**
> - Frontend: Next.js 16 완전 재작성 (Vite 삭제)
> - Backend: Spring Boot 3.5.8, ONLYOFFICE SDK 1.7.0 완전 통합

---

## 📋 현재 상태 (1편 완료)

| 영역 | 현재 | 대상 |
|-----|------|------|
| Backend | Spring Boot 3.5.8, 로컬 파일 시스템 | + PostgreSQL, MinIO, ONLYOFFICE SDK 1.7.0 |
| Frontend | React 18 + Vite (단일 에디터) | Next.js 16 + App Router + TanStack |
| Infrastructure | ONLYOFFICE only | + PostgreSQL + MinIO (볼륨) |

---

## 🗓️ Week 1: Infrastructure + Security Foundation

### Day 1-2: Infrastructure Setup (Issue #1)

**수정 파일:** `docker-compose.yml`

**작업 내용:**
- PostgreSQL 16 추가
  - Port: 5432
  - Volume: `postgres_data:/var/lib/postgresql/data`
  - Healthcheck: `pg_isready`
- MinIO 추가
  - Ports: 9000 (API), 9001 (Console)
  - Volume: `minio_data:/data`
  - Healthcheck: curl health API
- ONLYOFFICE 볼륨 추가
  - Volume: `/var/lib/onlyoffice`

**검증:**
```bash
docker-compose up -d
# PostgreSQL: psql -h localhost -U demo
# MinIO Console: http://localhost:9001
```

---

### Day 2-3: Backend Dependencies (Issue #2)

**수정 파일:** `backend/build.gradle`

**추가 의존성:** (v2.2 최신화)
```gradle
// ONLYOFFICE SDK
implementation 'com.onlyoffice:docs-integration-sdk:1.7.0'

// Database
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'org.postgresql:postgresql'

// MinIO
implementation 'io.minio:minio:8.6.0'

// Security & Validation
implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
implementation 'org.apache.tika:tika-core:3.2.3'

// Monitoring
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-core'
```

---

### Day 3: Application Configuration (Issue #3)

**수정 파일:** `backend/src/main/resources/application.yml`

**설정 구조:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/${POSTGRES_DB:onlyoffice}
    username: ${POSTGRES_USER:demo}
    password: ${POSTGRES_PASSWORD:demo123}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: documents

onlyoffice:
  url: ${ONLYOFFICE_URL:http://localhost:9980}
  secret: ${ONLYOFFICE_JWT_SECRET}  # 32자 이상 필수

server:
  baseUrl: ${SERVER_BASE_URL:http://host.docker.internal:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

---

### Day 3-4: Document Entity + Repository (Issue #4)

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/entity/Document.java`
- `backend/src/main/java/com/example/onlyoffice/entity/DocumentStatus.java`
- `backend/src/main/java/com/example/onlyoffice/repository/DocumentRepository.java`
- `backend/src/main/resources/db/migration/V1__create_documents_table.sql`

**Entity 구조:**
```java
@Entity
@Table(name = "documents")
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(unique = true, nullable = false)
    private String fileKey;  // ONLYOFFICE 문서 키 (불변)

    private String fileType;      // docx, xlsx, pptx, pdf
    private String documentType;  // word, cell, slide
    private Long fileSize;
    private String storagePath;   // MinIO object key

    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Version
    private Integer version = 1;  // 동시성 제어

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;  // Soft delete
    private String createdBy = "anonymous";
}

public enum DocumentStatus {
    PENDING, ACTIVE, DELETED
}
```

**Repository:**
```java
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status);
    Optional<Document> findByFileKey(String fileKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "3000")})
    Optional<Document> findWithLockById(Long id);
}
```

---

### Day 4-5: MinIO Storage Service (Issue #5)

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/config/MinioConfig.java`
- `backend/src/main/java/com/example/onlyoffice/service/StorageService.java` (interface)
- `backend/src/main/java/com/example/onlyoffice/service/MinioStorageService.java`

**MinioConfig:**
```java
@Configuration
public class MinioConfig {
    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.US_EAST_1)
            .build();
    }
}
```

**StorageService Interface:**
```java
public interface StorageService {
    void uploadFile(String objectKey, InputStream stream, long size, String contentType);
    InputStream downloadFile(String objectKey);
    void deleteFile(String objectKey);
    String getPresignedUrl(String objectKey, Duration expiry);
}
```

---

### Day 5: Security Services (Issue #6)

**삭제/교체 파일:**
- `backend/src/main/java/com/example/onlyoffice/util/JwtManager.java` → SDK Manager로 교체

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/service/FileSecurityService.java`
- `backend/src/main/java/com/example/onlyoffice/config/SecurityValidationConfig.java`
- `backend/src/main/java/com/example/onlyoffice/exception/FileValidationException.java`

**FileSecurityService:**
```java
@Service
public class FileSecurityService {
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_UNCOMPRESSED_SIZE = 1024 * 1024 * 1024; // 1GB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "docx", "xlsx", "pptx", "pdf"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/pdf"
    );

    private final Tika tika = new Tika();

    public void validateFile(MultipartFile file) {
        validateFileName(file.getOriginalFilename());
        validateExtension(file.getOriginalFilename());
        validateFileSize(file.getSize());
        validateMimeType(file);
        // ZIP 폭탄 검증은 압축 파일일 경우에만
    }

    private void validateFileName(String fileName) {
        // Path Traversal 방지
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new FileValidationException("Invalid file name");
        }
    }
}
```

**시크릿 검증 (시작 시):**
```java
@Configuration
public class SecurityValidationConfig {
    @Value("${onlyoffice.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validateSecrets() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT Secret must be at least 32 characters");
        }

        List<String> blacklist = List.of("change-me", "secret", "password", "your-secret");
        if (blacklist.stream().anyMatch(jwtSecret.toLowerCase()::contains)) {
            throw new IllegalStateException("JWT Secret contains blacklisted value");
        }
    }
}
```

---

## 🗓️ Week 2: Backend Services + API

### Day 6-8: Document Service + Saga Pattern (Issue #7)

**수정 파일:**
- `backend/src/main/java/com/example/onlyoffice/service/DocumentService.java` - 전면 리팩토링

**Saga 패턴 - 업로드:**
```java
@Service
@Transactional
public class DocumentService {

    public Document uploadDocument(MultipartFile file) {
        // Step 1: 파일 검증
        fileSecurityService.validateFile(file);

        // Step 2: DB에 PENDING 상태로 저장
        Document doc = Document.builder()
            .fileName(sanitizeFileName(file.getOriginalFilename()))
            .fileKey(generateFileKey())
            .fileType(getExtension(file.getOriginalFilename()))
            .documentType(getDocumentType(file.getOriginalFilename()))
            .fileSize(file.getSize())
            .storagePath("documents/" + generateStoragePath(file))
            .status(DocumentStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
        doc = documentRepository.save(doc);

        try {
            // Step 3: MinIO 업로드
            storageService.uploadFile(
                doc.getStoragePath(),
                file.getInputStream(),
                file.getSize(),
                file.getContentType()
            );

            // Step 4: ACTIVE로 변경
            doc.setStatus(DocumentStatus.ACTIVE);
            doc.setUpdatedAt(LocalDateTime.now());
            return documentRepository.save(doc);

        } catch (Exception e) {
            // 보상 트랜잭션: DB 레코드 삭제
            documentRepository.delete(doc);
            throw new DocumentUploadException("Upload failed", e);
        }
    }

    public void deleteDocument(Long id) {
        // Step 1: 비관적 락으로 조회 (3초 타임아웃)
        Document doc = documentRepository.findWithLockById(id)
            .orElseThrow(() -> new DocumentNotFoundException(id));

        // Step 2: Soft delete
        doc.setStatus(DocumentStatus.DELETED);
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);

        try {
            // Step 3: MinIO 삭제
            storageService.deleteFile(doc.getStoragePath());
        } catch (Exception e) {
            // 보상 트랜잭션: 상태 복구
            doc.setStatus(DocumentStatus.ACTIVE);
            doc.setDeletedAt(null);
            documentRepository.save(doc);
            throw new DocumentDeleteException("Delete failed", e);
        }
    }
}
```

---

### Day 8-9: Editor Config Service + SDK 통합 (Issue #8)

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/sdk/CustomSettingsManager.java`
- `backend/src/main/java/com/example/onlyoffice/sdk/CustomDocumentManager.java`
- `backend/src/main/java/com/example/onlyoffice/sdk/CustomCallbackManager.java`
- `backend/src/main/java/com/example/onlyoffice/service/EditorConfigService.java`

**SDK Manager 구현:**
```java
@Component
public class CustomSettingsManager implements SettingsManager {
    @Value("${onlyoffice.url}")
    private String documentServerUrl;

    @Value("${onlyoffice.secret}")
    private String secret;

    @Override
    public String getSetting(String name) {
        return switch (name) {
            case "files.docservice.url.site" -> documentServerUrl;
            case "files.docservice.secret" -> secret;
            default -> null;
        };
    }
}
```

**EditorConfigService:**
```java
@Service
public class EditorConfigService {
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ConfigService configService;  // ONLYOFFICE SDK

    public EditorConfigResponse getConfig(Long documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // SDK로 Config 생성
        Config config = configService.createConfig(
            doc.getFileKey(),
            doc.getFileName(),
            doc.getDocumentType(),
            doc.getFileType()
        );

        // MinIO Presigned URL 설정 (1시간)
        String fileUrl = storageService.getPresignedUrl(
            doc.getStoragePath(),
            Duration.ofHours(1)
        );
        config.getDocument().setUrl(fileUrl);

        // Callback URL 설정
        config.getEditorConfig().setCallbackUrl(serverBaseUrl + "/api/callback");

        // JWT 서명 (SDK 사용)
        String token = jwtManager.createToken(config);
        config.setToken(token);

        return new EditorConfigResponse(config, onlyofficeUrl);
    }
}
```

---

### Day 9: REST API Endpoints (Issue #9)

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/controller/DocumentController.java`
- `backend/src/main/java/com/example/onlyoffice/dto/DocumentDto.java`
- `backend/src/main/java/com/example/onlyoffice/dto/DocumentUploadResponse.java`
- `backend/src/main/java/com/example/onlyoffice/exception/GlobalExceptionHandler.java`

**API 엔드포인트:**
```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    // GET /api/documents - 목록 조회
    @GetMapping
    public List<DocumentDto> listDocuments() {
        return documentService.listActiveDocuments().stream()
            .map(DocumentDto::from)
            .toList();
    }

    // POST /api/documents - 업로드
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestPart("file") MultipartFile file) {
        Document doc = documentService.uploadDocument(file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(DocumentDto.from(doc));
    }

    // GET /api/documents/{id} - 상세 조회
    @GetMapping("/{id}")
    public DocumentDto getDocument(@PathVariable Long id) {
        return DocumentDto.from(documentService.getDocument(id));
    }

    // DELETE /api/documents/{id} - 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/documents/{id}/config - 에디터 설정
    @GetMapping("/{id}/config")
    public EditorConfigResponse getEditorConfig(@PathVariable Long id) {
        return editorConfigService.getConfig(id);
    }
}
```

---

### Day 9-10: Callback API Enhancement (Issue #10)

**수정 파일:**
- `backend/src/main/java/com/example/onlyoffice/controller/CallbackController.java`

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/service/CallbackService.java`
- `backend/src/main/java/com/example/onlyoffice/service/CallbackQueueProcessor.java`

**CallbackQueueProcessor:**
```java
@Service
public class CallbackQueueProcessor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ⚠️ 단일 인스턴스 전용! 수평 확장 시 Redis/Kafka 필요 (3편)

    public CompletableFuture<CallbackResponse> process(
            CallbackRequest request,
            String authHeader) {

        return CompletableFuture.supplyAsync(() -> {
            // 1. JWT 검증
            if (!validateJwt(authHeader)) {
                return new CallbackResponse(1); // error
            }

            // 2. status=2일 때만 저장 처리
            if (request.getStatus() == 2) {
                // 비관적 락으로 문서 조회 (3초 타임아웃)
                Document doc = documentRepository.findWithLockById(request.getKey());

                // ONLYOFFICE에서 파일 다운로드
                InputStream editedFile = downloadFromOnlyoffice(request.getUrl());

                // MinIO에 업로드
                storageService.uploadFile(doc.getStoragePath(), editedFile, ...);

                // 버전 증가
                doc.setVersion(doc.getVersion() + 1);
                doc.setUpdatedAt(LocalDateTime.now());
                documentRepository.save(doc);
            }

            return new CallbackResponse(0); // success
        }, executor);
    }
}
```

**CallbackController:**
```java
@RestController
@RequestMapping("/api/callback")
public class CallbackController {

    @PostMapping
    public ResponseEntity<CallbackResponse> handleCallback(
            @RequestBody CallbackRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            CallbackResponse response = callbackQueueProcessor
                .process(request, authHeader)
                .get(30, TimeUnit.SECONDS);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(new CallbackResponse(1));
        }
    }
}
```

---

## 🗓️ Week 3: Frontend Migration + Testing

### Day 11-12: Next.js Project Setup (Issue #11)

**작업:** Frontend 완전 재작성 (Vite → Next.js)

**명령어:**
```bash
cd frontend
rm -rf *
npx create-next-app@latest . --typescript --tailwind --app --src-dir
pnpm add @tanstack/react-query @tanstack/react-table
pnpm add @onlyoffice/document-editor-react
pnpm dlx shadcn@latest init
pnpm dlx shadcn@latest add button table card input dialog progress
```

**디렉토리 구조:**
```
frontend/src/
├── app/
│   ├── layout.tsx           # QueryClientProvider
│   ├── page.tsx             # 문서 목록
│   └── editor/[id]/page.tsx # 에디터
├── components/
│   ├── DocumentTable.tsx
│   ├── UploadButton.tsx
│   └── Editor.tsx           # 'use client'
├── hooks/
│   ├── useDocuments.ts
│   └── useUploadDocument.ts
└── lib/
    ├── api.ts
    └── queryClient.ts
```

---

### Day 12-13: Document List + API Hooks (Issue #12, #13)

**신규 파일:**

**`frontend/src/app/page.tsx`:**
```tsx
import { DocumentTable } from '@/components/DocumentTable';
import { UploadButton } from '@/components/UploadButton';

export default function HomePage() {
  return (
    <div className="container mx-auto py-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">문서 목록</h1>
        <UploadButton />
      </div>
      <DocumentTable />
    </div>
  );
}
```

**`frontend/src/hooks/useDocuments.ts`:**
```typescript
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

export function useDocuments() {
  return useQuery({
    queryKey: ['documents'],
    queryFn: () => api.get('/documents').then(res => res.data),
  });
}
```

**`frontend/src/hooks/useUploadDocument.ts`:**
```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query';

export function useUploadDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return api.post('/documents', formData);
    },
    // Optimistic Update
    onMutate: async (file) => {
      await queryClient.cancelQueries({ queryKey: ['documents'] });
      const previous = queryClient.getQueryData(['documents']);

      queryClient.setQueryData(['documents'], (old: any[]) => [
        { fileName: file.name, status: 'PENDING', id: 'temp' },
        ...old,
      ]);

      return { previous };
    },
    onError: (err, file, context) => {
      queryClient.setQueryData(['documents'], context?.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
```

---

### Day 14: Editor Page (Issue #14)

**신규 파일:**

**`frontend/src/app/editor/[id]/page.tsx`:**
```tsx
import { Editor } from '@/components/Editor';

interface Props {
  params: { id: string };
}

export default function EditorPage({ params }: Props) {
  return <Editor documentId={params.id} />;
}
```

**`frontend/src/components/Editor.tsx`:**
```tsx
'use client';

import { useEffect, useState } from 'react';
import { DocumentEditor } from '@onlyoffice/document-editor-react';
import { api } from '@/lib/api';

interface Props {
  documentId: string;
}

export function Editor({ documentId }: Props) {
  const [config, setConfig] = useState<any>(null);

  useEffect(() => {
    api.get(`/documents/${documentId}/config`)
      .then(res => setConfig(res.data));
  }, [documentId]);

  if (!config) return <div>Loading...</div>;

  return (
    <div className="h-screen">
      <DocumentEditor
        id="onlyoffice-editor"
        documentServerUrl={config.documentServerUrl}
        config={config.config}
      />
    </div>
  );
}
```

---

### Day 15-17: Testing (Issues #15-17)

**신규 파일:**
- `backend/src/test/java/com/example/onlyoffice/service/FileSecurityServiceTest.java`
- `backend/src/test/java/com/example/onlyoffice/integration/CallbackIntegrationTest.java`
- `backend/src/test/java/com/example/onlyoffice/integration/SagaCompensationTest.java`

**테스트 커버리지 목표:**
- Service/Security 레이어: 80% 이상

**보안 테스트 (Issue #15):**
- OWASP ZAP 스캔
- 수동 검증 3건:
  1. 파일 업로드 우회 시도
  2. Path Traversal 공격
  3. 압축 폭탄 공격

**통합 테스트 (Issue #16):**
```java
@SpringBootTest
@Testcontainers
class CallbackIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void shouldProcessCallbackWithValidJwt() {
        // Given
        String validToken = jwtManager.createToken(callbackPayload);

        // When
        ResponseEntity<?> response = callbackController.handleCallback(
            request, "Bearer " + validToken);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, document.getVersion());
    }
}
```

**Saga 테스트 (Issue #17):**
```java
@Test
void shouldRollbackOnMinIOUploadFailure() {
    // Given
    when(storageService.uploadFile(any(), any(), anyLong(), any()))
        .thenThrow(new RuntimeException("Upload failed"));

    // When & Then
    assertThrows(DocumentUploadException.class, () -> {
        documentService.uploadDocument(mockFile);
    });

    // DB에 문서가 없어야 함
    assertTrue(documentRepository.findAll().isEmpty());
}
```

---

### Day 18-19: Monitoring + Documentation (Issues #18-19)

**Actuator 설정 추가 (`application.yml`):**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

**신규 파일:**
- `backend/src/main/java/com/example/onlyoffice/metrics/DocumentMetrics.java`

```java
@Component
public class DocumentMetrics {
    private final Counter uploadCounter;
    private final Timer uploadTimer;

    public DocumentMetrics(MeterRegistry registry) {
        this.uploadCounter = registry.counter("document.upload.count");
        this.uploadTimer = registry.timer("document.upload.duration");
    }

    public void recordUpload(long durationMs) {
        uploadCounter.increment();
        uploadTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

**문서화:**
- README.md 업데이트
- API 명세서 작성
- 보안 가이드 작성

---

## 📁 Critical Files Summary

| 파일 | 작업 유형 | 우선순위 |
|-----|---------|---------|
| `docker-compose.yml` | 수정 | 🔴 Critical |
| `backend/build.gradle` | 수정 | 🔴 Critical |
| `backend/.../application.yml` | 수정 | 🔴 Critical |
| `backend/.../entity/Document.java` | 신규 | 🔴 Critical |
| `backend/.../service/DocumentService.java` | 리팩토링 | 🔴 Critical |
| `backend/.../service/MinioStorageService.java` | 신규 | 🔴 Critical |
| `backend/.../service/FileSecurityService.java` | 신규 | 🔴 Critical |
| `backend/.../controller/CallbackController.java` | 수정 | 🔴 Critical |
| `backend/.../controller/DocumentController.java` | 신규 | 🔴 Critical |
| `frontend/src/app/*` | 신규 (전체) | 🔴 Critical |

---

## ⚠️ 주의사항

1. **Callback 큐**: 단일 인스턴스 전용 (`newSingleThreadExecutor`)
   - 수평 확장 시 Redis/Kafka 기반 분산 큐 필요 (3편 예정)

2. **Actuator 보안**: 프로덕션에서 `/actuator/**` 보호 필요
   - Spring Security로 보호하거나 내부 네트워크에서만 접근 허용

3. **시크릿 검증**:
   - JWT Secret 32자 이상 필수
   - 기본값 거부 (`change-me`, `secret` 등)
   - 애플리케이션 시작 시 검증

4. **비관적 락**: 타임아웃 3초 설정
   - DB 조회 시 `@Lock(PESSIMISTIC_WRITE)` 사용

---

## ✅ Milestone Checkpoints

- [ ] **M1 (Day 2)**: Docker 서비스 전체 healthy
- [ ] **M2 (Week 1 끝)**: Entity + MinIO + Security 완료
- [ ] **M3 (Week 2 끝)**: Backend API 전체 동작
- [ ] **M4 (Day 14)**: Frontend 기본 기능 동작
- [ ] **M5 (Week 3 끝)**: 테스트 통과 + 문서화 완료

---

## 📊 GitHub Issue 매핑

| Issue # | 제목 | Day | 우선순위 |
|---------|------|-----|---------|
| #1 | Infrastructure Docker Compose | Day 1-2 | 🔴 |
| #2 | Backend Dependencies | Day 2-3 | 🔴 |
| #3 | Application Configuration | Day 3 | 🔴 |
| #4 | Document Entity + Repository | Day 3-4 | 🔴 |
| #5 | MinIO Storage Service | Day 4-5 | 🔴 |
| #6 | Security Services | Day 5 | 🔴 |
| #7 | Document Service + Saga | Day 6-8 | 🔴 |
| #8 | Editor Config Service | Day 8-9 | 🔴 |
| #9 | REST API Endpoints | Day 9 | 🔴 |
| #10 | Callback API Enhancement | Day 9-10 | 🔴 |
| #11 | Next.js Project Setup | Day 11-12 | 🔴 |
| #12 | API Service + TanStack Query | Day 12 | 🔴 |
| #13 | Document List Page | Day 12-13 | 🔴 |
| #14 | Editor Page | Day 14 | 🔴 |
| #15 | 파일 업로드 보안 테스트 | Day 15-17 | 🟡 |
| #16 | JWT Callback 검증 테스트 | Day 15-17 | 🟡 |
| #17 | Saga 트랜잭션 테스트 | Day 15-17 | 🟡 |
| #18 | Spring Actuator 메트릭 | Day 18 | 🟡 |
| #19 | API 문서 작성 | Day 18-19 | 🟡 |

---

**문서 버전**: 1.0
**작성일**: 2025-12-05
**참조 PRD**: `resources/plan/prd-v2.md`
