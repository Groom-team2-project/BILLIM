package com.billim.domain.notification.repository;

import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByEventKey(String eventKey);

    /** 상태별 적재량. 운영 지표(미처리·실패 사건)로 노출한다 */
    long countByStatus(OutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") long id);

    /**
     * 처리할 사건 ID를 점유한다. 소비 대기(PENDING)와 점유가 만료된 사건(PROCESSING)을 함께 가져온다.
     * SKIP LOCKED로 다른 작업자가 잡고 있는 행은 건너뛰어 여러 인스턴스가 같은 사건을 중복 처리하지 않는다.
     * LIMIT·SKIP LOCKED를 함께 쓰려면 네이티브 쿼리가 필요하다.
     */
    @Query(value = """
            SELECT id
            FROM outbox_events
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'PROCESSING' AND (lease_until IS NULL OR lease_until < :now))
            ORDER BY available_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockClaimableIds(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
