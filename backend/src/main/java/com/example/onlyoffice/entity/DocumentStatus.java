package com.example.onlyoffice.entity;

/**
 * 문서 생명주기 상태를 나타내는 열거형.
 * JPA @Enumerated(EnumType.STRING)과 함께 사용하여 데이터베이스에 읽기 쉬운 값으로 저장됩니다.
 */
public enum DocumentStatus {
    PENDING,
    ACTIVE
}
