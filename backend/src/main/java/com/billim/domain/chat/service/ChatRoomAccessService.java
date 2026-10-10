package com.billim.domain.chat.service;

import com.billim.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomAccessService {

    private final ChatRoomRepository roomRepository;

    @Transactional(readOnly = true)
    public boolean isParticipant(Long roomId, Long memberId) {
        if (roomId == null || memberId == null) {
            return false;
        }

        return roomRepository.findById(roomId)
            .map(room -> room.isParticipant(memberId))
            .orElse(false);
    }
}
