package com.billim.domain.member.service;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.member.dto.PublicProfileResponse;
import com.billim.domain.rental.service.RentalPartyQuery;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.dto.MyProfileResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 표시 이름 수정의 권한·버전 규칙. Docker 필요 */
@DataJpaTest
@Import({MemberServiceDbTest.MySql.class, MemberService.class, JpaAuditingConfig.class})
class MemberServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    /** 제재 판정은 safety의 ReportFlowDbTest가 검증. 여기서는 프로필 반영만 확인 */
    @MockitoBean SanctionService sanctionService;
    /** 소속 판정은 CommunityFlowDbTest가 검증. 여기서는 프로필 반영만 */
    @MockitoBean CommunityMembershipQuery membershipQuery;
    @MockitoBean BlockQuery blockQuery;
    @MockitoBean RentalPartyQuery rentalPartyQuery;

    @Test
    @DisplayName("유효한 활성 소속이 있으면 프로필에 담고 온보딩을 요구하지 않는다")
    void includesActiveMembership() {
        Member member = memberRepository.save(Member.register("홍길동"));
        when(membershipQuery.findActiveMembership(member.getId()))
                .thenReturn(Optional.of(membership("7", "새솔마을 5단지")));

        MyProfileResponse profile = memberService.getMyProfile(member.getId());

        assertThat(profile.activeMembership()).isNotNull();
        assertThat(profile.activeMembership().communityId()).isEqualTo("7");
        assertThat(profile.onboardingRequired()).isFalse();
    }

    /** 미인증·만료 모두 같은 분기 */
    @Test
    @DisplayName("유효한 활성 소속이 없으면 소속을 생략하고 온보딩을 요구한다")
    void omitsMembershipWithoutValidOne() {
        Member member = memberRepository.save(Member.register("홍길동"));
        when(membershipQuery.findActiveMembership(member.getId())).thenReturn(Optional.empty());

        MyProfileResponse profile = memberService.getMyProfile(member.getId());

        assertThat(profile.activeMembership()).isNull();
        assertThat(profile.onboardingRequired()).isTrue();
    }

    // ---------- 이웃 공개 프로필 (A_007) ----------

    @Test
    @DisplayName("같은 동네 이웃의 공개 프로필을 조회한다")
    void returnsPublicProfileForSameCommunity() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        Member target = memberRepository.save(Member.register("김철수"));
        em.flush();
        givenSameCommunity(viewer, target, "역삼동");
        when(blockQuery.hasBlocked(viewer.getId(), target.getId())).thenReturn(true);

        PublicProfileResponse profile = memberService.getPublicProfile(viewer.getId(), target.getId());

        assertThat(profile.member().displayName()).isEqualTo("김철수");
        assertThat(profile.communityName()).isEqualTo("역삼동");
        assertThat(profile.isVerified()).isTrue();
        assertThat(profile.blockedByMe()).isTrue();
    }

    @Test
    @DisplayName("다른 동네이고 거래도 없으면 404")
    void hidesProfileOutsideCommunity() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        Member target = memberRepository.save(Member.register("김철수"));
        em.flush();
        when(membershipQuery.findActiveCommunityId(viewer.getId())).thenReturn(Optional.of(1L));
        when(membershipQuery.findActiveMembership(target.getId()))
                .thenReturn(Optional.of(membership("2", "논현동")));

        assertThatThrownBy(() -> memberService.getPublicProfile(viewer.getId(), target.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("거래 없이 차단 관계면 404")
    void hidesProfileWhenBlockedWithoutRental() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        Member target = memberRepository.save(Member.register("김철수"));
        em.flush();
        givenSameCommunity(viewer, target, "역삼동");
        when(blockQuery.isBlockedBetween(viewer.getId(), target.getId())).thenReturn(true);

        assertThatThrownBy(() -> memberService.getPublicProfile(viewer.getId(), target.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 유효 소속 없는 본인의 자기 프로필 조회 허용 (C_008과 같은 기준) */
    @Test
    @DisplayName("본인은 유효 소속이 없어도 조회된다")
    void allowsOwnProfileWithoutMembership() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        em.flush();
        when(membershipQuery.findActiveMembership(viewer.getId())).thenReturn(Optional.empty());

        PublicProfileResponse profile = memberService.getPublicProfile(viewer.getId(), viewer.getId());

        assertThat(profile.member().displayName()).isEqualTo("홍길동");
        assertThat(profile.isVerified()).isFalse();
        assertThat(profile.blockedByMe()).isFalse();
        verifyNoInteractions(blockQuery, rentalPartyQuery);
        verify(membershipQuery, never()).findActiveCommunityId(anyLong());
    }

    /** 인증 계층이 보지 않는 회원 상태를 서비스에서 차단 (C_008과 같은 기준) */
    @Test
    @DisplayName("탈퇴한 조회자는 401")
    void rejectsWithdrawnViewer() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        Member target = memberRepository.save(Member.register("김철수"));
        em.flush();
        em.createNativeQuery("update members set status = 'WITHDRAWN' where id = :id")
                .setParameter("id", viewer.getId())
                .executeUpdate();
        em.clear();

        assertThatThrownBy(() -> memberService.getPublicProfile(viewer.getId(), target.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    /** 거래 당사자는 동네·차단과 무관하게 열람 (명세 3.3) */
    @Test
    @DisplayName("기존 거래 당사자는 동네·차단과 무관하게 조회한다")
    void allowsProfileForPastRentalParty() {
        Member viewer = memberRepository.save(Member.register("홍길동"));
        Member target = memberRepository.save(Member.register("김철수"));
        em.flush();
        when(rentalPartyQuery.hasTraded(viewer.getId(), target.getId())).thenReturn(true);
        when(blockQuery.isBlockedBetween(viewer.getId(), target.getId())).thenReturn(true);
        when(membershipQuery.findActiveMembership(target.getId())).thenReturn(Optional.empty());

        PublicProfileResponse profile = memberService.getPublicProfile(viewer.getId(), target.getId());

        assertThat(profile.member().displayName()).isEqualTo("김철수");
        assertThat(profile.communityName()).isNull();
        assertThat(profile.isVerified()).isFalse();
        assertThat(profile.blockedByMe()).isFalse();   // 상대가 나를 차단한 건 내 버튼에 미반영
    }

    @Test
    @DisplayName("표시 이름을 바꾸면 version이 올라간다")
    void incrementsVersionOnChange() {
        Member member = memberRepository.save(Member.register("홍길동"));
        long before = member.getVersion();

        memberService.changeDisplayName(member.getId(), before, "김철수");
        em.flush();

        assertThat(member.getDisplayName()).isEqualTo("김철수");
        assertThat(member.getVersion()).isEqualTo(before + 1);
    }

    /** 응답의 version을 그대로 다시 보내 연속 수정이 가능해야 한다 */
    @Test
    @DisplayName("응답받은 version으로 곧바로 다시 수정할 수 있다")
    void allowsConsecutiveUpdates() {
        Member member = memberRepository.save(Member.register("홍길동"));

        MyProfileResponse first = memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수");
        em.flush();
        MyProfileResponse second = memberService.changeDisplayName(member.getId(), first.version(), "이영희");
        em.flush();

        assertThat(second.member().displayName()).isEqualTo("이영희");
    }

    @Test
    @DisplayName("오래된 version으로 수정하면 409와 현재 version을 돌려준다")
    void rejectsStaleVersion() {
        Member member = memberRepository.save(Member.register("홍길동"));
        memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수");
        em.flush();

        assertThatThrownBy(() -> memberService.changeDisplayName(member.getId(), 0L, "이영희"))
                .isInstanceOfSatisfying(VersionConflictException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(e.getCurrentVersion()).isEqualTo(member.getVersion());
                });
    }

    /** 탈퇴 기능 미구현이라 상태를 직접 만든다. 구현되면 도메인 메서드로 교체 */
    @Test
    @DisplayName("탈퇴 회원은 수정할 수 없다")
    void rejectsWithdrawnMember() {
        Member member = memberRepository.save(Member.register("홍길동"));
        em.flush();
        em.createNativeQuery("update members set status = 'WITHDRAWN' where id = :id")
                .setParameter("id", member.getId())
                .executeUpdate();
        em.clear();

        assertThatThrownBy(() -> memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("제재 중인 회원은 프로필의 isRestricted가 참이다")
    void reflectsRestrictedState() {
        Member restricted = memberRepository.save(Member.register("홍길동"));
        Member normal = memberRepository.save(Member.register("김철수"));
        em.flush();
        when(sanctionService.isRestricted(restricted.getId())).thenReturn(true);

        assertThat(memberService.getMyProfile(restricted.getId()).isRestricted()).isTrue();
        assertThat(memberService.getMyProfile(normal.getId()).isRestricted()).isFalse();
    }

    @Test
    @DisplayName("없는 회원은 404")
    void rejectsUnknownMember() {
        assertThatThrownBy(() -> memberService.getMyProfile(999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("여러 회원의 요약을 ID로 묶어 조회한다")
    void returnsSummariesByIds() {
        Member hong = memberRepository.save(Member.register("홍길동"));
        Member kim = memberRepository.save(Member.register("김철수"));
        em.flush();

        Map<Long, MemberSummaryResponse> summaries =
                memberService.getSummaries(List.of(hong.getId(), kim.getId(), hong.getId()));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(kim.getId()).displayName()).isEqualTo("김철수");
        assertThat(summaries.get(hong.getId()))
                .isEqualTo(new MemberSummaryResponse(
                        String.valueOf(hong.getId()), "홍길동", hong.getCreatedAt()));
    }

    @Test
    @DisplayName("빈 목록은 빈 Map")
    void returnsEmptyMapForEmptyIds() {
        assertThat(memberService.getSummaries(List.of())).isEmpty();
    }

    @Test
    @DisplayName("없는 ID는 결과에서 빠진다")
    void skipsUnknownIds() {
        Member hong = memberRepository.save(Member.register("홍길동"));
        em.flush();

        Map<Long, MemberSummaryResponse> summaries =
                memberService.getSummaries(List.of(hong.getId(), 999_999L));

        assertThat(summaries).containsOnlyKeys(hong.getId());
    }

    /** 단건 조회 반복으로 되돌아가면 실패하는 회귀 테스트 */
    @Test
    @DisplayName("회원이 여러 명이어도 쿼리는 한 번만 실행한다")
    void queriesOnceForManyMembers() {
        List<Long> ids = List.of(
                memberRepository.save(Member.register("홍길동")).getId(),
                memberRepository.save(Member.register("김철수")).getId(),
                memberRepository.save(Member.register("이영희")).getId());
        em.flush();
        em.clear();

        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        assertThat(memberService.getSummaries(ids)).hasSize(3);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private void givenSameCommunity(Member viewer, Member target, String communityName) {
        long communityId = 1L;
        when(membershipQuery.findActiveCommunityId(viewer.getId())).thenReturn(Optional.of(communityId));
        when(membershipQuery.findActiveMembership(target.getId()))
                .thenReturn(Optional.of(membership(String.valueOf(communityId), communityName)));
    }

    private static MembershipResponse membership(String communityId, String communityName) {
        Instant now = Instant.parse("2026-10-18T00:00:00Z");
        return new MembershipResponse("1", communityId, communityName,
                "ACTIVE", now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), true);
    }
}
