package com.juliashtal.devanalytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated thread pool for data-source collection jobs.
     * A small pool is intentional — collection is I/O-bound and we don't want
     * unlimited concurrency hammering the GitHub API or the database.
     */
    @Bean("collectTaskExecutor")
    public Executor collectTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // Queue depth: how many pending sync requests can wait before rejecting
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("collect-");
        executor.initialize();
        return executor;
    }
}
