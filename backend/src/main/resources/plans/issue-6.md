# Issue #6: ONLYOFFICE SDK Manager + 보안 서비스 구현

## 이슈 요약
- **무엇을**: ONLYOFFICE SDK Manager 및 보안 서비스 구현
- **왜**: JWT 검증, 파일 보안, 시크릿 검증 강화
- **영향 범위**: Backend only

## 현재 상태 분석

### 기존 구현
- `JwtManager.java`: JJWT 0.13.0 사용, HS256 서명/검증 (기본 기능만)
- `KeyUtils.java`: document.key 생성 유틸리티
- `DocumentService.java`: 파일 I/O 및 문서 관리
- Apache Tika 3.2.3 이미 build.gradle에 포함

### 의존성 확인
- Issue #2 (Spring Boot 의존성) - 이미 완료됨
- 필요한 라이브러리 대부분 이미 존재

---

## 구현 계획

### Phase 1: 시크릿 검증 (애플리케이션 시작 시)

**파일**: `backend/src/main/java/com/example/onlyoffice/config/SecurityConfig.java` (신규)

```
1. @Configuration 클래스 생성
2. @PostConstruct 또는 ApplicationRunner로 시작 시 검증
3. 검증 로직:
   - JWT Secret 최소 32자 확인
   - 기본값 거부: "change-me", "secret", "your-secret-key" 등
   - 검증 실패 시 IllegalStateException으로 애플리케이션 시작 중단
```

### Phase 2: ONLYOFFICE SDK Manager Beans

**파일들**:
- `backend/src/main/java/com/example/onlyoffice/config/OnlyOfficeProperties.java` (신규)
- `backend/src/main/java/com/example/onlyoffice/service/ConfigService.java` (신규)

```
1. SettingsManager Bean (@ConfigurationProperties)
   - @ConfigurationProperties("onlyoffice")
   - JWT Secret, Server URL 관리
   - @Validated로 JSR-303 검증

2. DocumentManager Bean
   - 기존 KeyUtils.java 기능 통합
   - 문서 키 생성/검증 로직

3. ConfigService Bean
   - 에디터 Config JSON 생성 로직
   - EditorController의 config 생성 로직 추출/리팩토링
```

### Phase 3: JWT 검증 서비스 강화

**파일**: `backend/src/main/java/com/example/onlyoffice/util/JwtManager.java` (수정)

```
기존 JwtManager 확장 (JJWT 0.13.0 유지):
1. 만료 시간(exp) 검증 추가
2. 발행 시간(iat) 검증 추가
3. leeway 설정 (클럭 스큐 허용)
4. 더 상세한 예외 처리
```

### Phase 4: 파일 보안 서비스

**파일**: `backend/src/main/java/com/example/onlyoffice/service/FileSecurityService.java` (신규)

```
1. 파일명 새니타이징
   - Path Traversal 공격 방지 (../, .\\ 등 제거)
   - 특수문자 제거/치환

2. 확장자 검증
   - 허용: .docx, .xlsx, .pptx, .pdf
   - 대소문자 무관 처리

3. 파일 크기 제한
   - 최대 100MB (104,857,600 bytes)

4. MIME 타입 검증 (Apache Tika 3.2.3)
   - TikaConfig + Detector 사용
   - 확장자-MIME 매핑 검증

5. 매직 바이트 검증
   - 실제 파일 내용의 매직 바이트 확인
   - 확장자 위장 파일 탐지

6. 압축 폭탄 방어
   - OOXML 파일(.docx, .xlsx, .pptx)은 ZIP 포맷
   - 압축 해제 예상 크기 1GB 제한
```

---

## 파일 목록

### 신규 생성
| 파일 | 설명 |
|------|------|
| `config/SecurityConfig.java` | 시크릿 검증 및 보안 설정 |
| `config/OnlyOfficeProperties.java` | @ConfigurationProperties 클래스 |
| `service/FileSecurityService.java` | 파일 보안 검증 서비스 |
| `service/ConfigService.java` | 에디터 Config 생성 서비스 |
| `exception/SecurityValidationException.java` | 보안 검증 예외 |

### 수정
| 파일 | 변경 내용 |
|------|----------|
| `util/JwtManager.java` | 만료 시간 검증 추가 |
| `controller/CallbackController.java` | FileSecurityService 통합 |
| `controller/EditorController.java` | ConfigService로 로직 이관 |
| `application.yml` | 보안 관련 설정 추가 |

---

## 보안 체크리스트

- [ ] Path Traversal 공격 차단 확인
- [ ] 확장자-MIME 불일치 파일 거부
- [ ] ZIP 폭탄 차단 테스트
- [ ] 대용량 파일(100MB+) 거부 확인
- [ ] 약한 시크릿으로 시작 시 실패 확인

---

## 예상 구현 순서

1. `SecurityConfig.java` - 시크릿 검증 (시작 차단 기능)
2. `OnlyOfficeProperties.java` - 설정 클래스
3. `SecurityValidationException.java` - 예외 클래스
4. `FileSecurityService.java` - 파일 보안 서비스
5. `JwtManager.java` 수정 - 만료 시간 검증
6. `ConfigService.java` - Config 생성 서비스
7. 컨트롤러들에 서비스 통합
8. 테스트 작성

---

## 결정 사항

### JWT 라이브러리: JJWT 0.13.0 유지
- 기존 코드 호환성 유지
- JJWT도 만료 시간 검증 등 필요한 기능 모두 지원
- 리팩토링 최소화

---

## 참고 자료

### Apache Tika MIME 감지
```java
TikaConfig tika = new TikaConfig();
Metadata metadata = new Metadata();
try (TikaInputStream stream = TikaInputStream.get(file, metadata)) {
    MediaType mediaType = tika.getDetector().detect(stream, metadata);
    System.out.println("Detected: " + mediaType.toString());
}
```

### JJWT 토큰 검증 (만료 시간 포함)
```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
Jwts.parser()
    .verifyWith(key)
    .clockSkewSeconds(60)  // 1분 허용
    .build()
    .parseSignedClaims(token);
```

### Spring Boot 시작 시 검증
```java
@Configuration
public class SecurityConfig {
    @PostConstruct
    public void validateSecrets() {
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
    }
}
```