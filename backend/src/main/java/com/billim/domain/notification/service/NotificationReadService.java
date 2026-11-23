package com.billim.domain.notification.service;

import com.billim.domain.notification.dto.NotificationResponse;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.repository.NotificationRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 알림 읽음 처리 (API 명세 D_050, D_051).
 * 변경 없는 반복 호출에 안전하다 (명세 0.3).
 */
@Service
@Transactional(readOnly = true)
public class NotificationReadService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationReadService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** 본인 알림만. 타인 알림은 존재를 알리지 않고 404 */
    @Transactional
    public NotificationResponse markRead(long memberId, long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(found -> found.isReceivedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 이미 읽었으면 최초 readAt을 유지한다
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    /**
     * 기준 ID까지만 읽음 처리한다. 조회 이후 도착한 더 큰 ID의 알림은 미열람으로 남는다.
     *
     * @return 이번 호출로 읽음 처리한 건수
     */
    @Transactional
    public int markAllRead(long memberId, long throughId) {
        if (!notificationRepository.existsByIdAndRecipientId(throughId, memberId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return notificationRepository.markReadThrough(memberId, throughId, clock.instant());
    }
}
