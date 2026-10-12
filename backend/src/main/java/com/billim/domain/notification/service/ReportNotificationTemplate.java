package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.springframework.stereotype.Component;

/**
 * 신고 처리 알림 문구.
 * 제재 대상에게는 최소 사유만 알리고 신고자 정보를 노출하지 않는다. (API 명세 12.5, ERD 4.6)
 */
@Component
public class ReportNotificationTemplate implements NotificationTemplate {

    @Override
    public boolean supports(EventType eventType) {
        return eventType == EventType.REPORT_RESOLVED;
    }

    @Override
    public String title(EventType eventType, EventPayload payload, EventPayload.Recipient recipient) {
        if (recipient.role() == RecipientRole.SANCTIONED) {
            // 누가 신고했는지, 어떤 거래였는지 담지 않는다
            return "커뮤니티 규칙 위반으로 이용이 제한됐어요. 자세한 내용은 내 프로필에서 확인해 주세요";
        }
        return "접수한 신고의 처리 결과가 등록됐어요";
    }
}
