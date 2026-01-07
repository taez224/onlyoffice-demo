# Spring Boot 4.0 Migration Guide

> **마이그레이션 일자**: 2026년 1월  
> **이전 버전**: Spring Boot 3.5.8, Gradle 8.6  
> **현재 버전**: Spring Boot 4.0.1, Gradle 8.14, Hibernate 7, Spring Framework 7

## 개요

이 문서는 ONLYOFFICE Demo 프로젝트의 Spring Boot 4.0 마이그레이션 과정을 기록합니다.

### 주요 변경 사항

| 구분 | 이전 | 이후                        |
|------|------|---------------------------|
| Spring Boot | 3.5.8 | **4.0.1**                 |
| Gradle | 8.6 | **8.14**                  |
| Hibernate | 6.x | **7.x**                   |
| Spring Framework | 6.x | **7.x**                   |
| Jackson | 3.0 (기본) | 3.0 + **Jackson 2 호환 모듈** |

---

## 1. 의존성 변경

### build.gradle

```groovy
plugins {
    id 'org.springframework.boot' version '4.0.1'  // 3.5.8 → 4.0.1
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    // Jackson 2 하위 호환성 (JJWT, ONLYOFFICE SDK용)
    implementation 'org.springframework.boot:spring-boot-jackson2'
    
    // AOP → AspectJ 이름 변경
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'  // 이전: starter-aop
    
    // Spring Retry - Spring Framework 7에 retry 기능 내장으로 BOM에서 제거됨
    // 마이그레이션 권장: Spring Framework의 새 retry 기능으로 전환
    // 참고: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes
    implementation 'org.springframework.retry:spring-retry:2.0.12'
    
    // 테스트 모듈화된 스타터
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}
```

### gradle-wrapper.properties

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
```

---

## 2. Hibernate 7 - @SoftDelete 마이그레이션

### 이전 방식 (수동 Soft Delete)

```java
// Entity
@Column(name = "deleted_at")
private LocalDateTime deletedAt;

// Repository
Optional<Document> findByFileKeyAndDeletedAtIsNull(String fileKey);

// Service
document.setDeletedAt(LocalDateTime.now());
documentRepository.save(document);
```

### 현재 방식 (Hibernate 7 @SoftDelete)

```java
// Entity
@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Document {
    // deletedAt 필드 제거 - Hibernate가 자동 관리
}

// Repository - AndDeletedAtIsNull 접미사 불필요
Optional<Document> findByFileKey(String fileKey);  // 삭제된 문서 자동 필터링

// 복원용 native query
@Modifying
@Query(value = "UPDATE documents SET deleted_at = NULL, status = 'ACTIVE' WHERE id = :id", nativeQuery = true)
int restore(@Param("id") Long id);

// Service
documentRepository.delete(document);  // 자동으로 deleted_at 설정
documentRepository.restore(id);       // 보상 트랜잭션용
```

### 변경된 Repository 메서드

| 이전 | 이후 |
|------|------|
| `findByFileKeyAndDeletedAtIsNull()` | `findByFileKey()` |
| `findByFileNameAndDeletedAtIsNull()` | `findByFileName()` |
| `findByStatusAndDeletedAtIsNull()` | `findAllByStatus()` |
| `findWithLockByFileKeyAndDeletedAtIsNull()` | `findWithLockByFileKey()` |
| `softDelete(id, timestamp)` | `delete(entity)` + `restore(id)` |

---

## 3. Spring Framework 7 API 변경

### UriComponentsBuilder

```java
// 이전
UriComponentsBuilder.fromHttpUrl(serverBaseUrl)

// 이후
UriComponentsBuilder.fromUriString(serverBaseUrl)
```

---

## 4. 테스트 마이그레이션

### @MockBean → @MockitoBean

Spring Boot 4.0에서 `@MockBean`이 제거되었습니다.

```java
// 이전 (제거됨)
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean
private SomeService someService;

