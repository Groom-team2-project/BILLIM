package com.billim.domain.rental.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.TrustMetricsResponse;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalTrustService {
    private final RentalRepository rentals;
    private final MemberRepository members;
    private final CommunityMembershipQuery memberships;
    private final BlockQuery blocks;

    public TrustMetricsResponse get(Long memberId, Long viewerId) {
        if (viewerId == null || viewerId < 1) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        members.findById(viewerId).filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (memberId == null || memberId < 1) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!memberId.equals(viewerId) && !rentals.hasTraded(viewerId, memberId)) {
            var community = memberships.findActiveCommunityId(viewerId);
            if (community.isEmpty() || !memberships.hasValidMembership(memberId, community.get())
                    || blocks.isBlockedBetween(viewerId, memberId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        return new TrustMetricsResponse(
                Math.toIntExact(rentals.countByBorrowerIdAndStatus(memberId, RentalStatus.RETURNED)
                        + rentals.countByOwnerIdAndStatus(memberId, RentalStatus.RETURNED)),
                Math.toIntExact(rentals.countByOwnerIdAndStatus(memberId, RentalStatus.RETURNED)),
                Math.toIntExact(rentals.countByBorrowerIdAndStatusAndReturnPromiseKeptTrue(memberId, RentalStatus.RETURNED)),
                Math.toIntExact(rentals.countByBorrowerIdAndStatusAndReturnPromiseKeptIsNotNull(memberId, RentalStatus.RETURNED)),
                "완료된 대여 기준. 확정 반납 약속이 있는 거래의 서버 반납 확인 시각으로 약속 준수 집계.");
    }
}
