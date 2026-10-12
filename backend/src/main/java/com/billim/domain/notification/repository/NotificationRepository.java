package com.billim.domain.notification.repository;

import com.billim.domain.notification.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 모든 조회는 수신자(recipient_id)로 먼저 좁힌다. 타인의 알림은 조회 경로 자체를 만들지 않는다.
 * 커서는 id DESC 기준의 exclusive 값이다. (API 명세 D_048)
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndIdLessThanOrderByIdDesc(
            long recipientId, long cursor, Limit limit);

    List<Notification> findByRecipientIdAndIdLessThanAndReadAtIsNullOrderByIdDesc(
            long recipientId, long cursor, Limit limit);

    List<Notification> findByRecipientIdAndIdLessThanAndReadAtIsNotNullOrderByIdDesc(
            long recipientId, long cursor, Limit limit);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    boolean existsByIdAndRecipientId(long id, long recipientId);

    /**
     * 기준 ID까지 모두 읽음. 기준 ID보다 큰(= 더 최근) 알림은 건드리지 않는다. (API 명세 D_051)
     * 벌크 갱신이라 JPA Auditing이 동작하지 않으므로 updated_at을 함께 지정한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.readAt = :now, n.updatedAt = :now
             WHERE n.recipientId = :recipientId
               AND n.id <= :throughId
               AND n.readAt IS NULL
            """)
    int markReadThrough(@Param("recipientId") long recipientId,
                        @Param("throughId") long throughId,
                        @Param("now") Instant now);
}
