package com.billim.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 네이버 응답은 response 객체에 중첩 — 추출 실수는 전원 신규 가입으로 이어지므로 고정 */
class SocialOAuth2UserServiceTest {

    @Test
    @DisplayName("네이버 식별자와 닉네임은 response 안에서 꺼낸다")
    void extractsNaverAttributesFromResponse() {
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "message", "success",
                "response", Map.of("id", "naver-subject-1", "nickname", "홍길동"));

        assertThat(SocialOAuth2UserService.naverAttribute(attributes, "id")).isEqualTo("naver-subject-1");
        assertThat(SocialOAuth2UserService.naverAttribute(attributes, "nickname")).isEqualTo("홍길동");
    }

    /** 닉네임 동의 항목 미설정 대비 — 부재 시 null, 보정은 MemberAuthService 담당 */
    @Test
    @DisplayName("response가 없거나 항목이 빠지면 null")
    void returnsNullWhenAttributeMissing() {
        assertThat(SocialOAuth2UserService.naverAttribute(Map.of(), "id")).isNull();
        assertThat(SocialOAuth2UserService.naverAttribute(
                Map.of("response", Map.of("id", "only-id")), "nickname")).isNull();
    }
}
