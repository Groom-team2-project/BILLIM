package com.billim.domain.member.entity;

/**
 * social_accounts.provider — 소문자가 OAuth 등록 ID.
 * LOCAL은 OAuth 제공자가 아니라 관리자 전용 로그인(AdminLoginService)의 계정 식별용 —
 * 소셜 공용 계정을 만들 수 없어 자체 자격증명 계정이 필요하고,
 * members에 유니크 자연키가 없어 이 테이블의 유니크 키(provider, provider_subject)로 식별.
 */
public enum SocialProvider {
    KAKAO,
    NAVER,
    LOCAL
}
