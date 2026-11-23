package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.BlockPageResponse;
import com.billim.domain.safety.service.BlockService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이웃 차단 (API 명세 12.1 ~ 12.3).
 * PUT·DELETE는 변경 없는 반복 호출에 안전하다 (명세 0.3).
 */
@RestController
@RequestMapping("/api/v1/members/me/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    /** E_052 내 차단 목록. 내가 차단한 방향만 */
    @GetMapping
    public BlockPageResponse findMyBlocks(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        return blockService.findMyBlocks(SafetyRequests.memberId(member), page, size);
    }

    /** E_053 이웃 차단. 이미 차단했으면 그대로 성공 */
    @PutMapping("/{memberId}")
    public ResponseEntity<Void> block(
            @PathVariable @Positive Long memberId,
            @AuthenticationPrincipal LoginMember member) {
        blockService.block(SafetyRequests.memberId(member), memberId);
        return ResponseEntity.noContent().build();
    }

    /** E_054 내 차단 해제. 없는 관계도 성공 */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> unblock(
            @PathVariable @Positive Long memberId,
            @AuthenticationPrincipal LoginMember member) {
        blockService.unblock(SafetyRequests.memberId(member), memberId);
        return ResponseEntity.noContent().build();
    }
}
