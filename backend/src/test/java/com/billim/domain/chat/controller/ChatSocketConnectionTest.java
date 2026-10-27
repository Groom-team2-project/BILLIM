package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.chat.service.ChatSocketTicketService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Testcontainers
@Import(ChatSocketConnectionTest.Database.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.docker.compose.enabled=false",
    "spring.security.oauth2.client.registration.kakao.client-id=test",
    "spring.security.oauth2.client.registration.kakao.client-secret=test",
    "billim.chat.redis.enabled=false",
    "billim.chat.websocket.ticket-enabled=true",
    "billim.chat.websocket.allowed-origins=https://billim.netlify.app",
    "management.server.port=0"
})
class ChatSocketConnectionTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection
        MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
    }

    @LocalServerPort int port;
    @Autowired ChatSocketTicketService tickets;
    @MockitoBean ChatRoomAccessService access;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stopClient() { client.shutdownNow(); }

    @BeforeEach
    void permissions() {
        when(access.findLastSequenceForParticipant(1L, 2L)).thenReturn(Optional.of(0L));
        when(access.canReceive(1L, 2L, 3L)).thenReturn(true);
    }

    @Test
    @DisplayName("실제 보안 필터를 통과한 쿠키 없는 소켓은 연결권으로 열리고 재사용은 401")
    void opensWithoutCookieAndRejectsReplay() {
        String ticket = tickets.issue(1L, 2L, 3L).ticket();
        WebSocket socket = connect("https://billim.netlify.app", "&ticket=" + ticket);
        assertFalse(socket.isOutputClosed());
        socket.abort();
        assertHandshakeStatus(401, "https://billim.netlify.app", "&ticket=" + ticket);
        assertHandshakeStatus(401, "https://billim.netlify.app", "");
    }

    @Test
    @DisplayName("다른 출처 연결은 403이며 정상 출처에서 연결권 재사용 가능")
    void wrongOriginDoesNotConsumeTicket() {
        when(access.findLastSequenceForParticipant(4L, 2L)).thenReturn(Optional.of(0L));
        when(access.canReceive(4L, 2L, 3L)).thenReturn(true);
        String ticket = tickets.issue(4L, 2L, 3L).ticket();
        assertHandshakeStatus(403, "https://attacker.example", "&ticket=" + ticket, 4L);
        WebSocket socket = connect("https://billim.netlify.app", "&ticket=" + ticket, 4L);
        socket.abort();
    }

    private WebSocket connect(String origin, String query) { return connect(origin, query, 1L); }
    private WebSocket connect(String origin, String query, Long roomId) {
        return client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat?roomId=" + roomId + query), new WebSocket.Listener() { })
                .join();
    }
    private void assertHandshakeStatus(int expected, String origin, String query) {
        assertHandshakeStatus(expected, origin, query, 1L);
    }
    private void assertHandshakeStatus(int expected, String origin, String query, Long roomId) {
        CompletionException exception = assertThrows(CompletionException.class, () -> connect(origin, query, roomId));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, exception.getCause());
        assertEquals(expected, handshake.getResponse().statusCode());
    }
}
