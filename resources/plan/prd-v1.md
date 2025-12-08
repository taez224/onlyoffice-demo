# ONLYOFFICE 연동 2편 - Product Requirements Document (PRD)

## 📄 문서 정보

- **작성일**: 2025-11-23
- **버전**: 1.0
- **대상 시스템**: ONLYOFFICE Document Editor Integration Demo v2
- **작성자**: Product Team

---

## 1. Executive Summary

### 1.1 목적

본 프로젝트는 1편에서 구축한 기본적인 ONLYOFFICE 연동을 시연 가능 수준의 문서 편집 시스템으로 발전시키는 것을 목표로 합니다. 공식 Java SDK 도입, 영구 저장소 연동, 문서 관리 UI 구현을 통해 데모를 넘어선 적용 가능한 수준의 아키텍처를 구축합니다.

### 1.2 배경

1편에서 구현한 시스템은 다음과 같은 한계를 가지고 있습니다:

- **표준화 부재**: 수동으로 Config JSON을 생성하여 유지보수 어려움
- **휘발성 저장소**: 로컬 파일 시스템 사용으로 확장성 제한
- **메타데이터 부재**: 문서 정보를 체계적으로 관리할 수 없음
- **UX 한계**: 단일 에디터 페이지만 존재하여 문서 관리 불가능

### 1.3 핵심 가치 제안

1. **표준화**: ONLYOFFICE 공식 Java SDK를 통한 베스트 프랙티스 적용
2. **확장성**: Object Storage(S3 호환) 기반 파일 저장으로 무한 확장 가능
3. **관리성**: Database 기반 문서 메타데이터 관리 및 검색
4. **사용성**: 직관적인 문서 목록 UI 및 파일 업로드 기능

---

## 2. Product Overview

### 2.1 시스템 아키텍처

#### As-Is (1편)

```mermaid
graph TB
    subgraph "Client Layer"
        A[React Frontend]
    end

    subgraph "Application Layer"
        B[Spring Boot Backend<br/>Manual Config Generation]
    end

    subgraph "Storage Layer"
        C[Local FileSystem<br/>storage/]
    end

    subgraph "Document Server"
        D[ONLYOFFICE Docs]
    end

    A -->|HTTP| B
    B --> C
    B --> D
```

#### To-Be (2편)

```mermaid
graph TB
    subgraph "Client Layer"
        A[React SPA<br/>TanStack Router/Query/Table]
    end

    subgraph "Application Layer"
        B[Spring Boot Backend<br/>+ ONLYOFFICE SDK]
    end

    subgraph "Data Layer"
        C[(PostgreSQL<br/>Document Metadata)]
        D[S3 Ninja<br/>Object Storage]
    end

    subgraph "Document Server"
        E[ONLYOFFICE Docs]
    end

    A -->|REST API| B
    B --> C
    B --> D
    B --> E
```

### 2.2 주요 컴포넌트

#### Backend Components

| 컴포넌트 | 역할 | 기술 스택 |
|---------|------|----------|
| **ONLYOFFICE SDK Integration** | 에디터 설정 생성, Callback 처리 표준화 | ONLYOFFICE Java SDK 1.0.0 |
| **Document Service** | 문서 CRUD 비즈니스 로직 | Spring Boot |
| **Storage Service** | Object Storage 파일 입출력 | AWS S3 SDK (S3 Ninja 호환) |
| **Repository Layer** | 문서 메타데이터 영속화 | Spring Data JPA |

#### Frontend Components

| 컴포넌트 | 역할 | 기술 스택 |
|---------|------|----------|
| **Document List Page** | 문서 목록 조회 및 업로드 | React, TanStack Router |
| **Document Table** | 문서 목록 테이블 UI | TanStack Table |
| **Editor Page** | ONLYOFFICE 에디터 렌더링 | React, ONLYOFFICE API |
| **API Layer** | 서버 상태 관리 및 캐싱 | TanStack Query |

#### Infrastructure Components

