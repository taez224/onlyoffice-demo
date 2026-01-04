package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.exception.GlobalExceptionHandler;
import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.service.EditorConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("DocumentController")
class DocumentControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private EditorConfigService editorConfigService;

    private static final String FILE_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String NON_EXISTENT_FILE_KEY = "00000000-0000-0000-0000-000000000000";

    @Nested
    @DisplayName("GET /api/documents")
    class GetDocuments {

        @Test
        @DisplayName("ACTIVE 문서 목록 반환")
        void shouldReturnActiveDocuments() {
            // given
            List<Document> documents = List.of(
                    createDocument(1L, "doc1.docx", FILE_KEY),
                    createDocument(2L, "doc2.xlsx", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            );
            when(documentService.getActiveDocuments()).thenReturn(documents);

            // when
            MvcTestResult result = mvc.get().uri("/api/documents").exchange();

            // then
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
            assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$[0].fileName").isEqualTo("doc1.docx");
            assertThat(result).bodyJson().extractingPath("$[0].fileKey").isEqualTo(FILE_KEY);
            assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("ACTIVE");
            assertThat(result).bodyJson().extractingPath("$[1].id").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$[1].fileName").isEqualTo("doc2.xlsx");
        }

        @Test
        @DisplayName("문서가 없을 때 빈 배열 반환")
        void shouldReturnEmptyListWhenNoDocuments() {
            // given
            when(documentService.getActiveDocuments()).thenReturn(List.of());

            // when
            MvcTestResult result = mvc.get().uri("/api/documents").exchange();

            // then
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$").asArray().isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/documents/upload")
    class UploadDocument {

        @Test
        @DisplayName("파일 업로드 성공 시 201 반환")
        void shouldReturnCreatedOnSuccessfulUpload() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.docx",
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "test content".getBytes()
            );
            Document document = createDocument(1L, "test.docx", FILE_KEY);
            when(documentService.uploadDocument(any())).thenReturn(document);

            // when
            MvcTestResult result = mvc.post().uri("/api/documents/upload")
                    .multipart()
                    .file(file)
                    .exchange();

            // then
            assertThat(result).hasStatus(201);
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.fileName").isEqualTo("test.docx");
            assertThat(result).bodyJson().extractingPath("$.fileKey").isEqualTo(FILE_KEY);
            assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Document uploaded successfully");
        }
    }

    @Nested
    @DisplayName("DELETE /api/documents/{fileKey}")
    class DeleteDocument {

        @Test
        @DisplayName("문서 삭제 성공 시 204 반환")
        void shouldReturnNoContentOnSuccessfulDelete() {
            // given
            Document document = createDocument(1L, "test.docx", FILE_KEY);
            when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
            doNothing().when(documentService).deleteDocument(1L);

            // when
            MvcTestResult result = mvc.delete().uri("/api/documents/{fileKey}", FILE_KEY).exchange();

            // then
            assertThat(result).hasStatus(204);
            verify(documentService).findByFileKey(FILE_KEY);
            verify(documentService).deleteDocument(1L);
        }

        @Test
        @DisplayName("존재하지 않는 문서 삭제 시 404 반환")
        void shouldReturn404WhenDocumentNotFound() {
            // given
            when(documentService.findByFileKey(NON_EXISTENT_FILE_KEY)).thenReturn(Optional.empty());

            // when
            MvcTestResult result = mvc.delete().uri("/api/documents/{fileKey}", NON_EXISTENT_FILE_KEY).exchange();

            // then
            assertThat(result).hasStatus(404);
        }

        @Test
        @DisplayName("잘못된 fileKey 형식 시 400 반환")
        void shouldReturn400WhenInvalidFileKeyFormat() {
            // given
            String invalidFileKey = "invalid-key-format";

            // when
            MvcTestResult result = mvc.delete().uri("/api/documents/{fileKey}", invalidFileKey).exchange();

            // then
            assertThat(result).hasStatus(400);
        }
    }

    @Nested
    @DisplayName("GET /api/documents/{fileKey}/config")
    class GetEditorConfig {

        @Test
        @DisplayName("에디터 설정 반환 성공")
        void shouldReturnEditorConfig() {
            // given
            Map<String, Object> editorResponse = new HashMap<>();
            editorResponse.put("config", Map.of("document", Map.of("key", FILE_KEY + "_v1")));
            editorResponse.put("documentServerUrl", "http://localhost:9980");

            when(editorConfigService.createEditorResponseByFileKey(FILE_KEY))
                    .thenReturn(editorResponse);

            // when
            MvcTestResult result = mvc.get().uri("/api/documents/{fileKey}/config", FILE_KEY).exchange();

            // then
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.documentServerUrl").isEqualTo("http://localhost:9980");
            assertThat(result).bodyJson().extractingPath("$.config").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.config.document.key").isEqualTo(FILE_KEY + "_v1");
        }

        @Test
        @DisplayName("존재하지 않는 문서의 설정 조회 시 404 반환")
        void shouldReturn404WhenDocumentNotFound() {
            // given
            when(editorConfigService.createEditorResponseByFileKey(NON_EXISTENT_FILE_KEY))
                    .thenThrow(new DocumentNotFoundException("Document not found for fileKey: " + NON_EXISTENT_FILE_KEY));

            // when
            MvcTestResult result = mvc.get().uri("/api/documents/{fileKey}/config", NON_EXISTENT_FILE_KEY).exchange();

            // then
            assertThat(result).hasStatus(404);
        }

        @Test
        @DisplayName("잘못된 fileKey 형식 시 400 반환")
        void shouldReturn400WhenInvalidFileKeyFormat() {
            // given
            String invalidFileKey = "not-a-valid-uuid";

            // when
            MvcTestResult result = mvc.get().uri("/api/documents/{fileKey}/config", invalidFileKey).exchange();

            // then
            assertThat(result).hasStatus(400);
        }
    }

    private Document createDocument(Long id, String fileName, String fileKey) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        String documentType = switch (extension) {
            case "xlsx", "xls" -> "cell";
            case "pptx", "ppt" -> "slide";
            default -> "word";
        };

        return Document.builder()
                .id(id)
                .fileName(fileName)
                .fileKey(fileKey)
                .fileType(extension)
                .documentType(documentType)
                .fileSize(1024L)
                .storagePath("documents/" + fileKey + "/" + fileName)
                .status(DocumentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("anonymous")
                .build();
    }
}
