package com.billim.domain.rental.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

// 대여 요청과 거래 원장
@Entity
@Table(name = "rentals", indexes = {
        @Index(name = "idx_rentals_item_status_dates", columnList = "item_id,status,start_date,end_date"),
        @Index(name = "idx_rentals_borrower_status_created", columnList = "borrower_id,status,created_at,id"),
        @Index(name = "idx_rentals_owner_status_created", columnList = "owner_id,status,created_at,id"),
        @Index(name = "idx_rentals_status_expires", columnList = "status,request_expires_at"),
        @Index(name = "idx_rentals_chat_room", columnList = "chat_room_id,id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rental extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 거래 생성 당시의 물건·소유자·요청자·소속
    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private Long borrowerId;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    // KST 기준 날짜, 시작일과 종료일 모두 포함
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private RentalStatus status = RentalStatus.REQUESTED;

    // 시작일 다음 날 KST 00:00의 UTC 시각
    @Column(name = "request_expires_at", nullable = false)
    private Instant requestExpiresAt;

    // 반납일 다음 날 KST 00:00의 UTC 시각
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "item_title_snapshot", nullable = false, length = 100, updatable = false)
    private String itemTitleSnapshot;

    @Column(name = "place_name_snapshot", nullable = false, length = 100)
    private String placeNameSnapshot;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    // 최종 거절·취소 사유
    @Column(length = 500)
    private String reason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "handed_over_at")
    private Instant handedOverAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    // 반납 확인 시점의 확정 RETURN 약속 스냅샷
    @Column(name = "return_promise_at")
    private Instant returnPromiseAt;

    // 약속 없음은 NULL, 약속이 있으면 확인 시각 <= 약속 시각
    @Column(name = "return_promise_kept")
    private Boolean returnPromiseKept;

    @Version
    @Column(nullable = false)
    private long version;
}
