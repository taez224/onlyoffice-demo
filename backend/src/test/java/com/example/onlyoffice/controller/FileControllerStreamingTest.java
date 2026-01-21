package com.example.onlyoffice.controller;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.exception.GlobalExceptionHandler;
import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.service.MinioStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FileController의 스트림 생명주기 테스트.
 * <p>
 * StreamingResponseBody 사용 시 스트림이 모든 시나리오에서
 * 올바르게 닫히는지 검증합니다.
 */
@WebMvcTest(FileController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FileController Streaming Tests")
class FileControllerStreamingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private MinioStorageService storageService;

    private static final String FILE_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("정상 다운로드 완료 시 스트림이 닫혀야 함")
    void shouldCloseStreamOnSuccessfulDownload() throws Exception {
        // given
        String fileContent = "test file content";
        byte[] fileBytes = fileContent.getBytes();
        Document document = createDocument(1L, "test.docx", FILE_KEY, fileBytes.length);

        AtomicBoolean streamClosed = new AtomicBoolean(false);
        InputStream trackingStream = new TrackingInputStream(
                new ByteArrayInputStream(fileBytes), streamClosed);

        when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
        when(storageService.downloadFile(document.getStoragePath())).thenReturn(trackingStream);

        // when
        MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        // then
        assertThat(streamClosed.get())
                .as("InputStream should be closed after successful download")
                .isTrue();
    }

    @Test
    @DisplayName("IOException 발생 시에도 스트림이 닫혀야 함")
    void shouldCloseStreamOnIOException() throws Exception {
        // given
        Document document = createDocument(1L, "test.docx", FILE_KEY, 1024);

        AtomicBoolean streamClosed = new AtomicBoolean(false);
        InputStream failingStream = new FailingInputStream(streamClosed);

        when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
        when(storageService.downloadFile(document.getStoragePath())).thenReturn(failingStream);

        // when
        MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

        // IOException이 UncheckedIOException으로 전파되지만 스트림은 닫혀야 함
        try {
            mockMvc.perform(asyncDispatch(mvcResult));
        } catch (Exception e) {
            // 예외 전파는 정상 동작
        }

        // then - 핵심: 스트림이 닫혔는지 확인
        assertThat(streamClosed.get())
                .as("InputStream should be closed even when IOException occurs")
                .isTrue();
    }

    @Test
    @DisplayName("부분 읽기 후 예외 발생 시에도 스트림이 닫혀야 함")
    void shouldCloseStreamOnPartialReadWithException() throws Exception {
        // given
        Document document = createDocument(1L, "test.docx", FILE_KEY, 10000);

        AtomicBoolean streamClosed = new AtomicBoolean(false);
        // 일부 데이터를 반환한 후 예외 발생
        InputStream partialFailingStream = new PartialFailingInputStream(
                1000, streamClosed);

        when(documentService.findByFileKey(FILE_KEY)).thenReturn(Optional.of(document));
        when(storageService.downloadFile(document.getStoragePath())).thenReturn(partialFailingStream);

        // when
        MvcResult mvcResult = mockMvc.perform(get("/files/{fileKey}", FILE_KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

        // IOException이 UncheckedIOException으로 전파되지만 스트림은 닫혀야 함
        try {
            mockMvc.perform(asyncDispatch(mvcResult));
        } catch (Exception e) {
            // 예외 전파는 정상 동작
        }

        // then - 핵심: 스트림이 닫혔는지 확인
        assertThat(streamClosed.get())
                .as("InputStream should be closed even after partial read with exception")
                .isTrue();
    }

    private Document createDocument(Long id, String fileName, String fileKey, long fileSize) {
        return Document.builder()
                .id(id)
                .fileName(fileName)
                .fileKey(fileKey)
                .fileType("docx")
                .documentType("word")
                .fileSize(fileSize)
                .storagePath("documents/" + fileKey + "/" + fileName)
                .status(DocumentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("anonymous")
                .build();
    }

    /**
     * close() 호출을 추적하는 InputStream 래퍼
     */
    private static class TrackingInputStream extends InputStream {
        private final InputStream delegate;
        private final AtomicBoolean closed;

        TrackingInputStream(InputStream delegate, AtomicBoolean closed) {
            this.delegate = delegate;
            this.closed = closed;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            delegate.close();
        }
    }

    /**
     * 즉시 IOException을 발생시키는 InputStream
     */
    private static class FailingInputStream extends InputStream {
        private final AtomicBoolean closed;

        FailingInputStream(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public int read() throws IOException {
            throw new IOException("Simulated read failure");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException("Simulated read failure");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /**
     * 일부 데이터 후 IOException을 발생시키는 InputStream
     */
    private static class PartialFailingInputStream extends InputStream {
        private final int bytesBeforeFailure;
        private final AtomicBoolean closed;
        private int bytesRead = 0;

        PartialFailingInputStream(int bytesBeforeFailure, AtomicBoolean closed) {
            this.bytesBeforeFailure = bytesBeforeFailure;
            this.closed = closed;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= bytesBeforeFailure) {
                throw new IOException("Simulated failure after partial read");
            }
            bytesRead++;
            return 'X';
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (bytesRead >= bytesBeforeFailure) {
                throw new IOException("Simulated failure after partial read");
            }
            int toRead = Math.min(len, bytesBeforeFailure - bytesRead);
            for (int i = 0; i < toRead; i++) {
                b[off + i] = 'X';
            }
            bytesRead += toRead;
            return toRead;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