| 컴포넌트 | 역할 | 배포 방식 |
|---------|------|----------|
| **PostgreSQL** | 문서 메타데이터 저장소 | Docker Compose |
| **S3 Ninja** | S3 호환 Object Storage (로컬) | Docker Compose |
| **ONLYOFFICE Docs** | 문서 편집 서버 (기존) | Docker Compose |

### 2.3 컴포넌트 구조

```mermaid
graph TB
    subgraph "Frontend (React)"
        A1[DocumentListPage]
        A2[EditorPage]
        A3[DocumentTable Component]
        A4[UploadButton Component]
        A5[Editor Component]

        A1 --> A3
        A1 --> A4
        A2 --> A5
    end

    subgraph "React Hooks Layer"
        B1[useDocuments]
        B2[useUploadDocument]
        B3[useDeleteDocument]
        B4[useEditorConfig]

        A1 --> B1
        A1 --> B2
        A1 --> B3
        A2 --> B4
    end

    subgraph "API Service Layer"
        C1[getDocuments]
        C2[uploadDocument]
        C3[deleteDocument]
        C4[getEditorConfig]

        B1 --> C1
        B2 --> C2
        B3 --> C3
        B4 --> C4
    end

    subgraph "Backend API"
        D1[DocumentController]
        D2[CallbackController]
        D3[FileController]
    end

    C1 --> D1
    C2 --> D1
    C3 --> D1
    C4 --> D1
```

---

## 3. User Stories & Use Cases

### 3.1 Primary User: 문서 작성자

#### US-001: 문서 목록 조회
```
As a 문서 작성자
I want to 업로드된 모든 문서를 목록으로 확인
So that 원하는 문서를 빠르게 찾아 편집할 수 있다
```

**Acceptance Criteria:**
- 문서 파일명, 타입, 크기, 생성일이 테이블 형태로 표시됨
- 최신 생성일 기준으로 정렬됨
- 문서 클릭 시 에디터 페이지로 이동함

#### US-002: 문서 업로드
```
As a 문서 작성자
I want to 새로운 문서 파일을 시스템에 업로드
So that ONLYOFFICE 에디터로 편집할 수 있다
```

**Acceptance Criteria:**
- 파일 선택 후 업로드 버튼 클릭 시 파일이 업로드됨
- 업로드 완료 후 문서 목록이 자동으로 갱신됨
- 지원 형식: `.docx`, `.xlsx`, `.pptx`, `.pdf`

#### US-003: 문서 편집
```
As a 문서 작성자
I want to 목록에서 선택한 문서를 ONLYOFFICE 에디터로 편집
So that 실시간으로 문서를 수정하고 저장할 수 있다
```

**Acceptance Criteria:**
- 에디터가 정상적으로 로드되어 문서 내용이 표시됨
- 편집 후 저장(Ctrl+S) 시 변경사항이 S3 Ninja에 반영됨
- 브라우저 새로고침 시 최신 변경사항이 유지됨

#### US-004: 문서 삭제
```
As a 문서 작성자
I want to 더 이상 필요 없는 문서를 삭제
So that 문서 목록을 깔끔하게 관리할 수 있다
```

**Acceptance Criteria:**
- 삭제 버튼 클릭 시 확인 다이얼로그가 표시됨
- 확인 시 DB 레코드 및 S3 파일이 모두 삭제됨
- 삭제 후 문서 목록이 자동으로 갱신됨

### 3.2 시스템 사용 시나리오

#### Scenario 1: 최초 문서 업로드 및 편집

