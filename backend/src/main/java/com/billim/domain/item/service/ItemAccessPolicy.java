package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 물건 조회 권한 판정.
 * 활성 동네는 회원이 선택한 동네(members.active_community_id)를 쓴다.
 * 그 소속의 유효성(인증 만료·탈퇴)과 차단 판정은 동네 도메인(E) 연동 후 추가한다.
 */
@Component
public class ItemAccessPolicy {

    private final MemberRepository memberRepository;

    public ItemAccessPolicy(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 로그인 회원의 활성 동네 ID. 선택한 동네가 없거나 활동 중인 회원이 아니면 Optional.empty().
     * TODO(E): 선택한 동네가 유효한 소속인지(community_memberships의 인증 만료·탈퇴) 확인한다.
     */
    public Optional<Long> findActiveCommunityId(long memberId) {
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .map(Member::getActiveCommunityId);
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
