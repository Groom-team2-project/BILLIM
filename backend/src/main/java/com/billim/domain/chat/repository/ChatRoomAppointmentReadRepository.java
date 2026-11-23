package com.billim.domain.chat.repository;

import com.billim.domain.rental.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

/** 채팅 시스템 메시지에서 약속 사건의 연결 거래를 읽는다. */
public interface ChatRoomAppointmentReadRepository extends JpaRepository<AppointmentSlot, Long> {
}
