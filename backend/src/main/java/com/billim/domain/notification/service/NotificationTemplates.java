package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 등록된 {@link NotificationTemplate} 중 사건을 담당하는 것을 찾는다.
 * 먼저 등록된 템플릿이 이긴다. 담당 템플릿이 없으면 알림을 만들지 않고 건너뛴다.
 */
@Component
public class NotificationTemplates {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplates.class);

    private final List<NotificationTemplate> templates;

    public NotificationTemplates(List<NotificationTemplate> templates) {
        this.templates = List.copyOf(templates);
    }

    /**
     * 수신자에게 보여줄 문구. 담당 템플릿이 없거나 문구를 만들 수 없으면 빈 Optional 대신 null을 돌려
     * 호출 측이 "이 수신자에게는 알림 없음"으로 처리하게 한다.
     */
    public String title(EventType eventType, EventPayload payload, EventPayload.Recipient recipient) {
        for (NotificationTemplate template : templates) {
            if (template.supports(eventType)) {
                return template.title(eventType, payload, recipient);
            }
        }
        // 사건 유형을 추가하고 템플릿을 깜빡한 경우. 소비를 실패시키지 않고 경고만 남긴다
        log.warn("알림 문구 템플릿이 없는 사건 유형: {}", eventType);
        return null;
    }
}
