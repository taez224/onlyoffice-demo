package com.example.onlyoffice.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic 상태 머신 전환을 통한 스레드 안전 executor 래퍼.
 *
 * <p><b>동시성 모델:</b></p>
 * <ul>
 *   <li>Lock-free: AtomicReference와 compare-and-swap(CAS)으로 상태 전환</li>
 *   <li>Atomic: 모든 상태 변경은 분할 불가능한 연산</li>
 *   <li>Non-blocking: CAS 실패 시 재시도 루프 (정상 부하 시 드문 상황)</li>
 * </ul>
 *
 * <p><b>상태 머신 보장:</b></p>
 * <ul>
 *   <li>SHUTTING_DOWN executor로는 작업 제출 불가 → 새 executor 생성 트리거</li>
 *   <li>ACTIVE executor는 shutdown 불가 → submit과의 race condition 방지</li>
 *   <li>IDLE executor 재활성화 가능 → 버스트 활동 시 executor 수명 연장</li>
 * </ul>
 *
 * <p><b>Race Condition 제거:</b></p>
 * <ul>
 *   <li>이전: Idle 확인 → [race window] → Shutdown (executor가 새 작업 받을 수 있음)</li>
 *   <li>이후: Atomic CAS (ACTIVE→IDLE→SHUTDOWN) - 단계 사이 윈도우 없음</li>
 * </ul>
 *
 * @see ExecutorState
 * @see CallbackQueueService
 */
@Slf4j
class ManagedExecutor {
    private final String fileKey;
    private final AtomicReference<ExecutorState> state;

    /**
     * 초기 ACTIVE 상태로 managed executor를 생성.
     *
     * @param fileKey  로깅용 문서 식별자
     * @param executor 기본 싱글 스레드 executor service
     */
    ManagedExecutor(String fileKey, ExecutorService executor) {
        this.fileKey = fileKey;
        this.state = new AtomicReference<>(
                new ExecutorState.Active(System.currentTimeMillis(), executor)
        );
    }

    /**
     * Executor가 shutdown 중이 아니면 작업을 원자적으로 제출.
     * Idle cleanup을 방지하기 위해 접근 시간 업데이트.
     *
     * <p><b>원자적 동작:</b></p>
     * <ul>
     *   <li>ACTIVE: timestamp 업데이트를 위해 CAS 수행, 작업 제출</li>
     *   <li>IDLE: 재활성화(IDLE→ACTIVE)를 위해 CAS 수행, 작업 제출</li>
     *   <li>SHUTTING_DOWN: null 반환 (새 executor 생성 신호)</li>
     * </ul>
     *
     * @param task 제출할 작업
     * @param <T>  작업 반환 타입
     * @return 성공적으로 제출되면 Future, executor가 shutdown 중이면 null
     * @throws RejectedExecutionException 기본 executor가 shutdown 상태이면
     * @throws IllegalStateException      상태 머신이 손상되면
     */
    <T> Future<T> trySubmit(Callable<T> task) {
        long now = System.currentTimeMillis();

        while (true) {
            ExecutorState current = state.get();

            switch (current) {
                case ExecutorState.Active(long lastAccess, var exec) -> {
                    // ACTIVE 상태 유지하면서 마지막 접근 시간 업데이트
                    ExecutorState updated = new ExecutorState.Active(now, exec);
                    if (state.compareAndSet(current, updated)) {
                        return exec.submit(task);
                    }
                    // CAS 실패 (동시 수정), 재시도
                    log.trace("ACTIVE→ACTIVE 전환 CAS 재시도 (fileKey: {})", fileKey);
                }

                case ExecutorState.Idle(long lastAccess, var exec) -> {
                    // Idle 상태에서 executor 재활성화
                    ExecutorState updated = new ExecutorState.Active(now, exec);
                    if (state.compareAndSet(current, updated)) {
                        log.debug("Idle executor 재활성화 (fileKey: {})", fileKey);
                        return exec.submit(task);
                    }
                    // CAS 실패 (동시 수정), 재시도
                    log.trace("IDLE→ACTIVE 전환 CAS 재시도 (fileKey: {})", fileKey);
                }

                case ExecutorState.ShuttingDown(long lastAccess, var executor) -> {
                    // Shutdown 중인 executor로는 작업 제출 불가
                    log.debug("Shutdown 중인 executor로는 작업 제출 불가 (fileKey: {})", fileKey);
                    return null;  // 호출자에게 신호: 새 executor 생성
                }
            }
        }
    }

