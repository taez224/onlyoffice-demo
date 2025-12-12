package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    @Value("${storage.path}")
    private String storagePath;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(storagePath);
            Files.createDirectories(this.rootLocation);
            log.info("Storage directory initialized at: {}", this.rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    // ==================== 파일 시스템 작업 ====================

    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(this.rootLocation, 1)) {
            return walk
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored files", e);
        }
    }

    public File getFile(String fileName) {
        return this.rootLocation.resolve(fileName).toFile();
    }

    public void saveFile(String fileName, InputStream inputStream) {
        try {
            log.info("Saving file: {}", fileName);
            Path destinationFile = this.rootLocation.resolve(fileName);
            log.info("Saving file to: {}", destinationFile);
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + fileName, e);
        }
    }

    public String getServerUrl() {
        return serverBaseUrl;
    }

    // ==================== fileKey 기반 메서드 (신규) ====================

    /**
     * fileKey로 문서 조회
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @return 문서 Optional
     */
    public Optional<Document> findByFileKey(String fileKey) {
        return documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey);
    }

    /**
     * fileKey로 ONLYOFFICE document.key 생성
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @return fileKey_v{version} 형식의 document.key
     * @throws DocumentNotFoundException 문서를 찾을 수 없는 경우
     */
    public String getEditorKeyByFileKey(String fileKey) {
        return documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey)
            .map(doc -> {
                String key = KeyUtils.generateEditorKey(doc.getFileKey(), doc.getEditorVersion());
                log.debug("Generated editor key for fileKey {}: {}", fileKey, key);
                return key;
            })
            .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));
    }

    /**
     * fileKey로 문서 저장 후 editorVersion 증가
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @throws DocumentNotFoundException 문서를 찾을 수 없는 경우
     */
    public void incrementEditorVersionByFileKey(String fileKey) {
        documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey)
            .ifPresentOrElse(
                doc -> {
                    int oldVersion = doc.getEditorVersion();
                    doc.incrementEditorVersion();
                    documentRepository.save(doc);
                    log.info("Editor version incremented for fileKey {}: {} -> {}",
                        fileKey, oldVersion, doc.getEditorVersion());
                },
                () -> {
                    throw new DocumentNotFoundException("Document not found for fileKey: " + fileKey);
                }
            );
    }

    /**
     * fileKey로 파일 시스템에서 파일 가져오기
     *
     * @param fileKey 파일 고유 식별자 (UUID)
     * @return 파일 객체
     * @throws DocumentNotFoundException 문서를 찾을 수 없는 경우
     */
    public File getFileByFileKey(String fileKey) {
        Document doc = findByFileKey(fileKey)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        // storagePath에서 파일 가져오기
        return this.rootLocation.resolve(doc.getStoragePath()).toFile();
    }

    /**
     * fileKey 기반으로 URL에서 편집된 문서 다운로드 및 저장
     *
     * @param downloadUrl ONLYOFFICE에서 제공한 다운로드 URL
     * @param fileKey 파일 고유 식별자 (UUID)
     * @throws DocumentNotFoundException 문서를 찾을 수 없는 경우
     */
    public void saveDocumentFromUrlByFileKey(String downloadUrl, String fileKey) {
        Document doc = findByFileKey(fileKey)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));

        log.info("Downloading file from {} for fileKey {}", downloadUrl, fileKey);
        try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            Path destinationFile = this.rootLocation.resolve(doc.getStoragePath());
            Files.copy(in, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved successfully for fileKey: {}", fileKey);
        } catch (Exception e) {
            log.error("Error downloading file from {}", downloadUrl, e);
            throw new RuntimeException("Failed to save document from URL", e);
        }
    }

    // ==================== fileName 기반 메서드 (하위 호환성 - Deprecated) ====================

    /**
     * ONLYOFFICE document.key 생성
     * DB에 문서가 있으면 DB 기반 key 사용, 없으면 파일시스템 기반 fallback
     *
     * @deprecated Use {@link #getEditorKeyByFileKey(String)} instead
     * @param fileName 파일명
     * @return 유효한 document.key
     */
    @Deprecated
    public String getEditorKey(String fileName) {
        File file = getFile(fileName);

        return documentRepository.findByFileNameAndDeletedAtIsNull(fileName)
            .map(doc -> {
                String key = KeyUtils.generateEditorKey(doc.getFileKey(), doc.getEditorVersion());
                log.debug("Using DB-based key for {}: {}", fileName, key);
                return key;
            })
            .orElseGet(() -> {
                // DB에 문서가 없으면 파일시스템 기반 fallback (하위 호환성)
                String fallbackKey = KeyUtils.sanitize(fileName + "_" + file.lastModified());
                log.warn("Document not found in DB for {}, using fallback key: {}", fileName, fallbackKey);
                return fallbackKey;
            });
    }

    /**
     * 파일명으로 문서 조회
     *
     * @deprecated Use {@link #findByFileKey(String)} instead
     * @param fileName 파일명
     * @return 문서 Optional
     */
    @Deprecated
    public Optional<Document> findByFileName(String fileName) {
        return documentRepository.findByFileNameAndDeletedAtIsNull(fileName);
    }

    /**
     * 문서 저장 후 editorVersion 증가
     * 편집 종료(status=2) 시 호출하여 다음 편집 세션을 위한 새 key 생성
     *
     * @deprecated Use {@link #incrementEditorVersionByFileKey(String)} instead
     * @param fileName 파일명
     */
    @Deprecated
    public void incrementEditorVersion(String fileName) {
        documentRepository.findByFileNameAndDeletedAtIsNull(fileName)
            .ifPresentOrElse(
                doc -> {
                    int oldVersion = doc.getEditorVersion();
                    doc.incrementEditorVersion();
                    documentRepository.save(doc);
                    log.info("Editor version incremented for {}: {} -> {}",
                        fileName, oldVersion, doc.getEditorVersion());
                },
                () -> log.warn("Document not found in DB for version increment: {}", fileName)
            );
    }

    /**
     * URL에서 편집된 문서 다운로드 및 저장
     *
     * @deprecated Use {@link #saveDocumentFromUrlByFileKey(String, String)} instead
     * @param downloadUrl ONLYOFFICE에서 제공한 다운로드 URL
     * @param fileName 저장할 파일명
     */
    @Deprecated
    public void saveDocumentFromUrl(String downloadUrl, String fileName) {
        log.info("Downloading file from {} to {}", downloadUrl, fileName);
        try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            saveFile(fileName, in);
            log.info("File saved successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Error downloading file from {}", downloadUrl, e);
            throw new RuntimeException("Failed to save document from URL", e);
        }
    }
}