```mermaid
sequenceDiagram
    actor User
    participant UI as Document List Page
    participant API as Backend API
    participant DB as PostgreSQL
    participant S3 as S3 Ninja
    participant Editor as Editor Page
    participant OO as ONLYOFFICE Docs

    User->>UI: 페이지 접속 (/)
    UI->>API: GET /api/documents
    API->>DB: SELECT * FROM documents
    DB-->>API: []
    API-->>UI: 빈 배열 반환
    UI-->>User: "업로드된 문서가 없습니다"

    User->>UI: "파일 업로드" 클릭
    User->>UI: example.docx 선택
    UI->>API: POST /api/documents (multipart)
    API->>S3: putObject(doc_xxx.docx)
    S3-->>API: 업로드 완료
    API->>DB: INSERT document metadata
    DB-->>API: id=1
    API-->>UI: DocumentDTO (id=1)
    UI-->>User: 목록에 "example.docx" 표시

    User->>UI: "example.docx" 행 클릭
    UI->>Editor: navigate(/editor/1)
    Editor->>API: GET /api/config?documentId=1
    API->>DB: SELECT * FROM documents WHERE id=1
    DB-->>API: Document 객체
    API->>S3: generatePresignedUrl()
    S3-->>API: https://s3ninja/...
    API-->>Editor: EditorConfig JSON
    Editor->>OO: new DocsAPI.DocEditor(config)
    OO-->>Editor: 에디터 렌더링

    User->>OO: 문서 편집
    User->>OO: Ctrl+S (저장)
    OO->>API: POST /callback (status=2)
    API->>OO: GET edited file
    OO-->>API: Binary data
    API->>S3: putObject (덮어쓰기)
    API->>DB: UPDATE updated_at
    API-->>OO: {error: 0}
```

#### Scenario 2: 다중 문서 관리

```mermaid
stateDiagram-v2
    [*] --> 문서목록페이지

    문서목록페이지 --> 파일선택: Upload 버튼 클릭
    파일선택 --> 업로드중: A.docx 선택
    업로드중 --> 문서목록페이지: 업로드 완료

    문서목록페이지 --> 파일선택2: Upload 버튼 클릭
    파일선택2 --> 업로드중2: B.xlsx 선택
    업로드중2 --> 문서목록페이지: 업로드 완료

    문서목록페이지 --> 파일선택3: Upload 버튼 클릭
    파일선택3 --> 업로드중3: C.pptx 선택
    업로드중3 --> 문서목록페이지: 업로드 완료 (3개 문서)

    문서목록페이지 --> 에디터페이지: B.xlsx 클릭
    에디터페이지 --> 문서목록페이지: 뒤로가기

    문서목록페이지 --> 삭제확인: C.pptx 삭제 버튼
    삭제확인 --> 문서목록페이지: 확인 (2개 문서 남음)

    문서목록페이지 --> [*]
```

---

## 4. Functional Requirements

### 4.1 Backend API Specifications

#### 4.1.1 문서 목록 API

**Endpoint:** `GET /api/documents`

**Response:**
```json
[
  {
    "id": 1,
    "fileName": "example.docx",
    "fileKey": "doc_1234567890_abcdef",
    "fileType": "docx",
    "documentType": "word",
    "fileSize": 15360,
    "storagePath": "documents/doc_1234567890_abcdef.docx",
    "createdAt": "2025-11-23T10:30:00",
    "updatedAt": "2025-11-23T11:45:00",
    "createdBy": "anonymous"
  }
]
```

**비즈니스 로직:**
- 모든 문서를 `created_at DESC` 순서로 조회
- S3 Ninja 연결 상태 확인 불필요 (메타데이터만 반환)

#### 4.1.2 문서 업로드 API

**Endpoint:** `POST /api/documents`

**Request:**
```
Content-Type: multipart/form-data

file: <binary>
```

**Response:**
```json
{
  "id": 2,
  "fileName": "new-document.xlsx",
  "fileKey": "doc_1700000000_xyz123",
  "fileType": "xlsx",
  "documentType": "cell",
  "fileSize": 20480,
  "storagePath": "documents/doc_1700000000_xyz123.xlsx",
  "createdAt": "2025-11-23T12:00:00",
  "updatedAt": "2025-11-23T12:00:00",
  "createdBy": "anonymous"
}
```

**비즈니스 로직:**
1. 파일 확장자 검증 (허용: docx, xlsx, pptx, pdf)
2. 파일 크기 제한 (최대 50MB)
3. 고유 `fileKey` 생성 (timestamp + UUID)
4. S3 Ninja에 파일 업로드 (bucket: `documents`)
5. DB에 메타데이터 저장
6. DocumentDTO 반환

