package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.NotificationTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** payload JSON 변환. 사건은 오래 남으므로 스키마가 늘어난 뒤에도 읽혀야 한다 */
class EventPayloadCodecTest {

    private final EventPayloadCodec codec = new EventPayloadCodec();

    @Test
    @DisplayName("수신자·대상·표시 정보가 왕복해도 그대로 유지된다")
    void roundTripKeepsValues() {
        EventPayload payload = EventPayload.forRental(
                501L,
                List.of(new EventPayload.Recipient(7L, RecipientRole.OWNER),
                        new EventPayload.Recipient(8L, RecipientRole.BORROWER)),
                EventPayload.Display.ofRental("정우", "전동드릴",
                        LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)));

        EventPayload read = codec.read(codec.write(payload));

        assertThat(read).isEqualTo(payload);
    }

    @Test
    @DisplayName("약속 시각 같은 일시도 왕복한다")
    void roundTripKeepsInstant() {
        Instant appointmentAt = Instant.parse("2026-10-12T01:00:00Z");
        EventPayload payload = new EventPayload(
                List.of(new EventPayload.Recipient(7L, RecipientRole.BORROWER)),
                NotificationTargetType.RENTAL, 501L,
                EventPayload.Display.ofAppointment("정우", "전동드릴", "관리동 무인택배함", appointmentAt));

        assertThat(codec.read(codec.write(payload)).display().appointmentAt()).isEqualTo(appointmentAt);
    }

    @Test
    @DisplayName("나중에 추가된 모르는 필드가 있어도 이전 사건을 읽을 수 있다")
    void unknownFieldsAreIgnored() {
        String json = """
                {"recipients":[{"memberId":7,"role":"OWNER"}],
                 "targetType":"RENTAL","targetId":501,
                 "display":{"actorName":"정우","itemTitle":"전동드릴","futureField":"무시"},
                 "addedLater":true}""";

        EventPayload read = codec.read(json);

        assertThat(read.targetId()).isEqualTo(501L);
        assertThat(read.display().actorName()).isEqualTo("정우");
        assertThat(read.recipients().getFirst().role()).isEqualTo(RecipientRole.OWNER);
    }
}
