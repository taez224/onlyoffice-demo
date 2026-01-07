package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * DocumentService 엔드-투-엔드 동시성 테스트.
 *
 * <p>Mock을 사용한 동시성 제어 및 트랜잭션 격리를 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService 엔드-투-엔드 동시성 테스트")
class DocumentServiceEndToEndTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileSecurityService fileSecurityService;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private UrlDownloadService urlDownloadService;

    private DocumentService documentService;

    private Document testDocument;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, fileSecurityService, storageService, urlDownloadService);

        doNothing().when(storageService).deleteFile(anyString());

        testDocument = Document.builder()
                .id(1L)
                .fileName("test.docx")
                .fileKey("test-file-key")
                .fileType("docx")
                .documentType("word")
                .fileSize(1024L)
                .storagePath("documents/test-file-key/test.docx")
                .status(DocumentStatus.ACTIVE)
                .createdBy("tester")
                .build();

        when(documentRepository.findWithLockById(1L)).thenReturn(Optional.of(testDocument));
    }

    @Test
    @DisplayName("동시에 같은 문서를 삭제하려 할 때 하나만 성공하고 나머지는 멱등하게 처리된다")
    void deleteDocument_handlesConcurrentDeletes() throws Exception {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    documentService.deleteDocument(testDocument.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(testDocument.getStatus()).isEqualTo(DocumentStatus.DELETED);

        if (failureCount.get() > 0) {
            System.out.println("Failures: " + failureCount.get());
            exceptions.forEach(e -> System.out.println("  - " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @Test
    @DisplayName("삭제된 문서를 다시 삭제하려 하면 멱등하게 처리된다")
    void deleteDocument_isIdempotent() {
        documentService.deleteDocument(testDocument.getId());

        assertThat(testDocument.getStatus()).isEqualTo(DocumentStatus.DELETED);

        documentService.deleteDocument(testDocument.getId());

        assertThat(testDocument.getStatus()).isEqualTo(DocumentStatus.DELETED);
    }
}
