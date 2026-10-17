package com.billim.domain.item.service;

import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemSummaryResponse;
import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.service.MemberService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 응답 변환. 소유자 표시 정보는 회원 도메인의 배치 조회(MemberService.getSummaries) 결과를 쓴다. */
@ExtendWith(MockitoExtension.class)
class ItemAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Mock CategoryRepository categoryRepository;
    @Mock ItemImageRepository itemImageRepository;
    @Mock MemberService memberService;
    @Mock CommunityPlaceRepository placeRepository;

    ItemAssembler assembler;
    final Category category = new Category(1L, "TOOL", "공구", 1, true);

    @BeforeEach
    void setUp() {
        assembler = new ItemAssembler(categoryRepository, itemImageRepository, memberService, placeRepository);
    }

    private Item item(long id, long ownerId) {
        Item item = Item.create(ownerId, 100L, 1L, 10L, "전동드릴", null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), NOW);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "version", 0L);
        item.replaceImages(List.of(5L), NOW);
        return item;
    }

    private static MemberSummaryResponse member(long id, String name) {
        return new MemberSummaryResponse(String.valueOf(id), name, Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @DisplayName("상세의 소유자는 회원 도메인이 돌려준 표시 정보(이름·가입일)를 쓴다")
    void detailUsesOwnerSummaryFromMemberService() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(memberService.getSummaries(Set.of(2L))).thenReturn(Map.of(2L, member(2, "정우")));

        ItemDetailResponse res = assembler.detail(item(7, 2), 9L);

        assertThat(res.owner().id()).isEqualTo("2");
        assertThat(res.owner().displayName()).isEqualTo("정우");
        assertThat(res.owner().joinedAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @DisplayName("목록은 소유자를 한 번의 배치 조회로 가져온다 (항목마다 조회하지 않는다)")
    void summariesLookUpOwnersInOneBatch() {
        when(categoryRepository.findAllById(anyCollection())).thenReturn(List.of(category));
        when(itemImageRepository.findThumbnails(anyCollection())).thenReturn(List.of());
        when(memberService.getSummaries(anyCollection()))
                .thenReturn(Map.of(2L, member(2, "정우"), 3L, member(3, "민지")));

        List<ItemSummaryResponse> res = assembler.summaries(List.of(item(7, 2), item(8, 3), item(9, 2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(memberService, times(1)).getSummaries(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(res).extracting(r -> r.owner().displayName()).containsExactly("정우", "민지", "정우");
    }

    @Test
    @DisplayName("소유 회원 정보를 찾을 수 없으면 데이터 정합성 오류(500)로 처리한다")
    void missingOwnerIsAnIntegrityError() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(memberService.getSummaries(Set.of(2L))).thenReturn(Map.of());

        assertThatThrownBy(() -> assembler.detail(item(7, 2), 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("빈 목록이면 회원 조회를 하지 않는다")
    void emptyListSkipsMemberLookup() {
        assertThat(assembler.summaries(List.of())).isEmpty();
        verify(memberService, never()).getSummaries(anyCollection());
    }

    private static CommunityPlace place(long id, String name) {
        CommunityPlace p = CommunityPlace.of(100L, name, new BigDecimal("37.4976000"), new BigDecimal("127.0272000"), "정문 오른쪽", true);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("상세의 거래 장소는 공용 장소의 이름·좌표·안내를 채운다")
    void detailFillsPlaceFromCommunityPlace() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(memberService.getSummaries(Set.of(2L))).thenReturn(Map.of(2L, member(2, "정우")));
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place(10, "정문 경비실 옆 벤치")));

        ItemDetailResponse res = assembler.detail(item(7, 2), 9L);

        assertThat(res.place().id()).isEqualTo("10");
        assertThat(res.place().name()).isEqualTo("정문 경비실 옆 벤치");
        assertThat(res.place().latitude()).isEqualByComparingTo("37.4976");
        assertThat(res.place().guide()).isEqualTo("정문 오른쪽");
    }

    @Test
    @DisplayName("목록은 장소를 한 번에 조회하고, 장소 행이 없으면 id만 내려 목록 전체를 실패시키지 않는다")
    void summariesLoadPlacesInOneBatchAndFallBackToId() {
        when(categoryRepository.findAllById(anyCollection())).thenReturn(List.of(category));
        when(itemImageRepository.findThumbnails(anyCollection())).thenReturn(List.of());
        when(memberService.getSummaries(anyCollection())).thenReturn(Map.of(2L, member(2, "정우")));
        when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(place(10, "정문 경비실 옆 벤치")));
        Item known = item(7, 2);
        Item unknownPlace = item(8, 2);
        ReflectionTestUtils.setField(unknownPlace, "placeId", 99L);

        List<ItemSummaryResponse> res = assembler.summaries(List.of(known, unknownPlace));

        verify(placeRepository, times(1)).findAllById(anyCollection());
        assertThat(res.get(0).place().name()).isEqualTo("정문 경비실 옆 벤치");
        assertThat(res.get(1).place().id()).isEqualTo("99");
        assertThat(res.get(1).place().name()).isNull();
    }
}
