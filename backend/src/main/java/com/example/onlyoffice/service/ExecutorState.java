package com.example.onlyoffice.service;

import java.util.concurrent.ExecutorService;

/**
 * Executor 생명 주기 상태를 나타내는 Sealed 계층 구조.
 *
 * <p><b>상태 머신:</b></p>
 * <ul>
 *   <li>{@code Active}: Executor가 활발히 작업 처리 중</li>
 *   <li>{@code Idle}: Executor 유휴 상태이지만 재활성화 가능</li>
 *   <li>{@code ShuttingDown}: Executor shutdown 진행 중, 새로운 작업 거부</li>
 * </ul>
 *
 * <p><b>상태 전환:</b></p>
 * <ul>
 *   <li>Active → Idle: 비활성 시간 threshold 초과 후</li>
 *   <li>Idle → Active: 새로운 작업 도착 시 (재활성화)</li>
 *   <li>Idle → ShuttingDown: Cleanup 단계 중</li>
 *   <li>Active → (ShuttingDown으로 직접 전환 불가)</li>
 * </ul>
 *
 * <p><b>설계 이점:</b></p>
 * <ul>
 *   <li>Sealed interface는 switch 표현식에서 컴파일 타임 완전성 보장</li>
 *   <li>Record 구현은 불변이며 zero-copy</li>
 *   <li>Atomic compare-and-swap(CAS)을 통한 스레드 안전 상태 전환</li>
 *   <li>Null 상태 없음, enum+데이터 분리 복잡성 제거</li>
 * </ul>
 *
 * @since Java 21 (sealed interfaces, records)
 */
sealed interface ExecutorState permits ExecutorState.Active, ExecutorState.Idle, ExecutorState.ShuttingDown {

    /**
     * 마지막 접근 시간(submit 또는 재활성화).
     *
     * @return epoch 이후 경과 시간(밀리초)
     */
    long lastAccessTimeMs();

    /**
     * 기본 ExecutorService 인스턴스.
     *
     * @return executor
     */
    ExecutorService executor();

    /**
     * Executor가 활발히 작업을 처리 중.
     * 새로운 작업을 성공적으로 제출할 수 있음.
     *
     * @param lastAccessTimeMs 마지막 활동 시간
     * @param executor         기본 executor service
     */
    record Active(long lastAccessTimeMs, ExecutorService executor) implements ExecutorState {
    }

    /**
     * Executor가 설정된 시간 이상 유휴 상태.
     * 작업을 제출할 수 있음(Active로 재활성화 트리거).
     *
     * @param lastAccessTimeMs 마지막 활동 시간
     * @param executor         기본 executor service
     */
    record Idle(long lastAccessTimeMs, ExecutorService executor) implements ExecutorState {
    }

    /**
     * Executor가 shutdown 중.
     * 새로운 작업 제출 불가, 새 executor 생성 트리거.
     * 추적을 위해 이전 상태와 동일한 timestamp 사용.
     *
     * @param lastAccessTimeMs shutdown으로 표시된 시간
     * @param executor         기본 executor service
     */
    record ShuttingDown(long lastAccessTimeMs, ExecutorService executor) implements ExecutorState {
    }
}
