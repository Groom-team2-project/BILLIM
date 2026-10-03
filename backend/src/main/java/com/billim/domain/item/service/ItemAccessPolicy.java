package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 물건 조회 권한 판정.
 * 동네 소속·차단 판정은 해당 도메인(E)이 아직 없어 연동하지 않았다. 아래 TODO 지점에서 교체한다.
 */
@Component
public class ItemAccessPolicy {

    /**
     * 로그인 회원의 유효한 활성 동네 ID.
     * TODO(E): 동네 인증 도메인의 회원 소속 조회로 교체한다. 소속이 없으면 Optional.empty().
     *          연동 전에는 값을 지어내지 않고 503 DEPENDENCY_UNAVAILABLE로 처리한다.
     */
    public Optional<Long> findActiveCommunityId(long memberId) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "동네 인증 연동 전이라 처리할 수 없습니다.");
    }

    /** 유효한 소속이 없으면 403 COMMUNITY_VERIFICATION_REQUIRED */
    public long requireActiveCommunityId(long memberId) {
        return findActiveCommunityId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
    }

    /** 본인은 HIDDEN도 조회. 타인은 같은 유효 동네의 PUBLIC만. DELETED는 모두 불가 */
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
        Optional<Long> active = findActiveCommunityId(memberId);
        if (active.isEmpty() || !active.get().equals(item.getCommunityId())) {
            return false;
        }
        // TODO(E): 차단 관계(내가 차단했거나 나를 차단한 회원)의 물건은 제외한다.
        return true;
    }
}
