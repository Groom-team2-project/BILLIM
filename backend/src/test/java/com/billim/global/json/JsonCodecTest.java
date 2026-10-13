package com.billim.global.json;

import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.service.EventPayload;
import com.billim.domain.notification.service.RecipientRole;
import com.billim.domain.safety.service.ReportTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 컬럼 변환. 저장된 JSON은 오래 남으므로 스키마에 필드가 늘어난 뒤에도 읽혀야 한다.
 * (outbox_events.payload, reports.target_snapshot)
 */
class JsonCodecTest {

    private final JsonCodec codec = new JsonCodec();

    @Test
    @DisplayName("사건 payload의 수신자·대상·표시 정보가 왕복해도 그대로 유지된다")
    void eventPayloadRoundTripKeepsValues() {
        EventPayload payload = EventPayload.forRental(
                501L,
                List.of(EventPayload.Recipient.of(7L, RecipientRole.OWNER),
                        EventPayload.Recipient.of(8L, RecipientRole.BORROWER)),
                EventPayload.Display.ofRental("정우", "전동드릴",
                        LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)));

        assertThat(codec.read(codec.write(payload), EventPayload.class)).isEqualTo(payload);
    }

    @Test
    @DisplayName("수신자별 이동 대상도 왕복한다")
    void perRecipientTargetRoundTrips() {
        EventPayload payload = EventPayload.forReport(3L,
                List.of(EventPayload.Recipient.of(1L, RecipientRole.REPORTER),
                        EventPayload.Recipient.movingTo(9L, RecipientRole.SANCTIONED,
                                NotificationTargetType.MEMBER_SELF, 9L)),
                EventPayload.Display.ofLabel("영구 정지"));

        EventPayload read = codec.read(codec.write(payload), EventPayload.class);

        assertThat(read.recipients().getFirst().targetType()).isNull();
        assertThat(read.recipients().getLast().targetType()).isEqualTo(NotificationTargetType.MEMBER_SELF);
        assertThat(read.recipients().getLast().targetId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("약속 시각 같은 일시도 왕복한다")
    void instantRoundTrips() {
        Instant appointmentAt = Instant.parse("2026-10-12T01:00:00Z");
        EventPayload payload = EventPayload.forRental(501L,
                List.of(EventPayload.Recipient.of(7L, RecipientRole.BORROWER)),
                EventPayload.Display.ofAppointment("정우", "전동드릴", "관리동 무인택배함", appointmentAt));

        assertThat(codec.read(codec.write(payload), EventPayload.class).display().appointmentAt())
                .isEqualTo(appointmentAt);
    }

    @Test
    @DisplayName("나중에 추가된 모르는 필드가 있어도 이전에 저장한 JSON을 읽을 수 있다")
    void unknownFieldsAreIgnored() {
        String json = """
                {"recipients":[{"memberId":7,"role":"OWNER"}],
                 "targetType":"RENTAL","targetId":501,
                 "display":{"actorName":"정우","itemTitle":"전동드릴","futureField":"무시"},
                 "addedLater":true}""";

        EventPayload read = codec.read(json, EventPayload.class);

        assertThat(read.targetId()).isEqualTo(501L);
        assertThat(read.display().actorName()).isEqualTo("정우");
        assertThat(read.recipients().getFirst().role()).isEqualTo(RecipientRole.OWNER);
    }

    @Test
    @DisplayName("신고 증거 스냅샷도 같은 규칙으로 왕복한다")
    void reportSnapshotRoundTrips() {
        ReportTarget.ReportSnapshot snapshot =
                ReportTarget.ReportSnapshot.of("전동드릴", "계좌로 대여료 보내주세요");

        assertThat(codec.read(codec.write(snapshot), ReportTarget.ReportSnapshot.class))
                .isEqualTo(snapshot);
    }
}
