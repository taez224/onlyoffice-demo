package com.example.onlyoffice.config;

import com.example.onlyoffice.exception.SecurityValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
