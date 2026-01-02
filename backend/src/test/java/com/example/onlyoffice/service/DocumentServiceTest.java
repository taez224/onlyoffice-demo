package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentDeleteException;
import com.example.onlyoffice.exception.DocumentUploadException;
import com.example.onlyoffice.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Saga 단위 테스트")
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileSecurityService fileSecurityService;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private MultipartFile multipartFile;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, fileSecurityService, storageService);
    }

    @Test
    @DisplayName("업로드 성공 시 문서를 ACTIVE 상태로 저장한다")
    void uploadDocument_persistsAndActivatesDocument() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("sample.docx");
        when(multipartFile.getSize()).thenReturn(1_024L);
        when(fileSecurityService.sanitizeFilename("sample.docx")).thenReturn("sample.docx");
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(1L);
            }
            return doc;
        });

        Document result = documentService.uploadDocument(multipartFile, "tester");

        verify(fileSecurityService).validateFile(multipartFile);
        verify(storageService).uploadFile(multipartFile, result.getStoragePath());
        verify(documentRepository, never()).delete(any(Document.class));
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(result.getFileKey()).isNotBlank();
        assertThat(result.getFileName()).isEqualTo("sample.docx");
    }

    @Test
    @DisplayName("MinIO 업로드 실패 시 보상 트랜잭션으로 롤백한다")
    void uploadDocument_rollsBackWhenStorageFails() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("fail.docx");
        when(multipartFile.getSize()).thenReturn(512L);
        when(fileSecurityService.sanitizeFilename("fail.docx")).thenReturn("fail.docx");
        AtomicReference<Document> saved = new AtomicReference<>();
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(99L);
            saved.set(doc);
            return doc;
        });
        doThrow(new RuntimeException("upload boom"))
                .when(storageService).uploadFile(any(MultipartFile.class), anyString());

        assertThatThrownBy(() -> documentService.uploadDocument(multipartFile, null))
                .isInstanceOf(DocumentUploadException.class)
                .hasMessageContaining("Upload failed");

        // DB 롤백은 Spring @Transactional이 자동으로 처리하므로 delete() 호출 불필요
        // MinIO 업로드 실패 시에는 스토리지 삭제도 필요 없음
        verify(storageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("DB 상태 변경 실패 시 MinIO 객체를 정리한다")
    void uploadDocument_cleansUpStorageWhenActivatingFails() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("cleanup.docx");
        when(multipartFile.getSize()).thenReturn(2048L);
        when(fileSecurityService.sanitizeFilename("cleanup.docx")).thenReturn("cleanup.docx");

        AtomicReference<Document> savedDocument = new AtomicReference<>();
        AtomicInteger saveCount = new AtomicInteger();
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(77L);
                savedDocument.set(doc);
            }
            if (saveCount.incrementAndGet() == 2) {
                throw new RuntimeException("db boom");
            }
            return doc;
        });

        assertThatThrownBy(() -> documentService.uploadDocument(multipartFile, "tester"))
                .isInstanceOf(DocumentUploadException.class);

        // MinIO 업로드는 성공했으므로 보상 트랜잭션으로 MinIO 파일 삭제
        verify(storageService).uploadFile(multipartFile, savedDocument.get().getStoragePath());
        verify(storageService).deleteFile(savedDocument.get().getStoragePath());
        // DB 롤백은 Spring @Transactional이 자동으로 처리하므로 delete() 호출 불필요
    }

    @Test
    @DisplayName("삭제 시 soft delete 후 MinIO 객체를 제거한다")
    void deleteDocument_softDeletesAndClearsObject() {
        Document document = buildDocument();
        when(documentRepository.findWithLockById(anyLong())).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);

        documentService.deleteDocument(document.getId());

        verify(storageService).deleteFile(document.getStoragePath());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DELETED);
        assertThat(document.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제 중 MinIO 오류가 발생하면 상태를 복구한다")
    void deleteDocument_restoresStateWhenStorageFails() {
        Document document = buildDocument();
        when(documentRepository.findWithLockById(document.getId())).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);
        doThrow(new RuntimeException("delete boom"))
                .when(storageService).deleteFile(document.getStoragePath());

        assertThatThrownBy(() -> documentService.deleteDocument(document.getId()))
                .isInstanceOf(DocumentDeleteException.class)
                .hasMessageContaining("Delete failed");

        verify(documentRepository, times(2)).save(document);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(document.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("파일 다운로드 시 MinIO 스트림을 반환한다")
    void downloadDocumentStream_returnsStorageStream() {
        Document document = buildDocument();
        ByteArrayInputStream inputStream = new ByteArrayInputStream("hello".getBytes());
        when(documentRepository.findByFileKeyAndDeletedAtIsNull(document.getFileKey()))
                .thenReturn(Optional.of(document));
        when(storageService.downloadFile(document.getStoragePath())).thenReturn(inputStream);

        InputStream result = documentService.downloadDocumentStream(document.getFileKey());

        assertThat(result).isSameAs(inputStream);
    }

    @Test
    @DisplayName("ACTIVE 상태 문서만 생성일 내림차순으로 조회한다")
    void getActiveDocuments_returnsSortedActiveDocuments() {
        Document document = buildDocument();
        when(documentRepository.findByStatusAndDeletedAtIsNull(eq(DocumentStatus.ACTIVE), any(Sort.class)))
                .thenReturn(List.of(document));

        List<Document> documents = documentService.getActiveDocuments();

        assertThat(documents).containsExactly(document);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(documentRepository).findByStatusAndDeletedAtIsNull(eq(DocumentStatus.ACTIVE), sortCaptor.capture());
        Sort sort = sortCaptor.getValue();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    private Document buildDocument() {
        Document document = Document.builder()
                .id(10L)
                .fileName("doc.docx")
                .fileKey("file-key")
                .fileType("docx")
                .documentType("word")
                .fileSize(128L)
                .storagePath("documents/file-key/doc.docx")
                .status(DocumentStatus.ACTIVE)
                .createdBy("tester")
                .build();
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }
}
