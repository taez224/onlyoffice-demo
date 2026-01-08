package com.example.onlyoffice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Slf4j
@Service
public class CallbackQueueService {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_TASK_TIMEOUT_SECONDS = 60;

    @Value("${callback.executor.idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Value("${callback.executor.cleanup-interval-minutes:5}")
    private long cleanupIntervalMinutes;

    // Single map containing managed executors with atomic state transitions
    // Replaces previous dual-map approach (documentQueues + lastAccessTime)
    private final ConcurrentHashMap<String, ManagedExecutor> documentExecutors = new ConcurrentHashMap<>();

    /**
     * Callback 작업을 문서별 큐에 제출하고 완료까지 대기합니다.
     *
     * <p><b>동시성 제어:</b></p>
     * <ul>
     *   <li>Lock-free CAS 기반 상태 머신</li>
     *   <li>동일 문서의 callback: 순차 처리</li>
     *   <li>다른 문서의 callback: 병렬 처리</li>
     *   <li>Race condition 제거: atomic state transitions</li>
     * </ul>
     *
     * @param fileKey 문서 식별자
     * @param task    실행할 작업
     * @param <T>     반환 타입
     * @return 작업 결과
     * @throws Exception 작업 실행 중 발생한 예외
     */
    public <T> T submitAndWait(String fileKey, Callable<T> task) throws Exception {
        return submitAndWait(fileKey, task, DEFAULT_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Callback 작업을 문서별 큐에 제출하고 지정된 시간 동안 완료를 대기합니다.
     *
     * <p><b>재시도 로직:</b></p>
     * Executor가 shutdown 상태인 경우 새로운 executor를 생성하고 재시도합니다.
     * 이는 cleanup과 submit의 race condition 해결을 위한 설계입니다.
     *
     * @param fileKey 문서 식별자
     * @param task    실행할 작업
     * @param timeout 대기 시간
     * @param unit    시간 단위
     * @param <T>     반환 타입
     * @return 작업 결과
     * @throws Exception        작업 실행 중 발생한 예외
     * @throws TimeoutException 타임아웃 발생 시
     */
    public <T> T submitAndWait(String fileKey, Callable<T> task, long timeout, TimeUnit unit) throws Exception {
        log.debug("Queueing callback for fileKey: {}", fileKey);

        final int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            ManagedExecutor managed = documentExecutors.computeIfAbsent(fileKey, key ->
                    new ManagedExecutor(key, createExecutor(key))
            );

            Future<T> future = managed.trySubmit(task);

            if (future != null) {
                try {
                    T result = future.get(timeout, unit);
                    log.debug("Callback completed successfully for fileKey: {}", fileKey);
                    return result;
                } catch (ExecutionException e) {
                    log.error("Callback execution failed for fileKey: {}", fileKey, e.getCause());
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw new RuntimeException("Callback execution failed", cause);
                } catch (TimeoutException e) {
                    log.error("Callback timed out for fileKey: {} after {} {}", fileKey, timeout, unit);
                    future.cancel(true);
                    throw e;
                } catch (InterruptedException e) {
                    log.warn("Callback interrupted for fileKey: {}", fileKey);
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    throw e;
                }
            } else {
                log.info("Executor shutting down for fileKey: {}, attempt {}/{}", fileKey, attempt + 1, maxRetries);
                documentExecutors.remove(fileKey, managed);
            }
        }
        throw new IllegalStateException("Failed to submit callback task after " + maxRetries + " retries for fileKey: " + fileKey);
    }

    /**
     * 반환값이 없는 Callback 작업을 문서별 큐에 제출하고 완료까지 대기합니다.
     *
     * @param fileKey 문서 식별자
     * @param task    실행할 작업
     * @throws Exception 작업 실행 중 발생한 예외
     */
    public void submitAndWait(String fileKey, Runnable task) throws Exception {
        submitAndWait(fileKey, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 문서별 executor를 생성합니다.
     *
     * @param fileKey 문서 식별자
     * @return 문서별 싱글 스레드 executor
     */
    private ExecutorService createExecutor(String fileKey) {
        log.info("Creating single-thread executor for fileKey: {}", fileKey);
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "callback-" + fileKey);
            thread.setDaemon(false);
            return thread;
        });
    }

    /**
     * 서비스 종료 시 모든 executor를 graceful하게 shutdown합니다.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down CallbackQueueService with {} document queues...", documentExecutors.size());

        try {
            // Shutdown all executors in parallel
            documentExecutors.values().parallelStream().forEach(managed -> {
                try {
                    managed.forceShutdown(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while shutting down executor");
                }
            });

            documentExecutors.clear();
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }

        log.info("CallbackQueueService shutdown complete");
    }

    /**
     * 30분 이상 idle한 executor를 정리하는 scheduled job (5분마다 실행).
     *
     * <p><b>동시성 안전성:</b></p>
     * <ul>
     *   <li>Atomic state machine: ACTIVE → IDLE → SHUTTING_DOWN 상태 전환</li>
     *   <li>ACTIVE 상태인 executor는 shutdown할 수 없음 (submit과의 race 방지)</li>
     *   <li>CAS 기반 원자적 상태 전환으로 race condition 완전히 제거</li>
     * </ul>
     */
    @Scheduled(fixedDelayString = "${callback.executor.cleanup-interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void cleanupIdleExecutors() {
        long now = System.currentTimeMillis();
        long idleThresholdMs = TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);

        int cleanupCount = 0;

        for (var iterator = documentExecutors.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            String fileKey = entry.getKey();
            ManagedExecutor managed = entry.getValue();

            // Two atomic steps to safely cleanup
            // Step 1: Try to mark as IDLE if inactive for threshold
            if (managed.tryMarkIdle(now, idleThresholdMs)) {
                // Step 2: Try to shutdown (only succeeds if truly IDLE)
                if (managed.tryShutdown()) {
                    try {
                        managed.forceShutdown(5, TimeUnit.SECONDS);
                        iterator.remove();
                        cleanupCount++;
                        log.info("Cleaned up idle executor for fileKey: {}", fileKey);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Cleanup interrupted for fileKey: {}", fileKey);
                        break;
                    }
                }
            }
        }

        if (cleanupCount > 0) {
            log.info("Cleanup completed: {} idle executors removed", cleanupCount);
        }
    }

    /**
     * 생성된 executor 개수 조회 (모니터링/테스트용).
     *
     * @return 현재 관리 중인 executor 개수
     */
    public int getQueueCount() {
        return documentExecutors.size();
    }

    /**
     * 모든 큐가 종료되었는지 확인합니다 (테스트용).
     *
     * @return 모두 종료되면 true
     */
    public boolean allQueuesShutdown() {
        return documentExecutors.values().stream().allMatch(ManagedExecutor::isShutdown);
    }

    /**
     * 큐가 비어있는지 확인합니다 (테스트용).
     *
     * @return 큐가 비어있으면 true
     */
    public boolean isEmpty() {
        return documentExecutors.isEmpty();
    }
}
