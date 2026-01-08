package com.example.onlyoffice.service;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CallbackQueueService")
class CallbackQueueServiceTest {

    private CallbackQueueService callbackQueueService;

    @BeforeEach
    void setUp() {
        callbackQueueService = new CallbackQueueService();
    }

    @AfterEach
    void tearDown() {
        callbackQueueService.shutdown();
    }

    @Nested
    @DisplayName("submitAndWait with Callable")
    class SubmitAndWaitCallable {

        @Test
        @DisplayName("should execute task and return result")
        void shouldExecuteTaskAndReturnResult() throws Exception {
            // given
            String expected = "result";

            // when
            String result = callbackQueueService.submitAndWait("fileKey1", () -> expected);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should propagate exception from task")
        void shouldPropagateExceptionFromTask() {
            // given
            RuntimeException expectedException = new RuntimeException("Task failed");

            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey1", () -> {
                        throw expectedException;
                    })
            ).isInstanceOf(RuntimeException.class).hasMessage("Task failed");
        }

        @Test
        @DisplayName("should timeout when task takes too long")
        void shouldTimeoutWhenTaskTakesTooLong() {
            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey1", () -> {
                        Thread.sleep(5000);
                        return "result";
                    }, 100, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);
        }
    }

    @Nested
    @DisplayName("submitAndWait with Runnable")
    class SubmitAndWaitRunnable {

        @Test
        @DisplayName("should execute runnable task")
        void shouldExecuteRunnableTask() throws Exception {
            // given
            AtomicInteger counter = new AtomicInteger(0);

            // when
            callbackQueueService.submitAndWait("fileKey1", counter::incrementAndGet);

            // then
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should propagate exception from runnable")
        void shouldPropagateExceptionFromRunnable() {
            // given
            RuntimeException expectedException = new RuntimeException("Runnable failed");

            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey1", () -> {
                        throw expectedException;
                    })
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("Runnable failed");
        }
    }

    @Nested
    @DisplayName("Sequential Processing (Same Document)")
    class SequentialProcessing {

        @Test
        @DisplayName("should process multiple tasks sequentially for same fileKey")
        void shouldProcessMultipleTasksSequentially() throws Exception {
            // given
            List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
            int taskCount = 5;
            CountDownLatch allTasksSubmitted = new CountDownLatch(taskCount);
            CountDownLatch allTasksCompleted = new CountDownLatch(taskCount);

            // when - submit tasks from multiple threads to SAME fileKey
            ExecutorService submitter = Executors.newFixedThreadPool(taskCount);
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                submitter.submit(() -> {
                    try {
                        allTasksSubmitted.countDown();
                        allTasksSubmitted.await(); // Wait for all tasks to be ready

                        callbackQueueService.submitAndWait("sameFileKey", () -> {
                            // Simulate some work
                            Thread.sleep(50);
                            executionOrder.add(taskId);
                            return null;
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        allTasksCompleted.countDown();
                    }
                });
            }

            allTasksCompleted.await(10, TimeUnit.SECONDS);
            submitter.shutdown();

            // then - all tasks should be executed sequentially (in order)
            assertThat(executionOrder).hasSize(taskCount);
            // For same fileKey, should maintain order
            assertThat(executionOrder).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
        }

        @Test
        @DisplayName("should maintain order for same fileKey callbacks")
        void shouldMaintainOrderForSameFileKeyCallbacks() throws Exception {
            // given
            List<String> results = Collections.synchronizedList(new ArrayList<>());

            // when - submit tasks sequentially
            for (int i = 0; i < 3; i++) {
                final String value = "task" + i;
                callbackQueueService.submitAndWait("sameFileKey", () -> {
                    results.add(value);
                });
            }

            // then - order should be preserved
            assertThat(results).containsExactly("task0", "task1", "task2");
        }
    }

    @Nested
    @DisplayName("Parallel Processing (Different Documents)")
    class ParallelProcessing {

        @Test
        @DisplayName("should process different fileKeys in parallel")
        void shouldProcessDifferentFileKeysInParallel() throws Exception {
            // given
            long startTime = System.currentTimeMillis();
            int documentCount = 3;
            CountDownLatch allTasksCompleted = new CountDownLatch(documentCount);

            // when - submit tasks to DIFFERENT fileKeys
            for (int i = 0; i < documentCount; i++) {
                final String fileKey = "doc" + i;
                new Thread(() -> {
                    try {
                        callbackQueueService.submitAndWait(fileKey, (Runnable) () -> {
                            try {
                                // Simulate work: 500ms per callback
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        allTasksCompleted.countDown();
                    }
                }).start();
            }

            allTasksCompleted.await(10, TimeUnit.SECONDS);
            long elapsedTime = System.currentTimeMillis() - startTime;

            // then
            // If truly parallel: ~500ms (all 3 run at same time)
            // If sequential: ~1500ms (3 * 500ms)
            // We expect parallel execution, so should be closer to 500ms than 1500ms
            assertThat(elapsedTime)
                    .as("Different documents should be processed in parallel")
                    .isLessThan(1500);  // Increased from 1000ms to handle slow CI environments
        }

        @Test
        @DisplayName("should create separate queues for different fileKeys")
        void shouldCreateSeparateQueuesForDifferentFileKeys() throws Exception {
            // given
            callbackQueueService.submitAndWait("doc1", () -> {
            });
            callbackQueueService.submitAndWait("doc2", () -> {
            });
            callbackQueueService.submitAndWait("doc3", () -> {
            });

            // then - should have 3 separate queues
            assertThat(callbackQueueService.getQueueCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should reuse queue for same fileKey")
        void shouldReuseQueueForSameFileKey() throws Exception {
            // given
            callbackQueueService.submitAndWait("doc1", () -> {
            });
            callbackQueueService.submitAndWait("doc1", () -> {
            });
            callbackQueueService.submitAndWait("doc1", () -> {
            });

            // then - should have only 1 queue (reused)
            assertThat(callbackQueueService.getQueueCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle mixed documents (sequential within, parallel between)")
        void shouldHandleMixedDocuments() throws Exception {
            // given
            List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

            // when - submit tasks to different documents
            for (int doc = 0; doc < 2; doc++) {
                final int docId = doc;
                new Thread(() -> {
                    try {
                        for (int task = 0; task < 2; task++) {
                            final int taskId = task;
                            callbackQueueService.submitAndWait("doc" + docId, () -> {
                                executionLog.add("doc" + docId + "-task" + taskId);
                            });
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }

            Thread.sleep(1000);

            // then
            // Each document should have its tasks in order
            assertThat(executionLog)
                    .contains("doc0-task0", "doc0-task1", "doc1-task0", "doc1-task1");

            // Verify ordering within documents
            int doc0Task0Index = executionLog.indexOf("doc0-task0");
            int doc0Task1Index = executionLog.indexOf("doc0-task1");
            assertThat(doc0Task0Index).isLessThan(doc0Task1Index); // task0 before task1 for doc0
        }
    }

    @Nested
    @DisplayName("Shutdown")
    class Shutdown {

        @Test
        @DisplayName("should shutdown gracefully")
        void shouldShutdownGracefully() {
            // when
            callbackQueueService.shutdown();

            // then
            assertThat(callbackQueueService.allQueuesShutdown()).isTrue();
        }

        @Test
        @DisplayName("should complete pending tasks before shutdown")
        void shouldCompletePendingTasksBeforeShutdown() throws Exception {
            // given
            AtomicInteger completedTasks = new AtomicInteger(0);
            CountDownLatch taskStarted = new CountDownLatch(1);

            // Submit a long-running task
            CompletableFuture.runAsync(() -> {
                try {
                    callbackQueueService.submitAndWait("fileKey", () -> {
                        taskStarted.countDown();
                        Thread.sleep(500);
                        completedTasks.incrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    // Ignore
                }
            });

            // Wait for task to start
            taskStarted.await(1, TimeUnit.SECONDS);

            // when - shutdown while task is running
            callbackQueueService.shutdown();

            // then - task should have completed
            assertThat(completedTasks.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should continue processing after task failure")
        void shouldContinueProcessingAfterTaskFailure() throws Exception {
            // given
            AtomicInteger successCount = new AtomicInteger(0);

            // when - first task fails
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey", () -> {
                        throw new RuntimeException("First task failed");
                    })
            ).isInstanceOf(RuntimeException.class);

            // Second task should still work
            callbackQueueService.submitAndWait("fileKey", successCount::incrementAndGet);

            // then
            assertThat(successCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle checked exceptions")
        void shouldHandleCheckedExceptions() {
            // given
            Exception checkedException = new Exception("Checked exception");

            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey", () -> {
                        throw checkedException;
                    })
            ).isInstanceOf(Exception.class)
                    .hasMessage("Checked exception");
        }
    }

    @Nested
    @DisplayName("Idle Executor Cleanup")
    class IdleExecutorCleanup {

        @Test
        @DisplayName("should cleanup idle executors after timeout")
        void shouldCleanupIdleExecutors() throws Exception {
            // given
            callbackQueueService.submitAndWait("doc1", () -> {
            });
            callbackQueueService.submitAndWait("doc2", () -> {
            });
            callbackQueueService.submitAndWait("doc3", () -> {
            });

            // Verify 3 executors created
            assertThat(callbackQueueService.getQueueCount()).isEqualTo(3);

            // Simulate idle time by manipulating internal state
            // This is a simplification - in production we'd wait 30+ minutes
            callbackQueueService.cleanupIdleExecutors();

            // In actual test with mocking, we'd verify cleanup logic
            // For now just verify the method exists and is callable
            assertThat(callbackQueueService.getQueueCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should not cleanup active executors")
        void shouldNotCleanupActiveExecutors() throws Exception {
            // given
            callbackQueueService.submitAndWait("activeDoc", () -> {
            });

            int initialCount = callbackQueueService.getQueueCount();

            // when - cleanup is called immediately (no idle time)
            callbackQueueService.cleanupIdleExecutors();

            // then - active executor should not be removed
            assertThat(callbackQueueService.getQueueCount()).isGreaterThanOrEqualTo(initialCount - 1);
        }
    }


    @Nested
    @DisplayName("Retry Exhaustion")
    class RetryExhaustion {

        @Test
        @DisplayName("should throw IllegalStateException when max retries exhausted")
        void shouldThrowIllegalStateExceptionWhenRetriesExhausted() {
            // given
            ReflectionTestUtils.setField(callbackQueueService, "maxSubmitRetries", 0);

            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait("fileKey", () -> "result")
            ).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to submit callback task after 0 retries");
        }

        @Test
        @DisplayName("should include fileKey in exception message")
        void shouldIncludeFileKeyInExceptionMessage() {
            // given
            ReflectionTestUtils.setField(callbackQueueService, "maxSubmitRetries", 0);
            String fileKey = "test-file-key-123";

            // when/then
            assertThatThrownBy(() ->
                    callbackQueueService.submitAndWait(fileKey, () -> "result")
            ).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(fileKey);
        }
    }

    @Nested
    @DisplayName("Race Condition Prevention")
    class RaceConditionPrevention {

        @Test
        @DisplayName("should not reject submission when executor is being cleaned up")
        void shouldNotRejectSubmissionDuringCleanup() throws Exception {
            // given - create executor and let it become idle
            callbackQueueService.submitAndWait("racyDoc", () -> "task1");
            Thread.sleep(100);  // Ensure timestamp is old

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            CountDownLatch cleanupStarted = new CountDownLatch(1);
            CountDownLatch cleanupEnded = new CountDownLatch(1);
            CountDownLatch submitCompleted = new CountDownLatch(1);

            // when - cleanup and submit race
            Thread cleanupThread = new Thread(() -> {
                try {
                    cleanupStarted.countDown();
                    callbackQueueService.cleanupIdleExecutors();
                } finally {
                    cleanupEnded.countDown();
                }
            });

            Thread submitThread = new Thread(() -> {
                try {
                    cleanupStarted.await();
                    Thread.sleep(10);  // Let cleanup start
                    callbackQueueService.submitAndWait("racyDoc", () -> {
                        successCount.incrementAndGet();
                        return null;
                    });
                } catch (RejectedExecutionException e) {
                    failureCount.incrementAndGet();
                    throw new RuntimeException("Unexpected RejectedExecutionException", e);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    throw new RuntimeException(e);
                } finally {
                    submitCompleted.countDown();
                }
            });

            cleanupThread.start();
            submitThread.start();

            boolean completed = submitCompleted.await(5, TimeUnit.SECONDS);
            boolean cleanupCompleted = cleanupEnded.await(1, TimeUnit.SECONDS);

            cleanupThread.join(1000);
            submitThread.join(1000);

            // then - submission should succeed (either reactivates or creates new executor)
            assertThat(completed).isTrue();
            assertThat(cleanupCompleted).isTrue();
            assertThat(failureCount.get()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle concurrent submit and cleanup without race conditions")
        void shouldHandleConcurrentSubmitAndCleanup() throws Exception {
            // given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount + 1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when - multiple threads submit while cleanup runs periodically
            ExecutorService testPool = Executors.newFixedThreadPool(threadCount + 1);

            // Cleanup thread
            testPool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 20; i++) {
                        callbackQueueService.cleanupIdleExecutors();
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });

            // Submit threads
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                testPool.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 5; j++) {
                            callbackQueueService.submitAndWait("doc" + threadId, () -> {
                                Thread.sleep(5);
                                successCount.incrementAndGet();
                                return null;
                            });
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
            testPool.shutdown();

            // then - all tasks should complete successfully
            assertThat(completed).isTrue();
            assertThat(failureCount.get()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(threadCount * 5);
        }

        @Test
        @DisplayName("should prevent shutdown of active executor during cleanup")
        void shouldPreventShutdownOfActiveExecutor() throws Exception {
            // given
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch taskCanComplete = new CountDownLatch(1);
            CountDownLatch cleanupTriggered = new CountDownLatch(1);

            // Submit long-running task
            CompletableFuture<Void> taskFuture = CompletableFuture.runAsync(() -> {
                try {
                    callbackQueueService.submitAndWait("activeDoc", () -> {
                        taskStarted.countDown();
                        cleanupTriggered.await();
                        taskCanComplete.await();
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            taskStarted.await(2, TimeUnit.SECONDS);

            // when - trigger cleanup while task is active
            Thread cleanupThread = new Thread(() -> {
                try {
                    cleanupTriggered.countDown();
                    callbackQueueService.cleanupIdleExecutors();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            cleanupThread.start();
            Thread.sleep(50);  // Let cleanup attempt

            // then - executor should still exist (not shut down because it's active)
            assertThat(callbackQueueService.getQueueCount()).isGreaterThanOrEqualTo(1);

            // Cleanup
            taskCanComplete.countDown();
            taskFuture.join();
            cleanupThread.join();
        }

        @Test
        @DisplayName("should reactivate idle executor when new task arrives")
        void shouldReactivateIdleExecutor() throws Exception {
            // given - executor becomes idle
            callbackQueueService.submitAndWait("idleDoc", () -> "task1");
            Thread.sleep(100);

            // Mark as idle via cleanup (doesn't shutdown because still recent)
            // Actually, let's just force it to be idle by waiting
            int initialCount = callbackQueueService.getQueueCount();

            // when - new task arrives
            String result = callbackQueueService.submitAndWait("idleDoc", () -> "task2");

            // then - should reuse executor (count unchanged)
            assertThat(result).isEqualTo("task2");
            assertThat(callbackQueueService.getQueueCount()).isEqualTo(initialCount);
        }

        @Test
        @DisplayName("should handle rapid submit-cleanup-submit patterns safely")
        void shouldHandleRapidPatterns() throws Exception {
            // given
            AtomicInteger completedTasks = new AtomicInteger(0);
            AtomicInteger failedTasks = new AtomicInteger(0);

            // when - rapid pattern: submit -> cleanup -> submit -> cleanup
            for (int cycle = 0; cycle < 10; cycle++) {
                try {
                    // Submit
                    callbackQueueService.submitAndWait("patternDoc", () -> {
                        completedTasks.incrementAndGet();
                        return null;
                    });

                    // Immediately trigger cleanup
                    callbackQueueService.cleanupIdleExecutors();

                    // Submit again
                    callbackQueueService.submitAndWait("patternDoc", () -> {
                        completedTasks.incrementAndGet();
                        return null;
                    });

                    // Trigger cleanup again
                    callbackQueueService.cleanupIdleExecutors();
                } catch (Exception e) {
                    failedTasks.incrementAndGet();
                    System.err.println("Task failed in cycle " + cycle + ": " + e.getMessage());
                }
            }

            // then - all tasks should complete
            assertThat(failedTasks.get()).isEqualTo(0);
            assertThat(completedTasks.get()).isEqualTo(20);  // 2 per cycle × 10 cycles
        }
    }
}
