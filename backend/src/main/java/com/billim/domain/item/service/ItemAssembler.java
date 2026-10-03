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
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entity → 응답 DTO 변환. 목록은 카테고리·썸네일을 배치 조회해 항목별 쿼리를 만들지 않는다.
 *
 * 다른 도메인 정보는 아직 연동하지 않았다(값을 지어내지 않고 생략한다).
 * TODO(A): owner.displayName·joinedAt · TODO(E): place 이름·좌표·안내, distanceMeters · TODO(C): pendingRequestCount
 */
@Component
public class ItemAssembler {

    private final CategoryRepository categoryRepository;
    private final ItemImageRepository itemImageRepository;

    public ItemAssembler(CategoryRepository categoryRepository, ItemImageRepository itemImageRepository) {
        this.categoryRepository = categoryRepository;
        this.itemImageRepository = itemImageRepository;
    }

    public ItemDetailResponse detail(Item item, long viewerId) {
        Category category = categoryRepository.findById(item.getCategoryId()).orElseThrow(this::missingRef);
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
                MemberSummaryResponse.ofId(item.getOwnerId()), String.valueOf(item.getCommunityId()),
                CategoryResponse.from(category), PlaceResponse.of(item.getPlaceId(), item.getCommunityId()), images,
                item.getAvailableStartDate(), item.getAvailableEndDate(), item.getVisibility().name(),
                null, null, item.getVersion(), actions, item.getCreatedAt());
    }

    /** 목록 항목 변환. 입력 순서를 유지한다. */
    public List<ItemSummaryResponse> summaries(List<Item> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (Item i : items) {
            categoryIds.add(i.getCategoryId());
            itemIds.add(i.getId());
        }
        Map<Long, Category> categories = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, Long> thumbs = itemImageRepository.findThumbnails(itemIds).stream()
                .collect(Collectors.toMap(ItemImageRepository.Thumbnail::getItemId,
                        ItemImageRepository.Thumbnail::getMediaFileId));

        return items.stream().map(i -> {
            Category category = categories.get(i.getCategoryId());
            if (category == null) {
                throw missingRef();
            }
            Long thumb = thumbs.get(i.getId());
            return new ItemSummaryResponse(
                    String.valueOf(i.getId()), i.getTitle(), CategoryResponse.from(category),
                    MemberSummaryResponse.ofId(i.getOwnerId()), PlaceResponse.of(i.getPlaceId(), i.getCommunityId()),
                    thumb == null ? null : MediaFileResponse.contentUrl(thumb),
                    i.getVisibility().name(), null, null, null, i.getCreatedAt(), null, i.getVersion());
        }).toList();
    }

    /** 참조 대상(카테고리)이 사라진 데이터 정합성 오류 */
    private BusinessException missingRef() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
