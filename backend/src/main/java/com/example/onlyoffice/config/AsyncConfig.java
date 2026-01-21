package com.example.onlyoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * StreamingResponseBody를 위한 비동기 설정.
 * <p>
 * StreamingResponseBody는 비동기로 실행되므로 전용 스레드 풀을 구성하여
 * 파일 다운로드 요청을 효율적으로 처리합니다.
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Value("${streaming.async-timeout-ms:300000}")
    private long asyncTimeoutMs;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(streamingTaskExecutor());
        configurer.setDefaultTimeout(asyncTimeoutMs);
    }

    /**
     * 스트리밍 전용 스레드 풀 설정.
     * <p>
     * - corePoolSize: 기본 스레드 수 (동시 다운로드 기본 처리량)
     * - maxPoolSize: 최대 스레드 수 (부하 시 확장)
     * - queueCapacity: 대기열 크기 (스레드가 모두 사용 중일 때 대기)
     * - threadNamePrefix: 로그에서 스레드 식별용
     */
    @Bean
    public AsyncTaskExecutor streamingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("streaming-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
