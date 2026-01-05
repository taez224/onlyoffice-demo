package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileMigrationService 단위 테스트")
class FileMigrationServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @TempDir
    Path tempDir;

    private FileMigrationService fileMigrationService;

    @BeforeEach
    void setUp() {
        fileMigrationService = new FileMigrationService(documentRepository);
        ReflectionTestUtils.setField(fileMigrationService, "storagePath", tempDir.toString());
    }

    @Test
    @DisplayName("존재하지 않는 디렉토리 - 빈 리포트 반환")
    void migrateExistingFiles_returnsEmptyReportWhenDirectoryNotExists() {
        ReflectionTestUtils.setField(fileMigrationService, "storagePath", "/non/existent/path");

        MigrationReport report = fileMigrationService.migrateExistingFiles();

        assertThat(report.getSuccesses()).isEmpty();
        assertThat(report.getSkipped()).isEmpty();
        assertThat(report.getFailures()).isEmpty();
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("빈 디렉토리 - 빈 리포트 반환")
    void migrateExistingFiles_returnsEmptyReportForEmptyDirectory() {
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        assertThat(report.getSuccesses()).isEmpty();
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 파일 마이그레이션 성공")
    void migrateExistingFiles_migratesNewFile() throws Exception {
        // given
        Path testFile = tempDir.resolve("test.docx");
        Files.writeString(testFile, "test content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull("test.docx"))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        // then
        assertThat(report.getSuccesses()).hasSize(1);
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("이미 존재하는 파일은 스킵")
    void migrateExistingFiles_skipsExistingDocument() throws Exception {
        // given
        Path testFile = tempDir.resolve("existing.docx");
        Files.writeString(testFile, "existing content");

        Document existingDoc = Document.builder()
                .fileName("existing.docx")
                .fileKey("existing-key")
                .build();
        when(documentRepository.findByFileNameAndDeletedAtIsNull("existing.docx"))
                .thenReturn(Optional.of(existingDoc));

        // when
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        // then
        assertThat(report.getSkipped()).hasSize(1);
        assertThat(report.getSuccesses()).isEmpty();
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("숨김 파일(.으로 시작)은 무시")
    void migrateExistingFiles_ignoresHiddenFiles() throws Exception {
        // given
        Path hiddenFile = tempDir.resolve(".hidden");
        Files.writeString(hiddenFile, "hidden content");

        // when
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        // then
        assertThat(report.getSuccesses()).isEmpty();
        assertThat(report.getSkipped()).isEmpty();
        verify(documentRepository, never()).findByFileNameAndDeletedAtIsNull(anyString());
    }

    @Test
    @DisplayName("여러 파일 마이그레이션 - 성공/스킵 혼합")
    void migrateExistingFiles_handlesMixedResults() throws Exception {
        // given
        Path newFile = tempDir.resolve("new.xlsx");
        Path existingFile = tempDir.resolve("existing.pptx");
        Files.writeString(newFile, "new content");
        Files.writeString(existingFile, "existing content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull("new.xlsx"))
                .thenReturn(Optional.empty());
        when(documentRepository.findByFileNameAndDeletedAtIsNull("existing.pptx"))
                .thenReturn(Optional.of(Document.builder().fileName("existing.pptx").build()));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        // then
        assertThat(report.getSuccesses()).hasSize(1);
        assertThat(report.getSkipped()).hasSize(1);
    }

    @Test
    @DisplayName("다양한 확장자 파일 타입 결정 - word")
    void migrateExistingFiles_determinesWordType() throws Exception {
        // given
        Path docxFile = tempDir.resolve("document.docx");
        Files.writeString(docxFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getDocumentType()).isEqualTo("word");
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("다양한 확장자 파일 타입 결정 - cell")
    void migrateExistingFiles_determinesCellType() throws Exception {
        // given
        Path xlsxFile = tempDir.resolve("spreadsheet.xlsx");
        Files.writeString(xlsxFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getDocumentType()).isEqualTo("cell");
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("다양한 확장자 파일 타입 결정 - slide")
    void migrateExistingFiles_determinesSlideType() throws Exception {
        // given
        Path pptxFile = tempDir.resolve("presentation.pptx");
        Files.writeString(pptxFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getDocumentType()).isEqualTo("slide");
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("다양한 확장자 파일 타입 결정 - pdf")
    void migrateExistingFiles_determinesPdfType() throws Exception {
        // given
        Path pdfFile = tempDir.resolve("document.pdf");
        Files.writeString(pdfFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getDocumentType()).isEqualTo("pdf");
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("알 수 없는 확장자는 word로 기본값")
    void migrateExistingFiles_defaultsToWordForUnknownExtension() throws Exception {
        // given
        Path unknownFile = tempDir.resolve("file.xyz");
        Files.writeString(unknownFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getDocumentType()).isEqualTo("word");
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("확장자 없는 파일은 빈 문자열 확장자")
    void migrateExistingFiles_handlesFileWithoutExtension() throws Exception {
        // given
        Path noExtFile = tempDir.resolve("noextension");
        Files.writeString(noExtFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    assertThat(doc.getFileType()).isEmpty();
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("DB 저장 실패 시 실패 리포트에 추가")
    void migrateExistingFiles_reportsFailureOnSaveError() throws Exception {
        // given
        Path testFile = tempDir.resolve("fail.docx");
        Files.writeString(testFile, "content");

        when(documentRepository.findByFileNameAndDeletedAtIsNull("fail.docx"))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenThrow(new RuntimeException("DB error"));

        // when
        MigrationReport report = fileMigrationService.migrateExistingFiles();

        // then
        assertThat(report.getFailures()).hasSize(1);
        assertThat(report.getSuccesses()).isEmpty();
    }

    @Test
    @DisplayName("마이그레이션된 문서는 올바른 필드 값을 가짐")
    void migrateExistingFiles_setsCorrectDocumentFields() throws Exception {
        // given
        Path testFile = tempDir.resolve("sample.docx");
        Files.writeString(testFile, "sample content for testing");

        when(documentRepository.findByFileNameAndDeletedAtIsNull("sample.docx"))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document doc = invocation.getArgument(0);
                    // 검증
                    assertThat(doc.getFileName()).isEqualTo("sample.docx");
                    assertThat(doc.getFileKey()).isNotBlank();
                    assertThat(doc.getFileType()).isEqualTo("docx");
                    assertThat(doc.getDocumentType()).isEqualTo("word");
                    assertThat(doc.getFileSize()).isGreaterThan(0);
                    assertThat(doc.getStoragePath()).isEqualTo("sample.docx");
                    assertThat(doc.getCreatedBy()).isEqualTo("migration");
                    assertThat(doc.getEditorVersion()).isZero();
                    return doc;
                });

        // when
        fileMigrationService.migrateExistingFiles();

        // then
        verify(documentRepository).save(any(Document.class));
    }
}
