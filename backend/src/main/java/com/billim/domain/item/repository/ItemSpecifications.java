package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

/**
 * 물건 검색 조건 조합. 인덱스 시작 후보:
 * (community_id, visibility, created_at, id) · (community_id, visibility, category_id, created_at, id)
 */
public final class ItemSpecifications {

    private ItemSpecifications() {
    }

    public static Specification<Item> community(long communityId) {
        return (root, q, cb) -> cb.equal(root.get("communityId"), communityId);
    }

    /** 공개 물건만. 숨김·삭제 제외 */
    public static Specification<Item> publicOnly() {
        return (root, q, cb) -> cb.equal(root.get("visibility"), ItemVisibility.PUBLIC);
    }

    public static Specification<Item> category(Long categoryId) {
        return categoryId == null ? null : (root, q, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Item> place(Long placeId) {
        return placeId == null ? null : (root, q, cb) -> cb.equal(root.get("placeId"), placeId);
    }

    /** 제목 부분 검색. MVP는 LIKE로 시작해 측정한다. %·_·\ 는 이스케이프 */
    public static Specification<Item> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim()) + "%";
        return (root, q, cb) -> cb.like(root.<String>get("title"), pattern, '\\');
    }

    /** 대여 가능 기간이 선택 기간 전체를 포함 */
    public static Specification<Item> availableCovers(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        return (root, q, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.<LocalDate>get("availableStartDate"), start),
                cb.greaterThanOrEqualTo(root.<LocalDate>get("availableEndDate"), end));
    }

    public static Specification<Item> ownerNotIn(Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> cb.not(root.get("ownerId").in(ownerIds));
    }

    public static Specification<Item> idNotIn(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> cb.not(root.get("id").in(itemIds));
    }

    /** null 조건은 건너뛰고 AND로 묶는다. (Spring Data JPA 4의 and(null) 비허용 대응) */
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
