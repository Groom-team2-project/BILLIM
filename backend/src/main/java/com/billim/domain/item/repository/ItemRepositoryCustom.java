package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

// 지도 집계용 조회 (API 명세 B_018)
public interface ItemRepositoryCustom {

    // 조건에 맞는 물건 수를 거래 장소별로 집계 (place_id → 개수)
    Map<Long, Long> countByPlace(Specification<Item> spec);
}
