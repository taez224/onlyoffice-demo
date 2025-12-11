package com.example.onlyoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.Document;
import com.onlyoffice.model.documenteditor.config.document.DocumentType;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.service.documenteditor.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EditorConfigService")
@ExtendWith(MockitoExtension.class)
class EditorConfigServiceTest {

    @Mock
    private ConfigService sdkConfigService;

    @Mock
    private Config mockConfig;

    @Mock
    private Document mockDocument;

    private EditorConfigService editorConfigService;
    private ObjectMapper objectMapper;

    private static final String ONLYOFFICE_URL = "http://localhost:9980";
    private static final String FILE_NAME = "sample.docx";
    private static final String DOCUMENT_KEY = "sampledocx_v1";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        editorConfigService = new EditorConfigService(sdkConfigService, objectMapper);
        ReflectionTestUtils.setField(editorConfigService, "onlyofficeUrl", ONLYOFFICE_URL);
    }

    @Nested
    @DisplayName("createEditorResponse")
    class CreateEditorResponse {

        @Test
        @DisplayName("SDK ConfigService를 호출하여 Config 생성")
        void shouldCallSdkConfigServiceToCreateConfig() {
            // given
            when(sdkConfigService.createConfig(
                eq(FILE_NAME),
                eq(Mode.EDIT),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            editorConfigService.createEditorResponse(FILE_NAME);

            // then
            verify(sdkConfigService).createConfig(
                eq(FILE_NAME),
                eq(Mode.EDIT),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            );
        }

        @Test
        @DisplayName("응답에 config와 documentServerUrl 포함")
        void shouldIncludeConfigAndDocumentServerUrl() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            Map<String, Object> response = editorConfigService.createEditorResponse(FILE_NAME);

            // then
            assertThat(response).containsKeys("config", "documentServerUrl");
        }

        @Test
        @DisplayName("documentServerUrl이 정확히 설정됨")
        void shouldSetCorrectDocumentServerUrl() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            Map<String, Object> response = editorConfigService.createEditorResponse(FILE_NAME);

            // then
            assertThat(response.get("documentServerUrl")).isEqualTo(ONLYOFFICE_URL);
        }

        @Test
        @DisplayName("config가 Map으로 변환됨")
        void shouldConvertConfigToMap() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            Map<String, Object> response = editorConfigService.createEditorResponse(FILE_NAME);

            // then
            assertThat(response.get("config")).isNotNull();
            assertThat(response.get("config")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("다양한 파일명으로 Config 생성")
        void shouldCreateConfigForVariousFileNames() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            String[] fileNames = {"document.docx", "spreadsheet.xlsx", "presentation.pptx"};

            for (String fileName : fileNames) {
                // when
                Map<String, Object> response = editorConfigService.createEditorResponse(fileName);

                // then
                assertThat(response)
                    .as("Response for %s should contain required keys", fileName)
                    .containsKeys("config", "documentServerUrl");
            }
        }
    }

    @Nested
    @DisplayName("Integration with SDK")
    class IntegrationWithSdk {

        @Test
        @DisplayName("Mode.EDIT로 Config 생성")
        void shouldCreateConfigWithEditMode() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            editorConfigService.createEditorResponse(FILE_NAME);

            // then
            verify(sdkConfigService).createConfig(
                eq(FILE_NAME),
                eq(Mode.EDIT),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            );
        }

        @Test
        @DisplayName("Type.DESKTOP으로 Config 생성")
        void shouldCreateConfigWithDesktopType() {
            // given
            when(sdkConfigService.createConfig(
                anyString(),
                any(Mode.class),
                any(com.onlyoffice.model.documenteditor.config.document.Type.class)
            )).thenReturn(mockConfig);
            when(mockConfig.getDocument()).thenReturn(mockDocument);
            when(mockDocument.getKey()).thenReturn(DOCUMENT_KEY);

            // when
            editorConfigService.createEditorResponse(FILE_NAME);

            // then
            verify(sdkConfigService).createConfig(
                eq(FILE_NAME),
                eq(Mode.EDIT),
                eq(com.onlyoffice.model.documenteditor.config.document.Type.DESKTOP)
            );
        }
    }
}