#### 4.1.3 문서 삭제 API

**Endpoint:** `DELETE /api/documents/{id}`

**Response:**
```
HTTP 204 No Content
```

**비즈니스 로직:**
1. DB에서 문서 조회
2. S3 Ninja에서 파일 삭제
3. DB 레코드 삭제
4. 트랜잭션으로 원자성 보장

#### 4.1.4 에디터 설정 API

**Endpoint:** `GET /api/config?documentId={id}`

**Response:**
```json
{
  "config": {
    "documentType": "word",
    "document": {
      "fileType": "docx",
      "key": "doc_1234567890_abcdef",
      "title": "example.docx",
      "url": "http://localhost:8080/api/files/doc_1234567890_abcdef"
    },
    "editorConfig": {
      "callbackUrl": "http://localhost:8080/callback",
      "user": {
        "id": "anonymous",
        "name": "Anonymous"
      }
    }
  }
}
```

**비즈니스 로직:**
1. DB에서 `documentId`로 문서 조회
2. **ONLYOFFICE SDK의 ConfigService**를 사용하여 Config 생성
3. S3 Ninja Presigned URL 생성 (1시간 유효)
4. Callback URL 설정

#### 4.1.5 Callback API

**Endpoint:** `POST /callback`

**Request:**
```json
{
  "key": "doc_1234567890_abcdef",
  "status": 2,
  "url": "https://onlyoffice-docs/cache/files/...",
  "users": ["anonymous"]
}
```

**Response:**
```json
{
  "error": 0
}
```

**비즈니스 로직:**
1. **ONLYOFFICE SDK의 CallbackManager**로 요청 파싱
2. `status == 2` (저장 완료) 시:
   - Callback URL에서 편집된 파일 다운로드
   - S3 Ninja에 덮어쓰기
   - DB `updated_at` 갱신
3. `status == 6` (강제 저장) 시 동일 처리

### 4.2 API 흐름도

```mermaid
flowchart TD
    Start([API 요청]) --> CheckType{요청 타입}

    CheckType -->|GET /documents| GetList[문서 목록 조회]
    GetList --> QueryDB[(DB SELECT)]
    QueryDB --> ReturnList[DTO 배열 반환]

    CheckType -->|POST /documents| Upload[파일 업로드]
    Upload --> Validate{파일 검증}
    Validate -->|실패| Error400[400 Bad Request]
    Validate -->|성공| GenKey[fileKey 생성]
    GenKey --> S3Upload[S3에 업로드]
    S3Upload --> DBInsert[(DB INSERT)]
    DBInsert --> ReturnDoc[DocumentDTO 반환]

    CheckType -->|DELETE /documents/:id| Delete[문서 삭제]
    Delete --> FindDoc[(DB SELECT)]
    FindDoc --> CheckExists{존재?}
    CheckExists -->|없음| Error404[404 Not Found]
    CheckExists -->|있음| S3Delete[S3에서 삭제]
    S3Delete --> DBDelete[(DB DELETE)]
    DBDelete --> Return204[204 No Content]

    CheckType -->|GET /config| GetConfig[에디터 설정 조회]
    GetConfig --> FindDoc2[(DB SELECT)]
    FindDoc2 --> SDKConfig[SDK로 Config 생성]
    SDKConfig --> GenURL[Presigned URL 생성]
    GenURL --> ReturnConfig[Config JSON 반환]

    CheckType -->|POST /callback| Callback[콜백 처리]
    Callback --> ParseSDK[SDK로 파싱]
    ParseSDK --> CheckStatus{status == 2?}
    CheckStatus -->|No| ReturnOK[error: 0]
    CheckStatus -->|Yes| Download[편집 파일 다운로드]
    Download --> S3Overwrite[S3 덮어쓰기]
    S3Overwrite --> DBUpdate[(DB UPDATE updated_at)]
    DBUpdate --> ReturnOK

    ReturnList --> End([응답 반환])
    ReturnDoc --> End
    Return204 --> End
    ReturnConfig --> End
    ReturnOK --> End
    Error400 --> End
    Error404 --> End
```

