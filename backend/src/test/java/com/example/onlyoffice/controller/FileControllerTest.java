package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.exception.GlobalExceptionHandler;
import com.example.onlyoffice.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FileController")
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    private static final String FILE_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String NON_EXISTENT_FILE_KEY = "00000000-0000-0000-0000-000000000000";

    @Nested
    @DisplayName("GET /files/{fileKey}")
    class DownloadFile {

        @Test
        @DisplayName("파일 다운로드 성공")
        void shouldDownloadFileSuccessfully() throws Exception {
            // given
            String fileContent = "test file content";
            byte[] fileBytes = fileContent.getBytes();
            Document document = createDocument(1L, "test.docx", FILE_KEY, fileBytes.length);

            when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
            when(documentService.downloadDocumentStream(FILE_KEY))
                    .thenReturn(new ByteArrayInputStream(fileBytes));

            // when - StreamingResponseBody는 비동기이므로 asyncDispatch 사용
            MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // then
            MvcResult result = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, fileBytes.length))
                    .andExpect(content().bytes(fileBytes))
                    .andReturn();

            // Content-Disposition 헤더에 파일명이 포함되어 있는지 확인
            String contentDisposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(contentDisposition).contains("attachment");
            assertThat(contentDisposition).contains("test.docx");
        }

        @Test
        @DisplayName("한글 파일명 다운로드 성공")
        void shouldDownloadFileWithKoreanFilename() throws Exception {
            // given
            String fileContent = "test file content";
            byte[] fileBytes = fileContent.getBytes();
            String koreanFileName = "테스트문서.docx";
            Document document = createDocument(1L, koreanFileName, FILE_KEY, fileBytes.length);

            when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
            when(documentService.downloadDocumentStream(FILE_KEY))
                    .thenReturn(new ByteArrayInputStream(fileBytes));

            // when
            MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // then
            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(content().bytes(fileBytes));
        }

        @Test
        @DisplayName("존재하지 않는 파일 요청 시 404 반환")
        void shouldReturn404WhenFileNotFound() throws Exception {
            // given
            when(documentService.findByFileKey(NON_EXISTENT_FILE_KEY))
                    .thenReturn(Optional.empty());

            // when & then - 동기 예외이므로 asyncDispatch 불필요
            mockMvc.perform(get("/files/{fileKey}", NON_EXISTENT_FILE_KEY))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DocumentNotFoundException 발생 시 404 반환")
        void shouldReturn404WhenDocumentNotFoundExceptionThrown() throws Exception {
            // given
            when(documentService.findByFileKey(FILE_KEY))
                    .thenThrow(new DocumentNotFoundException("Document not found for fileKey: " + FILE_KEY));

            // when & then
            mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("빈 파일 다운로드 성공")
        void shouldDownloadEmptyFile() throws Exception {
            // given
            byte[] emptyBytes = new byte[0];
            Document document = createDocument(1L, "empty.txt", FILE_KEY, 0);

            when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
            when(documentService.downloadDocumentStream(FILE_KEY))
                    .thenReturn(new ByteArrayInputStream(emptyBytes));

            // when
            MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // then
            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 0))
                    .andExpect(content().bytes(emptyBytes));
        }

        @Test
        @DisplayName("대용량 파일 스트리밍 성공")
        void shouldStreamLargeFile() throws Exception {
            // given
            int fileSize = 1024 * 1024; // 1MB
            byte[] largeFileBytes = new byte[fileSize];
            for (int i = 0; i < fileSize; i++) {
                largeFileBytes[i] = (byte) (i % 256);
            }
            Document document = createDocument(1L, "large.bin", FILE_KEY, fileSize);

            when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
            when(documentService.downloadDocumentStream(FILE_KEY))
                    .thenReturn(new ByteArrayInputStream(largeFileBytes));

            // when
            MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // then
            MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, fileSize))
                    .andReturn();

            byte[] responseContent = finalResult.getResponse().getContentAsByteArray();
            assertThat(responseContent).hasSize(fileSize);
            assertThat(responseContent).isEqualTo(largeFileBytes);
        }
    }

    private Document createDocument(Long id, String fileName, String fileKey, long fileSize) {
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
                .fileSize(fileSize)
                .storagePath("documents/" + fileKey + "/" + fileName)
                .status(DocumentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("anonymous")
                .build();
    }
}
