package com.billim.domain.safety.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * 정책·안내 문서 키 (API 명세 PolicyPage.key).
 * 문서 본문은 {@code src/main/resources/policies/{key}.md}에 둔다. DB 편집 기능은 범위 밖.
 *
 * <p>약관·개인정보·고객센터는 별도 작성·검토가 끝난 뒤 파일을 추가한다.
 * 파일이 없으면 404로 응답한다. (API 명세 E_070)
 */
public enum PolicyKey {
    COMMUNITY,
    SAFETY,
    PROHIBITED,
    DAMAGE,
    DISPUTES,
    TRUST,
    TERMS,
    PRIVACY,
    SUPPORT,
    ABOUT;

    /** API는 소문자 키를 쓴다 */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 정의되지 않은 키는 빈 Optional. 호출 측이 404로 처리한다 */
    public static Optional<PolicyKey> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
