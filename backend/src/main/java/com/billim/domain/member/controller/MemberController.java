package com.billim.domain.member.controller;

import com.billim.domain.member.dto.MyProfileResponse;
import com.billim.domain.member.dto.UpdateProfileRequest;
import com.billim.domain.member.service.MemberService;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** 유효 로그인만 필요. 미인증·제재 상태도 자신의 온보딩·제한 상태 조회 가능 */
    @GetMapping("/me")
    public MyProfileResponse getMyProfile(@AuthenticationPrincipal LoginMember loginMember) {
        return MyProfileResponse.from(memberService.getMyProfile(loginMember.memberId()));
    }

    /** 표시 이름 수정. 응답의 version은 증가한 값이라 다음 수정에 그대로 사용 */
    @PatchMapping("/me")
    public MyProfileResponse updateMyProfile(@AuthenticationPrincipal LoginMember loginMember,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return MyProfileResponse.from(memberService.changeDisplayName(
                loginMember.memberId(), request.expectedVersion(), request.displayName()));
    }
}
