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
 * @param targetType 알림을 눌렀을 때 이동할 대상 종류. 수신자가 따로 지정하지 않으면 이 값을 쓴다
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

    /**
     * 신고 처리 사건. 신고자는 신고 결과로, 제재 대상은 자기 회원 정보로 이동하므로
     * 수신자마다 이동 대상이 다르다. 기본값은 신고 결과로 두고 수신자가 필요하면 덮어쓴다. (ERD 4.6)
     */
    public static EventPayload forReport(long reportId, List<Recipient> recipients, Display display) {
        return new EventPayload(recipients, NotificationTargetType.REPORT, reportId, display);
    }

    /**
     * 알림을 받을 회원.
     *
     * @param targetType 이 수신자만의 이동 대상 종류. null이면 payload의 값을 쓴다
     * @param targetId   이 수신자만의 이동 대상 ID. null이면 payload의 값을 쓴다
     */
    public record Recipient(
            long memberId,
            RecipientRole role,
            NotificationTargetType targetType,
            Long targetId
    ) {

        /** 이동 대상은 payload의 값을 따른다 */
        public static Recipient of(long memberId, RecipientRole role) {
            return new Recipient(memberId, role, null, null);
        }

        /** 이 수신자만 다른 곳으로 이동시킨다 */
        public static Recipient movingTo(long memberId, RecipientRole role,
                                         NotificationTargetType targetType, long targetId) {
            return new Recipient(memberId, role, targetType, targetId);
        }

        public NotificationTargetType targetTypeOr(NotificationTargetType fallback) {
            return targetType == null ? fallback : targetType;
        }

        public long targetIdOr(long fallback) {
            return targetId == null ? fallback : targetId;
        }
    }

    /**
     * 사건 발생 시점의 표시 정보 스냅샷.
     * 알림 문구는 생성 당시 값을 유지하므로 나중에 원본이 바뀌어도 다시 조회하지 않는다.
     *
     * @param label 사건별 짧은 표시 라벨. 예: 제재 종류('7일 이용 정지'). 자유 입력 사유는 담지 않는다
     */
    public record Display(
            String actorName,
            String itemTitle,
            LocalDate startDate,
            LocalDate endDate,
            String placeName,
            Instant appointmentAt,
            String label
    ) {

        public static Display empty() {
            return new Display(null, null, null, null, null, null, null);
        }

        /** 대여 상태 변경 알림에 필요한 최소 정보 */
        public static Display of(String actorName, String itemTitle) {
            return new Display(actorName, itemTitle, null, null, null, null, null);
        }

        public static Display ofRental(String actorName, String itemTitle,
                                       LocalDate startDate, LocalDate endDate) {
            return new Display(actorName, itemTitle, startDate, endDate, null, null, null);
        }

        public static Display ofAppointment(String actorName, String itemTitle,
                                            String placeName, Instant appointmentAt) {
            return new Display(actorName, itemTitle, null, null, placeName, appointmentAt, null);
        }

        /** 라벨만 쓰는 사건(신고 처리 등). 대상·신고자 정보는 담지 않는다 */
        public static Display ofLabel(String label) {
            return new Display(null, null, null, null, null, null, label);
        }
    }
}
