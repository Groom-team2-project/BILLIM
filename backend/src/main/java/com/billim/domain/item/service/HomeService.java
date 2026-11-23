package com.billim.domain.item.service;

import com.billim.domain.community.service.CommunityQueryService;
import com.billim.domain.item.dto.HomeResponse;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.notification.service.NotificationQueryService;
import com.billim.domain.rental.service.RentalQueryService;
import com.billim.global.security.LoginMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 홈 화면 조립 (API 명세 B_027). 조회 원천은 각 도메인 서비스, 이 클래스는 모으기만 함
@Service
public class HomeService {

    static final int LATEST_SIZE = 6;

    private final ItemAccessPolicy accessPolicy;
    private final ItemService itemService;
    private final CategoryService categoryService;
    private final CommunityQueryService communityQueryService;
    private final RentalQueryService rentalQueryService;
    private final NotificationQueryService notificationQueryService;

    public HomeService(ItemAccessPolicy accessPolicy, ItemService itemService, CategoryService categoryService,
                       CommunityQueryService communityQueryService, RentalQueryService rentalQueryService,
                       NotificationQueryService notificationQueryService) {
        this.accessPolicy = accessPolicy;
        this.itemService = itemService;
        this.categoryService = categoryService;
        this.communityQueryService = communityQueryService;
        this.rentalQueryService = rentalQueryService;
        this.notificationQueryService = notificationQueryService;
    }

    /** 유효 활성 동네가 없으면 403. 최신 물건과 물건 수는 검색(B_017 최신순)과 같은 노출 규칙 */
    @Transactional(readOnly = true)
    public HomeResponse home(LoginMember member) {
        long memberId = member.memberId();
        long communityId = accessPolicy.requireActiveCommunityId(memberId);
        ItemPageResponse latest = itemService.search(memberId,
                new ItemSearchCondition(null, null, null, null, null, ItemSort.LATEST, 0, LATEST_SIZE));
        return new HomeResponse(
                communityQueryService.findActive(communityId),
                toInt(latest.totalElements()),
                categoryService.list().items(),
                latest.items(),
                rentalQueryService.getRentalCountStatus(member),
                toInt(notificationQueryService.unreadCountOf(memberId)),
                0);   // 안 읽은 채팅 수: 명세상 P1 전에는 0
    }

    private static int toInt(long count) {
        return (int) Math.min(count, Integer.MAX_VALUE);
    }
}
