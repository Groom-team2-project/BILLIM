package com.billim.domain.community.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * community_memberships — 회원별 소속·인증 결과.
 *
 * <p>저장하는 것은 반경 확인 결과·동네·시점뿐이다. 개인 좌표·정확도·이동 기록은 남기지 않는다.
 * (정책 및 상태 5 위치 인증, ERD 4.2)
 *
 * <p><b>저장 동네는 회원당 둘까지다.</b> ACTIVE 소속마다 슬롯 1~2를 가지고
 * {@code (member_id, active_slot)} 유니크가 상한을 보장한다 (V23). 어느 쪽이 <b>현재 동네</b>인지는
 * 이 테이블이 아니라 {@code members.active_community_id}가 가진다.
 * (도메인 가이드 결정 '저장 동네와 현재 동네')
 *
 * <p>인증 재시도는 같은 행을 갱신한다. 이탈했던 동네를 다시 인증하면 그 행이 슬롯을 다시 받는다.
 */
@Entity
@Table(
        name = "community_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_memberships_member_community",
                columnNames = {"member_id", "community_id"}),
        indexes = @Index(name = "idx_community_memberships_community_status_expires",
                columnList = "community_id,status,verification_expires_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityMembership extends BaseTimeEntity {

    /** 인증 유효기간. 만료되면 재인증이 필요하다 (정책 및 상태 5 — 30일 제안) */
    public static final Duration VERIFICATION_TTL = Duration.ofDays(30);

    /** 저장 동네 상한. 슬롯 번호의 상한이기도 하다 (V23) */
    public static final int MAX_ACTIVE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MembershipStatus status;

    /**
     * 저장 동네 슬롯 1~2. ACTIVE가 아니면 null이다.
     * 값 자체에 의미는 없고 회원당 개수를 DB가 묶는 수단이다 (V23 유니크).
     */
    @Column(name = "active_slot")
    private Integer activeSlot;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "verification_expires_at", nullable = false)
    private Instant verificationExpiresAt;

    @Column(name = "left_at")
    private Instant leftAt;

    /** 최초 인증. 빈 슬롯을 받아 저장 동네가 된다 */
    public static CommunityMembership verified(long memberId, long communityId, int slot, Instant now) {
        CommunityMembership membership = new CommunityMembership();
        membership.memberId = memberId;
        membership.communityId = communityId;
        membership.activate(slot, now);
        return membership;
    }

    /** 재인증. 이미 저장 동네인 행의 인증 시각과 만료만 갱신한다. 슬롯은 그대로 둔다 */
    public void reverify(Instant now) {
        activate(activeSlot, now);
    }

    /** 이탈했던 동네를 다시 인증한다. 슬롯을 새로 받는다 */
    public void rejoin(int slot, Instant now) {
        activate(slot, now);
    }

    private void activate(Integer slot, Instant now) {
        if (slot == null || slot < 1 || slot > MAX_ACTIVE) {
            throw new IllegalArgumentException("저장 동네 슬롯은 1~" + MAX_ACTIVE + " 입니다: " + slot);
        }
        this.status = MembershipStatus.ACTIVE;
        this.activeSlot = slot;
        this.verifiedAt = now;
        this.verificationExpiresAt = now.plus(VERIFICATION_TTL);
        this.leftAt = null;
    }

    /** 저장 동네에서 빠진다. 슬롯을 비워 다른 동네가 쓸 수 있게 한다 */
    public void leave(Instant now) {
        if (status == MembershipStatus.LEFT) {
            return;
        }
        this.status = MembershipStatus.LEFT;
        this.activeSlot = null;
        this.leftAt = now;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    /**
     * 유효 소속 = 활성이고 인증이 만료되지 않음.
     * 물건 등록·대여 요청 같은 명령의 소속 판정 기준이다.
     */
    public boolean isValidAt(Instant now) {
        return isActive() && now.isBefore(verificationExpiresAt);
    }
}
