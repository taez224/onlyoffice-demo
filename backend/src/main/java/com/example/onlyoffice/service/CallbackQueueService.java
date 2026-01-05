package com.example.onlyoffice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Callback 요청을 문서별로 순차 처리하는 큐 서비스.
 *
 * <p><b>동시성 제어 전략:</b></p>
 * <ul>
 *   <li>각 문서(fileKey)마다 독립적인 싱글 스레드 executor 관리</li>
 *   <li>동일 문서의 callback: 순차 처리 (Race condition 방지)</li>
 *   <li>다른 문서의 callback: 병렬 처리 (성능 향상)</li>
 * </ul>
 *
 * <p><b>제한사항:</b></p>
 * <ul>
 *   <li>단일 JVM 인스턴스에서만 동작</li>
 *   <li>수평 확장(다중 인스턴스) 배포 시 Redis/Kafka 기반 분산 큐로 개선 필요</li>
 * </ul>
 *
 * @see com.example.onlyoffice.sdk.CustomCallbackService
 */
@Slf4j
@Service
public class CallbackQueueService {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_TASK_TIMEOUT_SECONDS = 60;

    @Value("${callback.executor.idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Value("${callback.executor.cleanup-interval-minutes:5}")
    private long cleanupIntervalMinutes;

    // 문서별 싱글 스레드 executor 관리
    // 동일 문서의 callback은 순차 처리, 다른 문서는 병렬 처리
    private final Map<String, ExecutorService> documentQueues = new ConcurrentHashMap<>();

    // Executor 마지막 접근 시간 추적 (idle 정리용)
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * Callback 작업을 문서별 큐에 제출하고 완료까지 대기합니다.
     *
     * <p><b>동시성 제어 전략:</b></p>
     * <ul>
     *   <li>각 문서(fileKey)마다 독립적인 싱글 스레드 executor 관리</li>
     *   <li>동일 문서의 callback: 순차 처리 (Race condition 방지)</li>
     *   <li>다른 문서의 callback: 병렬 처리 (성능 향상)</li>
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

        // 문서별 executor 생성 (없으면 생성, 있으면 기존 사용)
        ExecutorService executor = getOrCreateExecutor(fileKey);

        // 마지막 접근 시간 업데이트 (idle cleanup용)
        lastAccessTime.put(fileKey, System.currentTimeMillis());

        Future<T> future = executor.submit(task);

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
     * 문서별 executor를 생성하거나 기존 것을 반환합니다.
     * 첫 요청 시에만 executor 생성, 이후 재사용
     *
     * @param fileKey 문서 식별자
     * @return 문서별 싱글 스레드 executor
     */
    private ExecutorService getOrCreateExecutor(String fileKey) {
        return documentQueues.computeIfAbsent(fileKey, key -> {
            log.info("Creating single-thread executor for fileKey: {}", key);
            return Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "callback-" + key);
                thread.setDaemon(false);
                return thread;
            });
        });
    }

    /**
     * 서비스 종료 시 모든 executor를 graceful하게 shutdown합니다.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down CallbackQueueService with {} document queues...", documentQueues.size());

        // 모든 executor에 shutdown 신호
        documentQueues.values().forEach(ExecutorService::shutdown);

        try {
            // 모든 executor가 종료될 때까지 대기
            if (!waitForAllExecutorsTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Some executors did not terminate gracefully, forcing shutdown");

                // 강제 shutdown
                documentQueues.values().forEach(ExecutorService::shutdownNow);

                // 다시 한 번 대기
                if (!waitForAllExecutorsTermination(10, TimeUnit.SECONDS)) {
                    log.error("Some executors did not terminate after forced shutdown");
                }
            }

            documentQueues.clear();
            lastAccessTime.clear();
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing immediate shutdown");
            documentQueues.values().forEach(ExecutorService::shutdownNow);
            documentQueues.clear();
            lastAccessTime.clear();
            Thread.currentThread().interrupt();
        }

        log.info("CallbackQueueService shutdown complete");
    }

    /**
     * 30분 이상 idle한 executor를 정리하는 scheduled job (5분마다 실행).
     */
    @Scheduled(fixedDelayString = "${callback.executor.cleanup-interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void cleanupIdleExecutors() {
        long now = System.currentTimeMillis();
        long idleThresholdMs = TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);

        documentQueues.entrySet().removeIf(entry -> {
            String fileKey = entry.getKey();
            Long lastAccess = lastAccessTime.get(fileKey);

            if (lastAccess != null && (now - lastAccess) > idleThresholdMs) {
                ExecutorService executor = entry.getValue();
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                lastAccessTime.remove(fileKey);
                log.info("Cleaned up idle executor for fileKey: {}", fileKey);
                return true;
            }
            return false;
        });
    }

    /**
     * 모든 executor가 종료될 때까지 대기합니다.
     *
     * @param timeout 대기 시간
     * @param unit    시간 단위
     * @return 모든 executor가 종료되면 true
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    private boolean waitForAllExecutorsTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);

        for (ExecutorService executor : documentQueues.values()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            if (!executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 생성된 executor 개수 조회 (모니터링/테스트용).
     *
     * @return 현재 관리 중인 executor 개수
     */
    public int getQueueCount() {
        return documentQueues.size();
    }

    /**
     * 모든 큐가 종료되었는지 확인합니다 (테스트용).
     *
     * @return 모두 종료되면 true
     */
    public boolean allQueuesShutdown() {
        return documentQueues.values().stream().allMatch(ExecutorService::isShutdown);
    }

    /**
     * 큐가 비어있는지 확인합니다 (테스트용).
     *
     * @return 큐가 비어있으면 true
     */
    public boolean isEmpty() {
        return documentQueues.isEmpty();
    }
}
