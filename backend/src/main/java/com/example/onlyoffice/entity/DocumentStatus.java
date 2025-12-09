package com.example.onlyoffice.entity;

/**
 * 문서 생명주기 상태를 나타내는 열거형.
 * JPA @Enumerated(EnumType.STRING)과 함께 사용하여 데이터베이스에 읽기 쉬운 값으로 저장됩니다.
 */
public enum DocumentStatus {
    /**
     * 업로드 진행 중 상태.
     * 문서가 처음 생성되었을 때의 초기 상태입니다 (MinIO 업로드 전).
     */
    PENDING,

    /**
     * 정상 사용 가능 상태.
     * 처리가 완료된 후의 일반적인 운영 상태입니다.
     */
    ACTIVE,

    /**
     * 삭제됨 상태 (soft delete).
     * deletedAt 타임스탬프와 함께 사용됩니다.
     */
    DELETED
}
