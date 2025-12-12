package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final DocumentService documentService;

    /**
     * 파일 다운로드 엔드포인트
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @return 파일 리소스
     */
    @GetMapping("/files/{fileKey}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileKey) {
        Document doc = documentService.findByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        Resource resource = new InputStreamResource(documentService.downloadDocumentStream(fileKey));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(doc.getFileSize())
                .body(resource);
    }
}
