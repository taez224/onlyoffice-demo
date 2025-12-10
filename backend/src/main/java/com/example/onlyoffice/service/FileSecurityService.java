package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.SecurityValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
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
     * <p>
     * 메모리 효율성을 위해 BufferedInputStream의 mark/reset 기능을 사용하여
     * 파일을 byte[] 배열로 전체 로드하지 않고 스트림을 재사용합니다.
     * <p>
     * mark 한도: MAX_FILE_SIZE (100MB)
     * - 이 크기를 초과하는 파일은 이미 validateFileSize()에서 거부됨
     * - BufferedInputStream이 내부 버퍼링을 통해 효율적으로 처리
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

        // 4. InputStream을 한 번만 열고 BufferedInputStream의 mark/reset으로 재사용
        // 이 방식은 파일을 byte[] 배열로 전체 로드하지 않아 메모리 효율적입니다.
        try (InputStream inputStream = file.getInputStream();
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {

            // mark 설정: 전체 파일을 커버할 수 있을 만큼 (MAX_FILE_SIZE + 1)
            // MAX_FILE_SIZE를 초과하는 파일은 이미 위에서 거부되므로 안전합니다.
            bufferedStream.mark((int) MAX_FILE_SIZE + 1);

            // 5. MIME 타입 및 매직 바이트 검증
            String detectedMimeType = detectMimeType(bufferedStream, sanitizedFilename);
            validateMimeType(extension, detectedMimeType);

            log.info("File validation passed: {} ({}), size: {} bytes, MIME: {}",
                    sanitizedFilename, extension, file.getSize(), detectedMimeType);

            // 6. 압축 폭탄 검증 (OOXML 파일만)
            if (isOOXMLFile(extension)) {
                // 스트림을 처음 위치로 되감기 (mark/reset 패턴)
                log.debug("Resetting stream for zip bomb validation: {}", sanitizedFilename);
                bufferedStream.reset();
                validateZipBomb(bufferedStream);
            }

        } catch (IOException e) {
            throw new SecurityValidationException("파일 검증 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 파일명 새니타이징 - Path Traversal 공격 방지
     * <p>
     * Defense in Depth 전략:
     * 1. 반복 제거: 우회 패턴("....//" → "../") 방지
     * 2. Paths.get().getFileName(): OS 레벨 경로 구분자 제거
     * 3. 최종 화이트리스트 검증: 안전한 문자만 허용
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new SecurityValidationException("파일명이 비어있습니다");
        }

        // 1. null 바이트 제거 (우선 처리)
        String sanitized = filename.replace("\0", "");

        // 2. Path Traversal 패턴 반복 제거
        // 우회 시도 (예: "....//", "..../") 방지를 위해 패턴이 사라질 때까지 반복
        String previous;
        int maxIterations = 10;  // 무한 루프 방지
        int iterations = 0;

        do {
            previous = sanitized;
            // 모든 Path Traversal 패턴 제거
            sanitized = sanitized.replaceAll("\\.\\./", "")      // ../
                    .replaceAll("\\.\\./", "")                    // ../ (중복 처리)
                    .replaceAll("\\.\\\\", "")                    // ..\
                    .replaceAll("\\.\\.\\\\", "")                 // ..\\ (중복)
                    .replace("../", "")
                    .replace("..\\", "");

            iterations++;
            if (iterations >= maxIterations) {
                throw new SecurityValidationException("파일명 새니타이징 실패: 반복 제한 초과");
            }
        } while (!sanitized.equals(previous));  // 변화가 없을 때까지 반복

        // 3. OS 레벨 경로 구분자 제거 (Paths.get().getFileName())
        Path path = Paths.get(sanitized).getFileName();
        if (path == null) {
            throw new SecurityValidationException("유효하지 않은 파일명입니다");
        }

        sanitized = path.toString();

        // 4. 최종 검증: 크로스 플랫폼 안전 문자 검증
        // 허용: 영문, 숫자, 한글, 공백, 하이픈, 언더스코어, 점, 괄호
        // 거부: 경로 구분자, 제어 문자, 특수 문자
        if (sanitized.contains("..") ||
                sanitized.contains("/") ||
                sanitized.contains("\\") ||
                sanitized.contains(":") ||    // 드라이브 구분자 (Windows)
                sanitized.contains("*") ||    // 와일드카드
                sanitized.contains("?") ||
                sanitized.contains("\"") ||
                sanitized.contains("<") ||
                sanitized.contains(">") ||
                sanitized.contains("|")) {    // 파이프
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
     * <p>
     * 주의: TikaInputStream은 전달된 InputStream을 소유하지 않도록 설정해야 합니다.
     * 그렇지 않으면 try-with-resources에서 원본 스트림을 닫아버려 reset()이 실패합니다.
     */
    private String detectMimeType(InputStream inputStream, String filename) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        // TikaInputStream.get()은 내부적으로 스트림을 소비할 수 있으므로
        // detector.detect()에 직접 전달 (Tika가 내부적으로 버퍼링 처리)
        MediaType mediaType = detector.detect(inputStream, metadata);
        return mediaType.toString();
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
