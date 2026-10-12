package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.springframework.stereotype.Component;

/**
 * 신고 처리 알림 문구.
 * 제재 대상에게는 조치 종류만 알리고 신고자 정보·관리자가 쓴 사유 원문은 담지 않는다.
 * (API 명세 12.6, ERD 4.6 — "제재 대상에게는 최소 사유만 제공, 신고자 정보 비공개")
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
            return sanctionedTitle(payload.display().label());
        }
        return "접수한 신고의 처리 결과가 등록됐어요";
    }

    /**
     * 조치 라벨은 발행 측이 정해 둔 짧은 문구다(예: '7일 이용 정지').
     * 자세한 사유는 알림에 담지 않고 신고 결과 화면에서 확인하게 한다.
     */
    private String sanctionedTitle(String label) {
        if (label == null || label.isBlank()) {
            return "커뮤니티 규칙 위반으로 운영 조치가 적용됐어요. 자세한 내용은 내 프로필에서 확인해 주세요";
        }
        return "커뮤니티 규칙 위반으로 %s 조치가 적용됐어요. 자세한 내용은 내 프로필에서 확인해 주세요"
                .formatted(label);
    }
}
