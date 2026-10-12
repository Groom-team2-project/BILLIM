package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사건 → 문구. 프로토타입 '알림' 화면(E · 알림 · 운영)의 문구를 기준으로 한다.
 * 수신자 역할에 따라 문구가 달라지는지, 템플릿이 없는 사건을 어떻게 다루는지 확인한다.
 */
class NotificationTemplatesTest {

    private final NotificationTemplates templates = new NotificationTemplates(List.of(
            new RentalNotificationTemplate(),
            new AppointmentNotificationTemplate(),
            new ReportNotificationTemplate()));

    private static final EventPayload.Recipient OWNER =
            EventPayload.Recipient.of(1L, RecipientRole.OWNER);
    private static final EventPayload.Recipient BORROWER =
            EventPayload.Recipient.of(2L, RecipientRole.BORROWER);

    private EventPayload rental(EventPayload.Display display) {
        return EventPayload.forRental(501L, List.of(OWNER, BORROWER), display);
    }

    @Test
    @DisplayName("대여 요청·승인·거절·취소 문구에 상대 이름과 물건 제목이 들어간다")
    void rentalTitlesUseActorAndItem() {
        EventPayload payload = rental(EventPayload.Display.of("정우", "전동드릴"));

        assertThat(templates.title(EventType.RENTAL_REQUESTED, payload, OWNER))
                .isEqualTo("정우 님이 전동드릴 대여를 요청했어요");
        assertThat(templates.title(EventType.RENTAL_APPROVED, payload, BORROWER))
                .isEqualTo("정우 님이 전동드릴 대여 요청을 승인했어요");
        assertThat(templates.title(EventType.RENTAL_REJECTED, payload, BORROWER))
                .isEqualTo("정우 님이 전동드릴 대여 요청을 거절했어요");
        assertThat(templates.title(EventType.RENTAL_CANCELED, payload, OWNER))
                .isEqualTo("정우 님이 전동드릴 대여 요청을 취소했어요");
    }

    @Test
    @DisplayName("요청 마감은 행동한 사람이 없어 역할별로 다르게 안내한다")
    void expiredTitleDependsOnRole() {
        EventPayload payload = rental(EventPayload.Display.ofRental(
                null, "아이스박스 25L", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)));

        assertThat(templates.title(EventType.RENTAL_EXPIRED, payload, OWNER))
                .isEqualTo("아이스박스 25L 대여 요청이 응답 없이 마감됐어요 (10/12 – 10/14)");
        assertThat(templates.title(EventType.RENTAL_EXPIRED, payload, BORROWER))
                .isEqualTo("아이스박스 25L 대여 요청이 마감됐어요. 다른 날짜로 다시 요청할 수 있어요");
    }

    @Test
    @DisplayName("표시 정보가 비어 있어도 중립 문구로 알림을 만든다")
    void missingDisplayFallsBackToNeutralWording() {
        EventPayload payload = rental(EventPayload.Display.empty());

        assertThat(templates.title(EventType.RENTAL_APPROVED, payload, BORROWER))
                .isEqualTo("이웃 님이 물건 대여 요청을 승인했어요");
    }

    @Test
    @DisplayName("약속 알림은 KST 시·분까지 보여준다")
    void appointmentTitleShowsKstTime() {
        EventPayload payload = EventPayload.forRental(501L, List.of(BORROWER),
                EventPayload.Display.ofAppointment("정우", "전동드릴", "관리동 무인택배함",
                        Instant.parse("2026-09-26T01:00:00Z")));

        assertThat(templates.title(EventType.APPOINTMENT_PROPOSED, payload, BORROWER))
                .isEqualTo("정우 님이 약속을 제안했어요 · 9/26(토) 오전 10:00");
    }

    @Test
    @DisplayName("제재 대상에게는 조치 종류만 알린다")
    void sanctionedRecipientSeesActionLabel() {
        EventPayload.Recipient sanctioned = EventPayload.Recipient.of(9L, RecipientRole.SANCTIONED);
        EventPayload payload = EventPayload.forReport(3L, List.of(sanctioned),
                EventPayload.Display.ofLabel("7일 이용 정지"));

        String title = templates.title(EventType.REPORT_RESOLVED, payload, sanctioned);

        assertThat(title).contains("7일 이용 정지");
    }

    @Test
    @DisplayName("신고자와 제재 대상의 문구가 서로의 정보를 담지 않는다")
    void reportTitlesDoNotLeakCounterpartInfo() {
        EventPayload.Recipient reporter = EventPayload.Recipient.of(1L, RecipientRole.REPORTER);
        EventPayload.Recipient sanctioned = EventPayload.Recipient.of(9L, RecipientRole.SANCTIONED);
        // 발행 측이 자유 입력 사유를 담지 않는 것이 계약이지만, 담겼더라도 문구에 새지 않아야 한다
        EventPayload payload = EventPayload.forReport(3L, List.of(reporter, sanctioned),
                new EventPayload.Display("신고한 이웃", "전동드릴", null, null, null, null, "경고"));

        String reporterTitle = templates.title(EventType.REPORT_RESOLVED, payload, reporter);
        String sanctionedTitle = templates.title(EventType.REPORT_RESOLVED, payload, sanctioned);

        assertThat(reporterTitle).doesNotContain("신고한 이웃").doesNotContain("경고");
        assertThat(sanctionedTitle).doesNotContain("신고한 이웃").doesNotContain("전동드릴");
    }

    @Test
    @DisplayName("조치 라벨이 없어도 제재 대상에게 알릴 문구는 만든다")
    void sanctionedTitleFallsBackWithoutLabel() {
        EventPayload.Recipient sanctioned = EventPayload.Recipient.of(9L, RecipientRole.SANCTIONED);
        EventPayload payload = EventPayload.forReport(3L, List.of(sanctioned),
                EventPayload.Display.empty());

        assertThat(templates.title(EventType.REPORT_RESOLVED, payload, sanctioned)).isNotBlank();
    }

    @Test
    @DisplayName("담당 템플릿이 없으면 문구를 만들지 않고 null을 돌려 알림을 건너뛴다")
    void missingTemplateReturnsNull() {
        NotificationTemplates empty = new NotificationTemplates(List.of());

        assertThat(empty.title(EventType.RENTAL_APPROVED,
                rental(EventPayload.Display.of("정우", "전동드릴")), OWNER)).isNull();
    }
}
