package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.repository.NotificationRepository;
import com.billim.global.json.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사건을 회원별 앱 내 알림으로 바꾸는 소비자.
 * 소비 단위는 수신 회원 한 명({@code notification:{memberId}})이라, 한 수신자 처리가 실패해도
 * 다른 수신자의 알림은 그대로 남는다.
 */
@Component
public class NotificationOutboxConsumer implements OutboxConsumer {

    /** event_deliveries.consumer_key 접두사 (ERD 4.6) */
    public static final String CONSUMER_KEY_PREFIX = "notification:";

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxConsumer.class);

    private final NotificationRepository notificationRepository;
    private final JsonCodec jsonCodec;
    private final NotificationTemplates templates;

    public NotificationOutboxConsumer(NotificationRepository notificationRepository,
                                      JsonCodec jsonCodec, NotificationTemplates templates) {
        this.notificationRepository = notificationRepository;
        this.jsonCodec = jsonCodec;
        this.templates = templates;
    }

    @Override
    public List<String> consumerKeys(OutboxEvent event) {
        return jsonCodec.read(event.getPayload(), EventPayload.class).recipients().stream()
                .map(recipient -> consumerKey(recipient.memberId()))
                .distinct()
                .toList();
    }

    @Override
    public void consume(OutboxEvent event, String consumerKey) {
        long recipientId = memberId(consumerKey);
        EventPayload payload = jsonCodec.read(event.getPayload(), EventPayload.class);

        EventPayload.Recipient recipient = payload.recipients().stream()
                .filter(candidate -> candidate.memberId() == recipientId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "사건에 없는 수신자입니다: eventId=%d consumerKey=%s".formatted(event.getId(), consumerKey)));

        String title = templates.title(event.getEventType(), payload, recipient);
        if (title == null) {
            // 담당 템플릿이 없으면 알림을 만들지 않는다. 사건은 처리 완료로 기록해 재시도하지 않는다
            return;
        }

        // 수신자가 이동 대상을 따로 지정했으면 그것을 쓴다 (신고자는 신고 결과, 제재 대상은 자기 회원 정보)
        notificationRepository.save(Notification.of(
                recipientId,
                event.getId(),
                event.getEventType(),
                title,
                recipient.targetTypeOr(payload.targetType()),
                recipient.targetIdOr(payload.targetId())));

        log.info("알림 생성: eventId={} recipientId={} type={}",
                event.getId(), recipientId, event.getEventType());
    }

    public static String consumerKey(long memberId) {
        return CONSUMER_KEY_PREFIX + memberId;
    }

    private static long memberId(String consumerKey) {
        return Long.parseLong(consumerKey.substring(CONSUMER_KEY_PREFIX.length()));
    }
}
