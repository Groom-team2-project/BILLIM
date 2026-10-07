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
        String[] nicknames = {null, "", "   ", "김", "홍길동", "가".repeat(Member.DISPLAY_NAME_MAX + 50)};
        for (String nickname : nicknames) {
            String normalized = MemberAuthService.normalizeDisplayName(nickname);
            assertThatCode(() -> Member.register(normalized)).doesNotThrowAnyException();
        }
    }
}
