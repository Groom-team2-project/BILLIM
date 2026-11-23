package com.billim.domain.community.service;

import com.billim.MockChatRedisListener;

import com.billim.domain.community.dto.RegionLevel;
import com.billim.domain.community.dto.RegionScope;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
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
 * 동네 선택 단계 옵션과 범위 검색 (API 명세 E_073, E_009).
 *
 * <p>단계는 등록된 활성 동네의 공개 주소에서만 모인다. 전국 행정동 목록이 아니다.
 * 구가 있는 시, 구가 없는 자치구, 시·군·구가 없는 세종을 한 데이터에 섞어 둔다.
 */
@Import(CommunityRegionOptionsDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false",
        // 단계 집계만 본다. 개발자 .env에 카카오 키가 있어도 보충 등록으로 외부를 부르지 않게 끈다
        "billim.community.verification.region.enabled=false"
})
@MockChatRedisListener
class CommunityRegionOptionsDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired CommunityQueryService queryService;
    @Autowired CommunityRepository communityRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbc;

    private long yeoksam1;
    private long jochon;
    private long mangwon;

    @BeforeEach
    void setUp() {
        clean();
        yeoksam1 = save("역삼1동", "서울특별시 강남구 역삼1동", CommunityStatus.ACTIVE);
        save("역삼2동", "서울특별시 강남구 역삼2동", CommunityStatus.ACTIVE);
        save("삼성1동", "서울특별시 강남구 삼성1동", CommunityStatus.ACTIVE);
        mangwon = save("망원동", "서울특별시 마포구 망원동", CommunityStatus.ACTIVE);
        // 구가 있는 시 — 공개 주소가 네 토큰이다
        save("영통1동", "경기도 수원시 영통구 영통1동", CommunityStatus.ACTIVE);
        save("매탄1동", "경기도 수원시 영통구 매탄1동", CommunityStatus.ACTIVE);
        save("정왕동", "경기도 시흥시 정왕동", CommunityStatus.ACTIVE);
        // 시·군·구가 없는 세종 — 두 토큰이다
        jochon = save("조촌동", "세종특별자치시 조촌동", CommunityStatus.ACTIVE);
        // 같은 행정동에 경계가 다른 동네가 하나 더 있다 (region_code 없이 운영 seed)
        save("새솔마을 5단지", "서울특별시 강남구 역삼1동", CommunityStatus.ACTIVE);
        // 비활성 동네의 지역은 어느 단계에도 나오지 않는다
        save("폐지된동", "제주특별자치도 제주시 폐지된동", CommunityStatus.INACTIVE);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("UPDATE members SET active_community_id = NULL");
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM communities");
        jdbc.update("DELETE FROM members");
    }

    private long save(String name, String displayAddress, CommunityStatus status) {
        return communityRepository.save(Community.of(name, displayAddress,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), 500, status)).getId();
    }

    /**
     * 가입자를 붙인다. V22가 회원당 ACTIVE 소속 하나를 보장하므로 동네마다 회원을 따로 만든다.
     *
     * <p>{@code expiresInDays}가 음수면 인증이 만료된 소속이다. 만료 시각은 인증 시각보다 뒤여야
     * 하므로({@code ck_community_memberships_expires}) 인증을 60일 전으로 둔다.
     */
    private void addMember(long communityId, String status, int expiresInDays) {
        long memberId = memberRepository.save(Member.register("이웃" + communityId + status)).getId();
        // 이탈한 소속은 left_at이 있어야 하고 슬롯은 비어야 한다.
        // 저장 동네(ACTIVE)는 슬롯 1~2를 가져야 한다 (ck_community_memberships_active_slot, V23)
        boolean left = "LEFT".equals(status);
        String leftAt = left ? "NOW(6)" : "NULL";
        String slot = left ? "NULL" : "1";
        jdbc.update("""
                INSERT INTO community_memberships
                    (member_id, community_id, status, active_slot, verified_at,
                     verification_expires_at, left_at, created_at, updated_at)
                VALUES (?, ?, ?, %s, DATE_SUB(NOW(6), INTERVAL 60 DAY),
                        DATE_ADD(NOW(6), INTERVAL ? DAY), %s, NOW(6), NOW(6))
                """.formatted(slot, leftAt), memberId, communityId, status, expiresInDays);
    }

    // ---------- 단계 옵션 ----------

    @Test
    @DisplayName("시·도는 중복 없이 가나다순이고 비활성 동네의 지역은 빠진다")
    void sidoOptions() {
        assertThat(options(RegionLevel.SIDO, RegionScope.ALL))
                .containsExactly("경기도", "서울특별시", "세종특별자치시");
    }

    @Test
    @DisplayName("시·군·구는 고른 시·도 안으로 좁혀진다")
    void sigunguOptions() {
        assertThat(options(RegionLevel.SIGUNGU, RegionScope.of("서울특별시", null, null, null)))
                .containsExactly("강남구", "마포구");
        assertThat(options(RegionLevel.SIGUNGU, RegionScope.of("경기도", null, null, null)))
                .containsExactly("수원시", "시흥시");
    }

    @Test
    @DisplayName("구는 구가 있는 시에만 있다. 없으면 빈 목록이라 화면이 단계를 건너뛴다")
    void guOptions() {
        assertThat(options(RegionLevel.GU, RegionScope.of("경기도", "수원시", null, null)))
                .containsExactly("영통구");
        assertThat(options(RegionLevel.GU, RegionScope.of("서울특별시", "강남구", null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("읍·면·동은 구가 있으면 구 안으로, 없으면 시·군·구 안으로 좁혀진다")
    void dongOptions() {
        assertThat(options(RegionLevel.DONG, RegionScope.of("경기도", "수원시", "영통구", null)))
                .containsExactly("매탄1동", "영통1동");
        assertThat(options(RegionLevel.DONG, RegionScope.of("서울특별시", "강남구", null, null)))
                .containsExactly("삼성1동", "역삼1동", "역삼2동");
    }

    @Test
    @DisplayName("같은 행정동에 동네가 둘이어도 읍·면·동은 한 번만 나온다")
    void dongIsDistinct() {
        // 역삼1동에는 행정동 동네와 단지 동네가 있다
        assertThat(options(RegionLevel.DONG, RegionScope.of("서울특별시", "강남구", null, null)))
                .filteredOn("역삼1동"::equals).hasSize(1);
    }

    @Test
    @DisplayName("시·군·구가 없는 세종은 시·도 바로 아래가 읍·면·동이다")
    void sejongHasNoSigungu() {
        RegionScope sejong = RegionScope.of("세종특별자치시", null, null, null);

        assertThat(options(RegionLevel.SIGUNGU, sejong)).isEmpty();
        assertThat(options(RegionLevel.DONG, sejong)).containsExactly("조촌동");
    }

    @Test
    @DisplayName("상위 단계를 비우고 아래 단계를 물으면 400이다. 범위가 정해지지 않는다")
    void missingParentIsRejected() {
        assertRejects(RegionLevel.SIGUNGU, RegionScope.ALL);
        assertRejects(RegionLevel.GU, RegionScope.of("경기도", null, null, null));
        assertRejects(RegionLevel.DONG, RegionScope.ALL);
        // 시·도를 비우고 시·군·구만 고른 범위
        assertRejects(RegionLevel.DONG, RegionScope.of(null, "강남구", null, null));
    }

    @Test
    @DisplayName("이름이 겹치는 상위 단계에 걸리지 않는다. 접두사에 토큰 경계를 둔다")
    void prefixRespectsTokenBoundary() {
        save("강남구갑동", "서울특별시 강남구갑 어딘가동", CommunityStatus.ACTIVE);

        assertThat(options(RegionLevel.DONG, RegionScope.of("서울특별시", "강남구", null, null)))
                .containsExactly("삼성1동", "역삼1동", "역삼2동");
    }

    // ---------- 가입자 있는 동네만 (hasMembers) ----------

    @Test
    @DisplayName("가입자가 없는 동네는 단계에서 빠진다. 검색만 해도 동네가 등록되기 때문이다")
    void membersOnlySkipsEmptyCommunities() {
        addMember(yeoksam1, "ACTIVE", 30);

        assertThat(optionsWithMembers(RegionLevel.SIDO, RegionScope.ALL))
                .containsExactly("서울특별시");
        assertThat(optionsWithMembers(RegionLevel.SIGUNGU, RegionScope.of("서울특별시", null, null, null)))
                .containsExactly("강남구");
        assertThat(optionsWithMembers(RegionLevel.DONG, RegionScope.of("서울특별시", "강남구", null, null)))
                .containsExactly("역삼1동");
    }

    @Test
    @DisplayName("인증이 만료된 소속도 가입자로 센다. 재인증하면 돌아오는 사람이다")
    void membersOnlyCountsExpiredVerification() {
        addMember(jochon, "ACTIVE", -30);

        assertThat(optionsWithMembers(RegionLevel.SIDO, RegionScope.ALL))
                .containsExactly("세종특별자치시");
    }

    @Test
    @DisplayName("이탈한 소속은 가입자가 아니다")
    void membersOnlyIgnoresLeftMembership() {
        addMember(mangwon, "LEFT", 30);

        assertThat(optionsWithMembers(RegionLevel.SIDO, RegionScope.ALL)).isEmpty();
    }

    @Test
    @DisplayName("동네 목록도 같은 기준으로 걸러진다. 단계와 목록이 어긋나지 않게")
    void searchMembersOnly() {
        addMember(yeoksam1, "ACTIVE", 30);

        // 역삼1동에는 동네가 둘인데 가입자가 있는 쪽만 남는다
        assertThat(queryService.search(null,
                RegionScope.of("서울특별시", "강남구", null, "역삼1동"), true, 0, 20).items())
                .extracting("name").containsExactly("역삼1동");
        assertThat(queryService.search(null,
                RegionScope.of("서울특별시", "강남구", null, "역삼1동"), false, 0, 20).items())
                .extracting("name").containsExactly("새솔마을 5단지", "역삼1동");
    }

    // ---------- 범위 검색 (E_009) ----------

    @Test
    @DisplayName("범위로 검색하면 그 안의 동네만 이름순으로 나온다")
    void searchWithinScope() {
        assertThat(queryService.search(null,
                RegionScope.of("경기도", "수원시", "영통구", null), false, 0, 20).items())
                .extracting("name").containsExactly("매탄1동", "영통1동");
    }

    @Test
    @DisplayName("읍·면·동까지 고르면 그 행정동의 동네만 나온다. 단지 동네도 함께다")
    void searchAtDong() {
        assertThat(queryService.search(null,
                RegionScope.of("서울특별시", "강남구", null, "역삼1동"), false, 0, 20).items())
                .extracting("name").containsExactly("새솔마을 5단지", "역삼1동");
    }

    @Test
    @DisplayName("범위와 검색어를 함께 주면 범위 안에서 찾는다. 공개 주소도 함께 본다")
    void searchKeywordWithinScope() {
        // "새솔마을 5단지"는 이름에 역삼이 없지만 공개 주소가 "…강남구 역삼1동"이라 걸린다.
        // E_009가 이름과 공개 주소를 함께 보기 때문이고, 그래야 상위 지역으로도 찾을 수 있다
        assertThat(queryService.search("역삼",
                RegionScope.of("서울특별시", "강남구", null, null), false, 0, 20).items())
                .extracting("name").containsExactly("새솔마을 5단지", "역삼1동", "역삼2동");
        // 같은 검색어라도 다른 범위에서는 나오지 않는다
        assertThat(queryService.search("역삼",
                RegionScope.of("경기도", null, null, null), false, 0, 20).items()).isEmpty();
    }

    @Test
    @DisplayName("범위를 비우면 지금까지처럼 활성 동네 전체다")
    void searchWithoutScope() {
        assertThat(queryService.search(null, RegionScope.ALL, false, 0, 50).totalElements())
                .isEqualTo(9);
    }

    @Test
    @DisplayName("범위도 위에서 아래로 채워야 한다. 비우고 아래만 주면 400이다")
    void searchRejectsBrokenScope() {
        assertThatThrownBy(() -> queryService.search(
                null, RegionScope.of(null, "강남구", null, null), false, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private List<String> options(RegionLevel level, RegionScope scope) {
        return queryService.findRegionOptions(level, scope, false).items();
    }

    private List<String> optionsWithMembers(RegionLevel level, RegionScope scope) {
        return queryService.findRegionOptions(level, scope, true).items();
    }

    private void assertRejects(RegionLevel level, RegionScope scope) {
        assertThatThrownBy(() -> queryService.findRegionOptions(level, scope, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
