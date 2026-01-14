package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentDeleteException;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.exception.DocumentUploadException;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.model.documenteditor.config.document.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private static final String DEFAULT_STORAGE_PREFIX = "documents";
    private static final String DEFAULT_UPLOADER = "anonymous";
    private static final Sort ACTIVE_DOCUMENT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    /**
     * ONLYOFFICE Document Server는 60초 내 응답을 기대함.
     * 5초 버퍼를 추가하여 네트워크 지연 및 파일 다운로드 시간 허용.
     */
    private static final int CALLBACK_TRANSACTION_TIMEOUT_SECONDS = 65;

    private final DocumentRepository documentRepository;
    private final FileSecurityService fileSecurityService;
    private final MinioStorageService storageService;
    private final UrlDownloadService urlDownloadService;
    private final DocumentManager documentManager;

    @Transactional(readOnly = true)
    public Optional<Document> findByFileKey(String fileKey) {
        return documentRepository.findByFileKey(fileKey);
    }

    public Document uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    @Transactional(readOnly = true)
    public List<Document> getActiveDocuments() {
        return documentRepository.findAllByStatus(DocumentStatus.ACTIVE, ACTIVE_DOCUMENT_SORT);
    }

    public Document uploadDocument(MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException("File is empty");
        }

        fileSecurityService.validateFile(file);

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            originalFilename = "document";
        }

        String sanitizedFilename = fileSecurityService.sanitizeFilename(originalFilename);
        String extension = extractExtension(sanitizedFilename);
        String documentType = determineDocumentType(sanitizedFilename);
        String fileKey = KeyUtils.generateFileKey();
        String storagePath = buildStoragePath(fileKey, sanitizedFilename);

        Document document = Document.builder()
                .fileName(sanitizedFilename)
                .fileKey(fileKey)
                .fileType(extension)
                .documentType(documentType)
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .status(DocumentStatus.PENDING)
                .createdBy(resolveCreatedBy(createdBy))
                .build();

        document = documentRepository.save(document);

        boolean storageUploaded = false;
        try {
            storageService.uploadFile(file, storagePath);
            storageUploaded = true;

            document.setStatus(DocumentStatus.ACTIVE);
            return documentRepository.save(document);
        } catch (Exception e) {
            handleUploadFailure(document, storageUploaded);
            throw new DocumentUploadException("Upload failed for file " + sanitizedFilename, e);
        }
    }

    public void deleteDocument(Long id) {
        Document document = documentRepository.findWithLockById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        String storagePath = document.getStoragePath();
        DocumentStatus originalStatus = document.getStatus();

        documentRepository.delete(document);
        // flush() syncs to DB but does NOT commit - transaction still active for rollback
        documentRepository.flush();

        try {
            storageService.deleteFile(storagePath);
        } catch (Exception e) {
            documentRepository.restoreWithStatus(id, originalStatus);
            throw new DocumentDeleteException("Delete failed for document id " + id, e);
        }
    }

    @Transactional(readOnly = true)
    public String getEditorKeyByFileKey(String fileKey) {
        return documentManager.getDocumentKey(fileKey, false);
    }

    @Transactional(readOnly = true)
    public InputStream downloadDocumentStream(String fileKey) {
        Document document = getDocumentOrThrow(fileKey);
        return storageService.downloadFile(document.getStoragePath());
    }

    @Transactional(timeout = CALLBACK_TRANSACTION_TIMEOUT_SECONDS)
    public void processCallbackSave(String downloadUrl, String fileKey) {
        Document document = getDocumentWithLockOrThrow(fileKey);

        log.info("Processing SAVE callback with lock for fileKey: {}", fileKey);

        saveDocumentFromUrl(downloadUrl, document);

        int oldVersion = document.getEditorVersion();
        document.incrementEditorVersion();
        documentRepository.save(document);

        log.info("SAVE callback completed. fileKey: {}, version: {} -> {}",
                fileKey, oldVersion, document.getEditorVersion());
    }

    @Transactional(timeout = CALLBACK_TRANSACTION_TIMEOUT_SECONDS)
    public void processCallbackForceSave(String downloadUrl, String fileKey) {
        Document document = getDocumentWithLockOrThrow(fileKey);

        log.info("Processing FORCESAVE callback with lock for fileKey: {}", fileKey);

        saveDocumentFromUrl(downloadUrl, document);

        log.info("FORCESAVE callback completed. fileKey: {}, version unchanged: {}",
                fileKey, document.getEditorVersion());
    }

    private void saveDocumentFromUrl(String downloadUrl, Document document) {
        log.info("Downloading file from {} for fileKey {}", downloadUrl, document.getFileKey());

        try {
            UrlDownloadService.DownloadResult result = urlDownloadService.downloadAndSave(
                    downloadUrl, document.getStoragePath());

            if (result.fileSize() > 0) {
                document.setFileSize(result.fileSize());
            }
            documentRepository.save(document);
            log.info("File saved successfully for fileKey: {}", document.getFileKey());
        } catch (Exception e) {
            log.error("Error downloading file from {}", downloadUrl, e);
            throw new RuntimeException("Failed to save document from URL", e);
        }
    }

    private void handleUploadFailure(Document document, boolean storageUploaded) {
        if (storageUploaded) {
            try {
                storageService.deleteFile(document.getStoragePath());
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up storage object {} after upload failure", document.getStoragePath(), cleanupException);
            }
        }
    }

    private Document getDocumentOrThrow(String fileKey) {
        return documentRepository.findByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));
    }

    private Document getDocumentWithLockOrThrow(String fileKey) {
        return documentRepository.findWithLockByFileKey(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * SDK의 포맷 데이터베이스를 활용하여 문서 타입 결정.
     * 지원하지 않는 확장자는 FileSecurityService에서 이미 검증되었으므로
     * 여기서 null이 반환되면 프로그래밍 오류임.
     */
    private String determineDocumentType(String fileName) {
        DocumentType type = documentManager.getDocumentType(fileName);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + fileName + " (should have been validated by FileSecurityService)"
            );
        }
        return type.name().toLowerCase(Locale.ROOT);
    }

    private String buildStoragePath(String fileKey, String sanitizedFilename) {
        return DEFAULT_STORAGE_PREFIX + "/" + fileKey + "/" + sanitizedFilename;
    }

    private String resolveCreatedBy(String createdBy) {
        return StringUtils.hasText(createdBy) ? createdBy : DEFAULT_UPLOADER;
    }
}
