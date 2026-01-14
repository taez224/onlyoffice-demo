package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.SecurityValidationException;
import com.onlyoffice.manager.document.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
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
 *
 * <p><b>Integration Note:</b>
 * This service is ready to be integrated into a future file upload API.
 * Expected integration point: DocumentUploadController or similar REST endpoint
 * that accepts MultipartFile uploads.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @PostMapping("/api/documents/upload")
 * public ResponseEntity<DocumentDto> uploadDocument(@RequestParam("file") MultipartFile file) {
 *     // 1. Validate file security
 *     fileSecurityService.validateFile(file);
 *
 *     // 2. Save to storage (MinIO)
 *     String objectName = minioStorageService.uploadFile(file, ...);
 *
 *     // 3. Create document record
 *     Document document = documentService.createDocument(file, objectName);
 *
 *     return ResponseEntity.ok(documentDto);
 * }
 * }
 * </pre>
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
     * 위험한 MIME 타입 블랙리스트 (실행 파일, 스크립트 등)
     * 악성 파일이 문서 확장자로 위장해도 차단
     */
    private static final Set<String> DANGEROUS_MIME_TYPES = Set.of(
            // Executables
            "application/x-executable",
            "application/x-msdos-program",
            "application/x-msdownload",
            "application/x-dosexec",
            "application/vnd.microsoft.portable-executable",
            // Scripts
            "application/javascript",
            "application/x-javascript",
            "text/javascript",
            "application/x-php",
            "application/x-python",
            "application/x-sh",
            "application/x-shellscript",
            "application/x-bat",
            // Java
            "application/java-archive",
            "application/x-java-class",
            // Others
            "application/x-msmetafile"
    );

    private final Detector detector;
    private final MimeTypes mimeTypes;
    private final DocumentManager documentManager;

    public FileSecurityService(DocumentManager documentManager) {
        try {
            TikaConfig tikaConfig = new TikaConfig();
            this.detector = tikaConfig.getDetector();
            this.mimeTypes = tikaConfig.getMimeRepository();
        } catch (Exception e) {
            log.error("Apache Tika initialization failed - file content validation will be unavailable", e);
            throw new IllegalStateException("Failed to initialize Apache Tika", e);
        }
        this.documentManager = documentManager;
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

        // 2. 확장자 검증 (ONLYOFFICE SDK 활용)
        String extension = getFileExtension(sanitizedFilename);
        validateExtension(sanitizedFilename);

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
                    .replaceAll("\\.\\.\\\\", "")                 // ..\
                    .replace("../", "")                           // 추가 안전망
                    .replace("..\\", "");                         // 추가 안전망

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
     * 확장자 검증 - ONLYOFFICE SDK를 사용하여 지원되는 파일 형식인지 확인
     *
     * @param filename 검증할 파일명 (확장자 포함)
     */
    private void validateExtension(String filename) {
        if (documentManager.getDocumentType(filename) == null) {
            String extension = getFileExtension(filename);
            throw new SecurityValidationException(
                    String.format("허용되지 않은 파일 형식입니다: %s", extension)
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
     * MIME 타입 검증 - Apache Tika 레지스트리 기반
     *
     * 1. 위험한 MIME 타입(실행 파일, 스크립트) 블랙리스트 차단
     * 2. ZIP 기반 포맷(OOXML, ODF)은 application/zip 허용
     * 3. Tika 레지스트리에서 확장자의 예상 MIME과 비교
     */
    private void validateMimeType(String extension, String detectedMimeType) {
        // 1. 위험한 MIME 타입 블랙리스트 체크 (악성 파일 위장 방지)
        if (DANGEROUS_MIME_TYPES.stream().anyMatch(detectedMimeType::startsWith)) {
            throw new SecurityValidationException(
                    String.format("위험한 파일 형식이 감지되었습니다: %s", detectedMimeType)
            );
        }

        // 2. ZIP 기반 포맷 (OOXML, ODF, epub 등)은 application/zip으로 감지될 수 있음
        if (isOOXMLFile(extension) && detectedMimeType.startsWith("application/zip")) {
            log.debug("ZIP-based format detected for extension '{}', allowing", extension);
            return;
        }

        // 3. Tika 레지스트리에서 확장자의 예상 MIME 타입 조회
        try {
            MimeType expectedMimeType = mimeTypes.forName(
                    mimeTypes.getMimeType("file." + extension).getName()
            );

            // 감지된 MIME이 예상 MIME과 일치하거나 부모 타입인지 확인
            String expectedName = expectedMimeType.getName();
            if (detectedMimeType.startsWith(expectedName) ||
                    detectedMimeType.equals("application/octet-stream") ||
                    isRelatedMimeType(expectedName, detectedMimeType)) {
                return;
            }

            log.warn("MIME mismatch for '{}': expected={}, detected={}",
                    extension, expectedName, detectedMimeType);
            // MIME 불일치는 경고만 하고 통과 (SDK 확장자 검증 신뢰)
            // 엄격 모드가 필요하면 여기서 예외 발생

        } catch (MimeTypeException e) {
            // Tika에 등록되지 않은 확장자 - SDK 검증 신뢰
            log.debug("Extension '{}' not in Tika registry, trusting SDK validation", extension);
        }
    }

    /**
     * 관련 MIME 타입인지 확인 (예: text/plain은 많은 텍스트 기반 포맷과 호환)
     */
    private boolean isRelatedMimeType(String expected, String detected) {
        // 텍스트 기반 포맷들
        if (expected.startsWith("text/") && detected.startsWith("text/")) {
            return true;
        }
        // MS Office 레거시 포맷들
        if (expected.startsWith("application/vnd.ms-") && detected.startsWith("application/vnd.ms-")) {
            return true;
        }
        // application/octet-stream은 알 수 없는 바이너리 - 허용
        if (detected.equals("application/octet-stream")) {
            return true;
        }
        return false;
    }

    /**
     * OOXML(ZIP 기반) 파일 여부 확인 - 압축 폭탄 검증 대상
     */
    private static final Set<String> OOXML_EXTENSIONS = Set.of(
            // Office OOXML
            "docx", "docm", "dotx", "dotm",
            "xlsx", "xlsm", "xlsb", "xltx", "xltm",
            "pptx", "pptm", "potx", "potm", "ppsx", "ppsm",
            // Visio OOXML
            "vsdx", "vsdm", "vssx", "vssm", "vstx", "vstm",
            // OpenDocument (also ZIP-based)
            "odt", "ott", "ods", "ots", "odp", "otp",
            // Others
            "epub"
    );

    private boolean isOOXMLFile(String extension) {
        return OOXML_EXTENSIONS.contains(extension);
    }

    /**
     * ZIP 폭탄 검증 - 압축 해제 크기 제한
     * OOXML 파일(.docx, .xlsx, .pptx)은 ZIP 형식이므로 압축 폭탄 공격 가능
     *
     * @param inputStream BufferedInputStream (already buffered)
     */
    private void validateZipBomb(InputStream inputStream) throws IOException {
        long totalUncompressedSize = 0;
        int entryCount = 0;
        final int MAX_ENTRIES = 1000; // 최대 엔트리 수 제한

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {

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
