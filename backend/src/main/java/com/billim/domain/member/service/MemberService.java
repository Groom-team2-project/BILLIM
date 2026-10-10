package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 조회와 표시 이름 수정 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    /** 미인증·제재 상태도 자신의 온보딩·제한 상태는 조회 가능 */
    @Transactional(readOnly = true)
    public Member getMyProfile(Long memberId) {
        return find(memberId);
    }

    /**
     * 표시 이름 수정. 본인 ACTIVE 회원만 가능.
     * expectedVersion 선검사는 오래된 화면에서 보낸 수정을 거르고,
     * 엔티티의 @Version은 커밋 시점의 동시 수정을 막는다. 노리는 상황이 다르다.
     */
    @Transactional
    public Member changeDisplayName(Long memberId, long expectedVersion, String displayName) {
        Member member = find(memberId);
        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (member.getVersion() != expectedVersion) {
            throw new VersionConflictException(member.getVersion());
        }
        member.changeDisplayName(displayName);
        return member;
    }

    private Member find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
