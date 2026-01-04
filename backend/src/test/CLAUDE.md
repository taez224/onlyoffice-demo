# Backend Test Guide

## 테스트 스택
- JUnit 5 + AssertJ
- Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`)
- Mockito for mocking

## 테스트 컨벤션

### 1. Mock 어노테이션: `@MockitoBean` 사용

Spring Boot 3.4.0부터 `@MockBean`이 deprecated되었습니다. 새로운 `@MockitoBean`을 사용하세요.

```java
// Bad - deprecated, 4.0.0에서 제거 예정
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean
private SomeService someService;

// Good - Spring Boot 3.4.0+
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean
private SomeService someService;
```

### 2. Controller 테스트: `MockMvcTester` 사용

Spring Boot 3.4.0에서 추가된 `MockMvcTester`는 AssertJ 기반의 fluent API를 제공합니다.

```java
// Bad - 기존 MockMvc 방식
@Autowired
private MockMvc mockMvc;

@Test
void test() throws Exception {
    mockMvc.perform(get("/api/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
}

// Good - MockMvcTester 방식
@Autowired
private MockMvcTester mvc;

@Test
void test() {
    MvcTestResult result = mvc.get().uri("/api/documents").exchange();
    
    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(1);
}
```

#### MockMvcTester 장점
- `throws Exception` 불필요
- AssertJ fluent API로 가독성 향상
- `@WebMvcTest`에서 AssertJ가 classpath에 있으면 자동 설정

#### MockMvcTester 주요 메서드

```java
// HTTP 메서드
mvc.get().uri("/api/documents")
mvc.post().uri("/api/documents/upload")
mvc.delete().uri("/api/documents/{id}", 1L)
mvc.put().uri("/api/documents/{id}", 1L)

// Multipart 업로드
mvc.post().uri("/api/documents/upload")
    .multipart()
    .file(mockMultipartFile)
    .exchange();

// 결과 검증
MvcTestResult result = mvc.get().uri("/api/documents").exchange();

assertThat(result).hasStatusOk();           // 200
assertThat(result).hasStatus(201);          // 201 Created
assertThat(result).hasStatus(204);          // 204 No Content
assertThat(result).hasStatus(404);          // 404 Not Found

// JSON 검증
assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(1);
assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("test");
```

### 3. 테스트 클래스 구조

```java
@WebMvcTest(SomeController.class)
@Import(GlobalExceptionHandler.class)  // 예외 핸들러 포함
@DisplayName("SomeController")
class SomeControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SomeService someService;

    @Nested
    @DisplayName("GET /api/endpoint")
    class GetEndpoint {
        
        @Test
        @DisplayName("성공 케이스 설명")
        void shouldReturnExpectedResult() {
            // given
            when(someService.method()).thenReturn(expected);

            // when
            MvcTestResult result = mvc.get().uri("/api/endpoint").exchange();

            // then
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.field").isEqualTo(expected);
        }
    }
}
```

### 4. 테스트 네이밍 규칙

- 클래스: `{ClassName}Test`
- 메서드: `should{ExpectedBehavior}` 또는 `{methodName}_{scenario}_{expectedResult}`
- `@DisplayName`: 한글로 명확하게 설명

### 5. 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.example.onlyoffice.controller.DocumentControllerTest"

# 특정 테스트 메서드
./gradlew test --tests "*.DocumentControllerTest.shouldReturnActiveDocuments"
```
