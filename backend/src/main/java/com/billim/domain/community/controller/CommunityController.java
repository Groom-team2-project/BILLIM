package com.billim.domain.community.controller;

import com.billim.domain.community.dto.CommunityPageResponse;
import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.dto.NearbyCommunitiesResponse;
import com.billim.domain.community.dto.PlaceListResponse;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.service.CommunityPlaceService;
import com.billim.domain.community.service.CommunityQueryService;
import com.billim.domain.community.service.CommunityVerificationService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동네·공용 장소 API (API 명세 4장 E_009 ~ E_012, E_071).
 * 모두 로그인이 필요하고, 검색·조회·주변 찾기는 소속 인증 전에도 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    /** POST의 Idempotency-Key 형식 (명세 0.3: UUID 필수) */
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final CommunityQueryService queryService;
    private final CommunityVerificationService verificationService;
    private final CommunityPlaceService placeService;

    public CommunityController(CommunityQueryService queryService,
                               CommunityVerificationService verificationService,
                               CommunityPlaceService placeService) {
        this.queryService = queryService;
        this.verificationService = verificationService;
        this.placeService = placeService;
    }

    /** E_009 동네 검색. 위치 없이 검색할 수 있다 */
    @GetMapping
    public CommunityPageResponse search(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        requireLogin(member);
        return queryService.search(keyword, page, size);
    }

    /**
     * E_071 현재 위치로 주변 동네 찾기. 이 조회 자체는 소속 인증이 아니다.
     * 고정 경로가 {@code /{communityId}}보다 먼저 선언돼야 하므로 위에 둔다.
     */
    @PostMapping("/nearby")
    public NearbyCommunitiesResponse findNearby(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody VerifyLocationRequest request,
            @AuthenticationPrincipal LoginMember member) {
        requireLogin(member);
        return queryService.findNearby(request);
    }

    /** E_010 동네 정보 조회. 비활성 동네는 404 */
    @GetMapping("/{communityId}")
    public CommunityResponse findCommunity(
            @PathVariable @Positive Long communityId,
            @AuthenticationPrincipal LoginMember member) {
        requireLogin(member);
        return queryService.findActive(communityId);
    }

    /** E_011 위치 인증 및 활성 동네 선택. 최초·재인증 모두 200 */
    @PostMapping("/{communityId}/verifications")
    public MembershipResponse verify(
            @PathVariable @Positive Long communityId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody VerifyLocationRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return verificationService.verify(memberId(member), communityId, request);
    }

    /** E_012 공용 거래 장소 목록 */
    @GetMapping("/{communityId}/places")
    public PlaceListResponse findPlaces(
            @PathVariable @Positive Long communityId,
            @AuthenticationPrincipal LoginMember member) {
        return placeService.findPlaces(memberId(member), communityId);
    }

    private void requireLogin(LoginMember member) {
        memberId(member);
    }

    /** 인증 주체는 members.id를 담는다 (LoginMember 계약) */
    private long memberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }
}
