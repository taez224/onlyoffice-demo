package com.example.onlyoffice.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomDocumentManager")
class CustomDocumentManagerTest {

    private CustomDocumentManager documentManager;
    private CustomSettingsManager settingsManager;

    @BeforeEach
    void setUp() {
        // Use real CustomSettingsManager for integration testing
        settingsManager = new CustomSettingsManager();
        ReflectionTestUtils.setField(settingsManager, "documentServerUrl", "http://localhost:9980");
        ReflectionTestUtils.setField(settingsManager, "jwtSecret", "test-secret");
        ReflectionTestUtils.setField(settingsManager, "serverBaseUrl", "http://localhost:8080");

        documentManager = new CustomDocumentManager(settingsManager);
    }

    @Nested
    @DisplayName("getDocumentKey")
    class GetDocumentKey {

        @Test
        @DisplayName("fileId를 sanitize하여 document key 생성")
        void shouldGenerateSanitizedDocumentKey() {
            // given
            String fileId = "sample.docx";

            // when
            String result = documentManager.getDocumentKey(fileId, false);

            // then
            assertThat(result).isEqualTo("sampledocx");
        }

        @Test
        @DisplayName("특수문자가 포함된 fileId 처리")
        void shouldHandleSpecialCharactersInFileId() {
            // given
            String fileId = "my-file@2024#test.docx";

            // when
            String result = documentManager.getDocumentKey(fileId, false);

            // then
            assertThat(result).isEqualTo("my-file2024testdocx");
        }

        @Test
        @DisplayName("공백이 포함된 fileId 처리")
        void shouldHandleWhitespaceInFileId() {
            // given
            String fileId = "my document file.docx";

            // when
            String result = documentManager.getDocumentKey(fileId, false);

            // then
            assertThat(result).isEqualTo("mydocumentfiledocx");
        }
    }

    @Nested
    @DisplayName("getDocumentName")
    class GetDocumentName {

        @Test
        @DisplayName("fileId를 그대로 반환")
        void shouldReturnFileIdAsDocumentName() {
            // given
            String fileId = "sample.docx";

            // when
            String result = documentManager.getDocumentName(fileId);

            // then
            assertThat(result).isEqualTo(fileId);
        }

        @Test
        @DisplayName("특수문자가 포함된 fileId도 그대로 반환")
        void shouldReturnFileIdWithSpecialCharacters() {
            // given
            String fileId = "my-file@2024.docx";

            // when
            String result = documentManager.getDocumentName(fileId);

            // then
            assertThat(result).isEqualTo(fileId);
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
