package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatSocketTicketResponse;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.session.SessionTokens;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatSocketTicketService {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String PREFIX = "billim:chat:socket-ticket:";

    private final StringRedisTemplate redis;
    private final ChatRoomAccessService accessService;
    private final Clock clock;
    private final boolean enabled;
    private final List<String> allowedOrigins;

    public ChatSocketTicketService(
        StringRedisTemplate redis, ChatRoomAccessService accessService, Clock clock,
        @Value("${billim.chat.websocket.ticket-enabled:false}") boolean enabled,
        @Value("${billim.chat.websocket.allowed-origins:}") String origins
    ) {
        this.redis = redis;
        this.accessService = accessService;
        this.clock = clock;
        this.enabled = enabled;
        this.allowedOrigins = Arrays.stream(origins.split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (enabled && (allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(origin -> !validOrigin(origin)))) {
            throw new IllegalArgumentException("채팅 연결권 활성화 시 정확한 HTTPS 프론트 출처 설정 필요");
        }
    }

    public String[] allowedOrigins() {
        return enabled ? allowedOrigins.toArray(String[]::new) : new String[0];
    }

    public boolean acceptsOrigin(String origin) {
        return enabled && origin != null && allowedOrigins.contains(origin);
    }

    public ChatSocketTicketResponse issue(Long roomId, Long memberId, Long authSessionId) {
        requireEnabled();
        if (accessService.findLastSequenceForParticipant(roomId, memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!accessService.canReceive(roomId, memberId, authSessionId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            // 동일 세션·방의 연속 발급 상한. 만료 전 Redis 키 증식 방지.
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                PREFIX + "rate:" + authSessionId + ":" + roomId, "1", Duration.ofSeconds(1)))) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            String ticket = SessionTokens.newToken();
            Instant expiresAt = Instant.now(clock).plus(TTL);
            redis.opsForValue().set(PREFIX + SessionTokens.hash(ticket),
                roomId + ":" + memberId + ":" + authSessionId, TTL);
            return new ChatSocketTicketResponse(ticket, expiresAt);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public Identity consume(String ticket, Long roomId) {
        requireEnabled();
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        String value;
        try {
            // GETDEL로 여러 인스턴스의 동시 연결에서도 한 번만 소비.
            value = redis.opsForValue().getAndDelete(PREFIX + SessionTokens.hash(ticket));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (value == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        Identity identity;
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            identity = new Identity(Long.valueOf(parts[0]), Long.valueOf(parts[1]), Long.valueOf(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!identity.roomId().equals(roomId)
            || !accessService.canReceive(roomId, identity.memberId(), identity.authSessionId())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return identity;
    }

    private void requireEnabled() {
        if (!enabled) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private BusinessException unavailable(DataAccessException cause) {
        BusinessException exception = new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        exception.initCause(cause);
        return exception;
    }

    private static boolean validOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            boolean local = uri.getHost() != null && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            return uri.getHost() != null && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && ("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record Identity(Long roomId, Long memberId, Long authSessionId) { }
}
