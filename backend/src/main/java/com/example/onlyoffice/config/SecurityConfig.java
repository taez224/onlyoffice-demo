package com.example.onlyoffice.config;

import com.example.onlyoffice.exception.SecurityValidationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
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

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

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

    /**
     * 애플리케이션 시작 시 server.baseUrl 검증
     * Docker 환경과 로컬 개발 환경 모두 지원
     *
     * @throws SecurityValidationException 검증 실패 시 애플리케이션 시작 중단
     */
    @PostConstruct
    public void validateServerBaseUrl() {
        log.info("Validating server.baseUrl configuration...");

        // 1. Null/Blank 검증
        if (serverBaseUrl == null || serverBaseUrl.isBlank()) {
            throw new SecurityValidationException(
                    "server.baseUrl is required. Please set it in application.yml or environment variable.\n" +
                            "Example: server.baseUrl=http://host.docker.internal:8080 (Docker) or http://localhost:8080 (local)"
            );
        }

        // 2-4. URL Format, Scheme, Host 검증
        try {
            URI uri = URI.create(serverBaseUrl);
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new SecurityValidationException(
                        "server.baseUrl must be a valid HTTP or HTTPS URL. Current value: " + serverBaseUrl
                );
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new SecurityValidationException(
                        "server.baseUrl must include a valid host. Current value: " + serverBaseUrl
                );
            }
        } catch (IllegalArgumentException e) {
            throw new SecurityValidationException(
                    "server.baseUrl is not a valid URL format. Current value: " + serverBaseUrl +
                            "\nExample: http://host.docker.internal:8080", e
            );
        }

        // 5. Trailing Slash 검증
        if (serverBaseUrl.endsWith("/")) {
            throw new SecurityValidationException(
                    "server.baseUrl must not end with a trailing slash. This prevents double slashes in URL construction.\n" +
                            "Current value: " + serverBaseUrl + "\n" +
                            "Correct value: " + serverBaseUrl.substring(0, serverBaseUrl.length() - 1)
            );
        }

        // 6. Localhost 경고 (allow startup, just warn)
        if (serverBaseUrl.contains("localhost") || serverBaseUrl.contains("127.0.0.1")) {
            log.warn(
                    "server.baseUrl contains 'localhost' or '127.0.0.1'. " +
                            "This will NOT work in Docker environment! " +
                            "For Docker, use: http://host.docker.internal:8080"
            );
        }

        // 7. Docker Networking Info
        if (serverBaseUrl.contains("host.docker.internal")) {
            log.info("server.baseUrl is configured for Docker networking (host.docker.internal)");
        }

        log.info("server.baseUrl validation passed: {}", serverBaseUrl);
    }
}
