package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// 물건 검색 조건 조합. 인덱스 시작 후보:
// (community_id, visibility, created_at, id) · (community_id, visibility, category_id, created_at, id)
public final class ItemSpecifications {

    // 기간을 점유하는 대여 상태 (정책 및 상태 3장: 승인 이상 점유, 반납 후에도 점유 유지)
    private static final List<RentalStatus> OCCUPYING_STATUSES =
            List.of(RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED);


    private ItemSpecifications() {
    }

    public static Specification<Item> community(long communityId) {
        return (root, q, cb) -> cb.equal(root.get("communityId"), communityId);
    }

    public static Specification<Item> publicOnly() {
        return (root, q, cb) -> cb.equal(root.get("visibility"), ItemVisibility.PUBLIC);
    }

    public static Specification<Item> category(Long categoryId) {
        return categoryId == null ? null : (root, q, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    // 선택 기간에 대여할 수 있는 물건만 (정책 및 상태 3장, 대여 RentalQueryService와 같은 규칙)
    // 승인·대여 중·반납 완료 대여 기간과 하루라도 겹치면 제외(양끝 포함), 연체 중인 물건 제외. 요청 대기끼리의 겹침은 허용
    // rentals (item_id, status, start_date, end_date) 인덱스로 물건별 존재 확인
    public static Specification<Item> availableForRental(LocalDate start, LocalDate end, Instant now) {
        if (start == null || end == null) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> occupied = q.subquery(Long.class);
            Root<Rental> r = occupied.from(Rental.class);
            occupied.select(r.get("id")).where(
                    cb.equal(r.get("itemId"), root.get("id")),
                    r.get("status").in(OCCUPYING_STATUSES),
                    cb.lessThanOrEqualTo(r.<LocalDate>get("startDate"), end),
                    cb.greaterThanOrEqualTo(r.<LocalDate>get("endDate"), start));

            Subquery<Long> overdue = q.subquery(Long.class);
            Root<Rental> o = overdue.from(Rental.class);
            overdue.select(o.get("id")).where(
                    cb.equal(o.get("itemId"), root.get("id")),
                    cb.equal(o.get("status"), RentalStatus.ACTIVE),
                    cb.lessThanOrEqualTo(o.<Instant>get("dueAt"), now));

            return cb.and(cb.not(cb.exists(occupied)), cb.not(cb.exists(overdue)));
        };
    }

    public static Specification<Item> ownedBy(long ownerId) {
        return (root, q, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Item> notDeleted() {
        return (root, q, cb) -> cb.notEqual(root.get("visibility"), ItemVisibility.DELETED);
    }

    // 가까운 순 (API 명세 B_017 NEAREST = distance ASC, id DESC). 거리는 장소마다 고정이라 place_id → 거리(m) CASE로 정렬
    // 거리를 모르는 장소는 맨 뒤. 개수 조회(count)에는 정렬을 붙이지 않음
    public static Specification<Item> orderByPlaceDistance(Map<Long, Integer> metersByPlace) {
        return (root, q, cb) -> {
            if (q.getResultType() != Long.class && q.getResultType() != long.class) {
                if (metersByPlace.isEmpty()) {
                    q.orderBy(cb.desc(root.get("id")));
                } else {
                    CriteriaBuilder.SimpleCase<Long, Integer> distance = cb.selectCase(root.<Long>get("placeId"));
                    for (Map.Entry<Long, Integer> e : metersByPlace.entrySet()) {
                        distance = distance.when(e.getKey(), e.getValue());
                    }
                    q.orderBy(cb.asc(distance.otherwise(Integer.MAX_VALUE)), cb.desc(root.get("id")));
                }
            }
            return cb.conjunction();
        };
    }

    public static Specification<Item> ownerNotIn(Collection<Long> ownerIds) {
        return ownerIds == null || ownerIds.isEmpty() ? null : (root, q, cb) -> cb.not(root.get("ownerId").in(ownerIds));
    }

    public static Specification<Item> place(Long placeId) {
        return placeId == null ? null : (root, q, cb) -> cb.equal(root.get("placeId"), placeId);
    }

    // 제목 부분 검색. MVP는 LIKE로 시작 후 측정. %·_·\ 이스케이프
    public static Specification<Item> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim()) + "%";
        return (root, q, cb) -> cb.like(root.<String>get("title"), pattern, '\\');
    }

    // 대여 가능 기간이 선택 기간 전체를 포함
    public static Specification<Item> availableCovers(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        return (root, q, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.<LocalDate>get("availableStartDate"), start),
                cb.greaterThanOrEqualTo(root.<LocalDate>get("availableEndDate"), end));
    }

    // null 조건 제외 후 AND 결합 (Spring Data JPA 4의 and(null) 비허용 대응)
    @SafeVarargs
    public static Specification<Item> allOf(Specification<Item> first, Specification<Item>... rest) {
        Specification<Item> spec = first;
        for (Specification<Item> s : rest) {
            if (s != null) {
                spec = spec.and(s);
            }
        }
        return spec;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
