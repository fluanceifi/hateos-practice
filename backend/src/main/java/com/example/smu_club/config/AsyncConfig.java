package com.example.smu_club.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 기본 대기 스레드 수
        executor.setMaxPoolSize(10);    // 최대 스레드 수
        executor.setQueueCapacity(100); // 대기열 크기
        executor.setThreadNamePrefix("MailSender-");
        executor.initialize();
        return executor;
    }
}