package com.example.onlyoffice.exception;

/**
 * 보안 검증 실패 시 발생하는 예외
 * - 파일 보안 검증 실패
 * - JWT Secret 검증 실패
 * - 파일명, MIME 타입, 매직 바이트 검증 실패
 */
public class SecurityValidationException extends RuntimeException {

    public SecurityValidationException(String message) {
        super(message);
    }

    public SecurityValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
