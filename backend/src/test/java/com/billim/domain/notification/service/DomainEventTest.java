package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.NotificationTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 사건 자연 키 조립과 입력 검증 (ERD 4.6 event_key) */
class DomainEventTest {

    private static final EventPayload PAYLOAD = EventPayload.forRental(
            501L,
            List.of(EventPayload.Recipient.of(7L, RecipientRole.BORROWER)),
            EventPayload.Display.of("정우", "전동드릴"));

    @Test
    @DisplayName("event_key는 집합체·ID·version·동작으로 조립한다")
    void eventKeyIsBuiltFromAggregateAndVersion() {
        DomainEvent event = DomainEvent.of(EventType.RENTAL_APPROVED, 501L, 2L, PAYLOAD);

        assertThat(event.eventKey()).isEqualTo("rental:501:version:2:approved");
    }

    @Test
    @DisplayName("약속·신고 사건도 집합체 종류에 맞는 키를 만든다")
    void eventKeyCoversOtherAggregates() {
        assertThat(DomainEvent.of(EventType.APPOINTMENT_PROPOSED, 9L, 0L, PAYLOAD).eventKey())
                .isEqualTo("appointment_slot:9:version:0:proposed");
        assertThat(DomainEvent.of(EventType.REPORT_RESOLVED, 3L, 1L, PAYLOAD).eventKey())
                .isEqualTo("report:3:version:1:resolved");
    }

    @Test
    @DisplayName("같은 집합체의 같은 version·동작은 같은 키가 되어 중복 발행을 막는다")
    void sameStateChangeProducesSameKey() {
        String first = DomainEvent.of(EventType.RENTAL_APPROVED, 501L, 2L, PAYLOAD).eventKey();
        String second = DomainEvent.of(EventType.RENTAL_APPROVED, 501L, 2L, PAYLOAD).eventKey();

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("집합체 ID가 없거나 사건 유형이 비면 발행 요청을 만들 수 없다")
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> DomainEvent.of(EventType.RENTAL_APPROVED, 0L, 2L, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEvent("rental:1:version:0:approved", null, 1L, 0L, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEvent(" ", EventType.RENTAL_APPROVED, 1L, 0L, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("payload는 수신자 목록과 이동 대상을 담는다")
    void payloadCarriesRecipientsAndTarget() {
        assertThat(PAYLOAD.targetType()).isEqualTo(NotificationTargetType.RENTAL);
        assertThat(PAYLOAD.targetId()).isEqualTo(501L);
        assertThat(PAYLOAD.recipients()).hasSize(1);
    }

    @Test
    @DisplayName("수신자와 표시 정보를 생략해도 payload는 빈 값으로 정규화된다")
    void payloadNormalizesNulls() {
        EventPayload payload = new EventPayload(null, NotificationTargetType.RENTAL, 1L, null);

        assertThat(payload.recipients()).isEmpty();
        assertThat(payload.display()).isEqualTo(EventPayload.Display.empty());
    }
}
