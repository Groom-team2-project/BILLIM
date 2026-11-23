package com.billim.domain.item.controller;

import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.item.service.ItemService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 회원별 물건 목록 (프로필 화면)
@RestController
@RequestMapping("/api/v1/members")
public class MemberItemController {

    private final ItemService itemService;

    public MemberItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** B_024 내 등록 물건 목록. PUBLIC·HIDDEN, 삭제 제외, 최신순. 소속 변경 전 물건 포함 */
    @GetMapping("/me/items")
    public ItemPageResponse mine(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        return itemService.listMine(memberId, page, size);
    }

    /** B_025 이웃 공개 물건 목록. 내 유효 동네의 PUBLIC만, 최신순. 없는 회원·차단 관계면 404 */
    @GetMapping("/{memberId}/items")
    public ItemPageResponse neighbor(@PathVariable String memberId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     Authentication authentication) {
        long viewerId = AuthenticatedMember.id(authentication);
        return itemService.listNeighbor(viewerId, IdParser.parse(memberId), page, size);
    }
}
