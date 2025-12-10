package com.example.onlyoffice.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * JWT 토큰 생성 및 검증 관리자
 * - HS256 알고리즘 사용
 * - 만료 시간 검증
 * - Clock Skew 허용 (60초)
 */
@Slf4j
@Component
public class JwtManager {

    @Value("${onlyoffice.secret}")
    private String secret;

    /**
     * JWT 토큰 유효 시간 (기본: 1시간)
     */
    @Value("${onlyoffice.jwt.expiration-hours:1}")
    private int expirationHours;

    /**
     * Clock Skew 허용 시간 (초)
     * 서버 간 시간 차이 허용
     */
    private static final long CLOCK_SKEW_SECONDS = 60; // 1분

    /**
     * JWT 토큰 생성
     *
     * @param payloadClaims payload에 포함할 claims
     * @return JWT 토큰
     */
    public String createToken(Map<String, Object> payloadClaims) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationHours, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .claims(payloadClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.debug("JWT token created. Expires at: {}", expiration);
        return token;
    }

    /**
     * JWT 토큰 검증
     * - 서명 검증
     * - 만료 시간 검증
     * - Clock Skew 허용
     *
     * @param token JWT 토큰 (Bearer prefix 허용)
     * @return 검증 성공 여부
     */
    public boolean validateToken(String token) {
        // Bearer prefix 제거
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isBlank()) {
            log.warn("JWT token is null or empty");
            return false;
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)  // Clock Skew 허용
                    .build()
                    .parseSignedClaims(token);

            log.debug("JWT token validation successful");
            return true;

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.error("JWT signature validation failed: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.error("JWT token is malformed: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("JWT token validation failed with unexpected error: {}", e.getMessage(), e);
            return false;
        }
    }

}
