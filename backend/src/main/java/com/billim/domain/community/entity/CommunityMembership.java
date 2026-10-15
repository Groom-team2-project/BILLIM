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
 * <p>활성 소속은 회원당 하나다. 인증 재시도는 같은 행을 갱신한다.
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

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "verification_expires_at", nullable = false)
    private Instant verificationExpiresAt;

    @Column(name = "left_at")
    private Instant leftAt;

    /** 최초 인증 */
    public static CommunityMembership verified(long memberId, long communityId, Instant now) {
        CommunityMembership membership = new CommunityMembership();
        membership.memberId = memberId;
        membership.communityId = communityId;
        membership.activate(now);
        return membership;
    }

    /** 재인증. 같은 행의 인증 시각과 만료를 갱신하고 이탈 기록을 지운다 */
    public void reverify(Instant now) {
        activate(now);
    }

    private void activate(Instant now) {
        this.status = MembershipStatus.ACTIVE;
        this.verifiedAt = now;
        this.verificationExpiresAt = now.plus(VERIFICATION_TTL);
        this.leftAt = null;
    }

    /** 다른 동네를 인증해 이 소속에서 이탈한다 */
    public void leave(Instant now) {
        if (status == MembershipStatus.LEFT) {
            return;
        }
        this.status = MembershipStatus.LEFT;
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