// 이후
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean
private SomeService someService;
```

### 테스트 어노테이션 import 경로 변경

```java
// DataJpaTest
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// WebMvcTest
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

// TestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
```

### Soft Delete 테스트 패턴

```java
@Test
void softDelete() {
    // when
    documentRepository.delete(document);
    documentRepository.flush();

    // then - 삭제된 문서는 조회되지 않음
    assertThat(documentRepository.findById(id)).isEmpty();
}

@Test
void restore() {
    // given - 삭제된 문서
    documentRepository.delete(document);
    documentRepository.flush();

    // when
    int count = documentRepository.restore(id);

    // then
    assertThat(count).isEqualTo(1);
    Document restored = documentRepository.findById(id).orElseThrow();
    assertThat(restored.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
}
```

---

## 5. 변경된 파일 목록

### 설정 파일

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle` | Spring Boot 4.0, 의존성 변경 |
| `gradle-wrapper.properties` | Gradle 8.14 |

### 소스 코드

| 파일 | 변경 내용 |
|------|----------|
| `Document.java` | `@SoftDelete` 추가, `deletedAt` 필드 제거 |
| `DocumentRepository.java` | 메서드명 변경, `restore()` 추가 |
| `DocumentService.java` | `delete()` + `restore()` 패턴 적용 |
| `FileMigrationService.java` | Repository 메서드명 변경 |
| `CustomUrlManager.java` | `fromHttpUrl()` → `fromUriString()` |

### 테스트 코드

| 파일 | 변경 내용 |
|------|----------|
| `CallbackControllerTest.java` | import 경로 변경 |
| `DocumentControllerTest.java` | import 경로 변경 |
| `DocumentTest.java` | `deletedAt` 검증 제거 |
| `DocumentRepositoryTest.java` | Soft delete 테스트 패턴 변경 |
| `DocumentRepositoryLockTest.java` | Repository 메서드명 변경 |
| `DocumentServiceTest.java` | Mock 메서드명 변경, `restore()` 검증 |
| `DocumentServiceEndToEndTest.java` | 의존성 주입 방식 변경 |
| `FileMigrationServiceTest.java` | Repository 메서드명 변경 |

---

## 6. Jackson 2 호환성

Spring Boot 4.0은 기본적으로 Jackson 3.0을 사용하지만, 다음 라이브러리들은 Jackson 2.x에 의존합니다:

- **JJWT** (io.jsonwebtoken)
- **ONLYOFFICE Java SDK**

`spring-boot-jackson2` 모듈을 추가하여 하위 호환성을 유지합니다:

```groovy
implementation 'org.springframework.boot:spring-boot-jackson2'
```

---

## 7. 마이그레이션 체크리스트

- [x] Gradle 8.14 업그레이드
- [x] Spring Boot 4.0.1 업그레이드
- [x] Jackson 2 호환 모듈 추가
- [x] `@SoftDelete` 적용 (Hibernate 7)
- [x] Repository 메서드명 변경 (`AndDeletedAtIsNull` 제거)
- [x] `restore()` native query 추가
- [x] Spring Framework 7 API 변경 (`fromUriString`)
- [x] 테스트 `@MockitoBean` 마이그레이션
- [x] 테스트 import 경로 변경
- [x] 전체 테스트 통과 확인
- [x] 문서화 (AGENTS.md, CLAUDE.md) 업데이트

### 향후 작업 (TODO)

- [ ] **Spring Retry → Spring Framework 7 Retry 전환**: Spring Framework 7에 retry 기능이 내장되어 `spring-retry` 라이브러리 의존성 관리가 Spring Boot BOM에서 제거됨. 점진적으로 Spring Framework의 새 retry API로 마이그레이션 권장.

---

## 8. 참고 자료

- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Hibernate 7 Migration Guide](https://docs.jboss.org/hibernate/orm/7.0/migration-guide/migration-guide.html)
- [Spring Framework 7 What's New](https://docs.spring.io/spring-framework/reference/7.0/whats-new.html)
