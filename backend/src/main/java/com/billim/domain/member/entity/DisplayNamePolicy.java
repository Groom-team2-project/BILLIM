package com.billim.domain.member.entity;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 표시 이름의 문자·사칭 규칙. 길이 규칙은 Member 담당.
 *
 * <p>금지 문자: 제어·서식(폭 없는 공백, 방향 제어, ZWJ 포함)·사용자 정의 영역·미할당·짝 없는 서로게이트·줄 구분.
 * 보이지 않는 이름과 표시 순서 뒤집기로 다른 이름 흉내 차단. ZWJ 합성 이모지도 거부되는 비용 감수.
 *
 * <p>한글 채움 문자(U+3164 등)도 금지 — 분류는 글자지만 화면에 보이지 않아 "투명 이름"과 글자 사이 끼우기에 사용.
 *
 * <p>보이는 글자 필수: 공백·결합 부호(이모지 변형 선택자 포함)만으로 된 이름 거부. 이모지 뒤의 변형 선택자는 허용.
 *
 * <p>사칭 이름: 운영 주체로 오인될 단어. 전각·대소문자·공백·기호를 정규화한 뒤 비교 — "ＡＤＭＩＮ", "관 리 자" 우회 차단.
 * 한글 단어는 포함 여부, 영문은 바로 앞이 영문자가 아닐 때 — "공식admin" 차단, "badminton" 허용.
 */
public final class DisplayNamePolicy {

    private static final List<String> RESERVED_KOREAN = List.of("관리자", "운영자", "운영진", "운영팀");
    private static final List<String> RESERVED_LATIN = List.of("admin", "billim");
    /** 한글 채움 문자(초성·중성·호환·반각)와 점자 빈칸 — 보이지 않는 글자 */
    private static final Set<Integer> INVISIBLE_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

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

    /** 공백·결합 부호가 아닌 글자가 하나 이상 있는지 */
    public static boolean hasVisibleChar(String value) {
        return value.codePoints().anyMatch(cp -> !isForbidden(cp) && switch (Character.getType(cp)) {
            case Character.SPACE_SEPARATOR, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK,
                 Character.COMBINING_SPACING_MARK -> false;
            default -> true;
        });
    }

    public static boolean isReserved(String value) {
        String key = comparisonKey(value);
        return RESERVED_KOREAN.stream().anyMatch(key::contains)
                || RESERVED_LATIN.stream().anyMatch(word -> containsAtLatinBoundary(key, word));
    }

    /** 바로 앞이 영문자가 아닌 위치의 출현 — 영문 단어 내부("badminton")는 제외 */
    private static boolean containsAtLatinBoundary(String key, String word) {
        for (int i = key.indexOf(word); i >= 0; i = key.indexOf(word, i + 1)) {
            if (i == 0 || !isLatinLetter(key.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLatinLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isForbidden(int codePoint) {
        if (INVISIBLE_LETTERS.contains(codePoint)) {
            return true;
        }
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
