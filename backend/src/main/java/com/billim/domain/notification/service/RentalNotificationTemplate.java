package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 대여 상태 변경 알림 문구. 프로토타입 '알림' 화면(E · 알림 · 운영)의 문구를 기준으로 한다.
 * 수신자 역할에 따라 행동한 사람을 주어로 쓴다. (요청은 소유자에게, 승인·거절은 요청자에게)
 */
@Component
public class RentalNotificationTemplate implements NotificationTemplate {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    private static final String SOMEONE = "이웃";

    @Override
    public boolean supports(EventType eventType) {
        return switch (eventType) {
            case RENTAL_REQUESTED, RENTAL_APPROVED, RENTAL_REJECTED, RENTAL_CANCELED,
                 RENTAL_HANDED_OVER, RENTAL_RETURNED, RENTAL_EXPIRED -> true;
            default -> false;
        };
    }

    @Override
    public String title(EventType eventType, EventPayload payload, EventPayload.Recipient recipient) {
        String actor = actorName(payload);
        String item = itemTitle(payload);
        return switch (eventType) {
            case RENTAL_REQUESTED -> "%s 님이 %s 대여를 요청했어요".formatted(actor, item);
            case RENTAL_APPROVED -> "%s 님이 %s 대여 요청을 승인했어요".formatted(actor, item);
            case RENTAL_REJECTED -> "%s 님이 %s 대여 요청을 거절했어요".formatted(actor, item);
            case RENTAL_CANCELED -> "%s 님이 %s 대여 요청을 취소했어요".formatted(actor, item);
            case RENTAL_HANDED_OVER -> "%s 전달이 확인됐어요".formatted(item);
            case RENTAL_RETURNED -> "%s 님이 %s 반납을 확인했어요".formatted(actor, item);
            // 시스템 전이. 행동한 사람이 없어 역할별로 다르게 안내한다
            case RENTAL_EXPIRED -> expiredTitle(payload, recipient, item);
            default -> throw new IllegalArgumentException("대여 템플릿이 담당하지 않는 사건: " + eventType);
        };
    }

    private String expiredTitle(EventPayload payload, EventPayload.Recipient recipient, String item) {
        String period = period(payload);
        if (recipient.role() == RecipientRole.OWNER) {
            return "%s 대여 요청이 응답 없이 마감됐어요%s".formatted(item, period);
        }
        return "%s 대여 요청이 마감됐어요. 다른 날짜로 다시 요청할 수 있어요".formatted(item);
    }

    private String period(EventPayload payload) {
        EventPayload.Display display = payload.display();
        if (display.startDate() == null || display.endDate() == null) {
            return "";
        }
        return " (%s – %s)".formatted(display.startDate().format(DATE), display.endDate().format(DATE));
    }

    /** 표시 정보가 비어 있어도 알림은 만든다. 이름·제목이 없으면 중립 문구로 채운다 */
    private String actorName(EventPayload payload) {
        String name = payload.display().actorName();
        return name == null || name.isBlank() ? SOMEONE : name;
    }

    private String itemTitle(EventPayload payload) {
        String title = payload.display().itemTitle();
        return title == null || title.isBlank() ? "물건" : title;
    }
}
