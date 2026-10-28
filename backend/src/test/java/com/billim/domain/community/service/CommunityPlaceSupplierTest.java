package com.billim.domain.community.service;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.entity.CommunityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 공용 장소 자동 보충의 동시성·재시도 (도메인 가이드 결정 '공용 장소 자동 보충').
 *
 * <p>여러 인스턴스를 전제로 한다. 한 대에서도 동시 요청이면 같은 상황이 된다.
 */
class CommunityPlaceSupplierTest {

    private static final long COMMUNITY_ID = 3501L;
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final Duration RETRY_AFTER = Duration.ofHours(1);

    private static final PlaceCandidate CENTER = new PlaceCandidate(
            "역삼1동주민센터", "서울 강남구 역삼로7길 16", "서울 강남구 역삼동 829-20",
            new BigDecimal("37.4953"), new BigDecimal("127.0330"));
    private static final PlaceCandidate SAFETY = new PlaceCandidate(
            "역삼119안전센터", "서울 강남구 역삼로 236", "서울 강남구 역삼동 771-2",
            new BigDecimal("37.4980"), new BigDecimal("127.0310"));

    private PlaceFinder placeFinder;
    private CommunityPlaceWriter writer;
    private CommunityPlaceSupplier supplier;

    @BeforeEach
    void setUp() {
        placeFinder = mock(PlaceFinder.class);
        writer = mock(CommunityPlaceWriter.class);
        supplier = new CommunityPlaceSupplier(
                placeFinder, writer, Clock.fixed(NOW, ZoneOffset.UTC),
                true, 3, RETRY_AFTER);
    }

    /** id는 영속화로만 채워지므로 테스트에서는 직접 넣는다 */
    private Community community() {
        Community community = Community.of("역삼1동", "서울특별시 강남구 역삼1동",
                new BigDecimal("37.4953"), new BigDecimal("127.0330"),
                3000, CommunityStatus.ACTIVE);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        return community;
    }

    @Test
    @DisplayName("후보를 저장하고 그대로 돌려준다")
    void savesCandidates() {
        when(placeFinder.findNear(anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(CENTER, SAFETY));
        when(writer.save(anyLong(), any())).thenReturn(mock(CommunityPlace.class));

        assertThat(supplier.supply(community())).hasSize(2);
        verify(writer, never()).findActive(anyLong());
    }

    @Test
    @DisplayName("다른 요청이 먼저 넣어 충돌하면 커밋된 전체를 다시 읽는다")
    void rereadsOnConflict() {
        when(placeFinder.findNear(anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(CENTER, SAFETY));
        // 첫 번째는 충돌, 두 번째는 성공
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(writer).save(anyLong(), eq(CENTER));
        when(writer.save(anyLong(), eq(SAFETY))).thenReturn(mock(CommunityPlace.class));
        when(writer.findActive(anyLong()))
                .thenReturn(List.of(mock(CommunityPlace.class), mock(CommunityPlace.class)));

        // 내가 넣은 1건이 아니라 전체 2건을 본다
        assertThat(supplier.supply(community())).hasSize(2);
    }

    @Test
    @DisplayName("전부 충돌해도 빈 목록이 아니라 전체를 돌려준다")
    void neverReturnsEmptyWhenPlacesExist() {
        when(placeFinder.findNear(anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(CENTER, SAFETY));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(writer).save(anyLong(), any());
        when(writer.findActive(anyLong()))
                .thenReturn(List.of(mock(CommunityPlace.class), mock(CommunityPlace.class)));

        // 이 경로가 깨지면 동시 요청한 사용자가 "거래 장소 없음"을 본다
        assertThat(supplier.supply(community())).hasSize(2);
    }

    @Test
    @DisplayName("외부를 부르기 전에 시도를 기록한다. 다른 인스턴스가 같은 동네를 동시에 시도하지 않게")
    void marksAttemptBeforeCallingOutside() {
        when(placeFinder.findNear(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(List.of());

        supplier.supply(community());

        verify(writer).markAttempted(anyLong(), eq(NOW));
    }

    @Test
    @DisplayName("재시도 간격 안이면 외부를 부르지 않는다. 기록이 동네 행에 있어 인스턴스가 공유한다")
    void skipsWithinRetryWindow() {
        Community community = community();
        community.markPlaceSupplyAttempted(NOW.minus(Duration.ofMinutes(30)));

        assertThat(supplier.supply(community)).isEmpty();
        verifyNoInteractions(placeFinder);
    }

    @Test
    @DisplayName("재시도 간격이 지나면 다시 시도한다")
    void retriesAfterWindow() {
        Community community = community();
        community.markPlaceSupplyAttempted(NOW.minus(Duration.ofHours(2)));
        when(placeFinder.findNear(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(List.of(CENTER));
        when(writer.save(anyLong(), any())).thenReturn(mock(CommunityPlace.class));

        assertThat(supplier.supply(community)).hasSize(1);
    }

    @Test
    @DisplayName("꺼 두면 외부를 부르지 않고 기록도 남기지 않는다")
    void doesNothingWhenDisabled() {
        CommunityPlaceSupplier off = new CommunityPlaceSupplier(
                placeFinder, writer, Clock.fixed(NOW, ZoneOffset.UTC), false, 3, RETRY_AFTER);

        assertThat(off.supply(community())).isEmpty();
        verifyNoInteractions(placeFinder, writer);
    }
}
