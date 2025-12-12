package com.example.onlyoffice.exception;

/**
 * Thrown when a document upload fails after the DB record is created.
 */
public class DocumentUploadException extends RuntimeException {

    public DocumentUploadException(String message) {
        super(message);
    }

    public DocumentUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
