package com.billim.domain.notification.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * outbox_events.payload의 JSON 변환.
 * 애플리케이션 공용 ObjectMapper와 분리한 전용 매퍼를 쓴다. 공용 설정은 요청 본문 기준이라
 * 모르는 필드를 400으로 막지만, 이미 적재된 사건은 payload 필드가 늘어난 뒤에도 읽을 수 있어야 한다.
 */
@Component
public class EventPayloadCodec {

    // Jackson 3은 날짜를 기본적으로 ISO-8601 문자열로 쓴다. 숫자 timestamp 설정은 따로 하지 않는다
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String write(EventPayload payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("사건 payload를 직렬화할 수 없습니다.", e);
        }
    }

    /** 사건 적재 이후 payload 스키마가 바뀌었을 수 있으므로 모르는 필드는 무시한다 */
    public EventPayload read(String json) {
        try {
            return MAPPER.readValue(json, EventPayload.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("사건 payload를 읽을 수 없습니다.", e);
        }
    }
}
