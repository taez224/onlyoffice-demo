package com.example.onlyoffice.config;

import com.example.onlyoffice.exception.SecurityValidationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 보안 설정 및 애플리케이션 시작 시 검증
 * - JWT Secret 강도 검증
 * - 기본값/약한 시크릿 거부
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OnlyOfficeProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final OnlyOfficeProperties onlyOfficeProperties;

    /**
     * 거부할 약한 시크릿 목록
     */
    private static final List<String> FORBIDDEN_SECRETS = Arrays.asList(
            "change-me",
            "secret",
            "your-secret-key",
            "your-secret-key-must-be-at-least-32-characters-long-for-hs256",
            "password",
            "12345678901234567890123456789012",
            "00000000000000000000000000000000"
    );

    /**
     * 애플리케이션 시작 시 JWT Secret 검증
     *
     * @throws SecurityValidationException 검증 실패 시 애플리케이션 시작 중단
     */
    @PostConstruct
    public void validateJwtSecret() {
        String secret = onlyOfficeProperties.getSecret();

        log.info("Validating JWT secret configuration...");

        // 1. 최소 길이 검증 (HS256 요구사항)
        if (secret == null || secret.length() < 32) {
            throw new SecurityValidationException(
                    "JWT secret must be at least 32 characters for HS256 algorithm. Current length: " +
                            (secret == null ? 0 : secret.length())
            );
        }

        // 2. 기본값 거부
        if (FORBIDDEN_SECRETS.contains(secret.toLowerCase())) {
            throw new SecurityValidationException(
                    "JWT secret is using a default/weak value. Please set a strong secret in application.yml or environment variable JWT_SECRET"
            );
        }

        // 3. 엔트로피 검증 (선택적 - 너무 단순한 패턴 거부)
        if (isWeakPattern(secret)) {
            log.warn("JWT secret appears to use a simple pattern. Consider using a stronger secret for production.");
        }

        log.info("JWT secret validation passed. Secret length: {} characters", secret.length());
    }

    /**
     * 약한 패턴 검사
     * - 동일 문자 반복
     * - 연속된 숫자/문자
     */
    private boolean isWeakPattern(String secret) {
        // 동일 문자 반복 (예: "aaaaaaaa...")
        if (secret.chars().distinct().count() < 8) {
            return true;
        }

        // 연속된 숫자 (예: "12345678901234567890...")
        if (secret.matches(".*(?:0123|1234|2345|3456|4567|5678|6789).*")) {
            return true;
        }

        // 연속된 문자 (예: "abcdefgh...")
        if (secret.toLowerCase().matches(".*(?:abcd|bcde|cdef|defg|efgh|fghi).*")) {
            return true;
        }

        return false;
    }
}
