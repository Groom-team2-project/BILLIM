package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CreateItemRequest;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.item.dto.UpdateItemRequest;
import com.billim.domain.item.dto.VisibilityChangeRequest;
import com.billim.domain.item.port.CurrentMemberProvider;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.item.service.ItemService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;

/** B_017 검색 · B_019 등록 · B_020 상세 · B_021 수정 · B_022 공개 변경 · B_023 삭제 */
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;
    private final CurrentMemberProvider currentMember;

    public ItemController(ItemService itemService, CurrentMemberProvider currentMember) {
        this.itemService = itemService;
        this.currentMember = currentMember;
    }

    @GetMapping
    public ItemPageResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String placeId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long memberId = currentMember.requireMemberId();
        ItemSearchCondition cond = new ItemSearchCondition(
                keyword,
                categoryId == null ? null : IdParser.parse(categoryId),
                startDate, endDate,
                placeId == null ? null : IdParser.parse(placeId),
                ItemSort.parse(sort), page, size);
        return itemService.search(memberId, cond);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDetailResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateItemRequest request) {
        long memberId = currentMember.requireMemberId();
        IdempotencyKeys.require(idempotencyKey);
        return itemService.create(memberId, request);
    }

    @GetMapping("/{itemId}")
    public ItemDetailResponse get(@PathVariable String itemId) {
        long memberId = currentMember.requireMemberId();
        return itemService.get(memberId, IdParser.parse(itemId));
    }

    @PutMapping("/{itemId}")
    public ItemDetailResponse update(@PathVariable String itemId, @Valid @RequestBody UpdateItemRequest request) {
        long memberId = currentMember.requireMemberId();
        return itemService.update(memberId, IdParser.parse(itemId), request);
    }

    @PatchMapping("/{itemId}/visibility")
    public ItemDetailResponse changeVisibility(@PathVariable String itemId,
                                               @Valid @RequestBody VisibilityChangeRequest request) {
        long memberId = currentMember.requireMemberId();
        return itemService.changeVisibility(memberId, IdParser.parse(itemId), request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String itemId, @RequestParam Long expectedVersion) {
        long memberId = currentMember.requireMemberId();
        if (expectedVersion < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "expectedVersion은 0 이상이어야 합니다.");
        }
        itemService.delete(memberId, IdParser.parse(itemId), expectedVersion);
    }
}
