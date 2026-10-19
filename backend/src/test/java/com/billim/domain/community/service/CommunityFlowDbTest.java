package com.billim.domain.community.service;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동네 검색 → 위치 인증 → 공용 장소 조회까지의 통합 동작 (API 명세 E_009 ~ E_012, E_071).
 *
 * <p>커밋 후 상태(활성 소속 하나, members.active_community_id)를 확인해야 하므로
 * 테스트를 트랜잭션으로 감싸지 않고 직접 정리한다.
 */
@Import(CommunityFlowDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false",
        // 판정 기준을 테스트에서 고정한다. 운영 기본값이 바뀌어도 이 검증은 흔들리지 않는다
        "billim.community.verification.max-accuracy-meters=5000",
        "billim.community.verification.accuracy-tolerance=true",
        // 이 테스트는 반경 판정만 본다. 개발자 .env에 카카오 키가 있어도 외부를 부르지 않게 끈다
        "billim.community.verification.region.enabled=false"
})
class CommunityFlowDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    /** 반경 500m 동네의 중심 */
    private static final double CENTER_LAT = 37.5000000;
    private static final double CENTER_LON = 127.0000000;
    private static final int RADIUS_M = 500;

    /** 위 properties로 고정한 정확도 상한 */
    private static final double MAX_ACCURACY_M = 5000;

    @Autowired CommunityQueryService queryService;
    @Autowired CommunityVerificationService verificationService;
    @Autowired CommunityPlaceService placeService;
    @Autowired CommunityMembershipQuery membershipQuery;
    @Autowired CommunityRepository communityRepository;
    @Autowired CommunityMembershipRepository membershipRepository;
    @Autowired CommunityPlaceRepository placeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbc;

    private long me;
    private long homeId;
    private long neighborId;

    @BeforeEach
    void setUp() {
        clean();
        me = memberRepository.save(Member.register("소빈")).getId();
        homeId = saveCommunity("새솔마을 5단지", CENTER_LAT, CENTER_LON, CommunityStatus.ACTIVE);
        // 약 1.1km 북쪽. 반경 밖이고 주변 목록에서는 두 번째
        neighborId = saveCommunity("배곧한울마을", CENTER_LAT + 0.01, CENTER_LON, CommunityStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /** 자식 → 부모 순서로 지운다 */
    private void clean() {
        jdbc.update("UPDATE members SET active_community_id = NULL");
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM community_places");
        jdbc.update("DELETE FROM communities");
        jdbc.update("DELETE FROM members");
    }

    // ---------- 검색·조회 ----------

    @Test
    @DisplayName("이름 부분 검색으로 활성 동네를 찾고, 키워드를 비우면 전체를 준다")
    void searchByName() {
        assertThat(queryService.search("새솔", 0, 20).items())
                .extracting("name").containsExactly("새솔마을 5단지");
        assertThat(queryService.search(null, 0, 20).items()).hasSize(2);
    }

    @Test
    @DisplayName("비활성 동네는 검색·조회에서 빠지고 404다")
    void inactiveCommunityIsHidden() {
        long inactive = saveCommunity("폐지된마을", 37.6, 127.1, CommunityStatus.INACTIVE);

        assertThat(queryService.search(null, 0, 20).items()).hasSize(2);
        assertThatThrownBy(() -> queryService.findActive(inactive))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("동네 정보에는 유효 인증 회원 수가 담긴다")
    void communityIncludesVerifiedMemberCount() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(queryService.findActive(homeId).verifiedMemberCount()).isEqualTo(1);
        assertThat(queryService.findActive(neighborId).verifiedMemberCount()).isZero();
    }

    @Test
    @DisplayName("만료된 인증은 회원 수에 세지 않는다")
    void expiredVerificationIsNotCounted() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        expireMembership(me, homeId);

        assertThat(queryService.findActive(homeId).verifiedMemberCount()).isZero();
    }

    @Test
    @DisplayName("주변 동네는 가까운 순으로 준다")
    void nearbyIsOrderedByDistance() {
        var nearby = queryService.findNearby(location(CENTER_LAT, CENTER_LON, 10));

        assertThat(nearby.items()).extracting("id")
                .containsExactly(String.valueOf(homeId), String.valueOf(neighborId));
        assertThat(nearby.truncated()).isFalse();
    }

    @Test
    @DisplayName("주변 동네 조회는 정확도가 낮아도 거부하지 않는다. 인증이 아니기 때문")
    void nearbyAcceptsLowAccuracy() {
        assertThat(queryService.findNearby(location(CENTER_LAT, CENTER_LON, 5000)).items()).hasSize(2);
        assertThat(membershipQuery.findActiveCommunityId(me)).isEmpty();
    }

    // ---------- 위치 인증 ----------

    @Test
    @DisplayName("반경 안에서 인증하면 활성 소속이 되고 선택 동네가 회원에 반영된다")
    void verificationActivatesMembership() {
        MembershipResponse response = verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.communityName()).isEqualTo("새솔마을 5단지");
        assertThat(response.isValid()).isTrue();
        assertThat(membershipQuery.findActiveCommunityId(me)).contains(homeId);
        assertThat(activeCommunityIdOf(me)).isEqualTo(homeId);
    }

    @Test
    @DisplayName("반경 밖이면 403이고 소속이 생기지 않는다")
    void outsideRadiusIsForbidden() {
        // 중심에서 약 1.1km
        assertThatThrownBy(() -> verify(me, homeId, CENTER_LAT + 0.01, CENTER_LON, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_OUTSIDE));

        assertThat(membershipRepository.findAll()).isEmpty();
        assertThat(activeCommunityIdOf(me)).isNull();
    }

    @Test
    @DisplayName("정확도 상한을 넘으면 422이고 반경 판정을 하지 않는다")
    void inaccurateLocationIsRejected() {
        // 중심 좌표라 반경 안이지만 정확도만으로 거부된다
        assertThatThrownBy(() -> verify(me, homeId, CENTER_LAT, CENTER_LON, MAX_ACCURACY_M + 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_INACCURATE));

        assertThat(membershipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정확도 상한값은 허용한다")
    void accuracyAtLimitIsAllowed() {
        assertThat(verify(me, homeId, CENTER_LAT, CENTER_LON, MAX_ACCURACY_M).isValid()).isTrue();
    }

    @Test
    @DisplayName("측위 오차가 크면 반경 경계 밖이어도 그 오차만큼 너그럽게 본다")
    void accuracyWidensTheRadius() {
        // 중심에서 약 1.1km. 반경 500m를 넘지만 오차 1km를 빼면 안으로 들어온다
        assertThat(verify(me, homeId, CENTER_LAT + 0.01, CENTER_LON, 1000).isValid()).isTrue();
    }

    @Test
    @DisplayName("오차를 빼도 반경 밖이면 거부한다")
    void accuracyDoesNotForgiveFarAway() {
        // 중심에서 약 11km. 오차 1km로는 반경 500m 안으로 들어오지 못한다
        assertThatThrownBy(() -> verify(me, homeId, CENTER_LAT + 0.1, CENTER_LON, 1000))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_OUTSIDE));
    }

    @Test
    @DisplayName("같은 동네를 재인증하면 새 행을 만들지 않고 만료를 연장한다")
    void reverificationUpdatesSameRow() {
        MembershipResponse first = verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        expireMembership(me, homeId);

        MembershipResponse again = verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(membershipRepository.findAll()).hasSize(1);
        assertThat(again.isValid()).isTrue();
    }

    @Test
    @DisplayName("다른 동네를 인증하면 이전 소속은 LEFT가 되고 활성 소속은 하나만 남는다")
    void switchingCommunityLeavesPrevious() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        verify(me, neighborId, CENTER_LAT + 0.01, CENTER_LON, 10);

        CommunityMembership previous = membershipRepository
                .findByMemberIdAndCommunityId(me, homeId).orElseThrow();
        assertThat(previous.getStatus()).isEqualTo(MembershipStatus.LEFT);
        assertThat(previous.getLeftAt()).isNotNull();

        assertThat(membershipRepository.findByMemberIdAndStatus(me, MembershipStatus.ACTIVE))
                .get().extracting(CommunityMembership::getCommunityId).isEqualTo(neighborId);
        assertThat(activeCommunityIdOf(me)).isEqualTo(neighborId);
    }

    @Test
    @DisplayName("비활성 동네는 인증할 수 없다")
    void inactiveCommunityCannotBeVerified() {
        long inactive = saveCommunity("폐지된마을", CENTER_LAT, CENTER_LON, CommunityStatus.INACTIVE);

        assertThatThrownBy(() -> verify(me, inactive, CENTER_LAT, CENTER_LON, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---------- 공용 장소 ----------

    @Test
    @DisplayName("유효 소속이면 활성 장소만 이름순으로 본다")
    void memberSeesActivePlacesOnly() {
        savePlace(homeId, "관리동 무인택배함", true);
        savePlace(homeId, "경비실 앞", true);
        savePlace(homeId, "철거된 정자", false);
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(placeService.findPlaces(me, homeId).items())
                .extracting("name").containsExactly("경비실 앞", "관리동 무인택배함");
    }

    @Test
    @DisplayName("소속이 없으면 장소 목록을 볼 수 없다")
    void nonMemberCannotSeePlaces() {
        savePlace(homeId, "경비실 앞", true);

        assertThatThrownBy(() -> placeService.findPlaces(me, homeId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("인증이 만료되면 장소 목록을 볼 수 없다")
    void expiredMembershipCannotSeePlaces() {
        savePlace(homeId, "경비실 앞", true);
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        expireMembership(me, homeId);

        assertThatThrownBy(() -> placeService.findPlaces(me, homeId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 동네로 옮긴 뒤에는 이전 동네 장소를 볼 수 없다")
    void leftCommunityPlacesAreHidden() {
        savePlace(homeId, "경비실 앞", true);
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        verify(me, neighborId, CENTER_LAT + 0.01, CENTER_LON, 10);

        assertThatThrownBy(() -> placeService.findPlaces(me, homeId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("장소가 없는 동네는 빈 목록을 준다")
    void emptyPlaceListIsAllowed() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(placeService.findPlaces(me, homeId).items()).isEmpty();
    }

    // ---------- 소속 판정 창구 ----------

    @Test
    @DisplayName("소속 판정은 활성이고 만료 전인 소속만 유효로 본다")
    void membershipQueryHonoursExpiry() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        assertThat(membershipQuery.hasValidMembership(me, homeId)).isTrue();

        expireMembership(me, homeId);

        assertThat(membershipQuery.hasValidMembership(me, homeId)).isFalse();
        assertThat(membershipQuery.findActiveCommunityId(me)).isEmpty();
    }

    @Test
    @DisplayName("활성 소속 조회는 동네 이름과 인증 시점까지 함께 준다")
    void findsActiveMembershipWithCommunityName() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);

        assertThat(membershipQuery.findActiveMembership(me)).get().satisfies(membership -> {
            assertThat(membership.communityId()).isEqualTo(String.valueOf(homeId));
            assertThat(membership.communityName()).isEqualTo("새솔마을 5단지");
            assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE.name());
            assertThat(membership.isValid()).isTrue();
            assertThat(membership.verifiedAt()).isNotNull();
            assertThat(membership.verificationExpiresAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("인증이 만료되면 활성 소속 조회도 비어 있다")
    void activeMembershipIsEmptyWhenExpired() {
        verify(me, homeId, CENTER_LAT, CENTER_LON, 10);
        expireMembership(me, homeId);

        assertThat(membershipQuery.findActiveMembership(me)).isEmpty();
    }

    // ---------- fixtures ----------

    private MembershipResponse verify(long memberId, long communityId,
                                      double latitude, double longitude, double accuracy) {
        return verificationService.verify(memberId, communityId,
                location(latitude, longitude, accuracy));
    }

    private static VerifyLocationRequest location(double latitude, double longitude, double accuracy) {
        return new VerifyLocationRequest(latitude, longitude, accuracy);
    }

    private long saveCommunity(String name, double latitude, double longitude, CommunityStatus status) {
        return communityRepository.save(Community.of(
                name, name + " 일대",
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                RADIUS_M, status)).getId();
    }

    private void savePlace(long communityId, String name, boolean active) {
        placeRepository.save(CommunityPlace.of(communityId, name,
                BigDecimal.valueOf(CENTER_LAT), BigDecimal.valueOf(CENTER_LON), null, active));
    }

    /**
     * 인증 만료를 직접 만든다. DATETIME은 UTC instant를 담으므로(ERD 1장) 시각도 UTC로 계산한다.
     * Java Timestamp를 바인딩하면 JDBC가 JVM 기본 시간대로 변환해 기준이 어긋난다.
     */
    private void expireMembership(long memberId, long communityId) {
        jdbc.update("""
                UPDATE community_memberships
                   SET verified_at = UTC_TIMESTAMP(6) - INTERVAL 31 DAY,
                       verification_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY
                 WHERE member_id = ? AND community_id = ?
                """, memberId, communityId);
    }

    private Long activeCommunityIdOf(long memberId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT active_community_id FROM members WHERE id = ?", Long.class, memberId);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
