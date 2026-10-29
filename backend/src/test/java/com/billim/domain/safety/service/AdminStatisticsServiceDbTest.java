package com.billim.domain.safety.service;

import com.billim.MockChatRedisListener;
import com.billim.domain.safety.dto.AdminStatisticsResponse;
import com.billim.domain.safety.dto.CategoryCountResponse;
import com.billim.domain.safety.dto.DailyCountResponse;
import com.billim.domain.safety.dto.PopularItemResponse;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동네 운영 통계의 집계 정의 (API 명세 13.6, 도메인 가이드 결정 '운영 통계').
 *
 * <p>집계는 SQL이 하므로 실제 MySQL로 확인한다. 기간 경계는 KST 달력 날짜라 UTC 저장값과
 * 9시간 어긋난다 — 그 경계가 맞는지가 이 테스트의 핵심이다.
 *
 * <p>fixture는 created_at을 정확히 지정해야 해서 JDBC로 직접 넣는다. 다른 도메인의 엔티티
 * 팩토리를 쓰면 시각을 통제할 수 없다.
 */
@Import(AdminStatisticsServiceDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false"
})
@MockChatRedisListener
class AdminStatisticsServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    /** 2026-10-01 ~ 10-07 KST */
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 7);

    @Autowired AdminStatisticsService service;
    @Autowired JdbcTemplate jdbc;

    private long community;
    private long otherCommunity;
    private long owner;
    private long borrower;
    private long toolCategory;
    private long campCategory;

    @BeforeEach
    void setUp() {
        clean();
        community = saveCommunity("역삼1동");
        otherCommunity = saveCommunity("서초2동");
        owner = saveMember("정우");
        borrower = saveMember("소빈");
        toolCategory = categoryId("TOOL");
        campCategory = categoryId("CAMP");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ---------- 누적 ----------

    @Test
    @DisplayName("등록 물건은 논리 삭제만 제외한다. 숨긴 물건도 운영자에게는 등록된 물건이다")
    void registeredItemsExcludeOnlyDeleted() {
        saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveItem(community, toolCategory, "숨긴 사다리", "HIDDEN", at("2026-09-02"));
        saveItem(community, toolCategory, "지운 톱", "DELETED", at("2026-09-03"));
        saveItem(otherCommunity, toolCategory, "남의 동네 드릴", "PUBLIC", at("2026-09-04"));

        assertThat(stats().registeredItems()).isEqualTo(2);
    }

    @Test
    @DisplayName("활성 대여는 승인 이후만 센다. 요청 대기는 아직 거래가 아니다")
    void activeRentalsCountApprovedAndActive() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveRental(community, item, "REQUESTED", at("2026-10-02"));
        saveRental(community, item, "APPROVED", at("2026-10-02"));
        saveRental(community, item, "ACTIVE", at("2026-10-02"));
        saveRental(community, item, "RETURNED", at("2026-10-02"));
        saveRental(community, item, "CANCELED", at("2026-10-02"));

        assertThat(stats().activeRentals()).isEqualTo(2);
    }

    @Test
    @DisplayName("활성 대여·미처리 신고는 기간과 무관한 누적이다")
    void cumulativeFieldsIgnorePeriod() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        // 기간(10/1~10/7) 밖
        saveRental(community, item, "ACTIVE", at("2026-08-20"));
        saveReport(community, "PENDING", at("2026-08-20"));
        saveReport(community, "RESOLVED", at("2026-08-21"));

        AdminStatisticsResponse stats = stats();
        assertThat(stats.activeRentals()).isEqualTo(1);
        assertThat(stats.pendingReports()).isEqualTo(1);
    }

    // ---------- 기간 경계 ----------

    @Test
    @DisplayName("기간 경계는 KST 달력 날짜다. 10/1 00:00 KST 직전과 10/7 24:00 KST 직후는 빠진다")
    void periodBoundaryFollowsKst() {
        // 10/1 00:00 KST = 09/30 15:00 UTC
        saveItem(community, toolCategory, "경계 직전", "PUBLIC", Instant.parse("2026-09-30T14:59:59Z"));
        saveItem(community, toolCategory, "기간 첫 순간", "PUBLIC", Instant.parse("2026-09-30T15:00:00Z"));
        // 10/8 00:00 KST = 10/07 15:00 UTC
        saveItem(community, toolCategory, "기간 끝 순간", "PUBLIC", Instant.parse("2026-10-07T14:59:59Z"));
        saveItem(community, toolCategory, "경계 직후", "PUBLIC", Instant.parse("2026-10-07T15:00:00Z"));

        assertThat(stats().newItems()).isEqualTo(2);
    }

    @Test
    @DisplayName("일자별 요청은 KST 날짜로 묶는다. UTC로 묶으면 밤 9시 이후 요청이 전날로 간다")
    void dailyCountsGroupByKstDate() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        // 10/2 23:30 KST = 10/2 14:30 UTC — KST로 10/2
        saveRental(community, item, "REQUESTED", Instant.parse("2026-10-02T14:30:00Z"));
        // 10/3 00:30 KST = 10/2 15:30 UTC — KST로 10/3
        saveRental(community, item, "REQUESTED", Instant.parse("2026-10-02T15:30:00Z"));

        List<DailyCountResponse> daily = stats().dailyRentalCounts();
        assertThat(countOn(daily, LocalDate.of(2026, 10, 2))).isEqualTo(1);
        assertThat(countOn(daily, LocalDate.of(2026, 10, 3))).isEqualTo(1);
    }

    @Test
    @DisplayName("요청이 없는 날도 0으로 채운다. 날짜를 건너뛰면 그래프 추세가 왜곡된다")
    void dailyCountsFillEmptyDays() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveRental(community, item, "REQUESTED", at("2026-10-03"));

        List<DailyCountResponse> daily = stats().dailyRentalCounts();
        assertThat(daily).hasSize(7);
        assertThat(daily.getFirst().date()).isEqualTo(FROM);
        assertThat(daily.getLast().date()).isEqualTo(TO);
        assertThat(countOn(daily, LocalDate.of(2026, 10, 1))).isZero();
        assertThat(countOn(daily, LocalDate.of(2026, 10, 3))).isEqualTo(1);
    }

    // ---------- 성사율 ----------

    @Test
    @DisplayName("성사율은 기간 내 생성된 요청 대비 반납 완료 비율이다. 세 값이 서로 맞는다")
    void successRateIsReturnedOverRequests() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveRental(community, item, "RETURNED", at("2026-10-02"));
        saveRental(community, item, "RETURNED", at("2026-10-03"));
        saveRental(community, item, "REJECTED", at("2026-10-04"));
        saveRental(community, item, "ACTIVE", at("2026-10-05"));

        AdminStatisticsResponse stats = stats();
        assertThat(stats.requestCount()).isEqualTo(4);
        assertThat(stats.successfulRequestCount()).isEqualTo(2);
        assertThat(stats.successRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("요청이 없으면 성사율은 0이다. 0으로 나누지 않는다")
    void successRateIsZeroWithoutRequests() {
        AdminStatisticsResponse stats = stats();
        assertThat(stats.requestCount()).isZero();
        assertThat(stats.successRate()).isZero();
    }

    @Test
    @DisplayName("성사율은 소수 첫째 자리까지 끊는다")
    void successRateKeepsOneDecimal() {
        long item = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveRental(community, item, "RETURNED", at("2026-10-02"));
        saveRental(community, item, "REJECTED", at("2026-10-03"));
        saveRental(community, item, "REJECTED", at("2026-10-04"));

        assertThat(stats().successRate()).isEqualTo(33.3);
    }

    // ---------- 카테고리·인기 물건 ----------

    @Test
    @DisplayName("카테고리 합계는 등록 총수와 같다. 0건인 분류도 빠지지 않는다")
    void categoryCountsSumToRegisteredItems() {
        saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        saveItem(community, toolCategory, "사다리", "PUBLIC", at("2026-09-02"));
        saveItem(community, campCategory, "텐트", "PUBLIC", at("2026-09-03"));
        saveItem(community, campCategory, "지운 코펠", "DELETED", at("2026-09-04"));

        AdminStatisticsResponse stats = stats();
        List<CategoryCountResponse> counts = stats.categoryCounts();

        // 명세 Category는 10개 분류다. 0건도 응답에 남는다
        assertThat(counts).hasSize(10);
        assertThat(counts.stream().filter(c -> c.count() == 0)).isNotEmpty();
        // 결정 '운영 통계' — 합계를 검증한다
        assertThat(counts.stream().mapToInt(CategoryCountResponse::count).sum())
                .isEqualTo(stats.registeredItems());
        assertThat(countOfCode(counts, "TOOL")).isEqualTo(2);
        assertThat(countOfCode(counts, "CAMP")).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리는 sortOrder 순으로 돌려준다. 화면 분류 순서가 호출마다 바뀌지 않게")
    void categoryCountsAreSortedBySortOrder() {
        List<CategoryCountResponse> counts = stats().categoryCounts();

        assertThat(counts).isSortedAccordingTo(
                (a, b) -> Integer.compare(a.category().sortOrder(), b.category().sortOrder()));
    }

    @Test
    @DisplayName("인기 물건은 반납 완료 건수 상위 3개다. 동수는 itemId 오름차순으로 끊는다")
    void popularItemsRankByReturnedCount() {
        long drill = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        long tent = saveItem(community, campCategory, "텐트", "PUBLIC", at("2026-09-02"));
        long chair = saveItem(community, campCategory, "캠핑의자", "PUBLIC", at("2026-09-03"));
        long box = saveItem(community, campCategory, "아이스박스", "PUBLIC", at("2026-09-04"));

        saveRental(community, drill, "RETURNED", at("2026-10-02"));
        saveRental(community, drill, "RETURNED", at("2026-10-03"));
        saveRental(community, drill, "RETURNED", at("2026-10-04"));
        saveRental(community, tent, "RETURNED", at("2026-10-02"));
        saveRental(community, tent, "RETURNED", at("2026-10-03"));
        // chair와 box는 1건씩 — 동수라 id가 작은 chair가 먼저다
        saveRental(community, chair, "RETURNED", at("2026-10-02"));
        saveRental(community, box, "RETURNED", at("2026-10-02"));
        // 반납이 아닌 건은 세지 않는다
        saveRental(community, box, "ACTIVE", at("2026-10-05"));

        List<PopularItemResponse> popular = stats().popularItems();

        assertThat(popular).hasSize(3);
        assertThat(popular).extracting(PopularItemResponse::title)
                .containsExactly("전동드릴", "텐트", "캠핑의자");
        assertThat(popular.getFirst().returnedCount()).isEqualTo(3);
        assertThat(popular.getFirst().itemId()).isEqualTo(String.valueOf(drill));
    }

    @Test
    @DisplayName("인기 물건의 기간 기준은 요청 생성 시각이다. 성사 건수와 같은 모집단이어야 한다")
    void popularItemsUseRequestCreationForPeriod() {
        long drill = saveItem(community, toolCategory, "전동드릴", "PUBLIC", at("2026-09-01"));
        // 기간 안에 생성돼 반납까지 간 1건
        saveRental(community, drill, "RETURNED", at("2026-10-02"));
        // 기간 밖에 생성된 반납 건은 빠진다
        saveRental(community, drill, "RETURNED", at("2026-09-20"));

        AdminStatisticsResponse stats = stats();
        assertThat(stats.popularItems().getFirst().returnedCount()).isEqualTo(1);
        assertThat(stats.successfulRequestCount()).isEqualTo(1);
    }

    // ---------- 신규 이웃 ----------

    @Test
    @DisplayName("신규 이웃은 기간 내 합류한 소속 수다. 다른 동네 소속은 세지 않는다")
    void newMembersCountJoinsInPeriod() {
        long neighbor = saveMember("하은");
        saveMembership(owner, community, at("2026-10-02"));
        saveMembership(borrower, community, at("2026-09-20"));
        saveMembership(neighbor, otherCommunity, at("2026-10-03"));

        assertThat(stats().newMembers()).isEqualTo(1);
    }

    // ---------- 입력 검증 ----------

    @Test
    @DisplayName("from이 to보다 늦으면 400")
    void rejectsReversedPeriod() {
        assertThatThrownBy(() -> service.findStatistics(
                community, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("31일을 넘으면 400. 양끝을 포함해 31일까지 허용한다")
    void rejectsPeriodLongerThan31Days() {
        LocalDate start = LocalDate.of(2026, 10, 1);

        // 10/1 ~ 10/31 = 31일 — 통과
        assertThat(service.findStatistics(community, start, start.plusDays(30))).isNotNull();

        assertThatThrownBy(() -> service.findStatistics(community, start, start.plusDays(31)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("없는 동네는 404. 0만 가득한 통계를 정상 응답으로 주지 않는다")
    void rejectsUnknownCommunity() {
        assertThatThrownBy(() -> service.findStatistics(community + 9_999, FROM, TO))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("응답은 요청한 동네·기간을 그대로 담는다")
    void echoesRequestedCommunityAndPeriod() {
        AdminStatisticsResponse stats = stats();

        assertThat(stats.communityId()).isEqualTo(String.valueOf(community));
        assertThat(stats.from()).isEqualTo(FROM);
        assertThat(stats.to()).isEqualTo(TO);
    }

    // ---------- fixture ----------

    private AdminStatisticsResponse stats() {
        return service.findStatistics(community, FROM, TO);
    }

    /** KST 날짜의 정오. 경계와 무관한 fixture에 쓴다 */
    private Instant at(String isoDate) {
        return Instant.parse(isoDate + "T03:00:00Z");
    }

    private int countOn(List<DailyCountResponse> daily, LocalDate date) {
        return daily.stream().filter(d -> d.date().equals(date))
                .findFirst().orElseThrow().count();
    }

    private int countOfCode(List<CategoryCountResponse> counts, String code) {
        return counts.stream().filter(c -> c.category().code().equals(code))
                .findFirst().orElseThrow().count();
    }

    /** 자식 → 부모 순서로 지운다 */
    private void clean() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM rentals");
        jdbc.update("DELETE FROM items");
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM communities");
        jdbc.update("DELETE FROM members");
    }

    private long categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE code = ?", Long.class, code);
    }

    private long saveCommunity(String name) {
        jdbc.update("""
                INSERT INTO communities
                    (name, display_address, center_latitude, center_longitude,
                     verification_radius_m, status, created_at, updated_at)
                VALUES (?, ?, 37.4953, 127.0330, 3000, 'ACTIVE', NOW(6), NOW(6))
                """, name, "서울특별시 강남구 " + name);
        return lastId();
    }

    private long saveMember(String name) {
        jdbc.update("""
                INSERT INTO members (display_name, status, role, created_at, updated_at)
                VALUES (?, 'ACTIVE', 'USER', NOW(6), NOW(6))
                """, name);
        return lastId();
    }

    private long saveItem(long communityId, long categoryId, String title,
                          String visibility, Instant createdAt) {
        jdbc.update("""
                INSERT INTO items
                    (owner_id, community_id, category_id, place_id, title,
                     available_start_date, available_end_date, visibility,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, '2026-10-01', '2026-12-31', ?, 0, ?, ?)
                """, owner, communityId, categoryId, title, visibility,
                utc(createdAt), utc(createdAt));
        return lastId();
    }

    private void saveRental(long communityId, long itemId, String status, Instant createdAt) {
        jdbc.update("""
                INSERT INTO rentals
                    (item_id, owner_id, borrower_id, community_id, start_date, end_date, status,
                     request_expires_at, due_at, item_title_snapshot, place_name_snapshot,
                     place_id, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-10-10', '2026-10-11', ?,
                        NOW(6), NOW(6), '물건', '주민센터', 1, 0, ?, ?)
                """, itemId, owner, borrower, communityId, status,
                utc(createdAt), utc(createdAt));
    }

    private void saveReport(long communityId, String status, Instant createdAt) {
        jdbc.update("""
                INSERT INTO reports
                    (reporter_id, target_type, target_member_id, community_id, reason_code,
                     status, version, target_snapshot, created_at, updated_at)
                VALUES (?, 'MEMBER', ?, ?, 'MONEY_REQUEST', ?, 0, '{}', ?, ?)
                """, borrower, owner, communityId, status,
                utc(createdAt), utc(createdAt));
    }

    private void saveMembership(long memberId, long communityId, Instant createdAt) {
        jdbc.update("""
                INSERT INTO community_memberships
                    (member_id, community_id, status, verified_at, verification_expires_at,
                     created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
                """, memberId, communityId,
                utc(createdAt),
                utc(createdAt.plusSeconds(30 * 86400L)),
                utc(createdAt), utc(createdAt));
    }

    /**
     * Instant를 UTC 벽시계로 바꿔 넣는다.
     *
     * <p>{@code Timestamp.from()}을 쓰면 드라이버가 JVM 기본 시간대로 변환해 보낸다. 개발 장비가
     * KST라 9시간 밀린 값이 저장되고, Hibernate가 UTC로 쓴 운영 데이터와 어긋난다.
     * (application.yml hibernate.jdbc.time_zone=UTC)
     */
    private LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
