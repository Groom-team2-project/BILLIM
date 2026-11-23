package com.billim.domain.community.controller;

import com.billim.domain.community.dto.MyCommunitiesResponse;
import com.billim.domain.community.service.MyCommunityService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 동네 API (API 명세 4장 E_074 ~ E_076).
 *
 * <p>저장 동네는 둘까지고 현재 동네는 하나다. <b>추가는 위치 인증(E_011)으로만</b> 되고
 * 여기서는 이미 저장된 동네를 조회·전환·이탈한다.
 * (도메인 가이드 결정 '저장 동네와 현재 동네')
 *
 * <p>경로를 {@code /members/me} 아래 둔 이유는 회원 자신의 상태이기 때문이다. 소속 데이터는
 * 커뮤니티 도메인이 가지므로 구현은 이 도메인에 둔다 (E_072 내가 받은 운영 조치와 같은 구성).
 */
@RestController
@RequestMapping("/api/v1/members/me/communities")
public class MyCommunityController {

    /** POST의 Idempotency-Key 형식 (명세 0.3: UUID 필수) */
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final MyCommunityService myCommunityService;

    public MyCommunityController(MyCommunityService myCommunityService) {
        this.myCommunityService = myCommunityService;
    }

    /** E_074 내 동네 목록 */
    @GetMapping
    public MyCommunitiesResponse findMine(@AuthenticationPrincipal LoginMember member) {
        return myCommunityService.findMine(memberId(member));
    }

    /** E_075 현재 동네 변경. 저장 동네가 아니면 404 */
    @PostMapping("/{communityId}/select")
    public MyCommunitiesResponse select(
            @PathVariable @Positive Long communityId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @AuthenticationPrincipal LoginMember member) {
        return myCommunityService.select(memberId(member), communityId);
    }

    /** E_076 저장 동네 이탈. 마지막 하나는 409 */
    @DeleteMapping("/{communityId}")
    public MyCommunitiesResponse leave(
            @PathVariable @Positive Long communityId,
            @AuthenticationPrincipal LoginMember member) {
        return myCommunityService.leave(memberId(member), communityId);
    }

    /** 인증 주체는 members.id를 담는다 (LoginMember 계약) */
    private long memberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }
}
