package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CreateItemRequest;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemMapCondition;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.item.dto.MapResultsResponse;
import com.billim.domain.item.dto.UpdateItemRequest;
import com.billim.domain.item.dto.VisibilityChangeRequest;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.item.service.ItemService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** B_017 물건 검색. 같은 동네 공개 물건만, 기간은 시작·종료 함께, size 최대 50. 정렬 LATEST(기본)·NEAREST */
    @GetMapping
    public ItemPageResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String placeId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        ItemSearchCondition cond = new ItemSearchCondition(
                keyword,
                categoryId == null ? null : IdParser.parse(categoryId),
                startDate, endDate,
                placeId == null ? null : IdParser.parse(placeId),
                ItemSort.parse(sort), page, size);
        return itemService.search(memberId, cond);
    }

    /** B_018 지도 집계. 검색과 같은 노출·기간 규칙, 지도 범위 안 장소별 물건 수 최대 200곳(초과 시 truncated) */
    @GetMapping("/map")
    public MapResultsResponse map(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng,
            Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        ItemMapCondition cond = new ItemMapCondition(keyword,
                categoryId == null ? null : IdParser.parse(categoryId),
                startDate, endDate, minLat, maxLat, minLng, maxLng);
        return itemService.map(memberId, cond);
    }

    /** B_019 물건 등록. Idempotency-Key 필수. 사진 1~5장, 첫 사진이 대표(sort_order 0) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDetailResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateItemRequest request,
                                     Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        String key = IdempotencyKeys.require(idempotencyKey);
        return itemService.create(memberId, key, request);
    }

    /** B_020 물건 상세. 본인은 숨김 포함, 타인은 같은 동네 공개 물건만. 그 외 404 */
    @GetMapping("/{itemId}")
    public ItemDetailResponse get(@PathVariable String itemId, Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        return itemService.get(memberId, IdParser.parse(itemId));
    }

    /** B_021 물건 전체 수정. 본인만, expectedVersion 불일치 409. imageIds 순서가 새 표시 순서, description 생략은 제거 */
    @PutMapping("/{itemId}")
    public ItemDetailResponse update(@PathVariable String itemId, @Valid @RequestBody UpdateItemRequest request,
                                     Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        return itemService.update(memberId, IdParser.parse(itemId), request);
    }

    /** B_022 공개·공개 중지. 본인만, PUBLIC·HIDDEN만. expectedVersion 불일치 409 */
    @PatchMapping("/{itemId}/visibility")
    public ItemDetailResponse changeVisibility(@PathVariable String itemId,
                                               @Valid @RequestBody VisibilityChangeRequest request,
                                               Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        return itemService.changeVisibility(memberId, IdParser.parse(itemId), request);
    }

    /** B_023 물건 삭제. 본인만, 논리 삭제(행·사진 유지). expectedVersion 필수(0 이상), 불일치 409 */
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String itemId, @RequestParam Long expectedVersion,
                       Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        if (expectedVersion < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "expectedVersion은 0 이상이어야 합니다.");
        }
        itemService.delete(memberId, IdParser.parse(itemId), expectedVersion);
    }
}
