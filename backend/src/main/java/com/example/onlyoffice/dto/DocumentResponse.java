package com.example.onlyoffice.dto;

import com.example.onlyoffice.entity.Document;

import java.time.LocalDateTime;

/**
 * 문서 응답 DTO.
 * Entity 직접 노출을 방지하고 필요한 필드만 클라이언트에 전달합니다.
 */
public record DocumentResponse(
        Long id,
        String fileName,
        String fileKey,
        String fileType,
        String documentType,
        Long fileSize,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getFileKey(),
                document.getFileType(),
                document.getDocumentType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getCreatedBy()
        );
    }
}
