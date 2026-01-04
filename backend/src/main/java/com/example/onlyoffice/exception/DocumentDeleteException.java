package com.example.onlyoffice.exception;

/**
 * Thrown when a document deletion fails to remove the MinIO object.
 */
public class DocumentDeleteException extends RuntimeException {

    public DocumentDeleteException(String message) {
        super(message);
    }

    public DocumentDeleteException(String message, Throwable cause) {
        super(message, cause);
    }
}
