package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.Report;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 동네 운영 통계의 집계 쿼리 (API 명세 13.6 E_069).
 *
 * <p>통계는 물건·대여·소속·신고를 함께 세므로 다른 도메인의 테이블을 직접 읽는다. 조회 전용이고
 * 엔티티를 쓰지 않아 각 도메인의 쓰기 규칙에는 관여하지 않는다. 쓰기 규칙이 필요한 일은
 * 해당 도메인의 서비스를 호출해야 한다.
 *
 * <p>{@link Repository}만 상속해 CRUD를 열지 않는다. 타입 인자의 {@link Report}는 이 도메인이
 * 소유한 엔티티를 앵커로 둔 것이고 신고 집계에만 쓰인다.
 *
 * <p>KST 변환은 {@code created_at + INTERVAL 9 HOUR}로 한다. CONVERT_TZ는 MySQL 타임존 테이블이
 * 적재돼 있어야 동작한다. 저장은 UTC, 연결 time_zone도 UTC다. (ERD 1 공통 규칙)
 */
public interface AdminStatisticsRepository extends Repository<Report, Long> {

    /** 동네가 있는지. 없는 id로 0만 가득한 통계를 돌려주지 않는다 */
    @Query(value = "SELECT COUNT(*) FROM communities WHERE id = :communityId", nativeQuery = true)
    long countCommunity(@Param("communityId") long communityId);

    /** 누적 등록 물건. 논리 삭제만 제외해 숨긴 물건도 운영자에게는 보인다 */
    @Query(value = """
            SELECT COUNT(*)
            FROM items
            WHERE community_id = :communityId
              AND visibility <> 'DELETED'
            """, nativeQuery = true)
    long countRegisteredItems(@Param("communityId") long communityId);

    /** 기간 내 등록된 물건 */
    @Query(value = """
            SELECT COUNT(*)
            FROM items
            WHERE community_id = :communityId
              AND visibility <> 'DELETED'
              AND created_at >= :fromAt
              AND created_at < :toAt
            """, nativeQuery = true)
    long countNewItems(@Param("communityId") long communityId,
                       @Param("fromAt") Instant fromAt,
                       @Param("toAt") Instant toAt);

    /**
     * 진행 중인 대여. 승인 이후만 센다.
     *
     * <p>REQUESTED는 아직 거래가 아니다 — 소유자가 승인하지 않았고 시작일 다음 날 자동 만료된다.
     * 운영자 화면의 '활성 대여 · 진행 중'이 요청 대기까지 포함하면 실제보다 커 보인다.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM rentals
            WHERE community_id = :communityId
              AND status IN ('APPROVED', 'ACTIVE')
            """, nativeQuery = true)
    long countActiveRentals(@Param("communityId") long communityId);

    /** 기간 내 합류한 이웃. 재합류는 같은 행을 갱신해 created_at이 최초 합류로 남는다 */
    @Query(value = """
            SELECT COUNT(*)
            FROM community_memberships
            WHERE community_id = :communityId
              AND created_at >= :fromAt
              AND created_at < :toAt
            """, nativeQuery = true)
    long countNewMembers(@Param("communityId") long communityId,
                         @Param("fromAt") Instant fromAt,
                         @Param("toAt") Instant toAt);

    /** 누적 미처리 신고 */
    @Query(value = """
            SELECT COUNT(*)
            FROM reports
            WHERE community_id = :communityId
              AND status = 'PENDING'
            """, nativeQuery = true)
    long countPendingReports(@Param("communityId") long communityId);

    /**
     * 기간 내 생성된 대여 요청을 KST 날짜별로 센다. 요청 수·성사 수·일자별 그래프를 한 번에 얻는다.
     *
     * <p>요청이 없는 날은 행이 나오지 않는다. 서비스가 0으로 채운다.
     */
    @Query(value = """
            SELECT DATE(created_at + INTERVAL 9 HOUR) AS statDate,
                   COUNT(*)                           AS requestCount,
                   SUM(status = 'RETURNED')           AS returnedCount
            FROM rentals
            WHERE community_id = :communityId
              AND created_at >= :fromAt
              AND created_at < :toAt
            GROUP BY statDate
            ORDER BY statDate
            """, nativeQuery = true)
    List<DailyRentalRow> findDailyRentals(@Param("communityId") long communityId,
                                          @Param("fromAt") Instant fromAt,
                                          @Param("toAt") Instant toAt);

    /**
     * 카테고리별 누적 등록 물건. 0건인 분류도 빠지지 않게 categories에서 LEFT JOIN한다.
     *
     * <p>active 조건을 걸지 않는다. 비활성 분류를 빼면 카테고리 합계가 등록 총수와 어긋난다.
     * (도메인 가이드 결정 '운영 통계' — 합계·비율을 검증한다)
     */
    @Query(value = """
            SELECT c.id          AS categoryId,
                   c.code        AS code,
                   c.name        AS name,
                   c.sort_order  AS sortOrder,
                   COUNT(i.id)   AS itemCount
            FROM categories c
                     LEFT JOIN items i
                               ON i.category_id = c.id
                                   AND i.community_id = :communityId
                                   AND i.visibility <> 'DELETED'
            GROUP BY c.id, c.code, c.name, c.sort_order
            ORDER BY c.sort_order, c.id
            """, nativeQuery = true)
    List<CategoryCountRow> findCategoryCounts(@Param("communityId") long communityId);

    /**
     * 기간 내 생성된 요청 중 반납까지 간 건수가 많은 물건.
     *
     * <p>기간 기준을 반납 시각이 아니라 요청 생성 시각으로 둔다. 성사율의 분자와 같은 모집단이어야
     * 두 수치가 서로 설명된다. 동수는 itemId 오름차순으로 끊어 호출마다 순서가 바뀌지 않게 한다.
     */
    @Query(value = """
            SELECT r.item_id AS itemId,
                   i.title   AS title,
                   COUNT(*)  AS returnedCount
            FROM rentals r
                     JOIN items i ON i.id = r.item_id
            WHERE r.community_id = :communityId
              AND r.created_at >= :fromAt
              AND r.created_at < :toAt
              AND r.status = 'RETURNED'
            GROUP BY r.item_id, i.title
            ORDER BY returnedCount DESC, r.item_id
            LIMIT :limit
            """, nativeQuery = true)
    List<PopularItemRow> findPopularItems(@Param("communityId") long communityId,
                                          @Param("fromAt") Instant fromAt,
                                          @Param("toAt") Instant toAt,
                                          @Param("limit") int limit);

    /** 일자별 대여 집계 한 줄. statDate는 KST 달력 날짜다 */
    interface DailyRentalRow {
        java.time.LocalDate getStatDate();

        long getRequestCount();

        long getReturnedCount();
    }

    /** 카테고리별 집계 한 줄 */
    interface CategoryCountRow {
        long getCategoryId();

        String getCode();

        String getName();

        int getSortOrder();

        long getItemCount();
    }

    /** 인기 물건 한 줄 */
    interface PopularItemRow {
        long getItemId();

        String getTitle();

        long getReturnedCount();
    }
}
