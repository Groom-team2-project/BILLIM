package com.billim.global.json;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 컬럼(outbox_events.payload, reports.target_snapshot)의 변환.
 *
 * <p>애플리케이션 공용 ObjectMapper와 분리한 전용 매퍼를 쓴다. 공용 설정은 요청 본문 기준이라
 * 선언하지 않은 필드를 400으로 막지만(명세 0.1), 이미 저장된 JSON은 스키마에 필드가 늘어난 뒤에도
 * 읽을 수 있어야 한다. 그래서 읽기는 모르는 필드를 무시한다.
 */
@Component
public class JsonCodec {

    // Jackson 3은 날짜를 기본적으로 ISO-8601 문자열로 쓴다. 숫자 timestamp 설정은 따로 하지 않는다
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON으로 직렬화할 수 없습니다: " + value.getClass().getSimpleName(), e);
        }
    }

    /** 저장 이후 스키마가 바뀌었을 수 있으므로 모르는 필드는 무시한다 */
    public <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 JSON을 읽을 수 없습니다: " + type.getSimpleName(), e);
        }
    }
}
