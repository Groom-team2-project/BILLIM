package com.billim.domain.notification.service;

import com.billim.domain.notification.dto.NotificationPageResponse;
import com.billim.domain.notification.dto.UnreadCountResponse;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.repository.NotificationRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내 알림 조회 (API 명세 D_048, D_049).
 * 조회는 모두 수신자 기준이라 타인의 알림이 섞일 경로가 없다.
 */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    /** 커서를 받지 않은 첫 페이지. id < 이 값은 항상 참이라 조건 분기를 늘리지 않는다 */
    private static final long NO_CURSOR = Long.MAX_VALUE;

    private final NotificationRepository notificationRepository;

    public NotificationQueryService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * @param cursor 직전 페이지의 nextCursor. 없으면 null (최신부터)
     * @param read   읽음 여부 필터. null이면 전체
     */
    public NotificationPageResponse list(long memberId, Long cursor, int limit, Boolean read) {
        long from = cursor == null ? NO_CURSOR : cursor;
        // 다음 페이지 존재 여부를 알려면 한 건을 더 읽어야 한다
        Limit fetch = Limit.of(limit + 1);

        List<Notification> found;
        if (read == null) {
            found = notificationRepository
                    .findByRecipientIdAndIdLessThanOrderByIdDesc(memberId, from, fetch);
        } else if (read) {
            found = notificationRepository
                    .findByRecipientIdAndIdLessThanAndReadAtIsNotNullOrderByIdDesc(memberId, from, fetch);
        } else {
            found = notificationRepository
                    .findByRecipientIdAndIdLessThanAndReadAtIsNullOrderByIdDesc(memberId, from, fetch);
        }
        return NotificationPageResponse.of(found, limit);
    }

    public UnreadCountResponse unreadCount(long memberId) {
        return new UnreadCountResponse(Math.toIntExact(unreadCountOf(memberId)));
    }

    /**
     * 홈 화면 배지 등 다른 도메인에서 쓰는 안 읽은 수.
     * (API 명세 Home.unreadNotificationCount)
     */
    public long unreadCountOf(long memberId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(memberId);
    }
}
