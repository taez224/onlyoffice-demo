package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
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

        InputStream stream = null;
        try {
            stream = documentService.downloadDocumentStream(fileKey);
            Resource resource = new InputStreamResource(stream);

            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(doc.getFileName(), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(doc.getFileSize())
                    .body(resource);
        } catch (Exception e) {
            // 예외 발생 시 stream을 명시적으로 닫아 리소스 누수 방지
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException closeException) {
                    log.warn("Failed to close stream for fileKey: {}", fileKey, closeException);
                }
            }
            throw e;
        }
    }

}
