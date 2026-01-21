package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.service.MinioStorageService;
import com.example.onlyoffice.util.KeyUtils;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class FileController {

    private static final int BUFFER_SIZE = 65536; // 64KB buffer for optimal streaming

    private final DocumentService documentService;
    private final MinioStorageService storageService;

    /**
     * 파일 다운로드 엔드포인트
     * <p>
     * StreamingResponseBody를 사용하여 리소스 누수를 방지합니다.
     * try-with-resources를 통해 모든 시나리오(정상 완료, 클라이언트 연결 끊김,
     * 네트워크 타임아웃 등)에서 스트림이 안전하게 닫힙니다.
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @return StreamingResponseBody로 래핑된 파일 스트림
     */
    @GetMapping("/files/{fileKey}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable @Pattern(regexp = KeyUtils.UUID_REGEX, message = "Invalid fileKey format") String fileKey) {
        Document doc = documentService.findByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        String storagePath = doc.getStoragePath();
        log.debug("Starting file download for fileKey: {}, fileName: {}", fileKey, doc.getFileName());

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(doc.getFileName(), StandardCharsets.UTF_8)
                .build();

        StreamingResponseBody streamingBody = outputStream -> {
            streamFileContent(fileKey, storagePath, outputStream);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(doc.getFileSize())
                .body(streamingBody);
    }

    /**
     * 파일 내용을 출력 스트림으로 전송합니다.
     * <p>
     * try-with-resources를 사용하여 입력 스트림이 항상 닫히도록 보장합니다.
     * 이는 MinIO 연결 풀 고갈을 방지합니다.
     *
     * @param fileKey      파일 고유 식별자 (로깅용)
     * @param storagePath  스토리지 경로
     * @param outputStream HTTP 응답 출력 스트림
     */
    private void streamFileContent(String fileKey, String storagePath, OutputStream outputStream) {
        try (InputStream inputStream = storageService.downloadFile(storagePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.flush();
            log.debug("File streaming completed for fileKey: {}, totalBytes: {}", fileKey, totalBytes);
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during download for fileKey: {}", fileKey);
            } else {
                log.error("Stream failed for fileKey: {} - {}", fileKey, e.getMessage());
            }
            throw new UncheckedIOException("Failed to stream file: " + fileKey, e);
        }
    }

    /**
     * 클라이언트 연결 끊김 여부를 확인합니다.
     * <p>
     * 사용자가 다운로드를 취소하거나 브라우저를 닫은 경우는 정상적인 시나리오이므로
     * 서버 오류와 구분하여 DEBUG 레벨로 로깅합니다.
     *
     * @param e 발생한 IOException
     * @return 클라이언트 연결 끊김이면 true
     */
    private boolean isClientDisconnect(IOException e) {
        String exceptionName = e.getClass().getSimpleName();
        String message = e.getMessage();

        return "ClientAbortException".equals(exceptionName)
                || (message != null && (message.contains("Broken pipe")
                        || message.contains("Connection reset")));
    }

}
