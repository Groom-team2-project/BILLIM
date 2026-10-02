package com.billim.domain.item.port;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 다른 담당 구현체가 아직 없을 때 앱이 기동되도록 하는 자리표시자.
 * 실제 구현체 빈이 등록되면 그쪽이 우선한다(@Fallback). 임시 회원·고정값을 돌려주지 않고 항상 실패한다.
 * TODO: 각 담당 구현체 연결 후 이 클래스 삭제.
 */
@Configuration
class ItemPortFallbackConfig {

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Bean
    @Fallback
    CurrentMemberProvider unconnectedCurrentMemberProvider() {
        return () -> {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        };
    }

    @Bean
    @Fallback
    CommunityPort unconnectedCommunityPort() {
        return new CommunityPort() {
            @Override
            public long requireActiveCommunityId(long memberId) {
                throw unavailable();
            }

            @Override
            public Optional<Long> findActiveCommunityId(long memberId) {
                throw unavailable();
            }

            @Override
            public Optional<PlaceInfo> findPlace(long placeId) {
                throw unavailable();
            }

            @Override
            public Map<Long, PlaceInfo> findPlaces(Collection<Long> placeIds) {
                throw unavailable();
            }

            @Override
            public Optional<GeoPoint> findCenter(long communityId) {
                throw unavailable();
            }
        };
    }

    @Bean
    @Fallback
    MemberPort unconnectedMemberPort() {
        return memberIds -> {
            throw unavailable();
        };
    }

    @Bean
    @Fallback
    RentalPort unconnectedRentalPort() {
        return new RentalPort() {
            @Override
            public List<DateRange> findOpenRentalRanges(long itemId) {
                throw unavailable();
            }

            @Override
            public boolean hasOpenRentals(long itemId) {
                throw unavailable();
            }

            @Override
            public Set<Long> findUnavailableItemIds(long communityId, LocalDate startDate, LocalDate endDate) {
                throw unavailable();
            }

            @Override
            public boolean isRentalParticipant(long itemId, long memberId) {
                throw unavailable();
            }

            @Override
            public Map<Long, Integer> countPendingRequests(Collection<Long> itemIds) {
                throw unavailable();
            }
        };
    }

    @Bean
    @Fallback
    BlockPort unconnectedBlockPort() {
        return memberId -> {
            throw unavailable();
        };
    }
}
