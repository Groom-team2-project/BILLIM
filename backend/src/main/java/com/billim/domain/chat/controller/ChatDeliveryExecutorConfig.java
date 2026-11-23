package com.billim.domain.chat.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ChatDeliveryExecutorConfig {

    @Bean("chatDeliveryExecutor")
    ThreadPoolTaskExecutor chatDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("chat-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
