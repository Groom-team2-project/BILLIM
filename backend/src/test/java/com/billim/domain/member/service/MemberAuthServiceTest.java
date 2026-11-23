package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 제공자 닉네임이 표시 이름 규격(2~30자)을 벗어나도 가입이 실패하지 않는지 검증 */
class MemberAuthServiceTest {

    @Test
    @DisplayName("규격 내 닉네임은 그대로 사용")
    void keepsValidNickname() {
        assertThat(MemberAuthService.normalizeDisplayName("홍길동")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("앞뒤 공백 제거")
    void stripsSurroundingSpaces() {
        assertThat(MemberAuthService.normalizeDisplayName("  홍길동  ")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("경계값 2자·30자는 그대로 사용")
    void keepsBoundaryLengths() {
        String min = "가".repeat(Member.DISPLAY_NAME_MIN);
        String max = "가".repeat(Member.DISPLAY_NAME_MAX);
        assertThat(MemberAuthService.normalizeDisplayName(min)).isEqualTo(min);
        assertThat(MemberAuthService.normalizeDisplayName(max)).isEqualTo(max);
    }

    @Test
    @DisplayName("최대 길이 초과는 앞에서 잘라서 사용")
    void truncatesTooLongNickname() {
        String tooLong = "가".repeat(Member.DISPLAY_NAME_MAX + 1);
        assertThat(MemberAuthService.normalizeDisplayName(tooLong))
                .isEqualTo("가".repeat(Member.DISPLAY_NAME_MAX));
    }

    @Test
    @DisplayName("경계에 걸친 이모지는 쪼개지 않고 통째로 제외")
    void doesNotSplitSurrogatePair() {
        // 29자 + 이모지(2 char) = 31 char. 30 경계에서 이모지 분리 발생
        String withEmoji = "가".repeat(Member.DISPLAY_NAME_MAX - 1) + "😀";
        String normalized = MemberAuthService.normalizeDisplayName(withEmoji);

        assertThat(normalized).isEqualTo("가".repeat(Member.DISPLAY_NAME_MAX - 1));
        assertThat(normalized.codePoints().allMatch(Character::isDefined)).isTrue();
    }

    @Test
    @DisplayName("자른 뒤 공백만 남으면 대체 이름으로 가입")
    void replacesWhenTruncationLeavesBlank() {
        String spaced = "가" + " ".repeat(Member.DISPLAY_NAME_MAX * 2) + "나";
        assertThat(MemberAuthService.normalizeDisplayName(spaced))
                .hasSizeBetween(Member.DISPLAY_NAME_MIN, Member.DISPLAY_NAME_MAX);
    }

    @Test
    @DisplayName("1자 닉네임은 대체 이름으로 가입")
    void replacesTooShortNickname() {
        assertThat(MemberAuthService.normalizeDisplayName("김"))
                .isNotEqualTo("김")
                .hasSizeBetween(Member.DISPLAY_NAME_MIN, Member.DISPLAY_NAME_MAX);
    }

    @Test
    @DisplayName("닉네임 미동의·공백이면 대체 이름으로 가입")
    void replacesMissingNickname() {
        for (String nickname : new String[] {null, "", "   "}) {
            assertThat(MemberAuthService.normalizeDisplayName(nickname))
                    .hasSizeBetween(Member.DISPLAY_NAME_MIN, Member.DISPLAY_NAME_MAX);
        }
    }

    @Test
    @DisplayName("보정 결과는 항상 Member가 받아들인다")
    void normalizedNameIsAlwaysAccepted() {
        String[] nicknames = {
                null, "", "   ", "김", "홍길동",
                "가".repeat(Member.DISPLAY_NAME_MAX + 50),
                "가".repeat(Member.DISPLAY_NAME_MAX - 1) + "😀",
                "가" + " ".repeat(Member.DISPLAY_NAME_MAX * 2) + "나",
        };
        for (String nickname : nicknames) {
            String normalized = MemberAuthService.normalizeDisplayName(nickname);
            assertThatCode(() -> Member.register(normalized)).doesNotThrowAnyException();
        }
    }

    /** 거부하면 해당 닉네임 사용자는 가입 불가 — 제거 후 사용 */
    @Test
    @DisplayName("닉네임의 보이지 않는 문자는 제거하고 사용")
    void stripsInvisibleCharacters() {
        assertThat(MemberAuthService.normalizeDisplayName("홍\u200B길동")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("보이지 않는 문자만인 닉네임은 기본 이름으로 대체")
    void replacesInvisibleOnlyNickname() {
        assertThat(MemberAuthService.normalizeDisplayName("\u200B\u200B\u200B")).matches("이웃\\d{4}");
    }

    @Test
    @DisplayName("보이는 글자 없는 닉네임은 기본 이름으로 대체")
    void replacesNicknameWithoutVisibleCharacters() {
        assertThat(MemberAuthService.normalizeDisplayName("\uFE0F\uFE0F")).matches("이웃\\d{4}");
        assertThat(MemberAuthService.normalizeDisplayName("\u3164\u3164")).matches("이웃\\d{4}");
    }

    @Test
    @DisplayName("사칭 닉네임은 기본 이름으로 대체")
    void replacesReservedNickname() {
        String name = MemberAuthService.normalizeDisplayName("관리자");

        assertThat(name).matches("이웃\\d{4}");
        assertThatCode(() -> Member.register(name)).doesNotThrowAnyException();
    }
}
