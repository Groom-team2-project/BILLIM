package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.port.BlockPort;
import com.billim.domain.item.port.CommunityPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 물건 조회 권한 판정. 차단·활성 동네 판정은 각 담당 계약(Port)에 위임한다. */
@Component
public class ItemAccessPolicy {

    private final CommunityPort communityPort;
    private final BlockPort blockPort;

    public ItemAccessPolicy(CommunityPort communityPort, BlockPort blockPort) {
        this.communityPort = communityPort;
        this.blockPort = blockPort;
    }

    /** 본인은 HIDDEN도 조회. 타인은 같은 유효 동네의 PUBLIC·차단 없음만. DELETED는 모두 불가 */
    public boolean canView(Item item, long memberId) {
        if (item.isDeleted()) {
            return false;
        }
        if (item.isOwnedBy(memberId)) {
            return true;
        }
        if (item.getVisibility() != ItemVisibility.PUBLIC) {
            return false;
        }
        Optional<Long> active = communityPort.findActiveCommunityId(memberId);
        if (active.isEmpty() || !active.get().equals(item.getCommunityId())) {
            return false;
        }
        return !blockPort.findBlockRelatedMemberIds(memberId).contains(item.getOwnerId());
    }
}
