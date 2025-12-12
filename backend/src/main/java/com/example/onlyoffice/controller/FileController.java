package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

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
        // fileKey로 파일 가져오기
        File file = documentService.getFileByFileKey(fileKey);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        // 원본 fileName을 Content-Disposition header에 사용
        Document doc = documentService.findByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + doc.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