### 4.3 Frontend Requirements

#### 4.3.1 라우팅 구조

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | DocumentListPage | 문서 목록 및 업로드 UI |
| `/editor/:documentId` | EditorPage | ONLYOFFICE 에디터 렌더링 |

```mermaid
graph LR
    A[Root/] --> B[DocumentListPage]
    A --> C[editor/:documentId]
    C --> D[EditorPage]

    B -->|클릭 이벤트| C
    D -->|뒤로가기| B
```

#### 4.3.2 상태 관리

**TanStack Query Cache Keys:**
```typescript
['documents']              // 전체 문서 목록
['editorConfig', id]       // 특정 문서의 에디터 설정
```

**Mutations:**
```typescript
uploadDocument(file: File)       // 파일 업로드 → ['documents'] 무효화
deleteDocument(id: number)       // 문서 삭제 → ['documents'] 무효화
```

```mermaid
flowchart LR
    subgraph "Query Keys"
        A["['documents']"]
        B["['editorConfig', 1]"]
    end

    subgraph "Mutations"
        C[uploadDocument]
        D[deleteDocument]
    end

    subgraph "API Calls"
        E[GET /api/documents]
        F[GET /api/config?documentId=1]
        G[POST /api/documents]
        H[DELETE /api/documents/1]
    end

    A --> E
    B --> F
    C --> G
    D --> H

    G -.invalidates.-> A
    H -.invalidates.-> A
```

#### 4.3.3 UI/UX 요구사항

**문서 목록 페이지:**
- 테이블 헤더: 파일명, 타입, 크기, 생성일
- 각 행 클릭 시 에디터 페이지로 이동
- 상단에 "파일 업로드" 버튼 배치
- 로딩 중 스피너 표시
- 빈 목록 시 "업로드된 문서가 없습니다" 메시지

**에디터 페이지:**
- 전체 화면 에디터 (최소 높이 600px)
- 로딩 중 "에디터를 불러오는 중..." 메시지
- 에디터 상단에 "← 목록으로" 링크

---

## 5. Non-Functional Requirements

### 5.1 성능 요구사항

| 항목 | 목표 | 측정 방법 |
|------|------|----------|
| 문서 목록 로딩 | < 500ms | API 응답 시간 |
| 파일 업로드 (10MB) | < 3초 | 업로드 완료까지 시간 |
| 에디터 초기 렌더링 | < 2초 | 에디터 로드 완료까지 시간 |
| Callback 처리 | < 1초 | Callback 요청부터 S3 저장까지 |

### 5.2 확장성

- **파일 저장소**: S3 Ninja를 실제 AWS S3로 교체 가능한 인터페이스 설계
- **데이터베이스**: JPA를 통한 DB 독립성 확보 (PostgreSQL ↔ MySQL 전환 가능)
- **동시 사용자**: 최소 10명의 동시 편집 지원 (ONLYOFFICE Docs 스펙 기준)

### 5.3 보안

| 항목 | 요구사항 | 구현 방법 |
|------|----------|----------|
| 파일 업로드 검증 | 허용된 확장자만 업로드 | 백엔드 MIME 타입 검증 |
| 파일 크기 제한 | 최대 50MB | Spring Boot `maxFileSize` 설정 |
| S3 접근 제어 | 인증된 요청만 허용 | Presigned URL (1시간 만료) |
| Callback 위변조 방지 | JWT 서명 검증 | **3편에서 구현 예정** |

### 5.4 가용성

- **Docker Compose**: 모든 인프라를 로컬에서 재현 가능
- **데이터 영속성**: PostgreSQL 및 S3 Ninja 볼륨 마운트
- **에러 복구**: S3 업로드 실패 시 DB 롤백 (트랜잭션)

---

## 6. Data Model

### 6.1 Database Schema

