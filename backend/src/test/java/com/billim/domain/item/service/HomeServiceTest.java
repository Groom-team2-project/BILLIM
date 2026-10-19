package com.billim.domain.item.service;

import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.service.CommunityQueryService;
import com.billim.domain.item.dto.CategoryListResponse;
import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.dto.HomeResponse;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.item.dto.ItemSummaryResponse;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.notification.service.NotificationQueryService;
import com.billim.domain.rental.dto.RentalCountStatusResponse;
import com.billim.domain.rental.service.RentalQueryService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 홈 화면 조립 (API 명세 B_027). 각 도메인 서비스는 Mock
@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final long ME = 1L;
    private static final long COMMUNITY = 100L;

    @Mock ItemAccessPolicy accessPolicy;
    @Mock ItemService itemService;
    @Mock CategoryService categoryService;
    @Mock CommunityQueryService communityQueryService;
    @Mock RentalQueryService rentalQueryService;
    @Mock NotificationQueryService notificationQueryService;

    HomeService homeService;
    final LoginMember member = new LoginMember(ME, MemberRole.USER);

    @BeforeEach
    void setUp() {
        homeService = new HomeService(accessPolicy, itemService, categoryService, communityQueryService,
                rentalQueryService, notificationQueryService);
    }

    private static RentalCountStatusResponse rentals() {
        RentalCountStatusResponse.RentalCounts zero = new RentalCountStatusResponse.RentalCounts(0, 0, 0, 0, 0, 0, 0, 0);
        RentalCountStatusResponse.RentalCounts lent = new RentalCountStatusResponse.RentalCounts(2, 1, 0, 0, 0, 0, 0, 0);
        return new RentalCountStatusResponse(zero, lent, List.of());
    }

    @Test
    @DisplayName("각 도메인의 값을 제자리에 모은다: 동네·물건 수·카테고리·최신 물건·대여 요약·알림 수")
    void assemblesEachDomainValue() {
        CommunityResponse community = new CommunityResponse("100", "강남역 생활권", "서울 강남구 강남대로 396 일대",
                new BigDecimal("37.4979420"), new BigDecimal("127.0276210"), 1000, 12);
        List<ItemSummaryResponse> latest = List.of(mock(ItemSummaryResponse.class), mock(ItemSummaryResponse.class));
        List<CategoryResponse> categories = List.of(mock(CategoryResponse.class));
        RentalCountStatusResponse myRentals = rentals();
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemService.search(eq(ME), any())).thenReturn(new ItemPageResponse(latest, 0, 6, 37, true));
        when(communityQueryService.findActive(COMMUNITY)).thenReturn(community);
        when(categoryService.list()).thenReturn(new CategoryListResponse(categories));
        when(rentalQueryService.getRentalCountStatus(member)).thenReturn(myRentals);
        when(notificationQueryService.unreadCountOf(ME)).thenReturn(4L);

        HomeResponse res = homeService.home(member);

        assertThat(res.community()).isEqualTo(community);
        assertThat(res.itemCount()).isEqualTo(37);
        assertThat(res.categories()).isEqualTo(categories);
        assertThat(res.latestItems()).isEqualTo(latest);
        assertThat(res.myRentals()).isEqualTo(myRentals);
        assertThat(res.unreadNotificationCount()).isEqualTo(4);
        assertThat(res.unreadChatCount()).isZero();
    }

    @Test
    @DisplayName("최신 물건은 검색 최신순 첫 페이지 6개로 조회한다")
    void latestItemsUseLatestSearchFirstPageOfSix() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemService.search(eq(ME), any())).thenReturn(new ItemPageResponse(List.of(), 0, 6, 0, false));
        when(categoryService.list()).thenReturn(new CategoryListResponse(List.of()));

        homeService.home(member);

        ArgumentCaptor<ItemSearchCondition> cond = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemService).search(eq(ME), cond.capture());
        assertThat(cond.getValue().sort()).isEqualTo(ItemSort.LATEST);
        assertThat(cond.getValue().page()).isZero();
        assertThat(cond.getValue().size()).isEqualTo(6);
        assertThat(cond.getValue().keyword()).isNull();
    }

    @Test
    @DisplayName("유효 활성 동네가 없으면 403이고 다른 도메인을 조회하지 않는다")
    void requiresActiveCommunity() {
        when(accessPolicy.requireActiveCommunityId(ME))
                .thenThrow(new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));

        assertThatThrownBy(() -> homeService.home(member))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
        verify(itemService, never()).search(anyLong(), any());
        verify(rentalQueryService, never()).getRentalCountStatus(any());
        verify(notificationQueryService, never()).unreadCountOf(anyLong());
    }

    @Test
    @DisplayName("안 읽은 알림 수가 int 범위를 넘으면 최댓값으로 맞춘다")
    void clampsHugeUnreadCount() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemService.search(eq(ME), any())).thenReturn(new ItemPageResponse(List.of(), 0, 6, 0, false));
        when(categoryService.list()).thenReturn(new CategoryListResponse(List.of()));
        when(notificationQueryService.unreadCountOf(ME)).thenReturn(Long.MAX_VALUE);

        assertThat(homeService.home(member).unreadNotificationCount()).isEqualTo(Integer.MAX_VALUE);
    }
}
