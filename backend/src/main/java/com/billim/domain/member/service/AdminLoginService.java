package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 관리자 전용 로그인 — 소셜 공용 계정을 만들 수 없어 둔 자체 자격증명 접근 수단.
 * 자격증명을 DB가 아닌 환경 변수에 두는 이유: 일반 비밀번호 로그인 기능이 아니라
 * 배포 설정만으로 켜고 끄는 운영 도구이기 때문. 둘 중 하나라도 비면 비활성.
 */
@Service
public class AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);

    /** 요청마다 새로 만들지 않음 — matches()는 상태 없이 동작 */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final MemberAuthService memberAuthService;
    private final AdminLoginLockout lockout;
    private final Clock clock;
    private final String loginId;
    private final String passwordHash;

    public AdminLoginService(MemberAuthService memberAuthService,
                             AdminLoginLockout lockout,
                             Clock clock,
                             @Value("${billim.auth.admin-login-id:}") String loginId,
                             @Value("${billim.auth.admin-password-hash:}") String passwordHash) {
        this.memberAuthService = memberAuthService;
        this.lockout = lockout;
        this.clock = clock;
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
        // 대조 전 슬롯 선점 — 잠금 중·동시 시도 포화면 bcrypt 연산 없이 거부
        Instant retryAt = lockout.begin().orElse(null);
        if (retryAt != null) {
            throw locked(retryAt);
        }
        boolean matched;
        try {
            matched = credentialsMatch(loginId, password);
        } catch (RuntimeException e) {
            lockout.fail();   // 확보한 슬롯 반환 누락 방지
            throw e;
        }
        if (!matched) {
            Instant until = lockout.fail().orElse(null);
            if (until != null) {
                log.warn("관리자 로그인 잠금 — 실패 한도 도달, 해제 {}", until);
                throw locked(until);
            }
            log.warn("관리자 로그인 실패 — 최근 {}회", lockout.failures());
            // ID·비밀번호 중 무엇이 틀렸는지 비노출
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        lockout.succeed();
        return loginOrRegister();
    }

    private BusinessException locked(Instant until) {
        long minutes = Math.max(1, Duration.between(Instant.now(clock), until).toSeconds() / 60 + 1);
        return new BusinessException(ErrorCode.RATE_LIMITED,
                "로그인 실패가 반복되어 잠겼습니다. 약 %d분 후 다시 시도해 주세요.".formatted(minutes));
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
