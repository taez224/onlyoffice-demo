package com.example.onlyoffice.controller;

import com.example.onlyoffice.dto.DocumentResponse;
import com.example.onlyoffice.dto.DocumentUploadResponse;
import com.example.onlyoffice.dto.EditorConfigResponse;
import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.service.EditorConfigService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Document REST API 컨트롤러.
 * 문서 CRUD 및 에디터 설정 API를 통합 제공합니다.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String UUID_PATTERN = "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$";

    private final DocumentService documentService;
    private final EditorConfigService editorConfigService;

    /**
     * 문서 목록 조회 (ACTIVE 상태만).
     *
     * @return ACTIVE 상태의 문서 목록
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments() {
        log.info("Fetching active documents list");
        List<Document> documents = documentService.getActiveDocuments();
        List<DocumentResponse> response = documents.stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 문서 업로드.
     *
     * @param file 업로드할 파일
     * @return 업로드된 문서 정보
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        log.info("Uploading document: {}", file.getOriginalFilename());
        Document document = documentService.uploadDocument(file);
        DocumentUploadResponse response = DocumentUploadResponse.from(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 문서 삭제 (Soft Delete).
     *
     * @param fileKey 문서 고유 식별자 (UUID 형식)
     * @return 204 No Content
     */
    @DeleteMapping("/{fileKey}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid fileKey format") String fileKey) {
        log.info("Deleting document with fileKey: {}", fileKey);

        Document document = documentService.findByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        documentService.deleteDocument(document.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 문서 에디터 설정 조회.
     *
     * @param fileKey 문서 고유 식별자 (UUID 형식)
     * @return ONLYOFFICE 에디터 설정
     */
    @GetMapping("/{fileKey}/config")
    public ResponseEntity<EditorConfigResponse> getEditorConfig(
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "Invalid fileKey format") String fileKey) {
        log.info("Fetching editor config for document fileKey: {}", fileKey);

        // SDK 내부에서 DocumentNotFoundException 발생
        Map<String, Object> editorResponse = editorConfigService
                .createEditorResponseByFileKey(fileKey);
        EditorConfigResponse response = EditorConfigResponse.from(editorResponse);

        return ResponseEntity.ok(response);
    }
}
