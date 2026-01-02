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

    /**
     * 문서 업로드 (생성자 미지정).
     * 
     * @param file 업로드할 파일
     * @return 업로드된 문서
     * @see #uploadDocument(MultipartFile, String)
     */
    public Document uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    /**
     * ACTIVE 상태의 모든 문서를 생성일 내림차순으로 조회합니다.
     * 
     * <p>조회 조건:</p>
     * <ul>
     *   <li>상태: {@link DocumentStatus#ACTIVE}</li>
     *   <li>삭제 여부: soft delete되지 않음 (deletedAt IS NULL)</li>
     *   <li>정렬: 생성일 내림차순 (최신순)</li>
     * </ul>
     * 
     * @return ACTIVE 상태의 문서 목록 (최신순)
     * @see DocumentRepository#findByStatusAndDeletedAtIsNull(DocumentStatus, Sort)
     */
    @Transactional(readOnly = true)
    public List<Document> getActiveDocuments() {
        return documentRepository.findByStatusAndDeletedAtIsNull(DocumentStatus.ACTIVE, ACTIVE_DOCUMENT_SORT);
    }

    /**
     * 문서 업로드 Saga 패턴 구현.
     * 
     * <p><b>Saga Steps:</b></p>
     * <ol>
     *   <li><b>파일 검증</b>: {@link FileSecurityService}로 보안 검사 수행</li>
     *   <li><b>DB PENDING 저장</b>: 문서 메타데이터를 PENDING 상태로 저장</li>
     *   <li><b>MinIO 업로드</b>: 실제 파일을 스토리지에 업로드</li>
     *   <li><b>상태 ACTIVE 변경</b>: 업로드 완료 후 문서를 ACTIVE 상태로 변경</li>
     * </ol>
     * 
     * <p><b>보상 트랜잭션(Compensation):</b></p>
     * <ul>
     *   <li>MinIO 업로드 실패 → DB 레코드 삭제</li>
     *   <li>상태 변경 실패 → MinIO 파일 삭제 + DB 레코드 삭제</li>
     * </ul>
     * 
     * @param file      업로드할 파일
     * @param createdBy 생성자 ID (null일 경우 "anonymous")
     * @return 업로드된 ACTIVE 상태의 문서
     * @throws DocumentUploadException 업로드 실패 시 (보상 트랜잭션 실행 후 발생)
     * @see #handleUploadFailure(Document, boolean)
     * @see <a href="docs/document-service-saga-pattern.md">Saga Pattern Documentation</a>
     */
    public Document uploadDocument(MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException("File is empty");
        }

        // Step 1: 파일 검증
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

        // Step 2: DB에 PENDING 상태로 저장
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
            // Step 3: MinIO 업로드
            storageService.uploadFile(file, storagePath);
            storageUploaded = true;
            
            // Step 4: 상태를 ACTIVE로 변경
            document.setStatus(DocumentStatus.ACTIVE);
            return documentRepository.save(document);
        } catch (Exception e) {
            // 보상 트랜잭션 실행
            handleUploadFailure(document, storageUploaded);
            throw new DocumentUploadException("Upload failed for file " + sanitizedFilename, e);
        }
    }

    /**
     * 문서 삭제 Saga 패턴 구현 (비관적 락 적용).
     * 
     * <p><b>Saga Steps:</b></p>
     * <ol>
     *   <li><b>비관적 락 조회</b>: PESSIMISTIC_WRITE 락으로 동시 수정 방지 (타임아웃 3초)</li>
     *   <li><b>Soft Delete (DB)</b>: 상태를 DELETED로 변경, deletedAt 타임스탬프 설정</li>
     *   <li><b>MinIO 파일 삭제</b>: 스토리지에서 실제 파일 제거</li>
     * </ol>
     * 
     * <p><b>보상 트랜잭션(Compensation):</b></p>
     * <ul>
     *   <li>MinIO 삭제 실패 → DB 상태를 ACTIVE로 복구, deletedAt을 NULL로 초기화</li>
     * </ul>
     * 
     * <p><b>동시성 제어:</b></p>
     * <ul>
     *   <li>비관적 락(PESSIMISTIC_WRITE)으로 삭제 중 다른 트랜잭션의 수정 차단</li>
     *   <li>3초 타임아웃으로 데드락 방지</li>
     *   <li>이미 삭제된 문서는 조기 반환 (멱등성 보장)</li>
     * </ul>
     * 
     * @param id 삭제할 문서 ID
     * @throws DocumentNotFoundException 문서가 존재하지 않을 경우
     * @throws DocumentDeleteException   MinIO 삭제 실패 시 (보상 트랜잭션 실행 후 발생)
     * @see DocumentRepository#findWithLockById(Long)
     * @see <a href="docs/document-service-saga-pattern.md">Saga Pattern Documentation</a>
     */
    public void deleteDocument(Long id) {
        // Step 1: 비관적 락으로 조회 (타임아웃 3초)
        Document document = documentRepository.findWithLockById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // 이미 삭제된 문서 체크 (멱등성)
        if (document.getDeletedAt() != null || document.getStatus() == DocumentStatus.DELETED) {
            log.info("Document already deleted. id={} storagePath={}", id, document.getStoragePath());
            return;
        }

        // Step 2: Soft Delete (DB)
        document.setStatus(DocumentStatus.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        try {
            // Step 3: MinIO 파일 삭제
            storageService.deleteFile(document.getStoragePath());
        } catch (Exception e) {
            // 보상 트랜잭션: DB 상태 복구
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

    /**
     * 업로드 실패 시 보상 트랜잭션을 실행합니다.
     * 
     * <p><b>보상 전략:</b></p>
     * <ul>
     *   <li>MinIO 업로드 성공 후 DB 저장 실패 → MinIO 파일 삭제 + DB 레코드 삭제</li>
     *   <li>MinIO 업로드 실패 → DB 레코드만 삭제</li>
     * </ul>
     * 
     * @param document         저장된 PENDING 상태의 문서
     * @param storageUploaded  MinIO 업로드 성공 여부
     */
    private void handleUploadFailure(Document document, boolean storageUploaded) {
        if (storageUploaded) {
            // MinIO에 이미 업로드된 파일 정리
            try {
                storageService.deleteFile(document.getStoragePath());
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up storage object {} after upload failure", document.getStoragePath(), cleanupException);
            }
        }

        // DB 레코드 삭제
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
