package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.SocialAccount;
import com.billim.domain.member.entity.SocialProvider;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.member.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/** 소셜 식별자로 회원 조회 또는 가입 */
@Service
@RequiredArgsConstructor
public class MemberAuthService {

    private static final String FALLBACK_NAME_PREFIX = "이웃";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public Member loginOrRegister(SocialProvider provider, String providerSubject, String nickname) {
        return socialAccountRepository.findWithMember(provider, providerSubject)
                .map(SocialAccount::getMember)
                .orElseGet(() -> register(provider, providerSubject, nickname));
    }

    private Member register(SocialProvider provider, String providerSubject, String nickname) {
        Member member = memberRepository.save(Member.register(normalizeDisplayName(nickname)));
        socialAccountRepository.save(SocialAccount.link(member, provider, providerSubject));
        return member;
    }

    /**
     * 제공자 닉네임을 표시 이름 규격(2~30자)으로 보정.
     * 규격 이탈 닉네임으로 인한 가입 실패 방지. 표시 이름은 중복 허용.
     */
    static String normalizeDisplayName(String nickname) {
        String trimmed = nickname == null ? "" : nickname.strip();
        if (trimmed.length() > Member.DISPLAY_NAME_MAX) {
            trimmed = truncate(trimmed);
        }
        // 하한 검사는 절단 이후. 자른 결과가 공백뿐인 경우도 여기서 처리
        return trimmed.length() < Member.DISPLAY_NAME_MIN
                ? FALLBACK_NAME_PREFIX + (1000 + RANDOM.nextInt(9000))
                : trimmed;
    }

    /**
     * 표시 이름 상한까지 절단.
     * 경계가 서로게이트 쌍 가운데면 한 칸 앞에서 절단. 짝 없는 서로게이트는 utf8mb4 저장 불가.
     * 코드 포인트가 아닌 char 기준인 이유: Member의 길이 검증이 String.length() 기준.
     */
    private static String truncate(String value) {
        int end = Member.DISPLAY_NAME_MAX;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).strip();
    }
}