#### documents 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| id | BIGSERIAL | PRIMARY KEY | 문서 고유 ID |
| file_name | VARCHAR(255) | NOT NULL | 원본 파일명 (예: example.docx) |
| file_key | VARCHAR(255) | UNIQUE NOT NULL | ONLYOFFICE 문서 키 (수정 감지용) |
| file_type | VARCHAR(50) | NOT NULL | 확장자 (docx, xlsx, pptx, pdf) |
| document_type | VARCHAR(20) | NOT NULL | ONLYOFFICE 타입 (word, cell, slide) |
| file_size | BIGINT | - | 파일 크기 (bytes) |
| storage_path | VARCHAR(500) | NOT NULL | S3 Object Key |
| created_at | TIMESTAMP | DEFAULT NOW() | 최초 업로드 시각 |
| updated_at | TIMESTAMP | DEFAULT NOW() | 최근 수정 시각 |
| created_by | VARCHAR(100) | - | 업로더 ID (현재는 "anonymous") |

**인덱스:**
- `idx_file_key` on `file_key` (빠른 조회)
- `idx_created_at` on `created_at` (정렬 최적화)

### 6.2 ERD

```mermaid
erDiagram
    DOCUMENTS {
        bigserial id PK
        varchar file_name
        varchar file_key UK
        varchar file_type
        varchar document_type
        bigint file_size
        varchar storage_path
        timestamp created_at
        timestamp updated_at
        varchar created_by
    }

    DOCUMENTS ||--o{ DOCUMENT_VERSIONS : "has (3편)"

    DOCUMENT_VERSIONS {
        bigserial id PK
        bigint document_id FK
        int version_number
        varchar file_key
        varchar storage_path
        timestamp created_at
        varchar created_by
    }
```

### 6.3 S3 Object Structure

```mermaid
graph TD
    subgraph "S3 Ninja Bucket: documents"
        A[doc_1700000000_abc123.docx]
        B[doc_1700000001_def456.xlsx]
        C[doc_1700000002_ghi789.pptx]
    end

    subgraph "Object Metadata"
        D[Content-Type: application/vnd...]
        E[Content-Length: 15360]
        F[Last-Modified: 2025-11-23T...]
    end

    A --> D
    A --> E
    A --> F
```

**Object Key 규칙:**
- 형식: `doc_{timestamp}_{uuid}.{extension}`
- 중복 방지를 위한 timestamp + UUID 조합

---

## 7. Integration Points

### 7.1 ONLYOFFICE SDK 연동

**사용 Manager/Service:**

| SDK 컴포넌트 | 용도 | 구현 클래스 |
|-------------|------|------------|
| SettingsManager | ONLYOFFICE 서버 URL, JWT Secret 관리 | CustomSettingsManager |
| DocumentManager | 문서 키 생성, 메타데이터 관리 | CustomDocumentManager |
| FileStorageManager | 파일 입출력 추상화 | S3FileStorageManager |
| CallbackManager | Callback 요청 파싱 및 처리 | CustomCallbackManager |
| ConfigService | 에디터 Config JSON 생성 | SDK 기본 구현 사용 |

```mermaid
classDiagram
    class SettingsManager {
        <<interface>>
        +getServerUrl()
        +getSecret()
    }

    class DocumentManager {
        <<interface>>
        +generateKey()
        +getDocumentType()
    }

    class FileStorageManager {
        <<interface>>
        +uploadFile()
        +downloadFile()
        +deleteFile()
    }

    class CallbackManager {
        <<interface>>
        +parseCallback()
        +handleSave()
    }

    class CustomSettingsManager {
        +getServerUrl()
        +getSecret()
    }

    class CustomDocumentManager {
        +generateKey()
        +getDocumentType()
    }

    class S3FileStorageManager {
        -S3Client s3Client
        +uploadFile()
        +downloadFile()
        +deleteFile()
    }

    class CustomCallbackManager {
        +parseCallback()
        +handleSave()
    }

    SettingsManager <|.. CustomSettingsManager
    DocumentManager <|.. CustomDocumentManager
    FileStorageManager <|.. S3FileStorageManager
    CallbackManager <|.. CustomCallbackManager
```

