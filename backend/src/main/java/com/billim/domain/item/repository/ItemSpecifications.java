package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

// 물건 검색 조건 조합. 인덱스 시작 후보:
// (community_id, visibility, created_at, id) · (community_id, visibility, category_id, created_at, id)
public final class ItemSpecifications {

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
