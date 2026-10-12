package com.billim.domain.notification.repository;

import com.billim.domain.notification.entity.EventDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDeliveryRepository extends JpaRepository<EventDelivery, Long> {

    /** 이 소비 단위를 이미 처리했는지. 재소비 시 결과를 다시 만들지 않는 기준 */
    boolean existsByEventIdAndConsumerKey(long eventId, String consumerKey);
}
