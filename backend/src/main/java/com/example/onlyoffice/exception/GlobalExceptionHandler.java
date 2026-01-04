package com.example.onlyoffice.exception;

import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러.
 *
 * <p>애플리케이션 전체에서 발생하는 예외를 처리하고 적절한 HTTP 응답을 반환합니다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비관적 락 타임아웃 예외 처리.
     *
     * <p>다른 트랜잭션이 문서를 잠그고 있을 때 발생합니다.
     * 클라이언트는 잠시 후 재시도해야 합니다.</p>
     *
     * @param e 비관적 락 예외
     * @return HTTP 409 Conflict 응답
     */
    @ExceptionHandler(PessimisticLockException.class)
    public ProblemDetail handlePessimisticLockException(PessimisticLockException e) {
        log.warn("Pessimistic lock timeout occurred", e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "문서가 다른 사용자에 의해 사용 중입니다. 잠시 후 다시 시도하세요."
        );
        problemDetail.setTitle("Resource Locked");

        return problemDetail;
    }

    /**
     * 문서 업로드 실패 예외 처리.
     *
     * @param e 문서 업로드 예외
     * @return HTTP 500 Internal Server Error 응답
     */
    @ExceptionHandler(DocumentUploadException.class)
    public ProblemDetail handleDocumentUploadException(DocumentUploadException e) {
        log.error("Document upload failed", e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
        );
        problemDetail.setTitle("Upload Failed");

        return problemDetail;
    }

    /**
     * 문서 삭제 실패 예외 처리.
     *
     * @param e 문서 삭제 예외
     * @return HTTP 500 Internal Server Error 응답
     */
    @ExceptionHandler(DocumentDeleteException.class)
    public ProblemDetail handleDocumentDeleteException(DocumentDeleteException e) {
        log.error("Document delete failed", e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
        );
        problemDetail.setTitle("Delete Failed");

        return problemDetail;
    }

    /**
     * 문서를 찾을 수 없음 예외 처리.
     *
     * @param e 문서 미발견 예외
     * @return HTTP 404 Not Found 응답
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleDocumentNotFoundException(DocumentNotFoundException e) {
        log.warn("Document not found: {}", e.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        problemDetail.setTitle("Document Not Found");

        return problemDetail;
    }

    /**
     * 스토리지 예외 처리.
     *
     * @param e 스토리지 예외
     * @return HTTP 500 Internal Server Error 응답
     */
    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorageException(StorageException e) {
        log.error("Storage operation failed", e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 스토리지 작업 중 오류가 발생했습니다."
        );
        problemDetail.setTitle("Storage Error");

        return problemDetail;
    }
}
