package com.tranquility.SpeakSmart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration {

    @Bean(name = "audioProcessingTaskExecutor")
    public Executor audioProcessingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - minimum number of threads
        executor.setCorePoolSize(2);

        // Maximum pool size - maximum number of threads
        executor.setMaxPoolSize(5);

        // Queue capacity - number of tasks that can be queued
        executor.setQueueCapacity(10);

        // Thread name prefix for easier debugging
        executor.setThreadNamePrefix("AudioProcessing-");

        // Rejection policy when queue is full
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // Allow core threads to timeout
        executor.setAllowCoreThreadTimeOut(true);

        // Keep alive time for idle threads
        executor.setKeepAliveSeconds(60);

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    @Bean(name = "generalTaskExecutor")
    public Executor generalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("GeneralTask-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
