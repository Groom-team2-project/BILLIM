package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.PolicyPageResponse;
import com.billim.domain.safety.service.PolicyService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책·안내 정적 문서 (API 명세 E_070).
 *
 * <p>인증 주체를 쓰지 않는다. 명세 0.2는 이 경로를 익명 허용 대상으로 정의한다
 * ("익명 허용은 /auth/csrf, /auth/kakao, callback, /policies/{policyKey}만").
 *
 * <p>TODO(A 인증): SecurityFilterChain의 공개 경로에 {@code /api/v1/policies/**}를 더해야
 * 로그인 전에도 볼 수 있다. 보안 설정 변경이라 인증 담당과 합의한 뒤 적용한다.
 * 그 전까지는 로그인한 회원만 조회된다.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    /** 작성되지 않은 문서는 404 (약관·개인정보는 별도 검토 후 제공) */
    @GetMapping("/{policyKey}")
    public PolicyPageResponse find(@PathVariable @Pattern(regexp = "[a-z]{1,30}") String policyKey) {
        return policyService.find(policyKey);
    }
}
