package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatSocketTicketResponse;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.session.SessionTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class ChatSocketTicketServiceTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private final ChatRoomAccessService access = mock(ChatRoomAccessService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC);
    private ChatSocketTicketService service;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() { if (factory != null) factory.destroy(); }

    @BeforeEach
    void setUp() {
        try (var connection = factory.getConnection()) { connection.serverCommands().flushDb(); }
        service = new ChatSocketTicketService(redis, access, clock, true, "https://billim.netlify.app");
        when(access.findLastSequenceForParticipant(1L, 2L)).thenReturn(Optional.of(5L));
        when(access.canReceive(1L, 2L, 3L)).thenReturn(true);
    }

    @Test
    @DisplayName("연결권은 해시로만 저장하고 30초 유효기간과 일회성 사용을 적용한다")
    void ticketIsHashedExpiringAndSingleUse() {
        ChatSocketTicketResponse issued = service.issue(1L, 2L, 3L);
        String key = "billim:chat:socket-ticket:" + SessionTokens.hash(issued.ticket());
        assertEquals(Instant.now(clock).plusSeconds(30), issued.expiresAt());
        assertEquals("1:2:3", redis.opsForValue().get(key));
        assertTrue(redis.getExpire(key) > 0 && redis.getExpire(key) <= 30);
        assertFalse(issued.toString().contains(issued.ticket()));
        assertEquals(new ChatSocketTicketService.Identity(1L, 2L, 3L), service.consume(issued.ticket(), 1L));
        assertCode(ErrorCode.UNAUTHENTICATED, () -> service.consume(issued.ticket(), 1L));
    }

    @Test
    @DisplayName("다른 인스턴스에서 동시에 소비해도 한 연결만 성공한다")
    void concurrentConsumptionSucceedsOnlyOnce() throws Exception {
        String ticket = service.issue(1L, 2L, 3L).ticket();
        ChatSocketTicketService other = new ChatSocketTicketService(redis, access, clock, true, "https://billim.netlify.app");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> consumeAfter(start, service, ticket));
            var second = executor.submit(() -> consumeAfter(start, other, ticket));
            start.countDown();
            assertEquals(1, first.get() + second.get());
        }
    }

    @Test
    @DisplayName("만료·방 변조·로그아웃 후 연결권 사용을 거부한다")
    void expiryRoomTamperingAndRevocationAreRejected() {
        String ticket = service.issue(1L, 2L, 3L).ticket();
        redis.expireAt("billim:chat:socket-ticket:" + SessionTokens.hash(ticket), Instant.EPOCH);
        assertCode(ErrorCode.UNAUTHENTICATED, () -> service.consume(ticket, 1L));
        redis.delete("billim:chat:socket-ticket:rate:3:1");
        String wrongRoom = service.issue(1L, 2L, 3L).ticket();
        assertCode(ErrorCode.UNAUTHENTICATED, () -> service.consume(wrongRoom, 9L));
        assertCode(ErrorCode.UNAUTHENTICATED, () -> service.consume(wrongRoom, 1L));
        redis.delete("billim:chat:socket-ticket:rate:3:1");
        String revoked = service.issue(1L, 2L, 3L).ticket();
        when(access.canReceive(1L, 2L, 3L)).thenReturn(false);
        assertCode(ErrorCode.UNAUTHENTICATED, () -> service.consume(revoked, 1L));
    }

    @Test
    @DisplayName("비참여자·연속 발급·비활성 상태를 거부한다")
    void invalidIssuanceIsRejected() {
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.issue(9L, 2L, 3L));
        service.issue(1L, 2L, 3L);
        assertCode(ErrorCode.RATE_LIMITED, () -> service.issue(1L, 2L, 3L));
        ChatSocketTicketService disabled = new ChatSocketTicketService(redis, access, clock, false, "");
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> disabled.issue(1L, 2L, 3L));
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> disabled.consume("anything", 1L));
    }

    @Test
    @DisplayName("와일드카드·평문 운영 출처·경로 포함 출처로 활성화하지 않는다")
    void unsafeOriginsCannotBeEnabled() {
        for (String origin : new String[]{"", "*", "http://billim.netlify.app", "https://billim.netlify.app/", "null"}) {
            assertThrows(IllegalArgumentException.class, () -> new ChatSocketTicketService(redis, access, clock, true, origin));
        }
        assertTrue(service.acceptsOrigin("https://billim.netlify.app"));
        assertFalse(service.acceptsOrigin("https://attacker.example"));
        assertFalse(service.acceptsOrigin(null));
    }

    private int consumeAfter(CountDownLatch start, ChatSocketTicketService target, String ticket) throws InterruptedException {
        start.await();
        try { target.consume(ticket, 1L); return 1; }
        catch (BusinessException exception) { assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode()); return 0; }
    }

    private void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode());
    }
}
