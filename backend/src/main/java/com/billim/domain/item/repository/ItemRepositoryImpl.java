package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.LinkedHashMap;
import java.util.Map;

// ItemRepositoryCustom 구현. 검색과 같은 Specification으로 GROUP BY place_id (Criteria API)
class ItemRepositoryImpl implements ItemRepositoryCustom {

    private final EntityManager em;

    ItemRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Map<Long, Long> countByPlace(Specification<Item> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Item> root = query.from(Item.class);
        Expression<Long> placeId = root.get("placeId");
        Predicate where = spec.toPredicate(root, query, cb);
        query.multiselect(placeId, cb.count(root));
        if (where != null) {
            query.where(where);
        }
        query.groupBy(placeId);

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Tuple t : em.createQuery(query).getResultList()) {
            counts.put(t.get(0, Long.class), t.get(1, Long.class));
        }
        return counts;
    }
}
