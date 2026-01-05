package com.example.onlyoffice.repository;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentRepository 락 메커니즘 테스트.
 *
 * <p>Issue #17 Scenario 4 & 5 구현:</p>
 * <ul>
 *   <li>Scenario 4: 비관적 락 타임아웃 (3초)</li>
 *   <li>Scenario 5: 낙관적 락 버전 충돌</li>
 * </ul>
 *
 * <p><b>Note</b>: H2 in-memory database 사용으로 PostgreSQL과 락 동작이 약간 다를 수 있음.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DocumentRepository 락(Lock) 메커니즘 테스트")
class DocumentRepositoryLockTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // Set transaction timeout to 10 seconds (longer than pessimistic lock timeout of 3s)
        // Transaction timeout controls the overall transaction execution time
        // Pessimistic lock timeout (3s) is configured in DocumentRepository.findWithLockById() via @QueryHint
        // Transaction timeout must exceed lock timeout to allow lock acquisition within the transaction
        transactionTemplate.setTimeout(10);
    }

    @Nested
    @DisplayName("비관적 락 (Pessimistic Lock)")
    class PessimisticLockTests {

        @Test
        @DisplayName("비관적 락으로 문서를 조회하면 트랜잭션 내에서 락이 유지된다")
        void shouldMaintainPessimisticLockWithinTransaction() {
            // Given - Create test document
            Document document = createTestDocument();
            Long documentId = document.getId();

            // When - Acquire pessimistic lock within transaction
            transactionTemplate.execute(status -> {
                // First lock acquisition
                Optional<Document> lockedDoc1 = documentRepository.findWithLockById(documentId);

                // Then - Document should be retrieved with lock
                assertThat(lockedDoc1).isPresent();
                assertThat(lockedDoc1.get().getId()).isEqualTo(documentId);

                // Second lock acquisition within same transaction (should succeed)
                Optional<Document> lockedDoc2 = documentRepository.findWithLockById(documentId);
                assertThat(lockedDoc2).isPresent();

                // Modify document and save
                Document doc = lockedDoc2.get();
                doc.setFileSize(2048L);
                documentRepository.saveAndFlush(doc);

                return null;
            });

            // Verify document was updated
            Document updated = documentRepository.findById(documentId).orElseThrow();
            assertThat(updated.getFileSize()).isEqualTo(2048L);
        }

        @Test
        @Disabled("""
                H2 in-memory database는 PostgreSQL과 달리 MVCC(Multi-Version Concurrency Control)를 완전히
                지원하지 않아서 다중 트랜잭션 환경에서 락 차단 동작을 정확히 시뮬레이션하지 못합니다.
                Issue #17 Scenario 4의 "두 번째 요청이 타임아웃으로 실패"하는 동작은
                PostgreSQL 환경 (docker-compose up)에서 수동 검증이 필요합니다.
                """)
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        @DisplayName("비관적 락 보유 중 다른 트랜잭션의 접근이 차단되고 타임아웃된다")
        void shouldBlockConcurrentAccessWithPessimisticLock() throws Exception {
            // Given - Create test document
            Document document = createTestDocument();
            Long documentId = document.getId();

            CountDownLatch thread1Started = new CountDownLatch(1);
            AtomicReference<Exception> thread2Exception = new AtomicReference<>();

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                // Thread 1: Acquire lock and hold for 5 seconds
                Future<?> thread1 = executorService.submit(() -> {
                    transactionTemplate.execute(status -> {
                        documentRepository.findWithLockById(documentId);
                        thread1Started.countDown();  // Signal lock acquired
                        try {
                            Thread.sleep(5000);  // Hold lock for 5 seconds
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
                });

                // Wait for thread 1 to acquire lock
                thread1Started.await(2, TimeUnit.SECONDS);
                Thread.sleep(200);  // Buffer to ensure lock is held

                // Thread 2: Try to acquire same lock (should timeout at 3 seconds)
                Future<?> thread2 = executorService.submit(() -> {
                    try {
                        transactionTemplate.execute(status -> {
                            documentRepository.findWithLockById(documentId);
                            return null;
                        });
                    } catch (Exception e) {
                        thread2Exception.set(e);
                    }
                });

                // Then - Thread 2 should timeout
                thread2.get(10, TimeUnit.SECONDS);

                Exception exception = thread2Exception.get();
                assertThat(exception).isNotNull();
                assertThat(exception).satisfiesAnyOf(
                    ex -> assertThat(ex).isInstanceOf(PessimisticLockException.class),
                    ex -> assertThat(ex).isInstanceOf(QueryTimeoutException.class),
                    ex -> assertThat(ex.getCause()).isInstanceOf(PessimisticLockException.class),
                    ex -> assertThat(ex.getCause()).isInstanceOf(QueryTimeoutException.class)
                );

                thread1.get();  // Wait for thread 1 to complete
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Nested
    @DisplayName("낙관적 락 (Optimistic Lock)")
    class OptimisticLockTests {

        @Test
        @DisplayName("동시에 같은 문서를 수정하면 버전 충돌로 OptimisticLockException이 발생한다")
        void shouldThrowOptimisticLockExceptionOnVersionConflict() {
            // Given - Create test document
            Document document = createTestDocument();
            entityManager.flush();
            entityManager.clear();

            Long documentId = document.getId();

            // Load same document in two separate contexts
            Document document1 = documentRepository.findById(documentId).orElseThrow();
            Integer initialVersion = document1.getVersion();
            assertThat(initialVersion).isNotNull().isPositive();  // Verify initial version is set

            entityManager.detach(document1);

            Document document2 = documentRepository.findById(documentId).orElseThrow();

            entityManager.detach(document2);

            // Verify both have same version
            Integer version1 = document1.getVersion();
            Integer version2 = document2.getVersion();
            assertThat(version1).isEqualTo(version2);

            // When - Modify both
            document1.setFileSize(2048L);
            document2.setFileSize(4096L);

            // First save succeeds
            documentRepository.saveAndFlush(document1);
            entityManager.clear();

            // Then - Second save should fail with OptimisticLockException
            assertThatThrownBy(() -> {
                documentRepository.saveAndFlush(document2);
            })
                    .satisfiesAnyOf(
                            ex -> assertThat(ex).isInstanceOf(ObjectOptimisticLockingFailureException.class),
                            ex -> assertThat(ex).isInstanceOf(OptimisticLockException.class),
                            ex -> assertThat(ex.getCause()).isInstanceOf(OptimisticLockException.class)
                    );

            // Verify version was incremented
            Document updated = documentRepository.findById(documentId).orElseThrow();
            assertThat(updated.getVersion()).isGreaterThan(initialVersion);
        }

        @Test
        @DisplayName("낙관적 락으로 보호된 엔티티를 stale version으로 업데이트하면 OptimisticLockException이 발생한다")
        void shouldThrowOptimisticLockExceptionWhenVersionIsStale() {
            // Given - Create document with version 1
            Document document = createTestDocument();
            entityManager.flush();
            entityManager.clear();

            Long documentId = document.getId();
            Integer initialVersion = document.getVersion();

            // Simulate two concurrent sessions
            Document session1Doc = documentRepository.findById(documentId).orElseThrow();
            entityManager.detach(session1Doc);

            Document session2Doc = documentRepository.findById(documentId).orElseThrow();
            entityManager.detach(session2Doc);

            // Session 1: Update succeeds (version increments to 2)
            session1Doc.setFileSize(2048L);
            documentRepository.saveAndFlush(session1Doc);
            entityManager.clear();

            // Session 2: Try to update with stale version (still version 1)
            session2Doc.setFileSize(4096L);

            // Then - Should fail with OptimisticLockException
            assertThatThrownBy(() -> documentRepository.saveAndFlush(session2Doc))
                    .satisfiesAnyOf(
                            ex -> assertThat(ex).isInstanceOf(ObjectOptimisticLockingFailureException.class),
                            ex -> assertThat(ex).isInstanceOf(OptimisticLockException.class),
                            ex -> assertThat(ex.getCause()).isInstanceOf(OptimisticLockException.class)
                    );

            // Verify version was incremented by exactly 1 (from session 1 update)
            Document updated = documentRepository.findById(documentId).orElseThrow();
            assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
            assertThat(updated.getFileSize()).isEqualTo(2048L);
        }
    }

    /**
     * Helper method to create a test document.
     */
    private Document createTestDocument() {
        String fileKey = KeyUtils.generateFileKey();
        Document document = Document.builder()
                .fileName("test.docx")
                .fileKey(fileKey)
                .fileType("docx")
                .documentType("word")
                .fileSize(1024L)
                .storagePath("documents/" + fileKey + "/test.docx")
                .status(DocumentStatus.ACTIVE)
                .build();

        return documentRepository.saveAndFlush(document);
    }
}
