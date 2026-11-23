package com.billim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(name = "chatRedisMessageListenerContainer", types = RedisMessageListenerContainer.class)
@MockitoBean(name = "redisMessageListenerContainer", types = RedisMessageListenerContainer.class)
public @interface MockChatRedisListener {
}
