package com.billim.domain.member.entity;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * 표시 이름의 문자·사칭 규칙. 길이 규칙은 Member 담당.
 *
 * <p>금지 문자: 제어·서식(폭 없는 공백, 방향 제어, ZWJ 포함)·사용자 정의 영역·미할당·짝 없는 서로게이트·줄 구분.
 * 보이지 않는 이름과 표시 순서 뒤집기로 다른 이름 흉내 차단. ZWJ 합성 이모지도 거부되는 비용 감수.
 *
 * <p>사칭 이름: 운영 주체로 오인될 단어. 전각·대소문자·공백·기호를 정규화한 뒤 비교 — "ＡＤＭＩＮ", "관 리 자" 우회 차단.
 * 한글 단어는 포함 여부, 영문은 시작 일치 — "badminton" 같은 일반 단어 오탐 방지.
 */
public final class DisplayNamePolicy {

    private static final List<String> RESERVED_KOREAN = List.of("관리자", "운영자", "운영진", "운영팀");
    private static final List<String> RESERVED_PREFIXES = List.of("admin", "billim");

    private DisplayNamePolicy() {
    }

    public static boolean hasForbiddenChar(String value) {
        return value.codePoints().anyMatch(DisplayNamePolicy::isForbidden);
    }

    /** 소셜 닉네임 보정용 — 거부 대신 금지 문자 제거 */
    public static String stripForbidden(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().filter(cp -> !isForbidden(cp)).forEach(result::appendCodePoint);
        return result.toString();
    }

    public static boolean isReserved(String value) {
        String key = comparisonKey(value);
        return RESERVED_KOREAN.stream().anyMatch(key::contains)
                || RESERVED_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private static boolean isForbidden(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                 Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }

    /** NFKC로 전각·호환 문자 통일 후 글자·숫자만 소문자로 남김 */
    private static String comparisonKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder(normalized.length());
        normalized.codePoints().filter(Character::isLetterOrDigit).forEach(key::appendCodePoint);
        return key.toString();
    }
}
