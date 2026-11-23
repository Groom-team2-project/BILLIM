package com.billim.domain.safety.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.member.entity.Member;
import com.billim.domain.safety.dto.BlockPageResponse;
import com.billim.domain.safety.dto.BlockResponse;
import com.billim.domain.safety.entity.Block;
import com.billim.domain.safety.repository.BlockRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 이웃 차단과 해제 (API 명세 12.1 ~ 12.3).
 *
 * <p>차단은 신규 관계만 막는다. 진행 중인 거래는 반납 처리 경로를 위해 유지하고,
 * 기존 거래에 연결된 메시지도 허용한다. 그 판정은 각 도메인이 {@link BlockQuery}로 수행한다.
 * (정책 및 상태 5 차단, 도메인 가이드 결정 '차단 중 진행 거래')
 */
@Service
@Transactional(readOnly = true)
public class BlockService implements BlockQuery {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    private final BlockRepository blockRepository;
    private final CommunityMembershipQuery membershipQuery;
    private final MemberRowLock memberRowLock;
    private final EntityManager entityManager;

    public BlockService(BlockRepository blockRepository,
                        CommunityMembershipQuery membershipQuery,
                        MemberRowLock memberRowLock,
                        EntityManager entityManager) {
        this.blockRepository = blockRepository;
        this.membershipQuery = membershipQuery;
        this.memberRowLock = memberRowLock;
        this.entityManager = entityManager;
    }

    /** 내 차단 목록. 내가 차단한 방향만 보여준다 (API 명세 E_052) */
    public BlockPageResponse findMyBlocks(long memberId, int page, int size) {
        Page<Block> blocks = blockRepository
                .findByBlockerIdOrderByCreatedAtDescIdDesc(memberId, PageRequest.of(page, size));

        return new BlockPageResponse(
                toResponses(blocks.getContent()),
                page, size, blocks.getTotalElements(), blocks.hasNext());
    }

    /**
     * 이웃 차단 (API 명세 E_053). 이미 차단했으면 아무것도 바꾸지 않고 성공이다.
     *
     * <p>서로를 동시에 차단해도 교착이 생기지 않게 양측 회원 행을 ID순으로 잠근다.
     */
    @Transactional
    public void block(long memberId, long targetMemberId) {
        if (memberId == targetMemberId) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "자기 자신은 차단할 수 없습니다.");
        }

        memberRowLock.lockBoth(memberId, targetMemberId);
        requireKnownNeighbor(memberId, targetMemberId);

        if (blockRepository.findByBlockerIdAndBlockedId(memberId, targetMemberId).isPresent()) {
            return;
        }

        blockRepository.save(Block.of(memberId, targetMemberId));
        log.info("이웃 차단: blockerId={} blockedId={}", memberId, targetMemberId);
    }

    /**
     * 내 차단 해제 (API 명세 E_054). 없는 관계도 성공이다.
     *
     * <p>내가 만든 방향만 지운다. 상대가 나를 차단한 상태면 상호 노출·일반 채팅은 계속 제한된다.
     */
    @Transactional
    public void unblock(long memberId, long targetMemberId) {
        blockRepository.findByBlockerIdAndBlockedId(memberId, targetMemberId)
                .ifPresent(block -> {
                    blockRepository.delete(block);
                    log.info("차단 해제: blockerId={} blockedId={}", memberId, targetMemberId);
                });
    }

    @Override
    public boolean isBlockedBetween(long memberId, long otherMemberId) {
        if (memberId == otherMemberId) {
            return false;
        }
        return blockRepository.existsBetween(memberId, otherMemberId);
    }

    @Override
    public boolean hasBlocked(long memberId, long targetMemberId) {
        if (memberId == targetMemberId) {
            return false;
        }
        return blockRepository.existsByBlockerIdAndBlockedId(memberId, targetMemberId);
    }

    @Override
    public Set<Long> findBlockedAmong(long memberId, Collection<Long> candidateMemberIds) {
        if (candidateMemberIds == null || candidateMemberIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(blockRepository.findRelatedMemberIdsAmong(memberId, candidateMemberIds));
    }

    @Override
    public Set<Long> findBlockRelatedMemberIds(long memberId) {
        return new HashSet<>(blockRepository.findRelatedMemberIds(memberId));
    }

    /**
     * 볼 수 있는 상대만 차단할 수 있다. 같은 유효 동네 이웃이거나 거래한 적이 있는 상대다.
     * 그 외는 애초에 알 수 없는 사람이라 존재를 알리지 않고 404로 돌려보낸다. (명세 0.2)
     */
    private void requireKnownNeighbor(long memberId, long targetMemberId) {
        if (sharesCommunity(memberId, targetMemberId)) {
            return;
        }
        if (hasRentalTogether(memberId, targetMemberId)) {
            return;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private boolean sharesCommunity(long memberId, long targetMemberId) {
        return membershipQuery.findActiveCommunityId(memberId)
                .filter(communityId -> membershipQuery.hasValidMembership(targetMemberId, communityId))
                .isPresent();
    }

    /**
     * 둘이 거래 당사자였던 적이 있는지. 대여 도메인의 Repository를 바꾸지 않고 조회만 한다.
     * 소속이 바뀐 뒤에도 거래 상대는 차단할 수 있어야 한다.
     */
    private boolean hasRentalTogether(long memberId, long targetMemberId) {
        Long count = entityManager.createQuery("""
                        SELECT COUNT(r)
                          FROM Rental r
                         WHERE (r.ownerId = :memberId AND r.borrowerId = :targetMemberId)
                            OR (r.ownerId = :targetMemberId AND r.borrowerId = :memberId)
                        """, Long.class)
                .setParameter("memberId", memberId)
                .setParameter("targetMemberId", targetMemberId)
                .getSingleResult();

        return count > 0;
    }

    /** 차단한 회원의 표시 정보를 건별로 조회하지 않고 한 번에 모아 붙인다 */
    private List<BlockResponse> toResponses(List<Block> blocks) {
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<Long> blockedIds = blocks.stream().map(Block::getBlockedId).toList();
        Map<Long, Member> members = entityManager
                .createQuery("SELECT m FROM Member m WHERE m.id IN :ids", Member.class)
                .setParameter("ids", blockedIds)
                .getResultList().stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        return blocks.stream()
                .map(block -> BlockResponse.of(block, requireLoaded(members, block.getBlockedId())))
                .toList();
    }

    /** FK가 가리키는 회원은 반드시 있어야 한다. 없으면 데이터 손상이므로 숨기지 않는다 */
    private static Member requireLoaded(Map<Long, Member> members, long memberId) {
        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalStateException("차단 대상 회원을 찾을 수 없습니다: memberId=" + memberId);
        }
        return member;
    }
}
