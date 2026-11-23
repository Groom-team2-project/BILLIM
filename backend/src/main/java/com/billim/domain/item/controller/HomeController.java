package com.billim.domain.item.controller;

import com.billim.domain.item.dto.HomeResponse;
import com.billim.domain.item.service.HomeService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    /** B_027 홈 화면 조립. 유효 활성 동네 필요. 동네·최신 물건 6개·물건 수·카테고리·내 대여 요약·알림 수를 한 번에 */
    @GetMapping
    public HomeResponse home(@AuthenticationPrincipal LoginMember member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return homeService.home(member);
    }
}
