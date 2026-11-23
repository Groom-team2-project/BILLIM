package com.billim.global.security;

import com.billim.domain.member.entity.MemberRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 인증 주체. 제공자 식별자가 아니라 members.id를 담음.
 * getName()이 members.id를 반환하는 것은 호출 측과의 계약. (domain/item AuthenticatedMember)
 */
public record LoginMember(Long memberId, MemberRole role) implements OAuth2User {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }

    /** ROLE_ 접두사는 hasRole() 사용을 위한 Spring Security 규약 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** 제공자 응답 원문은 보관하지 않음. */
    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("memberId", memberId);
    }
}
