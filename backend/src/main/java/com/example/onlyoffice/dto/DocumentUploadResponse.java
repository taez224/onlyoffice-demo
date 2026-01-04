package com.example.onlyoffice.dto;

import com.example.onlyoffice.entity.Document;

/**
 * 문서 업로드 응답 DTO.
 * 업로드된 문서의 핵심 정보를 반환합니다.
 */
public record DocumentUploadResponse(
        Long id,
        String fileName,
        String fileKey,
        String fileType,
        String documentType,
        Long fileSize,
        String message
) {
    private static final String SUCCESS_MESSAGE = "Document uploaded successfully";

    public static DocumentUploadResponse from(Document document) {
        return new DocumentUploadResponse(
                document.getId(),
                document.getFileName(),
                document.getFileKey(),
                document.getFileType(),
                document.getDocumentType(),
                document.getFileSize(),
                SUCCESS_MESSAGE
        );
    }
}
