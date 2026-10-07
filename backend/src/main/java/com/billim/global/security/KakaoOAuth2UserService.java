package com.billim.global.security;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.SocialProvider;
import com.billim.domain.member.service.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 카카오 사용자 정보를 회원으로 변환.
 * 토큰 교환과 사용자 정보 조회는 부모(DefaultOAuth2UserService) 담당.
 */
@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberAuthService memberAuthService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = super.loadUser(userRequest);

        // user-name-attribute: id 설정에 따라 카카오 회원번호
        String providerSubject = kakaoUser.getName();
        String nickname = extractNickname(kakaoUser.getAttributes());

        Member member = loginOrRegister(providerSubject, nickname);
        return new LoginMember(member.getId(), member.getRole());
    }

    /**
     * 동시 첫 로그인 재시도.
     * 두 요청이 같은 제공자 식별자로 동시에 가입하면 UNIQUE 제약이 한쪽을 거부.
     * 거부된 트랜잭션은 롤백 확정이라 같은 트랜잭션에서 재조회가 불가능하므로 새 호출로 재시도.
     */
    private Member loginOrRegister(String providerSubject, String nickname) {
        try {
            return memberAuthService.loginOrRegister(SocialProvider.KAKAO, providerSubject, nickname);
        } catch (DataIntegrityViolationException e) {
            return memberAuthService.loginOrRegister(SocialProvider.KAKAO, providerSubject, nickname);
        }
    }

    /**
     * 닉네임 위치: kakao_account.profile.nickname (profile_nickname 동의 시).
     * properties.nickname은 구형 응답 대비. 둘 다 없으면 null이고 보정은 MemberAuthService 담당.
     */
    private static String extractNickname(Map<String, Object> attributes) {
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
}
