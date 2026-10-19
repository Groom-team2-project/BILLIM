package com.billim.domain.community.service;

import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.entity.Region;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 행정동·반경 병행 판정 (도메인 가이드 결정 '행정동 위치 인증').
 *
 * <p>판정 분기만 보는 단위 테스트다. 소속 저장·활성 동네 갱신은 {@link CommunityFlowDbTest}가 확인한다.
 */
class CommunityVerificationRegionTest {

    private static final long MEMBER_ID = 7L;
    private static final long COMMUNITY_ID = 3L;
    private static final String REGION_CODE = "1168064000";

    /** 동네 중심. 반경 500m */
    private static final double CENTER_LAT = 37.5;
    private static final double CENTER_LON = 127.0;
    private static final int RADIUS_M = 500;

    /** 테스트에서 고정한 정확도 상한 */
    private static final double MAX_ACCURACY_M = 5000;

    private CommunityQueryService queryService;
    private RegionResolver regionResolver;
    private CommunityVerificationService service;

    @BeforeEach
    void setUp() {
        queryService = mock(CommunityQueryService.class);
        regionResolver = mock(RegionResolver.class);

        CommunityMembershipRepository membershipRepository = mock(CommunityMembershipRepository.class);
        when(membershipRepository.findByMemberIdAndStatus(anyLong(), any(MembershipStatus.class)))
                .thenReturn(Optional.empty());
        when(membershipRepository.findByMemberIdAndCommunityId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(CommunityMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new CommunityVerificationService(
                queryService, membershipRepository, mock(ActiveCommunityWriter.class),
                regionResolver, Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC),
                MAX_ACCURACY_M, true);
    }

    @Test
    @DisplayName("행정동이 같으면 반경 밖이어도 인증된다")
    void verifiesByRegionOutsideRadius() {
        givenCommunity(REGION_CODE);
        givenResolvedRegion(REGION_CODE);

        // 중심에서 약 11km. 반경 500m 밖이다
        assertThatCode(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(37.6, 127.0, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("행정동이 같으면 정확도 상한을 넘어도 인증된다")
    void verifiesByRegionIgnoringAccuracy() {
        givenCommunity(REGION_CODE);
        givenResolvedRegion(REGION_CODE);

        assertThatCode(() -> service.verify(MEMBER_ID, COMMUNITY_ID,
                at(CENTER_LAT, CENTER_LON, MAX_ACCURACY_M + 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("행정동이 다르면 반경 안이면 인증된다")
    void fallsBackToRadiusWhenRegionDiffers() {
        givenCommunity(REGION_CODE);
        givenResolvedRegion("1168065000");

        assertThatCode(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(CENTER_LAT, CENTER_LON, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("행정동이 다르고 반경도 밖이면 403")
    void rejectsWhenRegionAndRadiusBothFail() {
        givenCommunity(REGION_CODE);
        givenResolvedRegion("1168065000");

        assertThatThrownBy(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(37.6, 127.0, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_OUTSIDE));
    }

    @Test
    @DisplayName("지도 조회가 실패하면 반경 판정으로 내려간다")
    void fallsBackToRadiusWhenResolverFails() {
        givenCommunity(REGION_CODE);
        when(regionResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        assertThatCode(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(CENTER_LAT, CENTER_LON, 10)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(37.6, 127.0, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_OUTSIDE));
    }

    @Test
    @DisplayName("행정동 코드가 없는 동네는 지도를 부르지 않는다")
    void skipsResolverWhenCommunityHasNoRegionCode() {
        givenCommunity(null);

        assertThatCode(() -> service.verify(MEMBER_ID, COMMUNITY_ID, at(CENTER_LAT, CENTER_LON, 10)))
                .doesNotThrowAnyException();
        verifyNoInteractions(regionResolver);
    }

    @Test
    @DisplayName("반경으로 떨어진 경우에는 정확도 상한이 그대로 걸린다")
    void keepsAccuracyLimitOnRadiusPath() {
        givenCommunity(REGION_CODE);
        givenResolvedRegion("1168065000");

        assertThatThrownBy(() -> service.verify(MEMBER_ID, COMMUNITY_ID,
                at(CENTER_LAT, CENTER_LON, MAX_ACCURACY_M + 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_INACCURATE));
    }

    private void givenCommunity(String regionCode) {
        Community community = Community.of(
                "역삼1동", "서울특별시 강남구 역삼1동",
                BigDecimal.valueOf(CENTER_LAT), BigDecimal.valueOf(CENTER_LON),
                RADIUS_M, CommunityStatus.ACTIVE);
        if (regionCode != null) {
            community.assignRegionCode(regionCode);
        }
        when(queryService.requireActive(COMMUNITY_ID)).thenReturn(community);
    }

    private void givenResolvedRegion(String code) {
        when(regionResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.of(
                new Region(code, "역삼1동", "서울특별시 강남구 역삼1동", CENTER_LAT, CENTER_LON)));
    }

    private static VerifyLocationRequest at(double latitude, double longitude, double accuracyMeters) {
        return new VerifyLocationRequest(latitude, longitude, accuracyMeters);
    }
}
