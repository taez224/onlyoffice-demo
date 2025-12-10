package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.SecurityValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 파일 보안 검증 서비스
 * - 파일명 새니타이징
 * - 확장자 검증
 * - 파일 크기 제한
 * - MIME 타입 검증
 * - 매직 바이트 검증
 * - 압축 폭탄 방어
 */
@Slf4j
@Service
public class FileSecurityService {

    /**
     * 최대 파일 크기: 100MB
     */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * 압축 해제 최대 크기: 1GB (압축 폭탄 방어)
     */
    private static final long MAX_UNCOMPRESSED_SIZE = 1024 * 1024 * 1024L; // 1GB

    /**
     * 허용된 파일 확장자
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "docx", "xlsx", "pptx", "pdf"
    );

    /**
     * 확장자별 허용 MIME 타입 매핑
     */
    private static final Map<String, Set<String>> EXTENSION_MIME_MAP = Map.of(
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip"  // OOXML은 ZIP 기반
            ),
            "xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip"
            ),
            "pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/zip"
            ),
            "pdf", Set.of(
                    "application/pdf"
            )
    );

    private final TikaConfig tikaConfig;
    private final Detector detector;

    public FileSecurityService() throws Exception {
        this.tikaConfig = new TikaConfig();
        this.detector = tikaConfig.getDetector();
    }

    /**
     * 파일 전체 보안 검증
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SecurityValidationException("파일이 비어있습니다");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new SecurityValidationException("파일명이 없습니다");
        }

        // 1. 파일명 새니타이징 및 검증
        String sanitizedFilename = sanitizeFilename(originalFilename);

        // 2. 확장자 검증
        String extension = getFileExtension(sanitizedFilename);
        validateExtension(extension);

        // 3. 파일 크기 검증
        validateFileSize(file.getSize());

        // 4. MIME 타입 및 매직 바이트 검증
        try {
            String detectedMimeType = detectMimeType(file.getInputStream(), sanitizedFilename);
            validateMimeType(extension, detectedMimeType);

            // 5. 압축 폭탄 검증 (OOXML 파일만)
            if (isOOXMLFile(extension)) {
                validateZipBomb(file.getInputStream());
            }

            log.info("File validation passed: {} ({}), size: {} bytes, MIME: {}",
                    sanitizedFilename, extension, file.getSize(), detectedMimeType);

        } catch (IOException e) {
            throw new SecurityValidationException("파일 검증 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 파일명 새니타이징 - Path Traversal 공격 방지
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new SecurityValidationException("파일명이 비어있습니다");
        }

        // Path Traversal 패턴 제거
        String sanitized = filename.replaceAll("\\.\\./", "")
                .replaceAll("\\.\\\\", "")
                .replaceAll("\\.\\.\\\\", "")
                .replace("../", "")
                .replace("..\\", "");

        // null 바이트 제거
        sanitized = sanitized.replace("\0", "");

        // 경로 구분자 제거
        Path path = Paths.get(sanitized).getFileName();
        if (path == null) {
            throw new SecurityValidationException("유효하지 않은 파일명입니다");
        }

        sanitized = path.toString();

        // 최종 검증
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            throw new SecurityValidationException("파일명에 유효하지 않은 문자가 포함되어 있습니다");
        }

        return sanitized;
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            throw new SecurityValidationException("파일 확장자가 없습니다");
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 확장자 검증
     */
    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new SecurityValidationException(
                    String.format("허용되지 않은 파일 형식입니다. 허용 형식: %s (입력: %s)",
                            ALLOWED_EXTENSIONS, extension)
            );
        }
    }

    /**
     * 파일 크기 검증
     */
    private void validateFileSize(long size) {
        if (size > MAX_FILE_SIZE) {
            throw new SecurityValidationException(
                    String.format("파일 크기가 제한을 초과했습니다. 최대: %d MB, 현재: %.2f MB",
                            MAX_FILE_SIZE / (1024 * 1024),
                            size / (1024.0 * 1024.0))
            );
        }
    }

    /**
     * MIME 타입 감지 (Apache Tika)
     */
    private String detectMimeType(InputStream inputStream, String filename) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        try (TikaInputStream tikaStream = TikaInputStream.get(inputStream)) {
            MediaType mediaType = detector.detect(tikaStream, metadata);
            return mediaType.toString();
        }
    }

    /**
     * MIME 타입 검증 - 확장자와 일치하는지 확인
     */
    private void validateMimeType(String extension, String detectedMimeType) {
        Set<String> allowedMimeTypes = EXTENSION_MIME_MAP.get(extension);

        if (allowedMimeTypes == null) {
            throw new SecurityValidationException("지원하지 않는 확장자입니다: " + extension);
        }

        boolean isValid = allowedMimeTypes.stream()
                .anyMatch(detectedMimeType::startsWith);

        if (!isValid) {
            throw new SecurityValidationException(
                    String.format("파일 형식 불일치. 확장자: %s, 실제 MIME: %s",
                            extension, detectedMimeType)
            );
        }
    }

    /**
     * OOXML 파일 여부 확인
     */
    private boolean isOOXMLFile(String extension) {
        return extension.equals("docx") || extension.equals("xlsx") || extension.equals("pptx");
    }

    /**
     * ZIP 폭탄 검증 - 압축 해제 크기 제한
     * OOXML 파일(.docx, .xlsx, .pptx)은 ZIP 형식이므로 압축 폭탄 공격 가능
     */
    private void validateZipBomb(InputStream inputStream) throws IOException {
        long totalUncompressedSize = 0;
        int entryCount = 0;
        final int MAX_ENTRIES = 1000; // 최대 엔트리 수 제한

        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;

                if (entryCount > MAX_ENTRIES) {
                    throw new SecurityValidationException(
                            "ZIP 파일의 엔트리 수가 너무 많습니다 (최대: " + MAX_ENTRIES + ")"
                    );
                }

                // 각 엔트리의 압축 해제 크기 계산
                long entrySize = entry.getSize();
                if (entrySize > 0) {
                    totalUncompressedSize += entrySize;
                } else {
                    // 크기를 알 수 없는 경우, 실제로 읽어서 계산
                    long size = 0;
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        size += bytesRead;
                        if (size > MAX_UNCOMPRESSED_SIZE) {
                            throw new SecurityValidationException(
                                    "ZIP 압축 해제 크기가 제한을 초과했습니다 (최대: 1GB)"
                            );
                        }
                    }
                    totalUncompressedSize += size;
                }

                if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                    throw new SecurityValidationException(
                            String.format("ZIP 압축 해제 크기가 제한을 초과했습니다. 최대: 1GB, 현재: %.2f GB",
                                    totalUncompressedSize / (1024.0 * 1024.0 * 1024.0))
                    );
                }

                zis.closeEntry();
            }
        }

        log.debug("ZIP bomb validation passed. Total uncompressed size: {} bytes, entries: {}",
                totalUncompressedSize, entryCount);
    }
}
