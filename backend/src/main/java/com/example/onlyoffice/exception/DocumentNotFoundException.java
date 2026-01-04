package com.example.onlyoffice.exception;

/**
 * Document를 찾을 수 없을 때 발생하는 예외
 * <p>
 * fileKey 또는 fileName으로 Document를 조회했으나
 * 데이터베이스에 존재하지 않거나 삭제된 경우 발생합니다.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long id) {
        super("Document not found for id: " + id);
    }

    public DocumentNotFoundException(String message) {
        super(message);
    }

    public DocumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
