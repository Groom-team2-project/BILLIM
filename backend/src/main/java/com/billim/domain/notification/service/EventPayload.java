package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.NotificationTargetType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * outbox_events.payload 스키마 — 수신 회원, 이동 대상, 최소 표시 정보.
 * 인증 비밀값·개인 좌표·집 주소는 담지 않는다. (ERD 4.6, 기획서 E 규칙)
 *
 * <p>필드를 추가해도 이전 사건을 읽을 수 있어야 하므로 역직렬화는 모르는 필드를 무시한다.
 * ({@link EventPayloadCodec})
 *
 * @param recipients 알림을 받을 회원과 역할. 행동한 본인은 발행 측에서 제외한다
 * @param targetType 알림을 눌렀을 때 이동할 대상 종류
 * @param targetId   targetType 기준 논리 참조 ID
 * @param display    문구 조립에 쓰는 표시 정보. 없는 값은 null
 */
public record EventPayload(
        List<Recipient> recipients,
        NotificationTargetType targetType,
        long targetId,
        Display display
) {

    public EventPayload {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        display = display == null ? Display.empty() : display;
    }

    /** 대여 사건의 기본형. 이동 대상은 거래 상세 */
    public static EventPayload forRental(long rentalId, List<Recipient> recipients, Display display) {
        return new EventPayload(recipients, NotificationTargetType.RENTAL, rentalId, display);
    }

    public record Recipient(long memberId, RecipientRole role) {
    }

    /**
     * 사건 발생 시점의 표시 정보 스냅샷.
     * 알림 문구는 생성 당시 값을 유지하므로 나중에 원본이 바뀌어도 다시 조회하지 않는다.
     */
    public record Display(
            String actorName,
            String itemTitle,
            LocalDate startDate,
            LocalDate endDate,
            String placeName,
            Instant appointmentAt
    ) {

        public static Display empty() {
            return new Display(null, null, null, null, null, null);
        }

        /** 대여 상태 변경 알림에 필요한 최소 정보 */
        public static Display of(String actorName, String itemTitle) {
            return new Display(actorName, itemTitle, null, null, null, null);
        }

        public static Display ofRental(String actorName, String itemTitle,
                                       LocalDate startDate, LocalDate endDate) {
            return new Display(actorName, itemTitle, startDate, endDate, null, null);
        }

        public static Display ofAppointment(String actorName, String itemTitle,
                                            String placeName, Instant appointmentAt) {
            return new Display(actorName, itemTitle, null, null, placeName, appointmentAt);
        }
    }
}
