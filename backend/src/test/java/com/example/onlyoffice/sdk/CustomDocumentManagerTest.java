package com.example.onlyoffice.sdk;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CustomDocumentManager")
class CustomDocumentManagerTest {

    private CustomDocumentManager documentManager;
    private CustomSettingsManager settingsManager;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        // Use real CustomSettingsManager for integration testing
        settingsManager = new CustomSettingsManager();
        ReflectionTestUtils.setField(settingsManager, "documentServerUrl", "http://localhost:9980");
        ReflectionTestUtils.setField(settingsManager, "jwtSecret", "test-secret");
        ReflectionTestUtils.setField(settingsManager, "serverBaseUrl", "http://localhost:8080");

        // Mock DocumentService
        documentService = mock(DocumentService.class);

        documentManager = new CustomDocumentManager(settingsManager, documentService);
    }

    @Nested
    @DisplayName("getDocumentKey")
    class GetDocumentKey {

        @Test
        @DisplayName("fileKey로 Document를 조회하여 editorKey 반환")
        void shouldReturnEditorKeyFromDocumentService() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";
            String expectedKey = "550e8400-e29b-41d4-a716-446655440000_v1";

            // Mock DocumentService behavior
            when(documentService.getEditorKeyByFileKey(fileKey))
                    .thenReturn(expectedKey);

            // when
            String result = documentManager.getDocumentKey(fileKey, false);

            // then
            assertThat(result).isEqualTo(expectedKey);
        }

        @Test
        @DisplayName("UUID fileKey로 버전 0인 document key 생성")
        void shouldReturnEditorKeyVersion0() {
            // given
            String fileKey = "abc-123-def-456";
            String expectedKey = "abc-123-def-456_v0";

            when(documentService.getEditorKeyByFileKey(fileKey))
                    .thenReturn(expectedKey);

            // when
            String result = documentManager.getDocumentKey(fileKey, false);

            // then
            assertThat(result).isEqualTo(expectedKey);
        }
    }

    @Nested
    @DisplayName("getDocumentName")
    class GetDocumentName {

        @Test
        @DisplayName("fileKey로 Document를 조회하여 원본 fileName 반환")
        void shouldReturnOriginalFileNameFromDocument() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";
            String originalFileName = "sample.docx";

            Document document = Document.builder()
                    .fileName(originalFileName)
                    .fileKey(fileKey)
                    .build();

            when(documentService.findByFileKey(fileKey))
                    .thenReturn(Optional.of(document));

            // when
            String result = documentManager.getDocumentName(fileKey);

            // then
            assertThat(result).isEqualTo(originalFileName);
        }

        @Test
        @DisplayName("Document를 찾을 수 없으면 fileKey를 fallback으로 반환")
        void shouldReturnFileKeyAsFallbackWhenDocumentNotFound() {
            // given
            String fileKey = "unknown-file-key";

            when(documentService.findByFileKey(fileKey))
                    .thenReturn(Optional.empty());

            // when
            String result = documentManager.getDocumentName(fileKey);

            // then
            assertThat(result).isEqualTo(fileKey);
        }

        @Test
        @DisplayName("특수문자가 포함된 원본 파일명도 정상 반환")
        void shouldReturnFileNameWithSpecialCharacters() {
            // given
            String fileKey = "abc-123";
            String fileName = "my-file@2024.docx";

            Document document = Document.builder()
                    .fileName(fileName)
                    .fileKey(fileKey)
                    .build();

            when(documentService.findByFileKey(fileKey))
                    .thenReturn(Optional.of(document));

            // when
            String result = documentManager.getDocumentName(fileKey);

            // then
            assertThat(result).isEqualTo(fileName);
        }
    }

    @Nested
    @DisplayName("Inherited SDK Features")
    class InheritedSdkFeatures {

        @Test
        @DisplayName("SDK의 getExtension 메서드 사용 가능")
        void shouldUseInheritedGetExtension() {
            // given
            String fileName = "document.docx";

            // when
            String extension = documentManager.getExtension(fileName);

            // then
            assertThat(extension).isEqualTo("docx");
        }

        @Test
        @DisplayName("SDK의 getBaseName 메서드 사용 가능")
        void shouldUseInheritedGetBaseName() {
            // given
            String fileName = "document.docx";

            // when
            String baseName = documentManager.getBaseName(fileName);

            // then
            assertThat(baseName).isEqualTo("document");
        }
    }
}
