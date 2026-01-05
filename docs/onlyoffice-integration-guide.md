# OnlyOffice Document Server 통합 가이드

이 문서는 OnlyOffice Document Server API 스펙과 현재 구현 상태를 정리한 기술 문서입니다.

**📝 Note**: The "개선 필요 사항" (Improvements Needed) section describes features that have been implemented through Issue #30 (fileKey/UUID migration). The system now uses:
- UUID-based `fileKey` as the immutable file identifier
- `editorVersion` that increments after SAVE callbacks
- `editorKey = fileKey_v{version}` format for Document Server sessions
- Proper callback handling with version tracking

See [Issue #30](https://github.com/taez224/onlyoffice-demo/issues/30) for implementation details.

## 목차

- [핵심 개념](#핵심-개념)
- [document.key 관리](#documentkey-관리)
- [Callback Handler](#callback-handler)
- [현재 구현 분석](#현재-구현-분석)
- [개선 필요 사항](#개선-필요-사항)
- [참고 자료](#참고-자료)

---

## 핵심 개념

### Key의 두 가지 유형

OnlyOffice에는 두 가지 key 개념이 존재합니다:

| 구분 | `document.key` | `referenceData.fileKey` |
|------|----------------|-------------------------|
| **용도** | 편집 세션 식별 | 파일 영구 식별 |
| **변경 시점** | 저장할 때마다 새로 생성 | 불변 (파일 생성 시 1회) |
| **역할** | 에디터 캐시 관리, co-editing 세션 공유 | 외부 데이터 참조, 파일 링크 |

### document.key 상세

```
document.key는 문서를 인식하기 위해 서비스가 사용하는 고유 문서 식별자입니다.
알려진 key가 전송되면 문서는 캐시에서 가져옵니다.
문서가 편집되고 저장될 때마다 key는 새로 생성되어야 합니다.
```

**제약사항:**
- 최대 길이: **128자**
- 특수문자: **사용 불가** (영문, 숫자, `_`, `-` 권장)
- 고유성: 동일 Document Server에 연결된 모든 서비스에서 고유해야 함

---

## document.key 관리

### Key 생성 규칙

```java
// 권장 패턴
String key = fileId + "_v" + version;  // 예: "doc123_v5"

// 또는 해시 기반
String key = hash(fileId + lastModified);
```

### Key 변경 시점

| 상황 | Key 변경 여부 | 설명 |
|------|--------------|------|
| 문서 열기 | 유지 | 동일 key로 co-editing 세션 참여 |
| 편집 중 (status 1) | 유지 | 세션 유지 |
| Force Save (status 6) | **유지** | 세션 중 저장, key 변경 금지 |
| 편집 종료 저장 (status 2) | **변경** | 다음 편집을 위해 새 key 생성 |
| 변경 없이 닫기 (status 4) | 유지 | 저장 발생 안 함 |

### Co-editing과 Key

```
동일한 key를 가진 사용자들은 같은 문서를 co-editing 합니다.
다른 key를 사용하면 완전히 별개의 파일로 인식됩니다.
```

**예시 시나리오:**
1. User A가 `key: doc1_v3`으로 문서 열기
2. User B가 `key: doc1_v3`으로 문서 열기 → **co-editing 시작**
3. User A가 저장 후 종료 → 서버에서 `doc1_v4`로 버전 증가
4. User C가 `key: doc1_v4`로 문서 열기 → 최신 버전으로 새 세션

---

## Callback Handler

### Callback Status 코드

| Status | 의미 | 처리 방법 |
|--------|------|----------|
| **1** | 편집 중 | 사용자 접속/해제 알림, 처리 불필요 |
| **2** | 저장 완료 (편집 종료) | 파일 다운로드 & 저장, **key 갱신** |
| **3** | 저장 에러 | 에러 로깅 |
| **4** | 변경 없이 닫힘 | 처리 불필요 |
| **6** | Force Save | 파일 다운로드 & 저장, key 유지 |
| **7** | Force Save 에러 | 에러 로깅 |

### Callback 요청 예시

**Status 2 (저장 완료):**
```json
{
  "key": "doc123_v3",
  "status": 2,
  "url": "https://documentserver/cache/edited-file.docx",
  "changesurl": "https://documentserver/cache/changes.zip",
  "history": {
    "changes": [...],
    "serverVersion": "7.5.0"
  },
  "users": ["user1"],
  "actions": [{"type": 0, "userid": "user1"}]
}
```

### Callback 응답

```json
{"error": 0}  // 성공
{"error": 1}  // 실패
```

---

## 현재 구현 분석

### 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Frontend   │────▶│   Backend   │────▶│ OnlyOffice  │
│  (Next.js)  │     │(Spring Boot)│     │   Server    │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    │  PostgreSQL │
                    │   + MinIO   │
                    └─────────────┘
```

### 주요 컴포넌트

| 파일 | 역할 |
|------|------|
| `EditorController.java` | 에디터 설정 JSON 생성, key 생성 |
| `CallbackController.java` | OnlyOffice callback 처리, 파일 저장 |
| `DocumentService.java` | 파일 I/O 처리 |
| `Document.java` | 문서 엔티티 (JPA) |

### 현재 Key 생성 방식

```java
// EditorController.java:40
document.put("key", fileName + "_" + file.lastModified());
```

**문제점:**
- 파일시스템의 `lastModified` 의존
- DB 엔티티와 연동되지 않음
- 저장 후 명시적 갱신 로직 없음

### 현재 Callback 처리

```java
// CallbackController.java
if (status == 2 || status == 6) {
    // 파일 다운로드 및 저장
    documentService.saveFile(fileName, inputStream);
    // ❌ key 갱신 로직 없음
}
```

---

## 개선 필요 사항

### Issue #23 참조

자세한 내용은 [GitHub Issue #23](https://github.com/taez224/onlyoffice-demo/issues/23) 참조

### 1. Document 엔티티 확장

```java
@Entity
public class Document {
    // 기존 필드
    @Column(name = "file_key", unique = true)
    private String fileKey;  // 불변 - referenceData용

    // 추가 필드
    @Column(name = "editor_version")
    private Integer editorVersion = 0;  // 편집 세션 버전

    public String getEditorKey() {
        return fileKey + "_v" + editorVersion;
    }

    public void incrementEditorVersion() {
        this.editorVersion++;
    }
}
```

### 2. EditorController 수정

```java
@GetMapping("/api/config")
public Map<String, Object> getEditorConfig(@RequestParam String fileName) {
    Document doc = documentRepository.findByFileName(fileName)
        .orElseThrow(() -> new NotFoundException("Document not found"));

    // DB 기반 key 생성
    String key = doc.getEditorKey();  // fileKey + "_v" + version

    // 길이 검증
    if (key.length() > 128) {
        key = generateSafeKey(doc);
    }

    document.put("key", key);
    // ...
}
```

### 3. CallbackController 수정

```java
@PostMapping("/callback")
public Map<String, Object> callback(...) {
    if (status == 2) {  // 편집 종료 & 저장 완료
        // 파일 저장
        documentService.saveFile(fileName, inputStream);

        // key 갱신 (다음 편집을 위해)
        Document doc = documentRepository.findByFileName(fileName).orElseThrow();
        doc.incrementEditorVersion();
        documentRepository.save(doc);

        log.info("Document saved, new version: {}", doc.getEditorVersion());
    }

    if (status == 6) {  // Force Save
        // 파일만 저장, key 유지
        documentService.saveFile(fileName, inputStream);
    }

    return Map.of("error", 0);
}
```

### 4. Key 유틸리티

```java
public class KeyUtils {
    private static final int MAX_KEY_LENGTH = 128;
    private static final Pattern SAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

    public static String generateSafeKey(String base, int version) {
        String raw = base + "_v" + version;
        String safe = SAFE_CHARS.matcher(raw).replaceAll("");

        if (safe.length() > MAX_KEY_LENGTH) {
            // 해시 기반 축약
            String hash = DigestUtils.md5Hex(base).substring(0, 16);
            safe = hash + "_v" + version;
        }

        return safe;
    }
}
```

---

## 참고 자료

### 공식 문서

- [document.key 스펙](https://api.onlyoffice.com/docs/docs-api/usage-api/config/document/document/#key)
- [Callback Handler](https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/)
- [Co-editing 가이드](https://api.onlyoffice.com/docs/docs-api/get-started/how-it-works/co-editing/)
- [referenceData 스펙](https://api.onlyoffice.com/docs/docs-api/usage-api/config/document/document/#referencedata)
- [Troubleshooting](https://api.onlyoffice.com/docs/docs-api/more-information/troubleshooting/)

### 관련 이슈

- [#23 OnlyOffice document.key 관리 로직 개선 필요](https://github.com/taez224/onlyoffice-demo/issues/23)

---

*마지막 업데이트: 2025-12-09*