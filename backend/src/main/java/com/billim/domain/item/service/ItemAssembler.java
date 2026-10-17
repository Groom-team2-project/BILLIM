package com.billim.domain.item.service;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.dto.ImageResponse;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemSummaryResponse;
import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.dto.PlaceResponse;
import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.service.MemberService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Entity → 응답 DTO 변환. 목록의 카테고리·썸네일·소유자·장소는 배치 조회(항목별 쿼리 방지)
// 미연동 정보는 값을 지어내지 않고 생략
// 거리는 물건 동네의 중심 기준(distanceBasis COMMUNITY_CENTER) · TODO(C): pendingRequestCount
@Component
public class ItemAssembler {

    private final CategoryRepository categoryRepository;
    private final ItemImageRepository itemImageRepository;
    private final MemberService memberService;
    private final CommunityPlaceRepository placeRepository;
    private final CommunityRepository communityRepository;

    static final String DISTANCE_BASIS = "COMMUNITY_CENTER";

    public ItemAssembler(CategoryRepository categoryRepository, ItemImageRepository itemImageRepository,
                         MemberService memberService, CommunityPlaceRepository placeRepository,
                         CommunityRepository communityRepository) {
        this.categoryRepository = categoryRepository;
        this.itemImageRepository = itemImageRepository;
        this.memberService = memberService;
        this.placeRepository = placeRepository;
        this.communityRepository = communityRepository;
    }

    public ItemDetailResponse detail(Item item, long viewerId) {
        Category category = categoryRepository.findById(item.getCategoryId()).orElseThrow(this::missingRef);
        MemberSummaryResponse owner = memberService.getSummaries(Set.of(item.getOwnerId())).get(item.getOwnerId());
        if (owner == null) {
            throw missingRef();
        }
        CommunityPlace place = placeRepository.findById(item.getPlaceId()).orElse(null);
        Integer distance = distance(communityRepository.findById(item.getCommunityId()).orElse(null), place);
        List<ImageResponse> images = item.getImages().stream()
                .map(i -> new ImageResponse(String.valueOf(i.getMediaFileId()), i.getSortOrder(),
                        MediaFileResponse.contentUrl(i.getMediaFileId())))
                .toList();
        // TODO: allowedActions 값 목록이 API 명세에 정의되지 않음. 소유자만 관리 동작을 반환하고 확정 시 교체
        List<String> actions = item.isOwnedBy(viewerId)
                ? List.of("EDIT", "CHANGE_VISIBILITY", "DELETE")
                : List.of();
        return new ItemDetailResponse(
                String.valueOf(item.getId()), item.getTitle(), item.getDescription(),
                owner, String.valueOf(item.getCommunityId()),
                CategoryResponse.from(category), place(item, place), images,
                item.getAvailableStartDate(), item.getAvailableEndDate(), item.getVisibility().name(),
                distance, distance == null ? null : DISTANCE_BASIS, item.getVersion(), actions, item.getCreatedAt());
    }

    // 입력 순서 유지
    public List<ItemSummaryResponse> summaries(List<Item> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> placeIds = new HashSet<>();
        for (Item i : items) {
            categoryIds.add(i.getCategoryId());
            itemIds.add(i.getId());
            ownerIds.add(i.getOwnerId());
            placeIds.add(i.getPlaceId());
        }
        Map<Long, MemberSummaryResponse> owners = memberService.getSummaries(ownerIds);
        Map<Long, Category> categories = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, Long> thumbs = itemImageRepository.findThumbnails(itemIds).stream()
                .collect(Collectors.toMap(ItemImageRepository.Thumbnail::getItemId,
                        ItemImageRepository.Thumbnail::getMediaFileId));
        Map<Long, CommunityPlace> places = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(CommunityPlace::getId, Function.identity()));
        Set<Long> communityIds = new HashSet<>();
        for (Item i : items) {
            communityIds.add(i.getCommunityId());
        }
        Map<Long, Community> communities = communityRepository.findAllById(communityIds).stream()
                .collect(Collectors.toMap(Community::getId, Function.identity()));

        return items.stream().map(i -> {
            Category category = categories.get(i.getCategoryId());
            MemberSummaryResponse owner = owners.get(i.getOwnerId());
            if (category == null || owner == null) {
                throw missingRef();
            }
            CommunityPlace place = places.get(i.getPlaceId());
            Integer distance = distance(communities.get(i.getCommunityId()), place);
            Long thumb = thumbs.get(i.getId());
            return new ItemSummaryResponse(
                    String.valueOf(i.getId()), i.getTitle(), CategoryResponse.from(category),
                    owner, place(i, place),
                    thumb == null ? null : MediaFileResponse.contentUrl(thumb),
                    i.getVisibility().name(), distance, distance == null ? null : DISTANCE_BASIS,
                    null, i.getCreatedAt(), null, i.getVersion());
        }).toList();
    }

    // 동네 중심과 장소 사이 거리(m). 둘 중 하나라도 모르면 null(응답에서 생략)
    private static Integer distance(Community community, CommunityPlace place) {
        if (community == null || place == null) {
            return null;
        }
        return GeoDistance.meters(community.getCenterLatitude(), community.getCenterLongitude(),
                place.getLatitude(), place.getLongitude());
    }

    // 장소 행이 없으면(정합성 이상) 목록 전체를 실패시키지 않고 id만 응답
    private static PlaceResponse place(Item item, CommunityPlace place) {
        return place == null ? PlaceResponse.of(item.getPlaceId(), item.getCommunityId()) : PlaceResponse.from(place);
    }

    // 참조 대상(카테고리·소유 회원)이 사라진 데이터 정합성 오류
    private BusinessException missingRef() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
