package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 전달·반납 약속 알림 문구. 약속만 시·분을 가지므로 KST 기준으로 표시한다. (정책 및 상태 3)
 */
@Component
public class AppointmentNotificationTemplate implements NotificationTemplate {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("M/d(E) a h:mm", Locale.KOREAN);
    private static final String SOMEONE = "이웃";

    @Override
    public boolean supports(EventType eventType) {
        return switch (eventType) {
            case APPOINTMENT_PROPOSED, APPOINTMENT_CONFIRMED, APPOINTMENT_WITHDRAWN -> true;
            default -> false;
        };
    }

    @Override
    public String title(EventType eventType, EventPayload payload, EventPayload.Recipient recipient) {
        EventPayload.Display display = payload.display();
        String actor = display.actorName() == null || display.actorName().isBlank()
                ? SOMEONE : display.actorName();
        String when = formatWhen(display.appointmentAt());
        return switch (eventType) {
            case APPOINTMENT_PROPOSED -> "%s 님이 약속을 제안했어요%s".formatted(actor, when);
            case APPOINTMENT_CONFIRMED -> "약속이 확정됐어요%s".formatted(when);
            case APPOINTMENT_WITHDRAWN -> "%s 님이 약속 제안을 철회했어요".formatted(actor);
            default -> throw new IllegalArgumentException("약속 템플릿이 담당하지 않는 사건: " + eventType);
        };
    }

    private String formatWhen(Instant appointmentAt) {
        return appointmentAt == null ? "" : " · " + WHEN.format(appointmentAt.atZone(KST));
    }
}
