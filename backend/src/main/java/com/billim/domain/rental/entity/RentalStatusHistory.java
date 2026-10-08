package com.billim.domain.rental.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 상태 전이 감사 이력, 추가 전용
@Entity
@Table(name = "rental_status_histories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rental_history_version", columnNames = {"rental_id", "rental_version"})
})
@org.hibernate.annotations.Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalStatusHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rental_id", nullable = false, updatable = false)
    private Long rentalId;

    // 시스템 만료 시 NULL
    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    // 최초 생성 시 NULL
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 15, updatable = false)
    private RentalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 15, updatable = false)
    private RentalStatus toStatus;

    @Column(length = 500, updatable = false)
    private String reason;

    // 거래 변경 후 버전, 낙관적 잠금용 @Version과 별개
    @Column(name = "rental_version", nullable = false, updatable = false)
    private long rentalVersion;
}