    /**
     * Threshold 시간 이상 비활성 상태이면 executor를 원자적으로 IDLE 상태로 표시.
     * ACTIVE executor만 IDLE로 전환 가능.
     *
     * <p><b>원자적 동작:</b></p>
     * <ul>
     *   <li>ACTIVE & (now - lastAccess) > threshold: CAS ACTIVE→IDLE 수행</li>
     *   <li>ACTIVE & (now - lastAccess) ≤ threshold: 상태 변경 없음</li>
     *   <li>IDLE: 이미 idle, 상태 변경 불필요</li>
     *   <li>SHUTTING_DOWN: idle 표시 불가, 상태 변경 없음</li>
     * </ul>
     *
     * @param now             현재 시간(밀리초)
     * @param idleThresholdMs Threshold(밀리초)
     * @return IDLE로 전환되었거나 이미 IDLE이면 true
     */
    boolean tryMarkIdle(long now, long idleThresholdMs) {
        ExecutorState current = state.get();

        switch (current) {
            case ExecutorState.Active(long lastAccess, var exec) -> {
                if ((now - lastAccess) > idleThresholdMs) {
                    ExecutorState idle = new ExecutorState.Idle(lastAccess, exec);
                    boolean marked = state.compareAndSet(current, idle);
                    if (marked) {
                        log.debug("Executor IDLE 상태로 표시 (fileKey: {}, 비활성 시간: {} ms)",
                                fileKey, (now - lastAccess));
                    }
                    return marked;
                }
                // 아직 idle 아님, active 유지
                return false;
            }

            case ExecutorState.Idle(long lastAccess, var exec) -> {
                // 이미 idle
                return true;
            }

            case ExecutorState.ShuttingDown(long lastAccess, var exec) -> {
                // Shutdown 중인 executor는 idle 표시 불가
                return false;
            }
        }
    }

    /**
     * IDLE 상태이면 executor를 원자적으로 shutdown.
     * ACTIVE executor는 shutdown 불가능 (작업 제출과의 race 방지).
     *
     * <p><b>원자적 동작:</b></p>
     * <ul>
     *   <li>IDLE: CAS IDLE→SHUTTING_DOWN 수행, shutdown 신호</li>
     *   <li>ACTIVE: shutdown 불가, false 반환</li>
     *   <li>SHUTTING_DOWN: 이미 shutdown 중, true 반환</li>
     * </ul>
     *
     * @return shutdown 시작됨 또는 진행 중이면 true, 여전히 active이면 false
     */
    boolean tryShutdown() {
        while (true) {
            ExecutorState current = state.get();

            switch (current) {
                case ExecutorState.Idle(long lastAccess, var exec) -> {
                    // IDLE → SHUTTING_DOWN으로 전환하고 shutdown 신호
                    ExecutorState shutdown = new ExecutorState.ShuttingDown(lastAccess, exec);
                    if (state.compareAndSet(current, shutdown)) {
                        exec.shutdown();
                        log.debug("IDLE executor shutdown 시작 (fileKey: {})", fileKey);
                        return true;
                    }
                    // CAS 실패 (executor가 재활성화됨), 재시도
                    log.trace("IDLE→SHUTTING_DOWN 전환 CAS 재시도 (fileKey: {})", fileKey);
                }

                case ExecutorState.Active(long lastAccess, var exec) -> {
                    // ACTIVE executor는 shutdown 불가능
                    log.trace("ACTIVE executor는 shutdown 불가능 (fileKey: {})", fileKey);
                    return false;
                }

                case ExecutorState.ShuttingDown(long lastAccess, var exec) -> {
                    // 이미 shutdown 중
                    return true;
                }
            }
        }
    }

    /**
     * 상태와 관계없이 executor를 강제 shutdown.
     * {@link CallbackQueueService#shutdown()} 통해 애플리케이션 shutdown 시 사용.
     *
     * <p><b>설계:</b> Try-with-resources를 사용하지 않는 이유:
     * <ul>
     *   <li>ExecutorService의 생명주기가 상태 머신에 의해 관리됨</li>
     *   <li>여러 스레드가 동시에 접근하므로 명시적 상태 전환 필요</li>
     *   <li>상태에 따라 선택적으로 shutdown 제어 (IDLE만 shutdown 가능)</li>
     *   <li>Try-with-resources는 단일 스코프 내에서만 안전함</li>
     * </ul>
     * 따라서 명시적으로 shutdown()/shutdownNow()를 호출하고 awaitTermination()으로 대기합니다.</p>
     *
     * @param timeout 종료 대기 최대 시간
     * @param unit    시간 단위
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    void forceShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        ExecutorState current = state.get();
        ExecutorService exec = current.executor();

        if (!exec.isShutdown()) {
            exec.shutdown();
        }

        if (!exec.awaitTermination(timeout, unit)) {
            log.warn("Executor가 graceful하게 종료되지 않음 (fileKey: {}), 강제 shutdown 수행", fileKey);
            exec.shutdownNow();
            // 강제 shutdown 완료 대기
            if (!exec.awaitTermination(1, TimeUnit.SECONDS)) {
                log.error("강제 shutdown 후에도 executor 여전히 활성 (fileKey: {})", fileKey);
            }
        }
    }

    /**
     * 기본 executor가 shutdown되었는지 확인.
     *
     * @return executor가 shutdown 상태이면 true
     */
    boolean isShutdown() {
        return state.get().executor().isShutdown();
    }

    /**
     * 현재 상태 조회 (테스트 및 모니터링용).
     *
     * @return 현재 executor 상태
     */
    ExecutorState currentState() {
        return state.get();
    }
}
