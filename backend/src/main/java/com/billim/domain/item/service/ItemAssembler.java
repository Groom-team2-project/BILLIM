package com.billim.domain.item.service;

import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.dto.ImageResponse;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemSummaryResponse;
import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.dto.MemberSummaryResponse;
import com.billim.domain.item.dto.PlaceResponse;
import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.item.port.CommunityPort;
import com.billim.domain.item.port.CommunityPort.GeoPoint;
import com.billim.domain.item.port.CommunityPort.PlaceInfo;
import com.billim.domain.item.port.MemberPort;
import com.billim.domain.item.port.MemberPort.MemberSummary;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity → 응답 DTO 변환. 목록은 카테고리·회원·장소·썸네일을 배치 조회해 항목별 쿼리를 만들지 않는다. */
@Component
public class ItemAssembler {

    private final CategoryRepository categoryRepository;
    private final ItemImageRepository itemImageRepository;
    private final MemberPort memberPort;
    private final CommunityPort communityPort;

    public ItemAssembler(CategoryRepository categoryRepository, ItemImageRepository itemImageRepository,
                         MemberPort memberPort, CommunityPort communityPort) {
        this.categoryRepository = categoryRepository;
        this.itemImageRepository = itemImageRepository;
        this.memberPort = memberPort;
        this.communityPort = communityPort;
    }

    public ItemDetailResponse detail(Item item, long viewerId) {
        Category category = categoryRepository.findById(item.getCategoryId()).orElseThrow(this::missingRef);
        PlaceInfo place = communityPort.findPlace(item.getPlaceId()).orElseThrow(this::missingRef);
        MemberSummary owner = memberPort.findSummaries(Set.of(item.getOwnerId())).get(item.getOwnerId());
        if (owner == null) {
            throw missingRef();
        }
        GeoPoint center = communityPort.findCenter(item.getCommunityId()).orElseThrow(this::missingRef);
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
                MemberSummaryResponse.from(owner), String.valueOf(item.getCommunityId()),
                CategoryResponse.from(category), PlaceResponse.from(place), images,
                item.getAvailableStartDate(), item.getAvailableEndDate(), item.getVisibility().name(),
                GeoDistance.meters(center, place), GeoDistance.BASIS, item.getVersion(), actions,
                item.getCreatedAt());
    }

    /** 목록 항목 변환. 입력 순서를 유지한다. */
    public List<ItemSummaryResponse> summaries(List<Item> items, Boolean availableForRange,
                                               Map<Long, Integer> pendingCounts) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> placeIds = new HashSet<>();
        Set<Long> communityIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (Item i : items) {
            categoryIds.add(i.getCategoryId());
            ownerIds.add(i.getOwnerId());
            placeIds.add(i.getPlaceId());
            communityIds.add(i.getCommunityId());
            itemIds.add(i.getId());
        }
        Map<Long, Category> categories = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, MemberSummary> owners = memberPort.findSummaries(ownerIds);
        Map<Long, PlaceInfo> places = communityPort.findPlaces(placeIds);
        Map<Long, GeoPoint> centers = communityIds.stream()
                .map(id -> Map.entry(id, communityPort.findCenter(id)))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        Map<Long, Long> thumbs = itemImageRepository.findThumbnails(itemIds).stream()
                .collect(Collectors.toMap(ItemImageRepository.Thumbnail::getItemId,
                        ItemImageRepository.Thumbnail::getMediaFileId));

        return items.stream().map(i -> {
            Category category = categories.get(i.getCategoryId());
            MemberSummary owner = owners.get(i.getOwnerId());
            PlaceInfo place = places.get(i.getPlaceId());
            GeoPoint center = centers.get(i.getCommunityId());
            if (category == null || owner == null || place == null || center == null) {
                throw missingRef();
            }
            Long thumb = thumbs.get(i.getId());
            return new ItemSummaryResponse(
                    String.valueOf(i.getId()), i.getTitle(), CategoryResponse.from(category),
                    MemberSummaryResponse.from(owner), PlaceResponse.from(place),
                    thumb == null ? null : MediaFileResponse.contentUrl(thumb),
                    i.getVisibility().name(), GeoDistance.meters(center, place), GeoDistance.BASIS,
                    availableForRange, i.getCreatedAt(),
                    pendingCounts == null ? null : pendingCounts.getOrDefault(i.getId(), 0),
                    i.getVersion());
        }).toList();
    }

    /** 참조 대상(카테고리·장소·회원)이 사라진 데이터 정합성 오류 */
    private BusinessException missingRef() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    /** 거리 정렬용: 장소 ID → 거리(m) */
    public Map<Long, Integer> distancesByPlace(Collection<Long> placeIds, long communityId) {
        GeoPoint center = communityPort.findCenter(communityId).orElseThrow(this::missingRef);
        return communityPort.findPlaces(placeIds).values().stream()
                .collect(Collectors.toMap(PlaceInfo::id, p -> GeoDistance.meters(center, p)));
    }
}
