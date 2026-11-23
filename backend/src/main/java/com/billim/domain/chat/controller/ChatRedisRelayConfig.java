package com.billim.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(prefix = "billim.chat.redis", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ChatRedisRelayConfig {

    private final ChatRedisRelay relay;

    @Bean
    RedisMessageListenerContainer chatRedisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        @Qualifier("chatRedisMessageExecutor") ThreadPoolTaskExecutor messageExecutor,
        @Qualifier("chatRedisSubscriptionExecutor") ThreadPoolTaskExecutor subscriptionExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(messageExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.addMessageListener(relay, new ChannelTopic(ChatRedisRelay.CHANNEL));
        return container;
    }

    @Bean("chatRedisMessageExecutor")
    ThreadPoolTaskExecutor chatRedisMessageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("chat-redis-message-");
        return executor;
    }

    @Bean("chatRedisSubscriptionExecutor")
    ThreadPoolTaskExecutor chatRedisSubscriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("chat-redis-subscription-");
        return executor;
    }
}
