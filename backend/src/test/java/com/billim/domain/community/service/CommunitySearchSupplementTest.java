package com.billim.domain.community.service;

import com.billim.domain.community.dto.RegionScope;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.entity.Region;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 동네 검색의 행정동 보충 등록 (API 명세 E_009).
 *
 * <p>검색어 일부만으로 전국 행정동이 후보에 나와야 하지만, 모든 입력마다 지도를 부르고
 * 행을 만들면 안 된다. 호출 조건만 보는 단위 테스트다.
 */
class CommunitySearchSupplementTest {

    private static final Region YEOKSAM = new Region(
            "1168064000", "역삼1동", "서울 강남구 역삼1동", 37.5006, 127.0361);

    private CommunityRepository communityRepository;
    private RegionResolver regionResolver;
    private CommunityAutoRegistrar autoRegistrar;
    private CommunityQueryService service;

    @BeforeEach
    void setUp() {
        communityRepository = mock(CommunityRepository.class);
        regionResolver = mock(RegionResolver.class);
        autoRegistrar = mock(CommunityAutoRegistrar.class);

        when(communityRepository.search(
                any(), anyString(), anyString(), anyString(), anyBoolean(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service = new CommunityQueryService(
                communityRepository, mock(CommunityMembershipRepository.class),
                regionResolver, autoRegistrar,
                Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("없는 행정동은 검색 중에 동네로 등록된다")
    void registersMissingRegion() {
        when(regionResolver.search(eq("역삼"), anyInt())).thenReturn(List.of(YEOKSAM));
        when(communityRepository.findByRegionCodeAndStatus(YEOKSAM.code(), CommunityStatus.ACTIVE))
                .thenReturn(Optional.empty());

        service.search("역삼", RegionScope.ALL, false, 0, 20);

        verify(autoRegistrar).register(YEOKSAM);
    }

    @Test
    @DisplayName("이미 있는 행정동은 다시 등록하지 않는다")
    void skipsRegisteredRegion() {
        when(regionResolver.search(anyString(), anyInt())).thenReturn(List.of(YEOKSAM));
        when(communityRepository.findByRegionCodeAndStatus(YEOKSAM.code(), CommunityStatus.ACTIVE))
                .thenReturn(Optional.of(mock(Community.class)));

        service.search("역삼", RegionScope.ALL, false, 0, 20);

        verify(autoRegistrar, never()).register(any(Region.class));
    }

    @Test
    @DisplayName("한 글자 검색어로는 지도를 부르지 않는다")
    void skipsShortKeyword() {
        service.search("역", RegionScope.ALL, false, 0, 20);

        verifyNoInteractions(regionResolver, autoRegistrar);
    }

    @Test
    @DisplayName("빈 검색어는 조건 없는 활성 동네 목록이고 지도를 부르지 않는다")
    void skipsBlankKeyword() {
        service.search("   ", RegionScope.ALL, false, 0, 20);

        verify(communityRepository).search(
                eq(CommunityStatus.ACTIVE), eq(""), eq(""), eq(""), eq(false), any(Pageable.class));
        verifyNoInteractions(regionResolver, autoRegistrar);
    }

    @Test
    @DisplayName("두 번째 페이지는 등록하지 않는다. 첫 페이지에서 끝난 일이다")
    void skipsLaterPages() {
        service.search("역삼", RegionScope.ALL, false, 1, 20);

        verifyNoInteractions(regionResolver, autoRegistrar);
    }

    @Test
    @DisplayName("지역 범위를 고른 검색은 지도를 부르지 않는다. 범위 옵션이 등록된 동네에서 나왔다")
    void skipsScopedSearch() {
        service.search("역삼", RegionScope.of("서울특별시", "강남구", null, null), false, 0, 20);

        verifyNoInteractions(regionResolver, autoRegistrar);
    }

    @Test
    @DisplayName("고른 상위 단계는 공개 주소 접두사가 된다. 토큰 끝에 공백을 둔다")
    void scopeBecomesAddressPrefix() {
        service.search("", RegionScope.of("경기도", "수원시", "영통구", null), false, 0, 20);

        verify(communityRepository).search(eq(CommunityStatus.ACTIVE),
                eq("경기도 수원시 영통구 "), eq(""), eq(""), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("읍·면·동까지 고르면 접두사가 아니라 확정된 공개 주소로 비교한다")
    void dongBecomesExactAddress() {
        service.search("", RegionScope.of("서울특별시", "강남구", null, "역삼1동"), false, 0, 20);

        verify(communityRepository).search(eq(CommunityStatus.ACTIVE),
                eq("서울특별시 강남구 "), eq("서울특별시 강남구 역삼1동"), eq(""), eq(false), any(Pageable.class));
    }
}
