package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ROOM_ID_ATTRIBUTE = "roomId";
    public static final String MEMBER_ID_ATTRIBUTE = "memberId";

    private final ChatRoomAccessService accessService;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletServerHttpRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        if(!(servletServerHttpRequest.getServletRequest().getUserPrincipal()
        instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof LoginMember member)
        || !authentication.isAuthenticated()
        || member.memberId() == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String[] roomIds = servletServerHttpRequest.getServletRequest()
            .getParameterValues("roomId");

        if (roomIds == null || roomIds.length != 1) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        Long roomId;
        try {
            roomId = Long.valueOf(roomIds[0]);
        } catch (NumberFormatException exception) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        if (roomId <= 0) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        if (!accessService.isParticipant(roomId, member.memberId())) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        attributes.put(ROOM_ID_ATTRIBUTE, roomId);
        attributes.put(MEMBER_ID_ATTRIBUTE, member.memberId());
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        @Nullable Exception exception) {

    }
}
