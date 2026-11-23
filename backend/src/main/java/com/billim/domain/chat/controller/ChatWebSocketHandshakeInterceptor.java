package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.chat.service.ChatSocketTicketService;
import com.billim.global.exception.BusinessException;
import com.billim.domain.member.entity.AuthSession;
import com.billim.global.security.LoginMember;
import com.billim.global.security.session.CurrentAuthSession;
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
    public static final String AUTH_SESSION_ID_ATTRIBUTE = "authSessionId";
    public static final String REPLAY_AFTER_SEQUENCE_ATTRIBUTE = "replayAfterSequence";

    private final ChatRoomAccessService accessService;
    private final CurrentAuthSession currentAuthSession;
    private final ChatSocketTicketService ticketService;

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

        String[] tickets = servletServerHttpRequest.getServletRequest().getParameterValues("ticket");
        if (tickets != null) {
            String origin = request.getHeaders().getOrigin();
            if (tickets.length != 1 || !ticketService.acceptsOrigin(origin)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            String[] rooms = servletServerHttpRequest.getServletRequest().getParameterValues("roomId");
            Long ticketRoomId;
            try {
                if (rooms == null || rooms.length != 1) throw new IllegalArgumentException();
                ticketRoomId = Long.valueOf(rooms[0]);
                if (ticketRoomId <= 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
            try {
                ChatSocketTicketService.Identity identity = ticketService.consume(tickets[0], ticketRoomId);
                Long lastSequence = accessService.findLastSequenceForParticipant(ticketRoomId, identity.memberId())
                    .orElse(null);
                if (lastSequence == null) {
                    response.setStatusCode(HttpStatus.NOT_FOUND);
                    return false;
                }
                attributes.put(ROOM_ID_ATTRIBUTE, ticketRoomId);
                attributes.put(MEMBER_ID_ATTRIBUTE, identity.memberId());
                attributes.put(AUTH_SESSION_ID_ATTRIBUTE, identity.authSessionId());
                attributes.put(REPLAY_AFTER_SEQUENCE_ATTRIBUTE, lastSequence);
                return true;
            } catch (BusinessException exception) {
                response.setStatusCode(exception.getErrorCode().getStatus());
                return false;
            }
        }

        if(!(servletServerHttpRequest.getServletRequest().getUserPrincipal()
        instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof LoginMember member)
        || !authentication.isAuthenticated()
        || member.memberId() == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        AuthSession authSession = currentAuthSession
            .get(servletServerHttpRequest.getServletRequest())
            .orElse(null);

        if (authSession == null
            || !authSession.isLoggedIn()
            || !authSession.getMember().getId().equals(member.memberId())) {
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

        Long replayAfterSequence = accessService
            .findLastSequenceForParticipant(roomId, member.memberId())
            .orElse(null);
        if (replayAfterSequence == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        attributes.put(ROOM_ID_ATTRIBUTE, roomId);
        attributes.put(MEMBER_ID_ATTRIBUTE, member.memberId());
        attributes.put(AUTH_SESSION_ID_ATTRIBUTE, authSession.getId());
        attributes.put(REPLAY_AFTER_SEQUENCE_ATTRIBUTE, replayAfterSequence);
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
