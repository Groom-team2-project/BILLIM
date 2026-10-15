package com.billim.domain.community.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 소속·인증 상태 규칙 (ERD 4.2, 정책 및 상태 5 위치 인증) */
class CommunityMembershipTest {

    private static final Instant NOW = Instant.parse("2026-10-13T01:00:00Z");
    private static final long MEMBER = 7L;
    private static final long COMMUNITY = 100L;

    private CommunityMembership verified() {
        return CommunityMembership.verified(MEMBER, COMMUNITY, NOW);
    }

    @Test
    @DisplayName("인증하면 활성 소속이 되고 30일 뒤 만료된다")
    void verificationActivatesForThirtyDays() {
        CommunityMembership membership = verified();

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getVerifiedAt()).isEqualTo(NOW);
        assertThat(membership.getVerificationExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(membership.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("유효 소속은 활성이고 만료 전인 경우만이다")
    void validOnlyWhileActiveAndNotExpired() {
        CommunityMembership membership = verified();

        assertThat(membership.isValidAt(NOW)).isTrue();
        assertThat(membership.isValidAt(NOW.plus(Duration.ofDays(30)).minusSeconds(1))).isTrue();
        // 만료 시각은 포함하지 않는다
        assertThat(membership.isValidAt(NOW.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    @DisplayName("만료된 소속은 활성이어도 유효하지 않다")
    void expiredMembershipIsActiveButNotValid() {
        CommunityMembership membership = verified();
        Instant afterExpiry = NOW.plus(Duration.ofDays(31));

        assertThat(membership.isActive()).isTrue();
        assertThat(membership.isValidAt(afterExpiry)).isFalse();
    }

    @Test
    @DisplayName("이탈하면 LEFT가 되고 이탈 시각이 남아 더 이상 유효하지 않다")
    void leaveMarksLeftAt() {
        CommunityMembership membership = verified();
        Instant leftAt = NOW.plusSeconds(60);

        membership.leave(leftAt);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.LEFT);
        assertThat(membership.getLeftAt()).isEqualTo(leftAt);
        assertThat(membership.isValidAt(leftAt)).isFalse();
    }

    @Test
    @DisplayName("이미 이탈한 소속을 다시 이탈시켜도 최초 이탈 시각을 유지한다")
    void repeatedLeaveKeepsFirstLeftAt() {
        CommunityMembership membership = verified();
        Instant firstLeave = NOW.plusSeconds(60);
        membership.leave(firstLeave);

        membership.leave(NOW.plusSeconds(600));

        assertThat(membership.getLeftAt()).isEqualTo(firstLeave);
    }

    @Test
    @DisplayName("재인증하면 같은 소속이 다시 활성이 되고 만료가 연장되며 이탈 기록이 지워진다")
    void reverifyReactivatesAndExtends() {
        CommunityMembership membership = verified();
        membership.leave(NOW.plusSeconds(60));
        Instant reverifiedAt = NOW.plus(Duration.ofDays(40));

        membership.reverify(reverifiedAt);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getVerifiedAt()).isEqualTo(reverifiedAt);
        assertThat(membership.getVerificationExpiresAt())
                .isEqualTo(reverifiedAt.plus(Duration.ofDays(30)));
        assertThat(membership.getLeftAt()).isNull();
        assertThat(membership.isValidAt(reverifiedAt)).isTrue();
    }
}
