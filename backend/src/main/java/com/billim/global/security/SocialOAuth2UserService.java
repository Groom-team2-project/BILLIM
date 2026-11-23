package com.billim.global.security;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.SocialProvider;
import com.billim.domain.member.service.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * 소셜 사용자 정보를 회원으로 변환. 제공자별 응답 구조 차이는 여기서만 흡수.
 * 토큰 교환과 사용자 정보 조회는 부모(DefaultOAuth2UserService) 담당.
 * 계정은 제공자별 별개 — 동일인이 카카오·네이버로 각각 로그인하면 서로 다른 회원.
 */
@Service
@RequiredArgsConstructor
public class SocialOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberAuthService memberAuthService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        SocialProvider provider = SocialProvider.valueOf(
                userRequest.getClientRegistration().getRegistrationId().toUpperCase(Locale.ROOT));
        OAuth2User oauthUser = super.loadUser(userRequest);

        String providerSubject = subjectOf(provider, oauthUser);
        String nickname = nicknameOf(provider, oauthUser.getAttributes());

        Member member = loginOrRegister(provider, providerSubject, nickname);
        return new LoginMember(member.getId(), member.getRole());
    }

    /**
     * 동시 첫 로그인 재시도.
     * 두 요청이 같은 제공자 식별자로 동시에 가입하면 UNIQUE 제약이 한쪽을 거부.
     * 거부된 트랜잭션은 롤백 확정이라 같은 트랜잭션에서 재조회가 불가능하므로 새 호출로 재시도.
     */
    private Member loginOrRegister(SocialProvider provider, String providerSubject, String nickname) {
        try {
            return memberAuthService.loginOrRegister(provider, providerSubject, nickname);
        } catch (DataIntegrityViolationException e) {
            return memberAuthService.loginOrRegister(provider, providerSubject, nickname);
        }
    }

    /**
     * 제공자의 불변 식별자(social_accounts.provider_subject).
     * 카카오는 getName()이 회원번호, 네이버는 response에 중첩이라 직접 추출.
     */
    private static String subjectOf(SocialProvider provider, OAuth2User oauthUser) {
        String subject = switch (provider) {
            case KAKAO -> oauthUser.getName();
            case NAVER -> naverAttribute(oauthUser.getAttributes(), "id");
            // 도달 불가 — LOCAL은 OAuth 등록 ID가 없어 valueOf()를 통과하지 못함
            case LOCAL -> throw new IllegalStateException("LOCAL은 OAuth 제공자가 아님");
        };
        if (subject == null || subject.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "invalid_user_info", provider + " 응답에 식별자 없음", null));
        }
        return subject;
    }

    /** 둘 다 없으면 null — 보정은 MemberAuthService 담당 */
    private static String nicknameOf(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case KAKAO -> kakaoNickname(attributes);
            case NAVER -> naverAttribute(attributes, "nickname");
            case LOCAL -> throw new IllegalStateException("LOCAL은 OAuth 제공자가 아님");
        };
    }

    /**
     * 닉네임 위치: kakao_account.profile.nickname (profile_nickname 동의 시).
     * properties.nickname은 구형 응답 대비.
     */
    private static String kakaoNickname(Map<String, Object> attributes) {
        if (attributes.get("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }
        if (attributes.get("properties") instanceof Map<?, ?> properties
                && properties.get("nickname") instanceof String nickname) {
            return nickname;
        }
        return null;
    }

    /** 네이버 사용자 정보 위치: response.{id,nickname} — 동의 항목에 따라 nickname 부재 가능 */
    static String naverAttribute(Map<String, Object> attributes, String key) {
        if (attributes.get("response") instanceof Map<?, ?> response
                && response.get(key) instanceof String value) {
            return value;
        }
        return null;
    }
}
