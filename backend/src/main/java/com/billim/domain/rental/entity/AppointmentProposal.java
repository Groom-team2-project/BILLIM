package com.billim.domain.rental.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// 약속 제안·변경 이력
@Entity
@Table(name = "appointment_proposals", indexes = {
        @Index(name = "idx_appointment_proposal_slot_status", columnList = "slot_id,status,id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentProposal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_id", nullable = false, updatable = false)
    private Long slotId;

    @Column(name = "proposer_id", nullable = false, updatable = false)
    private Long proposerId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private Long placeId;

    // KST로 선택한 약속 날짜·시각을 UTC로 저장
    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    @Column(name = "place_name_snapshot", nullable = false, length = 100, updatable = false)
    private String placeNameSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private AppointmentProposalStatus status = AppointmentProposalStatus.PROPOSED;

    // 제안자와 다른 회원, 수락 전 NULL
    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
