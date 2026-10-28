package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 관리자 전용 로그인 — 소셜 공용 계정을 만들 수 없어 둔 자체 자격증명 접근 수단.
 * 자격증명을 DB가 아닌 환경 변수에 두는 이유: 일반 비밀번호 로그인 기능이 아니라
 * 배포 설정만으로 켜고 끄는 운영 도구이기 때문. 둘 중 하나라도 비면 비활성.
 */
@Service
public class AdminLoginService {

    /** 요청마다 새로 만들지 않음 — matches()는 상태 없이 동작 */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final MemberAuthService memberAuthService;
    private final String loginId;
    private final String passwordHash;

    public AdminLoginService(MemberAuthService memberAuthService,
                             @Value("${billim.auth.admin-login-id:}") String loginId,
                             @Value("${billim.auth.admin-password-hash:}") String passwordHash) {
        this.memberAuthService = memberAuthService;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    /**
     * 자격증명 대조 후 관리자 회원 반환. 세션 발급은 컨트롤러 담당.
     * 비활성 시 404 — 운영에서 꺼두면 경로 탐색으로도 존재가 드러나지 않음.
     */
    public Member login(String loginId, String password) {
        if (this.loginId.isBlank() || this.passwordHash.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!credentialsMatch(loginId, password)) {
            // ID·비밀번호 중 무엇이 틀렸는지 비노출
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return loginOrRegister();
    }

    /** ID가 틀려도 해시 대조까지 수행 — 응답 시간 차이로 ID 일치 여부가 드러나지 않음 */
    private boolean credentialsMatch(String loginId, String password) {
        boolean idMatches = MessageDigest.isEqual(
                this.loginId.getBytes(StandardCharsets.UTF_8),
                loginId.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = ENCODER.matches(password, passwordHash);
        return idMatches && passwordMatches;
    }

    /**
     * 동시 첫 로그인 재시도 — 거부된 트랜잭션은 롤백 확정이라 새 호출로 재조회.
     * (SocialOAuth2UserService.loginOrRegister와 같은 구조)
     */
    private Member loginOrRegister() {
        try {
            return memberAuthService.loginOrRegisterLocalAdmin();
        } catch (DataIntegrityViolationException e) {
            return memberAuthService.loginOrRegisterLocalAdmin();
        }
    }
}
