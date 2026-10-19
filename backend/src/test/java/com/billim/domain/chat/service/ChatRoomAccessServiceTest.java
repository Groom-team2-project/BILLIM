package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.AuthSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRoomAccessServiceTest {

    private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
    private final AuthSessionRepository authSessionRepository = mock(AuthSessionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
    private final ChatRoomAccessService accessService =
        new ChatRoomAccessService(roomRepository, authSessionRepository, clock);

    @Test
    @DisplayName("참여자의 마지막 저장 순번을 조회한다")
    void participantCanReadLastSequence() {
        ChatRoom room = mock(ChatRoom.class);
        when(roomRepository.findById(901L)).thenReturn(Optional.of(room));
        when(room.isParticipant(2L)).thenReturn(true);
        when(room.getNextSequence()).thenReturn(12L);

        assertEquals(Optional.of(11L), accessService.findLastSequenceForParticipant(901L, 2L));
    }

    @Test
    @DisplayName("비참여자에게는 마지막 순번을 제공하지 않는다")
    void nonParticipantCannotReadLastSequence() {
        ChatRoom room = mock(ChatRoom.class);
        when(roomRepository.findById(901L)).thenReturn(Optional.of(room));

        assertEquals(Optional.empty(), accessService.findLastSequenceForParticipant(901L, 2L));
    }

    @Test
    @DisplayName("유효한 로그인 세션의 방 참여자는 메시지를 받을 수 있다")
    void validSessionParticipantCanReceive() {
        AuthSession authSession = loggedInSession(2L, true);
        ChatRoom room = mock(ChatRoom.class);
        when(authSessionRepository.findById(55L)).thenReturn(Optional.of(authSession));
        when(roomRepository.findById(901L)).thenReturn(Optional.of(room));
        when(room.isParticipant(2L)).thenReturn(true);

        assertTrue(accessService.canReceive(901L, 2L, 55L));
    }

    @Test
    @DisplayName("로그아웃되거나 만료된 세션은 메시지를 받을 수 없다")
    void revokedSessionCannotReceive() {
        AuthSession authSession = loggedInSession(2L, false);
        when(authSessionRepository.findById(55L))
            .thenReturn(Optional.of(authSession));

        assertFalse(accessService.canReceive(901L, 2L, 55L));
        verify(roomRepository, never()).findById(any());
    }

    @Test
    @DisplayName("탈퇴한 회원의 세션은 메시지를 받을 수 없다")
    void withdrawnMemberCannotReceive() {
        AuthSession authSession = loggedInSession(2L, true);
        when(authSession.getMember().isActive()).thenReturn(false);
        when(authSessionRepository.findById(55L)).thenReturn(Optional.of(authSession));

        assertFalse(accessService.canReceive(901L, 2L, 55L));
        verify(roomRepository, never()).findById(any());
    }

    @Test
    @DisplayName("세션 ID가 없으면 DB 조회 없이 메시지를 거부한다")
    void missingSessionIdCannotReceive() {
        assertFalse(accessService.canReceive(901L, 2L, null));

        verify(authSessionRepository, never()).findById(any());
    }

    private AuthSession loggedInSession(Long memberId, boolean usable) {
        AuthSession authSession = mock(AuthSession.class);
        Member member = mock(Member.class);
        when(authSession.isUsableAt(Instant.now(clock))).thenReturn(usable);
        when(authSession.isLoggedIn()).thenReturn(true);
        when(authSession.getMember()).thenReturn(member);
        when(member.isActive()).thenReturn(true);
        when(member.getId()).thenReturn(memberId);
        return authSession;
    }
}