**의존성:**
```gradle
implementation 'com.onlyoffice:docs-integration-sdk-java:1.0.0'
```

### 7.2 S3 Ninja 연동

**AWS S3 SDK 호환 설정:**
```yaml
s3:
  endpoint: http://localhost:9444
  access-key: AKIAIOSFODNN7EXAMPLE
  secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
  region: us-east-1
  bucket: documents
```

**주요 작업:**
- `putObject`: 파일 업로드
- `getObject`: 파일 다운로드
- `deleteObject`: 파일 삭제
- `generatePresignedUrl`: ONLYOFFICE가 접근 가능한 임시 URL 생성

### 7.3 PostgreSQL 연동

**Spring Data JPA 설정:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/onlyoffice_demo
    username: demo
    password: demo123
  jpa:
    hibernate:
      ddl-auto: update  # 개발 환경에서만 사용
    show-sql: true
```

---

## 8. Technology Stack

### 8.1 Backend

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 3.2.x |
| Language | Java | 17 |
| ORM | Spring Data JPA | 3.2.x |
| Database | PostgreSQL | 16 |
| Object Storage | S3 Ninja (AWS S3 SDK v2) | 2.39.1 |
| ONLYOFFICE SDK | docs-integration-sdk-java | 1.0.0 |

### 8.2 Frontend

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | **Next.js** | 16.x |
| React | React | 19.x |
| Language | TypeScript | 5.x |
| Build Tool | Turbopack (Next.js built-in) | - |
| State Management | TanStack Query | 최신 |
| Table | TanStack Table | 최신 |
| UI Library | shadcn/ui (Tailwind CSS) | 최신 |

**Note**: ONLYOFFICE Document Editor는 Client Component로 구현 (`'use client'` 필요)

### 8.3 Infrastructure

| Service | Technology | Port |
|---------|-----------|------|
| ONLYOFFICE Docs | onlyoffice/documentserver | 8000 |
| PostgreSQL | postgres:16 | 5432 |
| S3 Ninja | scireum/s3-ninja | 9444 |
| Backend | Spring Boot | 8080 |
| Frontend | Vite Dev Server | 5173 |

### 8.4 전체 기술 스택 다이어그램

```mermaid
graph TB
    subgraph "Frontend Stack"
        A1[React 18]
        A2[TypeScript 5]
        A3[Vite 5]
        A4[TanStack Router]
        A5[TanStack Query]
        A6[TanStack Table]
        A7[shadcn/ui]
    end

    subgraph "Backend Stack"
        B1[Spring Boot 3.2]
        B2[Java 17]
        B3[Spring Data JPA]
        B4[ONLYOFFICE SDK 1.0]
        B5[AWS S3 SDK 2.39]
    end

    subgraph "Infrastructure"
        C1[PostgreSQL 16<br/>:5432]
        C2[S3 Ninja<br/>:9444]
        C3[ONLYOFFICE Docs<br/>:8000]
    end

    A1 --> B1
    B1 --> C1
    B1 --> C2
    B1 --> C3
