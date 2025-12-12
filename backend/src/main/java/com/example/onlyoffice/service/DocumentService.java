package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentDeleteException;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.exception.DocumentUploadException;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDateTime;
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
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final Sort ACTIVE_DOCUMENT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final DocumentRepository documentRepository;
    private final FileSecurityService fileSecurityService;
    private final MinioStorageService storageService;

    @Transactional(readOnly = true)
    public Optional<Document> findByFileKey(String fileKey) {
        return documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey);
    }

    public Document uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    @Transactional(readOnly = true)
    public List<Document> getActiveDocuments() {
        return documentRepository.findByStatusAndDeletedAtIsNull(DocumentStatus.ACTIVE, ACTIVE_DOCUMENT_SORT);
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
        String documentType = determineDocumentType(extension);
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

        if (document.getDeletedAt() != null || document.getStatus() == DocumentStatus.DELETED) {
            log.info("Document already deleted. id={} storagePath={}", id, document.getStoragePath());
            return;
        }

        document.setStatus(DocumentStatus.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        try {
            storageService.deleteFile(document.getStoragePath());
        } catch (Exception e) {
            document.setStatus(DocumentStatus.ACTIVE);
            document.setDeletedAt(null);
            documentRepository.save(document);
            throw new DocumentDeleteException("Delete failed for document id " + id, e);
        }
    }

    @Transactional(readOnly = true)
    public String getEditorKeyByFileKey(String fileKey) {
        Document document = getDocumentOrThrow(fileKey);
        return KeyUtils.generateEditorKey(document.getFileKey(), document.getEditorVersion());
    }

    public void incrementEditorVersionByFileKey(String fileKey) {
        documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey)
                .ifPresentOrElse(doc -> {
                    int oldVersion = doc.getEditorVersion();
                    doc.incrementEditorVersion();
                    documentRepository.save(doc);
                    log.info("Editor version incremented for fileKey {}: {} -> {}",
                            fileKey, oldVersion, doc.getEditorVersion());
                }, () -> {
                    throw new DocumentNotFoundException("Document not found for fileKey: " + fileKey);
                });
    }

    @Transactional(readOnly = true)
    public InputStream downloadDocumentStream(String fileKey) {
        Document document = getDocumentOrThrow(fileKey);
        return storageService.downloadFile(document.getStoragePath());
    }

    public void saveDocumentFromUrlByFileKey(String downloadUrl, String fileKey) {
        Document document = getDocumentOrThrow(fileKey);
        log.info("Downloading file from {} for fileKey {}", downloadUrl, fileKey);

        try {
            URLConnection connection = URI.create(downloadUrl).toURL().openConnection();
            long contentLength = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = DEFAULT_CONTENT_TYPE;
            }

            try (ByteCountingInputStream inputStream =
                         new ByteCountingInputStream(connection.getInputStream())) {
                storageService.uploadStream(inputStream, contentLength, contentType, document.getStoragePath());

                long uploadedSize = inputStream.getBytesRead();
                if (uploadedSize > 0) {
                    document.setFileSize(uploadedSize);
                } else if (contentLength > 0) {
                    document.setFileSize(contentLength);
                }
                documentRepository.save(document);
                log.info("File saved successfully for fileKey: {}", fileKey);
            }
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

        try {
            documentRepository.delete(document);
        } catch (Exception deleteException) {
            log.error("Failed to remove pending document {} during compensation", document.getFileKey(), deleteException);
        }
    }

    private Document getDocumentOrThrow(String fileKey) {
        return documentRepository.findByFileKeyAndDeletedAtIsNull(fileKey)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for fileKey: " + fileKey));
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String determineDocumentType(String extension) {
        return switch (extension) {
            case "docx", "doc", "odt", "txt", "hwp" -> "word";
            case "xlsx", "xls", "xlsm", "ods", "csv" -> "cell";
            case "pptx", "ppt", "odp" -> "slide";
            case "pdf" -> "pdf";
            default -> "word";
        };
    }

    private String buildStoragePath(String fileKey, String sanitizedFilename) {
        return DEFAULT_STORAGE_PREFIX + "/" + fileKey + "/" + sanitizedFilename;
    }

    private String resolveCreatedBy(String createdBy) {
        return StringUtils.hasText(createdBy) ? createdBy : DEFAULT_UPLOADER;
    }

    private static class ByteCountingInputStream extends FilterInputStream {

        private long bytesRead;

        protected ByteCountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result >= 0) {
                bytesRead++;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result > 0) {
                bytesRead += result;
            }
            return result;
        }

        long getBytesRead() {
            return bytesRead;
        }
    }
}
