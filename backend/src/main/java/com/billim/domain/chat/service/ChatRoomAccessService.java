package com.billim.domain.chat.service;

import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomAccessService {

    private final ChatRoomRepository roomRepository;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isParticipant(Long roomId, Long memberId) {
        if (roomId == null || memberId == null) {
            return false;
        }

        return roomRepository.findById(roomId)
            .map(room -> room.isParticipant(memberId))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLastSequenceForParticipant(Long roomId, Long memberId) {
        if (roomId == null || memberId == null) {
            return Optional.empty();
        }

        return roomRepository.findById(roomId)
            .filter(room -> room.isParticipant(memberId))
            .map(room -> room.getNextSequence() - 1);
    }

    @Transactional(readOnly = true)
    public boolean canReceive(
        Long roomId,
        Long memberId,
        Long authSessionId
    ) {
        if (roomId == null || memberId == null || authSessionId == null) {
            return false;
        }

        boolean sessionValid = authSessionRepository.findById(authSessionId)
            .filter(session -> session.isUsableAt(Instant.now(clock)))
            .filter(AuthSession::isLoggedIn)
            .filter(session -> session.getMember().isActive())
            .filter(session -> session.getMember().getId().equals(memberId))
            .isPresent();

        return sessionValid && isParticipant(roomId, memberId);
    }
}
