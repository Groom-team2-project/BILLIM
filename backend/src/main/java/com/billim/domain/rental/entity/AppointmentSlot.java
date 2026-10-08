package com.billim.domain.rental.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 전달·반납 약속별 동시성 제어 기준
@Entity
@Table(name = "appointment_slots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointment_slot_rental_kind", columnNames = {"rental_id", "kind"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rental_id", nullable = false, updatable = false)
    private Long rentalId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10, updatable = false)
    private AppointmentKind kind;

    // 동일 슬롯의 CONFIRMED 제안 참조, 슬롯 최초 생성 시 NULL
    @Column(name = "confirmed_proposal_id")
    private Long confirmedProposalId;

    // 제안만 변경하는 경우에도 서비스에서 슬롯 버전 증가 처리 필요
    @Version
    @Column(nullable = false)
    private long version;
}
