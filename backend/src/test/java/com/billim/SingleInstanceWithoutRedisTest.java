package com.billim;

import com.billim.domain.chat.controller.ChatDatabaseRelay;
import com.billim.domain.chat.controller.ChatMessageCreateWebSocketListener;
import com.billim.domain.chat.controller.ChatRedisRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "spring.profiles.active=prod",
    "DB_URL=jdbc:mysql://127.0.0.1:1/test",
    "DB_USERNAME=test",
    "DB_PASSWORD=test",
    "KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/kakao/callback",
    "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
    "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
    "spring.data.redis.url=redis://127.0.0.1:1",
    "billim.chat.redis.enabled=false"
})
class SingleInstanceWithoutRedisTest {

    @Autowired
    private ChatMessageCreateWebSocketListener localDelivery;

    @Autowired
    private ChatDatabaseRelay databaseFallback;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Redis가 없는 단일 서버에서도 로컬 전달과 DB 재확인 구성이 시작된다")
    void startsWithoutRedis() {
        assertThat(localDelivery).isNotNull();
        assertThat(databaseFallback).isNotNull();
        assertThat(context.getBeansOfType(ChatRedisRelay.class)).isEmpty();
    }
}