```

---

## 9. Out of Scope (3편 이후로 연기)

다음 기능들은 2편 범위에서 제외되며, 향후 버전에서 다룰 예정입니다:

### 9.1 보안 강화
- JWT 기반 Callback 위변조 방지
- 사용자 인증/인가 시스템
- 파일 접근 권한 관리

### 9.2 협업 기능
- 실시간 동시 편집자 표시
- 문서 공유 링크 생성
- 댓글 및 리뷰 모드

### 9.3 고급 문서 관리
- 버전 히스토리 UI
- 문서 복원 기능
- 폴더 구조 및 카테고리 분류

### 9.4 성능 최적화
- Redis 기반 에디터 Config 캐싱
- CDN을 통한 정적 파일 배포
- 대용량 파일 청크 업로드

---

## 10. Success Metrics

### 10.1 개발 완료 기준

- [ ] 모든 API 엔드포인트가 Swagger UI에서 테스트 가능
- [ ] 문서 업로드 → 목록 조회 → 편집 → 저장 플로우가 정상 동작
- [ ] PostgreSQL에 문서 메타데이터가 정확히 저장됨
- [ ] S3 Ninja에 파일이 업로드/삭제됨
- [ ] Frontend 라우팅이 정상 작동 (`/`, `/editor/:id`)
- [ ] TanStack Query로 서버 상태가 자동 동기화됨

### 10.2 품질 기준

- [ ] Backend Unit Test 커버리지 > 70%
- [ ] 에디터 초기 로딩 시간 < 2초
- [ ] 10MB 파일 업로드 성공률 100%
- [ ] 브라우저 새로고침 후에도 편집 내용 유지

### 10.3 문서화 기준

- [ ] README에 전체 실행 방법 기재
- [ ] API 명세서 (Swagger 또는 Markdown)
- [ ] 아키텍처 다이어그램 업데이트
- [ ] 블로그 2편 발행 (Medium/Velog)

---

## 11. Risks & Mitigation

### 11.1 기술 리스크

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|----------|
| ONLYOFFICE SDK 문서 부족 | 중 | 공식 GitHub 예제 코드 참고, 커뮤니티 포럼 활용 |
| S3 Ninja 안정성 | 중 | 실제 AWS S3로 전환 가능하도록 추상화 레이어 구현 |
| TanStack 라이브러리 러닝커브 | 낮 | 공식 문서 및 예제 코드 학습 |

### 11.2 일정 리스크

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|----------|
| SDK 적용 시간 초과 | 중 | 핵심 기능 우선 구현, 추가 기능은 3편으로 연기 |
| DB 마이그레이션 이슈 | 낮 | 기존 파일이 적어 수동 마이그레이션 가능 |

---

## 12. Dependencies

### 12.1 외부 의존성

- **ONLYOFFICE Document Server**: 8000 포트에서 정상 동작 필요
- **Docker & Docker Compose**: PostgreSQL, S3 Ninja 실행 환경
- **인터넷 연결**: ONLYOFFICE SDK, npm 패키지 다운로드

### 12.2 내부 의존성

- 1편에서 구축한 기본 프로젝트 구조
- 기존 `docker-compose.yml`에 PostgreSQL, S3 Ninja 추가

```mermaid
graph TD
    A[기존 프로젝트<br/>1편] --> B[docker-compose.yml<br/>확장]
    B --> C[PostgreSQL 추가]
    B --> D[S3 Ninja 추가]

    E[기존 Backend] --> F[SDK 의존성 추가]
    E --> G[JPA 의존성 추가]
    E --> H[S3 SDK 의존성 추가]

    I[기존 Frontend] --> J[TanStack 라이브러리 추가]
    I --> K[라우팅 구조 변경]
```

---

## 13. Appendix

### 13.1 용어 정의

| 용어 | 설명 |
|------|------|
| **Document Key** | ONLYOFFICE가 문서 수정 여부를 판단하는 고유 식별자 (변경 시 강제 새로고침) |
| **Callback** | 문서 편집 완료 시 ONLYOFFICE Docs가 Backend로 전송하는 Webhook |
| **Presigned URL** | 임시 접근 권한이 부여된 S3 객체 URL (만료 시간 설정 가능) |
| **TanStack Query** | React의 서버 상태 관리 라이브러리 (캐싱, 자동 갱신 지원) |

### 13.2 참고 자료

- [ONLYOFFICE Java SDK GitHub](https://github.com/ONLYOFFICE/docs-integration-sdk-java)
- [ONLYOFFICE API Documentation](https://api.onlyoffice.com/editors/config/)
- [TanStack Query Docs](https://tanstack.com/query/latest)
- [TanStack Router Docs](https://tanstack.com/router/latest)
- [S3 Ninja Docker Image](https://hub.docker.com/r/scireum/s3-ninja)

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| 1.0 | 2025-11-23 | Product Team | 최초 작성 (Mermaid 다이어그램 포함) |