package com.example.onlyoffice.repository;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DocumentRepository 통합 테스트")
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    private Document activeDocument;
    private Document deletedDocument;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();

        activeDocument = documentRepository.save(Document.builder()
                .fileName("active.docx")
                .fileKey("active-key-001")
                .fileType("docx")
                .documentType("word")
                .fileSize(1024L)
                .storagePath("documents/active.docx")
                .status(DocumentStatus.ACTIVE)
                .build());

        deletedDocument = documentRepository.save(Document.builder()
                .fileName("deleted.pptx")
                .fileKey("deleted-key-003")
                .fileType("pptx")
                .documentType("slide")
                .fileSize(4096L)
                .storagePath("documents/deleted.pptx")
                .status(DocumentStatus.DELETED)
                .deletedAt(LocalDateTime.now())
                .build());
    }

    @Nested
    @DisplayName("Soft Delete 작업 테스트")
    class SoftDeleteTests {

        @Test
        @DisplayName("문서를 soft delete할 수 있다")
        void softDelete() {
            // given
            LocalDateTime deletedAt = LocalDateTime.now();

            // when
            int updatedCount = documentRepository.softDelete(activeDocument.getId(), deletedAt);

            // then
            assertThat(updatedCount).isEqualTo(1);

            Document result = documentRepository.findById(activeDocument.getId()).orElseThrow();
            assertThat(result.getDeletedAt()).isNotNull();
            assertThat(result.getStatus()).isEqualTo(DocumentStatus.DELETED);
        }

        @Test
        @DisplayName("이미 삭제된 문서는 soft delete 영향 없음")
        void softDeleteAlreadyDeleted() {
            // given
            LocalDateTime deletedAt = LocalDateTime.now();

            // when
            int updatedCount = documentRepository.softDelete(deletedDocument.getId(), deletedAt);

            // then
            assertThat(updatedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("삭제된 문서를 복원할 수 있다")
        void restore() {
            // when
            int updatedCount = documentRepository.restore(deletedDocument.getId());

            // then
            assertThat(updatedCount).isEqualTo(1);

            Document result = documentRepository.findById(deletedDocument.getId()).orElseThrow();
            assertThat(result.getDeletedAt()).isNull();
            assertThat(result.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("검색 메서드 테스트")
    class SearchTests {

        @Test
        @DisplayName("파일명으로 검색 (대소문자 무시)")
        void searchByFileName() {
            // when
            Page<Document> result = documentRepository.searchByFileName(
                    "%ACTIVE%", PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getFileName()).isEqualTo("active.docx");
        }

        @Test
        @DisplayName("삭제된 문서는 검색에서 제외")
        void searchExcludesDeletedDocuments() {
            // when
            Page<Document> result = documentRepository.searchByFileName(
                    "%deleted%", PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("낙관적 락 테스트")
    class OptimisticLockingTests {

        @Test
        @DisplayName("문서 수정 시 version이 자동 증가한다")
        void versionIncrementsOnUpdate() {
            // given
            Integer initialVersion = activeDocument.getVersion();

            // when
            activeDocument.setFileName("updated.docx");
            documentRepository.saveAndFlush(activeDocument);

            // then
            Document updated = documentRepository.findById(activeDocument.getId()).orElseThrow();
            assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
        }
    }
}
