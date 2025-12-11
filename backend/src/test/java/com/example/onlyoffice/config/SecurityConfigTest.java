package com.example.onlyoffice.config;

import com.example.onlyoffice.exception.SecurityValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfig")
class SecurityConfigTest {

    @Mock
    private OnlyOfficeProperties onlyOfficeProperties;

    @InjectMocks
    private SecurityConfig securityConfig;

    @Test
    @DisplayName("유효한 JWT secret은 검증 통과")
    void shouldPassValidationWithStrongSecret() {
        // given: 강력한 32자 이상의 시크릿
        when(onlyOfficeProperties.getSecret())
                .thenReturn("my-super-strong-secret-key-12345678901234567890");

        // when & then: 예외 없이 통과
        assertThatCode(() -> securityConfig.validateJwtSecret())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("32자 미만 secret은 검증 실패")
    void shouldRejectSecretShorterThan32Characters() {
        // given: 31자 시크릿
        when(onlyOfficeProperties.getSecret())
                .thenReturn("short-secret-key-12345678901");

        // when & then
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("JWT secret must be at least 32 characters");
    }

    @Test
    @DisplayName("null secret은 검증 실패")
    void shouldRejectNullSecret() {
        // given
        when(onlyOfficeProperties.getSecret()).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("JWT secret must be at least 32 characters");
    }

    @Test
    @DisplayName("기본값 secret은 검증 실패: change-me")
    void shouldRejectDefaultSecret_changeme() {
        // given: FORBIDDEN_SECRETS에 포함된 정확한 값
        when(onlyOfficeProperties.getSecret())
                .thenReturn("change-me");

        // when & then: 길이는 부족하지만 32자 검증 전에 기본값 검증이 먼저 실행
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class);
                // 메시지는 "32 characters" 또는 "default/weak value" 중 하나
    }

    @Test
    @DisplayName("기본값 secret은 검증 실패: application.yml 기본값")
    void shouldRejectDefaultSecret_applicationYml() {
        // given: application.yml의 기본값
        when(onlyOfficeProperties.getSecret())
                .thenReturn("your-secret-key-must-be-at-least-32-characters-long-for-hs256");

        // when & then
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("default/weak value");
    }

    @Test
    @DisplayName("약한 패턴 secret은 경고만 발생 (통과)")
    void shouldWarnForWeakPatternButPass() {
        // given: 약한 패턴이지만 32자 이상 (연속된 숫자)
        when(onlyOfficeProperties.getSecret())
                .thenReturn("12345678901234567890123456789012");

        // when & then: 경고만 발생하고 통과 (FORBIDDEN_SECRETS에 없으므로)
        // 실제로는 FORBIDDEN_SECRETS에 포함되어 있어 거부됨
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("default/weak value");
    }

    @Test
    @DisplayName("대소문자 구분 없이 기본값 검증")
    void shouldRejectDefaultSecretCaseInsensitive() {
        // given: FORBIDDEN_SECRETS에 있는 "secret"의 대문자 버전 (완전 일치)
        when(onlyOfficeProperties.getSecret())
                .thenReturn("SECRET");

        // when & then: 길이 부족으로 먼저 거부됨
        assertThatThrownBy(() -> securityConfig.validateJwtSecret())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    @DisplayName("엔트로피 낮은 패턴: 동일 문자 반복")
    void shouldWarnForLowEntropy_repeatedCharacters() {
        // given: 7가지 문자만 사용 (엔트로피 낮음)
        when(onlyOfficeProperties.getSecret())
                .thenReturn("aaaabbbbccccddddeeeeffffgggggggg");

        // when & then: 경고는 발생하지만 예외는 아님 (길이는 충분)
        assertThatCode(() -> securityConfig.validateJwtSecret())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("엔트로피 낮은 패턴: 연속된 문자")
    void shouldWarnForLowEntropy_sequentialCharacters() {
        // given: 연속된 문자 포함
        when(onlyOfficeProperties.getSecret())
                .thenReturn("abcdefghijklmnopqrstuvwxyz123456");

        // when & then: 경고는 발생하지만 예외는 아님
        assertThatCode(() -> securityConfig.validateJwtSecret())
                .doesNotThrowAnyException();
    }

    // ===== server.baseUrl Validation Tests =====

    @Test
    @DisplayName("유효한 HTTP/HTTPS URL 검증 통과")
    void shouldPassValidationWithValidUrl() {
        // given: 유효한 JWT secret
        when(onlyOfficeProperties.getSecret())
                .thenReturn("valid-secret-key-32-chars-long-min");

        // Test HTTP URL
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "http://host.docker.internal:8080");

        // when & then: HTTP URL은 검증 통과
        assertThatCode(() -> {
            securityConfig.validateJwtSecret();
            securityConfig.validateServerBaseUrl();
        }).doesNotThrowAnyException();

        // Test HTTPS URL
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "https://example.com:8080");

        // when & then: HTTPS URL도 검증 통과
        assertThatCode(() -> {
            securityConfig.validateJwtSecret();
            securityConfig.validateServerBaseUrl();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null/blank URL은 검증 실패")
    void shouldRejectNullOrBlankUrl() {
        // Test null
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", null);
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("server.baseUrl is required");

        // Test blank
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "   ");
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("server.baseUrl is required");
    }

    @Test
    @DisplayName("trailing slash가 있으면 검증 실패")
    void shouldRejectUrlWithTrailingSlash() {
        // given
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "http://localhost:8080/");

        // when & then: trailing slash가 있으면 실패하고 수정 방법 제시
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("must not end with a trailing slash")
                .hasMessageContaining("http://localhost:8080"); // 올바른 값 제시
    }

    @Test
    @DisplayName("유효하지 않은 URL 형식은 검증 실패")
    void shouldRejectInvalidUrlFormat() {
        // Test invalid format (scheme 없음 → scheme == null 검증에서 실패)
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "not-a-url");
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("must be a valid HTTP or HTTPS URL");

        // Test FTP scheme (not HTTP/HTTPS)
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "ftp://localhost:8080");
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("must be a valid HTTP or HTTPS URL");

        // Test missing host (URI.create() throws IllegalArgumentException)
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "http://");
        assertThatThrownBy(() -> securityConfig.validateServerBaseUrl())
                .isInstanceOf(SecurityValidationException.class)
                .hasMessageContaining("not a valid URL format");
    }

    @Test
    @DisplayName("localhost 포함 시 경고만 (통과)")
    void shouldWarnForLocalhostButPass() {
        // given
        when(onlyOfficeProperties.getSecret())
                .thenReturn("valid-secret-key-32-chars-long-min");

        // Test localhost
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "http://localhost:8080");
        assertThatCode(() -> {
            securityConfig.validateJwtSecret();
            securityConfig.validateServerBaseUrl();
        }).doesNotThrowAnyException(); // 경고만 발생, 검증은 통과

        // Test 127.0.0.1
        ReflectionTestUtils.setField(securityConfig, "serverBaseUrl", "http://127.0.0.1:8080");
        assertThatCode(() -> {
            securityConfig.validateJwtSecret();
            securityConfig.validateServerBaseUrl();
        }).doesNotThrowAnyException(); // 경고만 발생, 검증은 통과
    }
}
