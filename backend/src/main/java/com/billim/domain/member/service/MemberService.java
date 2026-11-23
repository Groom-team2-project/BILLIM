package com.billim.domain.member.service;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.member.dto.PublicProfileResponse;
import com.billim.domain.rental.service.RentalPartyQuery;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.dto.MyProfileResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 회원 조회와 표시 이름 수정 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    /** 제재 결정은 safety, 적용은 회원 도메인 (도메인 가이드 4장) */
    private final SanctionService sanctionService;
    /** 소속 판정은 커뮤니티 도메인 (도메인 가이드 C06) */
    private final CommunityMembershipQuery membershipQuery;
    private final BlockQuery blockQuery;
    private final RentalPartyQuery rentalPartyQuery;

    /** 미인증·제재 상태도 자신의 온보딩·제한 상태는 조회 가능 */
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long memberId) {
        return toProfile(find(memberId));
    }

    /**
     * 표시 이름 수정. 본인 ACTIVE 회원만 가능.
     * expectedVersion 선검사는 오래된 화면에서 보낸 수정을 거르고,
     * 엔티티의 @Version은 커밋 시점의 동시 수정을 막는다. 노리는 상황이 다르다.
     */
    @Transactional
    public MyProfileResponse changeDisplayName(Long memberId, long expectedVersion, String displayName) {
        Member member = find(memberId);
        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (member.getVersion() != expectedVersion) {
            throw new VersionConflictException(member.getVersion());
        }
        member.changeDisplayName(displayName);
        memberRepository.flush();   // 증가한 @Version을 응답에 반영
        return toProfile(member);
    }

    /**
     * 이웃 공개 프로필 (A_007). 거래 없는 타 동네·차단 관계는 존재 은닉으로 404.
     * 본인·거래 당사자는 소속·차단과 무관하게 허용 — C_008(공개 프로필과 동일 권한)과 같은 기준.
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(long viewerId, long targetId) {
        // 조회자·대상 한 번에 읽기 — 분리 조회 시 왕복 2회. 본인 조회는 두 ID가 같아 Set으로 중복 제거
        Map<Long, Member> loaded = memberRepository.findAllById(new LinkedHashSet<>(List.of(viewerId, targetId))).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));
        Member viewer = loaded.get(viewerId);
        if (viewer == null || !viewer.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Member target = loaded.get(targetId);
        if (target == null) {
            throw notFound();
        }
        boolean self = viewerId == targetId;
        MembershipResponse membership = membershipQuery.findActiveMembership(targetId).orElse(null);

        if (!self && !rentalPartyQuery.hasTraded(viewerId, targetId)
                && (!inSameCommunity(viewerId, membership)
                        || blockQuery.isBlockedBetween(viewerId, targetId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return PublicProfileResponse.of(target, membership,
                !self && blockQuery.hasBlocked(viewerId, targetId));
    }

    /** 목록 응답의 소유자·당사자 표시용 */
    @Transactional(readOnly = true)
    public Map<Long, MemberSummaryResponse> getSummaries(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(Set.copyOf(memberIds)).stream()
                .collect(Collectors.toMap(Member::getId, MemberSummaryResponse::from));
    }

    /** 호출부가 읽은 대상 소속의 communityId만 비교 — 같은 소속 중복 조회 방지 */
    private boolean inSameCommunity(long viewerId, MembershipResponse targetMembership) {
        if (targetMembership == null) {
            return false;
        }
        return membershipQuery.findActiveCommunityId(viewerId)
                .map(String::valueOf)
                .filter(targetMembership.communityId()::equals)
                .isPresent();
    }

    private MyProfileResponse toProfile(Member member) {
        return MyProfileResponse.from(member, sanctionService.isRestricted(member.getId()),
                membershipQuery.findActiveMembership(member.getId()).orElse(null));
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private Member find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
