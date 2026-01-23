package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentDeleteException;
import com.example.onlyoffice.repository.DocumentRepository;
import com.onlyoffice.manager.document.DocumentManager;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DocumentService 통합 테스트")
class DocumentServiceIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private MinioStorageService storageService;

    @MockitoBean
    private FileSecurityService fileSecurityService;

    @MockitoBean
    private UrlDownloadService urlDownloadService;

    @MockitoBean
    private DocumentManager documentManager;

    private Document testDocument;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();

        testDocument = documentRepository.saveAndFlush(Document.builder()
                .fileName("rollback-test.docx")
                .fileKey("rollback-test-key-001")
                .fileType("docx")
                .documentType("word")
                .fileSize(1024L)
                .storagePath("documents/rollback-test-key-001/rollback-test.docx")
                .status(DocumentStatus.ACTIVE)
                .build());
    }

    @Test
    @Transactional
    @DisplayName("스토리지 삭제 성공 시 문서가 soft delete된다")
    void deleteDocument_softDeletesWhenStorageSucceeds() {
        // given
        doNothing().when(storageService).deleteFile(anyString());

        // when
        documentService.deleteDocument(testDocument.getId());
        entityManager.flush();
        entityManager.clear();

        // then: soft deleted 문서는 findById에서 조회되지 않음
        assertThat(documentRepository.findById(testDocument.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("스토리지 삭제 실패 시 soft delete가 롤백된다")
    void deleteDocument_rollsBackSoftDeleteWhenStorageFails() {
        // given
        doThrow(new RuntimeException("Storage failure"))
                .when(storageService).deleteFile(anyString());

        // when & then: 예외 발생
        assertThatThrownBy(() -> documentService.deleteDocument(testDocument.getId()))
                .isInstanceOf(DocumentDeleteException.class)
                .hasMessageContaining("Delete failed");

        // 캐시 클리어하여 DB에서 다시 조회
        entityManager.clear();

        // then: 문서가 여전히 존재하고 soft delete되지 않았는지 확인
        Document afterFailure = documentRepository.findById(testDocument.getId()).orElse(null);
        assertThat(afterFailure).isNotNull();
        assertThat(afterFailure.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
    }

    @Test
    @Transactional
    @DisplayName("존재하지 않는 문서 삭제 시 예외 발생")
    void deleteDocument_throwsWhenDocumentNotFound() {
        // given
        Long nonExistentId = 999999L;

        // when & then
        assertThatThrownBy(() -> documentService.deleteDocument(nonExistentId))
                .isInstanceOf(com.example.onlyoffice.exception.DocumentNotFoundException.class);
    }
}
